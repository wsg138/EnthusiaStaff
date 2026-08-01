# Recovery and Troubleshooting

Recovery begins by preserving evidence and stopping conflicting work. It does
**not** begin by repeatedly running the same destructive command.

## Find the affected system

| Symptom or area | Feature details and source files | Related procedure |
| --- | --- | --- |
| Startup, MariaDB, protocol, modes, reload or identity | [[Core Platform and Infrastructure]] | [[Configuration]], [[Protocol and Network Traffic]] |
| Punishment, request, history, report or evidence | [[Moderation, Punishments, and Reports]] | [[Punishment System]], [[Reports and Evidence]] |
| Staff mode, vanish, freeze, inventory, confiscation, economy or alts | [[Staff Tools, Investigations, and Player-State Safety]] | [[Staff Mode, Vanish, and Freeze|Staff-Mode-Vanish-and-Freeze]], [[Inventory and Confiscation Safety]] |
| Discord, website, provider, migration, shadow or cutover | [[Integrations, Migration, and Release Readiness]] | [[Integrations]], [[LiteBans Migration]], [[Shadow Mode and Cutover]] |

## First response

1. Stop repeated actions for the affected player, case, asset, provider or migration.
2. Record exact time, backend/proxy, actor, target UUID and visible error.
3. Record applicable case, sanction, report, draft, request, operation, patch,
   migration, message and staff-session IDs.
4. Run `/estaff status`.
5. Run the narrowest relevant verify command; use `/estaff verify full` when needed.
6. Preserve logs and sanitized stack traces.
7. Determine whether authoritative state committed before retrying anything.
8. Replay only a documented idempotent stage.
9. Quarantine any ambiguous outcome.

## Never do these as a first response

- Do not spam the same punishment, removal, confiscation or restore command.
- Do not edit MariaDB rows to make state “look finished.”
- Do not release leases/fences manually without a documented recovery procedure.
- Do not point the plugin at a different database as a shortcut.
- Do not rebuild inventories or balances from memory.
- Do not enable another authority while the original authority may still be writing.
- Do not post secrets, raw addresses or private evidence in ordinary support channels.

## Operational modes

| Mode | Meaning |
| --- | --- |
| `BOOTSTRAP` | Configuration, schema and integrations are being established |
| `DEGRADED` | Safe reads/status remain; affected unsafe actions are disabled |
| `SHADOW_MIGRATION` | LiteBans is authoritative; EnthusiaStaff compares only |
| `ACTIVE` | EnthusiaStaff is authoritative |
| `MAINTENANCE` | Sensitive writes/reconnect evidence are suppressed for planned work |
| `READ_ONLY_FAILURE` | Destructive work is blocked pending recovery |

Do not change mode merely to bypass an error. See
[[Core Platform and Infrastructure]] for current mode completion and source files.

## MariaDB unavailable

Expected behavior: no new punishment or destructive edit. Safe status, history and
recovery inspection may remain.

Check:

- connection/TLS;
- credential scope;
- pool exhaustion;
- schema version and migration checksum;
- database time/clock skew;
- long-running transactions;
- lease, outbox and recovery-worker backlog;
- disk/host availability.

If the database may have committed before the client timed out, look up the exact
idempotency/case/operation record before retrying.

## Schema checksum or future version

Stop unsafe startup/work. Confirm migrations match the exact runtime artifacts.
Do not repair Flyway history manually without an approved database recovery plan
and backup.

## Paper–Velocity channel failure

New network sanctions must remain blocked when network enforcement cannot be
proven.

Check:

- certificate chain and hostname;
- key/trust stores and password environment variables;
- server identity/allowlist;
- protocol version;
- clock skew and replay rejection;
- queue limits and backlog;
- reconnect/backoff state;
- durable inbox/outbox acknowledgement.

The channel must not require an online player as transport.

## Punishment or request timed out

Do not immediately re-run. A timeout can occur after durable commit.

Check:

- draft/request ID;
- case and sanction records;
- idempotency key;
- network/Discord outbox entries;
- approval/request state;
- current operational mode.

Same-key replay should return the original outcome. Changed content with a reused
key should be rejected as a conflict.

