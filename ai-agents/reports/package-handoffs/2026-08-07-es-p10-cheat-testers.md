# ES-P10 cheat tester and fake-entity handoff

Date: 2026-08-07
Package: `ES-P10 — Cheat tester and fake-entity system`
Worker: `ChatGPT sequential package worker`
Status: `ACTIVE`

## Selection and routing

- Legitimate aggregate `main` at selection: `83302749b3247f7a05157f1625fc99da6aa43736` (merge PR #85).
- Required temporary implementation branch: `package/es-p10-cheat-testers`.
- ES-P10 had no pre-existing branch, PR, or package handoff.
- ES-P04 is `COMPLETE`, satisfying ES-P10's dependency.
- ES-P02 PR #70 and ES-P05 PR #81 were freshly rechecked and remain `BLOCKED` / `PARKED_BLOCKED` on the unchanged private Actions Billing & plans condition. A private staging run created at 2026-08-07 16:10 UTC for current EnthusiaStaff source had required Ubuntu job `92925059857` with runner ID `0`, empty runner name, and `steps: []`; Pi job `92925074453` was skipped. No product validation step executed. The blocker therefore did not change and those packages were not resumed or synchronized.
- All other incomplete implementation/provider packages remain dependency-blocked, planned, or deferred under the canonical DAG. Issue #43 remains open and deferred; LiteBans remains authoritative.
- ES-P10 was therefore the dependency-complete eligible `READY` package with the lowest numerical priority and was selected automatically.

## Starting repository facts

- `wsg138/EnthusiaStaff:main`: `83302749b3247f7a05157f1625fc99da6aa43736`.
- Highest Flyway migration: immutable V17 (`V17__website_appeal_workflow.sql`). Any required ES-P10 schema change must begin at V18; no migration is assumed unless durable tester state proves necessary.
- ES-P10 is internal to `COMP-STAFF`; no standalone repository synchronization is required.
- Existing Paper build already declares ProtocolLib `5.4.0` as a compile-only dependency and test dependency.
- Existing first-party ProtocolLib integration demonstrates the supported optional-adapter pattern. ProtocolLib 5.4.0 exposes `PacketType.Play.Client.USE_ENTITY`, `WrappedEnumEntityUseAction`, and current packet abstractions needed for an isolated client-side fake-entity implementation. No unisolated NMS dependency is authorized.

## Authoritative package behavior

The package includes `AUD-TESTER-001` and `AUD-TESTER-002` only. The authoritative goals require:

- Cheat Tester controls: right-click choose, left-click player run, shift-right-click configure, with text/command fallback for Bedrock usability.
- Release tester types: Totem refill, No-fall, Velocity/anti-knockback, and Auto-armor.
- Evidence only; no automatic punishment.
- Exact target-state snapshot/restore around temporary tester changes, with bounded crash-safe handling so temporary assets are not consumed or duplicated.
- Fake entities are target/staff-only, nonpersistent, record intended aim/interaction evidence, and must have Java/Bedrock-safe semantics.
- Fake bases are explicitly excluded and remain `ES-P11`.

## Current code facts

- `StaffToolDefinition.CHEAT_TESTER` exists as an advanced staff hotbar definition but is deliberately unavailable.
- `StaffToolDispatcher` deliberately routes the tester to an ES-P10 deferred message and exposes no tester action.
- No production tester service, fake-entity lifecycle, command, configuration, evidence model, or tester-specific tests existed at package selection.
- `InventoryCoordinator` exposes external per-player asset locks that can protect temporary asset mutation from concurrent moderation inventory work; any tester mutation must still snapshot and restore exact target state on the owning entity scheduler.

## Planned implementation boundary

- Implement bounded tester selection/configuration and authorized run/cancel/status controls.
- Keep one active tester session per target and bounded per-staff/global limits; all state is server/runtime scoped.
- Use the Paper/Folia entity scheduler for player mutation and the global scheduler only for non-entity coordination.
- Snapshot only state a tester temporarily changes and restore it deterministically on completion, cancel, timeout, target/staff disconnect, reload, and shutdown.
- Use a focused fake-entity adapter backed by ProtocolLib; fail closed when ProtocolLib is unavailable or unhealthy. No NMS reflection or unsupported packet internals outside that adapter.
- Restrict fake-entity audience to the suspect plus authorized controlling staff; never expose hidden staff state to ordinary players.
- Record bounded tester evidence/audit only; never auto-sanction.
- Preserve the production/private-data boundary and leave distributed Java/Bedrock representative acceptance to `ES-V02`.

## Validation state

No product implementation has been frozen or validated yet. Local checkout/build execution is unavailable in this session because the local environment cannot resolve `github.com`; connector-backed GitHub state is authoritative and hosted exact-head validation will be required before merge.

## Exact next action

Complete the ES-P10 implementation on `package/es-p10-cheat-testers`, checkpoint it through the single package PR, harshly review the final diff, resolve every valid review/static finding, freeze the exact head, run all applicable hosted exact-head Java/Wiki/static/review gates, evaluate private staging under the package validation policy without mislabeling unavailable infrastructure as success, merge normally only if all package gates are satisfied, verify containment and cleanup, publish terminal package state, and stop without activating ES-P11.

## Systems not to disturb

Do not modify parked ES-P02/ES-P05 product branches, begin ES-P11 fake bases, deploy, access production/private player data, run issue #43 acceptance, activate EnthusiaStaff punishment authority, disable LiteBans, rewrite Flyway history, or claim unavailable private staging passed.
