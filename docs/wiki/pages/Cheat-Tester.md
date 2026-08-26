# Cheat Tester

Cheat Tester is a merged, evidence-only staff tool for short bounded anti-cheat probes. It does **not** issue punishments, change moderation authority, or automatically decide that a player is cheating.

For overall staff-tool status and source navigation, use [[Staff Tools, Investigations, and Player-State Safety]]. For review rules, use [[Code Review Guide]].

## Quick controls

Cheat Tester is limited to authorized advanced staff sessions and the relevant permission nodes.

```text
/cheattester config
/cheattester select <totem-refill|no-fall|velocity|auto-armor|fake-entity>
/cheattester run <player> [type]
/cheattester cancel <player>
/cheattester status
/cheattester base create <player>
/cheattester base extend <player>
/cheattester base clear <player>
/cheattester base teleport <player>
/cheattester base status
```

The staff-mode blaze rod provides a Java interaction surface: right-click cycles the tester, left-clicking a target runs it, and shift-right-click shows configuration/status. The command forms are the supported text/Bedrock fallback.

A new test requires the target to be online on the current backend and cannot target the controlling staff member. Cross-controller cancellation or fake-base management requires the additional configured authority.

## Tester types

### Totem refill

Uses a totem already present in the target's normal inventory; the tester does not create one. The offhand is temporarily cleared and the probe observes whether a totem appears there. The exact owned pre-test state is restored afterward.

### Auto-armor

Temporarily moves one existing equipped armor piece into a valid empty normal slot and observes whether the armor slot is equipped again. It does not create replacement armor and must restore the exact owned pre-test state.

### Velocity / anti-knockback

Applies bounded configured velocity while temporarily protecting the target from damage, records displacement evidence, and restores owned movement/invulnerability state.

### No-fall

Runs a short bounded vertical movement probe, samples fall-distance behavior and records suspicious reset observations. It must restore the owned movement/inventory state after the probe.

### Fake entity

Uses the optional ProtocolLib adapter to show a nonpersistent synthetic entity only to the target and controlling staff member. Evidence may include interaction/attack counts, first-interaction latency and bounded aim-angle observations.

ProtocolLib is optional for the rest of EnthusiaStaff. If the adapter is missing, incompatible or unhealthy, **fake-entity testing fails closed** while unrelated tester types may remain available.

## Fake bases

Fake bases are bounded client-side visual probes. They do not place, break or replace real world blocks.

Before rendering, the runtime requires safe conditions such as:

- target online on the current backend;
- current chunk already loaded; no chunk generation/loading for the feature;
- template inside one chunk and valid build height;
- virtual cells correspond to real air;
- safe supporting floor/interior assumptions;
- no conflicting fake-base operation for the target;
- bounded global/per-controller concurrency;
- durable audit availability before rendering.

Only the target and authorized staff viewers that successfully join the view see the virtual structure. Other players do not.

Fake bases have a bounded lifetime and are cleared on expiry, excessive target movement, world/backend changes, disconnects, controller staff-mode exit/disconnect, or lifecycle shutdown. Extension starts a new bounded lifetime rather than making the probe permanent.

### Why fake-base cleanup cannot damage the world

The server world is never changed. Cleanup reads authoritative real block data and sends those states back to still-connected viewers. Duplicate cleanup is therefore idempotent with respect to world state.

If a viewer/session disappears before a restore packet can be sent, the virtual client blocks cannot persist in saved world data and disappear with that client session. Normal disable/reload still attempts explicit cleanup and logs scheduler/session retirement rather than claiming a restore it did not send.

The fake-base operation is intentionally in memory. Persisting a virtual-block session across process restart would imply continuity that does not exist after the client session is gone. Coordinate-free lifecycle events are instead written to the existing audit ledger.

## Durable tester recovery

State-changing testers journal recovery state using:

```text
V18__cheat_tester_session_journal.sql
```

The repository's **overall** current Flyway history is through **V19**. V18 is still the migration that owns Cheat Tester session-journal state; it is not the current global migration ceiling.

The durable row is written before temporary mutation. Only one globally active tester may own the same target, and active tester state participates in the inventory-lock/recovery contract so offline inventory work cannot race an unresolved tester restore.

Normal completion, staff cancellation, timeout, disconnect/reconnect, death/respawn, reload interruption and runtime restart converge on the same recovery model. A row is not terminal until restoration/cleanup is verified. If restoration cannot be proved, the active recovery state blocks a new tester for that target.

During a state-changing probe, common actions that could consume or move test assets are cancelled, including relevant drops, pickups, item use, swaps and inventory access. Probes are intentionally short and bounded.

## Configuration

`staff-tools.cheat-tester` is restart-owned configuration. `/estaff reload` must reject a candidate that attempts to change it as though a live runtime resource were rebuilt.

Representative configuration includes bounded timeout/sampling/concurrency, fake-entity distance and velocity/no-fall parameters. Runtime validation rejects unsafe or nonsensical ranges.

Fake-base limits are fixed release safety limits rather than an invitation to create arbitrarily large or long-lived visual structures.

## Evidence interpretation

A tester observation is investigation evidence, not a verdict. Staff must consider latency, player input, client platform, server conditions, other evidence and known false-positive modes.

Do not punish automatically because one tester looks suspicious. Use the ordinary case/evidence/punishment workflow after human review.

## Privacy

The durable tester state contains only the bounded identifiers/configuration/evidence and recovery snapshot needed for the operation. It must not become a place to store raw network addresses, unrelated chat/private messages, credentials or unrelated player evidence.

Fake-base audit events deliberately avoid storing base coordinates. Do not add coordinates to staff chat, Discord or public output merely because a virtual probe was used.

## Developer source map

Start with:

- `paper/src/main/java/net/enthusia/staff/paper/cheattester/`
- `paper/src/main/java/net/enthusia/staff/paper/command/CheatTesterCommand.java`
- `domain/src/main/java/net/enthusia/staff/domain/cheattester/`
- `persistence/src/main/java/net/enthusia/staff/persistence/JdbcCheatTesterSessionStore.java`
- `persistence/src/main/resources/db/migration/V18__cheat_tester_session_journal.sql`
- focused Paper/domain/persistence tests for tester state and fake-base behavior

Reviewers should follow the mutation from durable intent to scheduler-owned side effect to verification/terminal state. Also inspect ProtocolLib present/missing/failure behavior, cleanup idempotency, stale callbacks, player disconnect/reconnect, plugin disable and Bedrock/text fallback.

## Validation boundary

Automated unit/MariaDB tests can prove deterministic policy, schema and recovery transitions they actually exercise. They do not by themselves prove real Paper/Folia ownership, ProtocolLib compatibility, Java/Bedrock presentation, multi-backend disconnect behavior, or private staging acceptance.

Representative distributed Java/Bedrock/runtime acceptance remains a separate evidence layer. See [[Build and Testing]].

## Go deeper

- [[Staff Mode, Vanish, and Freeze|Staff-Mode-Vanish-and-Freeze]] — staff-mode hotbar and procedure.
- [[Staff Tools, Investigations, and Player-State Safety]] — status and source ownership.
- [[Inventory and Confiscation Safety]] — overlapping player-state recovery concerns.
- [[Recovery and Troubleshooting]] — what to do if cleanup/restoration is uncertain.
- [[Code Review Guide]] — scheduler, persistence, privacy and validation review.
- [[Build and Testing]] — evidence classes and staging boundaries.