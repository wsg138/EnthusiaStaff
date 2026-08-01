# EnthusiaStaff Wiki

EnthusiaStaff is the distributed moderation and staff platform being built for
the Enthusia Network. This Wiki contains staff procedures, administrator
references, implementation status, source maps and migration/recovery runbooks.

> **Pre-release:** EnthusiaStaff is not approved to replace LiteBans or the
> existing production staff stack. A documented feature may describe intended or
> partially tested behavior that is not deployed on the live server.

## Start here

Choose the path that matches what you are doing:

### Staff member

1. [[Staff Handbook]] — conduct, judgment, evidence and privacy.
2. [[Staff Quick Start|Moderator-Quick-Start]] — ordinary reports, punishments and investigation tools.
3. Open the focused procedure you need:
   - [[Punishment System]]
   - [[Reports and Evidence]]
   - [[Staff Mode, Vanish, and Freeze|Staff-Mode-Vanish-and-Freeze]]
   - [[Inventory and Confiscation Safety]]
   - [[Alt Investigations]]
   - [[Incident Playbooks]]

### Administrator or operator

1. [[Feature Completion Status|Implementation-Status]] — what exists and what remains.
2. [[Commands and Permissions]] — registered commands and permission nodes.
3. [[Roles and Permissions|Rank-Authority]] — rank boundaries and aggregate permissions.
4. [[Configuration]] and [[Integrations]] — runtime settings and dependencies.
5. [[Installation]], [[Recovery and Troubleshooting]], [[LiteBans Migration]], and
   [[Shadow Mode and Cutover]] — staging and release operations.

### Developer or reviewer

1. [[Developer Guide Index]] — choose the correct technical document by task.
2. [[Architecture]] — module boundaries and runtime ownership.
3. [[Developer Code Guide]] — files, services, stores and end-to-end feature traces.
4. [[Build and Testing]] — exact validation and evidence expectations.
5. [[Remaining Development Map|Development-Blueprint]] — current cross-feature development order.

## Find information by task

