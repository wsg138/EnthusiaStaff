# ES-P07 — Inventory and Ender editing runtime completion

Status: `BLOCKED` / `PARKED_BLOCKED`.

## Selected work

ES-P07 was selected from `main` `17fb50d02fdc35cffd1cbdc63e28f72cffd88315` after live reconciliation found no actionable continuation; ES-P07 was the lowest-priority-number dependency-complete READY package (priority 45). Implementation branch: `package/es-p07-inventory-runtime`. Implementation PR: #112. Frozen reviewed product/tracking head: `b34aade6ae79c7aaada0ada3c87970f937b6db6a`.

## Completed package work

The PR completes exact dirty-slot inventory/Ender writes, complete dirty-slot-set prevalidation, 32 MiB aggregate snapshot bounds, idempotent same-owner APPLYING lease replay, additional login/recovery mutation guards, direct command/GUI/permission wiring coverage, MariaDB/Testcontainers lease coverage, Wiki updates, and the required CodeRabbit/Codacy fixes. V1–V18 remain immutable; ES-P07 adds no migration.

Exact-head hosted/static evidence on `b34aade6ae79c7aaada0ada3c87970f937b6db6a` is green: Validate Wiki `31426025633`; Coverage `31426025143` / job `93577825964`; validation artifact `9077401417`, digest `sha256:6c9954d49d8f617d1565cc20f38c8280b4f4e20b1fd725589fa4d14ebe654a19`; Java 21 full tests and MariaDB/Testcontainers passed; runtime JAR inspection found zero provider API leaks; aggregate coverage is 47.14% lines / 38.24% branches / 49.80% instructions. Codacy static is success with zero issues; diff coverage is 31.58%; coverage variation is -0.04% against the -1.0% limit. CodeRabbit exact-head status is success and all review threads are resolved/outdated.

Sentinel artifact run `31426025081` / job `93577830080` passed and produced exact-head artifact `9077240364`, digest `sha256:37710e09e2cac75bb30e2ab69297331654188a6b7fb2dc538c741fa94b7ee832`.

Canonical Pi public run `31426022983` built the exact trusted runtime successfully and dispatched correlated private run `31426646043` in `wsg138/EnthusiaStaff-Staging`.

## External blockers

The required runtime gates cannot currently execute to completion:

1. Canonical private Pi staging run `31426646043`, job `93579820065`, remains queued without a runner assignment after the prior Sentinel Foundation deployment completed. No private Paper/MariaDB/Flyway cycle has executed, so canonical Pi is not passed.
2. After the Foundation deployment materially changed host state, exact-head Sentinel restart job 75 was legitimately re-requested. It remains queued because the Pi reports only 120 MB available memory against the required 700 MB and temperature 82.3 C at/above the 80.0 C maximum. No `PAPER_RESTART_OK` exists.

These are external infrastructure conditions, not product failures. Do not merge PR #112, do not call either runtime gate passed, and do not repeatedly rerun the same blocked gates without evidence that runner availability or host memory/temperature changed.

## Exact unblock condition

Resume ES-P07 as `ACTIONABLE_CONTINUATION` before new package work when either the already-correlated private staging run receives a trusted runner or equivalent runner availability materially changes, and when Sentinel host telemetry materially clears its resource gate. Final merge still requires the unchanged frozen head to receive both canonical private Pi success/cleanup and terminal Sentinel `PAPER_RESTART_OK`; if any run becomes stale/cancelled/wrong-head, rerun the canonical exact-head gate rather than reusing old evidence.

## Boundaries

Preserve PR #112 and `package/es-p07-inventory-runtime`. ES-P06 remains READY but must not be started by this worker. ES-P08 remains dependency-blocked by ES-P07. ES-X01 remains PARKED_BLOCKED on its unresolved supported RoseChat integration repository/source contract. Issue #43 remains deferred and LiteBans authoritative. No production deployment, data, shadow, cutover, authority change, source rewrite, or Flyway repair occurred.
