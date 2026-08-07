# Latest AI handoff

Current package handoff:

[`2026-08-07-es-p10-cheat-testers.md`](../package-handoffs/2026-08-07-es-p10-cheat-testers.md)

Canonical package registry:

[`PACKAGE-REGISTRY.md`](../../work-packages/PACKAGE-REGISTRY.md)

`ES-P10 — Cheat tester and fake-entity system` is `MERGE_PENDING` on PR #86 / `package/es-p10-cheat-testers`.

The package was selected automatically from legitimate `main` `83302749b3247f7a05157f1625fc99da6aa43736` after fresh live reconciliation. ES-P02 PR #70 and ES-P05 PR #81 remain `BLOCKED` / `PARKED_BLOCKED` on the unchanged private Actions Billing & plans zero-runner condition; neither was resumed.

ES-P10 now implements `AUD-TESTER-001` and `AUD-TESTER-002`: the real authorized Cheat Tester staff tool and `/cheattester` fallback, Totem refill / No-fall / Velocity / Auto-armor release probes, exact temporary target-state restoration, immutable V18 recovery journaling, global active-target fencing, durable inventory-lock participation, bounded evidence/audit, target/staff-scoped client-side fake entities, target-only suspect interaction evidence, fail-closed ProtocolLib behavior, lifecycle recovery, configuration, tests, and Wiki/operator documentation. Fake bases remain `ES-P11`; no automatic punishment or production use is authorized.

Harsh review fixed the pre-mutation cancellation/journal race and staff-observer evidence contamination. Static-analysis findings were resolved structurally by splitting session, probe, evidence, mutation-guard, snapshot, command/configuration, and persistence responsibilities.

Private Pi/distributed staging attempts remain **NOT A PASS** when the private required Ubuntu job receives runner ID `0`, empty runner name, and no steps and Pi is skipped. ES-P10's contract assigns representative distributed Java/Bedrock acceptance to `ES-V02`, so unavailable private staging is recorded without being relabeled as success or as an executed product failure.

Remaining work is package-finalization only: validate the frozen exact PR head with hosted/static/coverage/review gates and zero valid threads, merge PR #86 normally, verify containment, delete the implementation branch, publish terminal `COMPLETE` state, recompute dependency-derived readiness without activating another package, and stop.