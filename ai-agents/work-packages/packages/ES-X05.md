# `ES-X05` — Website UX, authentication, and appeals

## 1. Package identity
`ES-X05`; External/multi-repository; primary `COMP-SITE`; other `COMP-STAFF`; priority 35; conditional parallel safety after exact-sanction contract.

## 2. Status
`PARTIAL`

## 3. Objective
Complete website authentication, punishment/appeal UX, staff review workflow, privacy, rate limiting, retries, and private-contract integration.

## 4. Completed standalone-site work
- Cloudflare Access JWT signature, issuer, audience, expiry, and not-before verification.
- Canonical Minecraft identity derived only from verified claims and frozen in the request session.
- Authenticated appeal form and API without browser-editable identity fields.
- Privileged reviewer dashboard and API role checks.
- Same-origin mutation enforcement.
- Fail-closed KV rate limiting.
- Identity-bound appeal idempotency keys.
- Exact-sanction selector endpoint and UI boundary.
- Versioned/idempotent reviewer decisions with stale-update handling.
- Hosted Node 22 site validation workflow and focused regression tests.

## 5. Standalone repository evidence
- Repository: `wsg138/enthusia-site`
- Starting main: `9408166c75def0b55caa8d38fb546c6e77ea1f7d`
- Baseline PR: `wsg138/enthusia-site#1`
- Baseline reviewed head: `cce9cff6243ee757db9d470eb7d8d7735c8c3495`
- Baseline squash merge: `042b503b7a4adc2627f2259a09e7d7394ced06ce`
- Hardening branch: `package/es-x05-appeal-hardening`
- Hardening PR: `wsg138/enthusia-site#2`
- Hardening head at publication: `1b82971a1ebb7d48c96f832fb7aaaa9b0c106480`
- Hosted validation run: `31105287800` (queued at publication start)

## 6. Acceptance evidence obtained
- Unauthenticated requests fail closed.
- Linked identity is required and immutable.
- Browser UUID/name fields cannot override the authenticated identity.
- Reviewer access requires an explicitly configured privileged role.
- Mutation requests require exact same-origin.
- Rate limiting fails closed when its binding is absent.
- Review decisions require a bounded idempotency key and expected version.
- Focused baseline regression suite passed 5/5 locally before PR #1 merge.

## 7. Remaining completion work
- Wait for and resolve exact-head PR #2 hosted validation/review.
- Reconcile the site adapter with the real EnthusiaStaff website contract. Current core routes include punishment-code claim/revalidation and exact appeal acceptance; the placeholder storage/list service contract is not yet proven end to end.
- Import the standalone site into `components/enthusia-site/` through the canonical component-sync process.
- Produce standalone/aggregate content-hash parity evidence.
- Open, review, validate, and merge the required EnthusiaStaff aggregate PR.
- Update registry, workspace state, metadata, handoff, merge hashes, and delete temporary branches after verified merges.
- Private production credentials/routes and representative live acceptance remain excluded or deferred as documented.

## 8. Current blocker / resume state
The connected GitHub environment can edit repositories but has no local authenticated checkout or network access for the required component import/hash tooling. The aggregate component is still `NOT_IMPORTED`. Resume ES-X05 from site PR #2 and perform the aggregate import/parity work before selecting another package.

## 9. Security/privacy boundary
No production credentials or player data were committed. All unconfigured authentication, rate, upstream-service, or role boundaries fail closed. No Access token is logged.

## 10. Handoff
[`2026-08-06-es-x05-website-auth-appeals.md`](../../reports/package-handoffs/2026-08-06-es-x05-website-auth-appeals.md)

## 11. Last update
2026-08-06
