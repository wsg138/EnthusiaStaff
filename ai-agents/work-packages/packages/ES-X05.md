# `ES-X05` — Website UX, authentication, and appeals

## 1. Package identity

`ES-X05`; external/multi-repository; primary `COMP-SITE`; other `COMP-STAFF`; priority `35`; dependency `ES-P01`.

## 2. Status

`COMPLETE`.

The owner-directed outage-recovery pass selected this package only after proving ES-P02 remained parked on its unchanged private Billing & plans blocker and ordinary public GitHub-hosted Ubuntu execution had recovered.

## 3. Completed implementation

The package provides Cloudflare Access claim verification, canonical linked Minecraft identity, direct-but-unlinked appeal/reviewer pages, exact-punishment appeal selection/submission, privileged reviewer decisions, same-origin browser mutations, fixed-origin bearer-plus-HMAC private Staff API requests, bounded/time-limited upstream access, MariaDB-backed appeal persistence, atomic rate limiting, scoped replay/idempotency protection, optimistic revisions, audit events, and exact-sanction approval delegation. V17 is the only package migration; V1–V16 remain unchanged.

## 4. Standalone evidence and live defect repair

- Repository: `wsg138/enthusia-site`.
- ES-X05 PR #2 reviewed head `1a45b32e372cf6939c078a0d7986655e7ed639d6`; hosted validation `31113188453` success; production and preview Cloudflare deployments success; Codacy success; zero unresolved review threads; normal merge `b385f78c522f452cc48d78ed19fd2ee82573f64d`.
- Live reconciliation found follow-up PR #3, `Allow unlinked appeal pages to load publicly`, exact head `db8d4dc6836729b0558eaa2926f8bf4f362b8eaf`, normal merge/current standalone `main` `2fe7d59c1c5e12db0b7ba792fc9e2af4d24337c2`.
- PR #3 fixes a reproducible ES-X05 routing defect by removing page-level `functions/_middleware.js`, which redirected intended public-but-unlinked appeal/reviewer pages when Access/login configuration was absent. Authentication and reviewer-role authorization remain inside the protected `/api/appeals*` and `/api/reviewer/appeals*` handlers.
- PR #3 exact head passed site test workflow run `31118849099` / job `92674874313`, `Cloudflare Pages: enthusia-site` check `92675068365`, `Cloudflare Pages: enthusia-market-preview` check `92674953189`, and Codacy check `92675034770`; zero review threads were present.

## 5. Aggregate recovery and exact-head validation

- Aggregate implementation branch `package/es-x05-state-publication`; PR #73; normal merge `345b7bbcec6facb45c7f96b0e6e181ac7a38e1da`.
- Exact aggregate implementation hosted-validation/review product head `4c818bb3aea953d3f877efc8a48a9175ba219d38`; Coverage `31116854096` / `92668751419` passed Java 21 build/tests, MariaDB/Testcontainers migrations, coverage, runtime-JAR/provider-leak checks, artifacts, and Codacy coverage.
- Recovery started from aggregate `main` `9b1aac2677049ccc71dbddd963831f270c73dcd0` and finalization head `96bf9ab21b114a4523582a5ca267e6c1d1370cb1`.
- Current `main` was reconciled through normal merge commit `e9644c14e743f686758ee619ab347cbebe1b21ec`, preserving ES-P03 and current ES-P02 records. The standalone PR #3 deletion was mirrored under `components/enthusia-site/`.
- Frozen exact finalization head: `ab59b8357b8e2eb146b60ff122e316112906746f`.
- Coverage run `31140188918`, job `92748299782`: success on Ubuntu 24.04 / Temurin Java `21.0.11+10`; `./gradlew clean build jacocoAggregateReport runtimeJars` succeeded with unit and MariaDB/Testcontainers integration/migration tests, coverage, runtime-JAR integrity/provider-leak checks, artifacts, and Codacy upload.
- Runtime inspection checked 24 provider API source types with 0 leaks. Paper JAR SHA-256 `9880457c88f445de6f813f9bbee15544b59abc344d65420a6ae100d4ef5ab9d4`; Velocity JAR SHA-256 `74e0105a94c7f10fc371fe033f07ab46588a01c18bfeea832af3179e72f986d6`. Validation artifact `8979625925`, archive SHA-256 `42c9f835001de4847cd26961dbbe185a671b0239511d872a9553efeba44680f4`.
- CodeRabbit exact-head status succeeded; PR #74 has zero valid unresolved review threads. Codacy static `92748599134`, coverage variation `92749330468`, and diff coverage `92749330613` all succeeded.
- Wiki validation was not applicable because the finalization diff changed no wiki paths or workflow.

