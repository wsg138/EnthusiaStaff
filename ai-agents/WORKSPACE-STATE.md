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
| Active implementation package | `NONE` |
| Completed package priority | ES-X05 owner priority `35` |
| Standalone repository | `wsg138/enthusia-site` |
| Standalone reviewed head | `1a45b32e372cf6939c078a0d7986655e7ed639d6` |
| Standalone merge / main | `b385f78c522f452cc48d78ed19fd2ee82573f64d` via PR #2 |
| Aggregate exact validated head | `4c818bb3aea953d3f877efc8a48a9175ba219d38` |
| Aggregate implementation merge / main | `345b7bbcec6facb45c7f96b0e6e181ac7a38e1da` via PR #73 |
| Frozen aggregate product head | `96912301fc425ac6f5eff9349ee3b3d543d122eb` |
| Component parity | verified at SHA-256 `9910dc90d22be68bf034f03def0cabd617bdf2e9953f87231f11af1166fc07e2`; no added, missing, or modified component paths |
| Migration boundary | aggregate `main` now includes immutable `V17__website_appeal_workflow.sql`; V1–V16 remain unchanged |
| Canonical handoff | [`2026-08-06-es-x05-website-auth-appeals.md`](reports/agent-handoffs/2026-08-06-es-x05-website-auth-appeals.md) |
| Deferred validation | `ES-V02 — Distributed and Java/Bedrock staging` remains deferred because its other dependencies are incomplete |

## ES-X05 completion evidence

- Standalone PR #2 verifies Cloudflare Access JWT signature, issuer, audience, expiry, and not-before claims; derives linked Minecraft identity only from verified claims; and keeps appeal/reviewer pages off normal site navigation.
- The site sends only allowlisted POST routes to `https://staff-api.enthusia.info`, with a fixed bearer token, HMAC-SHA256 over method/path/timestamp/nonce/body hash, a seven-second timeout, strict same-origin browser mutations, and fail-closed configuration.
- Aggregate Velocity routes implement the same exact path and JSON contracts for eligible punishments, submission, reviewer listing, and versioned decisions. The loopback-only API validates bearer/HMAC requests in constant time, bounds bodies, enforces timestamp skew, and persists nonce replay protection.
- MariaDB-backed appeal submission rate limiting is atomic under row locks and scopes replay exemptions to account, exact punishment, and idempotency key. Submission and reviewer-decision idempotency constraints are identity- and appeal-scoped.
- Appeal approval delegates to the exact-sanction acceptance boundary rather than ending an entire combined case.
- Standalone validation run `31113188453`, production and market-preview Cloudflare deployments, Codacy, and review all passed before normal standalone merge.
- Aggregate Coverage run `31116854096` checked out exact head `4c818bb3aea953d3f877efc8a48a9175ba219d38` and passed the Java 21 clean build, all tests including MariaDB integration/migrations, aggregate JaCoCo report, runtime-JAR integrity/provider-leak inspection, artifact upload, and Codacy coverage upload.
- Aggregate review completed with CodeRabbit success and zero unresolved valid review threads.
- PR #73 merged normally as `345b7bbcec6facb45c7f96b0e6e181ac7a38e1da`; containment verification shows the implementation head has zero commits or files absent from aggregate `main`.

## Post-package state

- ES-X05 is `COMPLETE`.
- No new package was selected or activated during finalization.
- Future workers must reconcile live GitHub, classify every incomplete package, preserve ES-P02 while its external blocker is unchanged, then follow the canonical priority rules.

## Safety boundaries

- No production credentials, Cloudflare secrets, punishment records, player records, or private database data are committed.
- Authentication, origin, reviewer role, rate-limit, replay, body-size, timeout, and upstream-service configuration fail closed.
- LiteBans remains authoritative; issue #43 remains open and deferred. ES-X05 does not deploy or authorize a punishment-authority cutover.
- ES-P02 PR #70 and its preserved branch were not modified.
