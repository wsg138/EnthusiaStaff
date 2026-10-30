# Guided navigation + policy safety integration QA — 2026-10-24

**DRAFT / NO GO for release. No deployment authorization.**

## Branch provenance

This integration branch `test/punishment-guided-nav-policy-combined-20261024` is based on draft #715 at exact head `83be9bc8e05e9b2f987f6ea0f0f3fc563fa903fc` (guided asynchronous preparation navigation fence), which stacks on draft #713 at `a0792ba2444bea6f89ee82e7e8b59c507464d0e0`.

It cherry-picks draft #714's **test-only** commit `8260b5ccc49c9a2c834b4d0272329f776eca2a5a` into `f8d77fdb`. The new combined branch includes both fixes and the cross-product JUnit test without modifying either source PR/worktree. No other production code changes.

## Fresh Windows offline verification

`gradlew.bat --offline --no-daemon --rerun-tasks :domain:test :paper:test :staff-bot:test :velocity:test :integration-tests:compileTestJava :paper:shadowJar`

**BUILD SUCCESSFUL; 30 tasks executed.**

| Module | Passed | Skipped | Failed |
|---|---:|---:|---:|
| Domain | 347 | 0 | 0 |
| Paper | 1,372 | 0 | 0 |
| StaffBot compatibility | 509 | 14 | 0 |
| Velocity | 233 | 1 | 0 |
| **Total** | **2,461** | **15** | **0** |

The guided safety test examined **4,386 terminal questionnaires** and **56,595** policy/history/preference evaluations with the repository's *bundled* `reason-policies.yml`: **4,512** outcomes were ordinary warning or temporary mute; **52,083** returned mandatory manual review; **1,674** were calculated duration interpolations. Tests verified the configured sanctions' basic safety bounds, not the correctness of unapproved severity policy.

Built local `paper/build/libs/EnthusiaStaff-Paper-0.1.0-SNAPSHOT.jar`: **11,549,499 bytes**, SHA-256 `c721bda18cd95214ee59bb8a09c3b003dd8abb92c421b86c42879b50cd13feb3`. Read the built JAR's `config.yml`: `guided-punishment.enabled: false`.

## Unproven release gates

Only Java integration-test **sources compiled**. No MariaDB/database-backed integration execution, Linux/Folia/Paper live timing, Java/Bedrock/Geyser GUI acceptance, Discord route verification, current customized live policy comparison, production rank/permission behavior, or rollback rehearsal was performed. The 15 skips include environment-specific POSIX/Linux and external API scenarios. Owner severity calibration issue #616 remains unsigned. The repository's bundled policy is **not** the customized production `reason-policies.yml`. Guided punishment stays **OFF**.

Do not merge, deploy, install the local JAR, mutate server/database configuration, restart a server or approve punishments on the strength of this offline result.
