# ES-X03 active: paired Market static remediation

Date: 2026-09-18

Package: `ES-X03 — EnthusiaMarket destructive provider`

Worker state: `PARTIAL` / `ACTIONABLE_CONTINUATION`

## Routing and scope

A direct owner instruction selected the first bounded static-remediation path
recorded in the parked X03 state: paired standalone Market and aggregate Staff
remediation for valid provider debt. This does not authorize a broad Codacy
suppression, a private staging bypass, a credential substitution, production
access, or a new replacement X03 branch.

Existing Staff [PR #139](https://github.com/wsg138/EnthusiaStaff/pull/139) on
`package/es-x03-market-provider` is preserved as the aggregate leg. New Market
[PR #7](https://github.com/wsg138/EnthusiaMarket/pull/7) on
`package/es-x03-market-static-remediation` is the paired draft provider leg.
Separate unpaired Market PR #6 remains preserved and excluded.

## Starting and current references

- Staff `main`: `5edcb0c2abf49a836422d21cd02fec265b27e7e6`.
- Historical frozen Staff implementation head: `879eae12df35253cce6cd12179d5cef1afe95dd9`.
- Aggregate pre-mirror merge head: `2510cab70b764d8261010d7db626c84e97a65c1f`.
- Market `main`: `cc19fa966dcb155fa1743f5076fb5152e74bdf8f`.
- Current Market PR #7 head: `80cf7c32fdb1bda6242b91b3b82977fafb665e45`.

The aggregate worktree applies the exact standalone diff from `cc19fa9` through
`80cf7c3` under `components/enthusia-market/`. The only intended aggregate
extra remains `COMPONENT-METADATA.md`.

`component_sync.py compare` found no added, missing, or modified shared
entries at aggregate `414c5cae` and provider `80cf7c3`. Both trees produced
product hash `380210406c4039969c66cbadc234cb3071f9a31bcceae9c770efc8ea82682aea`.

## Completed remediation

- Updated `mkdocs-material` from 9.5.49 to 9.7.7 for the reported dependency
  vulnerability.
- Formatted all Market Markdown so the default Markdown rule set reports zero
  findings.
- Limited Detekt `TooManyFunctions` and Codacy Lizard complexity handling to
  Market test source only; production paths remain analyzed.
- Excluded only Market SQLite migrations from aggregate SQL Server-dialect
  engines.
- Refactored parser-facing complexity in Market search, auction config, wiki
  front-matter checks, shop edit/create plumbing, and web-sync helpers with
  focused behavior-preserving tests.

## Validation completed

At Market `80cf7c32fdb1bda6242b91b3b82977fafb665e45`:

- The CI-equivalent `test shadowJar jacocoTestReport --no-daemon` passed from
  a fresh Gradle user home with
  `C:\Dev\Enthusia\BuildDeps\EnthusiaMarket\LumaGuilds-2.1.24.jar`.
- `gradlew.bat detekt --no-daemon --console=plain` passed with the same pinned
  jar whose SHA-256 matches the public CI workflow.
- `mkdocs build --strict` passed.
- Default Markdown lint passed for all 79 Market Markdown files.
- `git diff --check` passed.

The previous exact-head hosted build stopped before compilation because JitPack
timed out resolving an unavailable Geyser snapshot. The provider now resolves
OpenCollab, its publisher repository, before JitPack. A fresh hosted result is
still required for the current head.

## Remaining work and blockers

Valid production complexity remains in stateful transaction, auction,
persistence, listener, and GUI paths. Continue only in bounded refactors with
targeted regression coverage. The Staff-specific RAC-table rule and two false
secret reports against dependency coordinates still need an authorized
path-scoped Codacy decision. Do not hide the full Market component, all
migrations, security tools, or Opengrep.

Canonical Pi is `NOT PASS`. Public supersession run `35168772060` and its retry
failed before private dispatch because public workflow-history access returned
HTTP 401 `Bad credentials`. No private Pi, Paper, or MariaDB runtime ran. The
staging owner must rotate or replace `ENTHUSIASTAFF_STAGING_TOKEN` with a
least-privilege credential that can read and dispatch the required private
Actions workflow. Do not use a personal credential or direct private dispatch.

## Exact next action

Inspect the resulting exact-head hosted/static/review checks and continue
remaining valid complexity in small batches. After the private bridge
credential is repaired, freeze the resulting heads and run all required
exact-head gates before normal merges and post-merge component parity.

No production listings, balances, items, player data, databases, deployment,
Discord configuration, authority, LiteBans, cutover, or issue #43 acceptance
changed.
