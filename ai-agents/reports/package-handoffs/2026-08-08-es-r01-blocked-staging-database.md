# ES-R01 database-gate evidence — disposable staging database unavailable

Date: 2026-08-08

## Package and current classification
`ES-R01 — Billing-independent staging bridge recovery`

Current worker state: `IN_PROGRESS` / `VERIFYING`.

The repository-side bridge implementation is merged and live artifact/source provenance has been proven. Repeated proof attempts have failed at the existing authorized disposable Pi-staging MariaDB prerequisite before Paper boot. This is strong evidence of a blocked external gate, but terminal `BLOCKED` / `PARKED_BLOCKED` classification is intentionally deferred until PR #94 merges normally and its required fresh current-`main` Pi Staging proof is inspected.

## Starting state
The worker started from legitimate `wsg138/EnthusiaStaff:main` `e482e64315f8c4f569506900ac8a8ef84cf0a90d` and `wsg138/EnthusiaStaff-Staging:main` `4c3adfb6e50091ff389e064ab9619f096dd4b2b2` after reading the required universal/package policies. ES-P02 and ES-P05 were both parked on the same private GitHub-hosted Actions Billing & plans blocker. The latest inspected old-route failure was staging run `31244561683`, job `93070895799`, runner ID `0`, zero steps. `Lincoln-PI-4` itself was healthy on private Staging Controls run `31245361935`, job `93072954209`, runner ID `2`.

## Repository-side work completed
### Public EnthusiaStaff
PR #93 implemented the public trusted build and bridge. It merged normally as `094838fa221476e0832cf821f7b4908b9402d0d9` from frozen head `cccadbd1885f78db517ff643f941d04bd0fba2a3`.

The public workflow now:
- authorizes exact main-history or exact open same-repository PR-head source SHAs;
- keeps fork PRs outside private staging;
- builds the exact source with Java 21 on public GitHub-hosted `ubuntu-latest`;
- emits a strict provenance manifest and SHA-256-bound runtime package;
- publishes a bounded transient prerelease transfer only after the build succeeds;
- dispatches and correlates the private staging workflow;
- waits for its exact verdict;
- requires transient release/tag cleanup as part of the result.

Frozen public head evidence: full build/tests/runtime inspection succeeded; Codacy static analysis succeeded with zero issues; diff coverage and coverage variation succeeded. CodeRabbit was rate-limited and produced no review threads, so it is recorded as unavailable rather than approval.

### Private EnthusiaStaff-Staging
PR #58 removed the private GitHub-hosted build dependency, added exact public provenance verification, and kept boot/restart only on `Lincoln-PI-4`; normal merge `570f83e41cb80b498a82c8b5a509c42345558a46`.

PR #59 added bounded connection-readiness retries only for SQLState class `08xxx`, with Paper still forbidden to boot until the guarded disposable pre-reset succeeds; exact-head Staging Controls run `31249532617`, job `93083557688`, succeeded; normal merge `313ed2815058eadeb8c823453f4152089cae01d4`.

PR #60 corrected live `pull_request_target` provenance semantics and error-path cleanup. For that event, source/run/job head is bound to the exact PR head while trusted workflow control is bound to the matching `main` base SHA. Exact-head Staging Controls run `31250097746`, job `93084990928`, succeeded on `Lincoln-PI-4` with all bridge, database, storage, successful-cycle, issue #43 prerequisite, and Sentinel tests. Normal merge `4036d6e915c2d751bef18849107722dfd1e586a6`.

No product Java source or migration changed in ES-R01. V18 remains immutable/current.

## Live proof history
### Merged-main bridge proof
Public run `31249125885` at source/control `094838fa221476e0832cf821f7b4908b9402d0d9`:
- public hosted build job `93082543002` succeeded on GitHub-hosted runner ID `1000009805`;
- bridge job `93083229835` dispatched private run `31249402654`;
- private job `93083246690` allocated `Lincoln-PI-4`, runner ID `2`;
- public release/run/digest/manifest verifier passed;
- disposable MariaDB pre-reset returned SQLState `08000` before Paper boot;
- the workflow failed closed;
- transient release `367158184` and tag `es-r01-staging-31249125885-1` were confirmed absent after cleanup.

