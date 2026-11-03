# Punishment full-stack offline compatibility validation — 2026-10-24

**STATUS: DRAFT / NO-GO FOR RELEASE. DO NOT MERGE OR DEPLOY.**

## Provenance

- Integration branch: `test/punishment-composite-qa-20261024`.
- Exact base: `11271252851c230b06970567881e2f3041c6faae`, [PR #708](https://github.com/wsg138/EnthusiaStaff/pull/708), latest stacked punishment approval/GUI decision candidate at validation time.
- New integration-only cherry-pick: `4c6a6b40`, source [PR #686](https://github.com/wsg138/EnthusiaStaff/pull/686), original commit `4cb63beaca92c9699c649206570b99f242dd65a0`, Discord-origin guided incidents require channel-specific manual review. The original was **not** an ancestor of the #708 base.
- This adds 6 lines of domain logic plus 37 lines of targeted tests to the #708 stack; the rest of the approval and confirmation source is unchanged from #708.
- Source #686 was cherry-picked cleanly. Existing development worktrees and draft PRs were not edited.

## Fresh Windows/offline evidence

Exact command:

`gradlew.bat --offline --no-daemon --rerun-tasks :domain:test :paper:test :staff-bot:test :velocity:test :integration-tests:compileTestJava :paper:shadowJar`

Result: **BUILD SUCCESSFUL**, 30 actionable Gradle tasks executed.

| Module | Passed | Skipped | Failed |
|---|---:|---:|---:|
| Domain | 347 | 0 | 0 |
| Paper | 1,356 | 0 | 0 |
| StaffBot compatibility | 509 | 14 | 0 |
| Velocity | 233 | 1 | 0 |
| **Total** | **2,445** | **15** | **0** |

- The compiled Java integration-test source was **not executed** against MariaDB or a live network.
- `git diff --check 11271252851c230b06970567881e2f3041c6faae...HEAD`: PASS.
- Local build artifact, for verification only: `paper/build/libs/EnthusiaStaff-Paper-0.1.0-SNAPSHOT.jar`.
- Bytes: `11,546,495`.
- SHA-256: `67ebcda5045420b55b952b39c43c4b364eb24d7f49d251309560cfca1fa2ffdc`.
- Inspected **built JAR** `config.yml`: `guided-punishment.enabled: false`.

## Limits and outstanding gates

This is a source-level/offline compatibility result, not a production candidate or deployment instruction. The base is a stacked draft feature branch, not current `main`, and may include separate StaffBot compatibility source not released by that project's coordinator.

No MariaDB migration/rollback, case creation on a test network, in-game Java/Bedrock/Geyser inventory clicks, Folia/Linux runtime checks, Discord delivery, current production `reason-policies.yml`, or rank/approval behavior on a real server was validated. The skipped cases include Linux/POSIX-specific and real external API tests.

Owner severity calibration [issue #616](https://github.com/wsg138/EnthusiaStaff/issues/616) remains **unapproved**. The live default flag must stay off. Release requires an explicit owner decision, isolated runtime acceptance, and an authorized rollback plan.

No production/test-server writes, database changes, network announcements, restarts, merges or deployments occurred.
