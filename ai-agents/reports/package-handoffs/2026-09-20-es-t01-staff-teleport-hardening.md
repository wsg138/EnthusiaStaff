# ES-T01 — staff teleport hardening handoff

Status: `PARKED_BLOCKED`.

Owner authorization: 2026-09-20 request to create review/bug-finding/fixing work useful for immediate plugin testing.

Start `main`: `c1054da6a8f89b312df2e05e25edc958fceda7ef`.
Branch: `package/es-t01-staff-teleport-hardening`.
PR: #215.
Frozen executable/validation candidate: `991315dbe3f90c3a46842ca63a8ae6a76a716572`.

The production implementation remains unchanged from `12bfd267a2d9d2dd3709060300efd6d2bc3a220b`. GitHub compare from `12bfd267...` to `991315d...` contains only this package/handoff documentation and `paper/src/test/java/net/enthusia/staff/paper/staff/StaffToolRandomTeleportServiceTest.java`; no production Java changed.

Finding `T01-STAFF-RTP-001`: candidate eligibility/location could become stale between collection and execution. Repair stores candidate IDs, re-resolves/revalidates the selected target on its entity scheduler, retries invalid/offline candidates, and captures location only after final eligibility succeeds.

Finding `T01-STAFF-RTP-002`: candidate UUID/state reads occurred on the global scheduler. Repair enumerates player references globally and performs identity/state reads on each target entity scheduler with single-settlement guards for execution/retirement races.

Regression coverage exercises stale/invalid targets, reconnect identity, scheduler rejection/throw/retirement/duplicate callbacks, actor authorization revalidation, retry exhaustion, and teleport failure/cancellation paths.

Codacy blocker: **CLEARED**. The former one-new-issue `MEDIUM Performance` finding was validly fixed in test code without suppression by moving loop-local `Harness` construction into per-case helpers and hoisting the repeated behavior list. Hosted Codacy Static Code Analysis `106544003807` on `991315d...` is `SUCCESS` with zero annotations, and the PR summary reports `0` new issues. Codacy Diff Coverage `106546409672` is `SUCCESS` at `78.9%`; Coverage Variation `106546409662` is `SUCCESS` at `+0.18%`.

Exact hosted evidence on `991315dbe3f90c3a46842ca63a8ae6a76a716572`:
- Coverage run `35663450629`: `SUCCESS`.
- Sentinel Restart Artifact run `35663450669`: `SUCCESS`.
- Codacy Static `106544003807`: `SUCCESS`, zero annotations.
- PR review threads: none.

Live runtime evidence: prior Sentinel restart job `506` passed executable head `12bfd267a2d9d2dd3709060300efd6d2bc3a220b` with `PAPER_RESTART_OK`. A fresh restart command (`5768485647`) and status query (`5768569846`) were issued after the test-only Codacy remediation, but no new Sentinel bot result has been published, so no fresh exact-SHA live pass is claimed. Supporting equivalence check: hosted Paper JARs built from `12bfd267...` and `991315d...` contain the same 4,641 unpacked runtime files with zero byte-content differences; raw archive/JAR hashes differ only because build/archive metadata is regenerated.

Canonical Pi remains the hard blocker. Original ES-T01 Pi run `35641111973` successfully built the exact runtime but failed before private dispatch because `wsg138/EnthusiaStaff-Staging` returned `HTTP 401: Bad credentials`; no private run occurred. The condition is still current: Pi Staging Supersession run `35663448866` on `991315d...` again failed with the same HTTP 401 while using `STAGING_TOKEN`, before private staging execution.

Repository bridge policy requires the staging owner to rotate/replace `ENTHUSIASTAFF_STAGING_TOKEN` with the approved least-privilege automation credential that can read workflow history and dispatch the required private workflow in `wsg138/EnthusiaStaff-Staging`. Do not use a personal credential and do not bypass the public bridge. The available GitHub connector intentionally does not expose Actions-secret mutation APIs, so this prerequisite cannot be repaired from this worker session.

Current `main` has advanced to `4ffab626f4f044ef03adcf2d41c25690e1bdaa71` via PR #223. Do not churn the parked branch merely to absorb moving `main`. After the credential is repaired, first reconcile the then-current `main` into ES-T01 with a normal merge, recheck collisions, freeze the resulting candidate, rerun every invalidated hosted/static/runtime gate, then run canonical Pi exactly once and require an actual successful private staging result plus cleanup. Only then may PR #215 merge normally and publish `COMPLETE`.

No replacement branch/package/PR was created; no issue #216 finding was selected; no direct-main push, rebase, squash, force-push, or auto-merge is permitted.
