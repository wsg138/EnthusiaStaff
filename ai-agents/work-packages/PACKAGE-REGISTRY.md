# Package registry

Last updated: 2026-08-10

Live GitHub overrides stale text. Detailed historical evidence remains in package files and canonical handoffs; this registry is the current routing authority.

## Rules

- Classify every incomplete package before selection and work exactly one package per worker.
- Existing `ACTIONABLE_CONTINUATION` work takes priority over newly `READY` work.
- Skip `PARKED_BLOCKED` work until its exact external unblock condition materially changes.
- Never call skipped, queued, cancelled, superseded, wrong-revision, or failed validation passing evidence.
- Issue #43 remains open/deferred and LiteBans remains authoritative until separately approved.

## Canonical current state

`ES-P01`, `ES-P02`, `ES-P03`, `ES-P04`, `ES-P05`, `ES-P09`, `ES-P10`, `ES-P11`, `ES-X05`, `ES-R01`, `ES-R02`, and `ES-V01` are `COMPLETE`.

`ES-P07 — Inventory and Ender editing runtime completion` is `BLOCKED` / `PARKED_BLOCKED` with unmerged implementation PR #112 on `package/es-p07-inventory-runtime`, frozen reviewed head `b34aade6ae79c7aaada0ada3c87970f937b6db6a`. Product implementation, hosted Java/MariaDB validation, static analysis, coverage, Wiki and review closure are complete. Required Pi runtime gates remain externally unavailable: correlated private Pi run `31426646043` / job `93579820065` is queued without a runner assignment, and exact-head Sentinel restart job 75 is queued because host telemetry reports 120 MB available memory (<700 MB) and 82.3 C (>=80.0 C). No private Paper/MariaDB/Flyway cycle or terminal `PAPER_RESTART_OK` exists. Preserve PR/branch and do not merge.

`ES-P06` remains `READY`. `ES-X01` remains `BLOCKED` / `PARKED_BLOCKED` on the unresolved supported RoseChat integration repository/source contract. `ES-P08` remains dependency-blocked until ES-P07 completes.

## Canonical package index

| ID | Title | Status | Classification | Priority | Dependencies | Assignment / live work |
| --- | --- | --- | --- | ---: | --- | --- |
| `ES-P01` | Exact-sanction appeal isolation | `COMPLETE` | — | 10 | — | merged PR #68 |
| `ES-R01` | Billing-independent staging bridge recovery | `COMPLETE` | — | 15 | — | terminal evidence retained |
| `ES-R02` | Report integration fixture clock recovery | `COMPLETE` | — | 16 | — | merged PR #103 |
| `ES-P02` | Runtime database recovery and Velocity reload | `COMPLETE` | — | 20 | `ES-P01` | merged PR #70 |
| `ES-P03` | Bedrock identity correctness | `COMPLETE` | — | 30 | `ES-P02` | merged PR #75 |
| `ES-X05` | Website UX, authentication, and appeals | `COMPLETE` | — | 35 | `ES-P01` | merged PR #74 |
| `ES-P04` | Staff-mode operational tools | `COMPLETE` | — | 40 | `ES-P03` | merged PR #79 |
| `ES-P07` | Inventory and Ender editing runtime completion | `BLOCKED` | `PARKED_BLOCKED` | 45 | `ES-P02` | PR #112; branch `package/es-p07-inventory-runtime`; frozen head `b34aade6ae79c7aaada0ada3c87970f937b6db6a`; resume only after Pi runner/resource conditions materially change |
| `ES-P05` | Report evidence and staff workflow completion | `COMPLETE` | — | 50 | `ES-P03`, `ES-P04` | merged PR #81 |
| `ES-P09` | Alt and network-identity completion | `COMPLETE` | — | 55 | `ES-P03` | merged PR #84 |
| `ES-P06` | Discord notification delivery completion | `READY` | `READY` | 60 | `ES-P05` | dependency complete |
| `ES-P08` | Item confiscation and restoration | `PLANNED` | `PARKED_BLOCKED` | 70 | `ES-P07` | dependency blocked |
| `ES-P10` | Cheat tester and fake-entity system | `COMPLETE` | — | 80 | `ES-P04` | merged PR #86 |
| `ES-P11` | Fake-base generation and cleanup | `COMPLETE` | — | 90 | `ES-P10` | merged PR #88 |
| `ES-X01` | RoseChat provider and communication integration | `BLOCKED` | `PARKED_BLOCKED` | 100 | `ES-P03`, `ES-P04`, `ES-P05` | supported integration repository/source contract unresolved |
| `ES-X02` | EnthusiaCurrency destructive provider | `PLANNED` | `PARKED_BLOCKED` | 110 | `ES-P08` | dependency blocked |
| `ES-X03` | EnthusiaMarket destructive provider | `PLANNED` | `PARKED_BLOCKED` | 120 | `ES-P08`, `ES-X02` | dependency blocked |
| `ES-X04` | EnthusiaCommend reputation provider | `PLANNED` | `PARKED_BLOCKED` | 125 | `ES-P08`, `ES-X02` | dependency blocked |
| `ES-V01` | Private LiteBans representative-data verification | `COMPLETE` | — | 200 | — | merged PR #110; terminal evidence retained |
| `ES-V02` | Distributed and Java/Bedrock staging | `DEFERRED` | `PARKED_BLOCKED` | 250 | `ES-P06`, `ES-P09`, `ES-P11`, `ES-X01`, `ES-X03`, `ES-X04`, `ES-X05` | dependencies/private acceptance blocked |
| `ES-V03` | Destructive, latency, and load acceptance | `DEFERRED` | `PARKED_BLOCKED` | 260 | `ES-P08`, `ES-X02`, `ES-X03`, `ES-X04` | dependencies/private acceptance blocked |
| `ES-A01` | LiteBans cutover acceptance | `DEFERRED` | `PARKED_BLOCKED` | 300 | `ES-V01`, `ES-V02`, `ES-V03` | dependencies + owner authorization + issue #43 required |
| `ES-QA01` | Final repository and workflow audit | `PLANNED` | `PARKED_BLOCKED` | 400 | `ES-A01` | dependency blocked |

## ES-P07 blocked record

Frozen head `b34aade6ae79c7aaada0ada3c87970f937b6db6a` passed Validate Wiki `31426025633`; Coverage `31426025143` / job `93577825964` with Java 21 full tests and MariaDB/Testcontainers; runtime JAR/provider-leak inspection; aggregate coverage 47.14% lines / 38.24% branches / 49.80% instructions; Codacy static zero issues, diff coverage 31.58%, coverage variation -0.04%; CodeRabbit success with zero valid unresolved review threads. Exact Sentinel artifact `9077240364` and Java validation artifact `9077401417` are retained in the blocked handoff.

Exact unblock condition: resume ES-P07 as `ACTIONABLE_CONTINUATION` before starting new READY work when runner availability materially changes so the canonical private gate can execute and Sentinel host memory/temperature materially clears its gate. Final merge still requires exact-head canonical private Pi success/cleanup and terminal Sentinel `PAPER_RESTART_OK`. If queued runs become stale/cancelled/wrong-head, rerun the canonical exact-head gate; never reuse superseded evidence.

Canonical handoff: `ai-agents/reports/package-handoffs/2026-08-10-es-p07-inventory-runtime-blocked.md`.

## Next sequential action

A new worker must reconcile live state. If ES-P07's external unblock condition changed, resume ES-P07 first as `ACTIONABLE_CONTINUATION`. If it remains parked, skip it and select the highest-priority eligible READY package; currently that is ES-P06 at priority 60. Do not begin ES-P08 until ES-P07 is COMPLETE.
