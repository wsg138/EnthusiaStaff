# EnthusiaStaff workspace state

Last updated: 2026-08-05

This is a routing record. Live GitHub and `ai-agents/work-packages/PACKAGE-REGISTRY.md` must be reconciled before acting.

## Repository

| Field | Value |
| --- | --- |
| Repository | `wsg138/EnthusiaStaff` |
| Default branch | `main` |
| ES-P01 starting `main` | `e434b3dedc003d1d5b3def64f38cc7465752b0e5` |
| ES-P01 implementation merge | `203b2854d5546a6d3744037c367099129654b42a` |
| Plugin version | `0.1.0-SNAPSHOT` |
| Java/runtime | Java 21; Paper/Leaf backends, Velocity, MariaDB |
| Highest migration | `V16`; V1–V16 remain immutable |
| Issue #43 | Open and deferred |

## Current package state

`ES-P01 COMPLETE — PR #68 merged normally. The exact reviewed head is contained in main, no package-only commit remains, the implementation branch is deleted, and the documented infrastructure exception does not claim a Pi pass. No package is active.`

| Field | Value |
| --- | --- |
| Canonical package status | `ai-agents/work-packages/PACKAGE-REGISTRY.md` |
| Completed package | `ES-P01 — Exact-sanction appeal isolation` |
| Implementation PR | `#68 — merged normally` |
| Frozen reviewed product head | `5a668d5fecd2bb809a31fdb7ddcb7e27b536a7be` |
| Final reviewed and validated PR head | `ffa8ae4e3ffbfcff39698caa6bbfb61ec40ee179` |
| Implementation merge commit | `203b2854d5546a6d3744037c367099129654b42a` |
| Containment | Merge parents are starting `main` and the exact reviewed head |
| Divergence | Package head is zero commits ahead of `main` |
| Implementation branch | `package/es-p01-appeal-isolation` — deleted |
| Finalization branch | `package/es-p01-finalization` — documentation only |
| Package handoff | `ai-agents/reports/package-handoffs/2026-08-05-es-p01-appeal-isolation.md` |
| Infrastructure disposition | `OWNER-APPROVED INFRASTRUCTURE EXCEPTION — STAGING DEFERRED` |
| Deferred package | `ES-V02 — Distributed and Java/Bedrock staging` |
| Newly ready packages | `ES-P02`, `ES-X05` |
| Active package | `NONE` |

## Exact-head hosted validation

Final head `ffa8ae4e3ffbfcff39698caa6bbfb61ec40ee179` passed Coverage run `31067403138`, job `92507961739`:

- Temurin Java `21.0.11+10`.
- `./gradlew clean build jacocoAggregateReport runtimeJars --no-daemon --no-build-cache --no-configuration-cache --console=plain`.
- `BUILD SUCCESSFUL` in 5m33s; 49 tasks, 40 executed and 9 up-to-date.
- All module and MariaDB/Testcontainers tests passed.
- Migration integrity and runtime-JAR inspection passed.
- 24 provider API source types checked; zero leaks.
- Paper JAR SHA-256 `c2c44ceb3d2ba9888aa167c3731a746008413336dd62d421f5b16f15dd8bb426`.
- Velocity JAR SHA-256 `43135fb89c02f0c2418b08a0ef120987a65e5143373dfc3af0b3d56df0a018d8`.
- JaCoCo: 47.07% lines, 38.17% branches, 49.81% instructions.
- Artifact `8954423281`, SHA-256 `4c90a22bd42fc63ae7f90a268f4847c9181d37ba7d4bc4b2aaa1fad35fc9b514`.
- Codacy and CodeRabbit passed; all six review threads were resolved.

## Owner-approved infrastructure exception

Status: `OWNER-APPROVED INFRASTRUCTURE EXCEPTION — STAGING DEFERRED`.

- Owner approval: `wsg138`, assigned-package instruction, 2026-08-05.
- Parent run `31067402120`, parent job `92507922737`, staging run `31067405608`.
- Build job `92507935906`: `runner_id: 0`, empty runner name, `steps: []`.
- Pi job `92507942018`: skipped, no runner, `steps: []`.
- Diagnostics artifact `8954313460`, SHA-256 `e5d73eda6fb3481b4a5bb9d78b8e300ebec3351e79748704c3c17fdc5f4bb58b`.
- No product build, test, migration, artifact, boot, or restart step executed.
- Deferred obligation: distributed Pi build/boot/restart and Java/Bedrock staging evidence in `ES-V02`.

The Pi gate is **not passed**. The exception is not staging verification, production verification, or proof of a successful boot. It cannot cover an allocated runner or any executed product, test, analysis, review, migration, artifact, or documentation failure.

## Dependency routing

- `ES-P02 — Runtime database recovery and Velocity reload`: `READY`.
- `ES-X05 — Website UX, authentication, and appeals`: `READY`.
- `ES-V02 — Distributed and Java/Bedrock staging`: `DEFERRED`; not started.
- No package is active.

## Boundaries

- No migration was added or edited; V16 remains highest.
- No deployment, private-data access, authority activation, issue #43 work, shadow period, cutover, rollback, or dependent package start occurred.
- Stop after ES-P01 finalization.
