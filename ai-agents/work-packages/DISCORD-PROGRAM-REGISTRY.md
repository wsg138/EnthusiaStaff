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

## Active package

`ES-D04` has an independent live continuation on Staff PR #151. ES-D05 did not edit, merge, rebase, close, replace, or take over D04.

D06 and D13 depend on both D04 and D05. D05 is now complete, but D04 remains active, so neither package is dependency-complete yet. No D06 work is activated or begun by this publication.

## ES-D05 completed record

ES-D05 completed through Staff PR #160.

- Final executable/review head: `936155cc356075aff10fd966de19e3d4bd8ca5f0`.
- Normal merge commit: `7bc8739bdc3f77db23c8b649f8c227f008162e47`.
- Pre-merge canonical `main`: `977216dc42a169a966c518545cc08cbc55617ebc`.
- Merge parents are exact pre-merge `main` and exact validated D05 head.
- Post-merge compare from `936155...` to `7bc873...` contains one merge commit and zero changed files, proving feature-head containment and no unmerged product delta.
- Temporary implementation branch `package/es-d05-staff-bot-runtime` is deleted.
- D05 adds no migration; canonical `main` remains at V19 until D04 merges its independent migration work.

Canonical completion handoff: `ai-agents/reports/package-handoffs/2026-08-26-es-d05-discord-acceptance.md`.
Historical blocked handoff: `ai-agents/reports/package-handoffs/2026-08-24-es-d05-staff-bot-runtime.md`.

The merged runtime is an isolated Java 21 staff-bot process with JDA 6.5.0, no privileged Gateway intents, exact staging/production application identity fencing, Enthusia-only guild locking, staging test-channel fencing, bounded work and replay primitives, loopback health/readiness, reconnect/backoff, privacy-safe lifecycle logging, deterministic shutdown, shaded executable verification, and non-destructive `--smoke-test`. Existing webhook delivery remains separate. No destructive moderation command, punishment enforcement, AutoMod, public bot, production deployment/configuration, LiteBans authority change, or issue #43 acceptance is included.

### Owner-authorized live Discord acceptance — PASS

Original trusted staging run `32926306691`, latest attempt 3 / job `98071453002`, successfully executed exact frozen D05 source `5f24ba1818c81e0a30a516fa70c8597586184b00`. Sanitized evidence proves staging application `1541279616881397772` PASS, Enthusia guild `1410303324745371709` PASS, required test channel `1541286004298752091` PASS for view/send, readiness PASS, smoke exit 0, and graceful close/shutdown PASS.

Later executable review/static repairs required fresh acceptance on the final executable head. Trusted staging control merge `c544d6e3e800ee83fa0a7bb60f894e847c23ca3c` pinned exact Staff source `936155cc356075aff10fd966de19e3d4bd8ca5f0`; run `33006830844` / job `98302641683` completed `SUCCESS` across trusted-runner assertion, exact-source fetch, Java 21 requirement, exact runtime build/integrity verification, the non-destructive live Discord smoke, sanitized PASS publication, process exit 0, and graceful shutdown. Staging PR #116 then restored the smoke workflow to manual-only without changing its exact source pin or safety fences.

Neither acceptance sent a moderation action or test message, changed Discord configuration, or accessed production moderation data. Discord credential values were not requested, inspected, exposed, logged, committed, or copied into tracking text.

### Exact final-head validation — PASS

For exact executable/review head `936155cc356075aff10fd966de19e3d4bd8ca5f0`:

- Coverage/full Java 21 run `33006430216` / job `98301256132`: `SUCCESS`; validation artifact `9620975030`, digest `sha256:0645ffb1fe1bc0163df8f8ace0903417d7b2fde910c6eca606af006ddac2c8d9`.
- Staff Bot Configuration Cache `33006430238` / job `98301258008`: `SUCCESS`, including cache reuse.
- Sentinel Restart Artifact `33006430207` / job `98301255587`: `SUCCESS`.
- Codacy Static Code Analysis: `SUCCESS`, zero annotations; diff coverage 62.74%; coverage variation +0.18%.
- CodeRabbit status: `SUCCESS`; all visible inline review threads resolved.
- Canonical Pi public run `33007222310`: terminal `SUCCESS`, including exact-PR/source binding, trusted build, private self-hosted staging verdict, transfer cleanup, and canonical terminal result publication.
- Exact-final-head live Discord smoke `33006830844` / job `98302641683`: `SUCCESS`.

## Latest completion

`ES-D05 — Staff bot runtime foundation` is the latest completed Discord package.

D04 remains independent active work. D06 and D13 remain dependency-blocked by D04; D07+ remain sequenced behind their stated dependencies.

## Selection

Classify every incomplete `ES-Dxx` as `ACTIONABLE_CONTINUATION`, `PARKED_BLOCKED`, or `READY`. Select the highest-priority actionable continuation; otherwise select the lowest-priority dependency-complete ready package. Skip blocked packages. Each worker completes exactly one package and stops only at `COMPLETE` or a genuine externally blocked terminal state after publishing durable state.