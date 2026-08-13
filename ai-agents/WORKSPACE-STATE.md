# Workspace state

Last updated: 2026-08-13

Live GitHub overrides stale records. Detailed package evidence remains in the registry, package records, canonical handoffs, and PR verification ledgers.

## Current routing

| Field | Value |
| --- | --- |
| Completed packages | `ES-P01`, `ES-P02`, `ES-P03`, `ES-P04`, `ES-P05`, `ES-P06`, `ES-P07`, `ES-P08`, `ES-P09`, `ES-P10`, `ES-P11`, `ES-X02`, `ES-X05`, `ES-R01`, `ES-R02`, `ES-V01` |
| Active package | `ES-X03 — EnthusiaMarket destructive provider`; claimed after live reconciliation from Staff `49e5aa999b43193181aafabbb75811c820fa03c7` and Market `bc24f1010642d6042307bc13a32fb33cc94e8883`. |
| Standalone candidate | Market `daed4d08d96f69f4513431c8bff8b90ada8faa70`: API v1, V025 durable moderation journal/locks/fences, snapshot/checksum lifecycle, human-reviewed hold, exact restoration, blacklist revisions, race tests, analyzer cleanup, and operator documentation. |
| Aggregate candidate | Staff committed head before final docs/state publication is `1034efc817fb95b9587cff00cd63b5b90e8cd009`: exact contract mirror, V19 Staff journal, coordinator/recovery/alerts, confirmed `/marketcase` workflow, provider-outage status, and exact Market component import. |
| Pre-merge parity | Aggregate component and standalone Market candidate are exact with hash `761b6e1e6168782b752cca5bffe6ca8b9330694b38f13b9c19d3a82dbecdaf67`; no added, missing, or modified files. State remains `SYNC_PENDING` until both PRs merge and parity is repeated. |
| Local validation | Focused Market and Staff tests pass. Disposable MariaDB provider/journal/upgrade tests passed on implementation checkpoints. Local Codacy delta analysis reports PMD/Opengrep/Trivy zero and no new Lizard findings. Final exact-head clean builds and MariaDB reruns remain. |
| Hosted validation | Not yet run on the final ES-X03 heads. No Codacy branch grade, CI result, CodeRabbit approval, Pi staging result, or merge approval is claimed. |
| Downstream routing | `ES-X03` is `ACTIVE`; `ES-X04` remains `READY` but is not activated by this worker. `ES-X01` remains independently `BLOCKED` / `PARKED_BLOCKED`. |
| Production boundary | Representative destructive/latency/load acceptance remains `ES-V03`; issue #43 remains open/deferred and LiteBans remains authoritative. No production balance, deployment, cutover, or authority change is authorized. |
| Canonical handoff | `ai-agents/reports/package-handoffs/2026-08-13-es-x03-market-provider.md` |

## Historical record

PRs #133 and #135 accurately record the previously reviewed `b922c5af...` standalone tree and `a3b6f2f7...` aggregate merge. They remain superseded historical evidence because later review found and repaired two valid fail-closed state-ordering defects. Corrective PRs Currency #14 and Staff #137 are the current completion authority.
