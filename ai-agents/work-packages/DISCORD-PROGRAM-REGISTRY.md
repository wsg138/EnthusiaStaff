# Discord moderation program registry

Owner authorization: on 2026-08-23 the owner authorized continued implementation of the Discord moderation platform and explicitly authorized a sequential worker system to carry the project forward. This registry activates `ES-D02` through `ES-D15` as the dedicated Discord-program lane. It does **not** authorize production deployment, production data access, LiteBans cutover, Discord production configuration changes, or issue #43 acceptance; those remain separately gated by repository policy.

The product authority is `docs/discord-moderation-platform.md`. The sequencing authority is this file plus the individual `ES-Dxx` package files. Live GitHub always overrides stale status text.

Dedicated Discord-program workers must reconcile the global `PACKAGE-REGISTRY.md` and all live PRs/branches for conflicts, but they select implementation work only from this lane. Unrelated `ES-Xxx`, `ES-Pxx`, website, competition, or other work never preempts a Discord-program worker unless it creates an actual path/schema/protocol collision that makes the selected Discord package unsafe.

| ID | Package | Status | Priority | Depends on |
| --- | --- | --- | ---: | --- |
| `ES-D01` | Discord domain and identity contract | `COMPLETE` | 130 | owner activation |
| `ES-D02` | Discord persistence and migration schema | `COMPLETE` | 131 | `ES-D01` |
| `ES-D03` | Authorization and cross-platform policy | `COMPLETE` | 132 | `ES-D01`, `ES-D02` |
| `ES-D04` | Account linking and DiscordSRV migration | `ACTIVE` | 133 | `ES-D01`–`ES-D03` |
| `ES-D05` | Staff bot runtime foundation | `BLOCKED` / `PARKED_BLOCKED` | 134 | `ES-D01`–`ES-D03` |
| `ES-D06` | Read-only staff moderation UX | `PLANNED` | 135 | `ES-D04`, `ES-D05` |
| `ES-D07` | Discord punishment enforcement | `PLANNED` | 136 | `ES-D03`, `ES-D05`, `ES-D06` |
| `ES-D08` | Cross-platform moderation integration | `PLANNED` | 137 | `ES-D07` |
| `ES-D09` | Discord evidence, cases, notes and linked-alt alerts | `PLANNED` | 138 | `ES-D06`, `ES-D07` |
| `ES-D10` | AutoMod shadow engine | `PLANNED` | 139 | `ES-D05`, `ES-D09` |
| `ES-D11` | AutoMod enforcement and security locks | `PLANNED` | 140 | accepted `ES-D10` shadow evidence |
| `ES-D12` | Staff website Discord expansion | `PLANNED` | 141 | `ES-D02`, `ES-D07`, `ES-D09` |
| `ES-D13` | Discord role-sync replacement | `PLANNED` | 142 | `ES-D04`, `ES-D05` |
| `ES-D14` | Public bot and sanitized public API | `PLANNED` | 143 | sanitized public contracts and completed identity foundation |
| `ES-D15` | Discord migration/cutover acceptance | `PLANNED` | 144 | `ES-D01`–`ES-D14` as applicable |

## Active and parked packages

`ES-D04` has an independent live continuation on Staff PR #151 owned by another worker. D05 did not edit, synchronize, merge, renumber, or replace D04.

`ES-D05` is independently `BLOCKED` / `PARKED_BLOCKED`. Its previous staging-Discord acceptance blocker is cleared, but fresh mandatory post-reconciliation ordinary hosted validation cannot currently execute because the repository GitHub Actions scheduler is stalled.

## ES-D05 current record

Implementation branch: `package/es-d05-staff-bot-runtime`. Implementation PR: #160. Current package-record head: `6451ede1d6caeeeee19ac16eac86fbbe5570bff5`. Frozen reviewed D05 product source: `5f24ba1818c81e0a30a516fa70c8597586184b00`.

Canonical current handoff: `ai-agents/reports/package-handoffs/2026-08-26-es-d05-hosted-validation-blocked.md`.
Acceptance checkpoint: `ai-agents/reports/package-handoffs/2026-08-26-es-d05-discord-acceptance.md` on the preserved implementation branch.
Historical staging-blocked handoff: `ai-agents/reports/package-handoffs/2026-08-24-es-d05-staff-bot-runtime.md`.

The isolated Java 21 staff-bot runtime, packaging, health/readiness, bounded workload/replay primitives, exact application/guild/channel fences, lifecycle/shutdown behavior, tests, docs, and non-destructive `--smoke-test` are implemented. JDA 6.5.0 requests no privileged Gateway intents in D05. Tokens remain secret and are never committed, logged, requested in chat, exposed in artifacts, or placed on command lines.

### Required live Discord acceptance — PASS

