# `ES-R01` — Billing-independent staging bridge recovery

## 1. Package identity
`ES-R01`; validation-infrastructure recovery; primary `COMP-STAFF`; supporting repository `wsg138/EnthusiaStaff-Staging`; priority 15; not parallel-safe with staging-workflow changes.

## 2. Status
`COMPLETE` as of 2026-08-09.

This owner-directed continuation entered as `ACTIONABLE_CONTINUATION` because ES-R02 had merged the narrow Report integration fixture clock repair and the old current-main public-build blocker had materially changed. Live post-merge Coverage and canonical Pi Staging then proved the exact current `EnthusiaStaff:main` source through the complete public→private acceptance chain.

## 3. Exact completed source and staging controls
- `wsg138/EnthusiaStaff:main` source under proof: `5220f21a44527fdd54bb469c767c40a2f232b171`.
- `wsg138/EnthusiaStaff-Staging:main` controls: `af1bd6d3ae8214e58eb969c23972f872b15c1f18`.
- ES-R02 frozen repair head: `20a4c697e64bebffad6c7bee0132dfd1d1237e9a`.
- ES-R02 normal merge/current-main commit: `5220f21a44527fdd54bb469c767c40a2f232b171`.
- Staging freshness repair remained the already-merged PR #75 / merge `af1bd6d3ae8214e58eb969c23972f872b15c1f18`; no new staging-control repair was required in this continuation.

## 4. ES-R02 prerequisite verification
Post-merge Coverage run `31334653827` completed `SUCCESS` on exact source `5220f21a44527fdd54bb469c767c40a2f232b171`. The trusted Java build is green again, including the ReportStore integration suite that previously failed on the stale fixture clock. ES-R02 is therefore terminal `COMPLETE` and is not reopened by ES-R01.

PR #103 merged normally from frozen head `20a4c697e64bebffad6c7bee0132dfd1d1237e9a`; its live review state had zero review threads, CodeRabbit status `success`, and Codacy Static Code Analysis `success` with zero annotations.

## 5. Canonical current-main proof
The already-triggered push run was consumed rather than duplicated:

- public Pi Staging run: `31334653835`, attempt 1, `SUCCESS`;
- public hosted build job: `93298406398`, `SUCCESS`;
- bridge job: `93299167848`, `SUCCESS`;
- exact source SHA: `5220f21a44527fdd54bb469c767c40a2f232b171`;
- public Actions runtime artifact: `9044030545`, `enthusiastaff-paper-5220f21a4452-31334653835-1`, artifact digest `sha256:22a29b5828d14b1cc0e8b6864f1bc12980b308d4bc7b2517abef8ddf1c355ec7`;
- runtime JAR: `EnthusiaStaff-Paper-0.1.0-SNAPSHOT.jar`, 9,123,435 bytes, SHA-256 `6436f3467ad871b854bf89176fa1f319b7905e5692446a6e72eace6096c601d3`.

The public Java 21 build completed the full Gradle build/test path and runtime-JAR integrity verification before bridge dispatch.

## 6. Bounded transfer, freshness and provenance
The bridge created only the bounded transient transfer for this exact run:

- release ID `367580675`;
- tag `es-r01-staging-31334653835-1`;
- asset ID `507885425`;
- asset `enthusiastaff-staging-5220f21a4452-31334653835-1.zip`;
- transfer SHA-256 `e5828649f8b6dc37a3da9039e62cd08d7091e4230bc3232fbee9ad870d03ff62`.

Private retrieval/verification completed successfully under staging controls `af1bd6d3...`. Therefore the repaired verifier passed its mandatory Release `published_at` publication-freshness gate and its independent Release Asset `created_at` upload-freshness gate while retaining the two-hour maximum age, future-skew protection, exact release/tag/asset/run/source/workflow provenance, digest, size, archive allowlist and manifest checks. No gate was weakened or bypassed.

Sanitized artifact validation also recorded `artifact_allowlist=PASS`, `manifest_validation=PASS`, `checksum_validation=PASS`, `zip_integrity=PASS`, `plugin_main_class=PASS`, and `provider_api_leaks=0` for resolved source `5220f21a...`.

## 7. Correlated trusted private execution
- correlated private run: `31334953968`, `SUCCESS`;
- private job: `93299183621`, `SUCCESS`;
- runner: `Lincoln-PI-4`;
- runner ID: `2`;
- labels: `self-hosted`, `Linux`, `ARM64`, `enthusia-staging`.

