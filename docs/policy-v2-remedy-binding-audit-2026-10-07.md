# Policy v2 — typed remedy coverage and remaining authority gates

Tracking: [#446](https://github.com/wsg138/EnthusiaStaff/issues/446) / [#355](https://github.com/wsg138/EnthusiaStaff/issues/355)  
Reconciled: 2026-10-07, `main` at `5f3f1e5b8a78aa91ba4df4f4845840a4e8ad18d1`  
Status: **review/audit only; Policy v2 remains disabled, Policy v1 stays authoritative**.

## What is already implemented

- Merged #452 implements optional versioned binding metadata, policy-load validation, and a pure resolver from pinned `RemedySpec` and stored `IncidentFinding`; old unbound historical remedies remain readable.
- Open #455 publishes `owner.2026-10-07.2` while retaining the original immutable `.1` snapshot. It adds typed bindings to supported remedies and restores baseline remedy requirements omitted from higher history tiers.
- Open #457 adds `PolicyV2RemedyService.registerConfigured` and stops caller-supplied condition override for a bound remedy, but still needs an authoritative subject-ID fence.
- Profile-component observations remain separately tracked under #453; an empty unreliable observation map must never satisfy a correction.

## Snapshot-by-snapshot audit

The bundled `.1` snapshot on `main` has **85 owner offenses, 59 remedy occurrences, 12 distinct remedy IDs, and no enforcement metadata**. The proposed `.2` snapshot in #455 has **85 owner offenses, 378 offense/rule action blocks, 199 remedy occurrences, 12 distinct IDs, 142 supported typed bindings, and 57 deliberately unbound `OTHER` entries**.

All **378 punitive action blocks** match `.1` by offense/rule without changed sanctions or review thresholds. The `.1` snapshot text is retained unchanged in #455 (apart from the manifest's new `active-version` selector); `mode: disabled` persists.

| Remedy ID | Proposed occurrences | Typed scope / condition | Authority status |
| --- | ---: | --- | --- |
| `remove-content` | 102 | CONTENT / MANUAL | Bound; durable trusted provider completion still required |
| `confiscate-illicit-value` | 36 | ASSET / MANUAL | Bound; durable committed confiscation evidence still required |
| `vpn-access` | 1 | NETWORK_ACCESS / VPN_APPROVAL | Bound; authority observation/registration proof pending |
| `correct-username` | 1 | NETWORK_ACCESS / USERNAME | Bound; explicit prohibited-name finding attribute |
| `correct-skin` | 1 | NETWORK_ACCESS / PROFILE_COMPONENT | Bound; reliable profile observation blocked by #453 |
| `correct-profile` | 1 | NETWORK_ACCESS / PROFILE_COMPONENT | Bound; reliable profile observation blocked by #453 |
| `remove-disruption` | 12 | Unsupported `OTHER` | Technical-world cleanup/provider contract required |
| `safety-containment` | 19 | Unsupported `OTHER` | Explicit authorized safety action and evidence lifecycle required |
| `restore-protected-area` | 5 | Unsupported `OTHER` | Protected-area/world rollback provider and evidence required |
| `market-cleanup` | 1 | Unsupported `OTHER` | Market-owned provider and durable cleanup acknowledgement |
| `remove-extra-stall` | 5 | Unsupported `OTHER` | Market-owned stall removal/one-stall invariant provider |
| `remove-invalid-reputation` | 15 | Unsupported `OTHER` | Reputation-owned invalid-entry removal provider |

Totals: **199 = 142 typed + 57 unsupported**. These counts describe **open PR #455**, not currently merged `main`. The six `OTHER` IDs remain unbound **by design**; never map `OTHER` blindly to a generic W3B scope or mark it satisfied when a UI opens.

## Required follow-up: authority safety gates

1. **PR #455 static review:** Coverage, Sentinel Restart Artifact, and Staff state reset runtime proof passed on head `b865622b667bc9880e1c1641c9ff41a8a4c004ea`, but Codacy reports six medium alerts (1 ErrorProne, 1 Complexity, 4 Performance). Triage these alerts before merge; fix actionable issues or document justified false positives.
2. **PR #457 target identity:** Current `PolicyV2Store.CaseRecord` has no subject UUID and both registration commands accept caller-supplied `subjectId`. Before any authoritative registration, read the canonical target from the linked `cases.target_id` (as current history queries already do), or expose it through a verified canonical case read model. Reject missing/mismatched UUID in both `register` and `registerConfigured` **before** writing W3B rows. Add adversarial cross-subject tests.
3. **Explicit `OTHER` action design:** Assign provider ownership and distinguish completion acknowledgements from merely requesting an action. Where no reliable provider exists, define authorized manually verified tasks and a safe fail-closed lifecycle, then extend the typed allowlist deliberately. Do not collapse technical cleanup, evidence safety, Market cleanup, and reputation cleanup into one opaque `OTHER` binding.
4. **Authority-only registration wiring:** Connect stored case, versioned finding, and bound remedies to the existing W3B service only behind an explicit cutover guard. Disabled and shadow modes must make **zero** live provider calls or player-affecting decisions.
5. **Correction and retry proof:** Verify stable operation IDs; no second application on replay; stale observation cannot satisfy; legitimate correction satisfies durably; retries survive restart; overturned findings clear corresponding restrictions; prevent case/subject aliasing.
6. **Provider/end-to-end tests:** Prove actual content removal and confiscation completion, Market/reputation provider semantics, access-gate observations on Paper/Velocity, audit data integrity, and public/private projection isolation.
7. **Independent review and owner acceptance:** Shadow-compare against Policy v1, inspect disagreements, prove rollback, and obtain specific approval before any deployment, mode change, or authority cutover.

## Suggested bounded work split

- **A — Snapshot PR #455:** review Codacy findings and merge only once safe; do not add unrelated policy rule changes.
- **B — Target identity fence for PR #457:** narrow source/persistence integration and cross-subject Testcontainers regression coverage. Avoid GUI/policy YAML edits.
- **C — Unsupported-provider contract:** inventory authority/provider APIs for the six `OTHER` IDs; propose explicit typed scope and trusted completion lifecycle, with no premature runtime writes.
- **D — Profile observations #453:** investigate reliable observable components and normalization independently; unknown/unavailable always fails closed.
- **E — Cutover adversarial test suite:** after A–D land, prove shadow silence, provider idempotency, appeals/overturn, restart/retry, and privacy.

No production activation is authorized by completing any one of these stages.
