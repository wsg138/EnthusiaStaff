# ES-X03 active: paired Market static remediation

Date: 2026-09-20

Package: **ES-X03 — EnthusiaMarket destructive provider**

Worker state: **PARTIAL / ACTIONABLE_CONTINUATION**

## Current paired checkpoint

Staff [PR #139](https://github.com/wsg138/EnthusiaStaff/pull/139) is
OPEN/non-draft/UNSTABLE on `package/es-x03-market-provider` and remains the
aggregate leg. Market [PR #7](https://github.com/wsg138/EnthusiaMarket/pull/7)
is OPEN/DRAFT/CLEAN on `package/es-x03-market-static-remediation` and remains
the paired provider leg. Preserve unpaired Market PR #6 unchanged.

- Direct Staff `main`: `313add94027d16bcfe08529136972fe49f6746f5`.
- Staff PR #139: `99e46102a6e33a39d30a29470cd229c7439689b1`, a normal
  two-parent merge of immediate paired product parent
  `d97a082523012edfac05db1d75fbd59a92d9b253` and then-current `main`
  `c1054da6a8f89b312df2e05e25edc958fceda7ef`.
- Market PR #7: `a7534f2475a6bf298aff50a87cb132f34c7aa063`.
- Clean-clone component comparison: 512 shared files, no added, missing, or
  modified path, and hash
  `bc302a74a4c9acc69cba22947f46688d0c108a666cb896774655cc8eb09588c9`.
- `COMPONENT-METADATA.md` remains the only aggregate-only component file.

The recent paired work includes the confirmed Bedrock currency-shop repair:
valid SELL/BUY form submissions now use the existing validated-price fallback
instead of an invalid zero cost override. TRADE behavior, existing shops,
balances, migrations, Java menus, and historic project attribution remain
unchanged.

## Exact-head evidence

- Market runs `35524744229` (build, tests, shadow JAR, MariaDB verification,
  security, and Detekt) and `35524744279` (Wiki) passed.
- Staff Coverage `35526526618` / job `106119546309` passed on `99e46102`,
  including aggregate build/tests, runtime JAR inspection, coverage, and
  validation-artifact upload.
- Staff Sentinel artifact `35526526642` / job `106119546325` passed on the
  same SHA. Durable Sentinel job `463` returned `PAPER_RESTART_OK`: Paper
  reached readiness and stopped cleanly twice against one disposable state.
- No live Market or Staff review thread remains. CodeRabbit is skipped/manual,
  so it is not treated as an automated review approval.

## Remaining acceptance blockers

Codacy static check `106119707074` is `ACTION_REQUIRED`; current triage
records 1,129 findings. The result is not suppressed or called passing;
continue only individual, validated, paired remediation for findings that are
genuinely actionable.

Canonical Pi `35526525971` failed before private dispatch because its
workflow-history request received HTTP 401 `Bad credentials`. No private Pi,
Paper, or MariaDB runtime ran. The staging owner must rotate or replace
`ENTHUSIASTAFF_STAGING_TOKEN` with a least-privilege credential able to read
workflow history and dispatch the required private workflow. Do not use a
personal credential or bypass the public bridge.

Staff owns V20, X03 owns branch-local V21, and D09 reserves V22; their
registrar hunks remain disjoint. No production listing, balance, item, player
data, database, deployment, authority, LiteBans, cutover, or issue #43
acceptance changed.

## Exact next action

Continue a small paired remediation only after reviewing the specific finding,
add behavior-preserving regression coverage where needed, prove component
parity, merge current `main` normally before acceptance, and rerun every
affected exact-head gate. After the bridge credential changes, rerun canonical
Pi through the public workflow; do not dispatch the private workflow directly.
