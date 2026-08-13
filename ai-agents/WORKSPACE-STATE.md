# Workspace state

Last updated: 2026-08-13

Live GitHub overrides stale records. Detailed package evidence remains in the registry, package records, canonical handoffs, and PR verification ledgers.

## Current routing

| Field | Value |
| --- | --- |
| Completed packages | `ES-P01`, `ES-P02`, `ES-P03`, `ES-P04`, `ES-P05`, `ES-P06`, `ES-P07`, `ES-P08`, `ES-P09`, `ES-P10`, `ES-P11`, `ES-X02`, `ES-X05`, `ES-R01`, `ES-R02`, `ES-V01` |
| Active package | `ES-X03 — EnthusiaMarket destructive provider`; claimed after live reconciliation from Staff `49e5aa999b43193181aafabbb75811c820fa03c7` and Market `bc24f1010642d6042307bc13a32fb33cc94e8883`. |
| Standalone candidate | Market `62408695063d03303026766befb065a0f1f51044`: API v1, V025 durable moderation journal/locks/fences, snapshot/checksum lifecycle, complete replay identity, human-reviewed hold, exact restoration, blacklist revisions, atomic acquisition/blacklist races, analyzer cleanup, and operator documentation. |
| Aggregate candidate | Staff last pushed head before final synchronization is `085a7d83264d36242cdbf1e90b31d16e83ef47ba`: exact contract mirror, V19 Staff journal, coordinator/recovery/alerts, confirmed `/marketcase` workflow, provider-outage status, and exact Market component import. |
| Pre-merge parity | Aggregate component and standalone Market candidate are exact with hash `8d27f4d9c64ca52feecd1df6200a45314610fa0df4b27da9d39b444152007c3b`; no added, missing, or modified files. State remains `SYNC_PENDING` until both PRs merge and parity is repeated. |
| Local validation | Staff's prior exact-head clean build passed 951 tests with 192 integration tests. Market `6240869` passed the complete clean Java 21 task graph over 120 suites/637 tests; the Windows run skipped five Docker cases and one unrelated remote-auth case, then a clean Docker-enabled Java 21 run executed all five MariaDB cases with zero skips. Final touched Market methods have zero new local analyzer findings; no rule or source path was suppressed. |
| Hosted validation | Staff Coverage, Wiki, Sentinel, and private Pi staging passed at `085a7d83`; the final aggregate synchronization is not pushed yet. Staff hosted Codacy remains `ACTION_REQUIRED` with 991 newly visible aggregate findings. Staff CodeRabbit skipped at its 545-file limit. Market's prior review findings were addressed or classified, but its incremental rerun was rate-limited and no final approval is claimed. PRs #139 and #3 remain open. |
| Downstream routing | `ES-X03` is `ACTIVE`; `ES-X04` remains `READY` but is not activated by this worker. `ES-X01` remains independently `BLOCKED` / `PARKED_BLOCKED`. |
| Production boundary | Representative destructive/latency/load acceptance remains `ES-V03`; issue #43 remains open/deferred and LiteBans remains authoritative. No production balance, deployment, cutover, or authority change is authorized. |
| Canonical handoff | `ai-agents/reports/package-handoffs/2026-08-13-es-x03-market-provider.md` |

## Historical record

PRs #133 and #135 accurately record the previously reviewed `b922c5af...` standalone tree and `a3b6f2f7...` aggregate merge. They remain superseded historical evidence because later review found and repaired two valid fail-closed state-ordering defects. Corrective PRs Currency #14 and Staff #137 are the current completion authority.
