# `ES-P07` — Inventory and Ender editing runtime completion

## 1. Package identity
`ES-P07`; Internal; primary `COMP-STAFF`; priority 45.

## 2. Status
`BLOCKED` / `PARKED_BLOCKED`. Implementation PR `#112`; branch `package/es-p07-inventory-runtime`; frozen reviewed head `b34aade6ae79c7aaada0ada3c87970f937b6db6a`.

## 3. Objective
Complete safe online/offline inventory and Ender viewing/editing, revisions, locks, queued patches, server scopes, and runtime recovery.

## 4. Dependencies
`ES-P02` is `COMPLETE`.

## 5. Completed implementation
- Exact logical dirty-slot inventory/Ender writes with complete-set prevalidation before mutation.
- 32 MiB aggregate serialized snapshot bound; existing 16 MiB per-item bound retained.
- Idempotent same-owner `APPLYING` lease replay without weakening competing-operation fencing.
- Additional login/recovery mutation guards.
- Direct `/invsee`/`/endersee` command, authoritative lookup, entity-scheduler, GUI edit-gate, and permission-split coverage.
- Unit and MariaDB/Testcontainers regressions for snapshot bounds, invalid slot sets, and same-owner lease replay.
- Inventory safety Wiki update; substantive CodeRabbit and Codacy findings fixed.

## 6. Exact-head evidence already passed
Frozen head `b34aade6ae79c7aaada0ada3c87970f937b6db6a`: Validate Wiki `31426025633`; Coverage `31426025143` / job `93577825964`; Java 21 full tests and MariaDB/Testcontainers; runtime-JAR/provider-leak inspection; validation artifact `9077401417` (`sha256:6c9954d49d8f617d1565cc20f38c8280b4f4e20b1fd725589fa4d14ebe654a19`); aggregate coverage 47.14% lines / 38.24% branches / 49.80% instructions; Codacy static zero issues; diff coverage 31.58%; coverage variation -0.04%; CodeRabbit success with zero valid unresolved review threads. Sentinel artifact `9077240364` (`sha256:37710e09e2cac75bb30e2ab69297331654188a6b7fb2dc538c741fa94b7ee832`) was produced successfully.

## 7. Blocking validation state
Canonical Pi public run `31426022983` built/dispatched the exact head, but correlated private run `31426646043` / job `93579820065` is queued without runner assignment; no private Paper/MariaDB/Flyway cycle has executed. Exact-head Sentinel restart job 75 is queued because current host telemetry reports 120 MB available memory (<700 MB) and 82.3 C (>=80.0 C). No `PAPER_RESTART_OK` exists.

## 8. Exact unblock condition
Resume ES-P07 as `ACTIONABLE_CONTINUATION` before new package work when runner availability materially changes so the private Pi gate can execute and Sentinel host memory/temperature materially clears its resource gate. Merge remains forbidden until the unchanged frozen head receives canonical private Pi success/cleanup and terminal Sentinel `PAPER_RESTART_OK`. Do not repeatedly rerun identical blocked gates without changed evidence.

## 9. Exclusions and boundaries
Item confiscation/restoration remains ES-P08; representative private multi-backend/large-inventory/Bedrock acceptance remains ES-V02; external destructive providers remain later packages. V18 remains current and immutable; ES-P07 adds no migration. Issue #43 remains deferred and LiteBans authoritative. No production deployment/data/shadow/cutover/source rewrite/Flyway repair occurred.

## 10. Resume state
Preserve PR #112 and `package/es-p07-inventory-runtime`. Canonical blocked handoff: `ai-agents/reports/package-handoffs/2026-08-10-es-p07-inventory-runtime-blocked.md`.
