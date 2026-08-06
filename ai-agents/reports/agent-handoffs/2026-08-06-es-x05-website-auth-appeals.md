# ES-X05 handoff — Website UX, authentication, and appeals

Date: 2026-08-06
Status: `COMPLETE` upon normal merge of PR #74
Owner priority: `35`
Canonical package: `ES-X05`

## Selection and scope

ES-P02 remained parked in PR #70 because its external runner/authorization condition did not change. ES-X05 was the highest-priority actionable continuation. Exactly one package was selected and completed; no second package was begun.

## Completed repositories

- Standalone `wsg138/enthusia-site`: reviewed head `1a45b32e372cf6939c078a0d7986655e7ed639d6`, validation run `31113188453` success, production/preview deployments and Codacy success, zero unresolved review threads, PR #2 normal merge/current `main` `b385f78c522f452cc48d78ed19fd2ee82573f64d`.
- Aggregate `wsg138/EnthusiaStaff`: frozen product head `96912301fc425ac6f5eff9349ee3b3d543d122eb`, exact hosted-validation/review head `4c818bb3aea953d3f877efc8a48a9175ba219d38`, PR #73 normal merge/current `main` `345b7bbcec6facb45c7f96b0e6e181ac7a38e1da`.
- V17 is the only new migration; V1–V16 remain unchanged.
- Finalization: branch `package/es-x05-finalization`, PR #74. Its exact head and current hosted-check result are recorded in the PR description/final report rather than a self-referential tracked file.

The site and aggregate implement verified identity, same-origin and bounded request controls, fixed-origin bearer-plus-HMAC private routes, persistent nonce replay controls, durable exact-punishment eligibility/submission/review, atomic rate limiting, scoped idempotency, optimistic revisions, reviewer authorization, audit events, and exact-sanction approval delegation.

## Successful validation and review

- Aggregate Coverage run `31116854096`, job `92668751419`, checked out exact head `4c818bb3aea953d3f877efc8a48a9175ba219d38` and passed Java 21 `clean build`, all unit and MariaDB/Testcontainers migration/integration tests, JaCoCo, runtime-JAR/provider-leak inspection, artifact upload, and Codacy coverage upload.
- JaCoCo: lines `47.50%`, branches `38.47%`, instructions `50.16%`.
- CodeRabbit succeeded and all eight aggregate review threads were resolved.
- Aggregate and standalone implementation containment passed.

## Component parity

Standalone and aggregate hash: `9910dc90d22be68bf034f03def0cabd617bdf2e9953f87231f11af1166fc07e2`. Added, missing, and modified paths: none. Evidence: [`2026-08-06-es-x05-component-parity.json`](../package-handoffs/2026-08-06-es-x05-component-parity.json).

## Owner-approved infrastructure exception

Evidence label: **OWNER-APPROVED INFRASTRUCTURE EXCEPTION — STAGING DEFERRED**.

- Owner/approval source/date: repository owner `wsg138`, explicit current ChatGPT project-conversation instruction on 2026-08-06 to skip the current ES-X05 live test, continue development, and run a larger combined test later using the developing PySentinel system.
- Named deferred package: `ES-V02 — Distributed and Java/Bedrock staging`.
- Aggregate parent dispatcher run `31116852061`, job `92668521113`, successfully authorized and dispatched exact product head `4c818bb3aea953d3f877efc8a48a9175ba219d38`.
- Private `wsg138/EnthusiaStaff-Staging` run `31116860919` build job `92668551209` had runner ID `0`, empty runner name, and executed-step list `[]`.
- Downstream Pi boot/restart job `92668600472` was skipped.
- No private checkout, build, test, artifact validation, Paper boot, restart, migration, or other product-validation step executed. The unavailable gate is not labeled `PASS`.
- Post-merge/finalization attempts `31117490156`, `31117820548`, `31117820542`, `31119701541`/job `92678016155`, and `31119699199`/job `92678140174` failed or were cancelled during action allocation/setup before product execution. These corroborate the external outage but are not passes.

`ES-V02` must pin the combined implementation heads and perform the deferred acceptance matrix: exact-head build/artifact verification, clean Paper boot/restart, V17 and later clean-install/upgrade/checksum migration paths, config changes and reload/restart, appeal/auth API and persistence, failure/recovery, provider and distributed behavior, representative Java/Bedrock clients, safely implemented automated client actions, and complete sandbox/database/process cleanup. Product defects found there must be routed to separate repair packages.

This approval does not authorize production credentials, production accounts, production player data/routes, LiteBans replacement, issue #43, or authority activation. Automated Minecraft-account credential handling requires separate safe implementation, security review, and owner authorization.

## Final boundaries and stop

LiteBans remains authoritative. Production cutover, Flyway repair/history rewriting, and ES-P02 changes remain excluded. After PR #74 merges normally, verify main/containment and stop. Do not activate or begin another package in this worker.
