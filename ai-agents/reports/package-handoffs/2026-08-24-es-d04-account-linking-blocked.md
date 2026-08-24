# ES-D04 Account linking and DiscordSRV migration — blocked handoff

Status: `BLOCKED` / `PARKED_BLOCKED`.

Date: 2026-08-24.

## Selection and live package identity
- Selected through the owner-authorized Discord program lane as an actionable continuation because PR #151 already existed and had safe work remaining.
- Package: `ES-D04 — Account linking and DiscordSRV migration`.
- Original claim `main`: `783925e2b49ab4567bd3c3869e43fc03ff6d285f`.
- Reconciled canonical `main` before this status publication: `f129226ac017c97fc4126629dd0f47bff729abd6`.
- Implementation branch: `package/es-d04-account-linking`.
- Implementation PR: #151, open, non-draft, mergeable at last reconciliation.
- Frozen implementation/product head: `b231022b065b5843d2dd73811dfbf51acba6314b`.
- The implementation branch is intentionally left frozen while canonical Pi evidence is unresolved.

## Collision reconciliation
- D04's changed product paths do not contain website or competition implementation.
- Independently parked ES-X03 PR #139 is not modified.
- Concurrent ES-X04 PR #152 is independent; its overlapping `PaperStorageBindings.java` work is not absorbed or overwritten.
- Concurrent D05 documentation/application-ID work in PR #153 is not absorbed and D05 is not started by this worker.
- Live canonical `main` remained `f129226ac017c97fc4126629dd0f47bff729abd6` throughout the final D04 reconciliation before status publication.

## Completed D04 product scope
The frozen implementation provides:
- two-direction, one-use, five-minute account-link codes with only SHA-256 hashes persisted;
- replacement invalidation, expiry, replay/restart safety, and current/historical ownership;
- online Minecraft-account verification before completion and confirmed self-unlink;
- one authoritative transactional owner for link/unlink/reassignment;
- atomic replacement-main handling when unlinking/reassigning a current main;
- permission-gated audited staff force-link, force-unlink, reassignment and main override/clear operations, with mutations and audits committed atomically;
- lifetime-active PlayTimePlugin main selection through the public `PlaytimeService` contract, 25% hysteresis, deterministic fallback and missing-provider preservation;
- explicit idempotent DiscordSRV snapshot import through public `AccountLinkManager` operations only;
- best-effort temporary current-main mirroring back to DiscordSRV without overwriting authoritative conflicts;
- Paper `/link` and `/unlink` runtime wiring with pre-resolved provider adapters and a thread-safe online-player view;
- forward-only V20 MariaDB schema for durable code and audit state;
- focused domain, Paper and MariaDB/Testcontainers tests for replay, expiry, concurrency, restart, provider presence/absence, import idempotency, historical relink, current-main replacement and audit rollback.

No production import is automatic or executed.

## Harsh-review repairs completed
The package was not frozen at its first green candidate. Valid findings were repaired, including:
1. current-main replacement occurring in a separate transaction before unlink/reassign;
2. staff main-account mutations committing separately from their audit records;
3. async command workers resolving Bukkit player/provider state off-thread;
4. account-link runtime graph construction on every command execution;
5. dynamic SQL predicate construction in D04 persistence paths;
6. elapsed link-code expiry state not durably committing before failure was reported;
7. MariaDB deadlock victims not being retried around complete link mutation transactions;
8. stale plugin metadata assumptions and targeted rollback/history coverage gaps.

CodeRabbit status is successful on the frozen head. All substantive code threads are resolved. Two live threads remain only on stale orchestration records in the frozen implementation branch. The canonical blocked state is published on `main` through a separate documentation-only status PR rather than moving the product head before exact-SHA staging evidence is recovered.

## Exact-head hosted validation
Frozen product head `b231022b065b5843d2dd73811dfbf51acba6314b`:

### Coverage/full build
- Workflow run: `32738304907`.
- Job: `97466391922`.
- Result: PASS.
- Exact source checkout: frozen SHA above.
- Java: Temurin `21.0.12+8`.
- Command: `clean build jacocoAggregateReport runtimeJars --no-daemon --no-build-cache --no-configuration-cache --console=plain`.
- Full repository build/tests: PASS, including MariaDB 11.8.3 Testcontainers integration.
- Runtime provider-contract inspection: 24 checked source types, 0 leaks.
- Paper runtime: 9,397,839 bytes; SHA-256 `f3f76799987541b6d7db52fb31f48938e79ddd81daa5733a8dc4404a27439d1c`.
- Velocity runtime: 8,118,226 bytes; SHA-256 `8f1d750862506d3cc0e281fbd3f03abff2a8d188770fec1225e771024e21e7a3`.
- Aggregate JaCoCo: 51.09% line, 41.59% branch, 53.44% instruction.
- Validation artifact: ID `9524397425`, digest `sha256:230de565c87f1939dd0f06f2bcb028a394d96e73e43237fb43b2f02adccbd6c8`.
- Codacy coverage upload/final notification: PASS on exact source.

