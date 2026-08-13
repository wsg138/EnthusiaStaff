# Workspace state

Last updated: 2026-08-13

Live GitHub overrides stale records. Detailed package evidence remains in the registry, package records, canonical handoffs, and PR verification ledgers.

## Current routing

| Field | Value |
| --- | --- |
| Completed packages | `ES-P01`, `ES-P02`, `ES-P03`, `ES-P04`, `ES-P05`, `ES-P06`, `ES-P07`, `ES-P08`, `ES-P09`, `ES-P10`, `ES-P11`, `ES-X02`, `ES-X05`, `ES-R01`, `ES-R02`, `ES-V01` |
| Active package | `ES-X03 — EnthusiaMarket destructive provider`; claimed after live reconciliation from Staff `49e5aa999b43193181aafabbb75811c820fa03c7` and Market `bc24f1010642d6042307bc13a32fb33cc94e8883`. |
| Standalone correction | Currency PR #14 merged normally as `2b4c8bf6d8e8ef1c8c6b042cd3147e66ffc660fe`. It validates removal plans before accepting committed replay and requires a monotonic bank revision before treating assets as already restored. |
| Aggregate correction | Staff PR #137 merged normally as `2150ac1d01849bd67ee97478f64cbcba31e5dc7f` from frozen head `88bd314da7224a64e6912ab2faa76f9548180584`. Containment is exact and the implementation branch is deleted. |
| Post-merge parity | `tools/component-sync/component_sync.py compare` reports `parity: true` for aggregate `2150ac1d...` and standalone `2b4c8bf...`, with identical hash `c5820e3121372f81c8611de9b6015f77e28f5c2160037da035f650660ed090eb` and no added, missing, or modified product files. Component metadata is `IN_SYNC`. |
| Local validation | Standalone and aggregate component Java 21 Maven verification passed 11 tests. The Staff Java 21 clean task graph completed with daemon exit 0: 218 suites / 936 tests, including 48 MariaDB Testcontainers suites / 189 tests, with zero failures, errors, or skips. Focused PMD 7 and Lizard report zero findings. |
| Hosted validation | Coverage/full build `31697097557`, Sentinel artifact `31697114562`, Codacy zero-new-issue gates, canonical Pi public run `31697114883`, and correlated private run `31697709094` passed on the frozen head. Zero review threads remain; CodeRabbit was rate-limited and no approval is claimed. |
| Downstream routing | `ES-X03` is `ACTIVE`; `ES-X04` remains `READY` but is not activated by this worker. `ES-X01` remains independently `BLOCKED` / `PARKED_BLOCKED`. |
| Production boundary | Representative destructive/latency/load acceptance remains `ES-V03`; issue #43 remains open/deferred and LiteBans remains authoritative. No production balance, deployment, cutover, or authority change is authorized. |
| Canonical handoff | `ai-agents/reports/package-handoffs/2026-08-13-es-x03-market-provider.md` |

## Historical record

PRs #133 and #135 accurately record the previously reviewed `b922c5af...` standalone tree and `a3b6f2f7...` aggregate merge. They remain superseded historical evidence because later review found and repaired two valid fail-closed state-ordering defects. Corrective PRs Currency #14 and Staff #137 are the current completion authority.
