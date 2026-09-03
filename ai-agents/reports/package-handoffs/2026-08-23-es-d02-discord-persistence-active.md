# ES-D02 Discord persistence — active handoff

Status: `ACTIVE`.

## Claim

- Package: `ES-D02 — Discord persistence and migration schema`.
- Starting `main`: `1a8b70755fa48f780c228c47943b8afaef746f36`.
- Branch: `package/es-d02-discord-persistence`.
- Dependency: merged `ES-D01` (`COMPLETE`).
- Flyway ceiling at claim: V18; D02 reserves V19 subject to live collision recheck.

## Live reconciliation

The only open Staff PR at claim is parked ES-X03 PR #139. There is no existing ES-D02 branch or PR. The visible website transition branch is behind `main` with zero commits ahead. D02 will not edit website, competition, provider-mirror, production/staging-secret, or LiteBans cutover paths.

## Intended bounded implementation

Persist the D01 moderation-subject and platform-identity foundation in authoritative MariaDB; preserve legacy UUID rows with a safe backfill; enforce current Discord↔Minecraft cardinality with database keys; persist main-account selection state, enforcement target/scope intents, Discord evidence retention metadata, security locks, reconciliation state, and bounded restart-safe maintenance work. Add transactional/revision-safe JDBC access and focused MariaDB/Testcontainers clean-install/upgrade tests.

## Boundaries

No Discord API call, bot runtime, DiscordSRV import, punishment side effect, AutoMod enforcement, website UI, public bot, production data access/migration, deployment, cutover, or authority change is part of D02. Issue #43 remains open and LiteBans remains authoritative.

## Resume

Continue implementation on the named branch, open one draft PR after the first coherent product checkpoint, then carry exact-head validation/review through normal merge or publish a genuine external blocker.
