# A2A Confirmation Gate — Spec

**Status: IMPLEMENTED 2026-09-04** as Layer A (see §6). §1 and §2 still hold and are the reason this
is only one layer of four — read §2 before treating the gate as a security control. §3 records the
design that was proposed; §6 records what was built instead, and why it differs in three places.
**Raised from:** the Osir Deploy Platform side, which needs the owned tier (single-tenant VPS) and
audited this surface before automating VPS builds. Written 2026-07-17.
**Scope:** `a2a-server` — `VpsSpecialistAgent`. Touches `common` and `mcp-server` only by moving one
existing class.

---

## 1. Why this exists

`VpsSpecialistAgent` exposes three operations that spend money or destroy data. On the MCP transport
all three stage through `PendingActionStore` and require `executeConfirmedAction`. On A2A they do not:

| skill | what it does | gate on A2A today | gate on MCP today |
|---|---|---|---|
| `build_vps` | installs an OS — **wipes the box** | in-band `confirm=ERASE` token | staged |
| `delete_vps` | terminates the VPS | **none** | staged |
| `order_vps` | **spends money** | **none** | staged |

`delete_vps` (`VpsSpecialistAgent.java`, `delete_vps` branch) calls `vpsService.deleteInstance()`
directly. `order_vps` calls `vpsService.orderVps()` directly. Both are one A2A message from a live
effect.

**This is not an authorization hole.** `A2AResource` propagates the caller's bearer token and the
backend enforces ownership (`sshKeyIds ⊆ ownedKeyIds(session)`, `userId`-from-session). A caller can
only affect their own resources. The exposure is a *customer's own token* driving an unattended agent
that decides, without a human, to rebuild or terminate their box.

## 2. The correction that motivated this spec

The deploy-platform notes claimed the fix was "port `PendingActionStore` to A2A — the in-band
`confirm=ERASE` token doesn't stop an unattended agent." **The premise is false and should not be
carried forward.**

`PendingActionStore` does not stop an unattended agent either. Its flow is `stage()` → returns an
`actionId` → `executeConfirmedAction(actionId)` → runs. An unattended agent holding the `actionId`
simply calls confirm. That is exactly one extra round-trip — the same cost as re-sending
`confirm=ERASE`. Both are **human-in-the-loop UX gates**: their entire security value is that a
person reads the summary. Neither is an authorization check.

So porting the store is **not** a security fix. Adopt it for the real benefits below, or don't adopt
it — but do not record the result as "the A2A gap is closed", because it will not be.

**What porting genuinely buys** (all real, none of them "stops an agent"):
- an audit row at confirm time, with the summary
- single-use claim + 5-minute expiry
- a caller match, so one session can't confirm another's staged action
- rate limiting (`DESTRUCTIVE` 3/min, `FINANCIAL` 5/min) — caps blast radius of a runaway loop
- the destructive summary lands in the task history where a human reviewing the conversation sees it
- one confirmation mechanism across both transports instead of two

**The only gate that actually stops an unattended caller is not exposing the operation to it.**
Removing `build_vps` / `delete_vps` / `order_vps` from the A2A agent card was considered and rejected
by the deploy-platform owner (`order_vps` is a wanted partner-agent revenue path). Recorded here so
the tradeoff isn't rediscovered: A2A is agent-to-agent by definition, so a gate that works by showing
a human a summary cannot work there in principle.

Note for whoever picks this up: the Osir Deploy Platform (C2) does **not** call A2A — it calls the
OSIR backend REST directly. Nothing in the owned tier depends on this surface, so it can change
freely without breaking deploys.

## 3. The change

### 3.1 Move the store into `common`

`git mv` these three, package `com.osir.mcp.security` **unchanged** so no imports in `mcp-server`
move:

- `PendingActionStore.java`
- `PendingAction.java`
- `DestructiveOpRateLimiter.java`

`mcp-server/…/security/` keeps `McpAudited`, `McpAuthInterceptor`, `RequiresAuth`,
`McpAuditInterceptor` — those are MCP-specific.

`common/build.gradle` needs `api 'io.quarkus:quarkus-scheduler'` for the `@Scheduled` expiry sweeps
(`PendingActionStore.cleanup`, `DestructiveOpRateLimiter.cleanup`). Both `mcp-server` and
`a2a-server` already have the extension.

