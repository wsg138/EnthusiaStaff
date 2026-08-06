# EnthusiaStaff workspace state

Last updated: 2026-08-06

Live GitHub state overrides stale records, but persistent package state must be published to `main`.

## Current routing

| Field | Value |
| --- | --- |
| Completed prerequisite | `ES-P01 — Exact-sanction appeal isolation` |
| Parked package | `ES-P02 — Runtime database recovery and Velocity reload` |
| ES-P02 classification | `PARKED_BLOCKED` while its runner/authorization condition is unchanged |
| Preserved ES-P02 work | branch `package/es-p02-runtime-db-recovery`, open PR #70 |
| Active package | `ES-X05 — Website UX, authentication, and appeals` |
| ES-X05 status | `PARTIAL` |
| ES-X05 classification | `ACTIONABLE_CONTINUATION` |
| Standalone continuation | `wsg138/enthusia-site#2`, branch `package/es-x05-appeal-hardening` |
| Exact standalone head | `11e68b60ef874a01f8b6f04f72bd8d694c496b56` |
| Exact validation | run `31105809682` — success |
| Deferred validation | `ES-V02 — Distributed and Java/Bedrock staging` |

## ES-X05 evidence
- Baseline standalone PR #1 merged as `042b503b7a4adc2627f2259a09e7d7394ced06ce`.
- Continuation PR #2 adds same-origin controls, fail-closed rate limiting, idempotency, exact-sanction selection, versioned reviewer decisions, and regression coverage.
- Nested potions in shulker boxes/bundles are normalized to exact namespaced potion IDs and vanilla tint colors before rendering; live updates are serialized.
- GitHub-hosted validation, tests, and source build pass at the exact head.
- `enthusia-market-preview` deploys the exact head successfully.

## Current blockers
1. The production Cloudflare `enthusia-site` project fails before exposing a source annotation. This failure predates ES-X05 and occurs on untouched prior main `9408166c75def0b55caa8d38fb546c6e77ea1f7d`; the same commits deploy to `enthusia-market-preview`. Cloudflare dashboard logs/settings access is required.
2. The real private EnthusiaStaff appeal service contract is not yet proven end to end.
3. `components/enthusia-site/` remains `IMPORT_PENDING`; aggregate content-hash parity and the required aggregate PR are outstanding.

## Required next-worker behavior
1. Inspect ES-P02 PR #70 only to confirm its external unblock condition remains unchanged; otherwise leave it untouched.
2. Resume ES-X05 PR #2 before selecting any other package.
3. Correct the production Cloudflare project settings and obtain an exact-head deployment pass.
4. Verify the real private appeal contract.
5. Merge the standalone continuation normally, import the exact merged site into the aggregate component, prove parity, open/validate/merge the aggregate PR, publish final hashes, and clean temporary branches.
6. Mark ES-X05 `COMPLETE` only after all standalone, production, contract, aggregate, and parity requirements are satisfied.

## Safety boundaries
- No production credentials or player records are committed.
- Authentication, origin, reviewer role, rate-limit, and upstream-service configuration fail closed.
- ES-P02 PR #70 and its preserved branch must not be modified merely for drift while the external blocker is unchanged.
