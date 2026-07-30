# EnthusiaStaff

EnthusiaStaff is the moderation and staff platform being built for the Enthusia
Network. The Wiki is divided into staff guides, administrator references,
operations, and developer documentation.

> **Pre-release:** EnthusiaStaff is not currently approved to replace LiteBans or
> the existing production staff stack. Check [[Implementation Status]] before
> using a documented workflow as a live-server replacement.

## Staff

Start here for ordinary moderation work:

- [[Staff Handbook]] — expectations, judgment, evidence, privacy, and conduct.
- [[Staff Quick Start|Moderator-Quick-Start]] — common reports, punishments, and
  staff tools in one short guide.
- [[Helper Guide]] — expectations and routine actions for the upcoming Helper role.
- [[Punishment System]] — using the punishment interface and correcting actions.
- [[Reports and Evidence]] — investigating and closing player reports.
- [[Staff Mode, Vanish, and Freeze|Staff-Mode-Vanish-and-Freeze]] — when and how to
  use investigation controls.
- [[Inventory and Confiscation Safety]] — viewing, editing, and removing assets.
- [[Incident Playbooks]] — handling common incidents and urgent problems.
- [[Privacy and Data Handling]] — information that must remain internal.

Staff members do not need to understand database health, protocol details, UUID
resolution internals, or permission matrices to follow the staff guides. Those
belong in the administrator and developer sections.

## Administrators and operators

- [[Commands and Permissions]]
- [[Roles and Permissions|Rank-Authority]]
- [[Configuration]]
- [[Installation]]
- [[Integrations]]
- [[Recovery and Troubleshooting]]
- [[LiteBans Migration]]
- [[Shadow Mode and Cutover]]

## Developers and reviewers

- [[Developer Guide Index]] — category links and answers to common reviewer
  questions.
- [[Architecture]] — module boundaries and runtime ownership.
- [[Developer Code Guide]] — files, packages, stores, feature traces, tests, and
  review order.
- [[Protocol and Network Traffic]] — persistent channel, ReplayGuard,
  acknowledgements, and outbound destinations.
- [[Vanish Internals]] — exact events, visibility calls, packet limitations, and
  integration gaps.
- [[Development Setup]]
- [[Build and Testing]]
- [[Wiki Maintenance]]

## Documentation rules

The Wiki distinguishes intended design from verified behavior:

- `ENTHUSIASTAFF-GOALS.md` defines the intended finished platform.
- Current code and tests show what exists.
- `reports/REQUIREMENTS-MATRIX.md` records conservative implementation and staging
  status.
- Wiki staff pages explain how people should use approved workflows.

A command, class, or permission existing in source does not by itself prove that a
feature is ready for production.