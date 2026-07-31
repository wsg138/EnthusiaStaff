# EnthusiaStaff workspace manifest

Last updated: 2026-07-30 (America/Indianapolis)

This manifest records review, recovery, and validation state. Nothing listed here
has been deployed, released, applied to production data, or used to replace
LiteBans.

## Repository checkpoint

| Repository | Default branch | Working branch | Current checkpoint | Validation state | Current blockers |
| --- | --- | --- | --- | --- | --- |
| EnthusiaStaff | `main` at merged PR #21 commit `90cfb0eb809b2895d105193b7bddf33fd6f95aa0` | `section/punishment-request-notifications-recovery` for draft PR #27 | Checkpoint B1.1 implementation and tests validated at `80101acadbaa06d09958076611ef933f1cc4efde`; shared-audience consumption is replaced by recipient-specific delivery state and precise immutable-intent replay verification | PASS: Coverage run `30603219197`; Java 21 clean build, MariaDB Testcontainers, aggregate JaCoCo, Paper/Velocity runtime inspection, 24 provider source checks per runtime jar, zero provider API leakage; artifact `8782689224` | PR #27 must remain draft and unmerged. B2 lifecycle transaction wiring, workers, Paper delivery, and Discord production remain outstanding. |
| EnthusiaStaff-Staging | Separate repository; not inspected or modified in this checkpoint | Unchanged | OUT OF SCOPE | NOT_RUN | Owned by the separate staging workflow/chat. |

## Merged PR #21 checkpoint

- Pull request: #21, **Expose durable punishment request interfaces**.
- Final head: `da531a4022d79935ed157c97c3260c42631d23f1`.
- Merge commit on `main`: `90cfb0eb809b2895d105193b7bddf33fd6f95aa0`.
- No deployment or staging claim is implied by the merge.

## Draft PR #27 checkpoint

- Pull request: #27, **Add durable punishment request notifications and recovery**.
- Branch: `section/punishment-request-notifications-recovery`.
- Base: `90cfb0eb809b2895d105193b7bddf33fd6f95aa0`.
- Checkpoint A head: `ccbd7806452c4dc5084fd03d76a496da324e6a87`.
- Checkpoint A validation: Coverage run `30599247739`, success; artifact `8781297271`.
- Checkpoint B1 head: `5e06fa952e4ed741569a3b12d8482e5a5e3b28b1`.
- Checkpoint B1 validation: Coverage run `30600138458`, success; artifact `8781596037`.
- Checkpoint B1.1 validated implementation/test head:
  `80101acadbaa06d09958076611ef933f1cc4efde`.
- B1.1 commit sequence:
  - `f415def0c0546e8fa389e10f2ea27eb518d2baef`: recipient-specific delivery
    identity, claim/backlog contracts, explicit intent expiry, and domain tests.
  - `9aef770759f7a31ab8be8fd170b347f44deafa94`: forward-only Flyway V12 and
    recipient-specific JDBC delivery persistence.
  - `d02c882dc69f60ec6ca9b6780e879d7053bcd72f`: concurrency, migration,
    idempotency, lifecycle, retention, and restart Testcontainers coverage plus
    checkpoint documentation.
  - `df7e717e131b7a7f6b25b15310c8018792d6180d`: test-compilation correction.
  - `43a08760b77463fd66684fb273c508b1f2ea24a9`: preserve the legacy 30-day
    alert-expiry default and prevent different recipients from contending on the
    shared intent row during delivery leasing.
  - `d2af2f84d63b0dba3b26e229b171c7e8a5cbee19`: correct the bounded terminal
    cleanup expectation after the complete cleanup set was verified.
  - `0179ac4fe342a7ea508cbc93aedcd1b6a2011811`: record the first validated B1.1
    implementation checkpoint.
  - `4338e1969404bc4d0debf7703a2f819c672f11fa`: add focused canonical-replay
    Testcontainers coverage for audience/filter mismatches, malformed UUID width,
    and foreign-key failures.
  - `80101acadbaa06d09958076611ef933f1cc4efde`: correct the focused test setup
    warning so the full assertions execute under `-Werror`.
- B1.1 deliberately does not wire request submission, claim, approval, denial,
  external fulfillment, bounded request expiration transitions, Paper listeners,
  schedulers, Bukkit delivery, or Discord network delivery.

## B1.1 validation evidence

Exact implementation/test head: `80101acadbaa06d09958076611ef933f1cc4efde`.

- Workflow: Coverage run `30603219197`, success.
- Runtime: Temurin Java `21.0.11+10`.
- Command: clean multi-module `build`, `jacocoAggregateReport`, and `runtimeJars`
  with the build cache and configuration cache disabled.
- Gradle result: `BUILD SUCCESSFUL`; 49 actionable tasks, 40 executed and 9
  up-to-date.
- MariaDB 11.8.3 Testcontainers executed, including the V11-to-V12 upgrade path,
  independent audience-recipient concurrency, canonical replay mismatch, malformed
  UUID-width, and foreign-key failure scenarios.
- Aggregate JaCoCo: lines `33.52%`, branches `27.51%`, instructions `35.87%`.
- Exactly one Paper runtime jar and one Velocity runtime jar were found and passed
  ZIP integrity inspection.
- Twenty-four provider API source types were checked against each runtime jar;
  provider API leakage was zero for both.
