# Recovery and Troubleshooting

Recovery begins by preserving evidence and stopping conflicting work. It does **not** begin by repeatedly running the same destructive command.

## Find the affected system

| Symptom or area | Product/source hub | Procedure or deep dive |
| --- | --- | --- |
| Startup, MariaDB, protocol, modes, reload or identity | [[Core Platform and Infrastructure]] | [[Configuration]], [[Protocol and Network Traffic]] |
| Punishment, request, history, report or evidence | [[Moderation, Punishments, and Reports]] | [[Punishment System]], [[Reports and Evidence]] |
| Staff mode, vanish, freeze, inventory, confiscation, economy or alts | [[Staff Tools, Investigations, and Player-State Safety]] | [[Staff-Mode-Vanish-and-Freeze]], [[Inventory and Confiscation Safety]], [[Vanish Internals]] |
| Discord, website, provider, migration, shadow or cutover | [[Integrations, Migration, and Release Readiness]] | [[Integrations]], [[LiteBans Migration]], [[Shadow Mode and Cutover]] |
| I am debugging the code behind the failure | [[Developer Code Guide]] | [[Code Review Guide]], [[Build and Testing]] |

## First response

1. Stop repeated actions for the affected player, case, asset, provider or migration.
2. Record exact time, backend/proxy, actor, target UUID and visible error.
3. Record applicable stable case, sanction, report, draft, request, operation, patch, migration, message or staff-session IDs.
4. Run `/estaff status`.
5. Run the narrowest relevant verify command; use `/estaff verify full` when needed.
6. Preserve logs and sanitized stack traces.
7. Determine whether authoritative state committed before retrying anything.
8. Replay only a documented idempotent stage.
9. Quarantine or escalate any ambiguous outcome.

## Never do these as a first response

- Do not spam the same punishment, removal, confiscation or restore command.
- Do not edit MariaDB rows to make state “look finished.”
- Do not release leases/fences manually without a documented recovery procedure.
- Do not point the plugin at a different database as a shortcut.
- Do not rebuild inventories or balances from memory.
- Do not enable another authority while the original authority may still be writing.
- Do not publish secrets, raw addresses, private messages, coordinates, staff notes or private evidence in ordinary support channels.

## Operational modes

| Mode | Meaning |
| --- | --- |
| `BOOTSTRAP` | Configuration, schema and integrations are being established |
| `DEGRADED` | Safe reads/status remain; affected unsafe actions are disabled |
| `SHADOW_MIGRATION` | LiteBans is authoritative; EnthusiaStaff compares only |
| `ACTIVE` | EnthusiaStaff is authoritative |
| `MAINTENANCE` | Sensitive writes/reconnect evidence are suppressed for planned work |
| `READ_ONLY_FAILURE` | Destructive work is blocked pending recovery |

Do not change mode merely to bypass an error. See [[Core Platform and Infrastructure]] and [[Implementation Status]] for the current merged implementation/acceptance boundary.

## MariaDB unavailable

Expected behavior is conservative: new punishments/destructive edits whose durable authority cannot be established must stop. Safe status/history/recovery inspection may remain where designed.

Check:

- connection/TLS;
- credential scope;
- pool exhaustion;
- schema version and migration checksum;
- database time/clock skew;
- long-running transactions/deadlocks;
- lease, outbox and recovery-worker backlog;
- disk/host availability.

If the database may have committed before the client timed out, look up the exact idempotency/case/operation record before retrying. Timeout-after-commit is a core idempotency case, not proof that “nothing happened.”

## Schema checksum or future version

Stop unsafe startup/work. Confirm migrations match the exact runtime artifacts. Current merged history is through V17.

Do not edit an applied migration or use Flyway repair simply to make a modified historical file pass. Follow an approved database recovery plan with backup/evidence when history itself is genuinely damaged.

## Paper-Velocity channel failure

New network sanctions must remain blocked when network enforcement cannot be proved.

Check:

- certificate chain and hostname;
- key/trust stores and password environment variables;
- server identity/allowlist;
- protocol version;
- clock skew and replay rejection;
- queue limits/backlog;
- reconnect/backoff state;
- durable inbox/outbox acknowledgement;
- stale/duplicate consumer state.

The channel must not require an online player as transport. A successful socket write is not necessarily a durable consumer acknowledgement.

## Punishment or request timed out

Do not immediately rerun. Check:

- draft/request ID;
- case/sanction state;
- idempotency key;
- network/Discord outbox state;
- approval/request state;
- current operational mode.

Same-key replay should return the original committed result when that is the defined idempotent behavior. Reusing a key for changed content should be rejected as a conflict.

## Report state conflict

A report action can fail because another staff member changed the revision. Reopen the report, read the latest revision and review the new state. Do not force the stale action or keep submitting an old confirmation screen.

## Inventory, confiscation or economy operation incomplete

Stop edits, transfers and server switching for the affected target/scope.

