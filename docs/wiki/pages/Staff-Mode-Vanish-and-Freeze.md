# Staff Mode, Vanish, and Freeze

Use this page for the staff-facing procedure. The three systems are related but separate:

- **Staff mode** protects normal player state and supplies the staff hotbar.
- **Vanish** controls who can see staff during an investigation.
- **Freeze** temporarily restricts a player while staff investigate.

For implementation status and source files, use [[Staff Tools, Investigations, and Player-State Safety]]. For packet/visibility internals, use [[Vanish Internals]]. For the advanced tester tool, use [[Cheat Tester]].

> **Deployment boundary:** merged code and automated validation are not production acceptance. Use only the staff workflows approved for the live server, and do not infer Java/Bedrock/Folia/distributed acceptance from a command existing in source.

## Quick actions

```text
/staff
/vanish
/freeze <player> <reason>
/unfreeze <player> <reason> CONFIRM
/stafftools
/stafftools random
/stafftools spectate <player>
/cheattester ...
```

If a staff-state, storage, provider, scheduler, or recovery error appears, stop repeating the action and use [[Recovery and Troubleshooting]].

## Staff mode

Enter or leave with:

```text
/staff
```

Staff mode durably records the normal player state **before** applying the temporary staff profile. That saved state is the recovery authority for inventory, armor, offhand, XP, health/hunger, effects, location/server, game mode, flight and other owned state.

Entry should fail closed when combat safety, storage, worker capacity, or durable snapshot creation cannot be proved. A reconnect or restart recovers the existing durable session rather than creating a new “normal” snapshot from temporary staff state.

### Before entering

- Finish or leave normal combat first.
- Enter only for legitimate staff work.
- Do not rearrange items to work around the snapshot/recovery model.
- Do not enter while status/verification reports a storage or recovery blocker.

Staff mode must never be used to escape combat, travel for normal play, protect ordinary items, find bases, or gain gameplay information.

## Staff hotbar

The operational hotbar routes into existing commands/services; possessing the item does not grant authority.

| Slot | Tool | Normal action | Text/Bedrock fallback |
| ---: | --- | --- | --- |
| 1 | Random Player Teleport | Choose a suitable online target | `/stafftools random` |
| 2 | Player Inspector | Use on a player | `/inspect <player>` |
| 3 | Freeze | Use on a player | `/freeze <player> <reason>` |
| 4 | Reports | Open report management | `/reports` |
| 5 | Cheat Tester | Cycle/run bounded evidence probes | `/cheattester ...` |
| 6 | Follow / Spectate | Follow an eligible player | `/stafftools spectate <player>` |
| 7 | Vanish | Toggle normal vanish path | `/vanish` |
| 8 | Staff Chat | Toggle configured staff channel | `/staffchat` |
| 9 | Staff Tools Menu | Show available actions | `/stafftools` |

Cheat Tester is merged behavior, not a reserved future slot. It remains an advanced evidence-only tool with its own permissions, recovery rules, fake-entity/provider behavior and fake-base controls. Read [[Cheat Tester]] before using or reviewing it.

Every tool interaction rechecks the active session, owner UUID/session token, current explicit rank, canonical slot/material and action permission. Command-backed actions then continue through their normal policy/provider/operational-mode boundary.

Copied, transferred, stale-session, wrong-slot, wrong-material or wrong-owner staff items are rejected. Staff items are not intentionally merged back into normal inventory when the session ends.

## Random teleport

Random teleport is an investigation tool, not free travel. Candidates are filtered for unsafe/inappropriate targets such as the actor, staff-mode players, vanished/frozen/exempt players, unsafe player state, and configured disabled worlds/backends.

Target state is sampled on the target's owning scheduler before the final asynchronous teleport. If no safe target exists, the action refuses safely.

Random-teleport/cooldown settings under `staff-tools` are restart-owned; `/estaff reload` must not claim to apply a restart-only change.

## Follow / spectate

`/stafftools spectate <player>` and the hotbar action share one path. The target must be an eligible player on the current backend and the target location is captured on the target's owning scheduler before the staff teleport.

Do not weaken the staff member's required profile/game mode merely to force spectating. A safe refusal is preferable to cross-thread or state-ownership shortcuts.

## Cheat Tester

Cheat Tester performs short, bounded evidence probes such as totem refill, no-fall, velocity, auto-armor and optional fake-entity behavior. Fake-base controls render a client-side virtual structure without changing real world blocks.

It never issues a punishment automatically. State-changing testers journal recovery state before temporary mutations and must restore/verify owned state before becoming terminal.

Commands, limits, permissions, fake-base behavior, ProtocolLib degradation and privacy rules are documented in [[Cheat Tester]].

## Vanish

Toggle with:

```text
/vanish
```

Use vanish only when visible staff presence would interfere with a legitimate investigation. Vanish is separately durable from staff mode and uses a rank-aware visibility policy.

Important limitation: hiding an entity through Paper does not automatically prove invisibility from every plugin, command suggestion, tab implementation, voice/chat provider, sound/particle effect, analytics surface or external API. Those are separate integration surfaces.

For exact session fencing, rank reconciliation, Paper visibility, ProtocolLib player-info behavior and uncovered surfaces, read [[Vanish Internals]].

## Freeze

Apply and release with:

```text
/freeze <player> <reason>
/unfreeze <player> <reason> CONFIRM
```

Freeze is an investigation restriction, not a punishment duration. Keep an active staff member responsible for a frozen player and release/handoff the restriction when the investigation cannot continue.

Freeze must survive the durable lifecycle it claims to support and must not rely on one movement event while inventory, interaction, teleport, backend-switch or other bypasses remain open.

A fail-closed temporary restriction while durable freeze status is unavailable is not proof that a freeze row exists. Check authoritative state before unfreezing or retrying.

## Leaving staff mode

Run `/staff` again. Safe exit marks the durable session as exiting, removes temporary staff tools, restores the original snapshot, verifies the restored state, and only then closes the session.

Do not rebuild a failed restoration manually from memory, screenshots, or a new snapshot. Preserve the original durable recovery record and follow [[Recovery and Troubleshooting]].

## When to stop

Stop the workflow and escalate when:

- staff mode does not restore exact normal state;
- a staff tool reports stale/wrong owner/wrong session unexpectedly;
- a player disconnects or switches backend during a sensitive operation;
- storage/provider/scheduler health is uncertain;
- a freeze bypass or visibility leak is observed;
- Cheat Tester cannot prove cleanup/restoration;
- repeating an action might create a second effect.

Record the backend, player UUID, session/operation ID where available, time, exact error and what already happened. Do not clear durable rows or locks manually.

## Permissions and authority

The relevant command/tool permissions are listed in [[Commands and Permissions]]. Rank semantics are explained in [[Roles and Permissions|Rank-Authority]].

A permission node is an entry gate, not a replacement for central rank/action policy. Staff-mode items and Discord/GUI surfaces must not become alternate authority implementations.

## Go deeper

- [[Cheat Tester]] — tester controls, fake entities/bases, journaling and evidence interpretation.
- [[Vanish Internals]] — visibility, scheduler and packet details.
- [[Inventory and Confiscation Safety]] — player asset mutation/recovery.
- [[Staff Tools, Investigations, and Player-State Safety]] — merged-main status and source map.
- [[Commands and Permissions]] — command and node reference.
- [[Recovery and Troubleshooting]] — failure handling.
- [[Code Review Guide]] — developer/reviewer invariants.