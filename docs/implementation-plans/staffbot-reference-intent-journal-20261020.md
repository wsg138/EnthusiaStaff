# Reference-only, inert StaffBot report intent journal

Date: 2026-10-20. Issue #625. **Draft staging only.**

This is the first durable component of the automatic StaffBot notification workflow. It is based on the isolated cross-feature candidate #645 (which includes the V32 report-message provenance migration) and introduces V33, a **separate** table `staffbot_report_notification_intents`.

`JdbcReportSubmissionStore` writes a reference-only intent **in the same MariaDB transaction** as an accepted new report or merged report. The source's `IdempotencyKey` forms a unique delivery key. A replay returns before this insert, so duplicate submissions should not produce duplicate intents. An aborted transaction rolls back the report, audit, Velocity outbox, and StaffBot intent together.

No report description, staff note, snapshots, personal information or other evidence is copied into the new intent table. It stores only report IDs, submission-level idempotency, event type, timestamps and delivery bookkeeping. Existing Velocity webhooks remain metadata-only.

## Deliberate safety gate

Every new record defaults to `INERT`. This PR implements **no scheduled claim loop, promotion, sender, JDA permission checks, or Discord call**. It contains only explicit storage methods for future approved callers. Neither restarting StaffBot nor changing an unrelated config can cause delivery or retroactive backfill. The existence of an intent must never be treated as operator approval.

Future work must deliberately distinguish old `INERT` records from newly authorized intents. A future `ARMED` state requires explicit owner-approved destination and policy, a trusted permission check, an audited activation decision, and a safe policy for ambiguous sends/restart recovery. Expired `LEASED` attempts should enter `REVIEW_REQUIRED`, not silently resend potentially successful Discord messages. This table's state model is preparation, not an implemented delivery machine.

## Verification and rollout gates

- Hosted exact-head MariaDB integration must exercise new report, merged report, repeated merge submission and verify exactly two intents, both `INERT`, neither `ARMED`.
- A new `JdbcStaffBotReportIntentStore` implements reference-only `claimArmed`,
  owner-bound `delivered`, `reviewRequired`, and `quarantineExpired`.
  **It has no activation method or Discord sender.** The claim function only
  selects explicitly `ARMED` records; existing `INERT` records remain hidden.
- Claim uses a bounded READ COMMITTED database transaction with row locks and
  `SKIP LOCKED`; acknowledgement requires the **same owner** and unexpired
  lease. Ambiguous/expired leases transition to `REVIEW_REQUIRED` and are
  never auto-claimed again. This prioritizes avoiding duplicate reports over
  automatic retries; a future human-authorized reconciliation design is needed.
- MariaDB integration tests synthesize an ARMED row **only inside disposable
  Testcontainers** and verify zero default claims, owner mismatch rejection,
  lease expiry quarantine, no auto-retry, terminal delivery and no re-delivery.

- Hosted baseline tests, static analysis, StaffBot JAR and protocol compatibility must pass. Local Windows development computer access was unavailable (429); no local tests are claimed.
- Migration V33 must be rehearsed on an isolated database snapshot with backup and rollback steps; it is **not** approved for live MariaDB.
- Owner selects exact private guild, channel, allowed roles, restricted-category handling, repeat follow-up behavior and staff revocation. Then add runtime claim/dedupe, effective JDA permission validation and synthetic Discord staging tests.
- PR #648 only provides inert formatting/private-channel predicates; this PR only provides transactional reference intents. They are not combined or activated for production.
