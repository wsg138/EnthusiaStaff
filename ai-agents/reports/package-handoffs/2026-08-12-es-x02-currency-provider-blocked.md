# ES-X02 — EnthusiaCurrency destructive provider — blocked handoff

Date: 2026-08-12 America/Indiana/Indianapolis

## Routing

- Package: `ES-X02` only.
- Status: `BLOCKED` / `PARKED_BLOCKED`.
- Dependency `ES-P08`: complete.
- Staff start: `4831b1442e572914c86fd8e202e7de6f546868e2`.
- Currency start: `922223cfff8c325e36f58b6af6adf6d74e4a5417`.
- Same-ID branches: `package/es-x02-currency-provider` in both repositories.
- Currency PR #11: open, non-draft, current head `5d9dfc7f03d33ee2147141fef4c777ba0e67d939`.
- Staff aggregate product PR: not opened; aggregate branch remains at the package-start Staff state because standalone must merge before exact import.

## Durable implementation

The standalone provider now publishes API v1 through Bukkit ServicesManager, owns expiring operation leases, snapshots exact bank/inventory/Ender Chest assets with SHA-256 state checksums and persistent bank revision, plans source-ordered exact debits, rejects stale state, supports idempotent apply/restore, advances restore revisions monotonically, blocks normal inventory movement while leased, and returns quarantine outcomes when durable flush or exact compensation cannot be proven. SQLite balance persistence now carries revisions, and regression tests cover lease ownership/expiry, exact denomination allocation, and revision persistence/restart upgrade.

A manual harsh review found one valid defect after the first green build: physical compensation could report `FAILED_ROLLED_BACK` even if restoring the physical state failed. The branch was repaired so compensation re-observes the exact account state; only a verified exact rollback returns `FAILED_ROLLED_BACK`, otherwise the operation returns `QUARANTINE_REQUIRED`.

## Exact-head validation

- Frozen candidate/current executable head: `5d9dfc7f03d33ee2147141fef4c777ba0e67d939`.
- Currency configured hosted suite: run `31657088614` passed on that exact head with Temurin Java 21 and `mvn -B -ntp verify`.
- The prior bot-authored compensation-fix head produced `action_required` rather than product evidence; a content-identical user-authored commit retriggered the exact-head suite. No skipped/action-required run is called a pass.
- CodeRabbit final automated review could not execute because the service reported a temporary review-rate limit. This is not labeled passing and does not waive manual review.
- Codacy PR summary currently reports 29 new findings: 2 critical security, 1 high performance, and 26 medium. The GitHub-visible summary exposes aggregate counts and an external link, not individual finding details. Those findings therefore cannot honestly be classified valid/invalid from the available evidence.
- Canonical Pi has not run. Aggregate hosted validation has not run. No staging or runtime pass is claimed.
- Representative destructive balances/private destructive acceptance remain excluded here and assigned to `ES-V03` by the original package contract.

## Blocker and exact resume action

`VALIDATION-POLICY.md` requires every valid Codacy/static finding resolved before merge. The current tool-visible GitHub evidence is insufficient to inspect the 29 findings individually, so ES-X02 is parked rather than merged around unknown critical/high findings.

Exact unblock condition: the individual Codacy findings for Currency PR #11 become accessible through a usable evidence path. Resume ES-X02 immediately when that changes; inspect every finding, fix every valid issue or record a concrete invalid disposition, rerun static analysis and harsh review on the unchanged/final head, then perform canonical Pi and the remaining standalone merge → exact aggregate import → aggregate PR/gates/merge → parity/cleanup sequence.

## Systems not to disturb

Preserve historical Currency PRs #1–#9 and unrelated Staff work. Do not alter LiteBans authority, issue #43, production routes/data, migration history, or other packages. Do not merge either ES-X02 product side one-sided, squash/rebase/force-push, or claim missing Pi/static/staging evidence as passing.
