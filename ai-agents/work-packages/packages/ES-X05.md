# `ES-X05` — Website UX, authentication, and appeals

## 1. Package identity
`ES-X05`; External/multi-repository; primary `COMP-SITE`; other `COMP-STAFF`; priority 35.

## 2. Status
`PARTIAL` — `ACTIONABLE_CONTINUATION`

## 3. Objective
Complete website authentication, exact-punishment appeal UX, reviewer controls, privacy, rate limiting, retries, and verified aggregate synchronization.

## 4. Completed standalone-site work
- Cloudflare Access JWT signature, issuer, audience, expiry, and not-before verification.
- Canonical Minecraft identity derived only from verified claims.
- Authenticated exact-punishment selector and appeal submission boundary.
- Privileged reviewer listing and versioned decision boundary.
- Same-origin mutation enforcement, fail-closed KV rate limiting, identity-bound idempotency, bounded bodies, and stale-decision protection.
- Hosted Node 22 test/build workflow.
- Correct exact vanilla potion IDs and tint colors for potions, splash potions, lingering potions, and tipped arrows nested in shulker boxes or bundles; live updates are serialized before rendering.

## 5. Standalone evidence
- Repository: `wsg138/enthusia-site`
- Starting main: `9408166c75def0b55caa8d38fb546c6e77ea1f7d`
- Baseline PR #1 reviewed head: `cce9cff6243ee757db9d470eb7d8d7735c8c3495`
- Baseline merge: `042b503b7a4adc2627f2259a09e7d7394ced06ce`
- Continuation branch: `package/es-x05-appeal-hardening`
- Continuation PR: `wsg138/enthusia-site#2`
- Exact head: `11e68b60ef874a01f8b6f04f72bd8d694c496b56`
- Hosted validation run: `31105809682` — success.
- Market preview Cloudflare deployment: success.
- Production `enthusia-site` Cloudflare deployment: failure with no code annotation.

## 6. Production build finding
The production `enthusia-site` Cloudflare project failed on untouched pre-package main `9408166c75def0b55caa8d38fb546c6e77ea1f7d` and continues to fail on `11e68b60ef874a01f8b6f04f72bd8d694c496b56`. The same exact commits deploy successfully to `enthusia-market-preview`, and the repository test/build workflow succeeds. This is a pre-existing production Cloudflare project configuration/build-setting failure, not evidence of a source build failure. Do not mark deployment accepted until the production project settings/logs are corrected and an exact-head deployment succeeds.

## 7. Remaining completion work
- Correct the production Cloudflare `enthusia-site` project configuration using its dashboard logs and obtain a successful exact-head deployment.
- Reconcile and verify the real private EnthusiaStaff appeal service contract; placeholder service paths are not end-to-end acceptance evidence.
- Import the standalone site into `components/enthusia-site/` using the canonical sync tooling.
- Prove standalone/aggregate content-hash parity.
- Open, validate, review, and merge the required EnthusiaStaff aggregate PR.
- Merge standalone PR #2 only after required checks and contract review are satisfied.
- Record final merge hashes and clean temporary branches before marking `COMPLETE`.

## 8. Resume state
Resume ES-X05 from `wsg138/enthusia-site#2`. Do not select another package. The next worker needs Cloudflare dashboard access or owner-supplied production build logs, plus an authenticated checkout capable of the canonical aggregate import/hash process.

## 9. Security/privacy boundary
No production credentials or player data were committed. Authentication, reviewer role, rate-limit, origin, and upstream-service configuration fail closed. No Access token is logged.

## 10. Handoff
[`2026-08-06-es-x05-website-auth-appeals.md`](../../reports/package-handoffs/2026-08-06-es-x05-website-auth-appeals.md)

## 11. Last update
2026-08-06
