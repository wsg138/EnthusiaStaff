# ES-D06 — Read-only staff moderation UX

Status: `COMPLETE`. Priority: 135. Depends on `ES-D04`, `ES-D05` (both `COMPLETE`). Internal package.

## Terminal state

Selected on 2026-08-27 and owner-reassigned on 2026-08-28 to finish the existing continuation. Completed on 2026-08-28 through implementation, review repair, exact-head hosted/static/runtime validation, canonical Pi staging, normal merge, containment, cleanup, and terminal publication.

- Starting `main`: `500136b37c9acc30b1de8a057feb79d3d16fc400`.
- Implementation PR: #177.
- Frozen reviewed/validated product head: `b624ee799aea7db7c561b0b064733374d4c61067`.
- Normal product merge commit: `5eab4d8ff7bf0c25253df828c837fbc8c96edfb3`.
- Product merge parents: pre-merge `main` `500136b37c9acc30b1de8a057feb79d3d16fc400` and exact feature head `b624ee799aea7db7c561b0b064733374d4c61067`.
- Product and merge tree: `5b3fd4d313dd4437dc04c346bd39efcc4e00f007`.
- Post-merge containment: one commit ahead of the product head, zero behind, zero file differences.
- Temporary implementation branch `package/es-d06-read-only-moderation-ux`: absent after merge.
- Temporary diagnostic branch `diagnostic/es-d06-codacy-remaining-20260828`: contains no file difference from merged `main` after removal of its diagnostic-only workflow; no unique product or diagnostic file remains. The connected mutation surface does not expose branch deletion, so the fully contained branch is safe to delete when that operation is available.
- D06 adds no Flyway migration.
- Production Discord changes, deployment/private-production-data access, LiteBans authority changes, issue #43 acceptance, and cutover remain unauthorized and were not performed.

Canonical terminal handoff: `ai-agents/reports/package-handoffs/2026-08-28-es-d06-complete.md`.
Historical active handoff: `ai-agents/reports/package-handoffs/2026-08-27-es-d06-active.md`.
Historical blocked handoff: `ai-agents/reports/package-handoffs/2026-08-27-es-d06-codacy-blocked.md`.

## Delivered scope

D06 provides read-only `/moderate`, user/message context moderation, `/moderate-minecraft`, `/linked`, `/history`, notes/cases views, ambiguity-safe Discord/Minecraft target resolution, compact ephemeral panels, authoritative linked-staff actor resolution, read-time reauthorization, permission-aware discovery, and signed expiring replay-resistant private component controls. Discord roles alone do not grant domain moderation authority.

Security/correctness hardening includes exact `127.0.0.1` authority-bridge binding, a Discord-safe 25-choice ambiguity ceiling with explicit truncation behavior, second-granularity component TTL validation, bounded read/render behavior, explicit allowlisted response construction, and regression coverage for authorization, rejection, replay, ambiguity, lifecycle, and failure paths.

## Final exact-head evidence — PASS

For exact frozen product head `b624ee799aea7db7c561b0b064733374d4c61067`:

- Coverage/full Java 21 validation `33204412446` / job `98961747084`: `SUCCESS`. Clean build/tests including MariaDB/Testcontainers completed; aggregate JaCoCo was 51.39% lines, 41.50% branches, and 53.72% instructions.
- Validation artifact `9699285991`: `sha256:ded2a61af49f789a6ac18754c0b236281d1ec31be8a7df4fbfb269509e8f9d96`.
- Runtime packaging inspection: 27 provider API source types checked, zero runtime leaks. Paper SHA-256 `04d23cf54a0d1cd3f524b86b0f380e0ad580a7641d369d46fa67dd156f88ff2f`; Velocity SHA-256 `ef6dcaca430e2e896be5243604de36e037712bc73eec7dd7d84bf25eb78940f1`.
- Staff Bot Configuration Cache run `33204412468`: `SUCCESS`.
- Sentinel Restart Artifact run `33204412444` / job `98961683122`: `SUCCESS`.
- Durable Sentinel restart job `327`: `PAPER_RESTART_OK` after readiness and clean shutdown twice against one disposable state.
- Hosted Codacy Static Code Analysis check `98961965089`: `SUCCESS`, zero annotations and no new valid findings.
- Codacy Diff Coverage check `98963786634`: `SUCCESS`, 45.74% diff coverage; no repository gate is defined for this metric.
- Repository-native PMD 6.55.0 repair validation `33144490940` / job `98762443017`: zero findings plus focused Java 21 tests. Broad static diagnostics were reconciled without broad exclusions; historical false-positive evidence remains documented rather than relabeled.
- All five visible PR #177 inline review threads were resolved/outdated before merge; every valid CodeRabbit finding was repaired.
- Canonical public Pi staging run `33204694500`: `SUCCESS` through exact-head build, private dispatch, verdict collection, transient-transfer removal, and terminal result publication.
- Correlated private staging run `33205431529` / job `98965140421`: `SUCCESS` on trusted `Lincoln-PI-4`, including exact bridge verification, guarded disposable Paper boot/restart, sanitized evidence publication, and durable-evidence enforcement.
- Sanitized Pi evidence identity: `artifact=enthusiastaff-paper-b624ee799aea-33204694500-1;runtime_sha256=728ab454b9cb546625985a02fa5d6c9fc7a6e37020974a409862f411e58dc96b`.

## Merge and containment

Immediately before merge, `main` was re-read at `500136b37c9acc30b1de8a057feb79d3d16fc400`, PR #177 remained mergeable, the PR head remained exact `b624ee799aea7db7c561b0b064733374d4c61067`, every required exact-head gate was terminal green, and no unresolved inline review thread remained.

PR #177 merged normally as `5eab4d8ff7bf0c25253df828c837fbc8c96edfb3`; no squash, rebase, force-push, or auto-merge was used. The merge commit has the exact validated feature head as its second parent. Compare from the frozen product head to the merge is one commit ahead, zero behind, with zero file differences, so the validated product is exactly contained.

The implementation branch is absent. The temporary diagnostic workflow was removed from the retained diagnostic branch; comparison against merged `main` has zero file differences. No unique D06 work remains.

## Completion checklist

- [x] Implement all read-only Discord staff moderation surfaces in scope.
- [x] Preserve authoritative EnthusiaStaff identity/authorization and prove Discord roles alone cannot grant moderation authority.
- [x] Implement signed, expiring, replay-resistant private interactions and bounded ambiguity handling.
- [x] Add authorization, rejection, replay, ambiguity, privacy, lifecycle, integration, and failure-path tests.
- [x] Harshly review the complete diff and repair all valid CodeRabbit/Codacy/PMD/CI findings without broad exclusions.
- [x] Pass exact-head Coverage, configuration-cache, Sentinel artifact/verdict, hosted Codacy, review, and canonical public/private Pi staging on the same frozen head.
- [x] Reconcile unchanged current `main` and mergeability immediately before merge.
- [x] Merge PR #177 normally and prove exact product containment.
- [x] Remove unique temporary implementation/diagnostic content and publish terminal `COMPLETE` state.

## Routing

`ES-D07 — Discord punishment enforcement` is now dependency-complete `READY`. `ES-D13 — Discord role-sync replacement` remains `READY`. A future Discord worker selects D07 first by priority unless live GitHub exposes a higher-priority actionable continuation. This D06 worker stops without starting either package.
