# ES-X03 resumed validation evidence — 2026-09-04

Status: `IN_PROGRESS`

This record supersedes the historical Discord-serialization blocker for the current ES-X03 continuation. It does not declare the package complete.

## Current serialization state

- Staff `main` reconciled into the existing ES-X03 branch with normal merge history.
- Staff Market compliance migration is serialized forward as `V21__market_compliance_journal.sql` after the Discord V20 migration.
- Shared Staff runtime, persistence, command, and plugin wiring preserve both current-main Discord behavior and ES-X03 Market integration.

## Aggregate Market parity reconciliation

The authoritative standalone component is `wsg138/EnthusiaMarket` at merged `main` commit `cc19fa966dcb155fa1743f5076fb5152e74bdf8f` (including merged hardening PR #5).

A guarded temporary validation workflow compared every standalone tracked path against `components/enthusia-market/`, allowing only the aggregate-owned `COMPONENT-METADATA.md` exception. The initial guard refused to push when it found additional drift. After expanding only to the paths proven divergent, the final guard run `33841890778` passed:

- frozen Staff source head verification;
- exact standalone checkout at `cc19fa966dcb155fa1743f5076fb5152e74bdf8f`;
- bounded synchronization of proven divergent paths;
- full standalone-to-aggregate content parity;
- no unexpected aggregate-only product paths;
- `git diff --check`;
- exact bounded-diff verification;
- fast-forward product-branch commit/push.

That run produced Staff commit `8c4dd16227d234afa5d22198ccc01e7d629fcebf` (`ES-X03: sync current Market hardening`).

The reconciliation also found that Staff's root `*.sql` ignore rule prevented newly mirrored standalone Market migrations from being tracked. The product commit adds only the narrow repository-owned allowlist `!components/enthusia-market/src/main/resources/migrations/*.sql`, so V025–V028 and future files in that exact migration directory remain trackable without weakening private SQL/export ignores elsewhere.

## Validation still required

The first automatic Coverage and Sentinel workflow records for `8c4dd16227d234afa5d22198ccc01e7d629fcebf` were `action_required` without jobs because that commit was authored by the GitHub Actions parity guard. They are not counted as passes. This evidence commit is intentionally user-authored through the repository API so canonical pull-request validation can execute on the new exact head.

Before ES-X03 can become complete, the final exact head still requires all applicable build/test/static/coverage/review gates, zero new valid Codacy findings, trusted Pi staging acceptance, final parity verification, normal PR merge, containment/diagnostic cleanup, and terminal registry/evidence publication.

## Production boundary

No production listing, balance, item, player row, database, migration/import execution, deployment, Discord configuration, website state, LiteBans authority, or cutover is changed by this validation record. Issue #43 remains deferred and LiteBans remains authoritative.