Trusted staging workflow `wsg138/EnthusiaStaff-Staging` run `32926306691`, latest attempt 3 / job `98071453002`, completed successfully on trusted self-hosted runner `Lincoln-PI-4` (Linux/ARM64). Trusted staging-control head `03b3fce61bffe552d7905a4e4aa18e3015ea4e00` pins exact D05 product SHA `5f24ba1818c81e0a30a516fa70c8597586184b00`, requires Java 21, builds/verifies the runtime before secret scope, and executes the non-destructive `--smoke-test`.

Sanitized outcome: staging application `1541279616881397772` PASS; Enthusia guild `1410303324745371709` PASS; required test channel `1541286004298752091` PASS for view/send; readiness PASS; smoke exit 0; graceful close/shutdown PASS. The workflow sends no moderation action or test message and performs no Discord configuration change or production-data access. No bot-token value was inspected or exposed.

### Frozen product evidence

For exact frozen product `5f24ba1818c81e0a30a516fa70c8597586184b00`:

- Coverage/full Java 21 validation `32874248685` / job `97888464396`: success; aggregate JaCoCo 50.76% lines / 41.41% branches / 53.21% instructions; artifact `9573547679`, digest `sha256:c6f2df467085d811593c7100feb5a4c698a46e14432e92d401662dff9d43455c`.
- Staff Bot Configuration Cache `32874248800` / job `97888275507`: success twice with configuration-cache problems treated as failures.
- Sentinel Restart Artifact `32874248693`: success.
- Codacy: zero new issues, 63.04% diff coverage, +0.17% coverage variation.
- CodeRabbit: success after valid findings were fixed; all live inline threads resolved.
- Canonical Pi public `32879118794` and correlated private `32880103099` / job `97907230239`: success for the frozen Paper/Pi gate.

These results remain attributed only to the revisions that executed them; they are not substituted for the fresh executable validation required after current-main reconciliation.

### Current-main reconciliation

PR #160 first incorporated later canonical executable state through ordinary two-parent merge `9c99e78f520cd59e7e59506c37573ac9ad028d63`. Later `main` advances were documentation/orchestration/component-metadata only and were reconciled normally through `5dee27e700a5fdd0a78121a5fd16c863dac5e0dc`. Current package-record head `6451ede1d6caeeeee19ac16eac86fbbe5570bff5` differs after that executable synchronization only by D05 state/handoff tracking; empty trigger commit `6b12c9ba781cd85075df649d89a3a01e7245d6b7` had exactly zero changed files from its parent.

Canonical `main` at blocker publication start is `592778acc3c77f834359732e16ff12b7b1e881d4`, migration boundary V19. D05 adds no migration and does not consume D04's unmerged V20.

### Current hard blocker — GitHub Actions scheduling

Fresh executable validation after the executable reconciliation is mandatory. The repository Actions scheduler is currently not executing queued work: a repository-wide query returned 0 in-progress runs and 14 queued runs, including `main` workflow-dispatch run `32984827059` queued since 15:21 UTC. D05 Coverage runs `32984359237` and `32984371731` remain queued without jobs; configuration-cache run `32984361382` remained queued after retry; Sentinel run `32984723125` has no completed executable result; Pi runs including `32984459623` and `32984806337` remain queued/non-passing.

A bounded validation-only PR #167 used the identical implementation candidate with a fresh PR concurrency key. External GitHub integrations received the event (including an exact-head Codacy analysis), but GitHub Actions created no exact-head workflow run. PR #167 was closed without merge.

This is infrastructure-unavailable evidence, not a D05 product failure. It still blocks merge: `VALIDATION-POLICY.md` says queued or missing checks are not passing evidence and expressly forbids using the owner-approved infrastructure exception to excuse a missing ordinary GitHub-hosted build that the repository normally executes.

### Exact unblock

Resume D05 only after GitHub Actions scheduling materially changes and ordinary hosted jobs begin allocating again, or another already-authorized exact-head hosted execution path becomes executable. Do not repeatedly rerun the same queued jobs merely to change timestamps.

Then reconcile live `main`/PR #160, run fresh applicable full Java 21/Coverage, staff-bot configuration-cache, Sentinel, static/review, and canonical Pi gates on the exact executable tree, merge #160 normally only if every required gate is terminal and green, verify containment/cleanup, publish `COMPLETE`, and stop. Do not start D06 in the same worker.

## ES-D04 independent live continuation

D04 remains outside D05 ownership. Its implementation PR #151 is active. Live GitHub controls its current head/check/review state.

## Latest completion

`ES-D03 — Authorization and cross-platform policy` remains the latest completed Discord package until D05 actually merges and terminal publication completes.

`ES-D06` and `ES-D13` remain dependency-blocked until both D04 and D05 complete. D07+ remain sequenced behind their stated dependencies.

## Selection

Classify every incomplete `ES-Dxx` as `ACTIONABLE_CONTINUATION`, `PARKED_BLOCKED`, or `READY`. Select the highest-priority actionable continuation; otherwise select the lowest-priority dependency-complete ready package. Skip blocked packages. Each worker completes exactly one package and stops only at `COMPLETE` or a genuine externally blocked terminal state after publishing durable state.
