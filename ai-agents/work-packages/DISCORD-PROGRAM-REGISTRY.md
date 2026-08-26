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
| `ES-D05` | Staff bot runtime foundation | `COMPLETE` | 134 | `ES-D01`–`ES-D03` |
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

## Active packages

`ES-D04` has an independent live continuation on Staff PR #151. D05 did not edit, merge, rebase, take over, or absorb D04.

No D05 implementation work remains. D06 and D13 remain dependency-blocked by D04 even though their D05 dependency is now satisfied.

## ES-D05 terminal record

Status: `COMPLETE`.

Implementation PR #160 merged normally as `7bc8739bdc3f77db23c8b649f8c227f008162e47`. Final synchronized/reviewed head was `936155cc356075aff10fd966de19e3d4bd8ca5f0`; frozen reviewed D05 product head remains `5f24ba1818c81e0a30a516fa70c8597586184b00`. The temporary implementation branch `package/es-d05-staff-bot-runtime` is absent after merge.

Canonical terminal handoff: `ai-agents/reports/package-handoffs/2026-08-26-es-d05-complete.md`.
Acceptance checkpoint handoff: `ai-agents/reports/package-handoffs/2026-08-26-es-d05-discord-acceptance.md`.
Historical blocked handoff: `ai-agents/reports/package-handoffs/2026-08-24-es-d05-staff-bot-runtime.md`.

The isolated Java 21 staff-bot runtime, packaging, health/readiness, bounded workload/replay primitives, exact application/guild/channel fences, lifecycle/shutdown behavior, tests, docs, and non-destructive `--smoke-test` are implemented. JDA 6.5.0 uses no privileged Gateway intents in D05. Tokens remain secret and are never committed, logged, requested in chat, exposed in artifacts, or placed on command lines.

### Required live Discord acceptance — PASS

Trusted staging workflow `wsg138/EnthusiaStaff-Staging` run `32926306691`, attempt 3 / job `98071453002`, completed successfully on trusted self-hosted runner `Lincoln-PI-4` (Linux/ARM64). Trusted staging-control head `03b3fce61bffe552d7905a4e4aa18e3015ea4e00` pins exact frozen D05 source `5f24ba1818c81e0a30a516fa70c8597586184b00`, requires Java 21, builds/verifies the runtime before secret scope, and executes the non-destructive `--smoke-test`.

Sanitized outcome: staging application `1541279616881397772` PASS; Enthusia guild `1410303324745371709` PASS; required test channel `1541286004298752091` PASS for view/send; readiness PASS; smoke exit 0; graceful close/shutdown PASS. The workflow sends no moderation action or test message and performs no Discord configuration change or production-data access. No bot-token value was inspected or exposed.

### Final exact-head gates — PASS

For exact final head `936155cc356075aff10fd966de19e3d4bd8ca5f0`:

- Coverage `33006430216`: success.
- Staff Bot Configuration Cache `33006430238`: success.
- Sentinel Restart Artifact `33006430207`: success.
- Canonical public Pi staging `33007222310`: success through exact-head build, private dispatch, verdict collection, cleanup, and terminal publication.
- Correlated private staging `33008160488` / job `98307232213`: success on trusted `Lincoln-PI-4`; exact artifact verification, guarded disposable Paper boot/restart, durable sanitized evidence, and cleanup passed.
- All visible PR #160 inline review threads were resolved/outdated before merge.

### Merge and containment

PR #160 merged normally, never squash/rebase/force/auto-merge. Merge commit `7bc8739bdc3f77db23c8b649f8c227f008162e47` has parents pre-merge `main` `977216dc42a169a966c518545cc08cbc55617ebc` and final feature head `936155cc356075aff10fd966de19e3d4bd8ca5f0`.

Post-merge compare is one commit ahead, zero behind, with zero file differences. The temporary D05 branch is gone, so no unique implementation work remains. D05 adds no migration and did not absorb D04/X03/provider/website/competition work.

## ES-D04 independent live continuation

D04 remains outside D05 ownership. Its implementation PR #151 is active. Live GitHub controls its current head/check/review state.

## Latest completion

`ES-D05 — Staff bot runtime foundation` is the latest completed Discord package.

`ES-D06` and `ES-D13` remain dependency-blocked until D04 completes. D07+ remain sequenced behind their stated dependencies. This D05 worker stops without beginning D06.

## Selection

Classify every incomplete `ES-Dxx` as `ACTIONABLE_CONTINUATION`, `PARKED_BLOCKED`, or `READY`. Select the highest-priority actionable continuation; otherwise select the lowest-priority dependency-complete ready package. Skip blocked packages. Each worker completes exactly one package and stops only at `COMPLETE` or a genuine externally blocked terminal state after publishing durable state.