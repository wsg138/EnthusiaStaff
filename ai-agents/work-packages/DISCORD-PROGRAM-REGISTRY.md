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
| `ES-D05` | Staff bot runtime foundation | `MERGE_PENDING` | 134 | `ES-D01`–`ES-D03` |
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

## Active and merge-pending packages

`ES-D04` has an independent live continuation on Staff PR #151. D05 does not edit, merge, rebase, or take over D04.

`ES-D05` resumed as an `ACTIONABLE_CONTINUATION` after its external staging-acceptance blocker materially changed. The required real Discord smoke is now successful. PR #160 is reconciled with current `main` and is awaiting fresh exact-head gates required by the aggregate executable-state change before normal merge.

## ES-D05 current record

Implementation branch: `package/es-d05-staff-bot-runtime`. Implementation PR: #160. Frozen reviewed D05 product head: `5f24ba1818c81e0a30a516fa70c8597586184b00`.

Canonical current acceptance handoff: `ai-agents/reports/package-handoffs/2026-08-26-es-d05-discord-acceptance.md`.
Historical blocked handoff: `ai-agents/reports/package-handoffs/2026-08-24-es-d05-staff-bot-runtime.md`.

The isolated Java 21 staff-bot runtime, packaging, health/readiness, bounded workload/replay primitives, exact application/guild/channel fences, lifecycle/shutdown behavior, tests, docs, and non-destructive `--smoke-test` are implemented. JDA 6.5.0 uses no privileged Gateway intents in D05. Tokens remain secret and are never committed, logged, requested in chat, exposed in artifacts, or placed on command lines.

### Required live Discord acceptance — PASS

Trusted staging workflow `wsg138/EnthusiaStaff-Staging` run `32926306691`, latest attempt 3 / job `98071453002`, completed successfully on trusted self-hosted runner `Lincoln-PI-4` (Linux/ARM64). The workflow revision `03b3fce61bffe552d7905a4e4aa18e3015ea4e4aa18e3015ea4e00` is not the canonical identifier; the exact trusted staging-control head is `03b3fce61bffe552d7905a4e4aa18e3015ea4e00`. It pins the D05 source to exact SHA `5f24ba1818c81e0a30a516fa70c8597586184b00`, requires Java 21, builds/verifies the runtime before secret scope, and executes the non-destructive `--smoke-test`.

Sanitized outcome: staging application `1541279616881397772` PASS; Enthusia guild `1410303324745371709` PASS; required test channel `1541286004298752091` PASS for view/send; readiness PASS; smoke exit 0; graceful close/shutdown PASS. The workflow sends no moderation action or test message and performs no Discord configuration change or production-data access. No bot-token value was inspected or exposed.

### Frozen product evidence

- Coverage/full validation `32874248685` / job `97888464396`: success; aggregate JaCoCo 50.76% lines / 41.41% branches / 53.21% instructions; artifact `9573547679`, digest `sha256:c6f2df467085d811593c7100feb5a4c698a46e14432e92d401662dff9d43455c`.
- Staff Bot Configuration Cache `32874248800` / job `97888275507`: success twice with configuration-cache problems treated as failures.
- Sentinel Restart Artifact `32874248693`: success.
- Codacy: zero new issues, 63.04% diff coverage, +0.17% coverage variation.
- CodeRabbit: success after valid findings were fixed; all currently visible inline threads resolved.
- Canonical Pi public `32879118794` and correlated private `32880103099` / job `97907230239`: success for the Paper/Pi gate.

### Current-main reconciliation

Canonical Staff `main` `37a2073b535cf32f89b2fc075699dca4e3420408` was merged normally into #160 through two-parent merge commit `9c99e78f520cd59e7e59506c37573ac9ad028d63`. The exact diff against that main head is D05-only: staff-bot source/tests/build integration, its configuration-cache workflow, and runtime documentation. D04's V20 migration/shared files, X03, website/Wiki, and provider work remain independent.

Because current `main` gained unrelated executable changes after the frozen D05 validation, fresh applicable exact-head hosted gates are required before merge. The successful live Discord acceptance remains attributed only to the exact frozen D05 product source it actually executed.

## ES-D04 independent live continuation

D04 remains outside D05 ownership. Its implementation PR #151 is active. Live GitHub controls its current head/check/review state. D05 did not modify, synchronize, merge, or replace D04 or its staging-control work.

## Latest completion

`ES-D03 — Authorization and cross-platform policy` remains the latest completed Discord package until #160 actually merges and D05 terminal publication completes.

`ES-D06` and `ES-D13` remain dependency-blocked until both D04 and D05 complete. D07+ remain sequenced behind their stated dependencies.

## Selection

Classify every incomplete `ES-Dxx` as `ACTIONABLE_CONTINUATION`, `PARKED_BLOCKED`, or `READY`. Select the highest-priority actionable continuation; otherwise select the lowest-priority dependency-complete ready package. Skip blocked packages. Each worker completes exactly one package and stops only at `COMPLETE` or a genuine externally blocked terminal state after publishing durable state.
