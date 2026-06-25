# TLD Discovery — listCategorizedTlds + bulkDomainSuggestions

## Overview

Domain TLD selection is a reasoning task, not a computation task. Instead of
scoring TLDs server-side with a brittle keyword-match algorithm, the MCP
exposes a two-tool workflow where the server filters and the LLM ranks.

The server's job: return the right candidate set with enough structured
metadata for the LLM to reason about relevance.  
The LLM's job: match the user's keywords and intent against `categories` and
`audience`, pick 3–6 TLDs, explain the reasoning, then find specific names.

---

## The Two-Step Workflow

```
User prompt
    │
    ▼
listCategorizedTlds          ← Step 1: filter the catalog
    │  returns candidates[]
    │  with categories, audience, price, restrictions
    │
    ▼
LLM picks 3–6 TLDs           ← LLM reasoning against user context
    │
    ▼
bulkDomainSuggestions        ← Step 2: find specific available names
    │  returns grouped suggestions
    │
    ▼
LLM presents names + reasoning to user
```

---

## Step 1 — listCategorizedTlds

Returns all TLDs from the OSIR catalog that have both `categories` **and**
`audience` populated. TLDs without this metadata never appear.

### Parameters (all optional)

| Parameter | Type | Default | Description |
|---|---|---|---|
| `excludeRestricted` | bool | `false` | Drop TLDs where `hasRestrictions == true` (e.g. `.health`, `.bank`) |
| `excludeCcTLDs` | bool | `false` | Drop country-code and IDN TLDs (`extensionType` is `ccTLD` or `IDN`) |
| `maxRegisterPrice` | number | `null` | Drop TLDs where `registrationPrice > value` (USD). TLDs with unknown price are included. |
| `excludePremium` | bool | `false` | Drop TLDs where `hasPremium == true`. **Do not use as a budget filter.** `hasPremium` means the TLD has registry-tier premium pricing for a small subset of names — the standard `registrationPrice` in the catalog applies to most names. `.app`, `.dev`, and `.tech` are all `hasPremium: true` but register the vast majority of names at their standard price. Set this only when the user explicitly says "no premium domains" or "no surprise pricing." For budget filtering, use `maxRegisterPrice` instead. |
| `registry` | string | `null` | Return only TLDs from this registry (e.g. `"GOOGLE"`, `"VERISIGN"`). Case-insensitive. |

Keywords and intent are **not** parameters — the LLM has them in conversation
context already.

### Response shape

```json
{
  "success": true,
  "totalCandidates": 38,
  "filtersApplied": {
    "excludeRestricted": true,
    "excludeCcTLDs": true,
    "maxRegisterPrice": null,
    "excludePremium": false,
    "registry": null
  },
  "candidates": [
    {
      "tld": "bio",
      "categories": ["health", "medical", "pharma"],
      "audience": ["professional", "b2b"],
      "registrationPrice": "56.00",
      "renewalPrice": "56.00",
      "currency": "USD",
      "extensionType": "gTLD",
      "hasRestrictions": false,
      "hasPremium": false,
      "registryName": "Identity Digital",
      "minRegistrationPeriod": 1,
      "maxRegistrationPeriod": 10
    }
  ]
}
```

- `candidates` is sorted **alphabetically by tld** — deliberately not ranked.
- `filtersApplied` echoes what the server actually applied, so the LLM can
  say "searched 38 candidates after applying your filters" accurately.
- On zero results: `success: true`, `candidates: []`, and a `message` hint
  suggesting which filter to relax.
- On catalog unavailable: `success: false` with an error message.

### Filter guidance for the LLM

| User says | Set filter |
|---|---|
| "no country domains" | `excludeCcTLDs: true` |
| "not too expensive" / "budget" | `maxRegisterPrice: 15` (or interpret) |
| mentions a regulated industry casually | `excludeRestricted: true` |
| wants to register `.health` intentionally | leave `excludeRestricted: false` |
| "no premium domains" / "no surprise pricing" | `excludePremium: true` |
| "budget" / "cheap" (without mentioning premium) | `maxRegisterPrice: N` — **not** `excludePremium` |

