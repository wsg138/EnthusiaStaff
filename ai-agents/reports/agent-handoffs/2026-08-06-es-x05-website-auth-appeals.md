# ES-X05 handoff — Website UX, authentication, and appeals

Date: 2026-08-06
Status: `BLOCKED`
Classification: `PARKED_BLOCKED`
Owner priority: `35`
Canonical package: `ES-X05`

## Selection and scope

ES-P02 remained parked in PR #70 because its external runner/authorization condition did not change. ES-X05 was the highest-priority actionable continuation. Exactly one package was selected; no second package was begun.

## Completed repositories

- Standalone `wsg138/enthusia-site`: reviewed head `1a45b32e372cf6939c078a0d7986655e7ed639d6`, validation run `31113188453` success, production/preview deployments and Codacy success, zero unresolved review threads, PR #2 normal merge/current `main` `b385f78c522f452cc48d78ed19fd2ee82573f64d`.
- Aggregate `wsg138/EnthusiaStaff`: frozen product head `96912301fc425ac6f5eff9349ee3b3d543d122eb`, exact hosted-validation/review product head `4c818bb3aea953d3f877efc8a48a9175ba219d38`, PR #73 normal merge/current `main` `345b7bbcec6facb45c7f96b0e6e181ac7a38e1da`.
- V17 is the only new migration; V1–V16 remain unchanged.
- Finalization: branch `package/es-x05-finalization`, open PR #74. Its current head and automatically generated check state are recorded in the PR description/final report rather than a self-referential tracked file.

The site and aggregate implement verified identity, same-origin and bounded request controls, fixed-origin bearer-plus-HMAC private routes, persistent nonce replay controls, durable exact-punishment eligibility/submission/review, atomic rate limiting, scoped idempotency, optimistic revisions, reviewer authorization, audit events, and exact-sanction approval delegation.

## Successful validation and review

- Aggregate Coverage run `31116854096`, job `92668751419`, checked out exact product head `4c818bb3aea953d3f877efc8a48a9175ba219d38` and passed Java 21 `clean build`, all unit and MariaDB/Testcontainers migration/integration tests, JaCoCo, runtime-JAR/provider-leak inspection, artifact upload, and Codacy coverage upload.
- JaCoCo: lines `47.50%`, branches `38.47%`, instructions `50.16%`.
- CodeRabbit succeeded and all valid aggregate review threads were resolved.
- Aggregate and standalone implementation containment passed.

## Component parity

Standalone and aggregate hash: `9910dc90d22be68bf034f03def0cabd617bdf2e9953f87231f11af1166fc07e2`. Added, missing, and modified paths: none. Evidence: [`2026-08-06-es-x05-component-parity.json`](../package-handoffs/2026-08-06-es-x05-component-parity.json).

## Owner-approved staging deferral

Evidence label: **OWNER-APPROVED INFRASTRUCTURE EXCEPTION — STAGING DEFERRED**.

- Owner/approval source/date: repository owner `wsg138`, explicit current ChatGPT project-conversation instruction on 2026-08-06 to skip the current ES-X05 live staging test, continue development, and run a larger combined test later using the developing PySentinel system.
- Named deferred package: `ES-V02 — Distributed and Java/Bedrock staging`.
- Original aggregate parent dispatcher run `31116852061`, job `92668521113`, successfully authorized and dispatched exact product head `4c818bb3aea953d3f877efc8a48a9175ba219d38`.
- Original private `wsg138/EnthusiaStaff-Staging` run `31116860919` build job `92668551209` had runner ID `0`, empty runner name, and executed-step list `[]`; downstream Pi boot/restart job `92668600472` was skipped.
- No private checkout, build, test, artifact validation, Paper boot, restart, migration, or other product-validation step executed. The unavailable gate is not labeled `PASS`.

`ES-V02` must pin the combined implementation heads and perform the deferred acceptance matrix: exact-head build/artifact verification, clean Paper boot/restart, V17 and later clean-install/upgrade/checksum migration paths, config changes and reload/restart, appeal/auth API and persistence, failure/recovery, provider and distributed behavior, representative Java/Bedrock clients, safely implemented automated client actions, and complete sandbox/database/process cleanup. Product defects found there must be routed to separate repair packages.

This approval does not authorize production credentials, production accounts, production player data/routes, LiteBans replacement, issue #43, or authority activation. Automated Minecraft-account credential handling requires separate safe implementation, security review, and owner authorization.

## Recovery attempt and current blocker

After GitHub Status reported Actions operational and the owner directed continuation, a tree-identical retrigger created exact finalization attempt head `e4be594d8dd811bd27b13c3a2207fcdb06a0a769`.

1. Coverage run `31122594623`, job `92686159333`, waited from `2026-08-06T17:15:27Z` through `17:30:28Z`, then was cancelled with runner ID `0`, empty runner name, and steps `[]`.
2. Exact-head Pi wrapper run `31122594379` allocated a runner, passed dispatch/auth steps, and dispatched private run `31122730837`.
3. Private build job `92686599218` waited from `17:18:15Z` through `17:33:16Z`, then was cancelled with runner ID `0`, empty runner name, and steps `[]`.
4. Downstream Pi boot/restart job `92688928718` was skipped, and the wrapper published failure.
5. No checkout, ordinary hosted build, package validation, product test, artifact validation, Paper boot, restart, or migration step executed in either ordinary hosted job.

These are infrastructure-unavailable results, not product failures or passes. The owner's staging deferral remains valid for the private/Pi evidence, but it cannot excuse the missing ordinary hosted Coverage gate. The assertion that testing was back did not produce standard Ubuntu hosted-runner allocation.

## Exact resume instructions

Do not run another identical retry until there is new evidence of ordinary Ubuntu hosted-runner recovery or another material runner, billing, authorization, or configuration change. Then:

1. Reconcile current aggregate and standalone heads, PR #74, branch heads, reviews, scope, and parity.
2. Synchronize only if real drift requires it and freeze one exact finalization head.
3. Obtain successful ordinary hosted Coverage for that exact head; retain the ES-V02 staging deferral and never call it a staging pass.
4. Reconfirm zero valid unresolved findings, unchanged eight-file documentation-only scope, mergeability, and deterministic parity.
5. Merge PR #74 normally, verify `main` containment/no unique work, publish `COMPLETE`, clean safe temporary branches, and stop without selecting another package.

## Final boundaries and stop

LiteBans remains authoritative. Production cutover, Flyway repair/history rewriting, and ES-P02 changes remain excluded. ES-X05 is correctly parked while the ordinary hosted runner condition remains unchanged. No follow-on package was selected or activated.
