# ES-D05 — Staff bot runtime foundation

Status: `ACTIVE`. Priority: 134. Depends on `ES-D01`–`ES-D03` (all `COMPLETE`). Internal package.

## Claim and live reconciliation

Selected by the owner-authorized Discord-program worker on 2026-08-24 after live reconciliation.

- Starting `main`: `168145d76efb13ed15f21f8a31ece3e96f7b7c7b`.
- Implementation branch: `package/es-d05-staff-bot-runtime`.
- Implementation PR: pending first coherent checkpoint.
- `ES-D04` was not resumed because its existing PR #151/branch was receiving concurrent commits from a separate worker during this selection; this worker does not touch or synchronize D04.
- No live D05, website, or competition branch collision was found before claim.
- Canonical `main` Flyway boundary is V19. D05 adds no migration.
- Fresh library review selected official JDA `6.5.0`, the current signed release, with audio dependencies excluded because D05 has no voice scope. JDA owns Discord REST rate-limit handling and incremental Gateway reconnect/backoff; D05 adds bounded application work and fail-closed runtime identity/guild checks around it.
- No earlier D05 package handoff existed at claim time.

Canonical handoff: `ai-agents/reports/package-handoffs/2026-08-24-es-d05-staff-bot-runtime.md`.

## Runtime identities
The owner has created both private Discord applications and has already added the staging bot to the Enthusia guild. These identifiers are public/non-secret configuration and may be committed:

- production application ID: `1541279426233376818`
- staging application ID: `1541279616881397772`
- Enthusia guild ID: `1410303324745371709`
- staff bot testing channel ID: `1541286004298752091`

Server Members Intent is enabled for the staging application. The testing channel is currently visible only to Admins and Founders, which is sufficient for D05 connectivity/runtime validation. D06 may deliberately widen access when Helper/Mod/Developer authorization-matrix testing begins; do not treat current channel visibility as the final staff-command authorization policy.

Bot tokens remain secret and must never be committed, logged, copied into tests, or requested in chat. D05 uses explicit environment/config selection so staging and production identities cannot be confused at runtime.

## Objective
Add the staff Discord bot as a third first-class Java 21 process isolated from Paper and Velocity, with no destructive moderation commands yet.

## Scope
Fresh implementation-time Discord library review; module/build/runtime packaging; Gateway lifecycle/reconnect/backoff; minimal privileged intents; guild lock to Enthusia guild `1410303324745371709`; staging test-channel targeting to `1541286004298752091`; REST/rate-limit handling; bounded executors/queues; secret/config injection; staging/prod separation; health/readiness; structured privacy-safe logging; graceful shutdown; interaction idempotency primitives. Existing webhook delivery remains separate.

## Exclusions
No punishment side effects, rich moderation UX, AutoMod enforcement, public installable bot, token in Git or production deployment.

## Implementation checklist

- [ ] Add isolated Java 21 `staff-bot` runtime module and executable shaded artifact.
- [ ] Implement explicit staging/production configuration with secret redaction and exact application identity fencing.
- [ ] Implement minimal Gateway intents, reconnect/backoff, Enthusia-only guild fence, and staging test-channel fence.
- [ ] Add bounded application executor and bounded interaction replay/idempotency primitive.
- [ ] Add local health/readiness endpoint and privacy-safe structured lifecycle logging.
- [ ] Implement deterministic graceful shutdown and restart-safe lifecycle behavior.
- [ ] Add unit/lifecycle/reconnect/rate-limit/shutdown/config/guild-fence/idempotency tests.
- [ ] Update runtime packaging/integrity checks and operator documentation without changing the webhook subsystem.
- [ ] Perform harsh full-diff review and resolve all valid review/static/CI findings.
- [ ] Obtain exact-head hosted validation and, because the staging bot exists, non-destructive staging connectivity/guild/test-channel evidence if the required secret/runtime path is available to the worker.
- [ ] Merge normally, verify containment/cleanup, publish terminal state, and stop without starting D06.

## Validation
Unit/lifecycle/reconnect/rate-limit/shutdown tests, runtime artifact integrity, dependency/security review and exact-head CI. Because the staging bot is available, final package acceptance requires connect/disconnect and guild-locked test-channel operation without destructive actions. Missing staging credentials are not to be copied into Git or chat; if no authorized validation path can access them after all safe work is complete, that is an external blocker rather than a reason to weaken the gate.
