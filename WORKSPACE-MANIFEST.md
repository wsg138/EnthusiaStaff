# EnthusiaStaff workspace manifest

Last updated: 2026-07-30 (America/Indianapolis)

This manifest records review, recovery, and validation state. Nothing listed here
has been deployed, released, applied to production data, or used to replace
LiteBans.

## Repository checkpoint

| Repository | Default branch | Working branch | Current checkpoint | Validation state | Current blockers |
| --- | --- | --- | --- | --- | --- |
| EnthusiaStaff | `main` at merged PR #21 commit `90cfb0eb809b2895d105193b7bddf33fd6f95aa0` | `section/punishment-request-notifications-recovery` for draft PR #27 | Checkpoint B1 validated at `5e06fa952e4ed741569a3b12d8482e5a5e3b28b1`; Checkpoint B1.1 corrects shared-audience consumption with recipient-specific delivery rows and precise duplicate verification | B1 Coverage run `30600138458` succeeded. B1.1 exact-head validation is required before B1.1 is recorded as tested. | PR #27 must remain draft and unmerged. B2 lifecycle transaction wiring, workers, Paper delivery, and Discord production remain outstanding. |
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
- Checkpoint B1 validation: Coverage run `30600138458`, success; Java 21 build,
  MariaDB Testcontainers, Paper/Velocity runtime inspection, 24 provider source
  checks per runtime jar, zero provider API leakage; artifact `8781596037`.
- B1.1 commit sequence begins with:
  - `f415def0c0546e8fa389e10f2ea27eb518d2baef`: recipient-specific delivery
    identity, claim/backlog contracts, explicit intent expiry, and domain tests.
  - `9aef770759f7a31ab8be8fd170b347f44deafa94`: forward-only Flyway V12 and
    recipient-specific JDBC delivery persistence.
  - The test/documentation head and its exact validation evidence are recorded
    after the final B1.1 commit and required checks complete.
- B1.1 deliberately does not wire request submission, claim, approval, denial,
  external fulfillment, bounded request expiration transitions, Paper listeners,
  schedulers, Bukkit delivery, or Discord network delivery.

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
- backfills null legacy `expires_at` values to `created_at + 30 days` and makes
  `expires_at` non-null;
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
are no longer the authoritative state for B1.1 delivery. `intent_state` controls
whether an intent accepts new recipients; `staff_alert_deliveries.state` controls
one recipient's delivery.

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

## Precise idempotent insertion

Immutable intent insertion now uses a normal `INSERT`. MariaDB duplicate-key error
1062 is treated as a possible replay only after loading the stored row and
verifying all canonical immutable fields. An identical deterministic-key replay
returns the existing intent; a duplicate alert ID, mismatched canonical field, or
unrelated SQL failure remains a persistence error. `INSERT ... ON DUPLICATE KEY`
is limited to recipient delivery materialization, where the only unique identity
is `(alert_id, recipient_id)` and all other constraint failures still propagate.

## Isolation and release rules

- Do not modify `wsg138/EnthusiaStaff-Staging` or staging PR #2.
- Do not dispatch staging workflows or access `Lincoln-PI-4`.
- Do not modify Pi services/files, GitHub staging environments, or secrets.
- Do not access production databases or production Discord credentials.
- Keep PR #27 draft and do not merge it without explicit authorization.
- A pushed commit is not a validated checkpoint until exact-head required checks pass.
