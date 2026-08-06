# EnthusiaStaff workspace state

Last updated: 2026-08-06

Live GitHub state overrides stale records, but persistent package state must be published to `main`.

## Current routing

| Field | Value |
| --- | --- |
| Completed package | `ES-P01 — Exact-sanction appeal isolation` |
| Parked packages | `ES-P02 — Runtime database recovery and Velocity reload`; `ES-X05 — Website UX, authentication, and appeals` |
| ES-P02 classification | `PARKED_BLOCKED` while its runner/authorization condition is unchanged |
| Preserved ES-P02 work | branch `package/es-p02-runtime-db-recovery`, open PR #70, current package-record head `80d4ea840f34017c09afb618f623581b31c6223d` |
| ES-X05 status/classification | `BLOCKED` / `PARKED_BLOCKED` |
| ES-X05 finalization | branch `package/es-x05-finalization`, open PR #74 |
| ES-X05 staging disposition | `OWNER-APPROVED INFRASTRUCTURE EXCEPTION — STAGING DEFERRED` to `ES-V02`; not a pass |
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
- Coverage run `31116854096`, job `92668751419`, passed Java 21 clean build, all unit and MariaDB/Testcontainers tests and migrations, JaCoCo, runtime-JAR/provider-leak checks, artifact upload, and Codacy coverage upload on exact product head `4c818bb3aea953d3f877efc8a48a9175ba219d38`.
- CodeRabbit passed with zero unresolved valid review threads.
- PR #73 merged normally as `345b7bbcec6facb45c7f96b0e6e181ac7a38e1da`; implementation containment and external parity passed.

## Owner-approved deferred staging record

Evidence label: **OWNER-APPROVED INFRASTRUCTURE EXCEPTION — STAGING DEFERRED**.

- Owner/approval: repository owner `wsg138`, explicit instruction in the current ChatGPT project conversation on 2026-08-06 to skip the ES-X05 live staging test for now, continue development, and run a larger combined test later through the developing PySentinel system.
- Named deferred package: `ES-V02 — Distributed and Java/Bedrock staging`.
- Original unavailable private run: aggregate dispatcher `31116852061`, job `92668521113`, dispatched `wsg138/EnthusiaStaff-Staging` run `31116860919`; build job `92668551209` had runner ID `0`, empty runner name, and steps `[]`; downstream Pi job `92668600472` was skipped.
- No checkout, build, product test, artifact validation, server boot, restart, migration, or other product-validation step executed in that private run. It is not a staging pass.
- `ES-V02` must later pin the combined implementation set and cover exact-head build/artifact provenance, Paper boot/restart, V17 and later migrations, config/reload changes, appeal/auth persistence, provider behavior, Java/Bedrock interaction, safely implemented automated test-client behavior, and complete cleanup/isolation.
- This approval does not authorize production credentials, production player accounts, production data, production routes, issue #43, LiteBans replacement, cutover, or authority activation. Automated Minecraft-account credential handling requires separate safe implementation, security review, and owner authorization.

## Current ordinary hosted blocker

The staging exception cannot excuse the repository's ordinary hosted exact-head Coverage gate.

- After GitHub Status reported Actions operational and the owner directed continuation, a tree-identical retrigger froze finalization attempt head `e4be594d8dd811bd27b13c3a2207fcdb06a0a769`.
- Coverage run `31122594623`, job `92686159333`, waited from `2026-08-06T17:15:27Z` through `17:30:28Z`, then was cancelled with runner ID `0`, empty runner name, and steps `[]`. No checkout or product/package validation executed.
- Exact-head Pi wrapper run `31122594379` successfully dispatched private run `31122730837`, proving dispatch/auth recovery.
- Private build job `92686599218` nevertheless waited from `17:18:15Z` through `17:33:16Z`, then was cancelled with runner ID `0`, empty runner name, and steps `[]`; downstream Pi boot/restart job `92688928718` was skipped. The wrapper published failure.
- These are infrastructure-unavailable results, not product failures or passes. The recovery statement did not result in ordinary hosted runner allocation.

Exact unblock: do not repeat an identical retry until there is new evidence of ordinary Ubuntu hosted-runner recovery or another material runner/billing/authorization/configuration change. Then reconcile `main`, standalone `main`, PR #74, reviews, scope, and parity; freeze a synchronized finalization head; obtain successful ordinary hosted Coverage for that exact head; retain the ES-V02 staging deferral; merge PR #74 normally; verify containment and persistent `COMPLETE` state; and stop without selecting another package.

## Safety boundaries

No production credentials, Cloudflare secrets, punishment records, player records, or private database data are committed. Issue #43, production cutover, Flyway repair/history rewriting, and authority activation remain deferred and excluded. ES-P02 PR #70 and its branch were not modified. No follow-on package was selected or activated.
