# `ES-P03` — Bedrock identity correctness

## 1. Package identity
`ES-P03`; Internal; primary `COMP-STAFF`; priority 30; sequential identity-correctness package.

## 2. Status
`ACTIVE` under the owner-directed 2026-08-06 routing exception recorded below. Implementation and review repairs are complete; exact-head hosted validation and final external review remain.

## 3. Objective
Persist and resolve Java and Floodgate/Bedrock identity correctly across Paper, Velocity, moderation, and network identity.

## 4. Why the package exists
The audit confirmed unconditional Java platform persistence and related offline/name-history risks that affect later staff, reports, alts, and staging.

## 5. Included audit IDs
`AUD-ID-001`, `AUD-ID-004`, and only the canonical Java/Floodgate platform-identity and normalization fields of `AUD-ALT-004`.

## 6. Included behavior
Replace unconditional Java persistence with proof-bearing Java/Bedrock/unknown observations; preserve Floodgate UUID, `*`-prefixed name, current-name, and history semantics; correct join, mute, proxy/backend, offline target, and the canonical platform/normalization fields consumed by alt records; add regression tests. Alt graph, confidence, ambiguity, manual relationships, protected network identity, and sanction-inheritance semantics belong to `ES-P09`.

## 7. Explicit exclusions
Alt graph/confidence/ambiguity/manual-relationship/inheritance completion (`ES-P09`); changing Floodgate rules; claiming live Bedrock acceptance; unrelated identity redesign.

## 8. Dependencies and owner-directed routing
The ordinary dependency requires `ES-P02` to be `COMPLETE`; ES-P02 remains `BLOCKED` / `PARKED_BLOCKED`. On 2026-08-06 the repository owner explicitly directed the sequential worker to continue another productive package while leaving `ES-P02` and `ES-X05` parked until GitHub-hosted runners recover. Because no other ordinary implementation package was dependency-complete, this instruction is recorded as a narrow routing exception permitting `ES-P03` implementation from current legitimate `main` while preserving ES-P02 PR #70 and its branch untouched. It does not mark ES-P02 complete, import unmerged ES-P02 work, waive ES-P03 validation, or authorize later packages automatically. Any merge conflict or behavioral dependency exposed by later ES-P02 integration must be reconciled through the owning package before release acceptance.

## 9. Component and repository boundaries
Root identity/runtime/persistence/tests/docs only. No external source import, permanent component branch, or isolated PR.

## 10. Required branches
Temporary `package/es-p03-bedrock-identity`, created from exact legitimate `main` `345b7bbcec6facb45c7f96b0e6e181ac7a38e1da`; delete after verified merge containment.

## 11. Required PRs
One PR to `wsg138/EnthusiaStaff:main`: PR #75.

## 12. Implementation checklist
- [x] Reconcile live state, both parked PRs, branches, review state, issue #43, migration V17, and current default head.
- [x] Record owner-directed routing exception without changing ES-P02 or ES-X05.
- [x] Trace every platform write/read and every current/historical name path.
- [x] Implement verified Java/Bedrock/unknown observation and `*`-alias handling.
- [x] Preserve known Bedrock identity against weaker later observations and repair legacy unconditional-Java observations safely.
- [x] Order unequal and equal timestamp identity/presence observations deterministically and protect same-time connections from disconnect races.
- [x] Treat SQL wildcard characters literally in validated exact-prefix searches.
- [x] Test Java, Bedrock, history, offline resolution, prefix search, duplicate updates, reconnect/order races, equal-time ties, and unavailable/incompatible Floodgate behavior.
- [x] Update operator/developer documentation and durable state.
- [x] Harshly review the complete diff and repair every valid correctness/static-analysis finding found so far.
- [ ] Freeze and exact-head validate all applicable gates.
- [ ] Merge normally, verify containment, clean the branch, publish `COMPLETE`, and stop.

## 13. Acceptance criteria
Every persisted player platform derives from verified runtime evidence or is explicitly `UNKNOWN`; Java and Bedrock records remain stable across server changes/reconnects; name/UUID resolution is deterministic and privacy-safe; no Java-only fallback corrupts Bedrock records; `*`-prefixed Bedrock current/history lookup remains supported; equal-time observations do not depend on database arrival order; the handoff to `ES-P09` is preserved.

## 14. Test requirements
Domain/Paper tests and MariaDB integration tests for join/mute/directory/network identity normalization, historical names, `*` aliases, verified versus unverified proxy observations, duplicate and out-of-order updates, deterministic equal-time ties, literal underscore prefixes, restart-relevant persistence, and unavailable/incompatible Floodgate behavior.

## 15. Static-analysis requirements
Java 21 warnings-as-errors and all configured analysis/review bots with zero valid unresolved findings.

## 16. Documentation requirements
Update identity/Bedrock/operator/developer docs and package registry/handoff; record the field-level ownership boundary with `ES-P09`; do not claim representative client acceptance.

## 17. Security and privacy requirements
No raw addresses or player rows in evidence; protected identity storage remains fail-closed; prevent identity spoofing and cross-platform collision; do not infer Bedrock solely from untrusted username text.

## 18. Migration impact
Current highest migration is immutable V17. No migration is required; V1–V17 remain unchanged.

## 19. Bedrock considerations
This package owns implementation correctness; `ES-V02` owns representative Java/Bedrock staging acceptance.

## 20. Distributed-runtime considerations
Account for multiple proxies/backends, server switching, duplicate directory writes, reconnect, equal and out-of-order observations, and eventual consistency.

## 21. External-provider considerations
Use the supported Floodgate API shape already present in the repository; explicit safe behavior when Floodgate/Geyser is absent, unavailable, or incompatible. Do not invent a provider contract.

## 22. Completion definition
All included behavior/tests/docs pass on one exact reviewed head; one PR merges normally; containment and branch cleanup are verified; no live-staging claim is made.

## 23. Resume state
PR #75 is ready for review on branch `package/es-p03-bedrock-identity`; starting `main` `345b7bbcec6facb45c7f96b0e6e181ac7a38e1da`. Valid CodeRabbit findings were repaired on the package branch; freeze the synchronized head for exact validation.

## 24. Last completed checkpoint
Implemented the evidence boundary, alias/history handling, deterministic timestamp and equal-time ordering, stale/equal disconnect protection, literal prefix search, regression tests, and documentation. Review repairs include the proxy-hosted provider fallback, dependency/readiness record consistency, equal-time ordering, and SQL `LIKE` escaping.

## 25. Remaining checklist
Freeze the final synchronized head; require zero new Codacy findings, successful applicable GitHub Actions on the exact head, zero valid unresolved review findings, normal merge, containment, branch cleanup, and final publication.

## 26. Known blockers
No source blocker identified. Ordinary hosted exact-head validation is mandatory. Representative Bedrock acceptance remains intentionally deferred to `ES-V02`.

## 27. Final evidence
Pending exact-head validation. Record the frozen SHA, workflow run/job IDs, Java version, tests, migration boundary, artifact integrity, analysis, review threads, and affected platform-write inventory.

## 28. Merge and synchronization record
Pending. External parity is not applicable. Intended post-merge terminal state: `COMPLETE` after normal merge, containment, safe branch cleanup, and persistent final publication.

## 29. Canonical handoff
[`2026-08-06-es-p03-bedrock-identity.md`](../../reports/package-handoffs/2026-08-06-es-p03-bedrock-identity.md)
