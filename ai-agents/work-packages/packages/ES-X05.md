# `ES-X05` — Website UX, authentication, and appeals

## 1. Package identity

`ES-X05`; external/multi-repository; primary `COMP-SITE`; other `COMP-STAFF`; priority `35`; dependency `ES-P01`.

## 2. Status

`COMPLETE`

Completed by: `ChatGPT sequential ES-X05 completion worker`.

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
- Exact vanilla potion IDs and tint colors for potions, splash potions, lingering potions, and tipped arrows nested in shulker boxes or bundles; live updates are serialized and failed manifest loads may retry.

## 5. Standalone evidence

- Repository: `wsg138/enthusia-site`.
- Starting main: `9408166c75def0b55caa8d38fb546c6e77ea1f7d`.
- Baseline PR #1 merge: `042b503b7a4adc2627f2259a09e7d7394ced06ce`.
- Continuation PR: `wsg138/enthusia-site#2`.
- Final reviewed head: `1a45b32e372cf6939c078a0d7986655e7ed639d6`.
- Hosted validation run `31113188453`: success.
- Production `enthusia-site` and market-preview Cloudflare deployments: success.
- Review: zero unresolved threads; Codacy passed with zero annotations.
- Normal merge commit and current standalone `main`: `b385f78c522f452cc48d78ed19fd2ee82573f64d`.
- Containment: branch `package/es-x05-appeal-hardening` has zero commits or files absent from the merge commit.

## 6. Completed aggregate and contract work

- Starting aggregate main: `515bd9a8591505c043b413f5b9ecb3e272c6d6f2` for the continuation pass.
- Aggregate PR: `wsg138/EnthusiaStaff#73`.
- Frozen aggregate product head: `96912301fc425ac6f5eff9349ee3b3d543d122eb`.
- Exact validated aggregate head: `4c818bb3aea953d3f877efc8a48a9175ba219d38`.
- Normal aggregate merge and resulting `main`: `345b7bbcec6facb45c7f96b0e6e181ac7a38e1da`.
- The site and Velocity implementation use the same exact POST routes and payloads:
  - `/v1/website/appeals/eligible`
  - `/v1/website/appeals/submit`
  - `/v1/website/appeals/reviewer/list`
  - `/v1/website/appeals/reviewer/{appealId}/decision`
- The Velocity API validates bearer/HMAC authentication, timestamp skew, nonce replay, body hash, canonical UUIDs, bounded request bodies, fixed fields, and role/service-boundary authorization. It is loopback-only behind the deployment proxy for `staff-api.enthusia.info`.
- Durable MariaDB appeal workflow includes exact-punishment eligibility, account binding, duplicate prevention, atomic rate limiting, request idempotency, reviewer version checks, appeal-scoped reviewer replay keys, audit events, and exact-sanction approval integration.
- V17 is the only new migration and does not alter V1–V16.
- Integration tests cover lifecycle behavior, stale decisions, duplicate appeals, account binding, rate limits, submission replay, reviewer replay scoping, and exact-sanction acceptance delegation.

## 7. Aggregate validation and review

- Coverage run `31116854096` checked out exact head `4c818bb3aea953d3f877efc8a48a9175ba219d38`.
- `./gradlew clean build jacocoAggregateReport runtimeJars` completed successfully on Java 21.
- All unit and integration tests passed, including MariaDB/Testcontainers migration and appeal-workflow coverage.
- Aggregate JaCoCo coverage: lines `47.50%`, branches `38.47%`, instructions `50.16%`.
- Runtime-JAR inspection found one valid Paper JAR and one valid Velocity JAR, no corrupt entries, and zero leaked provider API classes across 24 checked source types.
- Validation artifact `java-21-validation` uploaded successfully; Codacy coverage upload and final notification succeeded.
- CodeRabbit passed and all valid review threads were resolved.
- Containment verification shows the implementation head has zero commits or files absent from aggregate merge `345b7bbcec6facb45c7f96b0e6e181ac7a38e1da`.

## 8. Component synchronization

- Standalone source: merge commit `b385f78c522f452cc48d78ed19fd2ee82573f64d`.
- Aggregate destination: `components/enthusia-site/` in aggregate merge `345b7bbcec6facb45c7f96b0e6e181ac7a38e1da`.
- Canonical component hash: `9910dc90d22be68bf034f03def0cabd617bdf2e9953f87231f11af1166fc07e2`.
- Standalone hash equals aggregate hash.
- Added, missing, and modified path sets are empty.
- Metadata-only `COMPONENT-METADATA.md` is excluded by the canonical sync method.
- Parity evidence: [`2026-08-06-es-x05-component-parity.json`](../../reports/package-handoffs/2026-08-06-es-x05-component-parity.json).

## 9. Migration and authority boundary

- Aggregate `main` now includes immutable `V17__website_appeal_workflow.sql`; V1–V16 remain unchanged.
- LiteBans remains authoritative. Issue #43 remains open, deferred, and excluded.
- ES-X05 does not deploy or authorize a LiteBans cutover, access production punishment data, rewrite Flyway history, or activate EnthusiaStaff punishment authority.

## 10. Security/privacy boundary

No production credentials, Access tokens, player records, punishment records, or private database data are committed. Authentication, reviewer rank, origin, replay, rate-limit, timeout, request-size, and upstream-service configuration fail closed. Browser-provided identity and rank are never trusted as service authority.

## 11. Final state

- Package status: `COMPLETE`.
- No new package was selected or activated during finalization.
- Temporary branches contain no unique work; branch deletion is left to tooling that exposes a safe delete-ref operation.

## 12. Canonical handoff

[`2026-08-06-es-x05-website-auth-appeals.md`](../../reports/agent-handoffs/2026-08-06-es-x05-website-auth-appeals.md)

## 13. Last update

2026-08-06
