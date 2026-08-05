# Execution order and dependency graph

Dependencies are authoritative. Priority breaks ties only among dependency-complete packages.

```text
ES-P01
  ├── ES-P02
  │   ├── ES-P03
  │   │   ├── ES-P04
  │   │   │   ├── ES-P05
  │   │   │   │   ├── ES-P06
  │   │   │   │   └── ES-X01
  │   │   │   ├── ES-P10
  │   │   │   │   └── ES-P11
  │   │   │   └── ES-X01
  │   │   └── ES-P09
  │   └── ES-P07
  │       └── ES-P08
  │           ├── ES-X02
  │           │   ├── ES-X03
  │           │   └── ES-X04
  │           └── ES-V03
  └── ES-X05
```

Additional gates:

- `ES-V01` is separately deferred until a private local/Codex environment and representative database are available.
- `ES-V02` begins after all applicable implementation/provider packages listed in its package file complete.
- `ES-V03` begins after item and all destructive provider packages complete.
- `ES-A01` begins only after `ES-V01`, `ES-V02`, and `ES-V03` complete and the owner authorizes issue #43 acceptance.
- `ES-QA01` begins after `ES-A01` and after every applicable implementation/validation package is complete or explicitly accepted as deferred.

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
- destructive providers and private destructive acceptance around shared journals/rollback;
- implementation before distributed acceptance, cutover acceptance, and final audit.

No worker may start an unassigned parallel package merely because it appears safe.
