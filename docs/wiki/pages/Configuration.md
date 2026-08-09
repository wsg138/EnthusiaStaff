# Configuration

EnthusiaStaff is moving toward a modular, versioned configuration tree that can be validated as one immutable model. Current merged `main` implements only part of that target, so this page separates **current sources** from **planned modular layout**.

- Current core status: [[Core Platform and Infrastructure]]
- Commands/permissions: [[Commands and Permissions]]
- Provider settings: [[Integrations]]
- Report-specific configuration: [[Report Configuration]]
- Validation/reload evidence: [[Build and Testing]]

## Current configuration sources

| File or class | Current purpose |
| --- | --- |
| [`paper/src/main/resources/config.yml`](https://github.com/wsg138/EnthusiaStaff/blob/main/paper/src/main/resources/config.yml) | Current Paper runtime settings, including current staff-tool controls |
| [`paper/src/main/resources/reason-policies.yml`](https://github.com/wsg138/EnthusiaStaff/blob/main/paper/src/main/resources/reason-policies.yml) | Stable reason, family, ladder, decay and compatibility policy |
| [`paper/src/main/resources/reports.yml`](https://github.com/wsg138/EnthusiaStaff/blob/main/paper/src/main/resources/reports.yml) | Report submission/evidence/retention policy |
| [`paper/src/main/resources/gui/reports.yml`](https://github.com/wsg138/EnthusiaStaff/blob/main/paper/src/main/resources/gui/reports.yml) | Report queue/detail GUI presentation |
| [`paper/src/main/resources/plugin.yml`](https://github.com/wsg138/EnthusiaStaff/blob/main/paper/src/main/resources/plugin.yml) | Commands, permissions, rank inheritance and soft dependencies |
| [`ReasonPolicyConfigurationLoader.java`](https://github.com/wsg138/EnthusiaStaff/blob/main/paper/src/main/java/net/enthusia/staff/paper/config/ReasonPolicyConfigurationLoader.java) | Reason-policy parsing and validation |
| [`PaperReasonPolicyBootstrap.java`](https://github.com/wsg138/EnthusiaStaff/blob/main/paper/src/main/java/net/enthusia/staff/paper/PaperReasonPolicyBootstrap.java) | Valid reason-policy publication |
| [`AtomicReasonPolicyRepository.java`](https://github.com/wsg138/EnthusiaStaff/blob/main/domain/src/main/java/net/enthusia/staff/domain/ports/AtomicReasonPolicyRepository.java) | Immutable atomically replaceable policy boundary |
| [`PaperDatabaseConfiguration.java`](https://github.com/wsg138/EnthusiaStaff/blob/main/paper/src/main/java/net/enthusia/staff/paper/PaperDatabaseConfiguration.java) | Database settings/environment-variable references |
| [`VelocityConfiguration.java`](https://github.com/wsg138/EnthusiaStaff/blob/main/velocity/src/main/java/net/enthusia/staff/velocity/VelocityConfiguration.java) | Velocity/network/Discord/site settings and secret references |

The complete target modular tree is not yet implemented.

## Reason IDs, aliases and removed reasons

Reason IDs are durable identities, not display strings. The policy supports versioned compatibility metadata so old stored state can remain understandable without making removed policies newly selectable.

An alias maps one historical ID directly to one active canonical reason. Alias chains, cycles, self-targets, unknown targets, duplicate IDs and overlap with active/removed IDs are invalid.

A removed reason retains presentation metadata for history/review, but has no active ladder and cannot be selected for a new punishment. Existing cases/sanctions are not rewritten when a reason is renamed or retired.

Aliases, removed-reason metadata, active reasons and configuration version are published as one validated snapshot.

## Current history/sanction settings

Current Paper configuration includes history and exact-sanction-action presentation/validation settings such as bounded page size, request/appeal timeline inclusion, timezone and mutation-reason length limits.

These settings are reloadable only through their validated snapshot path. A rejected candidate must leave the previous valid values active and must not rebuild MariaDB, rerun migrations, reset operational mode, activate moderation authority, discard queued work or duplicate workers.

## Current report settings

Report policy and GUI configuration have their own source files and validation/publication path. They cover bounded submission/evidence behavior and report inventory presentation.

Use [[Report Configuration]] for supported fields and operator-facing behavior. Do not copy that full reference into this page.

## Current staff-tool settings

Current merged staff-tool controls are stored in Paper configuration and are applied to the runtime staff profile/dispatcher. Treat scheduler/lifecycle/provider capacity and any setting documented as restart-only accordingly; a reload command should not pretend a restart-only runtime resource was rebuilt successfully.

Staff-facing behavior is documented in [[Staff Mode, Vanish, and Freeze|Staff-Mode-Vanish-and-Freeze]].

## Target modular layout

The authoritative goals describe a broader shape similar to:

```text
plugins/EnthusiaStaff/
├── config.yml
├── storage.yml
├── messages.yml
├── discord.yml
├── website.yml
├── vanish.yml
├── staff-mode.yml
├── staff-tools.yml
├── inventory.yml
├── alts.yml
├── reports.yml
├── automod.yml
├── anticheat.yml
├── market.yml
├── reputation.yml
├── escalation.yml
├── migration.yml
├── integrations.yml
├── servers.yml
├── gui/
│   ├── punish-categories.yml
│   ├── punish-reasons.yml
│   ├── punish-review.yml
│   ├── punishment-history.yml
│   ├── remove-punishment.yml
│   ├── reports.yml
│   ├── report-details.yml
│   ├── player-inspector.yml
│   ├── staff-tools.yml
│   ├── cheat-testers.yml
│   └── alts.yml
└── punishments/
    └── <versioned policy files>
```

That tree is a finished-design target, not a statement that every file currently exists.

## Design rules

Configuration changes should preserve:

- stable IDs and explicit aliases;
- path-aware validation failures;
- immutable runtime snapshots;
- cross-file reference validation;
- safe defaults that cannot activate production authority accidentally;
- explicit restart-required settings;
- one all-or-nothing publication boundary for settings that must agree;
- durable state continuity across reload.

A reload must not discard active sanctions, drafts, requests, reports, staff sessions, leases, journals, recovery state, or queued durable delivery.

## Secrets

Keep real secrets outside Git and ordinary Wiki examples, including:

- MariaDB credentials;
- TLS key/trust-store passwords;
- network-identity encryption/HMAC keys;
- Discord webhook URLs;
- website bridge/authentication secrets;
- Cloudflare/email/Turnstile/D1/R2/Hyperdrive credentials;
- private provider credentials.

Configuration should normally reference an environment-variable/secret name rather than embed the secret value.

## Reload

```text
/estaff reload
```

For a reloadable configuration set, the safe model is:

1. parse a complete candidate separately from live state;
2. validate versions, IDs, aliases, cross-references, ranges, GUI slots, permissions, servers and integration references;
3. reject the whole affected candidate on any invalid dependency;
4. leave the prior valid snapshot active on failure;
5. publish only a fully valid immutable replacement;
6. preserve durable in-flight workflows and owned runtime resources.

Current implementation does not yet provide the full modular all-file reload described by the final goals.

## Restart-required boundaries

Typical restart-owned resources include:

- database pools/credentials;
- TLS key/trust material;
- backend/proxy identity and persistent sockets;
- provider classloading/service discovery;
- executor capacity and ownership.

Verification should report **RESTART REQUIRED** when applicable instead of claiming a hot reload changed a resource that remained live.

## Operational modes

```text
BOOTSTRAP
DEGRADED
SHADOW_MIGRATION
ACTIVE
MAINTENANCE
READ_ONLY_FAILURE
```

Modes are safety/authority states, not convenience toggles. A configuration failure must not be “fixed” by switching authority merely to make a command available.

## Review checklist

Before approving a configuration change, verify:

- stable/unique IDs and deterministic alias handling;
- removed IDs remain historical/presentation-only;
- references and ranges validate together;
- GUI slots/permissions/server scopes are known;
- invalid candidates leave the previous valid runtime model intact;
- restart-owned resources are reported honestly;
- secrets cannot appear in source/log/error output;
- active durable workflows remain interpretable after publication;
- tests cover invalid and rollback publication, not only happy-path parsing;
- any runtime claim beyond parser/publication tests is supported by the appropriate staging evidence.

## Current state

Configuration/reload is **partial**. Several important validated snapshots exist, but the complete modular tree and full cross-file immutable reload boundary in the goals are not finished. See [[Core Platform and Infrastructure]] and [[Implementation Status]] for current merged-main status without artificial percentages.

## Related pages

- [[Core Platform and Infrastructure]]
- [[Report Configuration]]
- [[Commands and Permissions]]
- [[Integrations]]
- [[Code Review Guide]]
- [[Build and Testing]]
- [[Recovery and Troubleshooting]]