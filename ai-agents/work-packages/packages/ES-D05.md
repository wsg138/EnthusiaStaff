# ES-D05 — Staff bot runtime foundation

Status: `MERGE_PENDING`. Priority: 134. Depends on `ES-D01`–`ES-D03` (all `COMPLETE`). Internal package.

## Claim and current reconciliation

Selected by the owner-authorized Discord-program worker on 2026-08-24 and carried through implementation, review, hosted validation, and the required live Discord acceptance on PR #160.

- Starting `main`: `168145d76efb13ed15f21f8a31ece3e96f7b7c7b`.
- Implementation branch: `package/es-d05-staff-bot-runtime`.
- Implementation PR: #160.
- Frozen validated D05 product head: `5f24ba1818c81e0a30a516fa70c8597586184b00`.
- Current-main reconciliation merge: `9c99e78f520cd59e7e59506c37573ac9ad028d63`, merging canonical `main` `37a2073b535cf32f89b2fc075699dca4e3420408` with normal two-parent merge history.
- Canonical `main` Flyway boundary is V19. D05 adds no migration and does not consume D04's unmerged V20.
- `ES-D04` remains separate on Staff PR #151 and was not modified or absorbed.
- Production Discord changes, production deployment/data access, LiteBans cutover, and issue #43 acceptance remain unauthorized.

Current acceptance handoff: `ai-agents/reports/package-handoffs/2026-08-26-es-d05-discord-acceptance.md`.
Historical blocked handoff: `ai-agents/reports/package-handoffs/2026-08-24-es-d05-staff-bot-runtime.md`.

## Runtime identities

These identifiers are public/non-secret configuration:

- production application ID: `1541279426233376818`
- staging application ID: `1541279616881397772`
- Enthusia guild ID: `1410303324745371709`
- staff bot testing channel ID: `1541286004298752091`

D05 deliberately requests no privileged Gateway intents because this foundation layer does not consume member, presence, or message-content event streams. Bot tokens remain secret and must never be committed, logged, copied into tests, requested in chat, exposed in artifacts, or placed on command lines.

## Objective and scope

Add the staff Discord bot as a third first-class Java 21 process isolated from Paper and Velocity, with no destructive moderation commands yet. The implemented scope includes JDA 6.5.0, staging/production identity fencing, Enthusia-only guild locking, staging test-channel view/send fencing, Gateway reconnect/backoff and REST rate-limit handling, bounded executors/queues, secret injection, health/readiness, privacy-safe lifecycle logging, graceful shutdown, bounded read-only interaction replay protection, shaded runtime packaging/integrity verification, and the non-destructive `--smoke-test`. Existing webhook delivery remains separate.

Exclusions remain punishment side effects, rich moderation UX, AutoMod enforcement, public installable bot behavior, production deployment/configuration, DiscordSRV role-sync replacement, token storage, and production cutover.

## Implementation checklist

- [x] Add isolated Java 21 `staff-bot` runtime module and executable shaded artifact.
- [x] Implement explicit staging/production configuration with secret redaction and exact application identity fencing.
- [x] Implement minimal Gateway intents, reconnect/backoff, Enthusia-only guild fence, and staging test-channel fence.
- [x] Add bounded application executor and bounded interaction replay/idempotency primitive.
- [x] Add local health/readiness endpoint and privacy-safe structured lifecycle logging.
- [x] Implement deterministic graceful shutdown and restart-safe lifecycle behavior.
- [x] Add unit/lifecycle/reconnect/rate-limit/shutdown/config/guild-fence/idempotency tests.
- [x] Update runtime packaging/integrity checks and operator documentation without changing the webhook subsystem.
- [x] Perform harsh full-diff review and resolve all valid review/static/CI findings.
- [x] Obtain exact-head hosted validation for the frozen product head.
- [x] Obtain the required non-destructive staging Discord connection acceptance with exact application/guild/test-channel evidence.
- [ ] Complete fresh applicable exact-head validation after current-main executable reconciliation.
- [ ] Merge normally, verify containment/cleanup, publish `COMPLETE`, and stop without starting D06.

