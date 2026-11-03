# Punishment private reason-search expiry navigation QA — 2026-10-25

**DRAFT / NO DEPLOY.** No changes to sanction policy, permissions, production reason policies, running servers, database, StaffBot or Discord.

## Provenance and issue

Isolated branch `fix/punishment-search-expiry-navigation-20261025` starts at [PR #726](https://github.com/wsg138/EnthusiaStaff/pull/726) exact source commit `26aa4e5b3f7b6959b98846aaac112100d9a3524f` (which adds slash-command navigation invalidation). Parent [PR #725](https://github.com/wsg138/EnthusiaStaff/pull/725) protects unrelated inventory opens.

Existing private search prompts schedule a 60-second entity-thread timeout. If the server is lagged and that timeout is delayed, `AsyncChatEvent` previously consumed the expired private search query, then `finishSearch` detected expiry and called `openState(viewer, origin)`, **reopening a stale punishment GUI** despite the expired prompt. At the exact 60-second deadline, the old strict `isAfter` check also accepted the search. The private note path already has a deadline guard, but search did not.

## Fix and regression

- Added `searchCaptureExpired(startedAt, now)`, using the same inclusive 60-second boundary as note capture.
- In `AsyncChatEvent`, expired search input stays private (event cancelled) but only sends a timeout notice after consuming the original prompt navigation token. No old GUI or reason search is opened.
- The second deadline check in `finishSearch` handles a chat submitted before expiry but whose queued entity callback runs after expiry: send a notice, **do not restore the abandoned origin GUI**.
- Added `PunishmentGuiSearchCaptureSafetyTest`: verifies 0, 59, 60, 61-second boundary values and structural safeguards for the late async event and late entity callback. The initial test compile **failed** on unchanged #726 source because the helper was absent (expected red). After the fix, the focused new test, note capture, external command, and preparation navigation suites **passed**.

## Fresh offline verification

`gradlew.bat --offline --no-daemon --rerun-tasks :domain:test :paper:test :staff-bot:test :velocity:test :integration-tests:compileTestJava :paper:shadowJar`: **BUILD SUCCESSFUL**, 30 executed Gradle tasks.

| Module | Passed | Skipped | Failed |
|---|---:|---:|---:|
| Domain | 347 | 0 | 0 |
| Paper | 1,393 | 0 | 0 |
| StaffBot compatibility | 509 | 14 | 0 |
| Velocity | 233 | 1 | 0 |
| **Total** | **2,482** | **15** | **0** |

Shaded Paper JAR `paper/build/libs/EnthusiaStaff-Paper-0.1.0-SNAPSHOT.jar`: **11,555,092 bytes**, SHA256 `343e92704f03bb76b6e07ccad1c2edacd9e0adebc6cd0b6b6c15aec238832dd6`. Packaged `config.yml` has `guided-punishment.enabled: false`.

## Release gate

Windows/offline tests do not establish runtime scheduler correctness on Linux/Folia/Paper, live MariaDB behavior, Java/Bedrock/Geyser GUI acceptance, production staff rank/permission checks, production-customized reason policies, or rollback. Integration Java test sources were **compiled only**; MariaDB-backed tests were **NOT run**. Owner severity-policy calibration #616 is not approved. All branches remain **DRAFT, unmerged, undeployed**, guided mode OFF.