That first failure was initially treated as potentially transient, so PR #59 added the bounded connection-only retry boundary rather than bypassing the reset.

### Corrected live PR-target proof
Public PR #94 exact head `4acb4853c5ce00805ff206e3d0bb28a2458e82c8`, public Pi Staging run `31250170297`:
- ordinary public hosted build job `93085175893` succeeded and uploaded the exact runtime package;
- bridge dispatched private run `31250450219`;
- private job `93085892938` allocated `Lincoln-PI-4`, runner ID `2`;
- corrected PR-target provenance verification passed using PR/source head `4acb4853c5ce00805ff206e3d0bb28a2458e82c8` and trusted base/workflow SHA `094838fa221476e0832cf821f7b4908b9402d0d9`;
- verified transfer release ID `367163460`, asset ID `506237999`, asset `enthusiastaff-staging-4acb4853c5ce-31250170297-1.zip`, SHA-256 `05ed21b6279b46283853e952214513b2871f838712509ac9e4e514c11ac82488`;
- verified runtime `EnthusiaStaff-Paper-0.1.0-SNAPSHOT.jar`, 9,123,435 bytes, SHA-256 `cfb526a90994803d64858b649a6452b23b5c12438461fb8f66d5cab18a21c449`;
- database evidence shows **seven total connection attempts**, all failing with SQLState `08000`; retry markers 1/7 through 6/7 and `connection_retry_result=exhausted` are present;
- Paper never booted, exactly as required by the fail-closed boundary;
- sanitized evidence artifact `9019842260`, digest `sha256:d0d203f707940c05d9d5728120d4a207b6cd0ad68357aeb7ea907561bf6bacc4`, uploaded successfully;
- public cleanup succeeded and release `367163460` plus tag `es-r01-staging-31250170297-1` both return 404 afterward.

This second attempt materially proves the database condition is not a one-shot readiness race within the package's bounded retry window.

## Exact blocked-gate evidence
The already-authorized disposable Pi-staging MariaDB endpoint configured through the existing private `pi-staging` environment has been unavailable from `Lincoln-PI-4`. The strongest captured database evidence fails at connection establishment with SQLState class `08` before identity/destructive-target checks can run.

No safe repository-side implementation remains. Do **not**:
- use another database target;
- change or expose private credentials;
- remove the disposable reset;
- allow Paper to boot before reset success;
- broaden ES-R01 into database administration;
- manually repeat the same gate without evidence the database availability condition changed.

## Post-merge decision and conditional unblock
PR #94 must merge normally before terminal package classification. Its merge-triggered current-`main` Pi Staging proof is the next mandatory package action and is not an optional manual rerun.

If that current-main proof succeeds fully, ES-R01 becomes `COMPLETE` and this blocked-gate evidence is superseded by successful acceptance. If it fails only because the same existing authorized disposable staging MariaDB endpoint remains unreachable from `Lincoln-PI-4`, terminal classification becomes `BLOCKED` / `PARKED_BLOCKED`. The exact later unblock condition is then material evidence that this same authorized endpoint is reachable again under the current `pi-staging` environment contract; only after that condition changes should a future worker resume and rerun the bridge.

Do not start ES-P02 in the same worker that terminally completes or parks ES-R01.

## Dependent package routing
ES-P02 and ES-P05 remain `BLOCKED` / `PARKED_BLOCKED`. Their branches/PRs alone do not make them actionable while ES-R01 is unfinished.

## Current checkpoint publication
Public PR #94 / branch `package/es-r01-proof-retry-checkpoint` is documentation/package-state only. It records the current verification checkpoint and, after normal merge, triggers the required current-main proof. It is **not** itself the terminal blocked-state publication. After the post-merge proof, persist the resulting terminal facts through the smallest documentation-only finalization PR necessary.

## Branch cleanup note
Merged implementation heads are contained by their recorded normal merge commits. The connected GitHub tool surface available to this worker provides branch creation/update but no delete-ref operation. Do not falsify cleanup by moving refs. Any remaining merged ES-R01 temporary refs have no unique implementation work and may be deleted later with a tool that actually supports ref deletion.

## Systems not to disturb
Production data/configuration, LiteBans authority, issue #43, private database contents/credentials, Flyway history/V18, ES-V02 acceptance, and all product package code remain outside this checkpoint publication.
