# `ES-P09` — Alt and network-identity completion

## 1. Package identity
`ES-P09`; Internal; primary `COMP-STAFF`; priority 55; conditionally parallel after identity correctness.

## 2. Status
`COMPLETE` — implementation PR #84 merged normally on 2026-08-07 as `a88201524690848f778297f140f7ee2ba5b6ce36`. Frozen reviewed/validated implementation head: `2ed33d9f36ec9e5583a030b63feb9eb935c5ccdb`.

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
Temporary implementation branch `package/es-p09-alt-network-identity` was created from legitimate `main` commit `ec88d4a4e30fac4acd6d06a60e67e27fed057bd7`, merged through PR #84, and automatically deleted by GitHub after verified containment.

## 11. Required PRs
One implementation PR to `wsg138/EnthusiaStaff:main`: PR #84, merged normally as `a88201524690848f778297f140f7ee2ba5b6ce36`. A documentation-only post-merge finalization PR publishes terminal package state.

## 12. Implementation checklist
Complete. Data flows and ES-P03 handoff were reconciled; graph/confidence/manual exceptions and retention were hardened; inheritance safety and privacy were implemented; direct concurrency/restart/privacy tests were added; Wiki/state/handoffs were updated; review findings were resolved; exact-head hosted/static validation passed; implementation merged normally; containment and branch cleanup were verified.

## 13. Acceptance criteria
Satisfied for the development package: raw addresses do not persist/log through this surface; deterministic protected tokens and key-version handling are covered; ambiguous links cannot silently inherit sanctions; manual decisions remain authorized/audited; queries/retention are bounded; restart and concurrent-proxy cases are covered; canonical platform identity is consumed without redefining it. Private representative-network false-positive/staging acceptance remains assigned to `ES-V02` by contract.

## 14. Test requirements
Covered by unit and direct MariaDB/Testcontainers tests for protector/key-version behavior, graph confidence, ambiguity/manual decisions, multi-proxy duplicate input, sanction inheritance and exact expiry, retention/purge/restart, authority fencing, and raw-address redaction/rejection. Existing migration checks remained green; no schema migration was added.

## 15. Static-analysis requirements
Satisfied on frozen head `2ed33d9f36ec9e5583a030b63feb9eb935c5ccdb`: Codacy check `92917176627` completed `success` with zero annotations. Repository CodeRabbit status was `success`; all four actionable review threads were resolved/outdated and zero valid unresolved threads remained. A later incremental CodeRabbit pass was quota-limited and is not represented as review evidence.

## 16. Documentation requirements
Satisfied. Privacy model and alt-investigation guidance were updated in the repository-managed Wiki, and package/registry/handoff records were reconciled.

## 17. Security and privacy requirements
Satisfied for development scope: no staff-facing/raw-address lookup was introduced; manual reason text rejects raw IPv4/IPv6 literals before audit persistence; protected equality/encryption tokens remain the identity representation; retention is bounded and uses a trusted runtime clock; manual decisions are preserved; key/version uncertainty remains fail-closed.

## 18. Migration impact
No schema change was required; V17 remains current and immutable.

## 19. Bedrock considerations
Consumes corrected platform identity from `ES-P03`; prefixed names do not redefine network identity semantics. Distributed Java/Bedrock acceptance remains `ES-V02`.

## 20. Distributed-runtime considerations
Bounded matching, simultaneous-online ambiguity, evidence throttling, concurrent duplicate observations, sanction idempotency, restart persistence, and authority-fenced retention are directly covered. Representative multi-runtime staging remains `ES-V02`.

## 21. External-provider considerations
No external source provider required; integration remains through verified Floodgate/runtime identity boundaries.

## 22. Completion definition
Met for ES-P09 development scope. PR #84 merged from exact reviewed/validated head, zero valid review threads remained, `main` contains the implementation head with zero file divergence, and the temporary package branch was deleted. Private false-positive/staging acceptance remains deferred to `ES-V02` and is not called passed.

## 23. Resume state
Terminal. Claimed 2026-08-07 by `ChatGPT sequential package worker`; start `main` `ec88d4a4e30fac4acd6d06a60e67e27fed057bd7`; frozen head `2ed33d9f36ec9e5583a030b63feb9eb935c5ccdb`; merge `a88201524690848f778297f140f7ee2ba5b6ce36`.

## 24. Last completed checkpoint
Post-merge verification complete. `main` at `a88201524690848f778297f140f7ee2ba5b6ce36` has `2ed33d9f36ec9e5583a030b63feb9eb935c5ccdb` as the implementation parent; compare reports `ahead_by=1`, `behind_by=0`, and no file differences. The temporary implementation branch no longer exists.

## 25. Remaining checklist
No ES-P09 development work remains. `ES-V02` retains private representative-network false-positive and distributed Java/Bedrock staging acceptance. This worker must not select another package.

## 26. Known blockers
No ES-P09 development blocker remains. The private staging path is still externally unavailable because of the GitHub Actions Billing & plans restriction; this is not a pass. On exact frozen head, public wrapper run `31193762319` dispatched private run `31193769314`; required Ubuntu build job `92916864019` received runner ID `0`, empty runner name, and steps `[]`; Pi job `92916876057` was skipped. This unavailable private acceptance is assigned to `ES-V02`, not used to claim staging success.

## 27. Final evidence
- Frozen implementation head: `2ed33d9f36ec9e5583a030b63feb9eb935c5ccdb`.
- Wiki/package validation: run `31193764800`, job `92916829444`, `success`.
- Java 21 build/tests/MariaDB-Testcontainers/aggregate JaCoCo/runtime-JAR inspection: run `31193765341`, job `92916907616`, `success`; all build/test/JAR/coverage steps completed successfully and no failed-test diagnostics ran.
- Codacy static analysis: check `92917176627`, `success`, zero annotations.
- Review: repository CodeRabbit status `success`; four actionable threads resolved/outdated; zero valid unresolved threads at merge.
- Private staging: **NOT A PASS**. Public wrapper `31193762319`; private run `31193769314`; Ubuntu job `92916864019` runner ID `0`, empty runner name, steps `[]`, Billing & plans rejection; Pi `92916876057` skipped. Deferred to `ES-V02` by package scope.
- Normal merge: PR #84 -> `a88201524690848f778297f140f7ee2ba5b6ce36`.
- Containment: implementation head is a parent of the merge; compare from implementation head to merge is ahead by one with no file differences.
- Cleanup: `package/es-p09-alt-network-identity` is absent after merge.

## 28. Merge and synchronization record
PR #84 merged normally into primary `main` on 2026-08-07 as `a88201524690848f778297f140f7ee2ba5b6ce36` from exact head `2ed33d9f36ec9e5583a030b63feb9eb935c5ccdb`. Post-merge `main` containment is exact and the temporary implementation branch is deleted. Terminal package state is being published through a documentation-only finalization PR; no product content changes after the frozen implementation head.