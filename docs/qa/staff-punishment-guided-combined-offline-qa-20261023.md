# Guided punishment combined offline validation - 2026-10-23

**Status:** VALIDATION ONLY; DO NOT MERGE OR DEPLOY.
**Branch:** test/punishment-guided-combined-qa-20261023.
**Base:** PR #672 at db78ba189dd4f2b0f2f254a2e85a1ea2aee2135d, stacked on #670 and #631.
**Domain sources merged without conflict:** PR #675 at 8ff423710989cbb06f524ec931d3c472d5932544 and PR #676 at f8ea57d3d5ed42badd2d5e257f58a187eaff34fb.
**Pre-document merge head:** 0521f54e0df81d29788416167e962c1589355afa.

## Exact Windows offline validation

Command: gradlew.bat --offline --no-daemon --rerun-tasks :domain:test :paper:test :staff-bot:test :velocity:test :integration-tests:compileTestJava :paper:shadowJar

- Domain: 325 passed, zero skipped or failed.
- Paper: 1,308 passed, zero skipped or failed.
- StaffBot compatibility only: 509 passed, 14 Linux/API-specific tests skipped.
- Velocity compatibility only: 233 passed, 1 POSIX-specific test skipped.
- Total: **2,375 passed, 15 skipped, zero failures**.
- Integration-test source compiled. Database-backed integration tests were NOT run.
- git diff --check origin/main...HEAD passed, and worktree was clean after merges.
- Local Paper JAR: paper/build/libs/EnthusiaStaff-Paper-0.1.0-SNAPSHOT.jar.
- Artifact bytes: 11,528,081; SHA-256: 617c147e78effa7df24ad205f16c496022a95054d1efded1469949b09ff62c0d.
- Inspected JAR config.yml confirms guided-punishment.enabled is false.

## Meaning and limitations

This validates the combined source-level GUI, rank-filtered guided results, restricted-sanction preview, voice manual-review guard, unknown-answer shortcuts, decreasing-mute guard, and bundled-policy scenario matrix.

The base is an older stacked feature candidate, not current production main and not the separate StaffBot worker release. No Linux/POSIX-only tests, MariaDB migration/recovery, actual Java/Bedrock/Geyser GUI, permission changes, voice enforcement, or custom production reason-policies.yml was validated. No production JAR or config was modified.

**Release status: NO-GO.** Guided mode remains disabled. Owner severity calibration (#616), independent source review, isolated live acceptance and rollback are required.
