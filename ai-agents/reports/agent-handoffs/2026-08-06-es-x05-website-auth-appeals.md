# ES-X05 handoff — Website UX, authentication, and appeals

Date: 2026-08-06
Status: `MERGE_PENDING`
Classification: `ACTIONABLE_CONTINUATION`
Owner priority: `35`
Canonical package: `ES-X05`

## Selection

ES-P02 remains `BLOCKED` / `PARKED_BLOCKED` in preserved branch `package/es-p02-runtime-db-recovery` and open PR #70. Its external staging runner/authorization condition has not changed, so it was left untouched. Live GitHub exposed ES-X05 as the highest-priority actionable continuation through existing standalone and aggregate work; no second package was selected.

## Standalone completion

- Repository: `wsg138/enthusia-site`.
- Baseline PR #1 merged normally as `042b503b7a4adc2627f2259a09e7d7394ced06ce`.
- Continuation PR #2 final reviewed head: `1a45b32e372cf6939c078a0d7986655e7ed639d6`.
- Site validation run `31113188453`: success.
- Production `enthusia-site` Cloudflare deployment: success.
- `enthusia-market-preview` Cloudflare deployment: success.
- Codacy: success with zero annotations.
- CodeRabbit: zero unresolved review threads.
- PR #2 merged normally as `b385f78c522f452cc48d78ed19fd2ee82573f64d`.
- Standalone `main` currently points to that merge commit.

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

## Aggregate implementation

- Repository: `wsg138/EnthusiaStaff`.
- Aggregate branch: `package/es-x05-state-publication`.
- Aggregate PR: #73.
- Starting aggregate `main`: `515bd9a8591505c043b413f5b9ecb3e272c6d6f2` for this continuation pass.
- Frozen product head before state reconciliation: `96912301fc425ac6f5eff9349ee3b3d543d122eb`.
- Migration boundary: V1–V16 remain unchanged; PR #73 adds `V17__website_appeal_workflow.sql`.

The aggregate adds a durable exact-punishment appeal workflow, MariaDB-backed atomic rate limiting, account and punishment binding, identity-scoped submission idempotency, appeal-scoped reviewer decision replay protection, optimistic revisions, audit events, role authorization, signed private API routes, bounded request bodies, nonce replay protection, and exact-sanction acceptance delegation.

## Contract verification

The standalone site and aggregate Velocity API match on exact POST route and JSON contracts:

- `/v1/website/appeals/eligible` — `accountId`.
- `/v1/website/appeals/submit` — `punishmentId`, `accountId`, `username`, `reason`, `idempotencyKey`.
- `/v1/website/appeals/reviewer/list` — `actorAccountId`, `actorRank`, `status`, `cursor`, `limit`.
- `/v1/website/appeals/reviewer/{appealId}/decision` — `actorAccountId`, `actorRank`, `decision`, `expectedVersion`, `note`, `idempotencyKey`.

The site signs the exact request target used by the Velocity authenticator. Velocity validates bearer value, timestamp skew, nonce form and persistent replay state, body hash, HMAC, canonical UUIDs, fixed allowed fields, and service-boundary reviewer authorization. The Java server is loopback-only and therefore requires the deployment proxy for the fixed public origin; no direct public listener is introduced.

## Component parity

- Standalone source SHA: `b385f78c522f452cc48d78ed19fd2ee82573f64d`.
- Aggregate component path: `components/enthusia-site/`.
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
- Added this required canonical per-PR handoff under `reports/agent-handoffs/` and recorded migration, priority, intended post-merge status, and handoff fields explicitly.

## Current validation state

Coverage run `31115480613` for aggregate product head `96912301fc425ac6f5eff9349ee3b3d543d122eb` failed during runner setup because GitHub returned `Service Unavailable` while resolving action downloads. Checkout and product code did not execute. This is neither a product failure nor a pass. State reconciliation commits require a new exact-head run before merge.

## Exact completion steps

1. Run the full applicable aggregate hosted build/test/migration/coverage/runtime-JAR checks on the exact final PR head.
2. Confirm applicable static analysis and zero valid unresolved review threads.
3. Reconfirm standalone `main` at `b385f78c522f452cc48d78ed19fd2ee82573f64d` and unchanged component hash parity.
4. Merge PR #73 with a normal merge commit and the reviewed head unchanged.
5. Verify aggregate-main containment and no unique temporary-branch work.
6. Publish final merge SHA and `COMPLETE` state in the registry, package file, workspace state, component metadata, and handoff pointer.
7. Delete temporary branches where tooling permits and stop without activating another package.

## Safety and exclusions

No production credentials, Access tokens, punishment records, player records, or private database data were committed. Authentication, identity, rank, origin, timeout, body size, replay, and rate-limit boundaries fail closed. LiteBans remains authoritative. Issue #43 remains open and deferred. No production cutover, Flyway repair/rewrite, private-data validation, authority activation, or ES-P02 change is included.
