# `ES-X05` — Website UX, authentication, and appeals

## 1. Package identity

`ES-X05`; external/multi-repository; primary `COMP-SITE`; other `COMP-STAFF`; priority `35`; dependency `ES-P01`.

## 2. Status

`BLOCKED` — `PARKED_BLOCKED`

Assigned worker: `ChatGPT sequential ES-X05 completion worker`.

## 3. Objective

Complete website authentication, exact-punishment appeal UX, reviewer controls, privacy, rate limiting, retries, the real private EnthusiaStaff appeal contract, production site deployment, and verified standalone/aggregate synchronization.

## 4. Completed standalone-site work

- Cloudflare Access JWT signature, issuer, audience, expiry, and not-before verification.
- Canonical Minecraft identity derived only from verified claims.
- Authenticated exact-punishment selector and appeal submission boundary.
- Privileged reviewer listing and versioned decision boundary.
- Same-origin mutation enforcement, identity-bound idempotency, bounded bodies, bounded upstream requests, and stale-decision protection.
- Fixed-origin, allowlisted, bearer-plus-HMAC private Staff API requests with timestamp, nonce, and body-hash authentication.
- Hosted Node 22 test/build workflow with persisted checkout credentials disabled.
- Exact vanilla potion IDs and tint colors for nested potion items; serialized live updates and retryable transient manifest failures.

## 5. Standalone evidence

- Repository: `wsg138/enthusia-site`.
- Final reviewed head: `1a45b32e372cf6939c078a0d7986655e7ed639d6`.
- Hosted validation run `31113188453`: success.
- Production and market-preview Cloudflare deployments: success.
- Codacy: success with zero annotations; zero unresolved review threads.
- PR #2 normal merge and current standalone `main`: `b385f78c522f452cc48d78ed19fd2ee82573f64d`.
- Containment: temporary standalone branch has zero unique commits or files.

## 6. Completed aggregate implementation

- Starting aggregate main: `515bd9a8591505c043b413f5b9ecb3e272c6d6f2` for the continuation pass.
- Aggregate PR: `wsg138/EnthusiaStaff#73`.
- Frozen aggregate product head: `96912301fc425ac6f5eff9349ee3b3d543d122eb`.
- Exact hosted-validation/review head: `4c818bb3aea953d3f877efc8a48a9175ba219d38`.
- Normal implementation merge and resulting aggregate `main`: `345b7bbcec6facb45c7f96b0e6e181ac7a38e1da`.
- Finalization branch/PR: `package/es-x05-finalization`; PR #74.
- Durable MariaDB appeal workflow, atomic rate limiting, identity-scoped submission idempotency, appeal-scoped reviewer replay protection, optimistic revisions, audit events, signed private API routes, and exact-sanction approval delegation are integrated.
- V17 is the only new migration; V1–V16 remain unchanged.

## 7. Successful aggregate evidence

- Coverage run `31116854096` checked out exact head `4c818bb3aea953d3f877efc8a48a9175ba219d38` and passed Java 21 clean build, all unit and MariaDB/Testcontainers integration/migration tests, JaCoCo, runtime-JAR integrity/provider-leak checks, artifact upload, and Codacy coverage upload.
- Aggregate JaCoCo: lines `47.50%`, branches `38.47%`, instructions `50.16%`.
- CodeRabbit passed and all valid review threads were resolved.
- Implementation containment passed: the implementation head has zero commits or files absent from merge `345b7bbcec6facb45c7f96b0e6e181ac7a38e1da`.

## 8. Blocking staging/infrastructure evidence

The configured Pi-staging gate did not pass and no owner-approved infrastructure exception exists.

- PR #73 dispatcher run `31116852061` targeted exact product head `4c818bb3aea953d3f877efc8a48a9175ba219d38` and dispatched staging run `31116860919`.
- The staging repository job `92668551209` (`Build trusted EnthusiaStaff Paper runtime`) completed with `runner_id: 0`, an empty runner name, and zero steps. No checkout, build, product test, boot, or restart executed. The Pi job was skipped.
- Post-merge dispatcher run `31117490156` failed during action preparation when GitHub returned `Service Unavailable`; product steps did not execute.
- Finalization-head Coverage run `31117820548` and Pi Staging run `31117820542` were cancelled after roughly fifteen minutes with `runner_id: 0`, empty runner names, and zero steps.
- GitHub Status reported an active major Actions incident/partial outage beginning August 6, 2026 at 15:22 UTC, including runs failing to start or failing partway through. These failures are infrastructure evidence, not product failures, but cancelled/zero-execution gates are not passes.

## 9. Exact unblock condition

Resume ES-X05 only after evidence shows the GitHub Actions/staging runner condition changed. Then:

1. Reconcile aggregate `main`, standalone `main`, PR #74, and both temporary branches.
2. Update/synchronize the finalization branch only as necessary.
3. Freeze the exact finalization head.
4. Obtain a successful ordinary hosted Coverage run and a successful trusted staging build plus Pi boot/restart run for that exact head, or obtain an explicit policy-valid owner infrastructure disposition that does not relabel the missing ordinary hosted build as passed.
5. Reconfirm zero valid review threads and deterministic component parity.
6. Merge PR #74 normally, verify containment/no unique work, publish `COMPLETE`, clean branches where tooling permits, and stop without selecting another package.

Do not manually rerun an identical zero-runner gate until there is evidence of recovery or configuration/capacity change.

## 10. Component synchronization

- Standalone source: `b385f78c522f452cc48d78ed19fd2ee82573f64d`.
- Aggregate implementation merge: `345b7bbcec6facb45c7f96b0e6e181ac7a38e1da`.
- Canonical component hash: `9910dc90d22be68bf034f03def0cabd617bdf2e9953f87231f11af1166fc07e2`.
- Added, missing, and modified path sets are empty; parity is true.
- Evidence: [`2026-08-06-es-x05-component-parity.json`](../../reports/package-handoffs/2026-08-06-es-x05-component-parity.json).

## 11. Authority and privacy boundary

LiteBans remains authoritative. Issue #43, production cutover, production credentials/data, Flyway repair/history rewriting, and authority activation remain excluded. Authentication, reviewer rank, origin, replay, rate-limit, timeout, request-size, and upstream-service configuration fail closed.

## 12. Canonical handoff

[`2026-08-06-es-x05-website-auth-appeals.md`](../../reports/agent-handoffs/2026-08-06-es-x05-website-auth-appeals.md)

## 13. Last update

2026-08-06
