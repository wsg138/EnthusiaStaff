# Report case-message provenance and paginated staff history (draft)

**Date:** 2026-10-15. **Issue:** #624. **Status:** staging only; not a release approval.

## Current bug and fix

`JdbcReportSubmissionStore.mergeDuplicate` commits the later report description into
`report_messages`, but the original staff `ReportDetails.description` and /reports view
display only the initial description. The same `report_messages` table also stores internal
staff case action notes. Therefore comparing `actor_id` to `reporter_id` is **not a safe
way to classify a row**: the reporter might themselves be a staff actor.

Versioned migration `V32__report_message_provenance.sql` adds a nonnullable origin enum
(`UNKNOWN`, `REPORTER_FOLLOWUP`, `STAFF_ACTION`) with default `UNKNOWN`. New merged
submissions and staff state-change notes set the right type inside their existing
transaction. Historical messages stay `UNKNOWN` rather than being falsely attributed.
Neither the original description nor the existing submission/event idempotency contracts
are modified. No message text or private evidence is sent to Discord.

A narrow `ReportStore.exists(reportId)` query checks the report ID without loading retained chat, private-message, or client-evidence snapshots.

The staff-only `/reports messages <report-id> [page]` reads five chronological rows per
page, ordered by timestamp and immutable message UUID. It displays the origin, sender,
timestamp, and bounded text, with next-page instructions. The final display rechecks `enthusiastaff.reports.manage` and independently
`enthusiastaff.reports.evidence` on the sender's owning scheduler. Reporter follow-ups
require manage permission; internal staff action notes and unverified historical rows
require evidence permission, including if privileges change during the async query. The normal `/reports view` and GUI
detail both point to the command. No extra GUI click action bypasses authority.

## Historical-message behavior

Historical `UNKNOWN` rows are accessible only to authorized evidence reviewers, clearly labeled
as **origin unverified**. No heuristic backfill, silent rewrite, or user/staff
misclassification is performed. New rows are classified transactionally. If a historical
TEXT row exceeds the conservative chat display cap, the response explicitly says so.

## Verification and rollout gates

- Local Windows compilation + domain, Paper, StaffBot, Velocity tests and shaded Paper
  build passed. The integration-test source compiles.
- Local MariaDB Testcontainers run was attempted but could not run because Docker is
  unavailable in the authorized Windows environment. It is **not a test pass**.
- `ReportStoreIntegrationTest` includes new cases for reporter merge/replay, a staff
  state change by the **same actor as the reporter**, legacy unknown provenance,
  chronological ordering, paging boundaries and input validation.
- Hosted Linux/MariaDB test must run at the **exact PR head** and migration must be
  rehearsed on disposable staging database snapshots before merge/release.
- Verify staff permission removal during queued asynchronous responses, and Java +
  Geyser/Bedrock text readability, against private test accounts.
- Validate DB migration impact and a backup-and-rollback plan on a **non-production**
  clone before authorizing any schema change. Migration must not be run against live
  MariaDB as part of development.
- Keep #625 separate: freeform report text delivery through StaffBot needs explicit
  private-channel and role-authorization design first.

Do not replace any live JAR/config/schema or enable guided sanctions based on this change.
