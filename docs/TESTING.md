# EnthusiaStaff testing guide

This document explains the repository-local automated tests, the current exhaustive test-hardening work, how workers should run and review the suite, and how test results relate to Sentinel Sim and staging.

The key rule is:

> **EnthusiaStaff owns its handwritten unit, regression, persistence, integration, architecture, command, permission, Paper, and Velocity tests. Sentinel Sim is an additional compatibility/runtime evidence layer, not the storage location for Staff's normal tests.**

## Current test-hardening work

The owner-directed test-hardening PR adds `paper/src/test/java/net/enthusia/staff/paper/AllFeatureSurfaceContractTest.java`.

That test is a **coverage contract/guard**, not a replacement for the existing behavioral suites. It is designed to make an omitted feature family visible in CI.

It currently enforces:

- the exact reviewed 30-command Paper surface;
- command declarations remain bound by Paper runtime wiring;
- every command permission reference resolves to a declared permission;
- the intentionally outer-unpermissioned commands remain explicit and reviewed;
- every `enthusiastaff.*` permission has an explicit fail-closed default;
- child-permission references exist and grant intentionally;
- the reviewed helper -> mod -> admin -> founder inheritance chain remains intact;
- selected sensitive authority edges remain attached to the intended rank;
- every major feature family has at least one concrete regression-test source somewhere in the repository.

The guarded feature families are:

1. runtime/reload;
2. punishment workflow;
3. sanction changes;
4. history;
5. reports/evidence;
6. freeze;
7. staff mode;
8. staff tools;
9. cheat tester;
10. fake bases;
11. vanish/visibility;
12. staff chat;
13. client evidence;
14. inventory/ender chest;
15. inspection/confiscation;
16. cases/recovery;
17. account linking;
18. Discord moderation;
19. network identity/alts;
20. persistence/migrations;
21. Velocity runtime.

If a new Staff feature is added and it does not fit one of those families, update the contract to name the new family and add real behavioral regression coverage for it.

## What the coverage contract does not prove

A green `AllFeatureSurfaceContractTest` does **not** mean every feature is bug-free. It proves the reviewed surface is still explicit and that concrete regression suites exist for the named feature families.

The actual behavior is still proved by the existing tests in modules such as:

- `domain/`;
- `persistence/`;
- `protocol/`;
- `paper/`;
- `velocity/`;
- `integration-tests/`.

Workers must inspect and extend those real behavioral tests when product behavior changes. Do not satisfy the coverage guard by creating an empty or meaningless test class with a matching name.

## Prerequisites

Follow `docs/development.md` and the repository's AI-agent rules. At minimum:

- JDK 21;
- checked-in Gradle wrapper;
- Docker available for MariaDB/Testcontainers portions of the full suite;
- Python 3 for Wiki validation when documentation changes require it.

Before modifying tests, reconcile live GitHub and read:

1. `ai-agents/AGENTS.md`;
2. `ai-agents/WORKSPACE-STATE.md`;
3. package registry/protocol files required by `AGENTS.md`;
4. all open/draft PRs and their changed paths;
5. the active test-hardening PR and this document.

The test-hardening PR is intentionally separate from canonical implementation/repair packages. Product fixes belong in their owning package branch unless the owner explicitly changes scope.

## How to run the new coverage contract

Windows:

```powershell
.\gradlew.bat :paper:test --tests net.enthusia.staff.paper.AllFeatureSurfaceContractTest
```

Linux/macOS:

```bash
./gradlew :paper:test --tests net.enthusia.staff.paper.AllFeatureSurfaceContractTest
```

Run the entire Paper test suite after focused changes:

```powershell
.\gradlew.bat :paper:test
```

or:

```bash
./gradlew :paper:test
```

## Full repository validation

The canonical complete validation remains the repository's existing Java 21 gate, not merely the focused contract test.

Windows:

```powershell
.\gradlew.bat --no-daemon --no-build-cache --no-configuration-cache --rerun-tasks clean test check runtimeJars
```

Linux/macOS:

```bash
./gradlew --no-daemon --no-build-cache --no-configuration-cache --rerun-tasks clean test check runtimeJars
```

For final package/review evidence, also follow `ai-agents/work-packages/VALIDATION-POLICY.md`, `ai-agents/STAGING-TEST-OPERATING-GUIDE.md`, and the selected package contract. Do not silently skip MariaDB/Testcontainers work when it is applicable.

