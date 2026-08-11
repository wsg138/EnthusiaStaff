# `ES-P06` — Discord notification delivery completion

## 1. Package identity
`ES-P06`; Internal; primary `COMP-STAFF`; priority 60.

## 2. Status
`COMPLETE`.

Final frozen implementation head: `7e21edb1d32a75727dc65df826f9de964adcfff3`.
Implementation PR: `#115`.
Normal implementation merge: `d78a5165493f810dbb3fd4d11e5e9d4b80ffed71`.
Original/pre-merge `main`: `449461b410c0b06d27bfd98a2940023aa0d9913f`.
Canonical terminal handoff: `ai-agents/reports/package-handoffs/2026-08-10-es-p06-discord-delivery-complete.md`.

## 3. Objective
Complete durable Discord notification rendering, delivery, retries, dead letters, reload/restart behavior, route authorization, and duplicate-safe cross-server operation without contacting production Discord during package acceptance.

## 4. Completed behavior
- Existing MariaDB durable producer, lease, retry, circuit, dead-letter, status, and bounded manual-retry paths remain authoritative.
- Velocity renders explicit destination-specific allowlisted projections rather than posting raw `payload_json`; nested/private evidence has no fallback renderer.
- `punishments`, `reports`, `logs-staffmode`, and reserved `alerts` route policy is explicit and documented with the currently produced event matrix.
- Route authorization is process-environment-backed. `STAGING` requires an exact approved-host allowlist; production Discord hosts and their subdomains cannot be relabeled as staging. `PRODUCTION` accepts only exact Discord webhook hosts/path shape on HTTPS/default port.
- HTTP, user-info/query/fragment-bearing routes, unsafe production hosts/paths, mixed route classes, malformed URIs, and redirects fail closed. Redirect following is disabled, so the webhook credential is not forwarded to another target.
- URI/configuration failures do not retain the raw secret-bearing URI in exception causes or messages.
- Webhook bodies are bounded and disable Discord mention parsing with `allowed_mentions.parse=[]`.
- Unicode normalization/truncation preserves valid surrogate pairs and replaces unmatched surrogate code units rather than emitting malformed text.
- The Java 21 `HttpClient` owned by the production transport is closed during worker shutdown; injected fake exchanges remain externally owned.
- Worker retry/circuit handling is bounded; poison payloads and redirect failures enter the durable failure path; rows become inspectable/recoverable dead letters after the configured attempt ceiling.
- Concurrent workers use MariaDB leasing/`FOR UPDATE SKIP LOCKED`; expired leases recover after runtime replacement. Delivery is deliberately documented as **at least once**, because an external HTTP success can occur immediately before a process dies prior to the database acknowledgment.
- Existing Velocity reload coordination treats Discord file-backed settings as restart-required, and route class/approved-host environment values are also process-scoped. `/estaff reload` therefore cannot partially republish a live Discord worker policy.
- Existing `/estaff discord status` and bounded `/estaff discord retry <destination>` operator recovery paths are documented.
- Source and Wiki runbooks cover event routing, destination policy, TLS/redirect/privacy controls, retry/circuit/dead-letter operations, restart/reload boundaries, status/recovery, and troubleshooting.

## 5. Included audit IDs
`AUD-REPORT-004`, `AUD-DISCORD-001`, `AUD-DISCORD-002`.

## 6. Exact-head automated validation
Frozen implementation head: `7e21edb1d32a75727dc65df826f9de964adcfff3`.

Passing final evidence:

- Validate Wiki run `31450684263`, job `93654254065` — success.
- Sentinel PR artifact-build run `31450684268`, job `93654253835` — success. This is the configured exact-Paper-artifact check only; no live Sentinel restart is claimed or substituted for canonical staging because ES-P06's changed runtime is Velocity-side.
- Hosted Coverage run `31450684287`, attempt 2, job `93657195445` — success on Temurin Java 21.0.11+10. `./gradlew clean build jacocoAggregateReport runtimeJars --no-daemon --no-build-cache --no-configuration-cache --console=plain` completed successfully with the full unit/MariaDB/Testcontainers suite, warnings-as-errors compilation, runtime-JAR inspection, aggregate coverage, validation artifact upload, and Codacy coverage upload.
- Aggregate JaCoCo: **47.56% lines**, **38.74% branches**, **50.23% instructions**.
- Paper runtime JAR: 9,148,983 bytes; SHA-256 `74fbc2f1ac487a4191ccc5d83b6d7c68ba857dd4c2fd8b060c13fff138c0fe33`; provider API leaks 0.
- Velocity runtime JAR: 7,906,399 bytes; SHA-256 `e3705f7729d3e1e48797635d4c88345aea68e9e3845bce47547c6879ab9920e2`; provider API leaks 0.
- Hosted validation artifact `9086657350`, digest `sha256:329ed42f108776e19713bac57dc36b47020f74dd78571296fc9a28cfde0be248`.
- Exact-head Codacy static check `93654428681` — success, zero issues/annotations.
- Codacy diff coverage `93658340705` — 72.52%, success; no diff-coverage gate configured.
- Codacy coverage variation `93658340416` — +0.43%, success against the -1.0% target.

