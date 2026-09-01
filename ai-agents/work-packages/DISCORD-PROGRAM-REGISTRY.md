# Discord moderation program registry

Owner authorization: on 2026-08-23 the owner authorized continued implementation of the Discord moderation platform and explicitly authorized a sequential worker system to carry the project forward. This registry activates `ES-D02` through `ES-D16` as the dedicated Discord-program lane. It does **not** authorize production deployment, production data access, LiteBans cutover, Discord production configuration changes, or issue #43 acceptance; those remain separately gated by repository policy.

The product authority is `docs/discord-moderation-platform.md`. The sequencing authority is this file plus the individual `ES-Dxx` package files. Live GitHub always overrides stale status text.

Dedicated Discord-program workers must reconcile the global `PACKAGE-REGISTRY.md` and all live PRs/branches for conflicts, but they select implementation work only from this lane. Unrelated `ES-Xxx`, `ES-Pxx`, website, competition, or other work never preempts a Discord-program worker unless it creates an actual path/schema/protocol collision that makes the selected Discord package unsafe.

| ID | Package | Status | Priority | Depends on |
| --- | --- | --- | ---: | --- |
| `ES-D01` | Discord domain and identity contract | `COMPLETE` | 130 | owner activation |
| `ES-D02` | Discord persistence and migration schema | `COMPLETE` | 131 | `ES-D01` |
| `ES-D03` | Authorization and cross-platform policy | `COMPLETE` | 132 | `ES-D01`, `ES-D02` |
| `ES-D04` | Account linking and DiscordSRV migration | `COMPLETE` | 133 | `ES-D01`–`ES-D03` |
| `ES-D05` | Staff bot runtime foundation | `COMPLETE` | 134 | `ES-D01`–`ES-D03` |
| `ES-D06` | Read-only staff moderation UX | `COMPLETE` | 135 | `ES-D04`, `ES-D05` |
| `ES-D16` | Moderation console real-data read bridge | `BLOCKED` | 135.5 | `ES-D03`, `ES-D05`, `ES-D06`, merged PR #186 |
| `ES-D07` | Discord punishment enforcement | `READY` | 136 | `ES-D03`, `ES-D05`, `ES-D06` |
| `ES-D08` | Cross-platform moderation integration | `PLANNED` | 137 | `ES-D07` |
| `ES-D09` | Discord evidence, cases, notes and linked-alt alerts | `PLANNED` | 138 | `ES-D06`, `ES-D07` |
| `ES-D10` | AutoMod shadow engine | `PLANNED` | 139 | `ES-D05`, `ES-D09` |
| `ES-D11` | AutoMod enforcement and security locks | `PLANNED` | 140 | accepted `ES-D10` shadow evidence |
| `ES-D12` | Staff website Discord expansion | `PLANNED` | 141 | `ES-D02`, `ES-D07`, `ES-D09` |
| `ES-D13` | Discord role-sync replacement | `READY` | 142 | `ES-D04`, `ES-D05` |
| `ES-D14` | Public bot and sanitized public API | `PLANNED` | 143 | sanitized public contracts and completed identity foundation |
| `ES-D15` | Discord migration/cutover acceptance | `PLANNED` | 144 | `ES-D01`–`ES-D14` as applicable |

## Active packages

ES-D16 is `BLOCKED` / `PARKED_BLOCKED` after full implementation and repository-controlled validation on frozen executable head `a009f4f5f857cf86a859be0d314264568d181670`. PR #187 remains open/draft and unmerged because the package's required live staging, which must be sanitized, cannot pass until the protected Cloudflare token has account-level named-tunnel authority. D07 and D13 remain dependency-complete `READY`; while D16 remains parked, a future Discord worker skips it and may select D07 by priority. If the Cloudflare permission condition changes, D16 becomes the higher-priority `ACTIONABLE_CONTINUATION` to resume before newly ready work.

## ES-D04 terminal record

Status: `COMPLETE`.

Implementation PR #151 merged normally as `4e7621b7a42e812cc7bf806a029f37a753cdd9f3`. Final reviewed/validated product head was `da0371681f5a44c72a614c8d6637b85d9080291d`. The temporary implementation branch `package/es-d04-account-linking` is absent after merge.

Canonical terminal handoff: `ai-agents/reports/package-handoffs/2026-08-26-es-d04-complete.md`.
Historical blocked handoff: `ai-agents/reports/package-handoffs/2026-08-24-es-d04-account-linking-blocked.md`.

D04 delivers authoritative account linking/unlinking, durable link-code replay/expiry semantics, historical ownership, audited staff recovery and main overrides, active-playtime main selection, DiscordSRV public-API import/mirroring, transactional mutation/audit/main replacement, and forward-only V20 persistence. Production import/deployment/configuration and LiteBans authority remain outside D04.

