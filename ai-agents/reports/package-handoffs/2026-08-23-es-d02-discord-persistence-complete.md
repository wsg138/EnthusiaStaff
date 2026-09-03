# ES-D02 Discord persistence — complete handoff

Status: `COMPLETE`.

## Scope result

ES-D02 persists the D01 moderation-subject/Discord identity contract in the existing authoritative EnthusiaStaff MariaDB. It adds forward-only V19 schema and transactional JDBC persistence for subjects/platform identities, Discord↔Minecraft current and historical links, durable main-account selection state, explicit enforcement targets/scopes, bounded Discord evidence-retention metadata, account-security locks, reconciliation state, and restart-safe maintenance work. Existing UUID players are safely backfilled rather than destructively reinterpreted.

No Discord bot/API runtime, DiscordSRV import, punishment side effect, AutoMod, website UI, competition path, public bot, production data/configuration, deployment, LiteBans authority change, or issue #43 cutover is part of this package.

## Revisions

- Starting `main`: `1a8b70755fa48f780c228c47943b8afaef746f36`.
- Branch: `package/es-d02-discord-persistence`.
- PR: #148.
- Frozen product head: `9f0d9bb44ab6929bec3bf652e0c9b3467104b423`.
- Migration: `V19__discord_moderation_persistence.sql`.
- Terminal state publication follows the frozen product head and changes only `ai-agents` Markdown state/handoff records.

## Final harsh-review repairs

The first green candidate was not accepted blindly. Full-diff review found and corrected two persistence-safety defects before freezing the product head:

1. operation-key replay could return success for a different logical request. The final implementation validates replay ownership/request identity and fails closed on collisions, including link/unlink, enforcement target, evidence, and security-lock operations;
2. linking a Minecraft-only subject into an existing Discord subject could fail when the source already owned D02 evidence/enforcement rows because the old subject was deleted under restrictive foreign keys. The final link transaction validates that the source is a standalone Minecraft identity, transfers every D02-owned subject reference to the canonical Discord subject, moves the identity, then removes the empty source subject.

`DiscordPersistenceSafetyIntegrationTest` covers the merge-with-history path and operation-key collision paths. The existing V19 integration suite continues to cover clean install, V18→V19 upgrade, backfill/cardinality, rollback/race/idempotency, retention/maintenance leases, security locks and reconciliation.

## Exact-product-head evidence

Frozen product head `9f0d9bb44ab6929bec3bf652e0c9b3467104b423`:

- Coverage workflow `32661521865`, job `97248320623`: PASS.
- Workflow explicitly checked out the exact frozen SHA and used Temurin Java 21.0.12.
- Gradle command: `clean build jacocoAggregateReport runtimeJars --no-daemon --no-build-cache --no-configuration-cache --console=plain`; `BUILD SUCCESSFUL` in 6m52s; 49 actionable tasks (40 executed, 9 up-to-date).
- MariaDB/Testcontainers integration tests completed successfully within the build.
- Runtime-JAR inspection checked 24 provider API source types with zero leaks.
- Paper runtime: 9,300,835 bytes; SHA-256 `15db902699e6fafbcb9c42e8a4ec011ffecdb176adf2e7dbab4834bbd9a6b4d5`; 4,926 entries.
- Velocity runtime: 8,041,767 bytes; SHA-256 `7d66a7827136b44087d5e4d167589269d858050c03e659a349cf871bf39f5700`; 4,237 entries.
- Aggregate JaCoCo: 50.25% line, 40.76% branch, 52.67% instruction. These are recorded repository measurements, not a claim that a broader future coverage target is complete.
- Validation artifact `9498959764`, digest `sha256:c80a26841485a3a27dcf6f6f670767a48eb6f7dbe42d55c4858b2c335cbfc152`.
- Codacy coverage report upload and final notification: PASS on the exact frozen SHA.
- Sentinel Restart Artifact workflow `32661521891`, job `97248320518`: PASS on the exact frozen SHA.
- Sentinel artifact `9498878851`, digest `sha256:6a0ab81859325b881f3f134677d0cdd95ac9505ea335bc973372732ba471c86b`.
- CodeRabbit exact-product-head status: success.
- Live PR #148 inline review threads immediately before terminal publication: 0.

## Final live reconciliation

Immediately before terminal publication:

- `main` was still `1a8b70755fa48f780c228c47943b8afaef746f36`;
- D02 was seven commits ahead and zero behind;
- `main` still ended at `V18__cheat_tester_session_journal.sql`, so V19 remained collision-free;
- the only other open Staff PR was independently parked ES-X03 PR #139;
- D02's full changed-file set contained no website or competition path.

No production secrets/private data were introduced. LiteBans remains authoritative and issue #43 remains separately gated.

## Routing after completion

This handoff and the terminal registry/package/workspace records are included in PR #148 and become canonical through its normal merge. The D02 worker then verifies feature-head containment/no unique work and deletes the temporary branch. `ES-D03 — Authorization and cross-platform policy` is newly dependency-complete and marked `READY`, but it is not started in this run.
