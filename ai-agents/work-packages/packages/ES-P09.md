# `ES-P09` — Alt and network-identity completion

## 1. Package identity
`ES-P09`; Internal; primary `COMP-STAFF`; priority 55; conditionally parallel after identity correctness.

## 2. Status
`ACTIVE` — selected by the 2026-08-07 sequential package worker after live reconciliation. `ES-P02` and `ES-P05` remain parked on the unchanged private Actions Billing & plans blocker; no competing ES-P09 branch, PR, or handoff existed.

## 3. Objective
Complete protected network identity, alt graph/confidence/manual relationships, IP/network sanction inheritance, privacy, retention, and operator workflow.

## 4. Why the package exists
The audit found low-proof graph/storage and false-positive behavior despite protected-token foundations and active network sanctions.

## 5. Included audit IDs
`AUD-ALT-001`, `AUD-ALT-002`, `AUD-ALT-003`, the alt graph/confidence/ambiguity/manual-relationship/protected-network-identity/retention/sanction-inheritance fields of `AUD-ALT-004`, and relevant `AUD-SEC-002`.

## 6. Included behavior
Protected address token/key lifecycle; identity graph/confidence and ambiguity controls; manual relationships/exceptions; `/alts` and `/alt` workflow; sanction inheritance and audit; bounded retention/purge/restart; safe logging; migration interaction. Canonical Java/Floodgate platform identity and required normalization are owned by `ES-P03` and consumed here rather than reimplemented.

## 7. Explicit exclusions
Canonical Java/Floodgate platform identity and normalization (`ES-P03`); production/private address datasets; production key rotation; acceptance/false-positive campaign (`ES-V02`).

## 8. Dependencies
`ES-P03` must be `COMPLETE`; satisfied by merge `b960e91ea59627a870ff24f89c2f761d0cbb68ab` and canonical completion publication.

## 9. Component and repository boundaries
Root identity/alt/persistence/Velocity/tests/docs only. No external source import, permanent component branch, or isolated PR.

## 10. Required branches
Active temporary branch `package/es-p09-alt-network-identity`, created from legitimate `main` commit `ec88d4a4e30fac4acd6d06a60e67e27fed057bd7`. Integration authority: this branch and its single same-repository PR to `main`. Delete after verified merge containment when tooling permits.

## 11. Required PRs
One PR to `wsg138/EnthusiaStaff:main`: PR #84.

## 12. Implementation checklist
Reconcile data flows and the `ES-P03` handoff; model graph/confidence/manual exceptions/retention; implement commands and inheritance safety; test privacy/races/restart; update docs/state/handoff; review/freeze/validate/merge/cleanup.

## 13. Acceptance criteria
Raw addresses never persist/log; deterministic protected tokens and key-version handling work; ambiguous links cannot silently inherit sanctions; manual decisions are authorized/audited; queries/retention are bounded; restart and concurrent proxies are safe; canonical platform identity is consumed without redefining it.

## 14. Test requirements
Protector/key-version, graph confidence, ambiguity/manual exception, multi-proxy duplicate input, sanction inheritance, retention/purge, restart, migration interaction, authorization and redaction tests.

## 15. Static-analysis requirements
Configured Java/static-analysis/review-bot gates; zero valid unresolved findings.

## 16. Documentation requirements
Privacy model, commands/permissions, confidence/ambiguity/manual workflows, keys/retention, the field-level handoff from `ES-P03`, troubleshooting, Wiki, registry/handoff.

## 17. Security and privacy requirements
Never expose raw/reversible addresses; keys from environment/secret store; least-privilege views; bounded retention; fail closed on key/version uncertainty.

## 18. Migration impact
No schema change was required; V17 remains current and immutable.

## 19. Bedrock considerations
Consume corrected platform identity from `ES-P03`; ensure prefixed names do not change network identity semantics; acceptance remains `ES-V02`.

## 20. Distributed-runtime considerations
Multiple proxies, reconnect, duplicate observations, ordering, inheritance races, process restart, and retention jobs must be safe.

## 21. External-provider considerations
No external source provider required; integrate only through verified Floodgate/runtime identity boundaries.

## 22. Completion definition
Development scope proven and merged in one exact-head PR with zero valid threads and branch cleanup; private false-positive/staging acceptance remains deferred.

## 23. Resume state
Claimed 2026-08-07 by `ChatGPT sequential package worker`. Start `main`: `ec88d4a4e30fac4acd6d06a60e67e27fed057bd7`. Branch: `package/es-p09-alt-network-identity`. No pre-existing ES-P09 work was taken over.

## 24. Last completed checkpoint
Implementation, direct tests, Wiki/privacy documentation, and the first CodeRabbit pass are complete. CodeRabbit's valid retention finding was fixed by removing cleanup from the observation transaction and scheduling it from an injected trusted UTC runtime clock behind the authority fence. PR #84 is ready for review and final exact-head gates are running.

## 25. Remaining checklist
Exact-head review/static/hosted validation, terminal state publication, normal merge/containment verification, and temporary branch cleanup remain.

## 26. Known blockers
No dependency blocker. Production/private representative network data and false-positive acceptance remain intentionally unavailable and deferred to `ES-V02`; this does not block development-package completion. Private staging remains unavailable under the known Billing & plans zero-runner condition and is not a pass.

## 27. Final evidence
In progress. Claim base `ec88d4a4e30fac4acd6d06a60e67e27fed057bd7`; branch `package/es-p09-alt-network-identity`; PR #84.

## 28. Merge and synchronization record
PR #84 is open and ready for review; no merge yet. Base at selection: `ec88d4a4e30fac4acd6d06a60e67e27fed057bd7`. Current head is the live PR head and must be frozen only after review fixes finish; exact-head validation follows that freeze.
