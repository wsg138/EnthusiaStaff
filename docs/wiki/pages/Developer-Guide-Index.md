# Developer Guide Index

Use this page to find the correct developer document without repeating the
content of those documents.

## Start by task

| I need to... | Open |
| --- | --- |
| See how complete every feature is | [[Feature Completion Status|Implementation-Status]] |
| See what development remains and in what order | [[Remaining Development Map|Development-Blueprint]] |
| Find the files and services for a feature | [[Developer Code Guide]] |
| Understand module boundaries and dependency direction | [[Architecture]] |
| Set up a development environment | [[Development Setup]] |
| Build, test and validate a change | [[Build and Testing]] |
| Understand Paper–Velocity traffic and ReplayGuard | [[Protocol and Network Traffic]] |
| Review vanish events, packets and visibility gaps | [[Vanish Internals]] |
| Update or publish documentation | [[Wiki Maintenance]] |

## Core developer documents

### Planning and status

- [[Feature Completion Status|Implementation-Status]] — feature-by-feature marks,
  estimated percentages and remaining work.
- [[Remaining Development Map|Development-Blueprint]] — unfinished work grouped
  into moderation, staff safety, investigations, integrations and migration.

### Code and architecture

- [[Architecture]] — module boundaries, dependency direction and runtime ownership.
- [[Developer Code Guide]] — repository map, important files, composition roots,
  persistence stores, feature traces, tests and high-risk review areas.
- [[Protocol and Network Traffic]] — connection topology, authentication,
  replay protection, acknowledgements and outbound destinations.
- [[Vanish Internals]] — vanish-specific events, packet behavior, visibility
  decisions, threading and known gaps.

### Working on the repository

- [[Development Setup]] — required tools and local setup.
- [[Build and Testing]] — focused tests, full validation, MariaDB, runtime jars,
  coverage, Codacy and staging evidence.
- [[Wiki Maintenance]] — documentation ownership, validation and publication.

## Feature traces

The [[Developer Code Guide]] contains the maintained source traces for:

- punishment creation, requests and sanction changes;
- reports and evidence;
- inventory inspection and editing;
- item and economy confiscation;
- staff mode, vanish and freeze;
- alts and network identity;
- Paper–Velocity and Discord delivery;
- LiteBans migration and cutover; and
- the website bridge.

Link directly to the relevant trace when reviewing a feature. Do not copy the
same file list or workflow into this index.

## Authoritative repository sources

- [Goals](https://github.com/wsg138/EnthusiaStaff/blob/main/ENTHUSIASTAFF-GOALS.md)
  define the finished behavior.
- [Requirements matrix](https://github.com/wsg138/EnthusiaStaff/blob/main/reports/REQUIREMENTS-MATRIX.md)
  records exact evidence and blockers.
- [Development blueprint](https://github.com/wsg138/EnthusiaStaff/blob/main/docs/development-blueprint.md)
  groups the remaining implementation work.

When a repeated question needs a detailed answer, put that answer in the most
focused technical page and link it here once.