Move `PendingActionStoreTest` to `common/src/test/…/security/` alongside it.

### 3.2 One validated claim, not two

Today `PendingActionStore.claim(actionId)` returns `Optional<PendingAction>` and does **no**
validation — `ConfirmationMCPServer` does expiry, caller match, and rate limit itself. If A2A copies
that sequence, the two transports drift, and a security check exists in two places.

Fold the checks into the store and give it a single entry point:

```java
public record Claim(PendingAction action, String error) {
    public boolean ok() { return action != null; }
}

/** exists -> unclaimed -> unexpired -> same caller -> within rate limit. */
public Claim claim(String actionId, String callerId)
```

Inject `DestructiveOpRateLimiter` into the store (constructor injection; `PendingActionStoreTest`
constructs it directly, so it becomes `new PendingActionStore(new DestructiveOpRateLimiter())`).

Do **not** leave the old unvalidated `claim(actionId)` in place next to the validated one — an
unvalidated claim sitting next to a validated one is a trap for the next reader.

`ConfirmationMCPServer.executeConfirmedAction` then reduces to: claim, return `claim.error()` if
`!ok()`, run, map the result. Its inline expiry/connection/rate-limit checks all delete. Its private
`extractSuccess(Object)` reflection helper is needed by A2A too — promote it to
`public static boolean PendingActionStore.isSuccess(Object)` rather than copying it.

⚠️ **`ConfirmationMCPServerTest` must be rewritten, not patched.** It mocks the store *and* the rate
limiter, then asserts expiry / wrong-connection / rate-limit behaviour — all of which now live inside
the mocked store, so those tests would be asserting the mock. They belong in `PendingActionStoreTest`
against the real store. What's left for `ConfirmationMCPServerTest` to own: that it passes
`connection.id()` as the callerId (if that ever becomes null or a constant, the caller match silently
stops meaning anything), that a denied claim returns the reason verbatim and doesn't run, and result
mapping incl. a throwing action.

### 3.3 Caller identity on A2A

A2A has no `McpConnection`. The bearer token is the only stable per-caller handle. Fingerprint it —
never store or log the raw token:

```java
private String callerId() {           // null when unauthenticated
    if (!authContext.hasOverride()) return null;
    byte[] d = MessageDigest.getInstance("SHA-256")
            .digest(authContext.getTokenOverride().getBytes(StandardCharsets.UTF_8));
    return "a2a-" + HexFormat.of().formatHex(d).substring(0, 16);
}
```

Staging or confirming while unauthenticated must be refused outright — the backend would reject it
anyway, and an anonymous callerId would let anonymous callers share a confirmation namespace.

**Known wart:** `TokenRefreshService` can refresh the token between stage and confirm, changing the
fingerprint and rejecting the confirm. The window is 5 minutes, it fails closed, and the caller can
re-stage. Key off a stable subject claim from the token instead if it proves annoying in practice.

### 3.4 `VpsSpecialistAgent`

Replace the `confirm=ERASE` block with staging; add staging to `delete_vps` and `order_vps`; add an
`execute_confirmed_action` skill.

- Capture every parameter **at stage time** inside the lambda. The confirm message carries only an
  `actionId` — if the op re-read its parameters at confirm time, a caller could stage a harmless
  action and confirm a different one.
- Buckets: `build_vps` and `delete_vps` → `DESTRUCTIVE`; `order_vps` → `FINANCIAL`.
- Staged response: `TaskState.INPUT_REQUIRED`, a `confirmation-required` artifact carrying the
  `ConfirmationRequiredResult` (actionId, toolName, summary, expiresIn), and the summary in the
  message text. The summary is the only thing a human has to judge by — it must name the target and
  the damage ("ERASES ALL DATA on that server… cannot be undone", "DEDUCTS FROM THE ACCOUNT
  BALANCE").
- **Route the confirm branch first**, before the destructive branches. It must trigger on
  `skill=execute_confirmed_action` *or* the presence of `actionId` metadata. Otherwise "yes,
  terminate the server" carrying an actionId re-enters the `delete_vps` branch (bare `contains`
  matching) and stages a second delete instead of confirming the first.
