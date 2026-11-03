# Punishment typed-command + chat-prompt navigation integration QA — 2026-10-25

**DRAFT / NO DEPLOY.** No punishment policy, live server, database, StaffBot or Discord changes.

## Source provenance

This isolated integration branch started from [PR #721](https://github.com/wsg138/EnthusiaStaff/pull/721) exact source commit `7017b5e97ecb24a5e0b31c10b51647304e219f02`. #721 added an inventory-bound navigation ticket to direct typed punishment draft preparation and review presentation. It was based on the then-reviewed source head of [PR #720](https://github.com/wsg138/EnthusiaStaff/pull/720), `50290fdf48937d2a83aba46a098d42cf82f7b0aa`.

Another worker advanced PR #720 with exact commit `a5def9398384cb0fedf48001e60c3254e385011f`, which changed private note/search chat callback tokens to be minted at **prompt creation**, after closing the prompt's source GUI; this prevents a delayed chat callback from obtaining a fresh token after an intervening navigation. The integration cherry-picked this one commit on top of #721 as `f1000c4d`, without modifying either original source branch. Cherry-pick had no conflicts.

Added a cross-route contract test to `PunishmentCommandPreparedNavigationContractTest` verifying a typed policy command captures a new target-resolution ticket **before submitting the asynchronous worker**, clears both pending private note/search captures and invalidates their old preparation ticket; delayed note/search callbacks must consume their original prompt token, never mint a fresh one.

## Fresh Windows/offline verification

`gradlew.bat --offline --no-daemon --rerun-tasks :domain:test :paper:test :staff-bot:test :velocity:test :integration-tests:compileTestJava :paper:shadowJar`: **BUILD SUCCESSFUL**, 30 executed Gradle tasks.

| Module | Passing | Skipped | Failed |
|---|---:|---:|---:|
| Domain | 347 | 0 | 0 |
| Paper | 1,385 | 0 | 0 |
| StaffBot compatibility | 509 | 14 | 0 |
| Velocity | 233 | 1 | 0 |
| **Total** | **2,474** | **15** | **0** |

Focused `PunishmentCommandPreparedNavigationContractTest`, `PunishmentGuiNoteCaptureSafetyTest`, and `PunishmentGuiPreparationNavigationContractTest` also passed.

Locally built `paper/build/libs/EnthusiaStaff-Paper-0.1.0-SNAPSHOT.jar`: **11,554,711 bytes**, SHA256 `7a85c58eea9553083ea3a9e2d4962cb32a2640497a632fd17e9e8bf636e1da7f`. Embedded default `config.yml` inspected: `guided-punishment.enabled: false`.

## Release gate exclusions

This is offline Windows JUnit and compile-only Java integration checking. MariaDB-backed tests were **NOT executed**. No Linux/Paper/Folia async scheduler acceptance, Java/Bedrock/Geyser GUI checks, customized live reason-policy comparison, staff permissions/role runtime or rollback rehearsal were completed. Owner severity-policy calibration #616 is not approved. All changes remain **DRAFT**, unmerged, undeployed; guided mode stays OFF.
