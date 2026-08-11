# ES-P06 package handoff — Discord notification delivery completion

Date: 2026-08-10
Package: `ES-P06`
Status: `COMPLETE`
Classification: terminal

## Selection and scope

- Startup reconciliation found no higher-priority actionable continuation. `ES-X01` remained `PARKED_BLOCKED`, while `ES-P06` was dependency-complete and the lowest-priority eligible `READY` package at priority 60.
- Original/pre-merge `main`: `449461b410c0b06d27bfd98a2940023aa0d9913f`.
- Package branch: `package/es-p06-discord-delivery`.
- Implementation PR: #115.
- Final frozen implementation head: `7e21edb1d32a75727dc65df826f9de964adcfff3`.
- Normal implementation merge: `d78a5165493f810dbb3fd4d11e5e9d4b80ffed71`.
- V18 remained the immutable Flyway ceiling; ES-P06 added no migration.
- Issue #43 remains open/deferred and LiteBans remains authoritative.

## Completed behavior

- Preserved the existing durable MariaDB `discord_outbox` producer/store, row-leasing, bounded retry, persisted circuit, dead-letter, status, and bounded manual-retry model.
- Replaced raw stored-JSON webhook output with bounded destination-specific allowlisted rendering. Nested/private evidence has no generic fallback renderer.
- Documented and verified current event routing for punishment, report, freeze/vanish/staff-mode, and reserved alert destinations.
- Added explicit process-environment route classification: `STAGING` requires exact approved staging hosts; `PRODUCTION` accepts only exact Discord webhook hosts/path shape. Production Discord hosts/subdomains cannot be relabeled as staging.
- Enforced HTTPS and rejected unsafe user-info/query/fragment routes, alternate production ports/paths, mixed route classes, and redirects. Redirect following remains disabled.
- Hardened URI/config failures so raw secret-bearing webhook strings are not retained in exception causes/messages.
- Added bounded rendering/body behavior and disabled Discord mention parsing through `allowed_mentions.parse=[]`.
- Hardened Unicode normalization/truncation so valid surrogate pairs are preserved and unmatched surrogate code units become replacement characters rather than malformed output.
- Closed the Java 21 `HttpClient` owned by production transport when the Discord worker closes; injected fake exchanges remain non-owning.
- Added focused worker tests for success, poison payloads, redirect failure, circuit deferral, and shutdown ownership; route/parser/renderer tests cover HTTPS/host/path/privacy/Unicode behavior.
- Added MariaDB restart lease recovery and concurrent-claim coverage. Existing integration coverage continues to prove delivery, circuit/dead-letter, and bounded retry behavior.
- Preserved honest **at-least-once** external delivery semantics: leases stop active concurrent duplicate claims, but a process can die after Discord accepts HTTP and before the local delivered acknowledgment commits.
- Confirmed existing Velocity reload coordination treats Discord file-backed settings as restart-required; environment-backed route authorization is also process-scoped. No partial hot-reload of the live worker occurs.
- Documented `/estaff discord status` and bounded `/estaff discord retry <destination>` recovery procedures.
- No production Discord endpoint was contacted; delivery acceptance used isolated fake/in-memory transport.

## Review and fix history

- An early concurrency integration test incorrectly assumed `FOR UPDATE SKIP LOCKED` fairness. It was corrected to prove the actual contract: no duplicate lease plus recoverability of a row temporarily skipped under concurrency.
- Shared Testcontainers state was isolated so restart/concurrency scenarios could not contaminate each other.
- One candidate produced 18 Codacy findings; all were fixed before the final freeze. The final exact-head static check is clean.
- A substantive CodeRabbit review found three valid defects: possible surrogate-pair splitting during truncation, null staging endpoint normalization before validation, and an owned Java 21 `HttpClient` not closed during worker shutdown. All were fixed with regression coverage; all three threads are resolved.
- Follow-up legitimate package commits refined normalization-before-truncation and unmatched-surrogate handling.
- CodeRabbit's final incremental rerun was rate-limited. It is explicitly **not** treated as a passing review result. Its generic docstring-coverage warning is not a repository/package correctness gate and did not identify a functional/security/lifecycle defect.
- Exact-head manual review of the complete final diff was recorded on PR #115 after all fixes. It covered scope, lifecycle/shutdown, concurrency/leases/idempotency, restart/reload, at-least-once semantics, route/TLS/redirect policy, privacy/secret exposure, bounds, Unicode, tests, and documentation. No further valid defect was found.
- Final valid unresolved review-thread count: zero.

## Frozen exact-head hosted evidence

Frozen implementation head: `7e21edb1d32a75727dc65df826f9de964adcfff3`.

Passing evidence:

- Validate Wiki run `31450684263` / job `93654254065` — success.
- Sentinel PR artifact-build run `31450684268` / job `93654253835` — success. This is the repository-configured exact Paper artifact check only; no live Sentinel restart is claimed as proof of the Velocity Discord worker.
- Coverage run `31450684287`, attempt 2, job `93657195445` — success on Temurin 21.0.11+10 with `./gradlew clean build jacocoAggregateReport runtimeJars --no-daemon --no-build-cache --no-configuration-cache --console=plain`.
- Full unit/MariaDB/Testcontainers suite, warnings-as-errors compilation, runtime-JAR integrity/provider-leak checks, aggregate JaCoCo, validation artifact upload, and Codacy coverage upload all passed.
- Aggregate JaCoCo: 47.56% lines, 38.74% branches, 50.23% instructions.
- Paper JAR: 9,148,983 bytes, SHA-256 `74fbc2f1ac487a4191ccc5d83b6d7c68ba857dd4c2fd8b060c13fff138c0fe33`, provider API leaks 0.
- Velocity JAR: 7,906,399 bytes, SHA-256 `e3705f7729d3e1e48797635d4c88345aea68e9e3845bce47547c6879ab9920e2`, provider API leaks 0.
- Hosted validation artifact `9086657350`, digest `sha256:329ed42f108776e19713bac57dc36b47020f74dd78571296fc9a28cfde0be248`.
- Codacy static check `93654428681` — success, zero findings/annotations.
- Codacy diff coverage `93658340705` — 72.52%, success; no diff gate configured.
- Codacy coverage variation `93658340416` — +0.43%, success against the -1.0% target.