- Update the agent card: mark the three as staging, and add `execute_confirmed_action`.

### 3.5 Tests worth having

Use a **real** `PendingActionStore` in `VpsSpecialistAgentTest` (`@Spy`, not `@Mock`) — the point is
that the stage→confirm round trip holds, which a stubbed store fakes.

- each of the three ops: first call stages, `verifyNoInteractions(vpsService)`
- `build_vps` summary names the instance and says it erases data
- stage while unauthenticated → refused
- confirm runs the action; confirm passes the **stage-time** parameters through
- confirm is single-use (replayed actionId doesn't act twice)
- confirm by a different token is refused
- an actionId plus destructive free text still confirms, never re-stages
- existing `handle_explicitSkillWinsOverTextKeywords` **will fail** — it asserts `delete_vps` calls
  `deleteInstance()` directly. Update it to assert it staged `delete_vps` instead.

## 4. Out of scope, still open

- `changeVpsPaymentTerm` is a billing change that doesn't stage, on either transport.
- Whether the A2A agent card should advertise destructive skills at all (§2).

## 6. What was actually built (2026-09-04)

`a2a-server/…/security/ConfirmationGate.java`, wired into `BaseSpecialistAgent` and pinned in
`A2AResource`. Staging returns `INPUT_REQUIRED` with a `confirmation-required` artifact; the caller
continues the SAME task with `{"skill": "execute_confirmed_action", "actionId": "…"}`.

Scope is wider than §3.4's `VpsSpecialistAgent`: leaving `register_domain` or `pay_invoice` ungated
after building the gate would have kept the inconsistency that prompted it. Gated: `order_vps`,
`build_vps`, `delete_vps` (VPS), `register_domain`, `renew_domain`, `transfer_domain` (Domain),
`pay_invoice`, `create_payment` (Billing), `delete_contact` (Contact), `delete_dns_record` (DNS).

**Three deliberate departures from §3:**

1. **The store was not moved or reused (§3.1/§3.2).** `PendingActionStore` holds a `Callable`, which
   is why an MCP restart forgets every staged action — the same in-memory assumption that produced
   the move-to-owned SEV-1 a day earlier. A2A tasks are already persisted, so a staged action is
   recorded ON the task as DATA (skill + frozen params) and survives a restart with it. Only
   `DestructiveOpRateLimiter` moved to `common`, so both transports share one set of limits; its
   `Bucket` now carries its own per-minute limit instead of a two-way ternary.
2. **Caller identity is the JWT `sub`, not a token fingerprint (§3.3).** §3.3's own "known wart" —
   `TokenRefreshService` rotating the token between stage and confirm — disappears, and it matches
   how the MCP side keys its money rule (`MoveToOwnedService.key()`).
3. **The confirm must echo the actionId.** §3.4 allowed `skill=execute_confirmed_action` alone; a
   bare "yes, go ahead" is now refused, so the caller has to repeat an id it did not choose.

**One trap §3 did not name:** `A2AResource` REPLACES task metadata on a continuation and re-scores
routing from scratch. So the frozen parameters must never be re-read from caller metadata at confirm
time (they are not), and a task with a live staged action is now pinned to the agent that staged it —
otherwise "yes, terminate it" is handed to whichever agent those words happen to match.

Tests: `VpsSpecialistAgentTest` covers stage-then-confirm, single use, confirm-with-different-params,
confirm-without-actionId, the price warning, and staging while unauthenticated, against a REAL gate
(`GateTestSupport`). a2a-server: 121 tests, 0 failures.

**Still missing, and this is the important part:** Layers B (per-tool `osir:*` scopes), C (a backend
spend/velocity cap) and D (out-of-band approval to a channel the calling agent does not control).
Layer A does not stop an unattended caller and was never going to — §2 said so, and it is tracked in
`docs/TODO.md` so the gap is not mistaken for closed.

## 5. Before committing this

This repo is public. Nothing here is an exploitable disclosure — §1 explains why authz holds and a
caller can only reach their own resources — but the maintainer should make that call knowingly rather
than have it happen as a side effect of a doc landing.