## Report state conflict

A report mutation may fail because another staff member changed the revision.
Reopen the report, read the latest revision and review the new state. Do not force
the stale action.

## Inventory or economy operation incomplete

Stop edits, transfers and server switching for the target/scope.

Inspect the operation as one state machine:

- actor/target/scope;
- operation state and idempotency key;
- lease owner/fence and expiration;
- before snapshot/checksum;
- selected item paths/fingerprints or economy plan;
- provider result;
- after verification;
- audit and quarantine state.

See [[Inventory and Confiscation Safety]] and
[[Staff Tools, Investigations, and Player-State Safety]].

## Staff-mode restoration failure

Keep the staff member out of normal gameplay. Preserve the original durable
session snapshot. Do not toggle staff mode repeatedly or replace the snapshot with
current broken state.

Record:

- session ID and revision;
- original/current backend and location;
- visible inventory/armor/offhand;
- XP, effects, health/hunger and game mode;
- reconnect/restart timing;
- any staff items that escaped the profile.

Escalate to owner recovery.

## Vanish exposure

Record exactly how the player was exposed:

- tab/player-info;
- entity spawn/metadata/equipment;
- command suggestion or `/seen`;
- chat/voice recipient;
- sound/particle/container effect;
- external API/provider;
- Java/Bedrock client and protocol version.

Do not assume Bukkit `hidePlayer` proves the other visibility layers are correct.
See [[Vanish Internals]].

## Freeze bypass

Record the exact bypass, client/platform, backend and sequence. Release or hand off
the freeze when the investigation cannot continue safely. Do not repeatedly test
an unsafe bypass on the live player.

## Outbox delivery stalled

Check:

- due time and attempt count;
- lease owner/fence/expiration;
- sanitized last error;
- destination configuration;
- circuit/dead-letter state;
- consumer acknowledgement;
- whether the producer transaction committed.

A successful HTTP/socket write is not necessarily a durable consumer
acknowledgement.

## Optional provider unavailable

Confirm only the dependent feature is disabled. Examples:

- RoseChat down: disable affected chat/staff/PM-evidence/automod paths;
- Voice down: text mute may remain, voice enforcement unavailable;
- Currency down: hide/block economy confiscation;
- Market down: block Market confirmation;
- ProtocolLib down: fail closed for dependent spectator presentation;
- Polar unavailable: disable Polar automation only.

See [[Integrations]].

## Migration mismatch

Keep LiteBans authoritative. Compare exact:

- source IDs and mappings;
- counts/checksums;
- timestamps and expirations;
- UUID/name interpretation;
- active state;
- login, mute and IP/network decisions;
- source schema variant/blocker output.

Correct source interpretation or implementation. Do not edit mappings solely to
force parity.

## Emergency freeze after cutover

When EnthusiaStaff authority may be unsafe:

1. enter `READ_ONLY_FAILURE` using the approved Founder control;
2. stop new traffic/destructive actions;
3. identify every post-cutover sanction and asset operation;
4. reconcile state into one selected authority;
5. resolve incomplete operations and outboxes;
6. prove parity and command ownership;
7. re-enable exactly one authority.

There is intentionally no automatic failback to LiteBans.

## Support evidence

Provide sanitized:

- exact source revision and artifact hashes;
- Java, Paper, Velocity and MariaDB versions;
- operational mode and health categories;
- stable case/operation/message/session/migration IDs;
- timestamps;
- bounded sanitized errors;
- reproduction steps;
- whether restart/reconnect changed behavior;
- which commands/actions were already attempted.

Do not provide secrets, raw addresses, private messages, coordinates, staff notes,
confiscated contents or appeal media in ordinary support channels.

## Related pages

- [[Core Platform and Infrastructure]]
- [[Moderation, Punishments, and Reports]]
- [[Staff Tools, Investigations, and Player-State Safety]]
- [[Integrations, Migration, and Release Readiness]]
- [[Configuration]]
- [[Integrations]]
- [[Protocol and Network Traffic]]
- [[LiteBans Migration]]
- [[Shadow Mode and Cutover]]
