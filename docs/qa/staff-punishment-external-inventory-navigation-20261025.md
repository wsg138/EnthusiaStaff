# Punishment GUI: unrelated inventory navigation cancels pending work (2026-10-25)

**DRAFT source-only change; no deployment or release approval.**

## Reproduced failure

Starting from draft PR #724 exact head `443d67f367c4db09f58fc110a04719e1b1864bb9`, a staff member could issue a punishment command or begin a private search/note prompt, then open an unrelated inventory (chest or another plugin's GUI). Existing punishment navigation fences were invalidated on **punishment GUI** close/click/drag and new punishment commands, but **not** on an unrelated `InventoryOpenEvent`. Therefore a delayed worker callback could replace the unrelated inventory, or a lingering private prompt could intercept the next chat message even though the staff member had navigated elsewhere.

New regression `PunishmentGuiExternalInventoryNavigationTest.unrelatedInventoryOpenCancelsPendingHistoryPreparationTargetAndPrivateChat` failed **RED** on the original code (missing `InventoryOpenEvent` import/handler), then passed **GREEN** after the fix. A second test verifies that invalidating the independent history/preparation/target fences prevents their old tokens from being consumed.

## Fix

Register a MONITOR, `ignoreCancelled=true` `InventoryOpenEvent` handler for any player inventory opening, **including non-punishment inventories**. The handler removes pending private note/search captures and invalidates history, draft-preparation and target-resolution tickets for that viewer. It does not close or replace any inventory and does not change sanction logic. A canceled inventory open does not abandon the current operation.

## Fresh local verification

Windows offline `gradlew.bat --offline --no-daemon --rerun-tasks :domain:test :paper:test :staff-bot:test :velocity:test :integration-tests:compileTestJava :paper:shadowJar` executed in this worktree; command output retrieval failed transiently, but a follow-up identical command without `--rerun-tasks` reported **BUILD SUCCESSFUL** with 30 tasks up-to-date, and current-worktree XML reports show the fresh executions at 03:46 local time, zero failures.

| Module | Passing | Skipped | Failed |
|---|---:|---:|---:|
| Domain | 347 | 0 | 0 |
| Paper | 1,388 | 0 | 0 |
| StaffBot compatibility | 509 | 14 | 0 |
| Velocity | 233 | 1 | 0 |
| **Total** | **2,477** | **15** | **0** |

The focused external-inventory, target navigation, preparation navigation, and note-capture suites also passed. `integration-tests:compileTestJava` compiled; **MariaDB-backed tests did not execute**.

Locally shaded `paper/build/libs/EnthusiaStaff-Paper-0.1.0-SNAPSHOT.jar`: **11,554,895 bytes**, SHA256 `ba645ebd021588551c5adcb3e2347e04334957ae7c0bd48734a724f3c400a84b`. Packaged default `config.yml` contains `guided-punishment.enabled: false`.

## Remaining gates

No live Paper/Folia/Linux scheduler acceptance, Java/Bedrock/Geyser UI acceptance, production permissions/rank test, customized production reason-policy comparison, MariaDB runtime acceptance, or rollback rehearsal. Severity calibration #616 remains unapproved. All changes stay **DRAFT**, unmerged, undeployed; guided punishments remain OFF.
