# Latest AI handoff

Current package handoff:

[`2026-08-07-es-p10-cheat-testers.md`](../package-handoffs/2026-08-07-es-p10-cheat-testers.md)

Canonical package registry:

[`PACKAGE-REGISTRY.md`](../../work-packages/PACKAGE-REGISTRY.md)

`ES-P10 — Cheat tester and fake-entity system` is `MERGE_PENDING` on PR #86 / `package/es-p10-cheat-testers` and is the sole package owned by this sequential worker.

Live reconciliation from legitimate `main` `83302749b3247f7a05157f1625fc99da6aa43736` found PR #86 and the ES-P10 package branch already advancing the package. Its live head still had exact-head Codacy size and coverage-variation failures plus unresolved CodeRabbit findings, so ES-P10 was resumed as the highest-priority `ACTIONABLE_CONTINUATION`, not claimed as a new package. ES-P02 PR #70 and ES-P05 PR #81 remain `BLOCKED` / `PARKED_BLOCKED` on the unchanged private Actions Billing & plans zero-runner condition; neither was resumed.

ES-P10 implements `AUD-TESTER-001` and `AUD-TESTER-002`: the real authorized Cheat Tester staff tool and `/cheattester` fallback, Totem refill / No-fall / Velocity / Auto-armor release probes, exact temporary target-state restoration, immutable V18 recovery journaling, global active-target fencing and cross-backend login recovery, durable inventory-lock participation, bounded evidence/audit, target/staff-scoped client-side fake entities, target-only suspect interaction evidence, fail-closed ProtocolLib provider/cleanup behavior, lifecycle recovery, strict configuration, tests, and Wiki/operator documentation. Fake bases remain `ES-P11`; no automatic punishment or production use is authorized.

Continuation review reconciled every live review thread and fixed the still-valid issues: strict nested config keys; excessive recovery polling; tagged-tool damage leakage; status permission/staff-mode privacy; missing inventory click/drag guards; non-finite settings; snapshot prevalidation ordering; ProtocolLib runtime/linkage/cleanup failure semantics; stale delayed fake spawns; cross-backend durable recovery; stable tester persistence IDs; mapper-backed audit JSON; V18 payload sizing; and the prior static-size/direct-coverage gap through focused extraction and tests. Earlier cancellation/journal-race and controlling-staff evidence fixes remain intact. Zero valid unresolved review threads remained at the state checkpoint.

Exact-head private staging child run `31223475992` is **NOT A PASS**: required Ubuntu build `93012838924` received runner ID `0`, empty runner name, and `steps: []`; Pi job `93012869273` was skipped. ES-P10's contract assigns representative distributed Java/Bedrock acceptance to `ES-V02`, so unavailable private staging is recorded without being relabeled as success or as an executed product failure.

Remaining work is package-finalization only: validate the final unchanged PR head with every applicable hosted Java/build/test/MariaDB/Testcontainers/migration/runtime-JAR/Wiki/static/coverage/review gate and zero valid threads, merge PR #86 normally, verify containment, delete the implementation branch, publish terminal `COMPLETE` state, recompute dependency-derived readiness without activating another package, and stop.