## Where local results are written

Gradle writes module-local results under paths such as:

- `paper/build/test-results/test/` — JUnit XML;
- `paper/build/reports/tests/test/` — HTML test report;
- corresponding `build/test-results/test/` and `build/reports/tests/test/` directories in the other modules.

Coverage/report locations remain governed by the existing Gradle/CI configuration and `docs/wiki/pages/Build-and-Testing.md`.

GitHub Actions is the durable source for exact-head CI evidence. Final evidence must correspond to the exact PR head being reviewed; a green run from an older SHA becomes stale after another commit.

## How to interpret coverage-contract failures

### Command surface mismatch

A command was added, removed, or renamed in `plugin.yml` without updating the reviewed contract.

Do not automatically update the expected set. First determine whether the command change is intentional and whether its behavior/permission routes have tests.

### Runtime binding failure

A command exists in metadata but the contract cannot find the reviewed Paper runtime binding. Treat this as either a real wiring regression or a contract that needs a deliberately reviewed update after a runtime refactor.

### Permission declaration/default failure

Treat permission failures as security-sensitive. Do not weaken the assertion simply to make CI green. Verify command/service boundary authority, permission defaults, child relationships, console/SYSTEM behavior, and rank inheritance.

### Feature-family coverage failure

The guard cannot find a concrete regression test source for a major feature family. The correct fix is normally to restore/add behavioral tests. Renaming the marker to match an unrelated test is not acceptable.

### Test/setup failure

If the test cannot locate repository/module files, determine whether CI working-directory/build behavior changed. Fix the test harness rather than changing production behavior.

## How workers should add tests for a feature

For every meaningful product change:

1. identify the feature family and owning module;
2. add focused behavioral tests close to that code;
3. include negative/hostile paths where applicable;
4. cover permission/authority decisions at the service boundary;
5. cover stale state, retries, duplicates, reload/restart, shutdown, and partial failure when relevant;
6. add MariaDB/migration coverage when persistence changes;
7. cover provider-present and provider-missing/degraded modes;
8. cover Paper/Velocity differences where relevant;
9. update `AllFeatureSurfaceContractTest` only when the reviewed surface/family mapping actually changes;
10. run focused tests, then the complete repository validation;
11. record exact-head CI/staging evidence required by the package protocol.

For Staff specifically, "every feature is tested" means there is meaningful behavior coverage for the feature and its important failure/authority boundaries. It does not mean one giant test method or a filename-only inventory.

## Reviewing a Staff test PR

Reviewers should verify:

- the test fails for the intended regression, not merely for implementation details;
- assertions prove externally meaningful behavior;
- permission and rank assertions cannot accidentally broaden authority;
- stale object/view/session cases are covered for GUI/inventory flows;
- retries and idempotency are covered for moderation/persistence/Discord/provider work;
- database tests verify transactions/migrations/indexed bounded paths where relevant;
- asynchronous code and Folia/Paper/Velocity ownership are exercised at the correct layer;
- no production database, credential, raw IP/private message, or real player data is used;
- no existing validation rule is suppressed to obtain a pass;
- final CI belongs to the exact reviewed head.

## Sentinel Sim versus Staff tests

Sentinel can add cross-plugin/runtime evidence to Staff, but it does not replace this repository's handwritten tests.

Use Sentinel when the question is about:

- loading a built Staff artifact with realistic dependencies;
- long action sequences/fuzzing;
- plugin compatibility;
- real-Paper startup/restart/config/database behavior;
- production-stack compatibility probing.

Use Staff's own test suite when the question is about Staff business logic, permissions, persistence, commands, adapters, or regressions that can be deterministically exercised in-process.

Sentinel evidence and canonical Staff staging evidence are separate systems. A pass in one must not be reported as a pass in the other.

## Maintenance rule

Whenever Staff gains/removes/renames a user-visible command, permission, major feature family, runtime route, provider mode, or persistence contract:

- update real behavioral tests;
- update the feature-surface contract if the reviewed surface changed;
- update this guide if test commands/layout/meaning changed;
- update the existing Build and Testing documentation when repository-wide validation changed;
- reconcile any relevant Sentinel profile separately rather than moving repository-local tests into Sentinel.