## 6. Merge, containment, and parity

- Finalization PR #74 merged normally as `2bcf5d46ca6471fddac600f85020c66105b1c0f2` with frozen head unchanged.
- Compare from frozen head `ab59b8357b8e2eb146b60ff122e316112906746f` to merge `2bcf5d46ca6471fddac600f85020c66105b1c0f2` reports no changed files, proving full containment and no unique branch work.
- Deterministic post-merge component parity used `tools/component-sync/component_sync.py` against exact aggregate merge `2bcf5d46ca6471fddac600f85020c66105b1c0f2` and standalone `main` `2fe7d59c1c5e12db0b7ba792fc9e2af4d24337c2`.
- Successful parity run `31140896890`, job `92750376952`, artifact `8979748083`: aggregate hash = standalone hash = `780269847698d37c470cb7c241539b1c7387014225cc7eee9598548c9dc97f8b`; added `[]`; missing `[]`; modified `[]`; parity `true`.
- Earlier harness-only attempts `31140685623` / `92749749317` and `31140785772` / `92750046294` failed respectively on an unrelated shallow-history precheck and an incorrect wrapper JSON key. The second already printed `parity: true`; the corrected third run passed every step and uploaded evidence.

## 7. Owner-approved deferred staging

Evidence label: **OWNER-APPROVED INFRASTRUCTURE EXCEPTION — STAGING DEFERRED**.

- Owner: repository owner `wsg138`; approval source/date: explicit project-conversation instruction on 2026-08-06.
- Named deferred package: `ES-V02 — Distributed and Java/Bedrock staging`.
- Original dispatcher `31116852061` / `92668521113`; private run `31116860919`; build `92668551209` had runner ID `0`, empty runner name and steps `[]`; Pi job `92668600472` skipped. No private checkout/build/test/artifact/Paper boot/restart/migration executed.
- No manual private-staging retry was requested during recovery. Repository PR automation automatically dispatched wrapper `31140187754` / job `92748257022`, which dispatched private run `31140197043` for frozen source `ab59b8357b8e2eb146b60ff122e316112906746f`. Private build `92748287250` again had runner ID `0`, empty runner name, steps `[]`, and the same Billing & plans payment/spending-limit annotation; Pi `92748295072` skipped.
- These results are unavailable-infrastructure evidence only. Private/Pi staging remains deferred to ES-V02 and is not called passed.

## 8. Authority/privacy boundary

LiteBans remains authoritative. Issue #43, production cutover, production credentials/accounts/data/routes, production/private player data, Flyway repair/history rewriting, ES-V02 execution, and authority activation remain excluded.

## 9. Canonical handoff

[`2026-08-06-es-x05-website-auth-appeals.md`](../../reports/agent-handoffs/2026-08-06-es-x05-website-auth-appeals.md)

## 10. Completion / next routing

ES-X05 is complete after hosted exact-head validation, normal merge, zero unresolved findings, containment, and deterministic external-component parity. This worker must stop after publishing this same-package completion record. `ES-P04` and `ES-P09` become dependency-ready because ES-P03 is complete; neither is started here. Normal sequential routing should prefer `ES-P04` at priority 40 absent a newly actionable continuation.