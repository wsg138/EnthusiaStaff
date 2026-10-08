# Policy v2 — real-world blackmail evidence gate (fail closed)

Tracking: [owner scope #468](https://github.com/wsg138/EnthusiaStaff/issues/468), [Policy v2 #355](https://github.com/wsg138/EnthusiaStaff/issues/355), candidate snapshot [PR #471](https://github.com/wsg138/EnthusiaStaff/pull/471).

## What is implemented

The Paper manual review workflow now has a required **independent** `PolicyV2RealWorldEvidenceGate` seam. All existing callers use the **unavailable** implementation, which refuses a positive real-world coercion claim. Merely clicking `real-world-leverage-verified = Yes` in the incident GUI **cannot** manufacture independent proof or reach the terminal review with an unconfigured verifier.

For `safety.blackmail-extortion`:

- A legacy owner snapshot without the two required fields is rejected for manual review, even if its resolver would otherwise issue a terminal ban.
- A complete candidate snapshot whose context is `game-only` / `uncertain`, or whose evidence flag is `false`, may reach the resolver's nonpunitive no-match/review path. The verifier is **never consulted** for these scopes. Game-only Minecraft leverage remains allowed; no punishment should be fabricated for it.
- Only `coercion-context=real-world` and `real-world-leverage-verified=true` calls the independent verifier with the *exact* reviewer UUID, subject UUID, incident time, full immutable `IncidentFinding` and candidate policy version. Its result must be true before review can continue. There is no default verifier that returns true.
- `submitShadow` re-evaluates the same gate before writing the shadow evaluation. If evidence has become invalid or unavailable, confirmation is rejected without recording.

## Important limits

This is **not evidence ingestion, persistent attestation, or approval**. It does **not** read private case attachments, store an evidence record, or perform an external verification. A future trusted adapter is required; it must resolve private durable evidence independently from staff selections, enforce authorized reviewer access and evidence provenance, pin the exact subject/finding/version/time, reject altered or revoked proof, and record an immutable private attestation with appropriate second-party review. Do **not** wire a `request -> true` placeholder in production.

The helper is non-authoritative while Policy v2 is disabled. The runtime modes remain DISABLED and SHADOW; Policy v1 still governs live punishments. The new candidate owner policy is stacked in PR #471 and is **not** selected. Neither adding this gate nor passing its unit tests permits Policy v2 cutover.

## Acceptance for a future provider

- Private evidence source and authorized independent reviewer both persist before severe outcome review; evidence checks are read-only and avoid leaking private refs to public outputs.
- Same evidence cannot be reattached to an unrelated subject or different offense, version or time; expiry/revocation is honored between initial review and submit.
- No trusted adapter / unavailable evidence / legacy unscoped rule -> fail closed without a punitive recommendation.
- Case revision, approval, persistence, privacy and appeal state need an additional durable integration proof; current gate is only the first manual-workflow boundary.
