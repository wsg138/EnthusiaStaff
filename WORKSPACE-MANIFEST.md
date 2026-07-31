# EnthusiaStaff workspace manifest

Last updated: 2026-07-31 (America/Indianapolis)

This manifest records repository, review, recovery, and validation state. Nothing
listed here has been deployed, released, applied to production data, or used to
replace LiteBans.

## Repository checkpoint

| Repository | Default branch | Working branch | Current checkpoint | Validation state | Current blockers |
| --- | --- | --- | --- | --- | --- |
| EnthusiaStaff | `main` at merged PR #21 commit `90cfb0eb809b2895d105193b7bddf33fd6f95aa0` | `section/punishment-request-notifications-recovery` for draft PR #27 | B1.3 terminal-delivery reconciliation, repeatable lifecycle occurrence identity, and connection-scoped alert persistence | PASS at exact implementation/test head `c14e654b80f1e8aabb737bb686ab963dc9f4f2b7`; Coverage run `30673652541` | B2 lifecycle transaction integration remains outstanding. Paper workers, Bukkit presentation, reconnect delivery, live Discord sending, staging, and production remain out of scope. |
| EnthusiaStaff-Staging | Separate repository; not inspected or modified | Unchanged | OUT OF SCOPE | NOT_RUN | Owned by the separate staging workflow/chat. |

## Merged PR #21 checkpoint

- Pull request: #21, **Expose durable punishment request interfaces**.
- Final head: `da531a4022d79935ed157c97c3260c42631d23f1`.
- Merge commit on `main`: `90cfb0eb809b2895d105193b7bddf33fd6f95aa0`.
- No deployment or staging claim is implied by the merge.

## Draft PR #27 checkpoint history

- Pull request: #27, **Add durable punishment request notifications and recovery**.
- Branch: `section/punishment-request-notifications-recovery`.
- Base: `90cfb0eb809b2895d105193b7bddf33fd6f95aa0`.
- PR remains open, draft, and unmerged.
- B1.1 validated implementation/test head:
  `80101acadbaa06d09958076611ef933f1cc4efde`.
- B1.1 validation: Coverage run `30603219197`, success; artifact
  `8782689224`.
- B1.2 validated head:
  `8dc1395a244b0e388eea51f71390497d400789ba`.
- B1.2 validation: Coverage run `30604253987`, success; artifact
  `8783056530`.
- B1.3 validated implementation/test head:
  `c14e654b80f1e8aabb737bb686ab963dc9f4f2b7`.
- B1.3 validation: Coverage run `30673652541`, success; job
  `91296363283`; artifact `8809706107`.

## B1.3 commit sequence

- `94e7ca769f02a002a01b9b68699f3a12aefcbb1b` — add terminal delivery and
  occurrence contracts plus forward-only Flyway V13.
- `38f2b78651338dc8f1931a7de11954e51578fdfe` — implement terminal delivery
  reconciliation, fenced cancellation, dead-letter recovery, and shared
  connection-scoped immutable alert persistence.
- `1fabd14e1bc8487b1347e1d629a53cb15f8430ad` — add occurrence, migration,
  cancellation, authorization-loss, cleanup, and recovery coverage.
- `d196ec7541009c3ed29ee78076a6c09a22a39aab` — correct the integration-test
  warning required by `-Werror`.
- `2c8983832708fe52dd45c320c29c546f68901047` — preserve compatibility for
  unrelated producers using the shared `staff_alerts` table.
- `fe6f03dec96f4c16619538b39cafb4251e67d5c3` — add bounded retry for
  transient audience claim contention.
- `c14e654b80f1e8aabb737bb686ab963dc9f4f2b7` — make audience fallback
  selection lock only recipient delivery rows while rechecking parent intent and
  current authorization through fixed SQL predicates.

## B1.3 state-machine checkpoint

`staff_alerts` remains the immutable lifecycle intent. `staff_alert_deliveries`
remains recipient-specific and now has these authoritative states:

- `PENDING`: eligible work available for a future recipient lease.
- `LEASED`: work owned by one fenced worker until `lease_until`.
- `DELIVERED`: presentation was acknowledged successfully.
- `CANCELLED`: terminally suppressed because the intent became obsolete or the
  recipient was no longer eligible; this is not represented as delivered or as a
  transport failure.
- `DEAD_LETTER`: retry policy was exhausted and explicit operational recovery or
  resolution is required.

Terminal reconciliation rules:

- Closing or expiring an intent changes its currently pending deliveries to
  `CANCELLED` in the same transaction.
- A still-valid lease may be acknowledged after parent intent expiry, preserving
  the cross-expiry acknowledgement rule.
- An expired lease under an active, unexpired parent returns to `PENDING`.
- An expired lease under a closed or expired parent becomes `CANCELLED`.
- Worker cancellation requires the exact delivery ID, matching lease owner,
  `LEASED` state, and an unexpired lease.
- A stale owner cannot cancel, acknowledge, or fail a reclaimed lease.
- Current audience authorization is checked during materialization and claim.
  Previously materialized pending work is cancelled as
  `RECIPIENT_INELIGIBLE` after rank loss or requester conflict.
- A later Bukkit worker can perform a synchronous final eligibility check and use
  fenced cancellation without falsely acknowledging presentation.
- Cancelled deliveries do not block retention cleanup.
- Unresolved dead letters do block cleanup. Operators may explicitly requeue an
  active-parent dead letter or resolve it to `CANCELLED` with a recorded reason.

