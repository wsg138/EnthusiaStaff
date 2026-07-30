# EnthusiaStaff

EnthusiaStaff is the moderation and staff platform being built for the Enthusia
Network. It combines network punishments, reports, staff mode, vanish, freeze,
inventory inspection, confiscation, alt investigations, Discord delivery,
LiteBans migration, and a punishment/appeal website into one audited platform.

> **Pre-release:** EnthusiaStaff is not currently approved to replace LiteBans
> or the existing production staff stack. A command being registered does not
> mean its complete workflow has passed staging. Check [[Implementation Status]]
> before using any procedure on a live server.

## Choose your starting point

### Staff members

- [[Staff Handbook]] — policies that apply to every staff tool.
- [[Moderator Quick Start]] — the shortest practical operating guide.
- [[Punishment System]] — cases, punishment ladders, confirmation, and changes.
- [[Reports and Evidence]] — claiming reports and handling evidence.
- [[Staff Mode, Vanish, and Freeze|Staff-Mode-Vanish-and-Freeze]] — investigation sessions and visibility.
- [[Inventory and Confiscation Safety]] — viewing, editing, and removing assets.
- [[Alt Investigations]] — confidence, household exceptions, and inheritance.
- [[Incident Playbooks]] — step-by-step response to common incidents.
- [[Privacy and Data Handling]] — what may and may not be shared.

### Administrators and operators

- [[Installation]]
- [[Configuration]]
- [[Commands and Permissions]]
- [[Integrations]]
- [[Recovery and Troubleshooting]]
- [[LiteBans Migration]]
- [[Shadow Mode and Cutover]]

### Developers

- [[Architecture]]
- [[Development Setup]]
- [[Build and Testing]]
- [[Wiki Maintenance]]
- [Authoritative goals](https://github.com/wsg138/EnthusiaStaff/blob/main/ENTHUSIASTAFF-GOALS.md)
- [Requirements matrix](https://github.com/wsg138/EnthusiaStaff/blob/main/reports/REQUIREMENTS-MATRIX.md)

## Operating principles

1. **Use the central workflow.** Do not bypass cases, sanction policy, inventory
   journals, provider APIs, or audit records with raw database edits.
2. **Confirm identity and evidence.** Similar usernames, previous names, Bedrock
   prefixes, and alt relationships are not interchangeable.
3. **Use the least authority needed.** A permission node does not override rank
   restrictions enforced by the application service.
4. **Do not guess through failures.** Stale, ambiguous, partial, or conflicting
   destructive work must stop or enter recovery quarantine.
5. **Protect private information.** Raw network identity, reporter identity,
   coordinates, private messages, internal notes, and confiscated contents are
   never public evidence.
6. **Preserve history.** Ending, revoking, reducing, or overturning a punishment
   changes its state; it does not erase the original case or audit trail.
7. **Keep exactly one punishment authority.** LiteBans remains authoritative
   until shadow comparison and cutover are formally completed.

## Platform shape

The finished platform has exactly two Minecraft runtime jars:

- `EnthusiaStaff-Paper-<version>.jar` on every supported backend.
- `EnthusiaStaff-Velocity-<version>.jar` on the proxy.

MariaDB is the durable authority. Paper performs server-local player and staff
interactions. Velocity performs network-wide login and mute enforcement,
protected network-identity processing, migration coordination, and restricted
website/API delivery.

## Documentation status

The Wiki is staff- and operator-facing. It summarizes intended behavior from
`ENTHUSIASTAFF-GOALS.md`, but availability is determined by current code,
tests, staging evidence, and the requirements matrix. Pages intentionally call
out missing commands, unavailable integrations, and unverified behavior rather
than presenting the target design as complete.
