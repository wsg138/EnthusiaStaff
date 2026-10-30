# Punishment slash-command navigation cancellation QA — 2026-10-25

**DRAFT / NOT FOR DEPLOYMENT.** No live server, database, Discord, staffbot deployment, or policy changes.

## Source and issue

Base: [PR #725](https://github.com/wsg138/EnthusiaStaff/pull/725), exact source commit `54b90b24887b459fdbaace4d1866373f7fc2e397`. That draft invalidates pending punishment UI/history/target/review callbacks and note/search captures when the staff member successfully opens an unrelated inventory. All three hosted checks on #725 passed when checked; zero unresolved review threads.

**Additional uncovered path:** a staff member can issue a different slash command (e.g. `/spawn`, `/home`, `/msg`) while an asynchronous punishment GUI request or a private note/search prompt is pending, without opening an inventory. Before this change, there was no general player-command cancellation event, so a delayed history/preparation/target callback could still reopen the punishment GUI over later command navigation, and a queued private note/search callback could reopen a stale draft. If the prompt remained active, a subsequent normal chat message could also be consumed privately. This is separate from the earlier #724 queued-picker fix and #725 unrelated-inventory fix.

## Fix

Observe `PlayerCommandPreprocessEvent` at `EventPriority.MONITOR`, without changing or cancelling the command. For the issuing player, clear both note/search captures and invalidate the history, preparation, and target-resolution fences. Do this **unconditionally** (not only when captures exist), because `AsyncChatEvent` may have already removed a prompt capture and queued its entity-thread callback. The later `/punish` command handler starts a new, fresh ticket after command preprocessing.

No punishment policy, reason/rank/authorization logic, GUI renderer, storage or submission behavior was modified.

## Test evidence

Red-to-green `PunishmentGuiExternalCommandNavigationTest`: initially failed against the unchanged #725 source (missing command handler), then passed after implementation. It covers all five cancellation operations, MONITOR observation, already-consumed async-chat capture with queued callback, and fresh next-command ticket issuance. Focused external-inventory and typed-command navigation contract tests passed alongside it.

Fresh Windows/offline `gradlew.bat --offline --no-daemon --rerun-tasks :domain:test :paper:test :staff-bot:test :velocity:test :integration-tests:compileTestJava :paper:shadowJar`: **BUILD SUCCESSFUL** (30 executed tasks).

| Module | Passed | Skipped | Failures |
|---|---:|---:|---:|
| Domain | 347 | 0 | 0 |
| Paper | 1,391 | 0 | 0 |
| StaffBot compatibility | 509 | 14 | 0 |
| Velocity | 233 | 1 | 0 |
| **Total** | **2,480** | **15** | **0** |

Shaded `paper/build/libs/EnthusiaStaff-Paper-0.1.0-SNAPSHOT.jar`: 11,554,972 bytes; SHA256 `09643d95186ce7da97ad29eb7151e35a100292149dca8100e3dff2c758def662`. Embedded `config.yml` still has `guided-punishment.enabled: false`.

## Release limitations

Windows offline JUnit and Java integration-source **compile only**. MariaDB-backed integration tests were not executed. Live Folia/Paper/Linux scheduler acceptance, Java/Bedrock/Geyser UI, staff permission/rank live tests, custom production reason-policies.yml and rollback rehearsal remain unverified. Severity-policy calibration #616 is unapproved. Keep all PRs DRAFT, unmerged and undeployed; keep guided mode disabled.
