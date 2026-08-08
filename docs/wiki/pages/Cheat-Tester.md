# Cheat Tester

The Cheat Tester is an evidence-only staff tool for short, bounded anti-cheat probes. It does **not** issue punishments, change LiteBans authority, or make an automatic cheating decision.

## Access and controls

Cheat Tester is available to authenticated advanced staff sessions (Mod, Developer, Admin, and Founder) with `enthusiastaff.cheattester`.

The staff-mode blaze rod uses these controls:

- **Right-click:** cycle the selected tester.
- **Left-click a player:** run the selected tester against that player. The damage event is cancelled.
- **Shift-right-click:** show the current tester configuration and status shortcut.

The text fallback is the supported Bedrock-safe control surface:

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

`enthusiastaff.cheattester.cancel-any` allows an authorized staff member to cancel another staff member's active tester. The target must be online on the current backend when a new test starts, and staff cannot target themselves.

Fake-base controls additionally require active staff mode plus `enthusiastaff.cheattester.fake-base`. By default, only the staff member who created a fake base can extend, clear, or teleport to it. `enthusiastaff.cheattester.fake-base.manage-any` allows Admin/Founder-level cross-controller management.

## Release tester types

### Totem refill

Uses a totem already present in the target's normal inventory; the tester does not create one. The target's offhand is temporarily cleared and the evidence records whether a totem appears there during the short probe. Exact pre-test inventory state is restored afterward.

### Auto-armor

Requires one equipped armor piece and one empty normal inventory slot. One existing equipped piece is temporarily moved into that empty slot and the evidence records whether the corresponding armor slot becomes equipped again. Exact pre-test inventory state is restored afterward.

### Velocity / anti-knockback

Applies a bounded configured velocity while the target is temporarily protected from damage. Evidence records resulting displacement. Original position, velocity, fall distance, invulnerability state, and inventory are restored.

### No-fall

Applies a short bounded vertical movement probe while the target is protected from damage. The runtime samples fall-distance behavior and records the maximum observed value and suspicious downward-airborne resets. Original movement and inventory state are restored.

### Fake entity

Creates a nonpersistent, client-side synthetic entity through the optional ProtocolLib adapter. It is shown only to the target and controlling staff member, never broadcast to ordinary players. Evidence records interaction count, attack count, first-interaction latency, and the minimum observed aim angle toward the synthetic entity.

### Fake base

Fake bases are a separate bounded visual probe implemented without changing the Minecraft world. The runtime sends a fixed 7x7 virtual deepslate/blackstone/tinted-glass structure through Paper's client-side multi-block-change API. It never places, breaks, replaces, or rolls back a real block.

Before showing the structure, the runtime requires all of the following:

- the target is online on the current backend;
- the target's current chunk is already loaded; the feature never loads or generates a chunk;
- the entire template fits inside that one chunk and inside the world's build height;
- every virtual template cell currently corresponds to real air;
- the 5x5 interior floor is solid and is not an explicitly hazardous block such as magma, campfire, cactus, or powder snow;
- no other fake-base operation already owns that target;
- the global limit of 8 active fake bases and per-controller limit of 2 are not exceeded;
- durable audit storage is available before rendering begins.

The target sees the virtual structure. Ordinary players do not. Authorized staff see it only after using the operation's **Teleport** control, which teleports that staff member to the virtual base and then applies the same client-only view to that staff client.

A fake base expires after five minutes. At roughly four minutes, the controlling staff member receives a warning with clickable **Extend**, **Clear**, and **Teleport** controls. **Extend** starts a new five-minute expiry window. The structure is also cleared when the target moves more than 48 blocks away, changes world/backend, disconnects, the controlling staff member disconnects or leaves staff mode, or the plugin's fake-base lifecycle closes.

## Fake-base cleanup and recovery

Fake-base world safety does not depend on a rollback log because there is no world mutation to roll back. Cleanup reads the current authoritative real block data for every overlaid cell and sends those real block states back to each still-connected viewer. Cleanup is idempotent, so duplicate cancellation paths cannot delete or replace real blocks.

