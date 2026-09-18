# ES-X03 parked: static-analysis boundary and Pi bridge authentication

Date: 2026-09-18
Package: `ES-X03 — EnthusiaMarket destructive provider`
Terminal worker state: `BLOCKED` / `PARKED_BLOCKED`

## Scope and immutable references

- Canonical Staff `main` at reconciliation: `6201765f0e07b08f9b22ed7ce96838c01fa94007`.
- Existing Staff implementation: [PR #139](https://github.com/wsg138/EnthusiaStaff/pull/139), branch `package/es-x03-market-provider`, exact head `879eae12df35253cce6cd12179d5cef1afe95dd9`. The PR is open, non-draft, and mergeable; it is preserved unchanged.
- Standalone EnthusiaMarket `main`: `cc19fa966dcb155fa1743f5076fb5152e74bdf8f`. The aggregate component on preserved Staff PR #139 matched that standalone product tree at reconciliation, but canonical `main` remains `NOT_IMPORTED` until Staff merges and final post-merge parity remains required. Standalone [PR #6](https://github.com/wsg138/EnthusiaMarket/pull/6) is a separate, unpaired hardening proposal and is preserved; it is not absorbed, rebased, or merged as part of X03.

## Reconciliation and collision result

The former D04 serialization blocker is resolved. Staff `main` owns V20, X03 owns branch-local `V21__market_compliance_journal.sql`, and active D09 reserves V22. X03 and D09 both touch `PaperCommandRegistrar.java`, but their hunks are disjoint. There is no X03 exact-path overlap with the user-owned dirty root worktree.

Manual review of the frozen X03 head covered the journal migration, JDBC store, reconciliation coordinator, provider boundary, command surface, lifecycle maintenance, and focused unit/integration coverage. It found durable intent before provider mutation, idempotency and optimistic-revision handling, fail-closed provider writes, explicit approval before confiscation, and recovery that does not auto-approve. No new source defect was found; the implementation head is left frozen.

## Exact-head validation record

| Gate | Result | Evidence |
| --- | --- | --- |
| Diff hygiene | PASS | `git diff --check origin/main...879eae12df35253cce6cd12179d5cef1afe95dd9` produced no output. |
| Coverage/full build | PASS | GitHub Actions run `35168774609`, job `105035696843`, on exact X03 head. |
| Sentinel restart artifact | PASS | Run `35168774608`, job `105035696425`, on exact X03 head; the required manifest is present. |
| PR review | No live inline threads | CodeRabbit's successful status is a skipped/manual review status, not an automated full-review approval. |
| Codacy static analysis | NOT PASS | Check `105036637444` is `action_required` with 2,085 new issues. |
| Canonical Pi supersession | NOT PASS | Run `35168772060` failed before private dispatch: public workflow-history lookup returned HTTP 401 `Bad credentials`. No private Pi/Paper/MariaDB runtime ran. |
| Durable Sentinel restart | NOT RUN | A status request was posted at [PR #139 comment 5730170958](https://github.com/wsg138/EnthusiaStaff/pull/139#issuecomment-5730170958); no status response was observed at publication and no restart was requested. |

The static result is not dismissed wholesale. Of the 2,085 reports, 1,989 are under the aggregate Market component and 96 are Staff/agent Markdown. Some high findings are demonstrable analyzer mismatches (the Staff-specific RAC-table pattern against Market table names, SQLint against component SQLite migrations, and dependency-coordinate literals mistaken for keys), while the report also contains real component-owned complexity, Markdown, and dependency debt, including a Trivy medium finding. The root `.codacy.yml` must not be broadened to hide `components/enthusia-market/**`, and the frozen X03 integration package must not silently take ownership of a new paired provider remediation.

## Exact unblock and preservation rules

Keep PR #139 and `package/es-x03-market-provider`; do not create a replacement implementation branch. Before a fresh exact-head static check, the owner must choose and record one of these bounded paths:

1. authorize a paired standalone-Market and aggregate remediation scope for valid provider static debt; or
2. make a reviewed analyzer-boundary/configuration decision that preserves standalone Market validation and does not globally suppress valid component findings.

The Codacy configuration decision must be made at the supported repository/analyzer scope, including any required enablement of supported tool configuration; it cannot be guessed by adding a broad aggregate exclusion. Independently, restore the public Pi workflow-history authentication/effective permission used by the supersession bridge. Only after both conditions change should a worker freeze the resulting exact head and rerun all required hosted/static/manual-review/Sentinel/Pi gates. Merge #139 normally only when every required gate is terminal and green, then prove post-merge standalone↔aggregate parity and update component metadata.

No production listing, balance, item, player, database, deployment, Discord configuration, authority, LiteBans, or cutover state changed.