Inspect the operation as one state machine:

- actor/target/scope;
- operation state/idempotency key;
- lease owner/fence/expiration;
- before snapshot/checksum;
- selected item paths/fingerprints or economy plan;
- provider/runtime side-effect result;
- after verification;
- audit/quarantine state.

A failed external call after durable intent is different from an ambiguous timeout after the external side effect may have happened. Do not choose retry/rollback until the operation model distinguishes those cases.

See [[Inventory and Confiscation Safety]] and [[Staff Tools, Investigations, and Player-State Safety]].

## Staff-mode restoration failure

Keep the staff member out of normal gameplay. Preserve the original durable session snapshot. Do not toggle staff mode repeatedly or replace the snapshot with current broken state.

Record:

- session ID/revision;
- original/current backend/location;
- visible inventory/armor/offhand;
- XP/effects/health/hunger/game mode;
- reconnect/restart timing;
- any staff items that escaped the profile.

Escalate to the documented recovery owner. The original snapshot is recovery evidence.

## Vanish exposure

Record exactly where the hidden state leaked:

- tab/player-info;
- entity spawn/metadata/equipment;
- command suggestion or `/seen`;
- chat/voice recipient;
- sound/particle/container effect;
- external API/provider;
- Java/Bedrock client and protocol version.

Do not assume Bukkit `hidePlayer` proves other visibility layers. See [[Vanish Internals]].

## Freeze bypass

Record the exact bypass, client/platform, backend and sequence. Release or hand off the freeze when the investigation cannot continue safely. Do not repeatedly exercise an unsafe bypass on a live player merely to collect more attempts.

## Outbox delivery stalled

Check:

- due time/attempt count;
- lease owner/fence/expiration;
- sanitized last error;
- destination/configuration;
- circuit/dead-letter/manual-retry state;
- consumer acknowledgement;
- whether the producer transaction committed.

Never “fix” a stuck message by blindly deleting the row when its corresponding side effect may already exist.

## Optional provider unavailable

Confirm only the dependent feature is disabled when that is safe. Examples:

- RoseChat unavailable: affected chat/staff/PM-evidence/automod paths degrade;
- Voice unavailable: text moderation may remain while voice enforcement is unavailable;
- Currency unavailable: economy confiscation is hidden/blocked;
- Market unavailable: market moderation confirmation is blocked;
- ProtocolLib unavailable/incompatible: dependent vanish/spectator presentation fails conservatively;
- Polar unsupported/unavailable: Polar automation remains disabled.

See [[Integrations]].

## Migration mismatch

Keep LiteBans authoritative. Compare exact source/mapped state:

- source IDs/mappings;
- counts/checksums;
- timestamps/expirations;
- UUID/name interpretation;
- active/expired state;
- login/mute/network decisions;
- source schema variant/blocker output.

Correct source interpretation or implementation. Do not edit mappings solely to force parity.

## Emergency freeze after cutover

When EnthusiaStaff authority may be unsafe:

1. enter `READ_ONLY_FAILURE` using the approved Founder control;
2. stop new traffic/destructive actions;
3. identify every post-cutover sanction and asset operation;
4. reconcile state into one selected authority;
5. resolve incomplete operations and outboxes;
6. prove parity and command ownership;
7. re-enable exactly one authority.

There is intentionally no automatic failback to LiteBans: post-cutover actions may exist only in EnthusiaStaff and must be reconciled first.

## Maintainer debugging path

When the symptom appears to be a code defect:

1. identify the owning feature hub and exact source trace in [[Developer Code Guide]];
2. identify the invariant that failed using [[Code Review Guide]];
3. locate durable evidence before reproducing a destructive path;
4. reproduce in a disposable environment where possible;
5. add the narrow unit/integration/concurrency/failure test that proves the bug and fix;
6. rerun the strongest required runtime/staging layer from [[Build and Testing]];
7. do not claim the original production/runtime incident is resolved until the exact relevant acceptance has been rerun.

## Support evidence

Provide only sanitized support material:

- exact source revision/artifact hashes;
- Java, Paper, Velocity, MariaDB and relevant provider versions;
- operational mode/health categories;
- stable case/operation/message/session/migration IDs;
- timestamps;
- bounded sanitized errors;
- reproduction steps;
- restart/reconnect effect;
- commands/actions already attempted.

Keep credentials, raw addresses, private messages, coordinates, staff notes, confiscated contents and appeal media out of ordinary support channels.

## Related pages

- [[Core Platform and Infrastructure]]
- [[Moderation, Punishments, and Reports]]
- [[Staff Tools, Investigations, and Player-State Safety]]
- [[Integrations, Migration, and Release Readiness]]
- [[Developer Code Guide]]
- [[Code Review Guide]]
- [[Build and Testing]]
- [[Configuration]]
- [[Integrations]]
- [[Protocol and Network Traffic]]
- [[LiteBans Migration]]
- [[Shadow Mode and Cutover]]