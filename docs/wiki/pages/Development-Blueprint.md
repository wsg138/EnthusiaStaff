# Remaining Development Map

Use this page to understand **what kinds of product work remain and which authoritative source answers the next question**. It is intentionally not a live package-worker dashboard.

## Quick answer

EnthusiaStaff has substantial merged foundations, but production authority is still gated by unfinished product areas and representative validation. The durable sequence is:

1. finish correctness/safety gaps in merged product areas;
2. complete required provider and external-component contracts;
3. run representative distributed Java/Bedrock/provider validation;
4. run destructive/load/process-recovery acceptance;
5. complete private LiteBans migration/shadow evidence and owner cutover acceptance;
6. perform the final release/no-fix audit on one pinned candidate.

Use [[Implementation Status]] for the readable merged-main picture. Use the [requirements matrix](https://github.com/wsg138/EnthusiaStaff/blob/main/reports/REQUIREMENTS-MATRIX.md) plus current legitimate review evidence for requirement-level proof.

## What remains by product area

| Area | Main remaining themes | Start here |
| --- | --- | --- |
| Core/runtime | full configuration/reload, lifecycle recovery, distributed topology, provider/classloader and Folia evidence | [[Core Platform and Infrastructure]] |
| Moderation | remaining escalation/provider/report-notification details and representative staff/runtime acceptance | [[Moderation, Punishments, and Reports]] |
| Player-state tools | inventory/offline/recovery safety, freeze coverage, vanish integrations, alts, advanced testers/fake systems | [[Staff Tools, Investigations, and Player-State Safety]] |
| Integrations/release | provider implementations, private site/runtime acceptance, LiteBans shadow/cutover, Java/Bedrock/Folia/load/process-kill evidence | [[Integrations, Migration, and Release Readiness]] |

These rows are product categories, not worker assignments.

## How a developer chooses the next document

- **I was assigned a specific implementation package:** follow the current `ai-agents/` package contract and live GitHub state. Do not infer package status from this page.
- **I need to understand a feature before changing it:** [[Developer Guide Index]] -> feature hub -> [[Developer Code Guide]].
- **I am reviewing a change:** [[Code Review Guide]].
- **I need to know what proof remains:** [[Build and Testing]] plus the requirements/evidence sources.
- **I am planning release/cutover work:** [[Integrations, Migration, and Release Readiness]], [[LiteBans Migration]], and [[Shadow Mode and Cutover]].

## Durable dependency principles

Regardless of current orchestration order:

- domain/persistence correctness precedes production authority;
- provider behavior must be implemented in the owning provider rather than invented in EnthusiaStaff;
- private/runtime validation follows the code it is intended to validate;
- Java/Bedrock/provider acceptance must use the exact candidate being evaluated;
- destructive/load/process-kill acceptance comes before production cutover;
- LiteBans remains authoritative throughout shadow and until an explicit accepted authority transition;
- a code or configuration change after an acceptance run invalidates the affected evidence.

## Repository and component model

`wsg138/EnthusiaStaff:main` is the aggregate repository for the current platform source and component copies. External components may also retain standalone repositories. When a component is mirrored in both places, release confidence requires the intended revisions/content to be reconciled rather than assuming one side represents the other.

See the current orchestration records only when doing assigned package work; general Wiki pages should not duplicate transient branch, package, or worker state.

## Release gates

The broad gates are:

- hosted clean build/test/static-analysis/runtime-artifact checks;
- private exact-candidate runtime checks;
- representative Velocity + multiple Paper backends + providers;
- Java and Bedrock/Geyser/Floodgate behavior;
- Folia-compatible scheduler/ownership behavior where supported;
- destructive workflow interruption/recovery and load/saturation;
- private representative LiteBans migration and shadow comparison;
- owner-authorized cutover and rollback acceptance;
- final release audit.

See [[Build and Testing]] for what each layer proves. Passing an earlier layer does not imply a later one passed.

## Authoritative references

- [Finished behavior](https://github.com/wsg138/EnthusiaStaff/blob/main/ENTHUSIASTAFF-GOALS.md)
- [[Implementation Status]]
- [Requirements matrix](https://github.com/wsg138/EnthusiaStaff/blob/main/reports/REQUIREMENTS-MATRIX.md)
- [[Developer Guide Index]]
- [[Code Review Guide]]
- [[Build and Testing]]
- [Repository package/orchestration records](https://github.com/wsg138/EnthusiaStaff/tree/main/ai-agents) — only for workers explicitly operating through that system