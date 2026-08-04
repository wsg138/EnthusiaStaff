# Latest AI handoff

Current handoff:

[`2026-08-04-freeze-mounted-movement.md`](2026-08-04-freeze-mounted-movement.md)

Related PR:

[`#64 — Block mounted movement while frozen`](https://github.com/wsg138/EnthusiaStaff/pull/64)

## Summary

| Field | Value |
| --- | --- |
| Work item | Eject existing mounts when freeze becomes active or is restored, and reject new mount attempts while restricted |
| PR | `#64` |
| Branch | `fix/freeze-mounted-movement` |
| Starting main | `f95d5ec404b7a4eca705bdd2ac013eb55af56a11` |
| State | `ACTIVE DRAFT — implementation, focused tests and harsh-review corrections complete; exact-head validation and review resolution pending` |
| Implementation | Shared immediate restriction leaves the current vehicle before inventory closure/notice; explicit `EntityMountEvent` cancellation reuses the existing fail-closed freeze boundary |
| Tests | Handler metadata; restricted and ordinary mount behavior; immediate vehicle-exit, inventory-close and notice calls; existing runtime-state lifecycle/fencing coverage remains applicable |
| Harsh review | Duplicate test fixture removed; mount coverage consolidated into `FreezeInteractionCoverageTest`; unused/import and invocation-adapter cleanup completed; backend-switch logic verified as already present and not duplicated |
| Migration boundary | V16 is highest; PR #64 adds no migration; V1–V16 remain immutable |
| Commands, permissions, configuration | None changed |
| External provider blocker | RoseChat private-message evidence remains blocked pending a supported provider contract. Do not route it through issue #43. |
| Production boundary | No deployment, authority activation, LiteBans change, production Discord use, production access, migration repair or cutover |
| Next owner-priority workstream | Freshly select one remaining staff-mode, vanish or freeze item only after PR #64 completes |

Final build, test, migration, runtime-JAR, coverage, static-analysis, Codacy, CodeRabbit, review-thread, Pi and merge evidence belongs in PR #64 live metadata and must bind to one unchanged final head. Pi must succeed when it executes normally; when direct evidence proves GitHub Actions quota or platform unavailability prevented repository code from executing, record that Pi did not run and do not claim success.

The next agent must first reconcile live GitHub and repository state, resume PR #64 rather than opening another branch, resolve every valid review finding, require the complete exact-head gate and stop after merge or a verified blocker. Do not edit V1–V16, use Flyway repair, deploy, access production data, alter LiteBans authority, begin issue #43 acceptance or combine another feature into PR #64.