- Paper SHA-256:
  `bc531cac93d61abb04b378a1f24c63515749a98f4f8c0f2759ea0c9c5da22b54`.
- Velocity SHA-256:
  `6ad5bca9842f1fb21a5cba87b5f7c2b5212dde5dee8e1fe79f405113f9dcf432`.
- Validation artifact: `8782689224`; artifact digest:
  `sha256:21ab8327f15488f789dd4495c78e4a2d42332b3de2f5597ef14b05dad9aae6e6`.
- Codacy coverage upload and final notification succeeded.

## Audience-consumption correction

B1 stored one immutable audience intent but also stored lease, retry, dead-letter,
and delivery state on that shared row. A successful delivery to the first eligible
reviewer therefore made the audience intent globally delivered and suppressed it
for all other reviewers.

B1.1 separates the two concerns:

- `staff_alerts` remains one immutable intent row per logical request lifecycle
  event and owns deterministic identity, request metadata, audience filters,
  visibility, schema version, creation, expiry, and terminal intent lifecycle.
- `staff_alert_deliveries` owns one durable delivery row per `(alert_id,
  recipient_id)` actually materialized, with independent pending/leased/delivered/
  dead-letter state, attempts, availability, lease fencing, error, and delivery
  timestamps.
- Direct intents guarantee one delivery row during insertion.
- Reviewer and operational audience deliveries are lazily materialized only for
  the currently eligible recipient; no permanent theoretical reviewer fan-out is
  created at intent insertion.
- One recipient's delivery or dead letter cannot consume or terminally alter an
  audience intent for another eligible recipient.

## Flyway V12 schema checkpoint

V12 is forward-only and does not edit or renumber V11. It:

- adds `intent_state`, `closed_at`, and `close_reason` to `staff_alerts`;
- backfills null legacy `expires_at` values to `created_at + 30 days`;
- makes `expires_at` non-null while retaining a 30-day default for legacy
  producers that do not yet supply explicit lifecycle expiry;
- adds intent eligibility and retention indexes;
- creates `staff_alert_deliveries` with composite primary key `(alert_id,
  recipient_id)`;
- adds recipient due-ordering, lease-recovery, and retention indexes;
- adds a non-cascading foreign key to `staff_alerts(alert_id)`;
- migrates legacy direct-recipient B1 delivery state into the new table;
- deliberately does not convert shared audience delivery state into a global
  recipient row because the B1 schema did not retain which audience member
  received it.

The legacy V11 `staff_alerts.state` and lease columns remain for compatibility but
are no longer authoritative for B1.1 delivery. `intent_state` controls whether an
intent accepts new recipients; `staff_alert_deliveries.state` controls one
recipient's delivery.

## Intent and delivery lifecycle

- New delivery materialization and claim require an `ACTIVE`, unexpired intent.
- Explicitly closed or expired intents cannot create or claim new recipient rows.
- A delivery already leased before intent expiry may still be acknowledged or
  failed while its own lease remains valid; this avoids representing a message
  already presented as undelivered solely because intent expiry occurred during
  presentation.
- Pending deliveries on a closed or expired intent are not claimable.
- Direct intent delivery closes that direct intent; audience delivery never closes
  the shared audience intent.
- Retention cleanup is bounded and deterministic. It removes only terminal
  intents with no pending, leased, or dead-letter recipient work, deleting child
  delivery rows before the non-cascading parent row.
- Delivery remains at-least-once. No exactly-once claim is made.

## Recipient claim and lease semantics

- Direct delivery rows are guaranteed transactionally with intent insertion.
- Audience delivery rows are inserted lazily for the current recipient only after
  requester exclusion and current rank eligibility are checked.
- Due selection locks only `staff_alert_deliveries` rows with `FOR UPDATE SKIP
  LOCKED`; the shared immutable intent is checked through an eligibility
  subquery, so different recipients can independently lease the same audience
  intent while two servers cannot lease the same `(alert_id, recipient_id)` row.
- Lease acquisition rechecks active/unexpired intent state, audience, requester
  exclusion, and minimum rank before changing the recipient row.
- Delivery and failure mutations require the explicit delivery identity, matching
  owner, `LEASED` state, and an unexpired lease.
- Attempts, retries, dead letters, reclaim, delivery, and restart persistence are
  per recipient.

## Precise idempotent insertion

Immutable intent insertion uses a normal `INSERT`. MariaDB duplicate-key error
1062 is treated as a possible replay only after loading the stored row and
verifying all canonical immutable fields: request ID, revision, lifecycle event,
audience, direct recipient or reviewer filters, visibility, schema version,
creation time, and expiry. An identical deterministic-key replay returns the
existing intent; a duplicate alert ID, mismatched canonical field, malformed UUID
storage, foreign-key failure, truncation, invalid enum/JSON, or unrelated SQL
failure remains a persistence error. `INSERT ... ON DUPLICATE KEY` is limited to
recipient delivery materialization, where the only unique identity is `(alert_id,
recipient_id)` and other constraint failures still propagate.

## Isolation and release rules

- Do not modify `wsg138/EnthusiaStaff-Staging` or staging PR #2.
- Do not dispatch staging workflows or access `Lincoln-PI-4`.
- Do not modify Pi services/files, GitHub staging environments, or secrets.
- Do not access production databases or production Discord credentials.
- Keep PR #27 draft and do not merge it without explicit authorization.
- A pushed commit is not a validated checkpoint until exact-head required checks pass.