The private workflow title bound the run to `EnthusiaStaff bridge 31334653835-1 / 5220f21a44527fdd54bb469c767c40a2f232b171`. Trusted-runner assertion and exact public artifact retrieval both succeeded before database/Paper execution.

## 8. Guarded database and Paper proof
Sanitized evidence proves the required disposable MariaDB/Paper sequence:

1. **Guarded pre-reset:** `phase=before`, `database_identity=verified-disposable`, `objects_removed=0`, `result=PASS`.
2. **Paper cycle 1:** storage bootstrap `PASS`; operational mode `SHADOW_MIGRATION`; EnthusiaStaff emitted `Storage verified; EnthusiaStaff entered SHADOW_MIGRATION`; Flyway created the disposable schema history and applied V1 through V18, ending at v18.
3. **First shutdown/reap boundary:** Java exit code `0`, stopping-server marker `PASS`, all-dimensions-saved marker `PASS`; the harness successfully advanced to cycle 2 only after the first process lifecycle completed.
4. **Paper cycle 2:** storage bootstrap `PASS`; same required operational mode; Flyway reported current schema version 18 and `Schema ... is up to date. No migration necessary`, proving restart/persistence of the initialized storage state.
5. **Second shutdown/reap boundary:** Java exit code `0`, stopping-server marker `PASS`, all-dimensions-saved marker `PASS`.
6. **Final guarded cleanup:** `phase=after`, `database_identity=verified-disposable`, `objects_removed=69`, `result=PASS`.

The terminal sanitized summary is `result=PASS`, `server_starts_completed=2`, `storage_ready_cycles_completed=2`, and `failure_count=0`.

## 9. Sanitized evidence and public correlation
Private sanitized evidence artifact:
- artifact ID `9044106847`;
- name `enthusiastaff-pi-evidence-5220f21a44527fdd54bb469c767c40a2f232b171-31334953968`;
- digest `sha256:273496920e3cb1e36c8f2468ca4dc012015cdde7cea913498c2d823657831def`;
- 26 sanitized files; no credentials or database contents are recorded in canonical package state.

The public bridge observed correlated private conclusion `success`, then completed its success assertion.

## 10. Transfer cleanup
Public bridge cleanup deleted both the exact transient release and tag. Independent post-run API checks return `404 Not Found` for release ID `367580675` and tag `es-r01-staging-31334653835-1`, confirming no transfer residue for this proof.

## 11. Completion decision
Every ES-R01 completion boundary has now genuinely passed for the same exact source SHA:

public GitHub-hosted Java 21 build → verified runtime artifact → bounded transient release/asset → Release `published_at` freshness → asset `created_at` freshness → exact provenance → correlated private execution → trusted `Lincoln-PI-4` → guarded pre-reset → Paper cycle 1/storage readiness → clean shutdown/reap → Paper cycle 2/restart-persistence/storage readiness → clean shutdown/reap → guarded final cleanup → sanitized evidence → correlated public success → transient transfer cleanup.

`ES-R01 = COMPLETE`.

## 12. Scope and preserved boundaries
No product Java behavior, V18-or-older migration, production data, production infrastructure, LiteBans authority, issue #43 behavior, ES-P02 PR #70, or ES-P05 PR #81 was modified in this continuation. The ES-P05 branch remains at `346e764f40b25c98e7d24ce7f863e5629773e814`. `noop-temp-ignore` remains untouched.

Historical ES-R01 terminal branches `package/es-r01-post-merge-finalization` and `package/es-r01-staging-state-update` were checked against current main and remain diverged with unique commits (`ahead_by=6` and `ahead_by=1`, respectively); they therefore do not meet the safe automatic deletion rule and are retained.

## 13. Routing after completion
`EXECUTION-ORDER.md` explicitly states that after ES-R01 completes, normal continuation priority resumes **ES-P02 before ES-P05**. ES-P02's old private-GitHub-hosted billing blocker is materially changed by the now-proven billing-independent bridge, so the next normal sequential worker must reconcile and resume `ES-P02 — Runtime database recovery and Velocity reload` as the highest-priority continuation, rerun its own exact-head gates through the current bridge, and make no claim from ES-R01's evidence on ES-P02's behalf.

This ES-R01 worker stops here and does not start ES-P02 or ES-P05.

## 14. Canonical handoff
`ai-agents/reports/package-handoffs/2026-08-09-es-r01-current-main-canonical-proof-complete.md`
