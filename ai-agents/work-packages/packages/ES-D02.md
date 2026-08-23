# ES-D02 — Discord persistence and migration schema

Status: `READY`. Priority: 131. Depends on `ES-D01` (`COMPLETE`). Internal package.

## Objective
Persist the D01 moderation-subject/Discord identity model in the existing authoritative MariaDB without creating a parallel moderation database.

## Scope
Forward-only migrations and repositories for moderation subjects and platform identities, Discord↔Minecraft current/history links, main-account selection/override state, explicit enforcement targets/scopes, Discord evidence metadata/retention state, account-security-lock state, Discord/native reconciliation state, and durable expiry/retention work required by later packages. Backfill existing UUID-centric records safely rather than destructively reinterpreting history.

## Required behavior
Fresh-check the live highest Flyway migration before choosing a number. Existing migrations are immutable. Define keys/FKs/uniqueness/indexes that enforce one current Discord owner per Minecraft UUID while allowing one Discord user to own several UUID links. Repository operations must be bounded, transactional, revision-safe and restart-safe. Clean-install and upgrade paths must both work.

## Exclusions
No JDA/Gateway runtime, commands/UI, DiscordSRV import, punishment side effects, AutoMod, website UI, public bot, production data migration or cutover.

## Validation
Focused repository/domain tests plus MariaDB/Testcontainers clean-install, upgrade and checksum validation; full Java 21 build/coverage/runtime-JAR/static/review gates. Prove rollback/idempotency and relevant indexes. Zero valid review threads.

## Completion
Normal merge to `main`, containment/cleanup, terminal handoff, and mark `ES-D03` eligible. Preserve website/competition paths unless a migration contract is strictly shared and conflict-free.
