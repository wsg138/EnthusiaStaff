# EnthusiaStaff workspace state

Last updated: 2026-08-06

Live GitHub state overrides stale records, but persistent package state must be published to `main`.

## Current routing

| Field | Value |
| --- | --- |
| Completed packages | `ES-P01 — Exact-sanction appeal isolation`; `ES-X05 — Website UX, authentication, and appeals` |
| Parked package | `ES-P02 — Runtime database recovery and Velocity reload` |
| ES-P02 classification | `PARKED_BLOCKED` while its runner/authorization condition is unchanged |
| Preserved ES-P02 work | branch `package/es-p02-runtime-db-recovery`, open PR #70, current package-record head `80d4ea840f34017c09afb618f623581b31c6223d` |
| ES-X05 completion basis | `OWNER-APPROVED INFRASTRUCTURE EXCEPTION — STAGING DEFERRED` to `ES-V02` |
| ES-X05 finalization | branch `package/es-x05-finalization`, PR #74; completion becomes persistent when PR #74 merges normally |
| Standalone repository/main | `wsg138/enthusia-site` at merge `b385f78c522f452cc48d78ed19fd2ee82573f64d` |
| Aggregate implementation merge/main | `345b7bbcec6facb45c7f96b0e6e181ac7a38e1da` via PR #73 |
| Exact hosted-validated product head | `4c818bb3aea953d3f877efc8a48a9175ba219d38` |
| Frozen aggregate product head | `96912301fc425ac6f5eff9349ee3b3d543d122eb` |
| Component parity | true at SHA-256 `9910dc90d22be68bf034f03def0cabd617bdf2e9953f87231f11af1166fc07e2`; no added, missing, or modified paths |
| Migration boundary | aggregate `main` includes immutable `V17__website_appeal_workflow.sql`; V1–V16 unchanged |
| Deferred validation owner | `ES-V02 — Distributed and Java/Bedrock staging`, including future PySentinel-supported combined runtime acceptance |
| Active implementation package | `NONE`; this worker does not activate the next package |

## ES-X05 integrated evidence

- Standalone PR #2 passed hosted validation, production and preview Cloudflare deployments, Codacy, and review, then merged normally.
- Aggregate PR #73 implements signed private appeal routes, durable MariaDB exact-punishment workflow, atomic rate limiting, scoped idempotency/replay protection, optimistic decisions, audit events, and exact-sanction approval delegation.
- Coverage run `31116854096`, job `92668751419`, passed Java 21 clean build, all unit and MariaDB/Testcontainers tests and migrations, JaCoCo, runtime-JAR/provider-leak checks, artifact upload, and Codacy coverage upload on exact head `4c818bb3aea953d3f877efc8a48a9175ba219d38`.
- CodeRabbit passed with zero unresolved valid review threads.
- PR #73 merged normally as `345b7bbcec6facb45c7f96b0e6e181ac7a38e1da`; implementation containment and external parity passed.

## Owner-approved deferred staging record

Evidence label: **OWNER-APPROVED INFRASTRUCTURE EXCEPTION — STAGING DEFERRED**.

- Owner/approval: repository owner `wsg138`, explicit instruction in the current ChatGPT project conversation on 2026-08-06 to skip the ES-X05 live test for now, continue development, and run a larger combined test later through the developing PySentinel system.
- Affected gate: aggregate dispatcher run `31116852061`, job `92668521113`, dispatched private `wsg138/EnthusiaStaff-Staging` run `31116860919` for exact product head `4c818bb3aea953d3f877efc8a48a9175ba219d38`.
- Unavailable private build job: `92668551209`, runner ID `0`, empty runner name, executed steps `[]`.
- Downstream Pi job: `92668600472`, skipped. No checkout, build, product test, artifact validation, server boot, restart, migration, or other product-validation step executed in the private run.
- Later wrapper/finalization attempts failed or were cancelled during GitHub setup before product execution. They corroborate infrastructure instability but are not labeled passes.
- Missing evidence is assigned to `ES-V02`, which must pin the combined implementation set and cover clean build/artifact provenance, Paper boot/restart, V17 and later migrations, config/reload changes, appeal/auth persistence, provider behavior, Java/Bedrock interaction, automated test-client behavior where safely implemented, and complete cleanup/isolation.
- The deferred gate is not a staging pass, production verification, or authority activation. LiteBans remains authoritative.
- This approval does not authorize production credentials, production player accounts, production data, or production routes. Any automated Minecraft-account credential handling requires separate safe implementation and authorization.

## Safety boundaries

No production credentials, Cloudflare secrets, punishment records, player records, or private database data are committed. Issue #43, production cutover, Flyway repair/history rewriting, and authority activation remain deferred and excluded. ES-P02 PR #70 and its branch were not modified. No follow-on package was selected or activated.
