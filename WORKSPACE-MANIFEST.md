# EnthusiaStaff workspace manifest

Last updated: 2026-07-30 (America/Indianapolis)

This manifest records review, recovery, and validation state. Nothing listed here
has been deployed, released, applied to production data, or used to replace
LiteBans.

## Repository checkpoint

| Repository | Default branch | Working branch | Current checkpoint | Validation state | Current blockers |
| --- | --- | --- | --- | --- | --- |
| EnthusiaStaff | `main` at merged PR #21 commit `90cfb0eb809b2895d105193b7bddf33fd6f95aa0` | `section/punishment-request-notifications-recovery` for draft PR #27 | Checkpoint A validated at `ccbd7806452c4dc5084fd03d76a496da324e6a87`; Checkpoint B1 adds hardened alert contracts, deterministic keys, Flyway V11, JDBC store primitives, and MariaDB integration coverage | Checkpoint A Coverage run `30599247739` succeeded. B1 exact-head validation is required before B1 can be recorded as tested. | PR #27 must remain draft and unmerged. B2 lifecycle transaction wiring, workers, Paper delivery, and Discord production remain outstanding. |
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
- Checkpoint A validation: Coverage run `30599247739`, success; Java 21 build,
  MariaDB Testcontainers, Paper/Velocity runtime inspection, 24 provider source
  checks per runtime jar, and zero provider API leakage; artifact `8781297271`.
- B1 commit sequence:
  - `db18a837f6f0b0f408904d6c8c964e19b72d62b7`: alert contract invariants,
    existing case visibility, deterministic SHA-256 intent keys, and domain tests.
  - `5acc8df89f7293cbb710e31938e3589f427951db`: Flyway V11 and durable JDBC
    alert-store primitives.
  - The final B1 test/documentation commit is recorded after it is pushed.
- B1 deliberately does not wire request submission, claim, approval, denial,
  external fulfillment, bounded expiration transitions, Paper listeners,
  schedulers, Bukkit delivery, or Discord network delivery.

## Current staff_alerts schema checkpoint

Before V11, `staff_alerts` contained:

- `alert_id`, `recipient_id`, `minimum_rank`, `alert_type`, `payload_json`,
  `created_at`, and `read_at`;
- unread indexes by direct recipient and minimum rank.

Flyway V11 evolves that table with deterministic intent identity, request and
lifecycle metadata, direct-or-audience targeting, visibility and schema version,
independent delivery state, durable leases, retry/dead-letter state, delivery and
retention timestamps, and indexes for direct/audience due work, lease recovery,
request lookup, retention, and unique intent keys. `read_at` remains distinct from
`delivered_at`; insertion sets neither field.

## Isolation and release rules

- Do not modify `wsg138/EnthusiaStaff-Staging` or staging PR #2.
- Do not dispatch staging workflows or access `Lincoln-PI-4`.
- Do not modify Pi services/files, GitHub staging environments, or secrets.
- Do not access production databases or production Discord credentials.
- Keep PR #27 draft and do not merge it without explicit authorization.
- A pushed commit is not a validated checkpoint until exact-head required checks pass.
