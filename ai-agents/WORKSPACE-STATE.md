# Workspace state

Last updated: 2026-08-24

Live GitHub overrides stale records. Detailed package evidence remains in the registries, selected package record, canonical handoff, and PR verification ledgers.

## Current routing

| Field | Value |
| --- | --- |
| Universal package active state | None after blocker publication. `ES-X04 — EnthusiaCommend reputation provider` is `BLOCKED` / `PARKED_BLOCKED` with both implementation PRs preserved. |
| X04 standalone | `wsg138/EnthusiaCommend` PR #12, branch `package/es-x04-commend-provider`, frozen head `30ac1afbb6b45e958c6972330c42a870d619d530`; open, non-draft, mergeable at last reconciliation. |
| X04 aggregate | Staff PR #152, branch `package/es-x04-commend-provider`, frozen head `9d44bbcac4d3cb9a489e9c9f755e80ae7ace28b1`; open, non-draft, mergeable at last reconciliation. |
| X04 product state | Transactional/versioned reputation moderation provider and Staff sanction projection are implemented. This continuation compacted committed operation snapshot persistence with legacy-read compatibility and fail-closed mismatch tests. Shared standalone↔aggregate product Git objects are synchronized pre-merge; aggregate-only `COMPONENT-METADATA.md` is the expected extra file. |
| X04 review state | Staff PR #152 has zero live inline threads. Commend PR #12's six correctness/data-integrity threads are all resolved. CodeRabbit status is successful on the frozen Staff head. Codacy remains non-passing with 100 reported new static issues (`8` high, `92` medium). |
| X04 standalone validation | PR workflow `32763949487` / job `97549027434` succeeded with Java 21, 110 tests, PMD, and artifact `9533731303`, but raw checkout proves it tested synthetic merge ref `cf6f64d...`, not exact standalone head `30ac1af...`; therefore it is not an exact-head PASS. Package-branch push CI is configured read-only, but an exact push run is not directly inspectable through the connected PR-run listing. |
| X04 Staff hosted validation | Exact-head Coverage/full validation `32763957896` / job `97549217101` PASS on `9d44bbc...`: Java 21 full build/tests including MariaDB/Testcontainers; 27 provider API source types / 0 leaks; Paper SHA-256 `7dd515e21409abb8c8496701e22ced3bdf3e266af8bc5c5bb0e7c52302c1198a`; Velocity SHA-256 `e4c7e48b51a8681eaac5742de96a841462aaeabd74507dcf1c8e1b02faef7586`; JaCoCo 50.50% line / 41.12% branch / 52.93% instruction; artifact `9534065111`, digest `sha256:132df7318d872c0f6e9863bd71fa3f8c69ee72478de742ff1d4f792ebf4fbd2f`; Codacy coverage upload/final notification PASS. |
| X04 Sentinel | Exact-head artifact run `32763957749` / job `97549055756` PASS; artifact `9533760777`, digest `sha256:285503604af4a7d2bd0bde450acf594909490767fdfc433a66e74ae9fe2d6d16`. Exact restart request comment `5400262894` was bound to `9d44bbc...` as durable job `231` and reached terminal `PAPER_RESTART_OK`; Paper reached readiness and stopped cleanly twice against one disposable state. Sentinel does not substitute for canonical Pi. |
| X04 canonical Pi blocker | Required canonical Pi is not verified. Existing automatic `pull_request_target` runs cannot be correlated through the connected commit-workflow listing, and PR #152 does not yet provide stable exact-head public/private run evidence. Independent control-plane fix PR #156 remains open/unmerged at `a1903feaf81cff9d8a151d197fc7efe2b1b855ae`; this worker did not modify or merge it. |
| X04 exact unblock | Obtain directly inspectable exact-head standalone CI; resolve/static-clean Codacy; make the trusted canonical Pi path discoverable/executable and verify exact public/private Pi evidence; reconcile live heads; then merge both implementation PRs normally and verify post-merge parity/metadata/cleanup. |
| Discord program active package | `ES-D05 — Staff bot runtime foundation` is `ACTIVE` on `package/es-d05-staff-bot-runtime`, claimed from exact `main` `168145d76efb13ed15f21f8a31ece3e96f7b7c7b`. Its canonical handoff is `ai-agents/reports/package-handoffs/2026-08-24-es-d05-staff-bot-runtime.md`. |
| Discord latest completion | `ES-D03 — Authorization and cross-platform policy` is `COMPLETE` through PR #149 on normal merge. Frozen validated merge-ready head: `5cd98a719e30eff64d1595f1e219ea70553c66c0`. |
| D04 live isolation | D04 PR #151 was actively receiving separate-worker commits during D05 selection; observed head `7eab5572b476419b125c6262c72b434f44ef1ef1`. D05 does not touch, synchronize, or replace that branch. Its older parked-state text below is historical until the D04 worker publishes newer canonical state. |
| D04 product state | Prior canonical record: durable two-direction five-minute account linking, V20 persistence/auditing, atomic recovery/main replacement, public PlayTime active-minute main selection, explicit DiscordSRV import and best-effort legacy mirroring, Paper `/link` and `/unlink`, concurrency/replay/restart coverage. Live PR #151 overrides stale frozen-head details. |
| D04 hosted validation | Prior canonical evidence: Coverage/full validation `32738304907` / job `97466391922` and Sentinel durable job `225` were successful for an older frozen SHA. Newer D04 commits require their own exact-head evaluation by the D04 worker. |
| D04 blocker | Prior canonical blocker was required canonical Pi staging. A separate D04 worker is actively reconciling the changed condition and newer product head; D05 makes no D04 status claim. |
| D05 runtime contract | PR #153 merged normally before D05 claim, recording production app `1541279426233376818`, staging app `1541279616881397772`, guild `1410303324745371709`, and staging test channel `1541286004298752091`; tokens remain secret. |
| D05 implementation state | Fresh review selected official JDA `6.5.0`; D05 will be a separate Java 21 process with exact app/guild fencing, bounded application work, local health/readiness, graceful shutdown, interaction replay protection, and no destructive commands or production deployment. |
| Migration state | Canonical `main` is at V19. D05 adds no migration and does not consume D04's unmerged V20. |
| D05 collision reconciliation | No existing D05, website, or competition branch was found at claim. Branch starts exactly from canonical `main` `168145d76efb13ed15f21f8a31ece3e96f7b7c7b`; D04 and universal X04/X03 work remain isolated. |
| Independently parked packages | `ES-X01` and `ES-X03` remain independently `BLOCKED` / `PARKED_BLOCKED`; X04 remains separately parked. D05 does not modify them. |
| Production boundary | No production Discord configuration, bot token, private production data, deployment, migration/import execution, LiteBans authority change, or cutover is authorized or performed. Issue #43 remains open and LiteBans remains authoritative. |
| Universal current handoff | `ai-agents/reports/package-handoffs/2026-08-24-es-x04-commend-provider-blocked.md` |
| Discord current handoff | `ai-agents/reports/package-handoffs/2026-08-24-es-d05-staff-bot-runtime.md` |

