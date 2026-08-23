# ES-D02 — Discord persistence and migration schema

Status: `ACTIVE`. Priority: 131. Depends on `ES-D01` (`COMPLETE`). Internal package.

Starting `main`: `1a8b70755fa48f780c228c47943b8afaef746f36`.
Branch: `package/es-d02-discord-persistence`.
Live migration ceiling at claim: `V18__cheat_tester_session_journal.sql`; D02 reserves V19 subject to a final pre-write `main` check.

## Objective
Persist the D01 moderation-subject/Discord identity model in the existing authoritative MariaDB without creating a parallel moderation database.

## Scope
Forward-only migrations and repositories for moderation subjects and platform identities, Discord↔Minecraft current/history links, main-account selection/override state, explicit enforcement targets/scopes, Discord evidence metadata/retention state, account-security-lock state, Discord/native reconciliation state, and durable expiry/retention work required by later packages. Backfill existing UUID-centric records safely rather than destructively reinterpreting history.

## Required behavior
Existing migrations are immutable. Keys/FKs/uniqueness/indexes must enforce one current Discord owner per Minecraft UUID while allowing one Discord user to own several UUID links. Repository operations must be bounded, transactional, revision-safe and restart-safe. Clean-install and upgrade paths must both work.

## Exclusions
No JDA/Gateway runtime, commands/UI, DiscordSRV import, punishment side effects, AutoMod, website UI, public bot, production data migration or cutover.

## Validation
Focused repository/domain tests plus MariaDB/Testcontainers clean-install, upgrade and checksum validation; full Java 21 build/coverage/runtime-JAR/static/review gates. Prove transactional rollback/idempotency and relevant indexes. Zero valid review threads.

## Collision and authority state
Live reconciliation at claim found only parked PR #139 (`ES-X03`) and no D02 branch/PR. The visible website transition branch has no commits ahead of current `main`; website and competition paths remain excluded. Issue #43 remains open and does not authorize production-like migration/cutover. LiteBans remains authoritative.

## Completion
Normal merge to `main`, containment/cleanup, terminal handoff, and mark `ES-D03` eligible. Preserve website/competition paths unless a migration contract is strictly shared and conflict-free.

## Current checkpoint
Package claimed from exact `main`; implementation and validation are in progress. No production data, Discord configuration, deployment, cutover, or authority change has occurred.
