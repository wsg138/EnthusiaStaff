# Discord moderation program registry

Owner authorization: on 2026-08-23 the owner authorized continued implementation of the Discord moderation platform and explicitly authorized a sequential worker system to carry the project forward. This registry activates `ES-D02` through `ES-D15` as the dedicated Discord-program lane. It does **not** authorize production deployment, production data access, LiteBans cutover, Discord production configuration changes, or issue #43 acceptance; those remain separately gated by repository policy.

The product authority is `docs/discord-moderation-platform.md`. The sequencing authority is this file plus the individual `ES-Dxx` package files. Live GitHub always overrides stale status text. Detailed historical validation evidence belongs in package files, PRs, and canonical package handoffs rather than being duplicated indefinitely here.

Dedicated Discord-program workers must reconcile the global `PACKAGE-REGISTRY.md` and all live PRs/branches for conflicts, but they select implementation work only from this lane. Unrelated `ES-Xxx`, `ES-Pxx`, website, competition, or other work never preempts a Discord-program worker unless it creates an actual path/schema/protocol collision that makes the selected Discord package unsafe.

| ID | Package | Status | Priority | Depends on |
| --- | --- | --- | ---: | --- |
| `ES-D01` | Discord domain and identity contract | `COMPLETE` | 130 | owner activation |
| `ES-D02` | Discord persistence and migration schema | `COMPLETE` | 131 | `ES-D01` |
| `ES-D03` | Authorization and cross-platform policy | `COMPLETE` | 132 | `ES-D01`, `ES-D02` |
| `ES-D04` | Account linking and DiscordSRV migration | `COMPLETE` | 133 | `ES-D01`–`ES-D03` |
| `ES-D05` | Staff bot runtime foundation | `COMPLETE` | 134 | `ES-D01`–`ES-D03` |
| `ES-D06` | Read-only staff moderation UX | `COMPLETE` | 135 | `ES-D04`, `ES-D05` |
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

No Discord-program implementation package is active after D06 terminal publication. `ES-D07` and `ES-D13` are dependency-complete `READY`; this D06 worker does not begin either package.

## Terminal package references

- `ES-D04 — Account linking and DiscordSRV migration`: `COMPLETE`; implementation PR #151 merged normally as `4e7621b7a42e812cc7bf806a029f37a753cdd9f3`. Canonical handoff: `ai-agents/reports/package-handoffs/2026-08-26-es-d04-complete.md`.
- `ES-D05 — Staff bot runtime foundation`: `COMPLETE`; implementation PR #160 merged normally as `7bc8739bdc3f77db23c8b649f8c227f008162e47`. Canonical handoff: `ai-agents/reports/package-handoffs/2026-08-26-es-d05-complete.md`.
- `ES-D06 — Read-only staff moderation UX`: `COMPLETE`; implementation PR #177 merged normally as `5eab4d8ff7bf0c25253df828c837fbc8c96edfb3` from exact validated product head `b624ee799aea7db7c561b0b064733374d4c61067`. Canonical handoff: `ai-agents/reports/package-handoffs/2026-08-28-es-d06-complete.md`. Historical active and blocked records remain preserved.

## ES-D06 terminal record

D06 delivers read-only `/moderate`, Discord user/message context moderation, `/moderate-minecraft`, `/linked`, `/history`, private notes/cases views, ambiguity-safe Minecraft resolution, compact ephemeral panels, authoritative linked-staff actor resolution, signed expiring replay-resistant components, permission-aware discovery, and read-time reauthorization. Discord roles alone never grant domain moderation authority.

Final reviewed repairs include exact IPv4 loopback authority binding, Discord's 25-option ambiguity ceiling, privacy-safe unexpected-read logging, preservation of the `/v1/staff-rank` authority path when adding the player query, valid authority-port bounds, and rejection of signed-component TTLs below one second. Test-only credential-shaped analyzer data was generated rather than hard-coded. No broad static-analysis exclusion was introduced.

For exact product head `b624ee799aea7db7c561b0b064733374d4c61067`:

- Coverage/full Java 21 validation `33204412446` / job `98961747084`: PASS. Clean build/tests including MariaDB/Testcontainers, Paper/staff-bot/Velocity tests, runtime-JAR integrity/provider-leak checks, and Codacy coverage reporting passed. JaCoCo: 51.39% line / 41.50% branch / 53.72% instruction. Artifact `9699285991`, digest `sha256:ded2a61af49f789a6ac18754c0b236281d1ec31be8a7df4fbfb269509e8f9d96`.
- Staff Bot Configuration Cache `33204412468` / job `98961683087`: PASS.
- Sentinel Restart Artifact `33204412444` / job `98961683122`: PASS.
- Hosted Codacy Static Code Analysis check `98961965089`: PASS with zero annotations/new issues. Non-gating Codacy Diff Coverage check `98963786634`: success, 45.74%, with no gate defined.
- Supplemental exact-product diagnostic `33204549522` / job `98962146236`: repository-native PMD 6.55.0 zero findings; Semgrep, Lizard, Trivy, Checkov, and Spectral zero issues. The PMD 7 adapter's inability to load the repository's PMD 6 XPath ruleset remains diagnostic-only and was never relabeled as product success.
- Durable Sentinel job `327`: `PAPER_RESTART_OK`.
- Canonical Pi public run `33204694500`: PASS through exact-source build, private dispatch/verdict collection, transient-transfer cleanup, and terminal publication.
- Correlated private run `33205431529` / job `98965140421`: PASS on trusted `Lincoln-PI-4`, including exact artifact verification, guarded disposable Paper boot/restart, durable sanitized evidence, and cleanup. Sanitized runtime digest: `sha256:728ab454b9cb546625985a02fa5d6c9fc7a6e37020974a409862f411e58dc96b`.
- All visible PR #177 inline review threads are resolved/outdated. The final manual harsh review found no remaining valid actionable defect. A later CodeRabbit incremental rerun was rate-limited after the final repair; that availability limit is not an additional package-contract acceptance gate.

PR #177 merged with a normal two-parent merge commit `5eab4d8ff7bf0c25253df828c837fbc8c96edfb3`, parents unchanged pre-merge `main` `500136b37c9acc30b1de8a057feb79d3d16fc400` and exact product head `b624ee799aea7db7c561b0b064733374d4c61067`. Post-merge compare is one commit ahead, zero behind, with zero file differences, proving exact containment and no unique implementation work. The implementation branch is absent. D06 adds no Flyway migration.

The residual `diagnostic/es-d06-codacy-remaining-20260828` branch contains diagnostic-workflow history only relative to the product and is safe to delete. The connected GitHub mutation surface available to this worker does not expose ref deletion, so this branch-cleanup item is recorded rather than falsely claimed complete. It does not contain unique D06 product work and is not a package blocker.

No production Discord configuration/data access, punishment side effect, AutoMod enforcement, website/competition work, deployment, LiteBans authority change, issue #43 acceptance, or secret exposure occurred.

## Latest completion

`ES-D06 — Read-only staff moderation UX` is the latest completed Discord package. `ES-D07` and `ES-D13` are `READY`; neither is started by this worker.

## Selection

Classify every incomplete `ES-Dxx` as `ACTIONABLE_CONTINUATION`, `PARKED_BLOCKED`, or `READY`. Select the highest-priority actionable continuation; otherwise select the lowest-priority dependency-complete ready package. Skip blocked packages. Each worker completes exactly one package and stops only at `COMPLETE` or a genuine externally blocked terminal state after publishing durable state.
