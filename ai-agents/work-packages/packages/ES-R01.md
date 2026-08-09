# `ES-R01` — Billing-independent staging bridge recovery

## 1. Package identity
`ES-R01`; validation-infrastructure recovery; primary `COMP-STAFF`; supporting repository `wsg138/EnthusiaStaff-Staging`; priority 15; not parallel-safe with staging-workflow changes.

## 2. Status
`BLOCKED` / `PARKED_BLOCKED` as of 2026-08-09 after an owner-directed `ACTIONABLE_CONTINUATION` repaired the newly exposed transient-release freshness verifier defect. The repair is merged and its regression/safety suite is green. ES-R01 cannot yet obtain the mandatory fresh current-`main` end-to-end proof because current `EnthusiaStaff:main` itself fails the trusted public Java build in two ReportStore integration tests before any bridge artifact is created.

## 3. Objective
Provide a billing-independent canonical staging route that builds the exact authorized EnthusiaStaff source SHA on public GitHub-hosted Java 21 infrastructure, transfers only a bounded verified artifact to private staging, re-verifies provenance on trusted `Lincoln-PI-4`, and requires guarded disposable database plus two-cycle Paper restart/persistence acceptance.

## 4. Material blocker history correction
The former package text claimed the exact unblock was restoration of MariaDB reachability from `Lincoln-PI-4`. That became stale before this continuation.

Historical ES-P05 proof at source `ebfbaa31d3de2b6a28b9dcbaf2c4366ee8e801e2` established the database/Paper path was usable:
- public Pi Staging run `31301426684`;
- private run `31301734048`, job `93215499833`;
- trusted `Lincoln-PI-4`, runner ID `2`;
- exact artifact/provenance verification passed;
- guarded pre-reset passed;
- Paper cycle 1 reached readiness, EnthusiaStaff enabled, MariaDB/Flyway initialized through V18;
- clean shutdown/full reap;
- Paper cycle 2 reached readiness with restart/persistence/schema-v18 proof;
- second clean shutdown/full reap;
- guarded final database cleanup passed;
- sanitized evidence artifact `9034945235`, digest `sha256:a81af3154c7e561c5ea09ed7072c970d483b25b63e217f34f1976132bab4ef3e`.

Therefore ES-R01 was correctly reclassified `ACTIONABLE_CONTINUATION` for this worker instead of being left parked on the obsolete MariaDB condition.

## 5. Newly exposed bridge defect and root cause
ES-P05 final candidate `346e764f40b25c98e7d24ce7f863e5629773e814` produced public run `31330788773`; public hosted build job `93288608088` succeeded. Bridge job `93289540403` created transient release `367563110`, tag `es-r01-staging-31330788773-1`, asset `507820281` / `enthusiastaff-staging-346e764f40b2-31330788773-1.zip`, then dispatched private run `31331175023` / job `93289556545` to trusted `Lincoln-PI-4` runner ID `2`. Private verification failed before database/Paper execution with:

`release created_at is expired for the staging bridge`

Investigation confirmed the verifier applied its two-hour transport freshness bound to GitHub Release `created_at`. GitHub documents Release `created_at` as the date of the commit used for the release, not the publication time of the release. A newly published transient release pointing to an older workflow/control commit can therefore legitimately have an old `created_at` and be rejected immediately.

The correct transport semantics are:
- release publication freshness: required valid Release `published_at`;
- asset upload freshness: Release Asset `created_at`;
- retain the existing two-hour maximum age and five-minute future-clock-skew guard.

## 6. Repair
Private staging PR #75 changed only:
- `scripts/fetch-enthusiastaff-bridge-artifact.sh`;
- `tests/test-staging-bridge-artifact.sh`.

The verifier now requires `release.published_at`, rejects missing/malformed/timezone-less publication timestamps, retains future-skew and two-hour expiry enforcement, and continues independently validating asset `created_at`. Exact release/tag/asset identity, repository/workflow/run/attempt/source SHA, same-repository PR provenance, canonical download URL, digest, size, archive allowlist, manifest, runtime digest/size and cleanup boundaries remain intact.

Frozen staging repair head: `19e38d6851367d835cfe50fc29e9f95a0936f66d`.
Normal staging merge commit: `af1bd6d3ae8214e58eb969c23972f872b15c1f18`.

## 7. Regression and safety evidence
Staging Controls CI run `31332576934`, job `93293056853`, executed on `Lincoln-PI-4` and passed the full staging-control suite. Focused bridge coverage proves:
1. old target commit + freshly published release + fresh asset = ACCEPT;
2. fresh release + expired asset = REJECT;
3. expired published release + fresh asset = REJECT;
4. future release publication beyond skew = REJECT;
5. future asset timestamp beyond skew = REJECT;
6. missing/null publication timestamp = REJECT;
7. mismatched release ID/tag = REJECT;
8. mismatched asset ID/name = REJECT;
9. digest mismatch = REJECT;
10. stale/moved PR provenance = REJECT;
11. wrong public workflow/control/source provenance = REJECT;
12. existing successful workflow-dispatch and pull-request-target fixtures continue to pass.

The same run also passed source-selection, database-wrapper, storage-readiness, successful-cycle, issue-43 prerequisite, multi-repository policy and Sentinel fixtures, including 292 isolated Sentinel unit tests. Repository-equivalent Bash validation executed the scripts under strict shell settings. No secret exposure was observed. CodeRabbit was green and there were zero valid unresolved review threads before merge.

