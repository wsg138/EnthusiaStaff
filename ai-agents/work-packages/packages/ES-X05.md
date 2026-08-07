# `ES-X05` — Website UX, authentication, and appeals

## 1. Package identity

`ES-X05`; external/multi-repository; primary `COMP-SITE`; other `COMP-STAFF`; priority `35`; dependency `ES-P01`.

## 2. Status

`MERGE_PENDING` — `ACTIONABLE_CONTINUATION`

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
- Exact vanilla potion IDs and tint colors for potions, splash potions, lingering potions, and tipped arrows nested in shulker boxes or bundles; live updates are serialized and failed manifest loads may retry.

## 5. Standalone evidence

- Repository: `wsg138/enthusia-site`
- Starting main: `9408166c75def0b55caa8d38fb546c6e77ea1f7d`
- Baseline PR #1 merge: `042b503b7a4adc2627f2259a09e7d7394ced06ce`
- Continuation branch: `package/es-x05-appeal-hardening`
- Continuation PR: `wsg138/enthusia-site#2`
- Final reviewed head: `1a45b32e372cf6939c078a0d7986655e7ed639d6`
- Hosted validation: run `31113188453` — success.
- Production `enthusia-site` Cloudflare deployment: success.
- Market-preview Cloudflare deployment: success.
- Review: zero unresolved threads; Codacy passed with zero annotations.
- Normal merge commit and current standalone `main`: `b385f78c522f452cc48d78ed19fd2ee82573f64d`.

## 6. Completed aggregate and contract work

- Aggregate branch: `package/es-x05-state-publication`.
- Aggregate PR: `wsg138/EnthusiaStaff#73`.
- Frozen aggregate product head before final state reconciliation: `96912301fc425ac6f5eff9349ee3b3d543d122eb`.
- The site and Velocity implementation use the same exact POST routes and payloads:
  - `/v1/website/appeals/eligible`
  - `/v1/website/appeals/submit`
  - `/v1/website/appeals/reviewer/list`
  - `/v1/website/appeals/reviewer/{appealId}/decision`
- The Velocity API validates bearer/HMAC authentication, timestamp skew, nonce replay, body hash, canonical UUIDs, bounded request bodies, fixed fields, and role/service-boundary authorization. It is loopback-only and is intended to sit behind the deployment proxy for `staff-api.enthusia.info`.
- Durable MariaDB appeal workflow includes exact-punishment eligibility, account binding, duplicate prevention, atomic rate limiting, request idempotency, reviewer version checks, appeal-scoped reviewer replay keys, audit events, and exact-sanction approval integration.
- V17 is the only new migration and does not alter V1–V16.
- Integration tests cover lifecycle behavior, stale decisions, duplicate appeals, account binding, rate limits, submission replay, reviewer replay scoping, and exact-sanction acceptance delegation.

## 7. Component synchronization

- Standalone source: merge commit `b385f78c522f452cc48d78ed19fd2ee82573f64d`.
- Aggregate destination: `components/enthusia-site/` in PR #73.
- Canonical component hash: `9910dc90d22be68bf034f03def0cabd617bdf2e9953f87231f11af1166fc07e2`.
- Standalone hash equals aggregate hash.
- Added, missing, and modified path sets are empty.
- Metadata-only `COMPONENT-METADATA.md` is excluded by the canonical sync method.
- Parity evidence: [`2026-08-06-es-x05-component-parity.json`](../../reports/package-handoffs/2026-08-06-es-x05-component-parity.json).

## 8. Remaining completion work

1. Obtain a successful exact-current-head aggregate Coverage run. Run `31115480613` failed before checkout because GitHub could not resolve action downloads; no product step executed.
2. Confirm applicable static-analysis checks and zero valid unresolved review threads on the final head.
3. Reconfirm deterministic parity against standalone `main` at `b385f78c522f452cc48d78ed19fd2ee82573f64d`.
4. Merge aggregate PR #73 by a normal merge commit with the reviewed head unchanged.
5. Verify aggregate-main containment and no unique temporary-branch work.
6. Publish final aggregate merge SHA and `COMPLETE` state, clean temporary branches where tooling permits, and stop without selecting another package.

## 9. Migration and authority boundary

- Current aggregate `main` remains at V16 until PR #73 merges.
- PR #73 adds immutable `V17__website_appeal_workflow.sql`.
- LiteBans remains authoritative. Issue #43 remains open, deferred, and excluded.
- ES-X05 does not deploy or authorize a LiteBans cutover, access production punishment data, rewrite Flyway history, or activate EnthusiaStaff punishment authority.

## 10. Security/privacy boundary

No production credentials, Access tokens, player records, punishment records, or private database data are committed. Authentication, reviewer rank, origin, replay, rate-limit, timeout, request-size, and upstream-service configuration fail closed. Browser-provided identity and rank are never trusted as service authority.

## 11. Canonical handoff

[`2026-08-06-es-x05-website-auth-appeals.md`](../../reports/agent-handoffs/2026-08-06-es-x05-website-auth-appeals.md)

## 12. Last update

2026-08-06
