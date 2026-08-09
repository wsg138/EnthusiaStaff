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
- journal submission and cancellation are coordinated so cancellation before submission creates no row, cancellation during a database write terminalizes the committed row before any probe mutation, and cancellation after commit follows the durable finish path;
- state-changing testers use the existing inventory external lock while live;
- globally ACTIVE tester rows also participate in the durable inventory lock contract after disconnect/restart;
- the V18 unique active-target fence prevents the same target from being tested concurrently on another backend;
- exact restoration is required before a state-changing durable row becomes terminal;
- timeout, staff cancellation, disconnect/reconnect, death/respawn, runtime interruption, and restart converge on the same recovery row;
- temporary target mutation guards block inventory/container/item interactions that could corrupt exact restoration while a state-changing probe is active;
- unsafe configuration values are rejected; tester configuration is restart-only and `/estaff reload` rejects changes that are not actually applied.

Runtime responsibilities are intentionally split across `CheatTesterManager`, `CheatTesterSession`, `CheatTesterProbeEngine`, `CheatTesterEvidence`, `CheatTesterSnapshotCodec`, and `CheatTesterMutationGuard` so lifecycle, evidence, packet mechanics, state restoration, and event fencing remain reviewable.

Direct evidence:

- `domain/src/main/java/net/enthusia/staff/domain/tester/`
- `domain/src/main/java/net/enthusia/staff/domain/ports/CheatTesterJournalStore.java`
- `persistence/src/main/resources/db/migration/V18__cheat_tester_session_journal.sql`
- `persistence/src/main/java/net/enthusia/staff/persistence/JdbcCheatTesterJournalStore.java`
- `persistence/src/main/java/net/enthusia/staff/persistence/CompositeInventoryTesterJournalStore.java`
- `paper/src/main/java/net/enthusia/staff/paper/tester/CheatTesterManager.java`
- `paper/src/main/java/net/enthusia/staff/paper/tester/CheatTesterSession.java`
- `paper/src/main/java/net/enthusia/staff/paper/tester/CheatTesterProbeEngine.java`
- `paper/src/main/java/net/enthusia/staff/paper/tester/CheatTesterEvidence.java`
- `paper/src/main/java/net/enthusia/staff/paper/tester/CheatTesterMutationGuard.java`
- `paper/src/main/java/net/enthusia/staff/paper/tester/CheatTesterCommand.java`
- `paper/src/main/java/net/enthusia/staff/paper/tester/CheatTesterSnapshotCodec.java`
- `paper/src/main/java/net/enthusia/staff/paper/staff/StaffToolDispatcher.java`
- `paper/src/main/resources/plugin.yml`
- `paper/src/main/resources/config.yml`

Direct tests include:

- `domain/src/test/java/net/enthusia/staff/domain/tester/CheatTesterTypeTest.java`
- `paper/src/test/java/net/enthusia/staff/paper/tester/CheatTesterSettingsTest.java`
- `paper/src/test/java/net/enthusia/staff/paper/tester/CheatTesterSessionTest.java`
- `paper/src/test/java/net/enthusia/staff/paper/tester/CheatTesterEvidenceTest.java`
- `paper/src/test/java/net/enthusia/staff/paper/tester/CheatTesterCommandTest.java`
- `paper/src/test/java/net/enthusia/staff/paper/tester/FakeEntityAdapterTest.java`
- `paper/src/test/java/net/enthusia/staff/paper/config/PaperConfigurationLoaderTest.java`
- `paper/src/test/java/net/enthusia/staff/paper/staff/StaffToolDefinitionTest.java`
- `integration-tests/src/test/java/net/enthusia/staff/integration/CheatTesterJournalIntegrationTest.java`

The MariaDB integration test covers global active-target fencing across backend IDs, restart persistence, evidence revision checkpointing, audit emission, durable inventory-lock participation, terminal release, and successful V18 migration. The session test directly covers cancellation before, during, and after durable journal submission.

## AUD-TESTER-002 — client-side fake entity

Implemented behavior:

- packet behavior is isolated behind `FakeEntityAdapter`;
- ProtocolLib is optional and the adapter fails closed if unavailable/unhealthy;
- no direct NMS dependency is introduced;
- a synthetic entity ID/UUID is journaled and the entity is spawned only for the target and controlling staff viewer;
- `USE_ENTITY` packets for the managed synthetic entity are captured and cancelled;
- only the target's interactions contribute suspect evidence; controlling-staff observer interactions are deliberately excluded from counts;
- evidence records target interactions, attacks, first-interaction latency, and the minimum sampled aim angle toward the entity;
- cleanup uses `ENTITY_DESTROY` for each viewer on that viewer's entity scheduler;
- an unverifiable packet-provider failure does not falsely mark the row complete; durable recovery remains ACTIVE until cleanup can be verified by a healthy runtime.

Direct evidence:

- `paper/src/main/java/net/enthusia/staff/paper/tester/FakeEntityAdapter.java`
- `paper/src/main/java/net/enthusia/staff/paper/tester/ProtocolLibFakeEntityAdapter.java`
- `paper/src/main/java/net/enthusia/staff/paper/tester/CheatTesterProbeEngine.java`
- `paper/src/main/java/net/enthusia/staff/paper/tester/CheatTesterManager.java`
- `paper/src/test/java/net/enthusia/staff/paper/tester/FakeEntityAdapterTest.java`
- `paper/src/test/java/net/enthusia/staff/paper/tester/CheatTesterEvidenceTest.java`

## Operational boundary

All tester results are evidence for human staff review. No observation automatically bans, mutes, kicks, warns, freezes, or creates a punishment request.

The target-state snapshot is bounded recovery material, not a general evidence capture surface. V18 does not add raw addresses, chat contents, private messages, or unrelated sensitive evidence.

Representative distributed Java/Bedrock behavior is intentionally not claimed here. That acceptance remains assigned to `ES-V02` under the canonical package plan. Private Pi/distributed staging that receives no runner is recorded as unavailable infrastructure evidence and is never described as a pass.
