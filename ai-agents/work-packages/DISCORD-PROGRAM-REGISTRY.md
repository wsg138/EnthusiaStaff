# Discord moderation program registry

Owner authorization: on 2026-08-23 the owner authorized continued implementation of the Discord moderation platform and explicitly authorized a sequential worker system to carry the project forward. This registry activates `ES-D02` through `ES-D15` as the dedicated Discord-program lane. It does **not** authorize production deployment, production data access, LiteBans cutover, Discord production configuration changes, or issue #43 acceptance; those remain separately gated by repository policy.

The product authority is `docs/discord-moderation-platform.md`. The sequencing authority is this file plus the individual `ES-Dxx` package files. Live GitHub always overrides stale status text.

Dedicated Discord-program workers must reconcile the global `PACKAGE-REGISTRY.md` and all live PRs/branches for conflicts, but they select implementation work only from this lane. Unrelated `ES-Xxx`, `ES-Pxx`, website, competition, or other work never preempts a Discord-program worker unless it creates an actual path/schema/protocol collision that makes the selected Discord package unsafe.

| ID | Package | Status | Priority | Depends on |
| --- | --- | --- | ---: | --- |
| `ES-D01` | Discord domain and identity contract | `COMPLETE` | 130 | owner activation |
| `ES-D02` | Discord persistence and migration schema | `COMPLETE` | 131 | `ES-D01` |
| `ES-D03` | Authorization and cross-platform policy | `READY` | 132 | `ES-D01`, `ES-D02` |
| `ES-D04` | Account linking and DiscordSRV migration | `PLANNED` | 133 | `ES-D01`–`ES-D03` |
| `ES-D05` | Staff bot runtime foundation | `PLANNED` | 134 | `ES-D01`–`ES-D03` |
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

## Latest completion

`ES-D02` completed on PR #148 from `package/es-d02-discord-persistence`. Its frozen product head is `9f0d9bb44ab6929bec3bf652e0c9b3467104b423`; exact-head Java 21/MariaDB/Testcontainers, runtime-JAR, Codacy, Sentinel and CodeRabbit gates passed with zero live review threads. V19 remained collision-free because live `main` still ended at V18 immediately before terminal publication. No website, competition, production Discord, deployment, LiteBans authority or issue #43 cutover path changed.

`ES-D03` is now the lowest-priority dependency-complete Discord package and is `READY` for a fresh Discord-program worker after live reconciliation. It is not started by the D02 worker.

## Selection

Classify every incomplete `ES-Dxx` as `ACTIONABLE_CONTINUATION`, `PARKED_BLOCKED`, or `READY`. Select the highest-priority actionable continuation; otherwise select the lowest-priority dependency-complete ready package. Skip blocked packages. A live overlapping website/competition/other worker may make only the overlapping package temporarily `PARKED_BLOCKED`; it does not authorize conflicting edits and does not prevent another independent Discord package from being selected when dependencies permit.

Each worker completes exactly one package and stops only at `COMPLETE` or a genuine externally blocked terminal state after publishing durable state. The worker must not stop merely because it produced a plan, opened a draft PR, reached a first checkpoint, or is waiting on ordinary CI that it can inspect in the same session.