## Frozen product evidence

Frozen D05 product head: `5f24ba1818c81e0a30a516fa70c8597586184b00`.

- Coverage/full Java 21 validation run `32874248685`, job `97888464396`: `SUCCESS`; full build/tests, MariaDB/Testcontainers where applicable, aggregate JaCoCo, runtime JAR tasks, and `:staff-bot:verifyStaffBotRuntime` passed.
- Aggregate JaCoCo: 50.76% lines, 41.41% branches, 53.21% instructions.
- Validation artifact `9573547679`; digest `sha256:c6f2df467085d811593c7100feb5a4c698a46e14432e92d401662dff9d43455c`.
- Staff Bot Configuration Cache run `32874248800`, job `97888275507`: `SUCCESS`; `:staff-bot:check` passed twice with configuration-cache problems treated as failures.
- Sentinel Restart Artifact run `32874248693`: `SUCCESS`.
- Codacy: zero new issues; 63.04% diff coverage; +0.17% coverage variation.
- CodeRabbit: `SUCCESS` after all valid runtime/build findings were corrected; no valid unresolved functional/security/lifecycle thread remains.
- Canonical Pi public `32879118794` and correlated private `32880103099` / job `97907230239`: `SUCCESS` on trusted `Lincoln-PI-4` for the repository Paper/Pi gate.

## Required live Discord acceptance — PASS

The previously external acceptance blocker is cleared.

Trusted staging workflow `wsg138/EnthusiaStaff-Staging` run `32926306691`, latest attempt 3 / job `98071453002`, completed successfully on trusted self-hosted runner `Lincoln-PI-4` (`Linux/ARM64`). Its trusted workflow revision `03b3fce61bffe552d7905a4e4aa18e3015ea4e00` pins the fetched product source to exact D05 SHA `5f24ba1818c81e0a30a516fa70c8597586184b00`, sets up Java 21, builds/verifies the exact staff-bot runtime before secret scope, and executes the existing non-destructive `--smoke-test`.

Sanitized acceptance evidence proves:

- staging application `1541279616881397772`: `PASS`;
- Enthusia guild `1410303324745371709`: `PASS`;
- required test channel `1541286004298752091`: `PASS` for view/send fence;
- readiness fence: `PASS`;
- smoke process exit: `0`;
- graceful close/shutdown path: `PASS`.

The workflow sends no moderation action or test message and performs no Discord configuration change or production-data access. No bot-token value was inspected, requested, committed, logged, copied into tracking text, or placed on a command line.

The live acceptance is attributed only to the exact frozen D05 product source it executed. It is not relabeled as a Discord smoke on the later aggregate reconciliation head.

## Current-main synchronization and validation rule

PR #160 was reconciled with canonical `main` `37a2073b535cf32f89b2fc075699dca4e3420408` using ordinary two-parent merge commit `9c99e78f520cd59e7e59506c37573ac9ad028d63`. The exact resulting diff against that main head is D05-only: `staff-bot` source/tests/build integration, the staff-bot configuration-cache workflow, and runtime documentation. D04/X03/website/Wiki/provider work remains independent.

Although the D05 staff-bot product surface itself remains the reviewed frozen implementation, current `main` gained unrelated executable repository changes after the original freeze. Therefore the synchronized PR head must receive fresh applicable exact-head hosted validation before normal merge. Any later state-only tracking commit may reuse the synchronized executable evidence only under the explicit frozen-product/state-only exception in `VALIDATION-POLICY.md`.

## Merge gate

Merge #160 normally only after the synchronized exact head has all applicable configured/package gates terminal and green, all valid review findings resolved, and live `main` still reconciled safely. Then verify exact containment/cleanup, publish ES-D05 `COMPLETE` durably, and stop. Do not begin D06 in this run.
