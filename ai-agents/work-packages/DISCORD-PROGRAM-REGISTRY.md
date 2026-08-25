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
| `ES-D05` | Staff bot runtime foundation | `ACTIVE` | 134 | `ES-D01`–`ES-D03` |
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

`ES-D05 — Staff bot runtime foundation` is active on `package/es-d05-staff-bot-runtime`, starting from exact `main` `168145d76efb13ed15f21f8a31ece3e96f7b7c7b`. Canonical handoff: `ai-agents/reports/package-handoffs/2026-08-24-es-d05-staff-bot-runtime.md`.

The owner-authored D05 runtime identity contract was merged normally through PR #153 before package claim, so this implementation branch contains the production/staging application IDs and staging test-channel ID without competing with that documentation branch.

At selection, D04 PR #151 was receiving concurrent commits from a separate worker. This D05 worker therefore did not touch, synchronize, or replace D04. No D05, website, or competition branch collision was found.

## ES-D04 parked lineage / independent live work

D04 remains outside this worker's ownership. Its canonical parked record predates newer live commits to PR #151, and live GitHub therefore overrides the stale frozen SHA below. The D05 worker observed live D04 head `7eab5572b476419b125c6262c72b434f44ef1ef1` during selection and left it untouched.

The prior canonical blocker was required exact-head canonical Pi staging for the D04 runtime/persistence changes. A separate worker is actively reconciling that package and its staging-control dependencies. Do not infer D04 completion or mergeability from this D05 record.

Canonical prior handoff: `ai-agents/reports/package-handoffs/2026-08-24-es-d04-account-linking-blocked.md`.

## Latest completion

`ES-D03 — Authorization and cross-platform policy` remains the latest completed Discord package through PR #149. Frozen validated merge-ready head: `5cd98a719e30eff64d159f1e219ea70553c66c0`.

## D05 claim record

- Dependencies D01–D03 are `COMPLETE`.
- Starting Staff `main`: `168145d76efb13ed15f21f8a31ece3e96f7b7c7b`.
- Branch: `package/es-d05-staff-bot-runtime`.
- PR: pending first coherent checkpoint.
- Flyway boundary: V19; D05 requires no migration.
- Fresh implementation-time library review selected official JDA `6.5.0`; voice/audio dependencies are excluded because D05 has no audio scope.
- Production Discord configuration/deployment and bot tokens remain outside authorization.

`ES-D06` and `ES-D13` remain dependency-blocked until both D04 and D05 complete. Completing D05 must not start either package in this worker.

## Selection

Classify every incomplete `ES-Dxx` as `ACTIONABLE_CONTINUATION`, `PARKED_BLOCKED`, or `READY`. Select the highest-priority actionable continuation; otherwise select the lowest-priority dependency-complete ready package. Skip blocked packages. A live overlapping website/competition/other worker may make only the overlapping package temporarily `PARKED_BLOCKED`; it does not authorize conflicting edits and does not prevent another independent Discord package from being selected when dependencies permit.

Each worker completes exactly one package and stops only at `COMPLETE` or a genuine externally blocked terminal state after publishing durable state. The worker must not stop merely because it produced a plan, opened a draft PR, reached a first checkpoint, or is waiting on ordinary CI that it can inspect in the same session.
