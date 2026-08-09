# ES-R01 handoff — release freshness repaired; current-main public build blocked

Date: 2026-08-09
Package: `ES-R01 — Billing-independent staging bridge recovery`
Terminal worker state: `BLOCKED` / `PARKED_BLOCKED`
Owner-directed classification on entry: `ACTIONABLE_CONTINUATION`

## Starting live state

- `wsg138/EnthusiaStaff:main`: `140d10ef63f3d6761c95afccbead13db53888304`
- `wsg138/EnthusiaStaff-Staging:main`: `19e7c44f646caa51d0a0d97fa15f6596014efadc`
- ES-P05 PR #81 preserved, unmodified and unmerged.

## Why the former blocker was stale

Live ES-P05 evidence proved the old ES-R01 MariaDB-unreachable condition had already changed:
- public run `31301426684`;
- private run `31301734048`;
- private job `93215499833`;
- trusted `Lincoln-PI-4`, runner ID `2`;
- exact artifact/provenance PASS;
- guarded pre-reset PASS;
- Paper cycle 1 ready; EnthusiaStaff enabled; MariaDB/Flyway through V18;
- clean shutdown/full reap;
- Paper cycle 2 ready; restart/persistence/schema-v18 proof PASS;
- clean shutdown/full reap;
- final guarded database cleanup PASS;
- sanitized evidence artifact `9034945235`, digest `sha256:a81af3154c7e561c5ea09ed7072c970d483b25b63e217f34f1976132bab4ef3e`.

That evidence required reclassifying ES-R01 as `ACTIONABLE_CONTINUATION` rather than leaving it parked on MariaDB reachability.

## Newly exposed freshness failure

ES-P05 final exact source `346e764f40b25c98e7d24ce7f863e5629773e814`:
- public Pi Staging `31330788773`;
- public build job `93288608088`: success;
- bridge job `93289540403`;
- transient release ID `367563110`;
- tag `es-r01-staging-31330788773-1`;
- asset ID `507820281`;
- asset `enthusiastaff-staging-346e764f40b2-31330788773-1.zip`;
- asset SHA-256 `ca8dc8481c6771d92f540ba2480acefb2aafdc96931646a423fdaf3ef0716782`;
- private run `31331175023`;
- private job `93289556545`;
- trusted `Lincoln-PI-4`, runner ID `2`;
- private failure at `Retrieve and verify exact public bridge artifact`:
  `release created_at is expired for the staging bridge`;
- DB/Paper runtime never executed;
- sanitized diagnostic artifact `9042975898`, digest `sha256:1b470d2f20a6f263ed424734ff9397663077bbeec2d3ea942e3981ed926d7a93`;
- public transfer cleanup succeeded.

## Confirmed root cause

`wsg138/EnthusiaStaff-Staging/scripts/fetch-enthusiastaff-bridge-artifact.sh` enforced the two-hour transport TTL against GitHub Release `created_at`.

GitHub's REST API defines Release `created_at` from the commit used for the release, not release publication. Therefore a just-published transient release targeting an older commit can have an old `created_at` and fail immediately. Release `published_at` represents publication; Release Asset `created_at` remains suitable for asset upload freshness.

## Repair

Staging branch: `package/es-r01-release-publication-freshness`
Frozen reviewed head: `19e38d6851367d835cfe50fc29e9f95a0936f66d`
PR: `wsg138/EnthusiaStaff-Staging#75`
Normal merge: `af1bd6d3ae8214e58eb969c23972f872b15c1f18`

Changed only:
- `scripts/fetch-enthusiastaff-bridge-artifact.sh`
- `tests/test-staging-bridge-artifact.sh`

Verifier behavior after repair:
- mandatory valid Release `published_at` for release-publication freshness;
- Release Asset `created_at` retained for upload freshness;
- two-hour maximum age retained;
- five-minute future-skew guard retained;
- missing/null/malformed/timezone-less publication timestamps fail closed;
- exact release ID/tag, asset ID/name, source/workflow/run/PR provenance, repository, digest, size, canonical URL, archive allowlist, manifest and cleanup checks retained.

## Regression/static/review proof

Staging Controls CI run `31332576934`, job `93293056853`, on `Lincoln-PI-4`: success.

