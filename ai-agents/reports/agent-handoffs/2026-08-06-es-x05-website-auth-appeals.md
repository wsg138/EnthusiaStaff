# ES-X05 handoff — Website UX, authentication, and appeals

Date: 2026-08-06
Status: `COMPLETE`
Owner priority: `35`
Canonical package: `ES-X05`

## Selection

ES-P02 remains `BLOCKED` / `PARKED_BLOCKED` in preserved branch `package/es-p02-runtime-db-recovery` and open PR #70. Its external staging runner/authorization condition did not change, so it was left untouched. Live GitHub exposed ES-X05 as the highest-priority actionable continuation through existing standalone and aggregate work. Exactly one package was completed; no second package was selected or activated.

## Standalone completion

- Repository: `wsg138/enthusia-site`.
- Baseline PR #1 merged normally as `042b503b7a4adc2627f2259a09e7d7394ced06ce`.
- Continuation PR #2 final reviewed head: `1a45b32e372cf6939c078a0d7986655e7ed639d6`.
- Site validation run `31113188453`: success.
- Production `enthusia-site` and `enthusia-market-preview` Cloudflare deployments: success.
- Codacy: success with zero annotations.
- CodeRabbit: zero unresolved review threads.
- PR #2 merged normally as `b385f78c522f452cc48d78ed19fd2ee82573f64d`; standalone `main` remains at that merge.
- Containment: branch `package/es-x05-appeal-hardening` is behind the merge by one merge commit, ahead by zero, and has no unique files.

## Standalone behavior

- Verifies Cloudflare Access JWT signature, issuer, audience, expiry, and not-before claims.
- Derives immutable linked Minecraft UUID/name only from verified claims.
- Keeps appeal and reviewer pages available only by direct URL rather than main-site navigation.
- Requires same-origin browser mutations and canonical UUIDs.
- Sends only allowlisted POST requests to fixed origin `https://staff-api.enthusia.info`.
- Uses a minimum-length bearer token and HMAC secret; signs method, exact path, timestamp, nonce, and SHA-256 body hash.
- Bounds Staff API requests to seven seconds and fails closed on missing configuration or upstream failure.
- Handles exact eligible punishments, duplicate-safe submissions, versioned reviewer decisions, stale updates, and client retry states.
- Normalizes exact vanilla potion IDs and tint colors recursively inside shulker boxes and bundles, serializes live updates, and retries after transient manifest failures.

## Aggregate completion

- Repository: `wsg138/EnthusiaStaff`.
- Starting aggregate `main`: `515bd9a8591505c043b413f5b9ecb3e272c6d6f2` for this continuation pass.
- Frozen product head before state reconciliation: `96912301fc425ac6f5eff9349ee3b3d543d122eb`.
- Final reviewed and validated PR head: `4c818bb3aea953d3f877efc8a48a9175ba219d38`.
- Aggregate PR #73 merged normally as `345b7bbcec6facb45c7f96b0e6e181ac7a38e1da`; aggregate `main` points to that merge before this completion-publication PR.
- Containment: the implementation head is an ancestor of the merge and has zero commits or files absent from aggregate `main`.
- Migration boundary: V1–V16 remain unchanged; aggregate `main` now includes immutable `V17__website_appeal_workflow.sql`.

The aggregate adds a durable exact-punishment appeal workflow, MariaDB-backed atomic rate limiting, account and punishment binding, identity-scoped submission idempotency, appeal-scoped reviewer decision replay protection, optimistic revisions, audit events, role authorization, signed private API routes, bounded request bodies, nonce replay protection, and exact-sanction acceptance delegation.

## Contract verification

The standalone site and aggregate Velocity API match on exact POST route and JSON contracts:

- `/v1/website/appeals/eligible` — `accountId`.
- `/v1/website/appeals/submit` — `punishmentId`, `accountId`, `username`, `reason`, `idempotencyKey`.
- `/v1/website/appeals/reviewer/list` — `actorAccountId`, `actorRank`, `status`, `cursor`, `limit`.
- `/v1/website/appeals/reviewer/{appealId}/decision` — `actorAccountId`, `actorRank`, `decision`, `expectedVersion`, `note`, `idempotencyKey`.

