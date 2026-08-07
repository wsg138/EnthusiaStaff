# Latest AI handoff

Current package handoff:

[`2026-08-07-es-p10-cheat-testers.md`](../package-handoffs/2026-08-07-es-p10-cheat-testers.md)

Canonical package registry:

[`PACKAGE-REGISTRY.md`](../../work-packages/PACKAGE-REGISTRY.md)

`ES-P10 — Cheat tester and fake-entity system` is `ACTIVE` on `package/es-p10-cheat-testers`.

The package was selected automatically from legitimate `main` `83302749b3247f7a05157f1625fc99da6aa43736` after fresh live reconciliation. ES-P02 PR #70 and ES-P05 PR #81 remain `BLOCKED` / `PARKED_BLOCKED`: private staging run `31196247124` again produced required Ubuntu build `92925059857` with runner ID `0`, empty runner name, and no steps, while Pi `92925074453` was skipped. Their external blocker therefore remains unchanged and neither package was resumed.

ES-P04 is `COMPLETE`, so ES-P10 was the dependency-complete eligible `READY` package with the lowest numerical priority. No competing ES-P10 branch, PR, or handoff existed before the claim.

ES-P10 includes `AUD-TESTER-001` and `AUD-TESTER-002`: authorized cheat tester workflow, the four release tester types, exact temporary target-state restoration, bounded evidence/audit, client-side fake-entity tooling and interaction capture, deterministic lifecycle cleanup, commands/staff-tool controls, configuration, and tests. Fake bases remain `ES-P11`; no automatic punishment or production use is authorized.

ProtocolLib 5.4.0 is the supported packet boundary already present in the Paper build and will remain isolated behind an optional fail-closed adapter. V17 is the current immutable migration boundary; no schema change is assumed. Representative distributed Java/Bedrock acceptance remains assigned to `ES-V02`.

This worker must complete or correctly park ES-P10, publish its durable terminal state, recompute dependency-derived statuses without activating another package, and stop.