## B1.3 lifecycle occurrence identity

- Deterministic alert keys now use the canonical `pra:v2` format.
- Non-repeatable lifecycle events use a request-revision occurrence.
- Repeatable `REQUEST_CLAIMED` events use the successful operation-lease fence
  token plus the immutable claiming reviewer UUID.
- Two later legitimate claims therefore produce distinct requester notifications.
- Replaying the same successful lease occurrence produces the same canonical key
  and no duplicate intent or direct delivery.
- Exact replay comparison includes occurrence key, lifecycle actor UUID, all
  audience/filter fields, schema version, creation time, and expiry.
- Wall-clock timestamps, display names, randomness, and secrets are not the sole
  claim occurrence identity.

## Flyway V13 checkpoint

V13 is forward-only. V11 and V12 were not edited or renumbered. V13:

- adds `occurrence_key` and `lifecycle_actor_id` to the shared `staff_alerts`
  table;
- backfills punishment-request rows with a stable legacy occurrence;
- keeps occurrence columns nullable at the shared-schema level so unrelated
  legacy alert producers remain compatible, while the punishment-request domain
  and JDBC writer require occurrence data;
- adds an event-occurrence lookup index;
- extends recipient delivery state with `CANCELLED`;
- adds `cancelled_at` and `cancel_reason`.

## Connection-scoped persistence

- `JdbcPunishmentRequestAlertWriter` owns immutable insertion, exact replay,
  direct-delivery creation, intent closure, and pending-child reconciliation on a
  caller-supplied JDBC `Connection`.
- The public `PunishmentRequestAlertStore` continues to open and manage its own
  transactions for worker-facing operations.
- Lifecycle transactions can reuse the existing connection without opening a
  nested transaction.
- Direct delivery creation remains atomic with immutable intent insertion.
- MariaDB duplicate-key error 1062 is considered a replay only after exact stored
  immutable-field comparison. Unrelated SQL exceptions propagate.
- Audience claim SQL uses fixed internal query variants and bound values; no
  user-controlled SQL fragment is accepted.

## B1.3 validation evidence

Exact implementation/test head: `c14e654b80f1e8aabb737bb686ab963dc9f4f2b7`.

- Workflow: Coverage run `30673652541`, success.
- Job: `91296363283`, success.
- Runtime: Temurin Java `21.0.11+10`.
- Exact command:

  ```bash
  ./gradlew clean build jacocoAggregateReport runtimeJars \
    --no-daemon \
    --no-build-cache \
    --no-configuration-cache \
    --console=plain
  ```

- Gradle result: `BUILD SUCCESSFUL in 2m 43s`; 49 actionable tasks, 40 executed
  and 9 up-to-date.
- MariaDB 11.8.3 Testcontainers executed, including V12-to-V13 migration,
  terminal reconciliation, lease recovery, stale-owner fencing,
  authorization-loss suppression, dead-letter policy, occurrence replay, and the
  prior B1.1/B1.2 suites.
- Aggregate JaCoCo: lines `34.19%`, branches `27.82%`, instructions `36.53%`.
- Exactly one Paper runtime JAR and one Velocity runtime JAR were found and
  passed ZIP integrity inspection.
- Twenty-four provider API source types were checked against each runtime JAR;
  provider API leakage was zero for both.
- Paper SHA-256:
  `bb2222ebc7260221caa73dcd4f8bd12237f3296146f19124df42c0f59c34ba8d`.
- Velocity SHA-256:
  `7a5b7a91c1777d71019442e3d1bc86610e8e11c64b44c705fc8060ce60126717`.
- Validation artifact: `8809706107`; artifact digest:
  `sha256:bfc69745443849f9ee3247cbcfa77c909dd86250aaca688ab71f4aed4e898a46`.
- Codacy coverage upload and final notification succeeded for the exact head.

## Codacy and review checkpoint

The accessible Codacy summary reported 39 new findings at the B1.3 head: 5
critical, 11 high, and 23 medium. Detailed authenticated per-finding data was not
available through the GitHub connector. The implementation review nevertheless
addressed the concrete high-risk pattern visible in the diff:

- runtime query construction was replaced with fixed internal SQL variants and
  bound parameters;
- no public API accepts SQL fragments;
- canonical replay remains strict rather than using broad `INSERT IGNORE`;
- shared-schema nullability was corrected instead of disabling the quality gate;
- all source compiles under `-Xlint:all -Werror` in the required build.

Remaining Codacy items must be classified from the refreshed exact-head report
when per-finding details are available. The global quality gate was not lowered or
disabled. CodeRabbit skipped review because PR #27 remains draft.

## Explicit B2 scope

B2 may now integrate the durable alert writer and Discord outbox insertion into
submission, claim, approval, denial, external fulfillment, and bounded expiration
transactions. It must remove read-triggered expiration mutation and keep reads as
reads. It must stop before Paper alert workers, Bukkit presentation, reconnect
listeners, recurring scheduler registration, and live Discord network delivery.

## Isolation and release rules

- Do not modify `main`.
- Do not modify `wsg138/EnthusiaStaff-Staging` or dispatch staging workflows.
- Do not access `Lincoln-PI-4`, Bloom, production databases, or production
  Discord credentials.
- Keep PR #27 draft and do not merge it.
- A pushed source commit is not a validated checkpoint until exact-head required
  checks pass.
- Unit tests and Testcontainers alone do not establish production readiness.
