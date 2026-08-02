# EnthusiaStaff workspace manifest

Last updated: 2026-08-02 (America/Indianapolis)

This manifest records repository, validation and blocker state for development
coordination. Nothing here authorizes production deployment, a LiteBans cutover,
production-data access or a change in punishment authority.

## Post-PR-27 repository checkpoint

| Field | Current value |
| --- | --- |
| Repository | `wsg138/EnthusiaStaff` |
| Default branch | `main` |
| Current merged repository state | `main` at `14666c5b065571c227373ec9e13e82e978b689ca`, the merge commit for PR #27 |
| PR #27 state | Merged and closed; do not reopen or recreate its former branch unless a new verified defect requires follow-up |
| PR #27 final reviewed source head | `28dcd90f96b7a0c772acc378f73b18d9af62fe0b` |
| PR #27 merge commit | `14666c5b065571c227373ec9e13e82e978b689ca` |
| Post-merge Coverage | Run `30728556897`, job `91444608191`, success on exact commit `14666c5b065571c227373ec9e13e82e978b689ca` with Temurin Java `21.0.11+10` |
| Post-merge build | `./gradlew clean build jacocoAggregateReport runtimeJars --no-daemon --no-build-cache --no-configuration-cache --console=plain`; `BUILD SUCCESSFUL in 3m 41s`; 49 actionable tasks, 40 executed and 9 up-to-date |
| Post-merge validation artifact | Artifact `8827227658`, 17,585,813 bytes, digest `sha256:20227da6ec7e86265a8810113bd7540dd46c1a9679ccee2f3b58e7b5c7717f6e` |
| Post-merge runtime JARs | Paper: 8,658,733 bytes, SHA-256 `5ff214ec4878dd41992281e65be641eaebffc9efe4ed16158a1a5360148ebfe2`; Velocity: 7,682,930 bytes, SHA-256 `7dd4c7ad628b0a60e2a11ad801617056c363913588fa3e52d608316fab9ad66c` |
| Packaging inspection | Exactly one Paper and one Velocity runtime JAR; both ZIP-valid; 24 provider API source types checked; zero provider API leaks |
| Aggregate coverage | Lines 44.53%; branches 35.86%; instructions 46.99%; Codacy coverage upload and final notification succeeded |
| Post-merge Wiki validation | Run `30728556902`, job `91444608079`, success; 34 pages validated on the PR #42 synchronization merge ref that combined `main` `14666c5b065571c227373ec9e13e82e978b689ca` with the then-current `section/plugin` branch |
| Production authority | **LiteBans remains authoritative** |

The Coverage evidence above is exact-main evidence for the PR #27 merge commit. The
Wiki run is synchronization evidence for the PR #42 merge ref and must not be
misrepresented as a separate runtime validation of another revision.

## Active implementation pull request

### PR #37 — Harden LiteBans cutover coordination

| Field | Current value |
| --- | --- |
| State | Open draft |
| Branch | `section/plugin` |
| Base | `main` at `14666c5b065571c227373ec9e13e82e978b689ca` |
| Current source head | `f4cf0c6f824296b8677272c248cc5bb689c25417` |
| Ahead/behind | 70 commits ahead and 0 behind current `main` |
| Current automated validation | Coverage run `30733659455` succeeded on the exact head; CodeRabbit commit status is successful; no unresolved inline review threads are present |
| Current implementation scope | Durable cutover state, maintenance and abort, activation linkage, duplicate handling, emergency freeze, writer fencing, cross-server coordination, restart recovery, audit persistence and minimum pool behavior |
| Remaining blocker | Issue #43 — `Complete LiteBans cutover production-like acceptance` |
| Merge boundary | Green CI is necessary but not sufficient; PR #37 must remain draft until one exact acceptance record satisfies `docs/cutover-acceptance.md` and issue #43 |
| Deployment boundary | Merging PR #37 would still not authorize a production LiteBans cutover |

PR #37 is the only current implementation branch. Do not create a synchronization
commit unless it actually becomes behind `main`; do not rebase or force-push it.
Migrations V11, V12 and V13 are checksum-locked and may not be changed.

## Current development route

The detailed path is maintained in:

```text
docs/development-blueprint.md
docs/wiki/pages/Development-Blueprint.md
reports/REQUIREMENTS-MATRIX.md
docs/cutover-acceptance.md
```

Immediate order:

1. Keep the post-PR-27 checkpoint documentation accurate and validated.
2. Preserve PR #37 at its exact reviewed source head unless a verified change is required.
3. Complete issue #43 against one pinned release candidate in isolated staging.
4. Run representative migration, interruption/restart, 168-hour shadow, maintenance,
   activation, freeze, rollback, reconciliation and distributed-runtime acceptance.
5. Mark PR #37 ready only after the full record is complete and independently reviewed.
6. Keep LiteBans authoritative until a separate production-cutover authorization exists.

## Checkpoint update rules

At every coherent repository checkpoint record:

- repository, branch, base and exact source revision;
- PR URL, state and merge/draft status;
- exact workflow run and job IDs;
- exact validation commands and tested revision;
- runtime JAR counts, sizes, hashes and packaging checks;
- review findings and unresolved threads;
- staging evidence boundaries and unavailable acceptance groups;
- current authority and production boundaries.

A skipped, cancelled, superseded, merge-ref-only or different-revision run is never
recorded as exact source evidence. A merged PR is a development checkpoint, not
deployment authorization.

## Release boundaries

- Keep LiteBans authoritative until the full acceptance record, final reconciliation,
  cutover rehearsal and explicit production authorization are complete.
- Never combine evidence from undeclared revisions into one release candidate.
- Keep production credentials, private JARs, databases, logs, evidence and runtime
  folders out of Git.
- Do not repair Flyway history, delete migration data or delete legacy LiteBans data.
- Retain backups and legacy data through cutover; legacy removal is a later manual
  operation.
- Do not represent isolated staging, Pi validation or merge-ref Wiki validation as
  production evidence.
