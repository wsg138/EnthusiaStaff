# ES-D06 — Read-only staff moderation UX

Status: `COMPLETE`
Priority: 135
Depends on: `ES-D04`, `ES-D05`
Internal package: yes
Claimed: 2026-08-27 from canonical `main` `500136b37c9acc30b1de8a057feb79d3d16fc400`
Owner reassignment: 2026-08-28 to the completing worker to resume the existing D06 continuation.
Implementation PR: #177
Validated product head: `b624ee799aea7db7c561b0b064733374d4c61067`
Product merge: `5eab4d8ff7bf0c25253df828c837fbc8c96edfb3`
Canonical terminal handoff: `ai-agents/reports/package-handoffs/2026-08-28-es-d06-complete.md`
Historical active handoff: `ai-agents/reports/package-handoffs/2026-08-27-es-d06-active.md`
Historical blocked handoff: `ai-agents/reports/package-handoffs/2026-08-27-es-d06-codacy-blocked.md`

## Objective

Provide fast, secure read-only Discord moderation surfaces before destructive actions exist.

## Scope delivered

`/moderate <user>`, user/message context moderation, `/moderate-minecraft`, `/linked`, `/history`, notes/cases read views, target resolution by Discord/Minecraft IDs/names with bounded ambiguity selectors, compact ephemeral profile panels, organized history filters, authoritative linked-staff actor resolution, signed/expiring/replay-resistant component IDs, permission-aware discovery, and read-time reauthorization.

## Exclusions preserved

No warn/mute/kick/ban/restrict side effects, AutoMod enforcement, website/competition work, production deployment/configuration, production/private data access, LiteBans authority change, cutover, issue #43 acceptance, or secret exposure occurred.

## Completion checklist

- [x] Read-only slash/context command and ephemeral panel surface implemented.
- [x] Authoritative linked-staff actor resolution and read-time authorization implemented; Discord roles do not grant domain authority.
- [x] Signed, expiring, replay-resistant private component protocol implemented, including rejection of sub-second TTLs that cannot survive the second-granularity wire format.
- [x] Discord-only, Java/Bedrock-linked, missing-link, ambiguity, privacy, stale/replay, and authorization paths covered by tests.
- [x] Authority bridge restricted to the exact IPv4 loopback host used by Paper; alternate loopback hosts are rejected.
- [x] Authority client preserves `/v1/staff-rank` while adding the player query; a live loopback regression test proves path, query, bearer header, and decoded rank.
- [x] Authority endpoint configuration rejects ports outside 1–65535.
- [x] Ambiguous player resolutions are bounded to Discord's 25-choice limit and mark overflow truncated; regression coverage exercises 30 matches.
- [x] Unexpected read failures are logged with privacy-safe type/code detail before returning a generic unavailable response.
- [x] Historical PMD/Semgrep/Codacy findings were reproduced and repaired without broad exclusions; superseded failures remain truthful history.
- [x] Exact-head Coverage/full Java 21/MariaDB/Testcontainers/runtime-JAR validation passed.
- [x] Exact-head Staff Bot Configuration Cache passed.
- [x] Exact-head hosted Codacy Static Code Analysis passed with zero annotations/new issues.
- [x] Repository-native PMD 6.55.0 and supplemental Semgrep/Lizard/Trivy/Checkov/Spectral analysis found zero product issues.
- [x] Durable Sentinel restart passed with `PAPER_RESTART_OK`.
- [x] Canonical public/private Pi staging passed, including exact-artifact verification, guarded disposable Paper boot/restart, durable sanitized evidence, and transfer/private cleanup.
- [x] All visible inline review threads are resolved/outdated; final manual harsh review found no remaining valid actionable defect.
- [x] Pre-merge `main` remained `500136b37c9acc30b1de8a057feb79d3d16fc400`; PR #177 merged normally from exact validated head.
- [x] Merge containment is proven: one commit ahead, zero behind, zero file differences; implementation branch absent and no unique product work remains.
- [x] Newly dependency-complete `ES-D07` is routed `READY` without being started.

## Final exact-head evidence

Exact product head: `b624ee799aea7db7c561b0b064733374d4c61067`.

- Coverage run `33204412446`, job `98961747084`: PASS. Java 21 clean build/tests including MariaDB/Testcontainers and all applicable modules; runtime-JAR validation and provider-leak checks passed. JaCoCo 51.39% line / 41.50% branch / 53.72% instruction. Artifact `9699285991`, digest `sha256:ded2a61af49f789a6ac18754c0b236281d1ec31be8a7df4fbfb269509e8f9d96`.
- Staff Bot Configuration Cache run `33204412468`, job `98961683087`: PASS.
- Sentinel Restart Artifact run `33204412444`, job `98961683122`: PASS.
- Codacy Static check `98961965089`: PASS, zero annotations/new issues.
- Codacy Diff Coverage check `98963786634`: success, 45.74%; no gate defined.
- Supplemental analyzer run `33204549522`, job `98962146236`: native PMD 6.55.0 zero findings; Semgrep/Lizard/Trivy/Checkov/Spectral zero issues. PMD 7 adapter incompatibility with the repository's PMD 6 XPath ruleset remains diagnostic-only.
- Sentinel job `327`: `PAPER_RESTART_OK`.
- Canonical Pi public run `33204694500`: terminal success with transient transfer cleanup success.
- Private Pi run `33205431529`, job `98965140421`: success on trusted `Lincoln-PI-4`; sanitized evidence identity runtime digest `sha256:728ab454b9cb546625985a02fa5d6c9fc7a6e37020974a409862f411e58dc96b`.

## Merge and containment

PR #177 merged normally as `5eab4d8ff7bf0c25253df828c837fbc8c96edfb3`. The merge parents are pre-merge `main` `500136b37c9acc30b1de8a057feb79d3d16fc400` and exact validated feature head `b624ee799aea7db7c561b0b064733374d4c61067`. Post-merge comparison from product head to merge is one commit ahead, zero behind, and has no file differences. The implementation branch was removed after merge.

The diagnostic branch `diagnostic/es-d06-codacy-remaining-20260828` is product-contained and safe to delete, but branch ref deletion is not exposed by the connected GitHub mutation surface available to this worker. It is not unique product work and does not block D06 completion.

## Terminal state

No implementation, validation, review, staging, merge, containment, or product cleanup work remains. `ES-D06` is `COMPLETE`. Stop without starting D07, D13, or any second package.
