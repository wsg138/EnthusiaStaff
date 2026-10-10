# Configuration

EnthusiaStaff configuration is being migrated toward a versioned, modular configuration model. The migration is tracked by issue #425.

Current production behavior remains the compatibility baseline. A setting becoming configurable must not weaken authorization, transaction safety, privacy boundaries, recovery, audit integrity, or concurrency guarantees.

## Current versioned configuration families

The Paper runtime currently validates these configuration families:

| File | Version metadata | Purpose |
| --- | --- | --- |
| `config.yml` | `config-version` | Core Paper/runtime settings, including reloadable and restart-owned sections |
| `reason-policies.yml` | `version` | Current punishment reason-policy definitions |
| `messages.yml` | `schema-version` | Player/staff-facing messages (C1/C1.1: `/estaff` and `/vanish`) |
| `ranks.yml` | `schema-version` | **C2 candidate only**: strict rank-capability preview; not active permission enforcement |
| `reports.yml` | `version` | Report policy |
| `gui/reports.yml` | `version` | Report GUI presentation |
| `policy-v2.yml` | `schema-version` plus versioned policy snapshots | Policy v2 publication/shadow configuration |

Additional files will join this validation surface as the configurability migration proceeds.

## Validate without applying

Use:

```text
/estaff config validate
```

This parses and validates the registered versioned configuration files and reports their versions. It is read-only: it does not publish a candidate, reload a subsystem, or mutate runtime state.

A failed file does not prevent the command from validating independent files, so one run can report useful information about the rest of the tree.

The command uses the existing `enthusiastaff.reload` authorization boundary.

## Reload

These commands use the same existing safe reload coordinator:

```text
/estaff reload
/estaff config reload
```

`/estaff config reload` is an alias, not a second reload implementation.

The existing coordinator validates a candidate before publication, rejects restart-owned changes, preserves the previous runtime on validation failure, and performs the existing rollback/reconciliation behavior for reloadable subsystems.

`messages.yml` participates in that same reload chain. A message candidate is parsed and validated before the existing reload runs, then published as one immutable snapshot only after the delegated reload succeeds. If message validation or the delegated reload fails, the previous message catalog remains active.

## Message configuration

C1 introduced the strict `messages.yml` catalog with `/estaff` responses. C1.1 adds `/vanish` command responses without changing the authorization or persistence rules. Shipped defaults reproduce the current wording.

Message templates use bounded MiniMessage plus explicitly declared placeholders such as `{label}` and `{operations}`. Every key introduced in the file's declared schema is required; unknown keys are rejected, and each template must use exactly the placeholder set defined for that message. Placeholder values are escaped and inserted literally, so player/command-derived values cannot inject formatting or actions.

The allowed MiniMessage surface is intentionally narrow: named/hex colors, text decorations, and reset. Interactive or data-bearing tags such as click, hover, insertion, selector, NBT, keybind, gradients/rainbows, and other unsupported tags are rejected during validation before they can become active. Existing Adventure/`StaffMessageStyle` presentation still supplies the default semantic colors when a configured message does not override them.

### Message schema upgrades

The shipped file now uses `schema-version: 2`. It adds a `messages.vanish` section with `permission-denied`, `player-only`, `usage` and `mode-disabled` keys.

Existing custom `schema-version: 1` files remain supported: all original v1 keys stay required, their customized values are preserved, and the new v2 keys use shipped defaults in memory. **The server does not silently overwrite your customized v1 file.** A missing old v1 key, invalid placeholder, unknown key, or unsafe MiniMessage tag still fails validation.

To customize the new vanish messages, back up `messages.yml`, change the header to `schema-version: 2`, and add the complete shipped `messages.vanish` section before `/estaff config validate` and `/estaff config reload`. Version 2 requires every v2 key; partially adding the section will be rejected.

If rolling the plugin JAR back to a version that supports only schema 1, restore the backed-up v1 message file along with the older JAR. Schema 2 cannot be read by the original C1 loader.

Available vanish placeholders: `{label}` and `{choices}` in `vanish.usage`; `{mode}` in `vanish.mode-disabled`. Placeholders are escaped and may not add clickable actions. The command's rank/vanish permissions are not controlled by message text.

Internal audit/security log messages and raw runtime diagnostic details are not automatically operator-editable merely because ordinary chat responses become configurable.

## Rank capability configuration preview (C2-B)

The plugin ships `ranks.yml` schema 1 and creates it if missing without overwriting your existing file. **This is a validation-only candidate; changing its grants, inheritance or setting names does not change anyone's actual abilities.** The running permission checks, duty/session rules, punishment policy, hierarchy protections, and vanish reconciliation still use their existing authoritative code. Neither `/estaff reload` nor a server restart activates preview capabilities.

`/estaff config validate` checks the schema, complete rank table, recognized typed capability IDs, exact key spelling, duplicate YAML keys/array entries, rank inheritance constraints, and separation of the Developer technical branch and non-player SYSTEM identity. An invalid candidate is reported but cannot apply a partial permission change. Shipped defaults represent the current **rank dimension** for selected Staff Mode capabilities, not a complete authorization decision (direct permissions and session/context rules still apply).

Allowed v1 rank names: HELPER, MOD, DEVELOPER, ADMIN, FOUNDER, SYSTEM. A rank may inherit only from a strictly lower rank on the moderation chain. Developer and SYSTEM cannot inherit; SYSTEM cannot receive grants. The canonical typed capability identifiers are defined in `StaffCapability.java`. This early schema models only the limited rank decisions audited in [the C2 authority audit](configurability-c2-rank-capability-audit.md); it does not offer configurable punishment approval or arbitrary command permission overrides.

Later C2-C work must compare preview decisions against live legacy decisions in shadow mode before any C2-D enforcement migration. C2-D needs separate review, cross-subsystem tests, revocation/reconciliation, and rollback, including coordinating with #271. Editing this file now is useful for future policy design, not a production permission change.

## Restart-owned settings

Not every setting is safe to rebuild while a server is running. Settings that own process/runtime resources remain restart-required until that subsystem has an explicit safe replacement lifecycle.

A reload must never claim that a restart-owned change was applied.

## Migration direction

The staged migration is:

1. shared validation/version/reload foundation;
2. centralized messages;
3. rank capabilities;
4. Staff Mode profiles/tools;
5. Policy v2 punishment configuration;
6. punishment GUI presentation;
7. remaining GUIs and integrations.

Policy and presentation remain separate. Security/correctness invariants remain code-owned.