Focused regression coverage passed for:
1. old target commit + fresh publication + fresh asset = ACCEPT;
2. fresh publication + expired asset = REJECT;
3. expired publication + fresh asset = REJECT;
4. future publication beyond skew = REJECT;
5. future asset timestamp beyond skew = REJECT;
6. missing/null publication timestamp = REJECT;
7. mismatched release ID/tag = REJECT;
8. mismatched asset ID/name = REJECT;
9. digest mismatch = REJECT;
10. stale/moved PR provenance = REJECT;
11. wrong public workflow/control/source provenance = REJECT;
12. existing success fixtures continue to pass.

The broader staging suite also passed source selection, disposable DB wrapper, storage readiness, successful-cycle, issue-43 prerequisite, multi-repository policy and Sentinel controls, including 292 isolated Sentinel unit tests. Repository-equivalent strict Bash execution passed. CodeRabbit was green; zero valid unresolved review threads. No secret exposure was observed.

The PR merge ref and frozen head had the same tree SHA `841955d405d7aa383c04a1a9033d5c0f5d901015`, so the successful PR CI tested byte-for-byte identical content to the frozen head.

## Public counterpart

Public branch: `package/es-r01-release-publication-freshness`
Initial docs head: `c2a80525e964acfaf230169863d835dcf07d3d60`
PR: `wsg138/EnthusiaStaff#102`

`docs/pi-staging-bridge.md` now documents Release `published_at` plus asset `created_at` and explicitly preserves the two-hour boundary. Canonical package/registry state on the same PR records this continuation and blocker. CodeRabbit is green and review threads are zero. No product Java file is changed.

Stale ES-R01 public PRs #96 and #98 were closed as superseded rather than merged or rewritten.

## New blocker discovered after the repair

The repaired verifier could not receive a fresh current-main artifact because current public `main` already fails the trusted Java build before transfer.

Baseline current-main proof of the blocker:
- source/control `140d10ef63f3d6761c95afccbead13db53888304`;
- automatic public Pi Staging run `31332055336`;
- public build job `93291754833`;
- result: failure before artifact upload/bridge;
- failing tests:
  - `ReportStoreIntegrationTest.stateLifecycleEnforcesAssignmentRevisionAndQueues()` — expected `true`, got `false`;
  - `ReportStoreIntegrationTest.duplicateSubmissionMergesEvidenceAndReplaysWithoutExtraRows()` — expected `2`, got `0`.

Same failures reproduced on ES-R01's documentation-only exact head:
- Coverage run `31332739840`: failure;
- unchanged-head Coverage rerun job `93294473586`: failure;
- canonical Pi Staging run `31333070856` / public build job `93294291022`: failure;
- bridge job `93295041935`: skipped because no verified artifact existed;
- no private run was dispatched.

This blocker predates the ES-R01 public docs change and is outside ES-R01 scope. The owner explicitly forbade Report Java product behavior changes, and weakening/skipping the integration tests would weaken the trusted public build boundary. No such bypass was made.

## Terminal classification

`ES-R01 = BLOCKED / PARKED_BLOCKED`

This is a new blocker, not the stale MariaDB condition and not the repaired transient-release timestamp defect.

Exact unblock: material evidence that current `EnthusiaStaff:main` again passes the canonical trusted public Java build, including the two ReportStore integration tests above, without ES-R01 weakening or bypassing the gate.

Once that condition changes, resume ES-R01 before any new package and obtain one fresh exact-current-main canonical proof through:

public GitHub-hosted Java 21 build → verified bounded transient release/asset → correlated private dispatch → trusted `Lincoln-PI-4` → exact artifact/provenance verification → guarded DB pre-reset → Paper cycle 1/storage readiness → clean shutdown/full reap → Paper cycle 2/restart-persistence readiness → clean shutdown/full reap → guarded final DB cleanup → sanitized evidence → correlated public success → transient release/tag cleanup.

Only then mark ES-R01 `COMPLETE`.

## ES-P05 preservation

PR #81 remains parked and untouched. This worker did not merge it, modify its product files, resynchronize it, rerun its staging, or delete its branch. The shared freshness defect is repaired, but that does not validate PR #81's current head.

The next sequential worker must reconcile current routing and reconsider ES-P02/ES-P05 according to the product-side public-build failure and canonical priorities. Do not start ES-P05 from an ES-R01 worker.

## `noop-temp-ignore`

The branch has no PR, but compare-to-main shows two unique commits/work. The owner's safe-delete precondition is therefore false. It was retained; no force-update or simulated deletion was performed.

## Boundaries preserved

- no Report Java product behavior change;
- no Flyway migration change; V18 remains immutable/current;
- no production infrastructure/credential change;
- no LiteBans authority change;
- no issue #43 change;
- no ES-P05 product change.
