# Configuration

EnthusiaStaff has several runtime/configuration boundaries now: Paper, Velocity, the standalone StaffBot application, the public site component, and the staging moderation web workspace. Not every setting participates in one hot-reloadable tree.

- Core/runtime status: [[Implementation Status]]
- Discord/StaffBot behavior: [[Discord Moderation Platform]] and [[Staff Bot Runtime and Operations]]
- Website/web boundaries: [[Website and Web API]]
- Commands/permissions: [[Commands and Permissions]]
- Provider settings: [[Integrations]]
- Report-specific configuration: [[Report Configuration]]
- Validation/reload evidence: [[Build and Testing]]

## Current configuration sources

| File/class/area | Current purpose |
| --- | --- |
| [`paper/src/main/resources/config.yml`](https://github.com/wsg138/EnthusiaStaff/blob/main/paper/src/main/resources/config.yml) | Paper runtime/staff-tool controls |
| [`paper/src/main/resources/reason-policies.yml`](https://github.com/wsg138/EnthusiaStaff/blob/main/paper/src/main/resources/reason-policies.yml) | reason/family/ladder/decay/compatibility policy |
| [`paper/src/main/resources/reports.yml`](https://github.com/wsg138/EnthusiaStaff/blob/main/paper/src/main/resources/reports.yml) | report submission/evidence/retention policy |
| [`paper/src/main/resources/gui/reports.yml`](https://github.com/wsg138/EnthusiaStaff/blob/main/paper/src/main/resources/gui/reports.yml) | report queue/detail GUI presentation |
| [`paper/src/main/resources/plugin.yml`](https://github.com/wsg138/EnthusiaStaff/blob/main/paper/src/main/resources/plugin.yml) | commands/permissions/soft dependencies |
| [`VelocityConfiguration.java`](https://github.com/wsg138/EnthusiaStaff/blob/main/velocity/src/main/java/net/enthusia/staff/velocity/VelocityConfiguration.java) | proxy/network/legacy Discord delivery/website API and secret references |
| [`StaffBotConfiguration.java`](https://github.com/wsg138/EnthusiaStaff/blob/main/staff-bot/src/main/java/net/enthusia/staff/discordbot/StaffBotConfiguration.java) | StaffBot environment, Discord identity, workers, health, moderation/read/punishment/private-service settings |
| [`staff-bot/README.md`](https://github.com/wsg138/EnthusiaStaff/blob/main/staff-bot/README.md) | supported StaffBot startup/configuration/recovery contract |
| [`moderation-web/`](https://github.com/wsg138/EnthusiaStaff/tree/main/moderation-web) | staging Cloudflare workspace config/build/deploy inputs |
| [`components/enthusia-site/`](https://github.com/wsg138/EnthusiaStaff/tree/main/components/enthusia-site) | public site/Pages Functions component configuration |

The complete target modular configuration tree from the long-term goals is still not implemented as one atomic all-file reload boundary.

## StaffBot configuration

StaffBot configuration covers, at a high level:

- staging/production environment identity;
- Discord application/guild/channel fences;
- MariaDB;
- bounded worker queues;
- loopback/private health endpoint;
- moderation/read presentation and behavior;
- Discord punishment/reconciliation behavior;
- private moderation-read/authority service connectivity;
- signing/authentication configuration for protected service boundaries.

Do not place real values for tokens, passwords, private origins, signing material, or production IDs in Wiki examples.

### File-backed staging startup

The merged runtime supports the staging-only pair:

```text
--token-file=<secret-mounted token file>
--moderation-config-file=<secret-mounted properties file>
```

Both must be supplied together for that mode. Partial/mixed staging configuration must fail closed, and the file-backed staging path must not be used to bypass production identity/configuration safety.

### Discord enforcement gate

Destructive Discord enforcement is intentionally explicit and defaults disabled. Configuration must never make a routine restart/reload accidentally activate production moderation authority.

See [[Staff Bot Runtime and Operations]].

## Website/API configuration

The Velocity website API and Cloudflare/browser surfaces have different configuration/security boundaries.

- Velocity owns authoritative API bind/authentication/route behavior.
- The public site uses Cloudflare Pages Functions/server-side mediation.
- The staging moderation workspace uses Cloudflare Worker/static assets plus signed launch/session/read configuration.
- StaffBot owns the private read API and launch issuer used by that staging workspace.

Do not collapse these into one shared secret or one public endpoint. See [[Website and Web API]].

## Reason IDs, aliases and removed reasons

Reason IDs are durable identities, not display strings. Aliases map historical IDs directly to active canonical reasons; chains/cycles/self-targets/unknown targets/duplicate IDs are invalid. Removed reasons retain presentation/history metadata but are not selectable for new punishments.

Existing cases/sanctions are not rewritten when a reason is renamed/retired.

## Current history/report/staff-tool settings

Current Paper configuration includes bounded history/exact-sanction presentation, mutation reason limits, report policy/GUI configuration, and Staff Mode/tool controls.

A rejected reload candidate must leave the previous valid snapshot active and must not rebuild MariaDB, rerun migrations, reset operational authority, discard durable work, or duplicate workers.

## Secrets and sensitive configuration

Keep real secrets out of Git, Wiki examples, issue/PR comments and ordinary logs, including:

- MariaDB credentials;
- TLS/HMAC/encryption/signing keys;
- Discord bot tokens/webhook URLs;
- website/private API credentials;
- Cloudflare credentials;
- private service origins/topology details;
- provider credentials.

The raw Discord bot token must not be copied into browser/Cloudflare configuration. The current staging web bootstrap derives domain-separated signing material rather than publishing the token; dedicated independent production signing secrets remain the stronger long-term design.

## Reload model

For reloadable configuration:

1. parse a complete candidate separately from live state;
2. validate versions/IDs/aliases/references/ranges/permissions/server scopes;
3. reject the affected candidate on any invalid dependency;
4. leave the prior valid snapshot active on failure;
5. atomically publish only a fully valid replacement;
6. preserve durable in-flight workflows and owned runtime resources.

Some resources are restart-owned rather than hot-reloadable.

## Restart-owned boundaries

Typical restart-owned resources include:

- database pools/credentials;
- TLS/signing material where the runtime contract requires startup ownership;
- persistent transport/Gateway sessions;
- provider classloading/service discovery;
- executor capacity;
- service listeners/bind addresses;
- StaffBot application identity and certain environment fences.

Report **RESTART REQUIRED** where appropriate instead of claiming a live resource changed when it did not.

## Operational/authority modes

Operational modes and separate authority/enforcement gates are safety state, not convenience toggles. A configuration problem must not be “fixed” by enabling authority merely to make a command or external effect work.

## Review checklist

Before approving configuration changes verify:

- IDs/references are stable, unique and validated together;
- invalid candidates retain the old live snapshot;
- restart-owned resources are reported honestly;
- secrets/private topology cannot appear in source/log/error output;
- StaffBot environment/application/guild fences fail closed;
- destructive Discord enforcement cannot be enabled accidentally;
- public/private website endpoints are not collapsed;
- active durable workflows remain interpretable across reload/restart;
- tests cover invalid/rollback behavior, not only happy-path parsing;
- runtime claims have the appropriate staging evidence.

## Current state

Configuration/reload remains **partial** as a single platform-wide system. Several strong validated configuration boundaries exist, but Paper, Velocity, StaffBot and web components intentionally have different lifecycle/secret/deployment ownership.

## Related pages

- [[Core Platform and Infrastructure]]
- [[Discord Moderation Platform]]
- [[Staff Bot Runtime and Operations]]
- [[Website and Web API]]
- [[Report Configuration]]
- [[Integrations]]
- [[Code Review Guide]]
- [[Build and Testing]]
- [[Recovery and Troubleshooting]]