If rendering fails after a viewer is registered, the operation is closed and an authoritative restore is attempted immediately. If a viewer or scheduler retires before a restore can be sent, disconnect/session teardown is the safety boundary: virtual client block changes cannot persist in the server world and disappear when that client session ends. A hard server/process interruption therefore cannot leave fake blocks in a saved world. Normal plugin-disable/reload lifecycle handling also attempts explicit viewer cleanup before operation state is discarded; scheduler retirement is logged rather than falsely reported as a successful restore.

The fake-base operation itself is intentionally in memory. Persisting a fake-block operation across a process restart would be misleading because the client session that received those virtual blocks no longer exists. Durable evidence instead records coordinate-free operation lifecycle events in the existing `audit_events` ledger. No new Flyway migration is required; V18 remains the current migration boundary.

Fake-base audit records include the operation correlation ID, staff/target UUIDs, server ID, lifecycle action, outcome, reason code, and timestamp. They deliberately do **not** store the fake-base coordinates or unrelated player data.

## Safety and recovery for state-changing testers

Tester state is journaled in MariaDB migration V18 **before** a temporary mutation is applied. There may be only one globally active tester row for a target UUID across backends. Active tester rows also participate in the inventory lock contract, so an offline inventory edit cannot race a pending tester recovery after a disconnect or restart.

For state-changing testers, the runtime stores a bounded exact snapshot of the target state it may need to restore. Normal completion, staff cancellation, timeout, disconnect/reconnect, death/respawn, reload interruption, and runtime restart all converge on the same durable recovery row. The row is not marked terminal until restoration or cleanup has been verified. If restoration cannot be verified, the row stays active and blocks a new tester for that target.

During a state-changing probe, common player actions that could consume or move test assets are cancelled, including drops, pickups, item consumption, hand swaps, placement, and inventory opening. The probes are intentionally short and targets are temporarily invulnerable.

## ProtocolLib and fake-entity failure behavior

ProtocolLib is optional for the rest of EnthusiaStaff. The fake-entity implementation is isolated behind a focused adapter and uses no direct NMS dependency. If ProtocolLib is missing, cannot initialize, or becomes unhealthy, fake-entity testing fails closed. Other tester types and fake-base rendering do not depend on ProtocolLib.

A fake-entity journal row is not falsely completed when packet cleanup cannot be verified. Recovery waits for a healthy packet adapter, removes the journaled synthetic entity identifier for the current client session when applicable, and then completes the row.

## Configuration

`staff-tools.cheat-tester` is restart-only configuration. `/estaff reload` rejects a candidate that changes it without a restart.

```yaml
staff-tools:
  cheat-tester:
    timeout-millis: 4000
    probe-ticks: 60
    maximum-active-global: 8
    maximum-active-per-staff: 2
    fake-entity-distance: 3.0
    velocity:
      horizontal: 0.75
      vertical: 0.30
    no-fall:
      vertical: 0.70
```

Runtime validation enforces safe bounds: timeout 1–15 seconds, global concurrency 1–32, per-staff concurrency no greater than the global limit, fake-entity distance 1–8 blocks, configured velocity components no greater than 2.0, and probe sampling 10–300 ticks.

Fake-base limits are fixed release safety limits rather than reloadable configuration: 7x7 approved template, one already-loaded chunk, 8 active globally, 2 per controlling staff member, 48-block distance, five-minute lifetime, and one-minute expiry warning.

## Evidence interpretation and privacy

A positive-looking tester observation is only evidence for staff review. Staff must consider latency, player input, Bedrock/Java behavior, server conditions, and other case evidence. The tester never creates a sanction or punishment request automatically.

The durable tester row contains tester/session identifiers, server/staff/target UUIDs, bounded configuration/evidence, and the recovery snapshot required to restore temporary state. It does not add raw network addresses, chat contents, private messages, or unrelated player evidence. Fake-base lifecycle evidence is separately coordinate-free as described above.

Representative distributed Java/Bedrock acceptance remains part of `ES-V02`; ordinary package validation must not claim that private acceptance passed when that environment is unavailable.
