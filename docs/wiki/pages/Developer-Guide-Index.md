# Developer Guide Index

Use this page to move from a feature question to the correct planning page,
source map, validation guide or focused technical explanation.

## Start by task

- **Understand what a feature does, its percentage, important files and remaining work:**
  choose one of the four [[Feature Completion Status|Implementation-Status]] hubs.
- **See what development should happen next:**
  [[Remaining Development Map|Development-Blueprint]]
- **Find the complete repository map and end-to-end feature traces:**
  [[Developer Code Guide]]
- **Understand module boundaries and dependency direction:** [[Architecture]]
- **Set up a development environment:** [[Development Setup]]
- **Build, test and validate a change:** [[Build and Testing]]
- **Understand Paper–Velocity traffic and ReplayGuard:**
  [[Protocol and Network Traffic]]
- **Review vanish events, packets and visibility gaps:** [[Vanish Internals]]
- **Update or publish documentation:** [[Wiki Maintenance]]

## Feature-to-code navigation

Choose the feature group first. Each page explains the feature in plain language,
then links directly to its commands, managers, domain services, JDBC stores,
integration boundaries and related Wiki pages.

| Feature group | Use it for |
| --- | --- |
| [[Core Platform and Infrastructure]] | Builds, modules, Paper/Velocity lifecycle, MariaDB, protocol, safe-write controls, modes, configuration, identity and CI. |
| [[Moderation, Punishments, and Reports]] | Cases, sanctions, punishment GUI/drafts, requests, escalation, history, appeals, reports, evidence and automod. |
| [[Staff Tools, Investigations, and Player-State Safety]] | Staff mode, vanish, freeze, inventory, confiscation, economy, alts, inspector, testers and fake systems. |
| [[Integrations, Migration, and Release Readiness]] | Discord, website, providers, LiteBans migration, shadow/cutover, platform acceptance and release evidence. |

After finding the important files on the group page, continue to
[[Developer Code Guide]] for the complete request path and review checklist.

## Core developer documents

### Planning and status

- [[Feature Completion Status|Implementation-Status]] — the four-group directory.
- The four feature-group pages — detailed categories, percentages, descriptions,
  source links and remaining work.
- [[Remaining Development Map|Development-Blueprint]] — cross-group execution order.
- [Requirements matrix](https://github.com/wsg138/EnthusiaStaff/blob/main/reports/REQUIREMENTS-MATRIX.md)
  — exact evidence, files, tests and blockers.

### Code and architecture

- [[Architecture]] — system shape, dependency direction and runtime ownership.
- [[Developer Code Guide]] — repository map, composition roots, stores, feature
  traces, threading rules, tests and high-risk review areas.
- [[Protocol and Network Traffic]] — persistent channel, authentication,
  replay protection, acknowledgements and outbound traffic.
- [[Vanish Internals]] — events, Paper/packet visibility layers, scheduling and gaps.

### Working on the repository

- [[Development Setup]] — prerequisites and local setup.
- [[Build and Testing]] — focused tests, full validation, MariaDB, runtime jars,
  coverage, Codacy and staging evidence.
- [[Wiki Maintenance]] — documentation ownership, validation and publication.

## Recommended review path

1. Read the exact requirement in
   [ENTHUSIASTAFF-GOALS.md](https://github.com/wsg138/EnthusiaStaff/blob/main/ENTHUSIASTAFF-GOALS.md).
2. Open the matching feature-group page for purpose, status and important files.
3. Read the exact row in the
   [requirements matrix](https://github.com/wsg138/EnthusiaStaff/blob/main/reports/REQUIREMENTS-MATRIX.md).
4. Follow the end-to-end trace in [[Developer Code Guide]].
5. Inspect the domain policy, persistence adapter, platform adapter and tests.
6. Identify the real staging requirement that mocks cannot prove.
7. Update the matrix and group page when the exact reviewed revision changes status.

## Common source entry points

- [Paper plugin entrypoint](https://github.com/wsg138/EnthusiaStaff/blob/main/paper/src/main/java/net/enthusia/staff/paper/EnthusiaStaffPaperPlugin.java)
- [Velocity plugin entrypoint](https://github.com/wsg138/EnthusiaStaff/blob/main/velocity/src/main/java/net/enthusia/staff/velocity/EnthusiaStaffVelocityPlugin.java)
- [Paper command package](https://github.com/wsg138/EnthusiaStaff/tree/main/paper/src/main/java/net/enthusia/staff/paper/command)
- [Domain application services](https://github.com/wsg138/EnthusiaStaff/tree/main/domain/src/main/java/net/enthusia/staff/domain/application)
- [Persistence stores](https://github.com/wsg138/EnthusiaStaff/tree/main/persistence/src/main/java/net/enthusia/staff/persistence)
- [Flyway migrations](https://github.com/wsg138/EnthusiaStaff/tree/main/persistence/src/main/resources/db/migration)
- [Integration tests](https://github.com/wsg138/EnthusiaStaff/tree/main/integration-tests/src/test/java)

## Documentation rule

Keep one primary owner for each type of information:

- finished behavior: goals;
- exact proof: requirements matrix;
- readable percentage and file entry points: feature-group pages;
- cross-group order: Remaining Development Map;
- full source traces: Developer Code Guide;
- validation commands: Build and Testing;
- staff/operator behavior: focused procedure pages.

When a repeated question needs more detail, add it to the most focused owning page
and link to that page rather than copying the same answer into several indexes.
