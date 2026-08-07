# EnthusiaStaff workspace state

Last updated: 2026-08-07

Live GitHub state overrides stale records, but persistent package state must be published to `main`.

## Current routing

| Field | Value |
| --- | --- |
| Completed packages | `ES-P01 — Exact-sanction appeal isolation`; `ES-P03 — Bedrock identity correctness`; `ES-P04 — Staff-mode operational tools`; `ES-X05 — Website UX, authentication, and appeals` |
| Parked package | `ES-P02 — Runtime database recovery and Velocity reload` |
| Active implementation package | `ES-P05 — Report evidence and staff workflow completion` |
| ES-P05 branch | `package/es-p05-report-workflow` |
| ES-P05 starting main | `bf9b305ba96d9536f3d111c79eef674bd2e11dc5` |
| ES-P05 handoff | `ai-agents/reports/package-handoffs/2026-08-07-es-p05-report-workflow.md` |
| Other ready packages | `ES-P09 — Alt and network-identity completion` at priority 55; `ES-P10 — Cheat tester and fake-entity system` at priority 80; do not activate while ES-P05 is active |
| ES-P02 status | `BLOCKED` / `PARKED_BLOCKED`; PR #70; private Actions Billing & plans blocker unchanged |
| ES-P04 status | `COMPLETE` under an explicit owner-approved package-specific infrastructure exception for Pi staging |
| ES-P04 validated head | `15d9428eba454e9ae4a905752129bd18676acdb1` |
| ES-P04 merge | PR #79; normal merge `a530b992232a8a08cbbd13b0eed6606228ceb652` |
| ES-P04 hosted validation | Wiki `31178353549` / `92865432750` success; Coverage `31178353504` / `92865439305` success on Java 21 including build/tests/coverage/runtime-JAR inspection; Codacy static `92865800728` success with zero issues; Codacy variation `92867049954` and diff coverage `92867049338` success; zero valid unresolved review threads |
| ES-P04 staging disposition | **OWNER-APPROVED INFRASTRUCTURE EXCEPTION — PI STAGING SKIPPED/DEFERRED**; private staging `31178359804`, Ubuntu build `92865456267` runner ID `0`, steps `[]`, Billing & plans failure; Pi `92865494913` skipped. This is not a staging pass. |
| ES-P04 internal follow-up | When the private Actions billing/runner path is available, rerun the ES-P04 Pi boot/restart staging against the merged behavior and record the result. This deferred check does not reopen ES-P04 unless it exposes a real defect. |
| Migration boundary | immutable V17 at ES-P05 start |
| Production boundary | issue #43 remains open and deferred; LiteBans remains authoritative |
| Parallel documentation branch | `docs/wiki-maintenance-2026-08` exists separately, has no package authority, and is not being modified by the ES-P05 worker |

## ES-P05 selection evidence

- Legitimate `main` at selection: `bf9b305ba96d9536f3d111c79eef674bd2e11dc5`.
- Live open PRs: only PR #70 for ES-P02; it remains parked on the unchanged private Actions Billing & plans blocker and therefore is not an actionable continuation.
- Live branches at selection: `main`, parked `package/es-p02-runtime-db-recovery`, and separate documentation branch `docs/wiki-maintenance-2026-08`; no ES-P05 branch or competing package branch existed.
- Dependencies ES-P03 and ES-P04 are complete. ES-P05 was the lowest-priority-number dependency-complete READY package, ahead of ES-P09 and ES-P10.
- Included scope: provider-independent report submission, durable cooldown/merge/duplicate behavior, queue/detail and text fallback, notes/status revision safety, bounded chat/coordinate/client evidence, retention/privacy, attachment boundary decision, restart/concurrency proof, documentation and validation.
- Preserved exclusions: RoseChat private-message evidence remains ES-X01; Discord route delivery remains ES-P06; production evidence/routes and issue #43 remain excluded.
- No prior ES-P05 package handoff existed.

## ES-P04 completion evidence retained

- Starting legitimate aggregate `main`: `5c820c29c2fe5a498ea7f80454579953ac05b436`.
- ES-P04 was selected ahead of ES-P09 after ES-P02 remained parked on its unchanged external blocker; existing PR #79 was resumed rather than duplicated.
- Scope completed: authenticated staff-mode tools for random teleport, inspector, freeze, reports, follow/spectate, vanish, staff chat, and tools menu; stale/spoofed/session/rank/slot/material checks; Folia-safe target sampling; cooldowns; durable command/service reuse; Bedrock command/text fallbacks; restoration/reconnect/reload/shutdown/rank-change safety.
- Explicit exclusions were preserved: Cheat Tester/fake entities remain ES-P10; fake bases remain ES-P11.
- Review fixes prevent deferred Cheat Tester issuance, cover inclusive cooldown boundaries, correct follow/spectate feedback, and resolve Codacy test-quality findings. Every valid review thread was resolved; scheduler-helper consolidation was withdrawn as optional maintainability follow-up.
- Frozen head `15d9428eba454e9ae4a905752129bd18676acdb1` passed all ordinary exact-head gates. The private staging route did not execute product code because GitHub rejected the required private Ubuntu build before runner allocation under Billing & plans.
- On 2026-08-07 the owner explicitly authorized ES-P04 to continue despite that infrastructure condition, with the Pi check marked skipped/deferred and an internal requirement to run it later when available. The exception is package-specific and does not relabel the failed/skipped staging evidence as a pass.
- PR #79 merged normally as `a530b992232a8a08cbbd13b0eed6606228ceb652`; the merge commit has validated head `15d9428e...` as its package parent.

## Next routing

ES-P05 is active. This worker must complete or correctly park ES-P05, persist its state, and stop. It must not activate ES-P09, ES-P10, ES-P06, or any other package in this session.

## Safety boundaries

No production credentials, punishment/player records, raw addresses, private databases, production routes, deployment, Flyway repair/history rewrite, LiteBans removal, issue #43 acceptance, production migration, shadow window, cutover, ES-V02 execution, or authority activation is authorized.
