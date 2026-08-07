# `ES-X05` — Website UX, authentication, and appeals

## 1. Package identity

`ES-X05`; external/multi-repository; primary `COMP-SITE`; other `COMP-STAFF`; priority `35`; dependency `ES-P01`.

## 2. Status

`MERGE_PENDING` — `ACTIONABLE_CONTINUATION`.

Assigned worker: `ChatGPT outage-recovery and finalization worker`.

The former ordinary public-hosted-runner blocker materially changed: ES-P02 exact-head Coverage run `31138550369` successfully executed on GitHub-hosted Ubuntu. ES-P02 itself remains parked because its private staging repository is still blocked by Billing & plans, so ES-X05 is the highest-priority actionable continuation permitted by the owner recovery instruction.

## 3. Completed implementation

The package provides Cloudflare Access claim verification, canonical linked Minecraft identity, direct-but-unlinked appeal/reviewer pages, exact-punishment appeal selection/submission, privileged reviewer decisions, same-origin browser mutations, fixed-origin bearer-plus-HMAC private Staff API requests, bounded/time-limited upstream access, MariaDB-backed appeal persistence, atomic rate limiting, scoped replay/idempotency protection, optimistic revisions, audit events, and exact-sanction approval delegation. V17 is the only package migration; V1–V16 remain unchanged.

## 4. Standalone evidence and live follow-up

- Repository: `wsg138/enthusia-site`.
- ES-X05 PR #2 reviewed head `1a45b32e372cf6939c078a0d7986655e7ed639d6`; hosted validation `31113188453` success; production and preview Cloudflare deployments success; Codacy success; zero unresolved review threads; normal merge `b385f78c522f452cc48d78ed19fd2ee82573f64d`.
- Live reconciliation found follow-up PR #3, `Allow unlinked appeal pages to load publicly`, exact head `db8d4dc6836729b0558eaa2926f8bf4f362b8eaf`, normal merge/current standalone `main` `2fe7d59c1c5e12db0b7ba792fc9e2af4d24337c2`.
- PR #3 fixes a reproducible ES-X05 routing defect by removing page-level `functions/_middleware.js`, which redirected intended public-but-unlinked appeal/reviewer pages when Access/login configuration was absent. Authentication and reviewer-role authorization remain inside the protected `/api/appeals*` and `/api/reviewer/appeals*` handlers.
- PR #3 exact head passed site test workflow run `31118849099` / job `92674874313`, `Cloudflare Pages: enthusia-site` check `92675068365`, `Cloudflare Pages: enthusia-market-preview` check `92674953189`, and Codacy check `92675034770`; zero review threads were present.

## 5. Aggregate evidence

- Aggregate implementation branch `package/es-x05-state-publication`; PR #73; normal merge `345b7bbcec6facb45c7f96b0e6e181ac7a38e1da`.
- Exact aggregate hosted-validation/review product head `4c818bb3aea953d3f877efc8a48a9175ba219d38`.
- Coverage run `31116854096`, job `92668751419`: Java 21 clean build, unit and MariaDB/Testcontainers integration/migration tests, JaCoCo, runtime-JAR/provider-leak checks, artifacts, and Codacy coverage succeeded.
- CodeRabbit passed and all valid implementation review threads were resolved.
- Finalization branch/PR: `package/es-x05-finalization`; PR #74.
- Recovery starting `main`: `9b1aac2677049ccc71dbddd963831f270c73dcd0`; starting finalization head `96bf9ab21b114a4523582a5ca267e6c1d1370cb1`.
- Current `main` is merged normally into the finalization branch, preserving completed ES-P03 state and current ES-P02 routing facts.
- The standalone PR #3 one-file deletion is mirrored at `components/enthusia-site/functions/_middleware.js`; no other standalone product delta exists since `b385f78c...`.

## 6. Owner-approved deferred staging

Evidence label: **OWNER-APPROVED INFRASTRUCTURE EXCEPTION — STAGING DEFERRED**.

- Owner: repository owner `wsg138`; approval source/date: explicit project-conversation instruction on 2026-08-06.
- Named deferred package: `ES-V02 — Distributed and Java/Bedrock staging`.
- Original dispatcher `31116852061` / `92668521113`; private run `31116860919`; build `92668551209` had runner ID `0`, empty runner name and steps `[]`; Pi job `92668600472` skipped.
- No private checkout, build, product test, artifact validation, Paper boot, restart, migration, or other product-validation step executed. This is unavailable infrastructure evidence, not a staging pass.
- This recovery worker preserves that disposition exactly and does not rerun or reinterpret the private Pi gate.

## 7. Finalization gates

Freeze one reconciled PR #74 head and require every applicable current repository gate on that exact head: ordinary hosted Coverage/build/test/migration/coverage/runtime-JAR/provider-leak/artifact checks, static analysis, Wiki/Markdown/package validation where configured/applicable, review with zero valid unresolved threads, and deterministic component parity against standalone `main` `2fe7d59c1c5e12db0b7ba792fc9e2af4d24337c2`.

If all pass, merge PR #74 normally, verify exact-head containment and no unique work, publish `COMPLETE`, update dependency-derived routing without starting another package, clean contained temporary work where supported, and stop.

## 8. Authority/privacy boundary

LiteBans remains authoritative. Issue #43, production cutover, production credentials/accounts/data/routes, production/private player data, Flyway repair/history rewriting, ES-V02 execution, and authority activation remain excluded.

## 9. Canonical handoff

[`2026-08-06-es-x05-website-auth-appeals.md`](../../reports/agent-handoffs/2026-08-06-es-x05-website-auth-appeals.md)

## 10. Last update

2026-08-06