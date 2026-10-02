# Developer Guide Index

Start here when you need to change, review, debug, or validate EnthusiaStaff. This page stays intentionally shallow: choose the task, get the answer you need, then follow the focused page into deeper source/evidence detail.

## Where do I start?

| I need to... | Start with | Go deeper when needed |
| --- | --- | --- |
| Understand what is merged versus incomplete | [[Implementation Status]] | matching feature hub and focused deep dive |
| Review a PR/commit | [[Code Review Guide]] | [[Architecture]], [[Developer Code Guide]], [[Build and Testing]] |
| Set up the repository | [[Development Setup]] | [[Build and Testing]] |
| Understand the system shape | [[Architecture]] | [[Developer Code Guide]] |
| Find the class/store/test that owns a feature | matching feature hub | [[Developer Code Guide]] |
| Work on Discord/StaffBot | [[Discord Moderation Platform]] | [[Staff Bot Runtime and Operations]], StaffBot source/tests |
| Build/deploy/recover StaffBot | [[Staff Bot Runtime and Operations]] | [[Build and Testing]], [[Recovery and Troubleshooting]] |
| Work on website/public API/appeals | [[Website and Web API]] | Velocity website source, site component/tests |
| Work on the staging browser moderation UI | [[Website and Web API]] | `moderation-web/`, StaffBot moderation-read API |
| Work on managed Discord roles | [[Discord Moderation Platform]] | `discord-platform-api/`, provider/consumer work |
| Review Paper/Folia player-state code | [[Code Review Guide]] | [[Staff Tools, Investigations, and Player-State Safety]], [[Cheat Tester]], [[Vanish Internals]] |
| Understand Paper/Velocity transport | [[Protocol and Network Traffic]] | protocol/persistence source and network tests |
| Review legacy webhook delivery | [[Discord Delivery]] | [[Protocol and Network Traffic]], [[Code Review Guide]] |
| Build/prove a change | [[Build and Testing]] | exact workflow/runtime evidence for the reviewed SHA |
| Diagnose a runtime failure | [[Recovery and Troubleshooting]] | focused runtime/feature page |
| Understand remaining product work | [[Development-Blueprint]] | goals + live GitHub for active development |
| Change/publish Wiki documentation | [[Wiki Maintenance]] | repository Wiki README and validation workflow |

## Feature ownership

| Feature group | Main subjects |
| --- | --- |
| [[Core Platform and Infrastructure]] | Module/runtime boundaries, Paper/Velocity lifecycle, MariaDB, protocol, configuration, identity and health. |
| [[Moderation, Punishments, and Reports]] | Cases, sanctions, punishment flows, requests, escalation, history, appeals, reports, evidence and automod. |
| [[Staff Tools, Investigations, and Player-State Safety]] | Staff mode, tools, Cheat Tester, vanish, freeze, inventory, confiscation, economy, alts and inspector. |
| [[Integrations, Migration, and Release Readiness]] | Provider contracts, Discord/StaffBot, website/web APIs, migration/shadow/cutover, client/topology acceptance and release evidence. |

Use the hub to find the feature, then use the focused deep dive/source map. Do not turn this index into a duplicate source guide.

## Focused deep dives

- [[Code Review Guide]] — cross-cutting reviewer checklist/evidence discipline.
- [[Discord Moderation Platform]] — current StaffBot/linking/enforcement/managed-role product status and Discord safety rules.
- [[Staff Bot Runtime and Operations]] — standalone Java/JDA runtime build/config/deploy/recovery.
- [[Website and Web API]] — public site, Velocity website API, staging moderation web and StaffBot read bridge.
- [[Discord Delivery]] — legacy webhook outbox/delivery boundary.
- [[Protocol and Network Traffic]] — Paper/Velocity authentication, replay, ACK and delivery.
- [[Cheat Tester]] — tester state/recovery/fake systems.
- [[Vanish Internals]] — session fencing, scheduler and visibility behavior.
- [[Inventory and Confiscation Safety]] — destructive player-state invariants/recovery.
- [[Recovery and Troubleshooting]] — runtime failure handling and safe evidence collection.

## Repository shape

```text
common/                shared identifiers, validation, security, bounded utilities
domain/                business policy, authorization, state machines and ports
integration-contracts/ supported compile-time contracts for Enthusia-owned providers
discord-platform-api/  provider-neutral managed-role API
persistence/           MariaDB/Flyway/JDBC stores, leases, journals, inboxes/outboxes
protocol/              authenticated Paper-Velocity transport
paper/                 commands, GUIs, listeners and server-local player state
paper-authority-bridge/ narrow private bridge for authorized Minecraft-side operations
velocity/              proxy enforcement, transport workers, website API, migration
staff-bot/             standalone Java/JDA Discord runtime and private read/launch services
integration-tests/     MariaDB/cross-module/recovery tests; never deployed
moderation-web/         staging-only Cloudflare staff moderation workspace
components/enthusia-site/ public site + Cloudflare Pages Functions component
```

