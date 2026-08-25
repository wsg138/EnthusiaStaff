# Discord moderation program registry

Owner authorization: on 2026-08-23 the owner authorized continued implementation of the Discord moderation platform and explicitly authorized a sequential worker system to carry the project forward. This registry activates `ES-D02` through `ES-D15` as the dedicated Discord-program lane. It does **not** authorize production deployment, production data access, LiteBans cutover, Discord production configuration changes, or issue #43 acceptance; those remain separately gated by repository policy.

The product authority is `docs/discord-moderation-platform.md`. The sequencing authority is this file plus the individual `ES-Dxx` package files. Live GitHub always overrides stale status text.

Dedicated Discord-program workers must reconcile the global `PACKAGE-REGISTRY.md` and all live PRs/branches for conflicts, but they select implementation work only from this lane. Unrelated `ES-Xxx`, `ES-Pxx`, website, competition, or other work never preempts a Discord-program worker unless it creates an actual path/schema/protocol collision that makes the selected Discord package unsafe.

| ID | Package | Status | Priority | Depends on |
| --- | --- | --- | ---: | --- |
| `ES-D01` | Discord domain and identity contract | `COMPLETE` | 130 | owner activation |
| `ES-D02` | Discord persistence and migration schema | `COMPLETE` | 131 | `ES-D01` |
| `ES-D03` | Authorization and cross-platform policy | `COMPLETE` | 132 | `ES-D01`, `ES-D02` |
| `ES-D04` | Account linking and DiscordSRV migration | `BLOCKED` / `PARKED_BLOCKED` | 133 | `ES-D01`–`ES-D03` |
| `ES-D05` | Staff bot runtime foundation | `READY` | 134 | `ES-D01`–`ES-D03` |
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

## Active package

None. `ES-D04 — Account linking and DiscordSRV migration` is parked blocked with its implementation preserved on `package/es-d04-account-linking`, PR #151, frozen head `b231022b065b5843d2dd73811dfbf51acba6314b`.

## ES-D04 parked blocker

D04 implementation and targeted review are complete at the frozen head. Exact-head Coverage/full Java 21 build/Testcontainers/runtime-JAR/provider-leak/Codacy evidence passed in run `32738304907`, job `97466391922`, with validation artifact `9524397425` (`sha256:230de565c87f1939dd0f06f2bcb028a394d96e73e43237fb43b2f02adccbd6c8`). Sentinel artifact run `32738306003`, job `97466394689`, passed and produced artifact `9524138779` (`sha256:4f472f5a20c9d825ad7129bbf0bc4727740a4166f9d1c697c843df5b84020b67`). Sentinel durable job `225` ended exactly `PAPER_RESTART_OK` on the same frozen SHA.

Canonical Pi staging is nevertheless a separate required gate because D04 changes Paper runtime and MariaDB/Flyway persistence. The connected GitHub worker cannot discover the PR's automatic `pull_request_target` Pi run with the available workflow-listing surface, and no exact D04 public/private Pi run IDs are durably recorded elsewhere. No Pi pass is claimed and Sentinel does not substitute for it.

Unblock by obtaining or executing the canonical public `Pi Staging` path for exact source `b231022b065b5843d2dd73811dfbf51acba6314b`, verifying its correlated private `wsg138/EnthusiaStaff-Staging` execution on `Lincoln-PI-4` plus every required provenance/runtime/restart/cleanup assertion, then resolving remaining tracking-only review state and merging PR #151 normally. Canonical handoff: `ai-agents/reports/package-handoffs/2026-08-24-es-d04-account-linking-blocked.md`.

## Latest completion

`ES-D03 — Authorization and cross-platform policy` remains the latest completed Discord package through PR #149. Frozen validated merge-ready head: `5cd98a719e30eff64d1595f1e219ea70553c66c0`.

## Ready work while D04 is parked

`ES-D05 — Staff bot runtime foundation` remains dependency-complete and `READY`. A future Discord worker may select it while D04's exact blocker is unchanged. This D04 worker did not start D05. `ES-D06` and `ES-D13` remain dependency-blocked until both D04 and D05 complete.

## Selection

Classify every incomplete `ES-Dxx` as `ACTIONABLE_CONTINUATION`, `PARKED_BLOCKED`, or `READY`. Select the highest-priority actionable continuation; otherwise select the lowest-priority dependency-complete ready package. Skip blocked packages. A live overlapping website/competition/other worker may make only the overlapping package temporarily `PARKED_BLOCKED`; it does not authorize conflicting edits and does not prevent another independent Discord package from being selected when dependencies permit.

Each worker completes exactly one package and stops only at `COMPLETE` or a genuine externally blocked terminal state after publishing durable state. The worker must not stop merely because it produced a plan, opened a draft PR, reached a first checkpoint, or is waiting on ordinary CI that it can inspect in the same session.