### Final exact-head gates — PASS

For exact final head `da0371681f5a44c72a614c8d6637b85d9080291d`:

- Coverage/full Java 21/MariaDB/Testcontainers `33029697612` / job `98379158884`: success.
- Codacy Static Code Analysis `98379478044`: success, zero annotations and six prior findings solved.
- Codacy Diff Coverage `98380515790`: success, 73.86% diff coverage with no repository gate defined.
- Sentinel Restart Artifact `33029697604` / job `98379112319`: success; artifact `9629765037`, digest `sha256:57ece0dbccc120c69f6a846d80aa5bf60aab04472f1439af5abe799c19074b99`.
- Durable Sentinel job `292`: terminal `PAPER_RESTART_OK`.
- Canonical public Pi `33029762105`: success through exact-head build, private dispatch, verdict collection, transfer cleanup, and terminal publication.
- Correlated private staging `33030278019` / job `98380970512`: success on trusted `Lincoln-PI-4`; exact bridge verification, guarded disposable Paper boot/restart, durable sanitized evidence, and cleanup passed.
- All visible PR #151 inline review threads were resolved before merge.

### Merge and containment

PR #151 merged normally, never squash/rebase/force/auto-merge. Merge commit `4e7621b7a42e812cc7bf806a029f37a753cdd9f3` has parents pre-merge `main` `36af6fc85052cbc38bb9840899b415fc53503af3` and exact feature head `da0371681f5a44c72a614c8d6637b85d9080291d`.

The merge and feature trees are identical at `63c2a0924d38ac9ce8e0a208f0eb79a671af37fc`. Post-merge compare is one commit ahead and zero behind, so the validated product is exactly contained. `V20__discord_account_linking.sql` is canonical on `main`, and the D04 branch is absent.

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

## ES-D06 terminal record

Status: `COMPLETE`.

Implementation PR #177 merged normally as `5eab4d8ff7bf0c25253df828c837fbc8c96edfb3`. Frozen reviewed/validated product head was `b624ee799aea7db7c561b0b064733374d4c61067`. The temporary implementation branch `package/es-d06-read-only-moderation-ux` is absent after merge.

Canonical terminal handoff: `ai-agents/reports/package-handoffs/2026-08-28-es-d06-complete.md`.
Historical active handoff: `ai-agents/reports/package-handoffs/2026-08-27-es-d06-active.md`.
Historical blocked handoff: `ai-agents/reports/package-handoffs/2026-08-27-es-d06-codacy-blocked.md`.

D06 delivers read-only `/moderate`, user/message context moderation, `/moderate-minecraft`, `/linked`, `/history`, notes/cases views, ambiguity-safe target resolution, compact ephemeral panels, authoritative linked-staff actor resolution, read-time reauthorization, permission-aware discovery, and signed expiring replay-resistant private component controls. Discord roles alone do not grant domain moderation authority. D06 adds no migration and no destructive moderation side effect.

### Final exact-head gates — PASS

For exact final head `b624ee799aea7db7c561b0b064733374d4c61067`:

- Coverage/full Java 21/MariaDB/Testcontainers run `33204412446` / job `98961747084`: success. JaCoCo 51.39% lines / 41.50% branches / 53.72% instructions.
- Validation artifact `9699285991`, digest `sha256:ded2a61af49f789a6ac18754c0b236281d1ec31be8a7df4fbfb269509e8f9d96`.
- Staff Bot Configuration Cache run `33204412468`: success.
- Sentinel Restart Artifact run `33204412444` / job `98961683122`: success.
- Durable Sentinel job `327`: terminal `PAPER_RESTART_OK`.
- Hosted Codacy Static Code Analysis check `98961965089`: success, zero annotations and no new valid findings.
- Codacy Diff Coverage check `98963786634`: success, 45.74% diff coverage with no repository gate defined.
- Repository-native PMD 6.55.0 repair validation `33144490940` / job `98762443017`: zero findings and focused Java 21 tests passed; no broad analyzer exclusion was added.
- All five visible PR #177 inline review threads were resolved/outdated before merge and every valid review finding was repaired.
- Canonical public Pi `33204694500`: success through exact-head build, private dispatch, verdict collection, transient-transfer removal, and terminal publication.
- Correlated private staging `33205431529` / job `98965140421`: success on trusted `Lincoln-PI-4`; exact bridge verification, guarded disposable Paper boot/restart, sanitized evidence publication, and durable-evidence requirement passed.
- Sanitized Pi evidence identity: `artifact=enthusiastaff-paper-b624ee799aea-33204694500-1;runtime_sha256=728ab454b9cb546625985a02fa5d6c9fc7a6e37020974a409862f411e58dc96b`.