| I need to... | Open this page |
| --- | --- |
| See how complete a feature is | [Feature Completion Status](https://github.com/wsg138/EnthusiaStaff/wiki/Implementation-Status) |
| Understand what a feature group does and find its source files | [Core platform](https://github.com/wsg138/EnthusiaStaff/wiki/Core-Platform-and-Infrastructure), [moderation](https://github.com/wsg138/EnthusiaStaff/wiki/Moderation,-Punishments,-and-Reports), [staff tools](https://github.com/wsg138/EnthusiaStaff/wiki/Staff-Tools,-Investigations,-and-Player-State-Safety), or [integrations/release](https://github.com/wsg138/EnthusiaStaff/wiki/Integrations,-Migration,-and-Release-Readiness) |
| Handle a player report | [Reports and Evidence](https://github.com/wsg138/EnthusiaStaff/wiki/Reports-and-Evidence) |
| Apply or correct a punishment | [Punishment System](https://github.com/wsg138/EnthusiaStaff/wiki/Punishment-System) |
| Use staff mode, vanish or freeze | [Staff Mode, Vanish, and Freeze](https://github.com/wsg138/EnthusiaStaff/wiki/Staff-Mode-Vanish-and-Freeze) |
| View, edit, confiscate or restore assets | [Inventory and Confiscation Safety](https://github.com/wsg138/EnthusiaStaff/wiki/Inventory-and-Confiscation-Safety) |
| Investigate related accounts | [Alt Investigations](https://github.com/wsg138/EnthusiaStaff/wiki/Alt-Investigations) |
| Check a command or permission | [Commands and Permissions](https://github.com/wsg138/EnthusiaStaff/wiki/Commands-and-Permissions) |
| Understand rank authority | [Roles and Permissions](https://github.com/wsg138/EnthusiaStaff/wiki/Rank-Authority) |
| Install or stage the plugins | [Installation](https://github.com/wsg138/EnthusiaStaff/wiki/Installation) |
| Diagnose a failure or recovery state | [Recovery and Troubleshooting](https://github.com/wsg138/EnthusiaStaff/wiki/Recovery-and-Troubleshooting) |
| Prepare or review LiteBans migration | [LiteBans Migration](https://github.com/wsg138/EnthusiaStaff/wiki/LiteBans-Migration) |
| Understand shadow mode or cutover | [Shadow Mode and Cutover](https://github.com/wsg138/EnthusiaStaff/wiki/Shadow-Mode-and-Cutover) |
| Find the class or store that owns a feature | [Developer Code Guide](https://github.com/wsg138/EnthusiaStaff/wiki/Developer-Code-Guide) |
| Understand Paper–Velocity traffic | [Protocol and Network Traffic](https://github.com/wsg138/EnthusiaStaff/wiki/Protocol-and-Network-Traffic) |
| Build and validate a change | [Build and Testing](https://github.com/wsg138/EnthusiaStaff/wiki/Build-and-Testing) |
| Update or publish the Wiki | [Wiki Maintenance](https://github.com/wsg138/EnthusiaStaff/wiki/Wiki-Maintenance) |

## Feature groups

The implementation is organized into four broad groups. Each group page contains
specific categories, percentages, plain-language descriptions, commands/runtime
ownership, direct source-file links and remaining work.

| Group | Completion | Main subjects |
| --- | ---: | --- |
| [[Core Platform and Infrastructure]] | **About 72%** | Runtime jars, architecture, lifecycle, MariaDB, protocol, modes, configuration, identity and CI. |
| [[Moderation, Punishments, and Reports]] | **About 56%** | Cases, sanctions, GUI/drafts, approvals, escalation, history, reports, evidence and automod. |
| [[Staff Tools, Investigations, and Player-State Safety]] | **About 44%** | Staff mode, vanish, freeze, inventory, confiscation, economy, alts, inspector and fake systems. |
| [[Integrations, Migration, and Release Readiness]] | **About 36%** | Discord, website, providers, LiteBans migration, shadow/cutover, platform acceptance and release evidence. |

## Current project status

Substantial tested foundations exist, including:

- exactly two Java 21 runtime jars;
- MariaDB-backed durable moderation state;
- authenticated Paper–Velocity transport and replay protection;
- punishment drafts and approval-request foundations;
- report persistence, concurrency and retention behavior;
- vanish visibility/recovery foundations;
- a restricted website bridge; and
- LiteBans schema/import/shadow comparison tooling.

Major user-facing, provider, recovery, client and release-acceptance work remains.
Use [[Feature Completion Status|Implementation-Status]] for the current readable
breakdown and the
[requirements matrix](https://github.com/wsg138/EnthusiaStaff/blob/main/reports/REQUIREMENTS-MATRIX.md)
for exact evidence.

## Documentation map

| Information | Primary location |
| --- | --- |
| Intended finished behavior | [ENTHUSIASTAFF-GOALS.md](https://github.com/wsg138/EnthusiaStaff/blob/main/ENTHUSIASTAFF-GOALS.md) |
| Exact implementation/test/blocker evidence | [Requirements matrix](https://github.com/wsg138/EnthusiaStaff/blob/main/reports/REQUIREMENTS-MATRIX.md) |
| Readable percentages and feature directory | [[Feature Completion Status|Implementation-Status]] |
| Feature descriptions and important source files | The four feature-group pages |
| Cross-feature development order | [[Remaining Development Map|Development-Blueprint]] |
| Complete repository map and traces | [[Developer Code Guide]] |
| Validation procedure | [[Build and Testing]] |
| Staff behavior | Focused staff guides |
| Deployment, migration and recovery | Focused operator runbooks |

Keeping each subject in one primary location makes links useful and avoids
copying the same roadmap, command table or source map across several pages.
