# ES-D05 staff bot runtime foundation — active handoff

Date: 2026-08-24
Status: `ACTIVE`
Package: `ES-D05 — Staff bot runtime foundation`

## Starting state

- Staff `main` at claim: `168145d76efb13ed15f21f8a31ece3e96f7b7c7b`.
- Implementation branch: `package/es-d05-staff-bot-runtime`.
- Implementation PR: pending first coherent implementation checkpoint.
- D01–D03: `COMPLETE`.
- D04: separate existing PR #151/branch; live head observed as `7eab5572b476419b125c6262c72b434f44ef1ef1` while another worker was committing. D05 must not touch it.
- D05 owner identity contract PR #153: merged normally as `168145d76efb13ed15f21f8a31ece3e96f7b7c7b` before claim.
- No pre-existing D05 package handoff was found.
- No live branch matching D05, website, or competition work was found during collision preflight.
- Canonical migration boundary on `main`: V19. D05 adds no migration.
- Issue #43: open; no production cutover authority.

## Runtime identity contract

- production application ID: `1541279426233376818`
- staging application ID: `1541279616881397772`
- Enthusia guild ID: `1410303324745371709`
- staging staff-bot testing channel ID: `1541286004298752091`
- staging Server Members Intent: owner records it enabled
- bot tokens: secret; never commit, log, paste into chat, or place in test fixtures

## Library/security review

Official JDA `6.5.0` is the current signed release and is selected for D05. It is actively maintained, provides Discord REST rate-limit handling and incremental Gateway reconnect/backoff, and exposes explicit shutdown/await-shutdown APIs. D05 has no voice/audio scope, so `opus-java` and `tink` are excluded per JDA's own supported no-audio dependency guidance. No package work depends on private JDA internals or reflection.

## Intended package implementation

- isolated Java 21 `staff-bot` process/module and executable shaded artifact;
- explicit staging/production config selected from environment, with token redaction and actual Discord application-ID validation after connect;
- only the minimum currently required Gateway intent (`GUILD_MEMBERS`) plus non-privileged baseline events; no message-content intent for D05;
- fail-closed Enthusia guild-only membership and staging test-channel ownership checks;
- JDA-owned REST rate limiting and auto-reconnect with bounded maximum backoff;
- bounded application work queue and bounded interaction replay/idempotency primitive for later command packages;
- loopback health/readiness HTTP surface without private moderation data;
- privacy-safe lifecycle logs containing environment/state/reason categories but no tokens, message content, private evidence, or user data;
- deterministic graceful shutdown and executor/resource cleanup;
- deterministic unit tests using abstractions/fakes rather than a real Discord token;
- non-destructive staging connectivity validation only through an authorized secret-bearing path if one exists.

## Validation state

Not started for implementation head. PR #153's prior documentation checks are historical contract evidence only and do not validate D05 product code.

Final required evidence will include exact-head Java 21 build/tests, static/review checks, shaded runtime artifact integrity, dependency review, and non-destructive staging connect/disconnect plus app/guild/test-channel fencing because the staging application exists. If no authorized runtime path can access the staging token, do not request or expose it; publish that as an external blocker after all other work is complete.

## Systems not to disturb

- D04 PR #151 and its branch;
- X03/X04 provider branches/PRs;
- website and competition work;
- existing webhook Discord delivery implementation;
- Flyway migrations;
- production Discord configuration/data;
- LiteBans authority and issue #43 acceptance.

## Exact next action

Create the D05 runtime module and deterministic lifecycle/config/identity/idempotency/health tests, then open the package PR and continue through full review and validation.
