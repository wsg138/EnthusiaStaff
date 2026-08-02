# Configuration

EnthusiaStaff is moving toward a modular, versioned configuration tree that is
validated as one immutable model. Current `main` still uses a smaller
configuration shape, so this page distinguishes **what exists now** from the
**target layout**.

- Core completion, source files and remaining work: [[Core Platform and Infrastructure]]
- Commands and permissions: [[Commands and Permissions]]
- Provider settings: [[Integrations]]
- Validation/reload testing: [[Build and Testing]]

## Current configuration sources

| File or class | Current purpose |
| --- | --- |
| [`paper/src/main/resources/config.yml`](https://github.com/wsg138/EnthusiaStaff/blob/main/paper/src/main/resources/config.yml) | Current Paper runtime settings |
| [`paper/src/main/resources/reason-policies.yml`](https://github.com/wsg138/EnthusiaStaff/blob/main/paper/src/main/resources/reason-policies.yml) | Current reason, ladder and escalation policy |
| [`paper/src/main/resources/plugin.yml`](https://github.com/wsg138/EnthusiaStaff/blob/main/paper/src/main/resources/plugin.yml) | Commands, permissions and soft dependencies |
| [`ReasonPolicyConfigurationLoader.java`](https://github.com/wsg138/EnthusiaStaff/blob/main/paper/src/main/java/net/enthusia/staff/paper/config/ReasonPolicyConfigurationLoader.java) | Parses and validates reason policies |
| [`PaperReasonPolicyBootstrap.java`](https://github.com/wsg138/EnthusiaStaff/blob/main/paper/src/main/java/net/enthusia/staff/paper/PaperReasonPolicyBootstrap.java) | Publishes the valid reason-policy model |
| [`PaperDatabaseConfiguration.java`](https://github.com/wsg138/EnthusiaStaff/blob/main/paper/src/main/java/net/enthusia/staff/paper/PaperDatabaseConfiguration.java) | Resolves database settings and environment-variable names |
| [`AtomicReasonPolicyRepository.java`](https://github.com/wsg138/EnthusiaStaff/blob/main/domain/src/main/java/net/enthusia/staff/domain/ports/AtomicReasonPolicyRepository.java) | Immutable atomically replaceable policy boundary |
| [`VelocityConfiguration.java`](https://github.com/wsg138/EnthusiaStaff/blob/main/velocity/src/main/java/net/enthusia/staff/velocity/VelocityConfiguration.java) | Velocity settings and environment-backed secret references |

The full modular tree below is not yet implemented on `main`.

## Current history and sanction-action settings

The following values are implemented in `paper/src/main/resources/config.yml` and validated as part of the immutable Paper configuration snapshot:

```yaml
history:
  page-size: 8
  include-request-events: true
  include-appeal-events: true
  timezone: UTC
sanction-actions:
  minimum-reason-length: 3
  maximum-reason-length: 500
  allow-permanent-reduction: true
```

`history.page-size` accepts `1` through `100`; timezone must be an IANA zone. Reason limits must be between `1` and `2000`, with maximum at least minimum. Invalid reload candidates leave the previous valid snapshot and command presentation settings active. These settings are reloadable and do not rebuild the MariaDB pool, rerun migrations, reset operational mode, activate authority, discard queued work or duplicate scheduled workers.

## Target layout

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
    ├── hate-harassment-safety.yml
    ├── spam-noise-language.yml
    ├── inappropriate-content.yml
    ├── politics-irl.yml
    ├── account-security.yml
    ├── complicity-evasion.yml
    ├── exploits.yml
    ├── market.yml
    ├── reputation.yml
    ├── mods-clients.yml
    ├── reports-tickets.yml
    └── other-extreme.yml
```

## Design rules

Every modular file should provide:

- comments and examples;
- a configuration version;
- stable IDs and explicit aliases;
- path-aware validation errors;
- immutable runtime models;
- explicit restart-required settings;
- safe defaults that do not enable production authority accidentally.

## Secrets

Keep real secrets outside Git and ordinary Wiki examples:

- MariaDB passwords;
- TLS key/trust-store passwords;
- network-identity encryption/HMAC keys;
- Discord webhook URLs;
- website bridge secrets;
- Cloudflare/email/Turnstile/D1/R2/Hyperdrive credentials;
- private provider credentials.

Use environment variables or an approved secret manager. Configuration should
store the environment-variable **name**, not the secret value.

## Stable IDs and durations

Supported duration examples:

```text
6h
21d
90d
permanent
```

Reason, family, ladder, GUI, server and integration IDs must remain stable.
Display-name changes must not create new policy identities. Removed IDs remain
readable in history but unselectable. Renames require explicit aliases.

## Reload

```text
/estaff reload
```

The target reload transaction is:

1. read every reloadable file into a temporary tree;
2. validate versions, paths, aliases, cross-references, ladders, GUI slots,
   durations, permissions, servers and integrations;
3. reject the entire candidate when any validation fails;
4. leave the current valid model unchanged;
5. atomically publish only a complete valid immutable model;
6. preserve active sanctions, drafts, requests, reports, staff sessions, locks,
   journals and recovery state.

A reload must never partially apply only some files.

Punishment-request alert settings and the history/sanction-action settings above use the validated all-or-nothing reload path.

## Restart-required settings

Examples likely to require restart include:

- database connection pools and credentials;
- TLS key/trust material;
- proxy/backend server identity;
- persistent-channel sockets;
- provider classloading;
- executor capacity and ownership.

Verification should report **RESTART REQUIRED** rather than claiming those
changes applied through reload.

## Operational modes

```text
BOOTSTRAP
DEGRADED
SHADOW_MIGRATION
ACTIVE
MAINTENANCE
READ_ONLY_FAILURE
```

Modes are safety controls, not convenience toggles. A configuration change must
not switch authority merely to make a command available.

## Validation checklist

Before approving a configuration change, confirm:

- IDs are unique and stable;
- aliases resolve exactly once;
- every reason has family, severity, ladder, decay, visibility, reportability,
  rank, automation eligibility, confiscation options and alt inheritance;
- every GUI reference exists and slots do not conflict;
- durations parse and limits are bounded;
- permission nodes and server scopes are known;
- secrets are absent from the diff/logs/errors;
- invalid candidates are rejected atomically;
- restart-only settings are reported clearly;
- active sanctions and stored historical policy remain interpretable.

## Current completion

Modular configuration is roughly **30%** complete and full atomic reload roughly
**40%** complete. The detailed breakdown and primary files are maintained in
[[Core Platform and Infrastructure]].

## Related pages

- [[Core Platform and Infrastructure]]
- [[Commands and Permissions]]
- [[Integrations]]
- [[Build and Testing]]
- [[Recovery and Troubleshooting]]