### Sentinel
- Artifact workflow run: `32738306003`.
- Artifact job: `97466394689`.
- Result: PASS on exact frozen SHA.
- Artifact: `9524138779`, `enthusiastaff-sentinel-paper`, digest `sha256:4f472f5a20c9d825ad7129bbf0bc4727740a4166f9d1c697c843df5b84020b67`.
- Sentinel command source: PR comment `5395469895`, exact body `@enthusia-sentinel test restart`.
- Durable Sentinel job: `225`.
- Terminal result: exactly `PAPER_RESTART_OK` on the frozen head.

Sentinel evidence is retained only for the profile it actually proves. It is not canonical Pi staging.

## Required canonical Pi blocker
D04 changes Paper runtime behavior and MariaDB/Flyway persistence, so `VALIDATION-POLICY.md` makes safe canonical Pi boot/restart an applicable independent package gate. The package cannot merge without a valid exact-source Pi result.

The available connected GitHub surface can retrieve a workflow after its run ID is known, but the exposed commit-workflow listing covers ordinary PR-head workflows and does not expose the `pull_request_target` run listing used by `.github/workflows/pi-staging-check.yml`. Direct list-workflow-run access is not available through this connector. Public search/indexing also exposes no D04 Pi run. No exact public `Pi Staging` run ID or correlated private `wsg138/EnthusiaStaff-Staging` run ID for frozen D04 is recorded in PR #151 or repository state.

Therefore:
- canonical Pi is **not** called passed;
- a public dispatch/unknown run is **not** inferred as passed;
- Sentinel `PAPER_RESTART_OK` is **not** substituted;
- no owner-approved infrastructure exception is claimed or applicable from the evidence available;
- PR #151 remains open and unmerged.

## Exact unblock condition
Resume D04 only after the exact external/tool condition changes enough to do one of the following safely:
1. identify and inspect an already-existing canonical public `Pi Staging` run for exact source `b231022b065b5843d2dd73811dfbf51acba6314b`, including its correlated private run; or
2. execute the canonical public workflow for that exact source through an authorized supported path, never by directly dispatching the private staging workflow.

A passing result must verify the exact source and same-repository PR provenance, public hosted artifact generation, bounded transfer, correlated private execution on trusted `Lincoln-PI-4`, guarded MariaDB reset, Flyway/schema behavior, two Paper boot/restart cycles as required by the current harness, clean shutdown/process reap, critical-failure scans, final database cleanup, sanitized evidence upload, and public transient-transfer cleanup.

After a real Pi PASS, re-reconcile current `main`, PR #151 head and competing branches; resolve the two remaining tracking-only review threads; apply only genuinely new valid findings; rerun any gates invalidated by any necessary change; merge #151 with a normal merge commit; verify the frozen/validated product content is contained with no unique implementation work; safely clean the temporary branch; mark D04 `COMPLETE`; publish terminal canonical state; and stop without beginning D05.

## Migration and production boundary
- Canonical `main` remains at V19 while D04 is unmerged.
- Frozen D04 adds only `V20__discord_account_linking.sql`.
- No migration checksum/history was rewritten.
- No production database, DiscordSRV mapping, Discord bot configuration/token, private user data, deployment, role-sync authority, punishment authority, LiteBans authority, issue #43 acceptance, or cutover was changed.
- LiteBans remains authoritative and issue #43 remains open.

## Routing after parking
- `ES-D04`: `BLOCKED` / `PARKED_BLOCKED` on the exact canonical-Pi observability/execution condition above.
- `ES-D05`: still `READY`; a future Discord-program worker may select it while D04 remains parked. This worker did not start it.
- `ES-D06` and `ES-D13`: remain dependency-blocked until D04 and D05 are complete.
- ES-X03/ES-X04, website and competition work remain independent.

## Stop condition
The D04 implementation PR remains preserved at the exact frozen product head. The persistent blocked state is published separately to `main` as required by `WORKER-PROTOCOL.md`. After that status publication is normally merged and verified, this worker stops and does not begin another package.
