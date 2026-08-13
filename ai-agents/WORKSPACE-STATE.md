# Workspace state

Last updated: 2026-08-13

Live GitHub overrides stale records. Detailed package evidence remains in the registry, package records, canonical handoffs, and PR verification ledgers.

## Current routing

| Field | Value |
| --- | --- |
| Completed packages | `ES-P01`, `ES-P02`, `ES-P03`, `ES-P04`, `ES-P05`, `ES-P06`, `ES-P07`, `ES-P08`, `ES-P09`, `ES-P10`, `ES-P11`, `ES-X05`, `ES-R01`, `ES-R02`, `ES-V01` |
| Active package | `ES-X02 — EnthusiaCurrency destructive provider`; `ACTIVE` / `ACTIONABLE_CONTINUATION` after a post-merge correctness review. |
| Standalone correction | Currency PR #14 merged normally as `2b4c8bf6d8e8ef1c8c6b042cd3147e66ffc660fe`. It validates removal plans before accepting committed replay and requires a monotonic bank revision before treating assets as already restored. |
| Aggregate continuation | The exact corrected Currency tree is imported on `package/es-x02-currency-provider`. Staff PR #133 and completion PR #135 remain historical merges; a follow-up Staff PR and new exact-head gates are pending. |
| Candidate parity | Pre-merge `tools/component-sync/component_sync.py compare` reports `parity: true` with standalone and aggregate hash `c5820e3121372f81c8611de9b6015f77e28f5c2160037da035f650660ed090eb` and no added, missing, or modified product files. Component metadata is `SYNC_PENDING`. |
| Local validation | Standalone and aggregate component Java 21 Maven verification passed 11 tests. The Staff Java 21 clean task graph completed with daemon exit 0: 218 suites / 936 tests, including 48 MariaDB Testcontainers suites / 189 tests, with zero failures, errors, or skips. Focused PMD 7 and Lizard report zero findings. |
| Required remaining gates | Commit/push the reconciled branch, open the follow-up Staff PR, pass exact-head hosted build/static/review/Sentinel/canonical-Pi gates, merge normally, prove post-merge parity, and republish terminal state. |
| Downstream routing | `ES-X03` and `ES-X04` are parked until ES-X02 is complete again. `ES-X01` remains independently `BLOCKED` / `PARKED_BLOCKED`. |
| Production boundary | Representative destructive/latency/load acceptance remains `ES-V03`; issue #43 remains open/deferred and LiteBans remains authoritative. No production balance, deployment, cutover, or authority change is authorized. |
| Canonical handoff | `ai-agents/reports/package-handoffs/2026-08-13-es-x02-currency-provider-followup.md` |

## Prior completion publication

PRs #133 and #135 accurately record the previously reviewed `b922c5af...` standalone tree and `a3b6f2f7...` aggregate merge. They are superseded as current routing authority because later review found and repaired two valid fail-closed state-ordering defects. Their evidence remains historical and is not relabeled.