The site signs the exact request target used by the Velocity authenticator. Velocity validates bearer value, timestamp skew, nonce form and persistent replay state, body hash, HMAC, canonical UUIDs, fixed allowed fields, and service-boundary reviewer authorization. The Java server is loopback-only behind the deployment proxy for the fixed public origin; no direct public listener is introduced.

## Aggregate validation and review

- Coverage run `31116854096` checked out exact head `4c818bb3aea953d3f877efc8a48a9175ba219d38`.
- `./gradlew clean build jacocoAggregateReport runtimeJars --no-daemon --no-build-cache --no-configuration-cache` completed successfully on Java 21 in 6 minutes 29 seconds.
- All unit and integration tests passed, including MariaDB/Testcontainers migrations and appeal-workflow integration coverage.
- Aggregate JaCoCo coverage: lines `47.50%`, branches `38.47%`, instructions `50.16%`.
- Runtime-JAR inspection checked 24 provider API source types and found zero provider API leaks. Paper JAR SHA-256: `a33a5d0c78ce4240ec404077c6a9add4041bd14dc89766350e8b7350ff60a7fa`; Velocity JAR SHA-256: `a85554cc37cd256fc0fe0f8f565285b1bb42a07b2704203a3143e4f8fbf70792`.
- Validation artifact `java-21-validation` uploaded successfully with artifact digest `847d00faa5c37f55f11b90b7f227e763ddb6f0696bf3c5dcc7828d02551d2c9b`.
- Codacy coverage upload and final notification succeeded.
- CodeRabbit succeeded and all eight aggregate review threads were resolved.
- Earlier run `31115480613` failed before checkout due to GitHub action-download infrastructure; it was superseded by the successful exact-head run and is not treated as product evidence.

## Component parity

- Standalone source SHA: `b385f78c522f452cc48d78ed19fd2ee82573f64d`.
- Aggregate component path: `components/enthusia-site/`.
- Aggregate implementation merge: `345b7bbcec6facb45c7f96b0e6e181ac7a38e1da`.
- Standalone hash: `9910dc90d22be68bf034f03def0cabd617bdf2e9953f87231f11af1166fc07e2`.
- Aggregate hash: `9910dc90d22be68bf034f03def0cabd617bdf2e9953f87231f11af1166fc07e2`.
- Added paths: none.
- Missing paths: none.
- Modified paths: none.
- Parity: true.
- Evidence: [`2026-08-06-es-x05-component-parity.json`](../package-handoffs/2026-08-06-es-x05-component-parity.json).

## Review findings addressed

- Removed a temporary write-capable remediation workflow.
- Mapped unavailable default appeal operations to the defined 503 domain error.
- Scoped submission idempotency and rate-limit replay keys to account and exact punishment.
- Evaluated/cleared expired rate-limit windows before granting replay exemptions.
- Stored reviewer decision keys in the decision field and scoped them by exact appeal and reviewer.
- Added coverage proving the same reviewer key may be used independently on different appeals.
- Reconciled the canonical package registry and routing state.
- Published the canonical per-PR handoff under `reports/agent-handoffs/` and recorded migration, priority, exact heads, merge hashes, parity, and final package status.

## Final state

- ES-X05 is `COMPLETE`.
- Standalone and aggregate package-specific PRs merged normally.
- Exact-head validation, review, deterministic parity, and merge containment passed.
- No new package was selected or activated.
- ES-P02 PR #70 and its branch were not modified.
- Temporary branches contain no unique work. The available connector does not expose a safe branch-delete action, so branch deletion is not claimed.

## Safety and exclusions

No production credentials, Access tokens, punishment records, player records, or private database data were committed. Authentication, identity, rank, origin, timeout, body size, replay, and rate-limit boundaries fail closed. LiteBans remains authoritative. Issue #43 remains open and deferred. No production cutover, Flyway repair/rewrite, private-data validation, authority activation, or ES-P02 change is included.
