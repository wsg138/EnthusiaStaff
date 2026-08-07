# ES-P10 — Cheat Tester and Fake-Entity Implementation Evidence

Package: `ES-P10`

Requirements owned: `AUD-TESTER-001`, `AUD-TESTER-002`

Excluded: `AUD-TESTER-003` fake bases (`ES-P11`), automatic punishment, production cutover/deployment, issue #43 acceptance, LiteBans authority changes, representative distributed Java/Bedrock acceptance (`ES-V02`).

## AUD-TESTER-001 — authorized tester workflow

Implemented surfaces:

- authenticated staff-mode item at the fixed Cheat Tester slot;
- right-click cycles the selected tester;
- shift-right-click shows configuration/status;
- left-click on a player cancels damage and runs the selected tester;
- `/cheattester` text fallback for configuration, selection, run, cancellation, and status;
- `enthusiastaff.cheattester` permission with advanced-rank availability and `enthusiastaff.cheattester.cancel-any` for administrative cancellation;
- release tester types: Totem refill, No-fall, Velocity/anti-knockback, Auto-armor;
- global/per-staff concurrency limits and same-target duplicate rejection;
- evidence-only completion messages and durable audit events; no sanction/punishment mutation path is called.

Safety implementation:

- the target snapshot is captured before temporary state mutation;
- V18 creates a durable `cheat_tester_sessions` recovery journal before mutation;
- state-changing testers use the existing inventory external lock while live;
- globally ACTIVE tester rows also participate in the durable inventory lock contract after disconnect/restart;
- the V18 unique active-target fence prevents the same target from being tested concurrently on another backend;
- exact restoration is required before the durable row becomes terminal;
- timeout, staff cancellation, disconnect/reconnect, death/respawn, runtime interruption, and restart converge on the same recovery row;
- unsafe configuration values are rejected; tester configuration is restart-only and `/estaff reload` rejects changes that are not actually applied.

Direct evidence:

- `domain/src/main/java/net/enthusia/staff/domain/tester/`
- `domain/src/main/java/net/enthusia/staff/domain/ports/CheatTesterJournalStore.java`
- `persistence/src/main/resources/db/migration/V18__cheat_tester_session_journal.sql`
- `persistence/src/main/java/net/enthusia/staff/persistence/JdbcCheatTesterJournalStore.java`
- `persistence/src/main/java/net/enthusia/staff/persistence/CompositeInventoryTesterJournalStore.java`
- `paper/src/main/java/net/enthusia/staff/paper/tester/CheatTesterManager.java`
- `paper/src/main/java/net/enthusia/staff/paper/tester/CheatTesterCommand.java`
- `paper/src/main/java/net/enthusia/staff/paper/tester/CheatTesterSnapshotCodec.java`
- `paper/src/main/java/net/enthusia/staff/paper/staff/StaffToolDispatcher.java`
- `paper/src/main/resources/plugin.yml`
- `paper/src/main/resources/config.yml`

Tests:

- `domain/src/test/java/net/enthusia/staff/domain/tester/CheatTesterTypeTest.java`
- `paper/src/test/java/net/enthusia/staff/paper/tester/CheatTesterSettingsTest.java`
- `paper/src/test/java/net/enthusia/staff/paper/config/PaperConfigurationLoaderTest.java`
- `paper/src/test/java/net/enthusia/staff/paper/staff/StaffToolDefinitionTest.java`
- `integration-tests/src/test/java/net/enthusia/staff/integration/CheatTesterJournalIntegrationTest.java`

The MariaDB integration test covers global active-target fencing across backend IDs, restart persistence, evidence revision checkpointing, audit emission, durable inventory-lock participation, terminal release, and successful V18 migration.

## AUD-TESTER-002 — client-side fake entity

Implemented behavior:

- packet behavior is isolated behind `FakeEntityAdapter`;
- ProtocolLib is optional and the adapter fails closed if unavailable/unhealthy;
- no direct NMS dependency is introduced;
- a synthetic entity ID/UUID is journaled and the entity is spawned only for the target and controlling staff viewer;
- `USE_ENTITY` packets for the managed synthetic entity are captured and cancelled;
- evidence records interactions, attacks, first-interaction latency, and the minimum sampled aim angle toward the entity;
- cleanup uses `ENTITY_DESTROY` for each viewer on that viewer's entity scheduler;
- an unverifiable packet-provider failure does not falsely mark the row complete; durable recovery remains ACTIVE until cleanup can be verified by a healthy runtime.

Direct evidence:

- `paper/src/main/java/net/enthusia/staff/paper/tester/FakeEntityAdapter.java`
- `paper/src/main/java/net/enthusia/staff/paper/tester/ProtocolLibFakeEntityAdapter.java`
- `paper/src/main/java/net/enthusia/staff/paper/tester/CheatTesterManager.java`
- `paper/src/test/java/net/enthusia/staff/paper/tester/FakeEntityAdapterTest.java`

## Operational boundary

All tester results are evidence for human staff review. No observation automatically bans, mutes, kicks, warns, freezes, or creates a punishment request.

The target-state snapshot is bounded recovery material, not a general evidence capture surface. V18 does not add raw addresses, chat contents, private messages, or unrelated sensitive evidence.

Representative distributed Java/Bedrock behavior is intentionally not claimed here. That acceptance remains assigned to `ES-V02` under the canonical package plan.
