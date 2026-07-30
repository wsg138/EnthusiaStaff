# EnthusiaStaff

EnthusiaStaff is the moderation and staff platform for the Enthusia Network. It
provides one Paper runtime for backend servers and one Velocity runtime for the
network proxy, with MariaDB as the durable source of truth.

> **Pre-release status:** EnthusiaStaff is not ready to replace the active
> moderation authority. LiteBans must remain authoritative until the documented
> migration, 168-hour shadow window, acceptance checks, and cutover gate have
> completed.

## Start here

- [[Architecture]] explains module boundaries, runtime ownership, and durable
  operation flow.
- [[Development Setup]] lists the supported development prerequisites.
- [[Build and Testing]] describes the mandatory clean build and MariaDB
  Testcontainers validation.
- [[Inventory and Confiscation Safety]] explains fenced inventory changes,
  case-linked snapshots, queued recovery, and quarantine.
- [Repository documentation](https://github.com/wsg138/EnthusiaStaff/tree/main/docs)
  contains the source-controlled technical specifications and recovery
  runbooks.
- [Requirements matrix](https://github.com/wsg138/EnthusiaStaff/blob/main/reports/REQUIREMENTS-MATRIX.md)
  records implementation and verification status conservatively.

## Runtime topology

EnthusiaStaff produces exactly two deployable artifacts:

- `EnthusiaStaff-Paper-<version>.jar` runs unchanged on each supported Paper
  backend.
- `EnthusiaStaff-Velocity-<version>.jar` runs on the Velocity proxy.

Paper owns server-local player and staff interactions. Velocity owns
network-wide login enforcement, protected network identity observations,
migration coordination, and durable network delivery. Both runtimes use
MariaDB-backed transactions, idempotency keys, leases, fencing tokens, and
recovery records for authoritative work.

## Safety principles

- Persist durable intent before destructive external changes.
- Re-read and verify authoritative state before committing a transition.
- Fail closed when authorization, identity, revision, fencing, or recovery
  evidence is incomplete.
- Quarantine ambiguous operations instead of guessing whether a side effect
  occurred.
- Keep secrets and raw network identities outside logs, public APIs, and source
  control.
- Treat optional integrations as independently degradable capabilities.

The Wiki is an operator and developer navigation layer. Detailed implementation
contracts remain in the repository so they are reviewed and versioned with the
code.
