# EnthusiaStaff workspace state

Last updated: 2026-08-06

Live GitHub state overrides stale records, but persistent package state must be published to `main`.

## Current routing

| Field | Value |
| --- | --- |
| Completed packages | `ES-P01 — Exact-sanction appeal isolation`; `ES-P03 — Bedrock identity correctness` |
| Parked packages | `ES-P02 — Runtime database recovery and Velocity reload`; `ES-X05 — Website UX, authentication, and appeals` |
| ES-P02 status | `BLOCKED` / `PARKED_BLOCKED`; branch `package/es-p02-runtime-db-recovery`; PR #70; package-record head `80d4ea840f34017c09afb618f623581b31c6223d`; untouched |
| ES-X05 status | `BLOCKED` / `PARKED_BLOCKED`; implementation merged; finalization branch `package/es-x05-finalization`; PR #74; head `96bf9ab21b114a4523582a5ca267e6c1d1370cb1`; untouched |
| ES-P03 status | `COMPLETE` |
| ES-P03 product head | `15608bc3099dc34aa080c80ca8e824ffd51cdae4` |
| ES-P03 implementation merge / current implementation main | `b960e91ea59627a870ff24f89c2f761d0cbb68ab` via PR #75 |
| ES-P03 validation | Coverage run `31133176482`, job `92726659126`; Wiki run `31133176536`, job `92726609318`; CodeRabbit success; Codacy 0 issues |
| ES-P03 containment | product head is contained by merge commit; no changed files or unique product work beyond the merge |
| Branch cleanup | remote ref deletion unavailable through the connected tool; containment is verified and the implementation branch is inactive |
| Migration boundary | immutable V17; V1–V17 unchanged by ES-P03 |
| Canonical handoff | [`2026-08-06-es-p03-bedrock-identity.md`](reports/package-handoffs/2026-08-06-es-p03-bedrock-identity.md) |
| Active implementation package | `NONE` |
| Next owner action | repository owner `wsg138` may direct the next sequential worker after live classification; this worker does not select or activate another package |
| Production boundary | issue #43 remains open and deferred; LiteBans remains authoritative |

## ES-P03 completion evidence

- Paper now records verified platform observations through supported Floodgate evidence; local provider absence or incompatibility remains `UNKNOWN`, including proxy-hosted layouts.
- Unverified Velocity observations retain UUID/name/presence but cannot corrupt a verified platform.
- Configured single-`*` Bedrock current/history aliases and literal prefix search are supported without username-based platform inference.
- Verified Bedrock repairs legacy rows and cannot be downgraded.
- Unequal timestamps use event time; equal-time observations use a stable data-derived tie-breaker; stale/equal disconnects cannot clear a tied or newer connection.
- MariaDB/Testcontainers coverage includes aliases/history, non-downgrade, out-of-order and equal-time races, reconnect handling, literal underscores, and invalid shapes.
- Exact head `15608bc3099dc34aa080c80ca8e824ffd51cdae4` passed the Java 21 clean build, all tests, migrations, aggregate coverage generation, runtime-JAR integrity/provider-leak inspection, Wiki validation, Codacy, and CodeRabbit with all review threads resolved.
- PR #75 merged normally as `b960e91ea59627a870ff24f89c2f761d0cbb68ab`.

## Routing exception boundary

ES-P02 remains blocked and was not marked complete. The 2026-08-06 owner instruction was a narrow exception for ES-P03 only. It does not automatically make ES-P04, ES-P05, ES-P09, or any other later package ready, and it does not waive future dependency or validation requirements.

## Safety boundaries

No production credentials, Cloudflare secrets, punishment records, player records, raw addresses, private databases, deployment, Flyway repair/history rewrite, LiteBans removal, issue #43 acceptance, production migration, shadow window, cutover, or authority activation was authorized or performed. Representative Java/Bedrock staging remains owned by `ES-V02`.