Non-passing final-head history retained explicitly:

- Coverage run `31450684287` attempt 1 / job `93654716868` failed in untouched `PunishmentRequestAlertStoreIntegrationTest.concurrentClaimsAreDeliveredAtMostOnceAcrossWorkers()` on a transient MariaDB record-change race. No ES-P06 changed file touches that store/test. This remains failed evidence, not a pass. One unchanged-SHA rerun was performed; attempt 2 passed the entire hosted suite.
- All earlier candidate-head checks, cancellations, and superseded Pi/build runs remain non-final and are not reused.

## Canonical public-to-private Pi evidence

- Public Pi run `31450682744`, attempt 1 — success.
- Exact-source public build job `93654251245` — success for source `7e21edb1d32a75727dc65df826f9de964adcfff3`.
- Public build artifact `9086332977`, digest `sha256:a5b89464ecbe6f132b9893ce6fe61b623b3b011975a0bf7a8652e30e23d039c8`.
- Public bridge job `93655372240` — success through exact artifact download, transient release publication, private dispatch/correlation, private-verdict wait, transient transfer cleanup, and final publication.
- Correlated private run `31451077909` / job `93655393387` — success on trusted `Lincoln-PI-4` using staging-controls head `932b372a0e5337b2a497b169a293e6b31f3e5d85`.
- Exact source, PR, public-build run/attempt, artifact provenance, allowlist, and checksum verification passed.
- Privately tested Paper runtime SHA-256: `87f6c6ec53aeadf4875345715f276bfd7d076c98fc5a6428aaf714d808b8c6ae`.
- Two Paper starts and two storage-ready cycles completed in `SHADOW_MIGRATION` mode.
- Cycle 1 validated and applied V1 through V18 to the disposable MariaDB target.
- Restart verified schema v18 current with no migration necessary and re-entered `SHADOW_MIGRATION`.
- Both shutdown checks and critical-failure scans passed with `failure_count=0`.
- Guarded cleanup verified the disposable target and removed 69 database objects.
- Sanitized evidence artifact `9086623670`, digest `sha256:98627335ce81a862a2d77287548a03d2ef85e238c8d14e5b4e932d471b230ce7`.

The canonical Pi result is exact package/runtime/provenance/restart evidence. It is not described as a live production Discord or Velocity webhook delivery test; the Discord delivery boundary is proven by the isolated fake transport and MariaDB tests.

## Merge, containment, and cleanup

- Final pre-merge reconciliation confirmed `main` still exactly `449461b410c0b06d27bfd98a2940023aa0d9913f`, PR #115 mergeable, frozen head unchanged, exact-head hosted/static/Pi evidence passing, and zero valid unresolved review findings.
- PR #115 merged with the required normal merge method as `d78a5165493f810dbb3fd4d11e5e9d4b80ffed71`.
- Merge parent 1: pre-merge `main` `449461b410c0b06d27bfd98a2940023aa0d9913f`.
- Merge parent 2: frozen feature head `7e21edb1d32a75727dc65df826f9de964adcfff3`.
- Feature and merge trees are identical: `8f7b7dae841779af573012df3e30fb6302580654`. The merge is exactly one commit ahead of the feature with zero product-file delta.
- GitHub automatically deleted `package/es-p06-discord-delivery`; branch lookup returns 404.
- External parity is not applicable to internal `COMP-STAFF` scope.

## Terminal routing

- ES-P06 is `COMPLETE`.
- ES-P08 remains dependency-complete and `READY` at priority 70.
- ES-X01 remains `BLOCKED` / `PARKED_BLOCKED` on the unresolved supported RoseChat repository/default branch/source/AGENTS contract.
- ES-X02 remains blocked by incomplete ES-P08; ES-X03/ES-X04 remain blocked by ES-P08/ES-X02.
- ES-V02 is no longer blocked by ES-P06 but remains parked on incomplete ES-X01, ES-X03, and ES-X04. ES-V03 remains blocked by ES-P08/ES-X02/ES-X03/ES-X04.
- ES-A01 remains deferred on ES-V02/ES-V03, owner authorization, and issue #43. ES-QA01 remains blocked by ES-A01.
- V18 remains immutable. Issue #43 remains open/deferred and LiteBans authoritative.

## Systems not disturbed

- No ES-P08 or second-package implementation was started.
- No ES-X01 provider API/repository was invented.
- No V1–V18 migration was edited or repaired.
- No production Discord route, production data, deployment, production shadow window, cutover, LiteBans removal, punishment-authority change, or source rewrite occurred.
- Legitimate shared staging work was not cancelled or preempted to obtain the Pi result.

This documentation-only terminal publication closes ES-P06. A future sequential worker must reconcile live GitHub before selecting another package; absent a new actionable continuation, current routing places ES-P08 next.