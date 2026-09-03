# ES-D02 — Discord persistence and migration schema

Status: `COMPLETE`. Priority: 131. Depends on `ES-D01` (`COMPLETE`). Internal package.

Starting `main`: `1a8b70755fa48f780c228c47943b8afaef746f36`.
Branch: `package/es-d02-discord-persistence`.
PR: #148.
Frozen product head: `9f0d9bb44ab6929bec3bf652e0c9b3467104b423`.
Migration: `V19__discord_moderation_persistence.sql`, reserved after a final live check confirmed `main` still ended at V18.

## Objective
Persist the D01 moderation-subject/Discord identity model in the existing authoritative MariaDB without creating a parallel moderation database.

## Completed scope
D02 adds the forward-only V19 schema and transactional JDBC persistence for moderation subjects and platform identities, Discord↔Minecraft current/history links, main-account selection state, explicit enforcement targets/scopes, Discord evidence metadata/retention state, account-security locks, reconciliation state, and bounded durable maintenance work. Existing UUID players are backfilled into compatible subject/main-account state without rewriting historical moderation authority.

The schema enforces one current Discord owner per Minecraft UUID while allowing one Discord user to own several Minecraft links. Repository mutations use operation keys, optimistic revisions, transactions, bounded claims and restart-safe persisted state.

## Harsh-review repairs
The final product head fixes two defects found after the first green candidate:

- idempotency replays now fail closed when an operation key is reused for a different logical request instead of silently replaying unrelated state;
- verified linking now atomically merges a standalone Minecraft-only subject into an existing Discord subject while carrying D02-owned enforcement, evidence, security-lock and link-history references to the canonical subject instead of failing on foreign keys or detaching history.

Focused MariaDB integration coverage proves both behaviors.

## Exact-product-head validation
Frozen product head `9f0d9bb44ab6929bec3bf652e0c9b3467104b423` passed:

- Coverage workflow `32661521865`, job `97248320623`: exact SHA checkout, Temurin Java 21.0.12, `clean build jacocoAggregateReport runtimeJars`, including MariaDB/Testcontainers clean-install/upgrade and persistence integration tests; `BUILD SUCCESSFUL` in 6m52s.
- Aggregate JaCoCo: 50.25% lines, 40.76% branches, 52.67% instructions.
- Runtime-JAR inspection: 24 provider API source types checked, zero leaks. Paper SHA-256 `15db902699e6fafbcb9c42e8a4ec011ffecdb176adf2e7dbab4834bbd9a6b4d5`; Velocity SHA-256 `7d66a7827136b44087d5e4d167589269d858050c03e659a349cf871bf39f5700`.
- Validation artifact `9498959764`, digest `sha256:c80a26841485a3a27dcf6f6f670767a48eb6f7dbe42d55c4858b2c335cbfc152`.
- Codacy coverage upload and final notification: success in the same exact-head workflow.
- Sentinel Restart Artifact workflow `32661521891`, job `97248320518`: success; artifact `9498878851`, digest `sha256:6a0ab81859325b881f3f134677d0cdd95ac9505ea335bc973372732ba471c86b`.
- CodeRabbit exact-product-head status: success.
- Live PR #148 review threads before terminal publication: 0.

The repository-wide coverage percentages are recorded measurements, not a relabeling of any broader future coverage target.

## Final collision and authority state
Immediately before terminal publication, `main` remained `1a8b70755fa48f780c228c47943b8afaef746f36`; D02 was seven commits ahead and zero behind. The live migration ceiling remained `V18__cheat_tester_session_journal.sql`, so V19 had no collision. The only other open Staff PR was independently parked ES-X03 PR #139. No website or competition path is changed by D02.

No Discord API/runtime, DiscordSRV import, punishment side effect, AutoMod, website UI, public bot, production data/configuration, deployment, LiteBans authority change, or issue #43 cutover occurred. LiteBans remains authoritative.

## Completion
This terminal record is part of PR #148 and becomes canonical through that PR's normal merge. After merge, the worker verifies feature-head containment/no unique work and deletes the temporary branch. `ES-D03` becomes `READY`; this D02 worker does not start it.