## 7. Review disposition
- An earlier candidate produced 18 valid Codacy findings; all were fixed before the frozen head and the final exact-head Codacy static result is clean.
- A substantive CodeRabbit review found three valid defects: UTF-16 truncation could split a surrogate pair, staging route validation did not explicitly reject a null endpoint before host normalization, and the Java 21 `HttpClient` owned by the Discord transport was not closed at worker shutdown. All three were fixed with regression coverage and all three review threads are resolved.
- Follow-up in-scope commits hardened normalization-before-truncation and unmatched-surrogate handling without broadening package scope.
- CodeRabbit's later incremental rerun on the frozen head was rate-limited. It is explicitly **not** recorded as a review pass. A generic docstring-coverage warning is not a repository/package correctness gate and did not identify a security, lifecycle, or functional defect.
- A complete exact-head manual review was recorded on PR #115 after the fixes, covering scope, lifecycle/shutdown, leases/concurrency/idempotency, at-least-once semantics, reload/restart, HTTPS/host/redirect policy, secret/privacy exposure, bounds, Unicode, tests, and documentation. No additional valid defect was found.
- Final valid unresolved review-thread count: **zero**.

## 8. Non-passing/superseded evidence
- All checks on earlier implementation heads are superseded and are not final evidence.
- Coverage run `31450684287` attempt 1 / job `93654716868` failed in untouched `PunishmentRequestAlertStoreIntegrationTest.concurrentClaimsAreDeliveredAtMostOnceAcrossWorkers()` on a transient MariaDB “record has changed since last read” race. No ES-P06 changed file touches that store/test. The failure remains non-passing history. The same unchanged frozen SHA was rerun once; attempt 2 completed the full suite successfully.
- Earlier package candidates included a concurrency test that incorrectly assumed `SKIP LOCKED` fairness; the test was corrected to prove no duplicate lease and recoverability instead. Failed/cancelled/superseded runs from that history are not reused.
- The final rate-limited CodeRabbit incremental run is unavailable review evidence, not a pass.

## 9. Canonical Pi staging evidence
Canonical public-to-private Pi validation passed for the exact frozen head without contacting any production Discord route.

- Public Pi run `31450682744`, attempt 1 — success end-to-end.
- Public exact-source build job `93654251245` — success; exact source `7e21edb1d32a75727dc65df826f9de964adcfff3`.
- Public build artifact `9086332977`, digest `sha256:a5b89464ecbe6f132b9893ce6fe61b623b3b011975a0bf7a8652e30e23d039c8`.
- Public bridge job `93655372240` — success, including artifact publication, private dispatch/correlation, private-verdict wait, transient transfer cleanup, and final success publication.
- Correlated private run `31451077909`, job `93655393387` — success on trusted `Lincoln-PI-4` using staging-controls head `932b372a0e5337b2a497b169a293e6b31f3e5d85`.
- Exact bridge source/PR/provenance/checksum verification passed. The privately tested Paper runtime SHA-256 was `87f6c6ec53aeadf4875345715f276bfd7d076c98fc5a6428aaf714d808b8c6ae`.
- Two Paper starts and two storage-ready cycles completed in required `SHADOW_MIGRATION` mode.
- Cycle 1 validated 18 migrations and applied V1 through V18 to the disposable database.
- Cycle 2 verified schema v18 current with no migration necessary and re-entered `SHADOW_MIGRATION`.
- Both shutdown checks and critical-failure scans passed; `failure_count=0`.
- Guarded post-test cleanup verified the disposable target and removed 69 database objects.
- Sanitized private evidence artifact `9086623670`, digest `sha256:98627335ce81a862a2d77287548a03d2ef85e238c8d14e5b4e932d471b230ce7`.

The canonical Pi gate proves exact package/runtime/provenance/restart safety for the configured Paper bridge. ES-P06's Discord delivery boundary itself is exercised by the isolated/in-memory fake transport and MariaDB tests; the Pi result is not misrepresented as a production Discord or live Velocity webhook test.

## 10. Migration and production boundaries
- ES-P06 added no migration. V18 remains the immutable migration ceiling.
- No Flyway repair or existing migration rewrite occurred.
- No production Discord route was contacted. The package's delivery tests used isolated fake/in-memory transport only.
- `discord.enabled=false` remains the safe default.
- Issue #43 remains open/deferred; LiteBans remains authoritative.
- No production data, production shadow window, deployment, punishment-authority change, cutover, or source rewrite occurred.
- Broader representative distributed Java/Bedrock acceptance remains assigned to `ES-V02`.

## 11. Merge, containment, and cleanup
PR #115 merged with the required normal merge method as `d78a5165493f810dbb3fd4d11e5e9d4b80ffed71`.

The merge has exactly two parents:
1. `449461b410c0b06d27bfd98a2940023aa0d9913f` — pre-merge `main`.
2. `7e21edb1d32a75727dc65df826f9de964adcfff3` — frozen validated feature head.

The feature head and merge commit have the same tree `8f7b7dae841779af573012df3e30fb6302580654`; containment is therefore one merge commit ahead with zero product-file delta. GitHub automatically deleted `package/es-p06-discord-delivery`; the branch lookup returns 404. External component parity is not applicable to this internal package.

## 12. Dependency result and stop state
- ES-P06 is `COMPLETE`.
- `ES-P08 — Item confiscation and restoration` remains dependency-complete and `READY` at priority 70.
- `ES-X01 — RoseChat provider and communication integration` remains `BLOCKED` / `PARKED_BLOCKED` on the unresolved supported integration repository/default branch/source/AGENTS contract.
- `ES-X02`, `ES-X03`, `ES-X04`, `ES-V02`, `ES-V03`, `ES-A01`, and `ES-QA01` remain parked on their documented dependencies/external conditions. ES-V02 is no longer blocked by ES-P06, but still depends on incomplete ES-X01, ES-X03, and ES-X04.

This worker completed exactly ES-P06. It does not activate, prepare, stage, or partially implement ES-P08 or any other package.