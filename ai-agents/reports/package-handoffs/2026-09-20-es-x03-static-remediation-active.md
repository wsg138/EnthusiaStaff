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

- Direct Staff `main`: `63e920d1b9cc95491cc4950abf944e7efb1d3b6a`.
- Staff PR #139: `47b0b13cb1fd3786fc2743ee8765cc322a6d7a0f`. Its parent
  `f69aef6f451c85a660fe5e1d7326090c51b87da7` is a normal merge of the prior
  paired head `99e46102a6e33a39d30a29470cd229c7439689b1` and then-current
  `main` `63e920d1b9cc95491cc4950abf944e7efb1d3b6a`.
- Market PR #7: `9f4a4145ab3628831edc27a534c4420faa5e23f1`.
- Clean-clone component comparison: 512 shared files, no added, missing, or
  modified path, and hash
  `d266bb0039f9b7e60cf59b253b52de15e60cd42b17a0c425bbe3ad98896bbd52`.
- `COMPONENT-METADATA.md` remains the only aggregate-only component file.

The recent paired work includes the confirmed Bedrock currency-shop repair:
valid SELL/BUY form submissions now use the existing validated-price fallback
instead of an invalid zero cost override. TRADE behavior, existing shops,
balances, migrations, Java menus, and historic project attribution remain
unchanged.

The latest paired listener repair filters off-hand interaction events, which
prevents duplicate create flows when Paper emits per-hand interaction events.
It preserves the existing validation order and adds direct Java-route,
Bedrock-route, empty-hand, off-hand, and missing-target regression coverage.

## Exact-head evidence

- Market runs `35542232664` (build, tests, shadow JAR, MariaDB verification,
  security, and Detekt) and `35542232684` (Wiki) passed.
- Staff Coverage `35542245591` / job `106161876789` passed on `47b0b13`,
  including aggregate build/tests, runtime JAR inspection, coverage, and
  validation-artifact upload.
- Staff Sentinel artifact `35542245567` / job `106161876827` passed on the
  same SHA. Sentinel comment `5753258198` admitted durable job `476`, which
  returned `PAPER_RESTART_OK`: Paper reached readiness and stopped cleanly
  twice against one disposable state.
- Clean component validation passed locally in both repositories: focused
  listener tests, full test/shadow-JAR/JaCoCo runs, Detekt, and Lizard. The
  clean-clone comparison confirms the exact shared hash above.
- No live Market or Staff review thread remains. CodeRabbit is skipped/manual,
  so it is not treated as an automated review approval.

## Remaining acceptance blockers

Codacy static check `106161999175` is `ACTION_REQUIRED` with 1,126 reported
issues. Diff coverage `106163053583` passed at 57.66% with no configured gate.
The static result is not suppressed or called passing;
continue only individual, validated, paired remediation for findings that are
genuinely actionable.

Canonical Pi `35542244424` / job `106161874455` failed before private dispatch because its
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
parity, and rerun affected exact-head gates. After the bridge credential
changes, rerun canonical Pi through the public workflow; do not dispatch the
private workflow directly.
