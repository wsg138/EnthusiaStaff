# Staff punishment cross-feature integration — October 16, 2026

## Purpose and provenance

This is an isolated **validation candidate**, not a deployable production tag.

Source heads pinned during assembly:

- Guided punishment and Worker A/B/C integration: #631 at `70bfd007319efe683a1bb6e9c14110ebb542f10c`.
- Current `main` snapshot: `2d971fde567882ad11fc8aec4f386203d876d3c1`, including #642 private-channel staging preflight.
- Report-message origin/paginated history: #637 at `7025b3675210143dfeb30aceae3ec7420b7aea96`.
- Mod+ ephemeral StaffBot report-text preview: #643 at `1a65ef4c3b5077ecce84050660e153b33606d214`.

Each independent source PR remains open and authoritative. This branch contains normal Git merge commits; no worker source branches were force-updated.

## Resolved integration conflict

`ReportGuiRenderer` had competing description lore changes from #618 and #637.
Resolution deliberately retains **both**:

- 480-character description preview with an explicit full-text `/reports view <UUID>` route when truncated.
- Clearly identified original description plus `/reports messages <UUID>` for merged reporter updates, staff actions and legacy unknown-origin messages.

Added `ReportGuiDescriptionPreviewTest` cross-feature assertion covering both short and truncated detail views and ensuring the history link survives.

## Verification

- Local Windows offline Gradle `:domain:test :paper:test :staff-bot:test :velocity:test :integration-tests:compileTestJava :paper:shadowJar` passed against this combination before the small GUI integration test addition.
- Targeted `:paper:test --tests net.enthusia.staff.paper.report.ReportGuiDescriptionPreviewTest` passed after the addition.
- Hosted exact-head CI (Linux, MariaDB migration, Codacy, coverage, Sentinel JAR, protocol/Via) **must pass before promotion**.
- Windows does not prove POSIX/FastAPI or Testcontainers runtime behavior; hosted checks are mandatory.

## Production blockers

1. Guided punishment severity profile is prototype only; explicit policy-calibration and sanction-duration approval (#616) are outstanding. Guided feature flag remains OFF.
2. Production `reason-policies.yml` is historically customized and must not be silently overwritten with repo defaults. Backup and diff with approved version first.
3. Report migration V32 must be rehearsed against a disposable staging database snapshot with backup and rollback plan; do not run it against live MariaDB by merely deploying this PR.
4. Java, Bedrock/Geyser, Paper/Leaf/Folia permissions, follow scheduling, GUI clicks, report body privacy and role revocation require actual isolated staging tests.
5. #643 only enables the authorized **ephemeral manual** report-preview; #625 automatic private StaffBot notifications have not been implemented or approved for live channel delivery.
6. #624 reporter origin history requires exact-head MariaDB proof and safe sensitive-evidence permission. Staff action notes and unknown legacy rows must stay separately restricted.
7. Validate exact JAR hash/artifact, rollback procedure and owner acceptance before any production release. Never merge this test branch merely because static CI is green.

No production JAR, live Discord channel, credential, database or configuration has been modified for this validation.

## Follow-up integration (2026-10-16)

The cross-feature branch also incorporates a newer \`origin/main\` snapshot at
\`c945b92e0\` and the lead severity-profile PR #621 update
\`aa2763b3d\`. The new within-tier severity-duration weight is an **unapproved
test-only** option with the default value **0.0**, which preserves the
historical guided prototype. Local Domain/Paper/StaffBot/Velocity tests and
shaded Paper JAR build PASS after this additional merge. Any nonzero weight
requires owner-calibrated documented sanction outcomes and exact-head hosted
verification. Guided production flag remains OFF.
