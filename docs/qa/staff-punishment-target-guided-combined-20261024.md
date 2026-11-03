# Punishment target + guided navigation combined offline QA — 2026-10-24

**Draft integration/QA only. NOT approved for deployment.**

## Provenance

Integration branch `test/punishment-target-guided-combined-20261024` started from the exact head of draft PR #717, commit `85209acc3d01125b403a9d8e30b74e5ee0ba569d`. #717 includes guided async calculation and review-origin fencing from PR #715 and the exhaustive cross-product policy test originally from PR #714, on top of the broader punishment/approval GUI stack.

The integration adds the independently developed target-resolution navigation fence from draft PR #716, original commit `09fd84f91126e42d2a1b84a1fe68a9f2e5f3e5fa`, based on #713. Its cherry-pick was **not automatic**: both #715 and #716 modified the shared `presentState` entity-thread method, creating a conflict. The conflict was resolved explicitly with a six-argument final entity-thread handler carrying two distinct typed payloads: `Guided` questionnaire origin and `PunishmentGuiTargetResolutionFence.Ticket`. Dedicated overloads supply each without conflation. Existing history and preparation navigation tokens remain separate.

A new merged-path contract assertion verifies target ticket and guided preparation tokens are both checked **before** saving questionnaire return state or opening the inventory. Source PRs and their worktrees are unchanged.

## Fresh offline verification

`gradlew.bat --offline --no-daemon --rerun-tasks :domain:test :paper:test :staff-bot:test :velocity:test :integration-tests:compileTestJava :paper:shadowJar`

Result: **BUILD SUCCESSFUL**, 30 Gradle tasks executed.

| Module | Passed | Skipped | Failed |
|---|---:|---:|---:|
| Domain | 347 | 0 | 0 |
| Paper | 1,378 | 0 | 0 |
| StaffBot compatibility | 509 | 14 | 0 |
| Velocity | 233 | 1 | 0 |
| **Total** | **2,467** | **15** | **0** |

Focused tests for target resolution navigation, guided preparation navigation and bundled-policy exhaustive safety also passed after the merge conflict resolution.

Exhaustive safety test: **4,386** questionnaire paths, **56,595** policy/history/preference evaluations, **4,512** permitted ordinary outcomes, **52,083** manual-review evaluations, **1,674** calculated durations. Tests evaluate the repository's *bundled* policy, not the customized live production policy.

Locally built shaded Paper JAR `paper/build/libs/EnthusiaStaff-Paper-0.1.0-SNAPSHOT.jar`, **11,552,507 bytes**, SHA-256 **`db84092e60cc8aee1a4e6d8b7ce3b0ca5f88a28f2a988024b0f6b094488aa07b`**. Its embedded `config.yml` has `guided-punishment.enabled: false`.

## Still unproven

Integration-test Java compiled but was **not executed** against MariaDB. No actual Paper/Folia/Linux event races, Java/Bedrock/Geyser inventory acceptance, Discord enforcement, rank/permission tests on a live network, server changes, policy comparison or rollback rehearsal. The owner has not approved severity-calibration issue #616. A green Windows/offline build cannot establish runtime release safety. Keep guided mode OFF. No merges, live server/data writes, Discord posts, deployment or restarts.
