# EnthusiaStaff

EnthusiaStaff is the distributed moderation and staff platform being built for
the Enthusia Network.

> **Pre-release:** EnthusiaStaff is not approved to replace LiteBans or the
> existing production staff stack.

## Project status

Start with these two pages:

- [[Feature Completion Status|Implementation-Status]] — every major feature has a
  completion mark or estimated percentage, plus what is working and what remains.
- [[Remaining Development Map|Development-Blueprint]] — unfinished work grouped
  into moderation, staff safety, investigations, integrations and migration.

The repository has substantial tested foundations, including Java 21 packaging,
MariaDB persistence, authenticated Paper–Velocity communication, punishment
requests, report persistence, vanish recovery and LiteBans comparison tooling.
Many user-facing workflows, providers and live-runtime acceptance groups remain
incomplete.

## Staff documentation

- [[Staff Handbook]] — expectations, judgment, evidence, privacy and conduct.
- [[Staff Quick Start|Moderator-Quick-Start]] — common moderation workflows.
- [[Helper Guide]] — Helper responsibilities and restrictions.
- [[Punishment System]] — punishments, requests and corrections.
- [[Reports and Evidence]] — report investigation and evidence handling.
- [[Staff Mode, Vanish, and Freeze|Staff-Mode-Vanish-and-Freeze]] — investigation controls.
- [[Inventory and Confiscation Safety]] — viewing, editing and removing assets.
- [[Alt Investigations]] — relationship evidence and exceptions.
- [[Incident Playbooks]] — urgent and unusual incidents.
- [[Privacy and Data Handling]] — information that must remain internal.

## Administration and operations

- [[Commands and Permissions]]
- [[Roles and Permissions|Rank-Authority]]
- [[Configuration]]
- [[Integrations]]
- [[Installation]]
- [[Recovery and Troubleshooting]]
- [[LiteBans Migration]]
- [[Shadow Mode and Cutover]]

## Development

- [[Developer Guide Index]] — choose the correct technical document by task.
- [[Architecture]] — modules, dependency direction and runtime ownership.
- [[Developer Code Guide]] — files, services, stores, feature traces and tests.
- [[Protocol and Network Traffic]] — Paper–Velocity transport and outbound traffic.
- [[Vanish Internals]] — events, packets, visibility decisions and known gaps.
- [[Development Setup]] — local prerequisites and setup.
- [[Build and Testing]] — validation commands and evidence requirements.
- [[Wiki Maintenance]] — documentation editing and publication.

## Documentation ownership

- `ENTHUSIASTAFF-GOALS.md` defines intended finished behavior.
- `reports/REQUIREMENTS-MATRIX.md` records exact implementation evidence and blockers.
- [[Feature Completion Status|Implementation-Status]] converts that evidence into
  readable planning estimates.
- [[Remaining Development Map|Development-Blueprint]] groups only the unfinished work.
- [[Developer Code Guide]] owns detailed code paths and file responsibilities.
- [[Build and Testing]] owns validation procedures.

Keeping each subject on one primary page prevents the developer documentation
from repeating the same roadmap, test and source-map information in several places.