### Merge and containment

Immediately before merge, `main` remained `500136b37c9acc30b1de8a057feb79d3d16fc400`, PR #177 remained mergeable, the feature head was unchanged, all required exact-head gates were terminal green, and no unresolved inline review thread remained.

PR #177 merged normally as `5eab4d8ff7bf0c25253df828c837fbc8c96edfb3` with parents pre-merge `main` `500136b37c9acc30b1de8a057feb79d3d16fc400` and exact feature head `b624ee799aea7db7c561b0b064733374d4c61067`. Merge and feature trees are identical at `5b3fd4d313dd4437dc04c346bd39efcc4e00f007`. Post-merge compare is one commit ahead, zero behind, with zero file differences.

The implementation branch is absent. The temporary diagnostic-only workflow was removed from `diagnostic/es-d06-codacy-remaining-20260828`; comparison of that retained branch content against merged `main` has zero file differences. The branch has no unique work and is safe to delete when an authorized branch-delete mutation is available.

No production deployment/configuration/data access, moderation mutation, secret access, LiteBans authority change, issue #43 acceptance, or cutover occurred.

## ES-D16 parked record

Status: `BLOCKED` / `PARKED_BLOCKED`.

Implementation PR #187 remains open/draft and unmerged on `package/es-d16-moderation-read-bridge`. Frozen reviewed executable head: `a009f4f5f857cf86a859be0d314264568d181670`. Canonical blocked handoff: `ai-agents/reports/package-handoffs/2026-09-01-es-d16-cloudflare-tunnel-blocked.md`.

The product head implements the owner-approved real-data read bridge while retaining simulation-only punishment/deletion behavior: target-bound hosted launch tickets, authenticated loopback moderation read API, D03/D06 authorization/read reuse, explicit response DTO allowlists, replay/expiry/rate/request bounds, bounded JDA channel/message reads, Cloudflare Worker session/proxy flow, and real-data preview rendering. It performs no punishment mutation, message deletion, permission override application, production Discord change, LiteBans authority change, issue #43 acceptance, or production cutover.

Exact-head repository-controlled gates are terminal green: Coverage/full Java 21 run `33529631291` / job `99929426510` (`BUILD SUCCESSFUL`, runtime inspection zero provider leaks, 51.70% line / 41.74% branch / 54.02% instruction coverage, validation artifact `9809522236` digest `sha256:00f970e5dc8a3d2823d4dae4d226be1cff8d71abcf601658f2405eb383aef1d6`); Moderation Web Validation `33529631190`; Staff Bot Configuration Cache `33529631174`; Staff Bot PR Artifact `33529631246`; Sentinel Restart Artifact `33529631254`; Codacy Static Code Analysis check `99929501999` with zero annotations; Codacy Diff Coverage `99932381673` at 33.57% with no configured gate; and exact-head CodeRabbit review `5080380849` bound to `a009f4f5f857cf86a859be0d314264568d181670` with zero new findings. All three earlier CodeRabbit correctness threads are resolved/confirmed addressed.

Required Cloudflare staging run `33530157844` / job `99930994457` checked out exact `a009f4f5f857cf86a859be0d314264568d181670`. Worker build/deploy, fixed staging origin checks, first-use protected launch, authenticated session, and replay rejection passed. The first account-level Cloudflare Tunnel API GET returned HTTP 403, so no tunnel configuration or DNS mutation succeeded and the workflow correctly failed its hard `Require fixed private tunnel provisioning` gate. This failure is not a pass and cannot use the zero-execution infrastructure exception because a runner and product steps executed and D16 explicitly requires sanitized staged acceptance.

Exact unblock: grant or replace the protected staging Cloudflare API token with the required Cloudflare Tunnel account read/edit authority for the owning account, rerun exact-head staging, then complete Bloom/live read acceptance (including the required staging Discord message-content state verification) before any normal merge/containment path resumes. Do not merge PR #187 while this blocker remains.

## Latest completion

`ES-D06 — Read-only staff moderation UX` is the latest completed Discord package. D07 and D13 are dependency-complete `READY`. This D06 worker stops without beginning either package.

## Selection

Classify every incomplete `ES-Dxx` as `ACTIONABLE_CONTINUATION`, `PARKED_BLOCKED`, or `READY`. Select the highest-priority actionable continuation; otherwise select the lowest-priority dependency-complete ready package. Skip blocked packages. Each worker completes exactly one package and stops only at `COMPLETE` or a genuine externally blocked terminal state after publishing durable state.