## X04 parked record

X04 was selected because its existing paired PRs made it the highest-priority universal `ACTIONABLE_CONTINUATION`. The implementation is frozen at Commend `30ac1afbb6b45e958c6972330c42a870d619d530` and Staff `9d44bbcac4d3cb9a489e9c9f755e80ae7ace28b1` while required evidence remains incomplete. The worker repaired one bounded persistence concern, mirrored the change exactly, added focused regression tests, preserved read-only CI permissions, completed exact-head Staff full validation, and obtained exact-head Sentinel restart success.

The package is not merged because validation policy requires all applicable exact reviewed-head evidence. The observable Commend build remains merge-ref-only; Staff Codacy reports unresolved new static issues; and canonical Pi cannot yet be correlated through the current trusted public control plane. Missing or merge-ref-only evidence is explicitly not called a pass.

Resume the existing X04 PRs only when those exact conditions can be resolved and proven. Do not replace the implementation branches or bypass the independent staging-control-plane review.

## D05 active record

D05 was selected through the dedicated Discord-program lane after D04 was found to be concurrently active on its existing branch. The owner-authored D05 identity contract in PR #153 was validated and merged normally first so implementation starts from one canonical contract. D05 owns only the new staff-bot runtime/build/tests/docs and directly necessary package-state files; it does not absorb D04, website, competition, or global provider work.

Starting Staff `main` is `168145d76efb13ed15f21f8a31ece3e96f7b7c7b`; implementation branch is `package/es-d05-staff-bot-runtime`. Main remains at Flyway V19 and D05 adds no migration. JDA 6.5.0 is the selected maintained Discord library; audio dependencies are intentionally excluded. Production Discord configuration, credentials, deployment, and moderation authority remain out of scope.

## D04 independent record

D04 is actively owned by another worker in PR #151. The previous parked handoff remains historical input only; live GitHub and the D04 worker's eventual publication supersede it. D05 must not edit or rebase D04 merely because staging-control work on `main` changed.

## Independent ES-X03 blocker

ES-X03 remains parked on its own recorded blocker. D05 does not alter that package, its provider mirror, or its frozen validation evidence.
