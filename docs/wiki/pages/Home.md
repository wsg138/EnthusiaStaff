# EnthusiaStaff Wiki

EnthusiaStaff is the Enthusia Network's staff and moderation platform. Choose a guide for your role; start with [[Implementation Status]] when you need to distinguish merged code, reported deployment, and verified operational authority.

> **Deployment is component-specific.** The owner reports installing the Paper 0.1.1 JAR on the live server following [PR #787](https://github.com/wsg138/EnthusiaStaff/pull/787). This is not independent server verification, and does not establish that Velocity, StaffBot, web services, or a replacement moderation authority are live. See [[Implementation Status]].

## Start with your role

| Audience | First step | Next steps |
| --- | --- | --- |
| **Players and staff** | [[Staff Handbook]] and [[Moderator-Quick-Start]] | [[Helper Guide]], [[Reports and Evidence]], [[Punishment System]], [[Staff-Mode-Vanish-and-Freeze]] |
| **Server operators and administrators** | [[Installation]] and [[Configuration]] | [[Commands and Permissions]], [[Rank-Authority]], [[Staff Bot Runtime and Operations]], [[Recovery and Troubleshooting]], [[Shadow Mode and Cutover]] |
| **Contributors and reviewers** | [[Developer Guide Index]] | [[Development Setup]], [[Architecture]], [[Developer Code Guide]], [[Code Review Guide]], [[Build and Testing]] |

The [[_Sidebar]] groups links by audience. A feature page describes behavior and limitations; the developer source guide is the detailed code map.

## Quick answers

- **What is implemented, deployed, or still unverified?** [[Implementation Status]]
- **Which commands and permissions are relevant?** [[Commands and Permissions]] and [[Rank-Authority]]
- **How do staff handle reports and sanctions?** [[Reports and Evidence]] and [[Punishment System]]
- **How does Discord moderation work?** [[Discord Moderation Platform]]
- **How do operators run or recover StaffBot?** [[Staff Bot Runtime and Operations]]
- **What are the website and browser moderation boundaries?** [[Website and Web API]]
- **How do Paper and Velocity exchange messages?** [[Protocol and Network Traffic]]
- **Where does a feature live in the source?** [[Developer Code Guide]]
- **How do I build, test, and review a change?** [[Build and Testing]] and [[Code Review Guide]]
- **How is the Wiki published and restored?** [[Wiki Maintenance]]

## Feature guides

- [[Core Platform and Infrastructure]] — modules, infrastructure, identity, runtime and health.
- [[Moderation, Punishments, and Reports]] — cases, sanctions, reports, evidence and appeals.
- [[Staff Tools, Investigations, and Player-State Safety]] — staff mode, vanish, freeze, inspection, inventory and confiscation.
- [[Integrations, Migration, and Release Readiness]] — providers, Discord, site/API, migration, acceptance and rollback.

Use these hubs for the overview, then follow links to the focused procedure or source trace. Historical worker records are not a substitute for merged-source verification.

## Evidence and authority

[Product goals](https://github.com/wsg138/EnthusiaStaff/blob/main/ENTHUSIASTAFF-GOALS.md) describe intended behavior. Current merged source, configuration, migrations and tests establish implementation; [requirements/evidence](https://github.com/wsg138/EnthusiaStaff/blob/main/reports/REQUIREMENTS-MATRIX.md) and exact-candidate runtime records qualify what is proven. This Wiki summarizes those sources; it does not itself authorize production cutover.
