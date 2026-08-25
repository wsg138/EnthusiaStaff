# ES-D05 staff bot runtime foundation — active handoff

Date: 2026-08-24
Status: `ACTIVE`
Package: `ES-D05 — Staff bot runtime foundation`

## Starting state

- Staff `main` at claim: `168145d76efb13ed15f21f8a31ece3e96f7b7c7b`.
- Implementation branch: `package/es-d05-staff-bot-runtime`.
- Implementation PR: #160.
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

Fresh dependency review also found that JDA 6.5.0 publishes `jackson-databind:2.22.0`, which is affected by CVE-2026-59889; the fixed 2.22.x release is 2.22.1. D05 therefore pins both `jackson-core` and `jackson-databind` to `2.22.1` and makes exact resolved versions part of `verifyStaffBotRuntime`. JDA 6.5.0 is also beyond the 6.1.3 fix for GHSA-93fv-4pm9-xp28.

## Implemented package surface

- isolated Java 21 `staff-bot` process/module and executable shaded artifact;
- explicit staging/production environment selection, token redaction, actual Discord application-ID validation after connect;
- minimum privileged Gateway intent (`GUILD_MEMBERS`), no Message Content, no member cache/chunking;
- fail-closed Enthusia-only guild membership and staging test-channel visibility/send-permission checks;
- JDA-owned REST rate limiting and auto-reconnect with 60-second maximum reconnect delay;
- bounded application worker queue with saturation metrics and daemon fallback after graceful shutdown;
- bounded read-only interaction replay/idempotency primitive, with durable idempotency explicitly reserved for destructive future work;
- loopback liveness/readiness HTTP surface with single-snapshot state responses;
- privacy-safe lifecycle logs containing environment/state/reason categories but no token/message/user/evidence data;
- deterministic graceful JDA shutdown with forced fallback and terminal failure semantics;
- deterministic unit tests for config, identity/guild/channel fencing, lifecycle/reconnect, health, bounded work, replay TTL/saturation and shutdown escalation;
- shaded-jar integrity verification wired into repository `check`, including full ZIP read-through, executable manifest/entry point, JDA presence, no-audio class exclusions, patched Jackson resolution, size and SHA-256 evidence;
- non-destructive `--smoke-test` mode that waits for the complete staging identity/guild/channel readiness fence and sends no messages.

## Validation state

Implementation is under exact-head hosted validation on PR #160. A superseded head `18207fa...` exposed and recorded one Gradle verifier bug (`ZipFile.testzip()` was a Python API name); the current implementation replaces it with a Java read-through of every ZIP entry rather than weakening the integrity gate. Superseded failures are not passes and are not final evidence.

Final required evidence remains exact-head Java 21 build/tests, static/review checks, shaded runtime artifact integrity, dependency review, and non-destructive staging connect/disconnect plus app/guild/test-channel fencing because the staging application exists. If no authorized runtime path can access the staging token, do not request or expose it; publish that as an external blocker after all other work is complete.

## Systems not to disturb

- D04 PR #151 and its branch;
- X03/X04 provider branches/PRs;
- website and competition work;
- existing webhook Discord delivery implementation;
- Flyway migrations;
- production Discord configuration/data;
- LiteBans authority and issue #43 acceptance.

## Exact next action

Finish exact-head hosted validation and full-diff/review reconciliation, then obtain the required non-destructive staging bot smoke evidence through an authorized secret-bearing path or publish a precise external blocker if no such path exists.
