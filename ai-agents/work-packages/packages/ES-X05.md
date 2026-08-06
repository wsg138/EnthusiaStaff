# `ES-X05` — Website UX, authentication, and appeals

## 1. Package identity

`ES-X05`; external/multi-repository; primary `COMP-SITE`; other `COMP-STAFF`; priority `35`; dependency `ES-P01`.

## 2. Status

`COMPLETE` upon normal merge of finalization PR #74.

Completion evidence includes **OWNER-APPROVED INFRASTRUCTURE EXCEPTION — STAGING DEFERRED** to `ES-V02`; the unavailable staging gate is not a pass.

Assigned worker: `ChatGPT sequential ES-X05 completion worker`.

## 3. Objective and completed behavior

Completed website authentication, exact-punishment appeal UX, reviewer controls, privacy, rate limiting, retries, the private EnthusiaStaff appeal contract, production site deployment, durable MariaDB appeal persistence, and verified standalone/aggregate synchronization.

The standalone site verifies Cloudflare Access JWT signature/issuer/audience/time claims, derives Minecraft identity only from verified claims, enforces same-origin mutations, and sends only allowlisted fixed-origin bearer-plus-HMAC requests with bounded bodies and timeouts. The aggregate implements matching signed routes, persistent nonce replay controls, service-boundary reviewer authorization, durable exact-punishment eligibility/submission/review, atomic rate limiting, scoped idempotency, optimistic revisions, audit events, and exact-sanction approval delegation. V17 is the only new migration; V1–V16 remain unchanged.

## 4. Standalone evidence

- Repository: `wsg138/enthusia-site`.
- Final reviewed head: `1a45b32e372cf6939c078a0d7986655e7ed639d6`.
- Hosted validation run `31113188453`: success.
- Production and market-preview Cloudflare deployments: success.
- Codacy: success with zero annotations; zero unresolved review threads.
- PR #2 normal merge/current standalone `main`: `b385f78c522f452cc48d78ed19fd2ee82573f64d`.
- Containment: temporary standalone branch has zero unique commits or files.

## 5. Aggregate evidence

- Starting aggregate main: `515bd9a8591505c043b413f5b9ecb3e272c6d6f2`.
- Aggregate implementation branch: `package/es-x05-state-publication`.
- Frozen product head: `96912301fc425ac6f5eff9349ee3b3d543d122eb`.
- Exact hosted-validation/review head: `4c818bb3aea953d3f877efc8a48a9175ba219d38`.
- Coverage run `31116854096`, job `92668751419`: success for Java 21 clean build, all unit and MariaDB/Testcontainers integration/migration tests, JaCoCo, runtime-JAR/provider-leak checks, artifacts, and Codacy coverage.
- JaCoCo: lines `47.50%`, branches `38.47%`, instructions `50.16%`.
- CodeRabbit passed; all eight valid review threads were resolved.
- PR #73 normal implementation merge/current aggregate `main`: `345b7bbcec6facb45c7f96b0e6e181ac7a38e1da`.
- Implementation containment passed.
- Finalization branch/PR: `package/es-x05-finalization`; PR #74. The exact finalization head and final hosted-check evidence are recorded in PR #74 to avoid a self-referential tracked-file loop.

## 6. Owner-approved deferred staging

Evidence label: **OWNER-APPROVED INFRASTRUCTURE EXCEPTION — STAGING DEFERRED**.

- Owner: repository owner `wsg138`.
- Approval source/date: explicit instruction in the current ChatGPT project conversation on 2026-08-06 to skip the current ES-X05 live test, continue development, and perform a larger combined PySentinel test later.
- Named deferred package: `ES-V02 — Distributed and Java/Bedrock staging`.
- Aggregate parent dispatcher: run `31116852061`, job `92668521113`, exact source head `4c818bb3aea953d3f877efc8a48a9175ba219d38`.
- Private staging run: `wsg138/EnthusiaStaff-Staging` run `31116860919`.
- Unavailable build job: `92668551209`; runner ID `0`; runner name empty; executed steps `[]`.
- Downstream Pi job: `92668600472`; skipped.
- No private checkout, build, artifact validation, product test, Paper boot, restart, migration, or other product-validation step executed. This is infrastructure-unavailable evidence, not a product result.
- Later attempts `31117490156`, `31117820548`, `31117820542`, `31119701541`, and `31119699199` also stopped during allocation/action setup before product execution; none is labeled a pass.

`ES-V02` must later test the pinned combined implementation set through PySentinel or equivalent isolated tooling, including ordinary exact-head build/artifact provenance, clean Paper boot and restart, V17 and later migration paths, config mutation/reload/restart behavior, appeal/auth API and persistence, failure/recovery behavior, provider interaction, representative Java/Bedrock scenarios, safely implemented automated client behavior, and complete cleanup. Any discovered product defect becomes a separately assigned repair package.

This exception does not authorize production credentials, production Minecraft accounts, player data, routes, authority activation, or issue #43. Automated account-token handling requires separate security review and owner authorization.

## 7. Component synchronization

- Standalone source: `b385f78c522f452cc48d78ed19fd2ee82573f64d`.
- Aggregate implementation merge: `345b7bbcec6facb45c7f96b0e6e181ac7a38e1da`.
- Canonical component hash: `9910dc90d22be68bf034f03def0cabd617bdf2e9953f87231f11af1166fc07e2`.
- Added, missing, and modified path sets are empty; parity is true.
- Evidence: [`2026-08-06-es-x05-component-parity.json`](../../reports/package-handoffs/2026-08-06-es-x05-component-parity.json).

## 8. Authority and privacy boundary

LiteBans remains authoritative. Issue #43, production cutover, production credentials/data, Flyway repair/history rewriting, and authority activation remain excluded. Authentication, reviewer rank, origin, replay, rate-limit, timeout, request-size, and upstream-service configuration fail closed.

## 9. Canonical handoff

[`2026-08-06-es-x05-website-auth-appeals.md`](../../reports/agent-handoffs/2026-08-06-es-x05-website-auth-appeals.md)

## 10. Last update

2026-08-06