---

## Step 2 — bulkDomainSuggestions

Generates specific name suggestions for keywords across the TLDs chosen in
step 1. Suggestions are grouped by keyword.

### Parameters

| Parameter | Required | Description |
|---|---|---|
| `keywords` | Yes | 1–10 keywords describing the project |
| `tlds` | Yes | 1–6 TLDs chosen from step 1 (no leading dot: `"tech"` not `".tech"`) |
| `lang` | No | Language code, default `"eng"` |
| `maxResults` | No | Max suggestions per keyword, default 20 |

The 6-TLD cap is enforced server-side. Use step 1 to narrow down before
calling this tool.

### Response shape

```json
{
  "success": true,
  "requestedTlds": ["com", "app", "bio"],
  "returnedTlds": ["com", "app"],
  "groups": [
    {
      "keyword": "voice",
      "suggestions": [
        { "name": "voicebio.com", "availability": "available" },
        { "name": "voiceapp.app", "availability": "available" }
      ]
    },
    {
      "keyword": "biomarker",
      "suggestions": [
        { "name": "biomarkerlab.bio", "availability": "available" }
      ]
    }
  ]
}
```

- `returnedTlds` may be a subset of `requestedTlds` if a TLD returned no
  results.
- `availability` is `"available"`, `"taken"`, or `"unknown"`. For `"unknown"`,
  follow up with `checkDomainAvailability` on specific names.
- Suggestions on premium-tier TLDs may carry premium pricing — confirm with
  `checkDomainAvailability` before presenting a price to the user.

---

## Controlled Vocabularies

The LLM should match these terms against user keywords and intent.

### categories

```
generic       business      commerce      tech          dev
ai            software      web           mobile        api
cloud         data          health        medical       pharma
clinical      wellness      fitness       dental        finance
fintech       banking       insurance     education     academic
media         news          design        art           creator
agency        community     social        blog          nonprofit
personal      brand         marketplace   retail        music
audio         video         photo
```

### audience

```
b2c           b2b           professional  developer     creator
enterprise    smb           consumer
```

---

## Architecture Decision

This is **Option C — Server Filters, LLM Ranks** from the implementation
brief.

**Why not server-side scoring?**  
A keyword-match scoring algorithm needs synonym maps, category-expansion
tables, and tuned weights to produce useful results. It's a mini NLP pipeline
that the LLM already does natively. Server-side scoring that doesn't use ML
reliably degrades to alphabetical order — which is what the previous
`recommendTldsForKeywords` implementation did.

**What the server is responsible for:**
- Maintaining the TLD catalog with accurate `categories`, `audience`, pricing,
  and restriction flags.
- Applying deterministic structural filters (price cap, ccTLD exclusion, etc.)
  that the LLM shouldn't have to compute.
- Returning a stable, predictable candidate set.

**What the LLM is responsible for:**
- Matching the user's project description to the categories and audience.
- Ranking candidates and selecting 3–6 to pass to `bulkDomainSuggestions`.
- Explaining the selection to the user in natural language.

**What was removed:**  
`recommendTldsForKeywords` and `TldRecommendationService` — replaced by
`listCategorizedTlds` in `CatalogMCPServer`. The static TLD metadata map
(100+ entries) was also removed; all metadata now comes from the backend
catalog directly.

---

## Example

**User prompt:**  
> "I'm building a voice biomarker health app. No country domains, budget
> around $15/year."

**LLM calls:**
```
listCategorizedTlds(excludeCcTLDs=true, maxRegisterPrice=15)
```

**LLM reasoning against returned candidates:**
- `.bio` — categories: health, medical, pharma → strong match for "biomarker"
- `.app` — categories: mobile, software → relevant for a health app product
- `.com` — categories: generic, business → universal, always worth including
- `.health` — hasRestrictions: true → excluded by filter (good, user said casual)
- `.ai` — registrationPrice: $80 → excluded by price cap

**LLM calls:**
```
bulkDomainSuggestions(keywords=["voice","biomarker"], tlds=["com","app","bio"])
```

**LLM presents to user:**  
Specific available names with reasoning per name.