## 8. Public documentation counterpart
Public PR #102, branch `package/es-r01-release-publication-freshness`, documents the corrected GitHub timestamp semantics and retained trust boundary. Its initial frozen docs head was `c2a80525e964acfaf230169863d835dcf07d3d60`. CodeRabbit is green and review threads are zero. No ES-P05 product file is changed by this package.

## 9. Fresh-proof attempt and new exact blocker
The required canonical proof cannot currently advance past the public hosted build.

Current starting/main SHA `140d10ef63f3d6761c95afccbead13db53888304` already failed its own automatic canonical Pi Staging run `31332055336`, public build job `93291754833`, before this ES-R01 public docs branch was involved. The exact failures were:
- `ReportStoreIntegrationTest.stateLifecycleEnforcesAssignmentRevisionAndQueues()` — expected `true`, got `false` at `ReportIntegrationFixtures.assertQueueContains` / line 236 of the test;
- `ReportStoreIntegrationTest.duplicateSubmissionMergesEvidenceAndReplaysWithoutExtraRows()` — expected `2`, got `0` at line 101.

On unchanged ES-R01 docs head `c2a80525e964acfaf230169863d835dcf07d3d60`:
- Coverage run `31332739840` failed those same two tests; its unchanged-head rerun also failed;
- canonical Pi Staging run `31333070856`, public build job `93294291022`, failed those same two tests;
- bridge job `93295041935` was skipped because no verified public artifact existed;
- no private run was dispatched, so provenance, DB reset and Paper runtime were correctly not claimed.

This is not a regression caused by the ES-R01 bridge repair: the failing current-main run predates the public ES-R01 docs change, and the ES-R01 public diff is documentation-only. Fixing or weakening ReportStore product/test behavior is explicitly outside ES-R01 scope. Skipping those integration tests would weaken the trusted public build boundary and is prohibited.

## 10. Current exact unblock
Material evidence that current `EnthusiaStaff:main` again passes the canonical trusted public Java build, including the two ReportStore integration tests above, without ES-R01 weakening or bypassing the build gate. Once that condition changes, resume ES-R01 before starting a new package and run one fresh exact-current-main canonical public→private staging proof.

The next sequential worker must reconcile whether ES-P02 or the parked ES-P05 work is the correct owner of the product-side condition under current routing. This ES-R01 worker does not modify, synchronize, rerun staging for, merge, or delete ES-P05 PR #81.

## 11. Acceptance criteria state
- **PASS:** billing-independent public GitHub-hosted build architecture exists.
- **PASS:** bounded transient release/asset transport with exact provenance and cleanup exists.
- **PASS:** release publication freshness now uses semantically correct `published_at`; independent asset-upload freshness remains on asset `created_at`.
- **PASS:** two-hour maximum age and future-skew protections remain enforced.
- **PASS:** trusted Pi verifier and staging-control regression suite are green after the repair.
- **BLOCKED / NOT A PASS:** no fresh current-main public build artifact can currently be produced, so no post-repair private provenance/DB/Paper proof exists.
- **NOT CLAIMED:** ES-P02 or ES-P05 completion/validation.

## 12. Scope boundaries
No report Java product behavior, ReportStore product/tests, Flyway migration, V18, production infrastructure, production credential, LiteBans authority, issue #43 behavior, or ES-P05 implementation file was changed by ES-R01.

## 13. PR and merge record
Historical ES-R01 bridge merges remain valid. This continuation adds:
- `wsg138/EnthusiaStaff-Staging` PR #75 — normal merge `af1bd6d3ae8214e58eb969c23972f872b15c1f18` from frozen head `19e38d6851367d835cfe50fc29e9f95a0936f66d`;
- `wsg138/EnthusiaStaff` PR #102 — public documentation/canonical state counterpart; terminal publication/merge state must be recorded by the final worker verification.

Stale public ES-R01 PRs #96 and #98 were closed as superseded rather than merged or history-rewritten.

## 14. Cleanup
`noop-temp-ignore` has no PR, but compare-to-main proves two unique commits/work. It therefore does not satisfy the owner's deletion condition and is intentionally retained; no force-update or simulated deletion was performed.

## 15. Security/privacy
No credential, private database content, or secret-bearing runtime evidence was copied into public artifacts or package records. The repaired verifier still fails closed before DB/Paper execution on provenance/freshness mismatch. The new blocker occurs even earlier, during the trusted public product build.

## 16. Migration and authority impact
None. V18 remains immutable/current. LiteBans authority and issue #43 remain unchanged/deferred.

## 17. Completion definition
ES-R01 becomes `COMPLETE` only after a fresh exact-current-main run succeeds through public Java 21 build → verified bounded transient release/asset → correlated private dispatch → trusted `Lincoln-PI-4` → exact artifact/provenance verification → guarded DB pre-reset → Paper cycle 1/storage readiness → clean shutdown/full reap → Paper cycle 2/restart-persistence readiness → clean shutdown/full reap → guarded final DB cleanup → sanitized evidence → correlated public success → transient release/tag cleanup.

That definition is not met in this continuation because the current-main public build is red before transport. ES-R01 is therefore truthfully terminal for this worker as `BLOCKED` / `PARKED_BLOCKED`, not `COMPLETE`.

## 18. Resume boundary
Resume only when live GitHub shows the current-main trusted public build condition materially changed. Do not repeat identical staging attempts merely because time passed. Do not weaken integration tests, provenance, freshness, cleanup, or database/Paper gates. A resumed ES-R01 worker should obtain the one fresh current-main end-to-end proof, mark `COMPLETE` if and only if it succeeds, publish canonical state, and stop. Do not start ES-P05 in the same ES-R01 worker.
