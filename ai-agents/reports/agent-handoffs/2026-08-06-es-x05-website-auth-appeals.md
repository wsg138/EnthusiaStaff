# ES-X05 handoff — Website UX, authentication, and appeals

Date: 2026-08-06
Status: `BLOCKED`
Classification: `PARKED_BLOCKED`
Owner priority: `35`
Canonical package: `ES-X05`

## Selection and scope

ES-P02 remained parked in PR #70 because its external runner/authorization condition did not change. Live GitHub exposed ES-X05 as the highest-priority actionable continuation. Exactly one package was selected; no second package was begun.

## Standalone completion

- Repository: `wsg138/enthusia-site`.
- Final reviewed head: `1a45b32e372cf6939c078a0d7986655e7ed639d6`.
- Site validation run `31113188453`: success.
- Production and market-preview Cloudflare deployments: success.
- Codacy: success with zero annotations; zero unresolved review threads.
- PR #2 normal merge/current standalone `main`: `b385f78c522f452cc48d78ed19fd2ee82573f64d`.
- Standalone containment: zero unique temporary-branch commits or files.

The standalone site verifies Cloudflare Access JWT signature/issuer/audience/time claims, derives linked Minecraft identity only from verified claims, keeps appeal/reviewer pages off normal navigation, enforces same-origin mutations, and sends only allowlisted fixed-origin POST requests signed with bearer plus HMAC over method/path/timestamp/nonce/body hash. Requests are bounded and fail closed.

## Aggregate implementation

- Starting aggregate `main`: `515bd9a8591505c043b413f5b9ecb3e272c6d6f2` for this continuation pass.
- Frozen product head: `96912301fc425ac6f5eff9349ee3b3d543d122eb`.
- Exact hosted-validation/review head: `4c818bb3aea953d3f877efc8a48a9175ba219d38`.
- Aggregate PR #73 normal merge/current aggregate `main`: `345b7bbcec6facb45c7f96b0e6e181ac7a38e1da`.
- Finalization branch/PR: `package/es-x05-finalization`; PR #74.
- V17 is the only new migration; V1–V16 remain unchanged.

The aggregate implements matching exact POST routes, loopback-only bearer/HMAC authentication, timestamp and persistent nonce replay controls, bounded bodies, canonical UUIDs, service-boundary reviewer authorization, durable MariaDB exact-punishment eligibility/submission/review, atomic rate limiting, scoped idempotency, optimistic revisions, audit events, and exact-sanction approval delegation.

## Successful validation and review

- Coverage run `31116854096` checked out exact head `4c818bb3aea953d3f877efc8a48a9175ba219d38` and passed `./gradlew clean build jacocoAggregateReport runtimeJars` on Java 21.
- All unit and MariaDB/Testcontainers migration/integration tests passed.
- JaCoCo: lines `47.50%`, branches `38.47%`, instructions `50.16%`.
- Runtime-JAR/provider-leak inspection passed across 24 source types.
- Validation artifact and Codacy coverage upload succeeded.
- CodeRabbit succeeded and all eight aggregate review threads were resolved.
- PR #73 containment passed: the implementation head has zero commits/files absent from merge `345b7bbcec6facb45c7f96b0e6e181ac7a38e1da`.

## Component parity

- Standalone source: `b385f78c522f452cc48d78ed19fd2ee82573f64d`.
- Aggregate implementation merge: `345b7bbcec6facb45c7f96b0e6e181ac7a38e1da`.
- Standalone and aggregate hash: `9910dc90d22be68bf034f03def0cabd617bdf2e9953f87231f11af1166fc07e2`.
- Added, missing, and modified paths: none.
- Parity: true.
- Evidence: [`2026-08-06-es-x05-component-parity.json`](../package-handoffs/2026-08-06-es-x05-component-parity.json).

## Blocking staging and infrastructure evidence

The configured staging/Pi gate has no passing evidence and no explicit owner-approved infrastructure exception.

1. PR #73 Pi dispatcher run `31116852061` targeted exact product head `4c818bb3aea953d3f877efc8a48a9175ba219d38` and dispatched private staging run `31116860919`.
2. Staging build job `92668551209` (`Build trusted EnthusiaStaff Paper runtime`) finished with `runner_id: 0`, an empty runner name, and no steps. No checkout, ordinary hosted build, product test, boot, or restart ran. The Pi job was skipped.
3. Post-merge dispatcher run `31117490156` allocated a hosted runner but failed during action preparation when GitHub returned `Service Unavailable`; no product step ran.
4. PR #74 exact-head Coverage run `31117820548` and Pi Staging run `31117820542` waited about fifteen minutes, then were cancelled with runner ID 0, empty runner names, and no steps.
5. GitHub Status reported an active major Actions incident/partial outage beginning August 6, 2026 at 15:22 UTC, stating that workflows could fail to start or fail partway through and that Actions API requests could error. This corroborates infrastructure unavailability but does not convert any gate into a pass.

No product failure is claimed, no staging pass is claimed, and no infrastructure exception is invented.

## Exact unblock and resume instructions

Resume ES-X05 before selecting new work only after evidence shows the Actions/staging condition changed. Then:

1. Reconcile current aggregate and standalone heads, PR #74, branch heads, review threads, checks, and parity.
2. Synchronize the finalization branch only if required by real drift.
3. Freeze the exact finalization head.
4. Obtain successful ordinary hosted Coverage and successful trusted staging build plus Pi boot/restart evidence for that exact head; alternatively, use only an explicit policy-valid owner disposition that does not relabel a missing ordinary hosted build as passed.
5. Reconfirm zero valid unresolved findings, unchanged parity, and mergeability.
6. Merge PR #74 normally, verify main containment/no unique work, publish `COMPLETE`, clean temporary branches where safe tooling permits, and stop without selecting another package.

Do not manually rerun identical zero-runner gates until there is evidence of runner/service recovery, billing/authorization change, configuration change, or another material unblock.

## Safety and exclusions

No production credentials, Access tokens, player/punishment records, or private database data were committed. LiteBans remains authoritative. Issue #43, production cutover, Flyway repair/history rewriting, authority activation, and ES-P02 changes remain excluded. Authentication, identity, rank, origin, timeout, body-size, replay, and rate-limit boundaries fail closed.
