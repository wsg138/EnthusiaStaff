# Latest AI handoff

Current persistent package handoff:

[`2026-08-06-es-x05-website-auth-appeals.md`](2026-08-06-es-x05-website-auth-appeals.md)

Canonical package registry:

[`PACKAGE-REGISTRY.md`](../../work-packages/PACKAGE-REGISTRY.md)

Outage-recovery state is reconciled for the selected package: `ES-P01`, `ES-P03`, and `ES-X05 — Website UX, authentication, and appeals` are `COMPLETE`. `ES-P02 — Runtime database recovery and Velocity reload` remains `BLOCKED` / `PARKED_BLOCKED`. No package is active.

ES-X05 recovery started from aggregate `main` `9b1aac2677049ccc71dbddd963831f270c73dcd0` and finalization head `96bf9ab21b114a4523582a5ca267e6c1d1370cb1`. Current `main` was normally merged into the branch as `e9644c14e743f686758ee619ab347cbebe1b21ec`; frozen head `ab59b8357b8e2eb146b60ff122e316112906746f` passed Coverage `31140188918` / `92748299782`, CodeRabbit, Codacy static `92748599134`, and Codacy coverage checks `92749330468` / `92749330613` with zero valid unresolved review threads. PR #74 merged normally as `2bcf5d46ca6471fddac600f85020c66105b1c0f2`; containment has zero changed files.

Live standalone reconciliation included the already-reviewed PR #3 fix, current standalone `main` `2fe7d59c1c5e12db0b7ba792fc9e2af4d24337c2`, deleting page-level middleware that incorrectly redirected public-but-unlinked appeal/reviewer pages. Exact post-merge parity run `31140896890` / `92750376952` proved the aggregate and standalone trees identical under the repository hash rules: hash `780269847698d37c470cb7c241539b1c7387014225cc7eee9598548c9dc97f8b`, no added/missing/modified paths; evidence artifact `8979748083`.

The owner-approved ES-X05 private/Pi staging deferral remains assigned to `ES-V02`, deferred and not passed. No manual staging retry was requested; PR automation automatically dispatched wrapper `31140187754` / `92748257022`, private run `31140197043`, whose Ubuntu build `92748287250` again received no runner with the same Billing & plans restriction; Pi `92748295072` skipped. This does not change the deferral.

ES-P02 remains parked: exact hosted head `d671fef9fd14f0c4ae711c83edb29bc9b08ea002` passed Coverage `31138550369` / `92743341861`, but its package-specific private run `31139079620` failed before runner allocation in `92744901730` on the same Billing & plans restriction; Pi `92744908539` skipped. Do not repeat that staging attempt until the billing condition materially changes.

Dependency-derived state after ES-X05 recovery: `ES-P04 — Staff-mode operational tools` is `READY` at priority 40 and `ES-P09 — Alt and network-identity completion` is `READY` at priority 55. Neither was started. A normal sequential worker may resume after this recovery worker stops, reclassifying live continuations first; absent one, ES-P04 is the expected next package.

LiteBans remains authoritative; issue #43 and production activation remain deferred.