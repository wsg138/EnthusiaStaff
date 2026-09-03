# Developer Guide Index

Start here when you need to change, review, debug, or validate EnthusiaStaff. This page stays intentionally shallow: choose the task, get the answer you need, then follow the link into deeper source or evidence detail.

## Where do I start?

| I need to... | Start with | Go deeper when needed |
| --- | --- | --- |
| Understand what is merged versus still incomplete | [[Implementation Status]] | matching feature hub, requirements/review evidence |
| Review a pull request or commit | [[Code Review Guide]] | [[Developer Code Guide]], [[Architecture]], [[Build and Testing]] |
| Set up the repository | [[Development Setup]] | [[Build and Testing]] |
| Understand the system shape | [[Architecture]] | [`docs/architecture.md`](https://github.com/wsg138/EnthusiaStaff/blob/main/docs/architecture.md), [[Developer Code Guide]] |
| Find the class/store/test that owns a feature | matching feature hub | [[Developer Code Guide]] |
| Trace one request end to end | [[Developer Code Guide]] | focused deep dive and tests |
| Review Paper/Folia player-state code | [[Code Review Guide]] | [[Staff Tools, Investigations, and Player-State Safety]], [[Cheat Tester]], [[Vanish Internals]] |
| Understand Paper/Velocity transport | [[Protocol and Network Traffic]] | protocol/persistence source and network tests |
| Understand Discord foundations versus future runtime | [[Discord Moderation Platform]] | [[Developer Code Guide]], [[Rank Authority]], `docs/discord-authorization.md` |
| Review current webhook delivery/privacy | [[Discord Delivery]] | [[Protocol and Network Traffic]], [[Code Review Guide]] |
| Review vanish/session scheduling deeply | [[Vanish Internals]] | Paper visibility/staff source and runtime staging |
| Build or prove a change | [[Build and Testing]] | exact workflow evidence for the reviewed SHA |
| Diagnose a runtime failure | [[Recovery and Troubleshooting]] | matching feature hub, source map, logs/evidence |
| Understand remaining product work | [[Development-Blueprint]] | goals, requirements matrix, explicitly assigned orchestration records |
| Change or publish Wiki documentation | [[Wiki Maintenance]] | repository Wiki README and validation workflow |

## Feature ownership

The feature hubs answer **what owns this behavior, what is merged, where are the important files, and what limitations remain?**

| Feature group | Main subjects |
| --- | --- |
| [[Core Platform and Infrastructure]] | Builds, module boundaries, Paper/Velocity lifecycle, MariaDB, protocol, operational modes, configuration, identity and health. |
| [[Moderation, Punishments, and Reports]] | Cases, sanctions, punishment flows, requests, escalation, history, appeals, reports, evidence and automod. |
| [[Staff Tools, Investigations, and Player-State Safety]] | Staff mode, hotbar/tools, Cheat Tester, vanish, freeze, inventory, confiscation, economy, alts and inspector. |
| [[Integrations, Migration, and Release Readiness]] | Provider contracts, Discord, website, LiteBans migration/shadow/cutover, client/topology acceptance and release evidence. |

Use the hub to find the feature, then use [[Developer Code Guide]] for the detailed trace. Do not turn this index into a duplicate source map.

## Focused deep dives

Use these when the general source map is not enough:

- [[Code Review Guide]] — cross-cutting reviewer checklist and evidence discipline.
- [[Protocol and Network Traffic]] — authentication, replay, ACKs and at-least-once delivery.
- [[Discord Moderation Platform]] — merged identity/persistence/authorization foundations versus unmerged bot/link/enforcement runtime.
- [[Discord Delivery]] — current webhook outbox, renderer, retries and privacy boundary.
- [[Cheat Tester]] — tester state, V18 recovery journal, fake entities and fake bases.
- [[Vanish Internals]] — session fencing, rank reconciliation, scheduler and packet behavior.
- [[Inventory and Confiscation Safety]] — destructive player-state invariants and recovery.
- [[Recovery and Troubleshooting]] — runtime failure handling and safe evidence collection.

## How source is organized

```text
common/                shared identifiers, validation, security and bounded utilities
domain/                business policy, authorization, state machines and ports
integration-contracts/ supported compile-time contracts for Enthusia-owned providers
persistence/           MariaDB/Flyway/JDBC stores, leases, journals, inboxes/outboxes
protocol/              authenticated Paper-Velocity transport
paper/                 commands, GUIs, listeners and server-local player state
velocity/              proxy enforcement, network identity, workers, migration/site bridge
integration-tests/     MariaDB/cross-module/recovery tests; never deployed
```

Current merged `main` has two Minecraft runtime artifacts, Paper and Velocity. Domain/schema foundations for a future Discord staff bot do not create another deployed runtime by themselves.

The core rule is: **domain policy owns the decision; platform code owns translation and runtime effects; persistence owns durable implementation of domain ports.** See [[Architecture]] and [[Code Review Guide]] for the boundary rules.

## Common composition roots

- [Paper plugin](https://github.com/wsg138/EnthusiaStaff/blob/main/paper/src/main/java/net/enthusia/staff/paper/EnthusiaStaffPaperPlugin.java)
- [Paper runtime lifecycle](https://github.com/wsg138/EnthusiaStaff/blob/main/paper/src/main/java/net/enthusia/staff/paper/PaperRuntimeLifecycle.java)
- [Paper runtime components](https://github.com/wsg138/EnthusiaStaff/blob/main/paper/src/main/java/net/enthusia/staff/paper/PaperRuntimeComponents.java)
- [Paper storage bindings](https://github.com/wsg138/EnthusiaStaff/blob/main/paper/src/main/java/net/enthusia/staff/paper/PaperStorageBindings.java)
- [Paper commands](https://github.com/wsg138/EnthusiaStaff/tree/main/paper/src/main/java/net/enthusia/staff/paper/command)
- [Velocity plugin](https://github.com/wsg138/EnthusiaStaff/blob/main/velocity/src/main/java/net/enthusia/staff/velocity/EnthusiaStaffVelocityPlugin.java)
- [Domain application services](https://github.com/wsg138/EnthusiaStaff/tree/main/domain/src/main/java/net/enthusia/staff/domain/application)
- [Domain authorization](https://github.com/wsg138/EnthusiaStaff/tree/main/domain/src/main/java/net/enthusia/staff/domain/auth)
- [Persistence stores](https://github.com/wsg138/EnthusiaStaff/tree/main/persistence/src/main/java/net/enthusia/staff/persistence)
- [Flyway migrations](https://github.com/wsg138/EnthusiaStaff/tree/main/persistence/src/main/resources/db/migration)
- [Integration tests](https://github.com/wsg138/EnthusiaStaff/tree/main/integration-tests/src/test/java)

## Before changing a feature

Answer these questions first:

1. What finished behavior does [`ENTHUSIASTAFF-GOALS.md`](https://github.com/wsg138/EnthusiaStaff/blob/main/ENTHUSIASTAFF-GOALS.md) require?
2. What does current merged code actually do?
3. Which domain service/policy owns the decision?
4. Which port/store/table/migration owns durable state?
5. Which Paper, Velocity, website, Discord, or provider adapter performs the runtime effect?
6. Which tests prove pure policy, MariaDB behavior, concurrency, or recovery?
7. Which runtime/staging claim still cannot be proven by those tests?
8. Which staff/operator/Wiki page owns the human-facing behavior?

If the answer to #3 is “the command/GUI/listener contains its own copy,” stop and review the architecture boundary before adding more logic.

## Review path

For a disciplined review:

1. [[Code Review Guide]] for invariants and failure modes.
2. [[Architecture]] for module/runtime ownership.
3. matching feature hub for merged state and primary paths.
4. [[Developer Code Guide]] for the detailed end-to-end trace.
5. [[Build and Testing]] for what the available evidence actually proves.
6. focused deep dives for the risk area, such as [[Protocol and Network Traffic]], [[Discord Moderation Platform]], [[Cheat Tester]], [[Vanish Internals]], [[Inventory and Confiscation Safety]], or [[Recovery and Troubleshooting]].

## Source-of-truth discipline

- Intended finished behavior: [`ENTHUSIASTAFF-GOALS.md`](https://github.com/wsg138/EnthusiaStaff/blob/main/ENTHUSIASTAFF-GOALS.md).
- Implemented behavior: current merged code, config, migrations and tests.
- Exact proof/blockers: [requirements matrix](https://github.com/wsg138/EnthusiaStaff/blob/main/reports/REQUIREMENTS-MATRIX.md) plus current legitimate PR/workflow/runtime evidence. Reconcile with live `main` after recent merges.
- Human guidance: this Wiki.
- Work orchestration/history: `ai-agents/`; do not copy transient worker/package state into general product pages.

## Related pages

- [[Code Review Guide]]
- [[Development Setup]]
- [[Build and Testing]]
- [[Architecture]]
- [[Developer Code Guide]]
- [[Recovery and Troubleshooting]]
- [[Wiki Maintenance]]