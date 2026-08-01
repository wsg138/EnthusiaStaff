# EnthusiaStaff

EnthusiaStaff is the distributed moderation and staff platform being built for
the Enthusia Network. The Wiki is divided into staff guides, administrator
references, operations and developer documentation.

> **Pre-release:** EnthusiaStaff is not currently approved to replace LiteBans or
> the existing production staff stack. Check [[Implementation Status]] before
> using a documented workflow as a live-server replacement.

## Current development direction

The project has advanced substantially beyond early scaffolding. The current
repository contains tested Java 21 packaging, MariaDB-backed moderation state,
authenticated Paper–Velocity communication, durable punishment requests, report
persistence, a restricted website bridge, improved vanish recovery and detailed
LiteBans shadow-comparison evidence. Major production gates still remain.

Use [[Development Blueprint]] for the visual road from the current checkpoint to
full acceptance, the 168-hour shadow period, cutover rehearsal and production
authority.

## Staff

Start here for ordinary moderation work:

- [[Staff Handbook]] — expectations, judgment, evidence, privacy and conduct.
- [[Staff Quick Start|Moderator-Quick-Start]] — common reports, punishments and
  staff tools in one short guide.
- [[Helper Guide]] — expectations and routine actions for the upcoming Helper role.
- [[Punishment System]] — using the punishment interface and correcting actions.
- [[Reports and Evidence]] — investigating and closing player reports.
- [[Staff Mode, Vanish, and Freeze|Staff-Mode-Vanish-and-Freeze]] — when and how to
  use investigation controls.
- [[Inventory and Confiscation Safety]] — viewing, editing and removing assets.
- [[Incident Playbooks]] — handling common incidents and urgent problems.
- [[Privacy and Data Handling]] — information that must remain internal.

Staff members do not need to understand database health, protocol details, UUID
resolution internals or permission matrices to follow the staff guides. Those
belong in the administrator and developer sections.

## Administrators and operators

- [[Implementation Status]]
- [[Development Blueprint]]
- [[Commands and Permissions]]
- [[Roles and Permissions|Rank-Authority]]
- [[Configuration]]
- [[Installation]]
- [[Integrations]]
- [[Recovery and Troubleshooting]]
- [[LiteBans Migration]]
- [[Shadow Mode and Cutover]]

## Developers and reviewers

- [[Development Blueprint]] — milestones, workstreams, release gates and the
  immediate execution order.
- [[Developer Guide Index]] — category links and answers to common reviewer
  questions.
- [[Architecture]] — module boundaries and runtime ownership.
- [[Developer Code Guide]] — files, packages, stores, feature traces, tests and
  review order.
- [[Protocol and Network Traffic]] — persistent channel, ReplayGuard,
  acknowledgements and outbound destinations.
- [[Vanish Internals]] — exact events, visibility calls, packet limitations and
  integration gaps.
- [[Development Setup]]
- [[Build and Testing]]
- [[Wiki Maintenance]]

## Documentation rules

The Wiki distinguishes intended design from verified behavior:

- `ENTHUSIASTAFF-GOALS.md` defines the intended finished platform.
- Current code and exact-SHA tests show what exists.
- `reports/REQUIREMENTS-MATRIX.md` records conservative implementation and staging status.
- `docs/development-blueprint.md` orders the remaining work and release gates.
- Wiki staff pages explain how people should use approved workflows.

A command, class or permission existing in source does not by itself prove that a
feature is ready for production. A green pull request proves only the tested
scope at its exact reviewed head.