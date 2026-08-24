# ES-D05 — Staff bot runtime foundation

Status: `READY`. Priority: 134. Depends on `ES-D01`–`ES-D03` (all `COMPLETE`). Internal package.

## Readiness
ES-D03 completion satisfies this package's remaining internal dependency. A future worker must still perform fresh live GitHub collision reconciliation and a fresh implementation-time Discord library/security review before claiming runtime work.

## Runtime identities
The owner has created both private Discord applications and has already added the staging bot to the Enthusia guild. These identifiers are public/non-secret configuration and may be committed:

- production application ID: `1541279426233376818`
- staging application ID: `1541279616881397772`
- Enthusia guild ID: `1410303324745371709`

Server Members Intent is enabled for the staging application. Bot tokens remain secret and must never be committed, logged, copied into tests, or requested in chat. D05 should use explicit environment/config selection so staging and production identities cannot be confused at runtime.

## Objective
Add the staff Discord bot as a third first-class Java 21 process isolated from Paper and Velocity, with no destructive moderation commands yet.

## Scope
Fresh implementation-time Discord library review (JDA is the current preferred candidate unless evidence changes); module/build/runtime packaging; Gateway lifecycle/reconnect/backoff; minimal privileged intents; guild lock to Enthusia guild `1410303324745371709`; REST/rate-limit handling; bounded executors/queues; secret/config injection; staging/prod separation; health/readiness; structured privacy-safe logging; graceful shutdown; interaction idempotency primitives. Existing webhook delivery remains separate.

## Exclusions
No punishment side effects, rich moderation UX, AutoMod enforcement, public installable bot, token in Git or production deployment.

## Validation
Unit/lifecycle/reconnect/rate-limit/shutdown tests, runtime artifact integrity, dependency/security review and exact-head CI. Where a staging bot/guild is available, prove connect/disconnect without destructive actions; otherwise record genuine non-applicability rather than fake success.
