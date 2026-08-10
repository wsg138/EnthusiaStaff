# Execution order and dependency graph

Dependencies are authoritative. Priority breaks ties only among dependency-complete packages.

## Canonical direct-dependency DAG

Each package appears once. A comma-separated dependency set means every listed package must be `COMPLETE` before the package can become `READY`, unless a later validation package explicitly permits an owner-accepted deferred gate.

```text
ES-R01
ES-P01 -> ES-P02
ES-P01 -> ES-X05
ES-P02 -> ES-P03
ES-P02 -> ES-P07
ES-P03 -> ES-P04
ES-P03 -> ES-P09
ES-P04 -> ES-P05
ES-P04 -> ES-P10
ES-P03 + ES-P04 + ES-P05 -> ES-X01
ES-P05 -> ES-P06
ES-P10 -> ES-P11
ES-P07 -> ES-P08
ES-P08 -> ES-X02
ES-P08 + ES-X02 -> ES-X03
ES-P08 + ES-X02 -> ES-X04
ES-P08 + ES-X02 + ES-X03 + ES-X04 -> ES-V03
ES-P06 + ES-P09 + ES-P11 + ES-X01 + ES-X03 + ES-X04 + ES-X05 -> ES-V02
ES-V01 + ES-V02 + ES-V03 -> ES-A01
ES-A01 + all applicable completed-or-owner-accepted-deferred packages -> ES-QA01
```

`ES-R01` is the independent validation-infrastructure recovery package created by the 2026-08-08 owner-directed deadlock recovery. It did not replace or relax any product dependency. It repaired the canonical public→private Pi route while preserving ordinary hosted build and private self-hosted runtime acceptance. ES-P02 and ES-P05 later passed their own exact-head gates through that route and are now `COMPLETE`; ES-R01 proof was never substituted for either package's acceptance.

`ES-V01` remains independently deferred until a private local/Codex environment and representative database are available. It does not grant production authority and does not change the implementation dependency chain.

## Current sequential routing snapshot

After ES-P05 completion, there is no active package. Dependency-derived `READY` packages are:

1. `ES-P07` — priority 45; exact next normal sequential selection absent a newly discovered actionable continuation.
2. `ES-P06` — priority 60; newly ready because ES-P05 is complete.
3. `ES-X01` — priority 100; newly ready because ES-P03, ES-P04 and ES-P05 are complete.

A terminal package worker must not activate any of those packages merely because it updated their derived status. Every new worker must reconcile live GitHub and reclassify incomplete packages before selecting work.

## Parallel safety

Safe parallelism requires different repositories or proven non-overlapping paths plus no shared migration, lifecycle, destructive journal, protocol, configuration, or package-state edits. `Conditional` never means automatic.

Potentially parallel after prerequisites and preflight:

- `ES-X05` can proceed beside the `ES-P02` chain after `ES-P01`, because site and core work are separate unless contract files overlap.
- `ES-P07` can proceed beside later `ES-P03` work after `ES-P02` only when shared lifecycle/configuration files do not overlap.
- `ES-P09`, `ES-P05`, and `ES-P10` may diverge after their prerequisites only when their aggregate PR paths and package-state edits are coordinated.
- `ES-X03` and `ES-X04` may run concurrently only if no shared EnthusiaStaff destructive-coordinator files or migrations are changed.

Keep sequential:

- exact-sanction appeal mutation before site appeals;
- lifecycle/reload before Bedrock identity and inventory runtime;
- staff tool dispatch before testers;
- inventory runtime before item/provider destruction;
- all item, currency, market, and reputation packages before `ES-V03`;
- implementation before distributed acceptance, cutover acceptance, and final audit.

No worker may start an unassigned parallel package merely because it appears safe.