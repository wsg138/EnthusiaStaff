# EnthusiaStaff workspace state

Last updated: 2026-08-06

Live GitHub state overrides stale records, but persistent package state must be published to `main`.

## Current routing

| Field | Value |
| --- | --- |
| Completed prerequisite | `ES-P01 — Exact-sanction appeal isolation` |
| Parked package | `ES-P02 — Runtime database recovery and Velocity reload` |
| ES-P02 classification | `PARKED_BLOCKED` while its runner/authorization condition is unchanged |
| Preserved ES-P02 work | branch `package/es-p02-runtime-db-recovery`, open PR #70, current package-record head `80d4ea840f34017c09afb618f623581b31c6223d` |
| Active package | `ES-X05 — Website UX, authentication, and appeals` |
| ES-X05 status | `MERGE_PENDING` |
| ES-X05 classification | `ACTIONABLE_CONTINUATION` |
| Owner priority | `35` |
| Aggregate work | `wsg138/EnthusiaStaff#73`, branch `package/es-x05-state-publication` |
| Frozen aggregate product head | `96912301fc425ac6f5eff9349ee3b3d543d122eb` |
| Standalone repository | `wsg138/enthusia-site` |
| Standalone reviewed head | `1a45b32e372cf6939c078a0d7986655e7ed639d6` |
| Standalone merge | PR #2 merged normally as `b385f78c522f452cc48d78ed19fd2ee82573f64d`; standalone `main` points to that merge |
| Standalone validation | Site validation run `31113188453` succeeded; production and market-preview Cloudflare deployments succeeded; zero unresolved review threads |
| Component parity | verified at SHA-256 `9910dc90d22be68bf034f03def0cabd617bdf2e9953f87231f11af1166fc07e2` |
| Migration boundary | aggregate PR adds immutable `V17__website_appeal_workflow.sql`; current aggregate `main` remains at V16 until PR #73 merges |
| Intended post-merge status | `COMPLETE`, only after exact-head aggregate validation/review, normal merge of PR #73, containment, parity, and final state publication |
| Canonical handoff | [`2026-08-06-es-x05-website-auth-appeals.md`](reports/agent-handoffs/2026-08-06-es-x05-website-auth-appeals.md) |
| Deferred validation | `ES-V02 — Distributed and Java/Bedrock staging` |

## ES-X05 completed evidence

- Standalone PR #2 verifies Cloudflare Access JWT signature, issuer, audience, expiry, and not-before claims; derives the linked Minecraft identity from verified claims; and keeps appeal/reviewer pages off normal site navigation.
- The site sends only allowlisted POST routes to `https://staff-api.enthusia.info`, with a fixed bearer token, HMAC-SHA256 over method/path/timestamp/nonce/body hash, a seven-second timeout, strict same-origin browser mutations, and fail-closed configuration.
- Aggregate Velocity routes implement the same exact path and JSON contracts for eligible punishments, submission, reviewer listing, and versioned decisions. The API is loopback-only behind the deployment proxy, authenticates bearer/HMAC requests in constant time, bounds bodies, enforces timestamp skew, and persists nonce replay protection.
- MariaDB-backed appeal submission rate limiting is atomic under row locks and scopes replay exemptions to account, exact punishment, and idempotency key. Submission and reviewer-decision idempotency constraints are identity- and appeal-scoped.
- Appeal approval delegates to the exact-sanction acceptance boundary rather than ending an entire combined case.
- The standalone merged tree and `components/enthusia-site/` import have no added, missing, or modified files under the canonical component-sync hash method.
- Nested potions in shulker boxes/bundles use exact namespaced potion IDs and vanilla tint colors before rendering; live updates are serialized and transient manifest failures can retry.

## Current aggregate gate

- PR #73 is the only aggregate implementation PR for ES-X05.
- Its last product commit is `96912301fc425ac6f5eff9349ee3b3d543d122eb`.
- Coverage run `31115480613` did not execute checkout or product code because GitHub returned `Service Unavailable` while resolving action downloads. It is transient infrastructure evidence, not a product failure or a passing gate.
- Final merge still requires a successful exact-head hosted build/test/migration/coverage/runtime-JAR gate, applicable static analysis, zero valid unresolved review threads, unchanged parity, and normal merge-commit integration.

## Required next-worker behavior

1. Leave ES-P02 PR #70 and its branch untouched unless the external staging unblock condition demonstrably changes.
2. Resume ES-X05 PR #73; do not select another package.
3. Validate the exact current PR head, address every valid review finding, and preserve V1–V16 migration immutability while validating V17.
4. Reconfirm standalone `main` at `b385f78c522f452cc48d78ed19fd2ee82573f64d` and deterministic aggregate parity.
5. Merge PR #73 only through a normal merge commit after all gates pass and the reviewed head is unchanged.
6. Verify merge containment and no unique branch work, publish final merge hashes and `COMPLETE` state through the canonical records, clean temporary branches where tooling permits, and stop without activating another package.

## Safety boundaries

- No production credentials, Cloudflare secrets, punishment records, player records, or private database data are committed.
- Authentication, origin, reviewer role, rate-limit, replay, body-size, timeout, and upstream-service configuration fail closed.
- LiteBans remains authoritative; issue #43 remains open and deferred. ES-X05 does not deploy or authorize a punishment-authority cutover.
- ES-P02 PR #70 and its preserved branch must not be modified merely for drift while the external blocker is unchanged.
