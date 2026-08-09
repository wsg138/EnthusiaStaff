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

`ES-R01` is an independent validation-infrastructure recovery package created by the 2026-08-08 owner-directed deadlock recovery. It does not replace or relax a product dependency. Its purpose is to remove the shared private-GitHub-hosted billing dependency from the exact-head staging route while preserving an ordinary hosted build and the existing private self-hosted Pi boot/restart gate. Until ES-R01 completes, ES-P02 and ES-P05 remain `PARKED_BLOCKED`; after it completes, a policy-valid repository-side staging route is available even if GitHub billing remains blocked, so normal continuation priority resumes ES-P02 before ES-P05. Each blocked package still must rerun its own exact-head gates and may not treat ES-R01's proof as package staging evidence.

`ES-V01` is independently deferred until a private local/Codex environment and representative database are available. It does not grant production authority and does not change the dependency chain for implementation packages.

## Parallel safety

Safe parallelism requires different repositories or proven non-overlapping paths plus no shared migration, lifecycle, destructive journal, protocol, configuration, or package-state edits. `Conditional` never means automatic.

Potentially parallel after prerequisites and preflight:

- `ES-X05` can proceed beside the `ES-P02` chain after `ES-P01`, because site and core work are separate unless contract files overlap.
- `ES-P07` can proceed beside later `ES-P03` work after `ES-P02` only when shared lifecycle/configuration files do not overlap.
- `ES-P09`, `ES-P05`, and `ES-P10` may diverge after their prerequisites only when their aggregate PR paths and package-state edits are coordinated.
- `ES-X03` and `ES-X04` may run concurrently only if no shared EnthusiaStaff destructive-coordinator files or migrations are changed.

Keep sequential:

- ES-R01 staging-workflow recovery before another identical private-hosted staging retry for ES-P02 or ES-P05 while the billing condition remains unchanged;
- exact-sanction appeal mutation before site appeals;
- lifecycle/reload before Bedrock identity and inventory runtime;
- staff tool dispatch before testers;
- inventory runtime before item/provider destruction;
- all item, currency, market, and reputation packages before `ES-V03`;
- implementation before distributed acceptance, cutover acceptance, and final audit.

No worker may start an unassigned parallel package merely because it appears safe.
