# Backend contract check — lockDomain & getDomainInfo (2026-09-03)

Verified against backend source at v2.11.5 (`domain-registrar`). Bug 1's diagnosis holds.
**Bug 2's does not** — please do not ship that fix as written.

## Bug 1 — lockDomain: confirmed, here is the exact payload

`POST /v2/domains/{domain}/lock` (and `/unlock`) return the standard v2 envelope with the
payload **nested under `data`**:

```json
{
  "success": true,
  "data": { "domain": "example.com", "locked": true, "message": "Domain locked successfully" },
  "timestamp": "2026-09-03T10:00:00Z"
}
```

- Source: `DomainController.lockDomain` → `EPPApiResponse.success(result.toResponseDTO())`,
  `DomainLockResponseDTO {String domain; boolean locked; String message;}`.
- The flag is a **boolean `locked`**, not a string status — map `data.locked` → your
  locked/unlocked wording.
- Failure is HTTP 400: `{"success": false, "error": "<message>", "errorCode": "LOCK_FAILED"}`.

So "everything mapped to null" is consistent with reading the fields at the top level instead of
under `data`. Your fix (prefer backend values, deterministic fallback text) is right — just map
`data.domain`, `data.locked`, `data.message`.

## Bug 2 — getDomainInfo: the premise is incorrect

> "The /info endpoint is EPP-level and simply doesn't carry those flags — the code fabricated
> false instead of admitting ignorance."

**The backend does carry them, and they are real values from the same source `listUserDomains`
reads.** `DomainInfoResponseDTO` (returned by `GET /v2/domains/{domain}/info`) includes:

| JSON field | Type | Populated from |
|---|---|---|
| `autoRenew` | boolean | `domainRegistry.autorenew` (DB) |
| **`privacy`** | boolean | `domainRegistry.privacy` (DB) |
| `expiryDate` | date-time | EPP `exDate`, or DB `expirationDate` on the fallback path |
| `creationDate` | date-time | EPP `crDate`, or DB `creationDate` on the fallback path |
| `locked`, `status`, `statuses`, `nameservers`, `registrantEmail`, `registrar`, `premium`, `expired`, `inRedemptionPeriod`, `rgpStatus`, `redemptionEndDate`, `inAutoRenewGracePeriod`, `dnssecEnabled`, `dnssecRecords` | — | EPP + DB |

Both code paths set them explicitly:
- EPP path: `infoResponse.autoRenew = domainRegistry.autorenew; infoResponse.privacy = domainRegistry.privacy;`
- DB fallback: `DomainInfoResponseDTO.fromDomainRegistry(...)` sets the same two fields.

**Most likely the actual mapping bug: the field is named `privacy`, not `privacyProtection`** —
the same field-name class of miss as Bug 1. `autoRenew` is spelled exactly that.

### Why the planned fix would be worse than the bug

Making the fields nullable/omitted and adding *"Does NOT report autoRenew or privacy settings;
read them from listUserDomains"* to the tool description would (a) discard real data the backend
sends, and (b) put a **false statement** into the agent-facing contract that every model will
trust. That is the same failure mode as audit F1 — asserting something untrue for a non-domain
reason. Please map the fields instead:

```
data.autoRenew  -> autoRenew
data.privacy    -> privacyProtection   // note the backend name
data.expiryDate / data.creationDate -> dates
```

Once mapped, `getDomainInfo` and `listUserDomains` agree by construction — both read
`DomainRegistry`.

### Two honest caveats (real, narrow)

1. **Transferred-out domains**: when the backend detects the domain has been transferred away, it
   returns `status: "transferredOut"` with `autoRenew` hardcoded `false` (privacy still real).
   Defensible (the domain is no longer ours) but it *is* a fabricated value; treat `autoRenew` as
   meaningless when `status == "transferredOut"`.
2. **Pending-create domains**: `expiryDate`/`creationDate` are deliberately `null` until the
   registry confirms creation. Null there means "not yet", not "unknown" — worth saying in the
   tool description, since that is a legitimate source of the null dates Claude.ai saw.

## Suggested description wording

Instead of the disclaimer, something true and useful:

> Returns registry (EPP) state plus account settings for one domain: status, nameservers,
> lock state, auto-renew, privacy, creation/expiry dates, DNSSEC and redemption info. Dates are
> null while a registration is still pending at the registry.

Questions → the backend session / Armand.
