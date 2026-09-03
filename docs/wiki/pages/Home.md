# EnthusiaStaff Wiki

EnthusiaStaff is the distributed moderation and staff platform for the Enthusia Network. This Wiki is the human-facing guide to staff procedure, administration, operations, development, review, and recovery.

> **Pre-release boundary:** repository implementation is not permission to replace the current production moderation authority. Check [[Implementation Status]] before treating a documented workflow as deployed or accepted.

## Choose what you are doing

| I am... | Start here | Then continue to... |
| --- | --- | --- |
| **Staff** | [[Staff Handbook]] | [[Moderator-Quick-Start]], [[Punishment System]], [[Reports and Evidence]], [[Discord Moderation Platform]], [[Staff-Mode-Vanish-and-Freeze]] |
| **Administrator** | [[Commands and Permissions]] | [[Rank-Authority]], [[Configuration]], [[Integrations]], [[Discord Moderation Platform]] |
| **Operator** | [[Installation]] | [[Implementation Status]], [[Recovery and Troubleshooting]], [[LiteBans Migration]], [[Shadow Mode and Cutover]] |
| **Developer** | [[Developer Guide Index]] | [[Development Setup]], [[Architecture]], [[Developer Code Guide]], [[Build and Testing]] |
| **Code reviewer** | [[Code Review Guide]] | [[Architecture]], [[Developer Code Guide]], [[Build and Testing]] |
| **Troubleshooter** | [[Recovery and Troubleshooting]] | the affected feature hub, [[Protocol and Network Traffic]], or a focused safety page |

## Quick answers

- **What is actually merged and how proven is it?** [[Implementation Status]]
- **What is planned for Discord moderation/linking/AutoMod?** [[Discord Moderation Platform]]
- **Where does a feature live in source?** [[Developer Code Guide]]
- **What should I verify in a PR?** [[Code Review Guide]]
- **How do I build and validate it?** [[Build and Testing]]
- **How do Paper and Velocity communicate?** [[Protocol and Network Traffic]]
- **How should a failure be recovered?** [[Recovery and Troubleshooting]]
- **How is the Wiki maintained and published?** [[Wiki Maintenance]]

## Feature hubs

Use a feature hub when you need the plain-language purpose, current limitations, important source paths, and links to focused procedures.

- [[Core Platform and Infrastructure]] — runtime artifacts, architecture, MariaDB, protocol, lifecycle, configuration, identity, health.
- [[Moderation, Punishments, and Reports]] — cases, sanctions, punishment workflows, history, reports, evidence, appeals, automod.
- [[Staff Tools, Investigations, and Player-State Safety]] — staff mode, vanish, freeze, inventory, confiscation, economy, alts, inspector, staff tools.
- [[Integrations, Migration, and Release Readiness]] — providers, Discord, website, LiteBans migration, shadow/cutover, acceptance and release evidence.

The hubs intentionally avoid being package-worker logs. Exact orchestration history belongs in `ai-agents/`; the Wiki describes the product and the evidence needed to trust it.

## Information authority

When sources disagree, use this order:

1. [`ENTHUSIASTAFF-GOALS.md`](https://github.com/wsg138/EnthusiaStaff/blob/main/ENTHUSIASTAFF-GOALS.md) for intended finished behavior, with approved focused specifications such as [`docs/discord-moderation-platform.md`](https://github.com/wsg138/EnthusiaStaff/blob/main/docs/discord-moderation-platform.md) defining newly approved extensions until consolidated into the main goals document.
2. Current merged code, configuration, tests, migrations, and runtime evidence for implemented behavior.
3. [Requirements matrix](https://github.com/wsg138/EnthusiaStaff/blob/main/reports/REQUIREMENTS-MATRIX.md) and current legitimate review evidence for exact proof/blockers; reconcile them with live `main` when one has not yet caught up with a recent merge.
4. This Wiki for readable staff, operator, developer, and reviewer guidance.

A class, command, test, or merged PR by itself does not establish production readiness. See [[Build and Testing]] for what each validation layer can and cannot prove.