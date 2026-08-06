# ES-X05 handoff — Website UX, authentication, and appeals

Date: 2026-08-06
Status: `PARTIAL`
Classification: `ACTIONABLE_CONTINUATION`

## Selection
ES-P02 remains `PARKED_BLOCKED`; ES-X05 was selected as the next eligible package. ES-P02 PR #70 and its branch were not modified.

## Standalone progress
- Baseline site PR `wsg138/enthusia-site#1` merged as `042b503b7a4adc2627f2259a09e7d7394ced06ce`.
- Continuation PR `wsg138/enthusia-site#2` remains open and mergeable.
- Branch: `package/es-x05-appeal-hardening`.
- Exact head: `11e68b60ef874a01f8b6f04f72bd8d694c496b56`.
- Site validation run `31105809682`: success.
- The workflow runs repository validation, appeal security tests, nested potion-preview tests, and `npm run build`.

## Completed behavior
- Verified Cloudflare Access identity and immutable linked Minecraft identity.
- Same-origin appeal/reviewer mutations.
- Fail-closed KV rate limits and bounded inputs.
- Identity-bound appeal idempotency.
- Exact eligible-punishment selection.
- Role-gated reviewer listing and versioned/idempotent stale-safe decisions.
- Correct potion variant IDs and exact vanilla tint colors inside shulker boxes/bundles, including serialized live updates.

## Production build investigation
The red `Cloudflare Pages: enthusia-site` check is not introduced by ES-X05. It also fails on untouched prior main `9408166c75def0b55caa8d38fb546c6e77ea1f7d`. Both that commit and current head deploy successfully to `enthusia-market-preview`, while current repository tests/build pass. The production project therefore has a pre-existing Cloudflare project configuration/build-setting failure. GitHub exposes no build log beyond the Cloudflare dashboard link.

## Aggregate and contract state
- `components/enthusia-site/` remains `IMPORT_PENDING`.
- Deterministic standalone/aggregate hash parity has not been produced.
- The real private EnthusiaStaff appeal service contract is not yet proven end to end; placeholder internal service routes are not acceptance evidence.

## Exact resume instructions
1. Resume `wsg138/enthusia-site#2` at exact head `11e68b60ef874a01f8b6f04f72bd8d694c496b56` or reconcile any newer head.
2. Use Cloudflare dashboard access to inspect and correct the `enthusia-site` project build settings/bindings; obtain a successful exact-head production deployment.
3. Reconcile the site routes with the real private EnthusiaStaff appeal contract and add contract tests without trusting browser identity or roles.
4. Merge the verified standalone continuation normally.
5. Run the canonical component import into `components/enthusia-site/`, prove content-hash parity, and open the required aggregate PR.
6. Validate, review, merge, update all final state/hashes, delete temporary branches, and only then mark ES-X05 `COMPLETE`.

## Safety
No production credentials or player records were committed. Authentication, origin, role, rate-limit, and upstream-service boundaries fail closed. No Access token is logged.
