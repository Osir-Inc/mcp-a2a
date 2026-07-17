# VPS OS build — MCP and A2A

How an agent orders a VPS that arrives **with an operating system on it**, and how it reinstalls one.

This document covers the agent's surface. The backend REST contract it calls is documented separately
in the OSIR backend's own `docs/vps-os-build.md`. Requires backend **v2.9.2+** (2.9.1 mis-reports in-flight builds as FAILED; 2.9.0 also has no
`packageId` template lookup — see [§6](#6-version-compatibility)).

---

## 1. Why this exists

VirtFusion has **two** steps, and they are separate operations:

1. **Order** — a server is created. It has no operating system. It is an empty box.
2. **Build** — an OS template is installed onto it.

Until backend v2.9.0, Osir only ever did step 1. Every VPS we created was handed to the customer with
no OS. `orderVps` alone still does that if you omit `operatingSystemId` — that is legal, and sometimes
wanted, but it is almost never what a caller means.

The rule to internalise: **ordering costs money, building does not.** A build can be retried for free,
forever. A failed build must never lead an agent to order a second server.

---

## 2. The tools

All under the VPS MCP server. All require authentication (`loginWithDevice`) except where noted.

| Tool | What it does |
|---|---|
| `listVpsPackages` | Packages with pricing/specs/locations. **No auth.** Gives you a `packageId`. |
| `listVpsOsTemplates` | Installable OS templates — **by package (before ordering) or by instance (reinstall)**. |
| `addSshKey` | Store an OpenSSH public key on the account. **Idempotent.** Returns a key id. |
| `listMySshKeys` | Stored keys with ids and SHA256 fingerprints. |
| `deleteSshKey` | Remove a key. Does not affect servers already built with it. |
| `orderVps` | Order a server, optionally **with** an OS and SSH keys. Costs money. Staged. |
| `buildVpsInstance` | Install an OS on an existing server. Free. **Destructive.** Staged. |
| `getVpsInstanceDetails` | Includes `buildState` — poll this to follow a build. |

`orderVps`, `buildVpsInstance` and `osirAppDelete` are **staged**: they return an `actionId` instead of
acting. Present the summary to the user, then call `executeConfirmedAction(actionId)` if they approve.
See [`A2A-CONFIRMATION-GATE-SPEC.md`](A2A-CONFIRMATION-GATE-SPEC.md).

---

## 3. `listVpsOsTemplates` — the two keys

```
listVpsOsTemplates(packageId: string?, instanceId: string?, includeEol: bool = false)
```

**Pass exactly one.** Neither or both is an error, and the error is returned to you as a message rather
than an exception.

| Key | The question it answers | When |
|---|---|---|
| `packageId` (from `listVpsPackages`) | "What could I install if I ordered this?" | **Before ordering.** Feed the id into `orderVps`. |
| `instanceId` (from `listMyVpsInstances`) | "What can I reinstall this server with?" | **Reinstall.** Feed the id into `buildVpsInstance`. |

### Why not one unkeyed "list all templates"?

Because VirtFusion does not have one — it offers only per-server and per-package listings — and because
**the installable set genuinely varies with the package**. An ARM package offers ARM templates. Answering
from the wrong key advertises a template that VirtFusion then rejects at build time, which surfaces to a
customer as an inexplicable failure on a server they have already paid for.

So: ask with the key that matches what you are about to do. Do not borrow another server's `instanceId`
to answer a pre-order question — an older version of this guidance suggested exactly that; it was wrong.

### Template ids are not stable

> **Ids are per-install and change when templates are re-imported.** Never hardcode one, never remember
> one across sessions, never cache one in a saved preset. `46` is Debian 12 today and may be something
> else next month. Always resolve fresh, immediately before you use it.

Response (`{ templates: [...] }`, unwrapped for you into `result.templates`):

```json
{ "id": 46, "name": "Debian", "version": "12 (Bookworm)", "variant": "Minimal",
  "arch": 1, "eol": false, "type": "linux", "displayName": "Debian 12 (Bookworm) Minimal" }
```

EOL and non-Linux templates are filtered out unless `includeEol=true`.

---

## 4. SSH keys — required for OSIR APP DEPLOY

**OSIR APP DEPLOY must have an SSH key**, and it must be attached at build time. Without one, nothing
can log into the server to deploy anything, and there is no way to add a key afterwards short of a
rebuild — which erases the box.

Keys are **per user**, not per server: one key serves every VPS the customer owns.

### The idempotency contract

`addSshKey(name, publicKey)` is safe to call **before every order**. Identity is the key's SHA256
fingerprint — the key material — not the name and not the raw string. So the same key pasted twice with
different names, comments, or whitespace is one key, and re-storing it returns the existing entry rather
than creating a duplicate.

This is deliberate: it means an automated caller never has to ask "do I already have this key?" It can
just say "store this, give me the id" on every run.

```
addSshKey(name: "osir-app-deploy", publicKey: "ssh-ed25519 AAAAC3Nza... deploy@osir")
  → { id: 3, fingerprint: "SHA256:Yl3s…", name: "osir-app-deploy" }
```

Accepted algorithms: `ssh-rsa`, `ssh-ed25519`, `ecdsa-sha2-nistp{256,384,521}`. `ssh-dss` is rejected —
modern OpenSSH disables it by default, so accepting it would only mint keys that cannot log in. The key
must be a **single line**; a multi-line paste is rejected.

The VirtFusion user is always derived from the authenticated session and never from anything the caller
sends. There is no `userId` parameter and there will not be one.

---

## 5. The OSIR APP DEPLOY flow

The path is **OSIR APP DEPLOY → this agent (MCP/A2A) → domain-registrar backend → VirtFusion**. OSIR APP
DEPLOY never calls the backend directly.

```
1. listVpsPackages()                                   → packageId  (no auth needed)
2. addSshKey(name, publicKey)                          → keyId      (idempotent — just call it)
3. listVpsOsTemplates(packageId: <from 1>)             → operatingSystemId
4. orderVps(packageId, hostname, paymentTerm,
            operatingSystemId: <from 3>,
            sshKeyIds: [<from 2>])                     → actionId
5. executeConfirmedAction(actionId)                    → instanceId   ← money is spent here
6. getVpsInstanceDetails(instanceId)  ·  poll ~5s      → until buildState == COMPLETE
7. deploy over SSH
```

Steps 1–4 cost nothing and are freely retryable. Step 5 is the only one that spends money.

### Polling (step 6)

Poll at **~5 seconds, not 500ms** — every read hits VirtFusion live. Stop on `COMPLETE` or `FAILED`.

| `buildState` | Meaning |
|---|---|
| `UNBUILT` | No OS. Not an error — it means nobody has installed one. |
| `QUEUED` / `BUILDING` | In flight. |
| `COMPLETE` | OS installed. `builtAt` is set. Safe to deploy. |
| `FAILED` | Retry with `buildVpsInstance` — **free**. Never re-order to recover. |

> **Never re-order on a FAILED read — re-build.** Ordering spends money and leaves the first server
> behind; building is free and retryable forever. That asymmetry should always decide it.
>
> This is not hypothetical. Until backend **v2.9.2**, `getVpsInstanceDetails` returned `FAILED` for the
> whole ~20s of *every healthy build*: VirtFusion's `buildFailed` flag means "not successfully built
> yet", not "the build failed", and the backend checked it first. A caller polling mid-build saw FAILED
> on a server that was seconds from ready. On **v2.9.2+** `FAILED` is trustworthy — the rule stands
> anyway, because a bad read costs a server either way.

There is **no `queueId`** and no progress percentage. VirtFusion's build response carries no queue id, so
polling the instance is the only status source. Do not design a progress bar around one.

### If the order succeeds but the build does not

The backend treats a build failure at order time as **non-fatal**: you get a provisioned, unbuilt server
rather than a rolled-back order the customer has already paid for. Recovery is `buildVpsInstance`, free.

---

## 6. Version compatibility

| Backend | Behaviour |
|---|---|
| ≤ 2.9.0 | `listVpsOsTemplates(packageId:)` **not supported** — 400. Only `instanceId` works, so a first order cannot install an OS. |
| 2.9.1 | `packageId` supported. **But every in-flight build reports `FAILED` for ~20s** (see §4.3) — polling a build is unreliable. |
| **2.9.2+** | Both fixed. A build reads `QUEUED` → `BUILDING` → `COMPLETE`, and `FAILED` is trustworthy. **Use this.** |
| ≥ 2.9.1 | Supported. |

---

## 7. `buildVpsInstance` — destructive

```
buildVpsInstance(instanceId, operatingSystemId, sshKeyIds?, hostname?, swap?)
```

**This erases the disk, including any deployed application.** It is how reinstall works. It is staged
behind the confirmation gate and rate-limited as a destructive op; the A2A path additionally requires
metadata `confirm=ERASE`.

Do not call it to "fix" a server without the owner's explicit agreement. `swap` accepts 256/512/768 (MB)
or 1/1.5/2/3/4/5/6/8 (GB). `hostname` defaults to the instance's current one.

A build already `QUEUED` or `BUILDING` is rejected with a 409 rather than started twice.

---

## 8. A2A

Skills on `VpsSpecialistAgent`: `list_os_templates`, `build_vps`, `list_ssh_keys`, `add_ssh_key`, plus
the pre-existing `order_vps` etc.

Metadata keys: `packageId` **or** `instanceId` (+ `includeEol`) for `list_os_templates`;
`instanceId`, `operatingSystemId`, `sshKeyIds`, `hostname`, `swap`, `confirm=ERASE` for `build_vps`;
`name`, `publicKey` for `add_ssh_key`.

Routing note: the keyword set deliberately excludes bare `os` and `key` — `score()` matches substrings,
so `os` would hit *cost/host/most/close* (and this agent's own *hosting*) and `key` would hit domain
auth-key questions. `ssh`/`template`/`reinstall`/`rebuild` are specific enough.

---

## 9. Gotchas

- **`orderVps` without `operatingSystemId` gives a server with no OS.** Legal, rarely intended.
- **Template ids change.** Resolve immediately before use.
- **`packageId` and `instanceId` are not interchangeable.** See §3.
- **A key cannot be added after a build** without another (destructive) build. Attach it up front.
- **Retrying a build is free.** Ordering again is not. Never confuse the two.
- **`buildVpsInstance` on a working server wipes it.** Always confirm with the owner.
