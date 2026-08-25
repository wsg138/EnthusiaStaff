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

## Active package

None. `ES-D04` and `ES-D05` are independently `BLOCKED` / `PARKED_BLOCKED`; a future Discord worker must reconcile live GitHub and resume an actionable continuation only when its recorded blocker can actually be resolved.

## ES-D05 parked blocker

`ES-D05 — Staff bot runtime foundation` is preserved on `package/es-d05-staff-bot-runtime`, implementation PR #160. Frozen validated product head: `5f24ba1818c81e0a30a516fa70c8597586184b00`. Canonical handoff: `ai-agents/reports/package-handoffs/2026-08-24-es-d05-staff-bot-runtime.md`.

The isolated Java 21 staff-bot runtime, packaging, health/readiness, bounded workload/replay primitives, exact application/guild/channel fences, lifecycle/shutdown behavior, tests, docs, and non-destructive `--smoke-test` are implemented. The final product head passed exact-head full Java 21 validation, staff-bot configuration-cache validation, Codacy with zero new issues, CodeRabbit after valid findings were corrected, Sentinel artifact validation, and the canonical Paper/Pi staging path. The generic CodeRabbit docstring-coverage warning is not a functional/security/lifecycle package gate.

D05 is not complete because its own acceptance contract requires a **real staging Discord connection smoke** using the staging bot token, proving exact staging application `1541279616881397772`, exact Enthusia guild `1410303324745371709`, required test channel `1541286004298752091` with view/send permission, reconnect/readiness behavior, and clean disconnect/shutdown. No authorized secret-bearing execution path accessible to the connected worker contains `ENTHUSIA_STAFF_BOT_TOKEN`; the current public/private Pi control path is Paper-only. The token must not be requested in chat, committed, logged, or placed on a command line, and the validation gate must not be weakened.

Exact unblock: securely provision the staging token to an authorized trusted runtime/secret manager, run the already-implemented `--smoke-test` against exact frozen product source/artifact `5f24ba1818c81e0a30a516fa70c8597586184b00`, publish only sanitized provenance/readiness/shutdown evidence, then resume PR #160, reconcile live state, merge normally if the reviewed executable head is still exact and all applicable gates remain satisfied, verify containment/cleanup, and publish `COMPLETE`. Do not start D06 in the same worker.

## ES-D04 parked lineage / independent live work

D04 remains outside D05 ownership. Its implementation PR #151 remains open, and its independent private staging-control dependency PR `wsg138/EnthusiaStaff-Staging#109` remains open at D05 terminal reconciliation. D05 did not modify, synchronize, merge, or replace D04 or its staging-control work.

Canonical prior handoff: `ai-agents/reports/package-handoffs/2026-08-24-es-d04-account-linking-blocked.md`.

## Latest completion

`ES-D03 — Authorization and cross-platform policy` remains the latest completed Discord package through PR #149. Frozen validated merge-ready head: `5cd98a719e30eff64d159f1e219ea70553c66c0`.

## D05 frozen evidence summary

- Frozen product head: `5f24ba1818c81e0a30a516fa70c8597586184b00`.
- Coverage/full validation run `32874248685`, job `97888464396`: success; aggregate JaCoCo 50.76% lines / 41.41% branches / 53.21% instructions; validation artifact `9573547679`, digest `sha256:c6f2df467085d811593c7100feb5a4c698a46e14432e92d401662dff9d43455c`.
- Staff Bot Configuration Cache run `32874248800`, job `97888275507`: success with two configuration-cache executions and problems treated as failures.
- Sentinel Restart Artifact run `32874248693`: success for the frozen head.
- Codacy: zero new issues, 63.04% diff coverage, +0.17% coverage variation.
- CodeRabbit status: success after valid findings were fixed; no unresolved functional/security/lifecycle finding remains.
- Canonical Pi public run `32879118794` and correlated private run `32880103099`, job `97907230239`: success on trusted `Lincoln-PI-4`; this is Paper/Pi evidence only and is not relabeled as the missing Discord smoke.

`ES-D06` and `ES-D13` remain dependency-blocked until both D04 and D05 complete. D07+ remain sequenced behind their stated dependencies.

## Selection

Classify every incomplete `ES-Dxx` as `ACTIONABLE_CONTINUATION`, `PARKED_BLOCKED`, or `READY`. Select the highest-priority actionable continuation; otherwise select the lowest-priority dependency-complete ready package. Skip blocked packages. A live overlapping website/competition/other worker may make only the overlapping package temporarily `PARKED_BLOCKED`; it does not authorize conflicting edits and does not prevent another independent Discord package from being selected when dependencies permit.

Each worker completes exactly one package and stops only at `COMPLETE` or a genuine externally blocked terminal state after publishing durable state. The worker must not stop merely because it produced a plan, opened a draft PR, reached a first checkpoint, or is waiting on ordinary CI that it can inspect in the same session.