Current merged `main` therefore has more than the original two Minecraft plugin artifacts: Paper and Velocity remain Minecraft runtimes, while StaffBot is a separate executable application and the web components have their own deployment models.

The core rule is: **domain policy owns the decision; platform code owns translation/runtime effects; persistence owns durable implementation; public/browser surfaces receive only explicitly approved projections.**

## Common composition roots

- [Paper plugin](https://github.com/wsg138/EnthusiaStaff/blob/main/paper/src/main/java/net/enthusia/staff/paper/EnthusiaStaffPaperPlugin.java)
- [Velocity plugin](https://github.com/wsg138/EnthusiaStaff/blob/main/velocity/src/main/java/net/enthusia/staff/velocity/EnthusiaStaffVelocityPlugin.java)
- [StaffBot application](https://github.com/wsg138/EnthusiaStaff/blob/main/staff-bot/src/main/java/net/enthusia/staff/discordbot/StaffBotApplication.java)
- [StaffBot runtime](https://github.com/wsg138/EnthusiaStaff/blob/main/staff-bot/src/main/java/net/enthusia/staff/discordbot/StaffBotRuntime.java)
- [Velocity website router](https://github.com/wsg138/EnthusiaStaff/blob/main/velocity/src/main/java/net/enthusia/staff/velocity/WebsiteApiRouter.java)
- [Discord platform API](https://github.com/wsg138/EnthusiaStaff/tree/main/discord-platform-api)
- [Domain application services](https://github.com/wsg138/EnthusiaStaff/tree/main/domain/src/main/java/net/enthusia/staff/domain/application)
- [Domain authorization](https://github.com/wsg138/EnthusiaStaff/tree/main/domain/src/main/java/net/enthusia/staff/domain/auth)
- [Persistence stores](https://github.com/wsg138/EnthusiaStaff/tree/main/persistence/src/main/java/net/enthusia/staff/persistence)
- [Flyway migrations](https://github.com/wsg138/EnthusiaStaff/tree/main/persistence/src/main/resources/db/migration)
- [Public site component](https://github.com/wsg138/EnthusiaStaff/tree/main/components/enthusia-site)
- [Moderation web workspace](https://github.com/wsg138/EnthusiaStaff/tree/main/moderation-web)
- [Integration tests](https://github.com/wsg138/EnthusiaStaff/tree/main/integration-tests/src/test/java)

## Before changing a feature

Answer these questions first:

1. What finished behavior do the authoritative goals/focused specifications require?
2. What does current merged code actually do?
3. Which domain service/policy owns the decision?
4. Which port/store/table/migration owns durable state?
5. Which Paper, Velocity, StaffBot, web, or provider adapter performs the runtime effect?
6. Which tests prove pure policy, MariaDB behavior, concurrency or recovery?
7. Which runtime/staging/production claim remains unproved?
8. Which staff/operator/Wiki page owns the human-facing behavior?

For Discord/web changes also ask: **is this public data, privileged staff data, or a destructive authority path?** Do not let a browser, Discord role, or integration route silently widen that boundary.

## Review path

1. [[Code Review Guide]] for invariants/failure modes.
2. [[Architecture]] for module/runtime ownership.
3. Matching feature/focused page for merged state and primary paths.
4. [[Developer Code Guide]] for the detailed trace where needed.
5. [[Build and Testing]] for evidence interpretation.
6. Focused pages such as [[Discord Moderation Platform]], [[Staff Bot Runtime and Operations]], [[Website and Web API]], [[Protocol and Network Traffic]], [[Vanish Internals]], or [[Inventory and Confiscation Safety]].

## Source-of-truth discipline

- Intended finished behavior: [`ENTHUSIASTAFF-GOALS.md`](https://github.com/wsg138/EnthusiaStaff/blob/main/ENTHUSIASTAFF-GOALS.md) plus approved focused specifications.
- Implemented behavior: current merged code, config, migrations and tests.
- Exact proof/blockers: current legitimate PR/workflow/runtime evidence, reconciled with live `main`.
- Human guidance: this Wiki.
- Work orchestration/history: `ai-agents/`; do not copy transient worker/package state into general product pages.

## Related pages

- [[Code Review Guide]]
- [[Architecture]]
- [[Discord Moderation Platform]]
- [[Staff Bot Runtime and Operations]]
- [[Website and Web API]]
- [[Build and Testing]]
- [[Developer Code Guide]]
- [[Recovery and Troubleshooting]]
- [[Wiki Maintenance]]