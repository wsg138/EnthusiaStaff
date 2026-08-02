# EnthusiaStaff workspace manifest

Last updated: 2026-08-01 (America/Indianapolis)

This manifest records repository, validation and blocker state for development
coordination. Nothing listed here authorizes a production deployment, release,
LiteBans replacement or production-data change.

## Root repository state and validated checkpoint

| Field | Current value |
| --- | --- |
| Repository | `wsg138/EnthusiaStaff` |
| Default branch | `main` |
| Current merged repository state | `main` at `22319db3b7244153864f431191929ef275c239c5`, the PR #40 Wiki information-architecture merge commit |
| Latest fully validated implementation revision | PR #36 head `3afeffc926571170e8df18c7d096ca7f4d89ec1b` |
| Evidence boundary | Every build, test, Codacy, artifact and Pi result in this section attaches to `3afeffc926571170e8df18c7d096ca7f4d89ec1b`; later documentation merge commits are not separately claimed as runtime-tested |
| Clean Java validation | 40/40 tasks; 99 suites / 398 tests; no failures, errors or skips at `3afeffc926571170e8df18c7d096ca7f4d89ec1b` |
| MariaDB validation | 15 MariaDB 11.8.3 Testcontainers suites / 68 tests at `3afeffc926571170e8df18c7d096ca7f4d89ec1b` |
| Runtime artifacts | From `3afeffc926571170e8df18c7d096ca7f4d89ec1b`: Paper SHA-256 `83D457FCA65839B6E674CC937F37E63782620D023E9B202F89ED3A88CF4D5060`; Velocity SHA-256 `FA17E9F891286250FEC21AD19CD425540C15CC163D58D65DF5E177856AEBDBD9` |
| Hosted quality result | At `3afeffc926571170e8df18c7d096ca7f4d89ec1b`: zero new Codacy findings, three fixed, 92.59% diff coverage, +0.103 coverage variation, no clone increase |
| Exact-SHA Pi staging | PASS — run `30709333535`, tested revision `3afeffc926571170e8df18c7d096ca7f4d89ec1b` |
| Production authority | **NOT READY**; LiteBans and the existing staff stack remain authoritative |

PR #36 preserved and tested every LiteBans shadow dimension across import,
reconciliation, replay and source deletion: counts, checksums, active state, UUID
mappings, expirations, login decisions, mute decisions, IP-ban decisions,
rejected rows and extra/orphan mappings.

## Active root pull requests

### PR #37 — Harden LiteBans cutover coordination

| Field | Current value |
| --- | --- |
| State | Draft, open |
| Branch | `section/plugin` |
| Base | `main` at `3f4e2a1164d570aadfb82522b07b4b32c9f2a7f9` when the checkpoint was recorded |
| Current head | `511c92f7a36a8d892002e5904501d9dbb36cf4a6` at the recorded checkpoint |
| Current scope | Maintenance entry, duplicate rejection, abort, emergency freeze, transition audit, exact 168-hour/seven-summary gate, final incremental import, activation linkage and duplicate activation rejection |
| Current evidence | Focused MariaDB Testcontainers class passed; local PMD/Lizard clean for the new test |
| Status boundary | First focused checkpoint only; production refactor and complete validation remain unfinished |

PR #37 is the immediate LiteBans workstream. Do not represent it as complete until
its implementation, complete clean build, full MariaDB suite, hosted quality
checks, Wiki validation, exact-head staging and review are finished.

### PR #27 — Durable punishment request notifications and recovery

| Field | Current value |
| --- | --- |
| State | Open and unmerged; final human merge-review candidate after the post-manifest exact-head Coverage run and truthful hosted-review disposition |
| Branch | `section/punishment-request-notifications-recovery` |
| Included main | `22319db3b7244153864f431191929ef275c239c5` through normal two-parent merge commit `088a7f111dbb8590bf117423b31821b22cf1082d` |
| Validated implementation head | `65844e554b570a21d8759ce32dbc592be625ed4b`; 189 commits ahead and 0 behind included `main` when validated |
| Exact validation | Coverage run `30721449376`, job `91425842011`, Java `21.0.11+10`, exact clean build/Jacoco/runtime-JAR command succeeded; 41/41 tasks, 104 suites / 435 tests, including 19 MariaDB 11.8.3 Testcontainers suites / 86 tests |
| Runtime artifact | Artifact `8825026309`, digest `sha256:194defb3f01f049cae20d3381241d72535f1e837ebd7d0790b60de4e6c30b70c` |
| Runtime JARs | Paper: 5,646,928 bytes, SHA-256 `D33AD4508E428607A35E0E98A413A2E838DB6D2D437A9736F39570034382D2A7`; Velocity: 1,948,708 bytes, SHA-256 `DA613FB11D1E7B7CBF0D09B41D0240B8A36007267F518957EFD2B880B09AF652` |
| Packaging inspection | Exactly one Paper and one Velocity runtime JAR; both ZIP-valid; MariaDB driver class/service metadata packaged; 44 provider API source types and 0 runtime provider API leaks |
| Coverage | Aggregate line 93.16%, branch 79.88%, instruction 92.86%; XML uploaded to Codacy successfully |
| Required correction evidence | Behavioral staff activation failure tests, checked/unchecked rollback and suppressed rollback tests, deterministic concurrent duplicate replay, complete SQLException-chain tests, V13 DEAD_LETTER preservation, NULL-deny reviewer persistence test, and per-recipient fallback contention/progress tests all passed |
| Review state | Six previously major CodeRabbit threads are resolved; a fresh review must be requested for the final remote head, or the exact quota-limited state reported |
| Evidence boundary | The validation above predates this manifest-only commit. A new exact-head Coverage run is mandatory and supersedes it for final merge-review evidence |
| Deployment boundary | This PR does not enable production alerts, replace LiteBans, send live Discord notifications, deploy a runtime JAR or authorize production deployment |

PR #27 is reconciled as an engineering merge candidate. Exact-head build, runtime
artifact, coverage, hosted-quality and review evidence belongs to the pull request
and must remain green for the final reviewed head before merge.

## Related repositories

| Repository | Expected role | Current coordinated status | Main blocker |
| --- | --- | --- | --- |
| `wsg138/enthusia-site` | Private punishment and appeal website | Root bridge exists; complete site branch not reconstructed or validated here | Auth/session/CSRF/media/rate-limit work, secrets and private staging |
| `wsg138/EnthusiaCurrency` | Exact economy moderation snapshots and plans | Root integration contract/adapter exists; provider implementation not validated | Provider branch and cross-plugin staging |
| `wsg138/EnthusiaCommend` | Persistent reputation restriction API | Root contract/adapter exists; provider implementation not validated | Provider branch and all write-entry enforcement tests |
| `wsg138/EnthusiaAutoClicker` | Versioned bounded client evidence | Root contract/adapter exists; provider implementation not validated | Provider branch and handshake/offline evidence staging |
| Intended `wsg138/Enthusia-RoseChat` | Moderation/staff channel and evidence bridge | Blocked; repository/API remains missing or inaccessible | Do not invent a remote or unsupported reflective/command integration |
| `wsg138/EnthusiaMarket` | Supported stall moderation and escrow-safe behavior | Root adapter exists; provider implementation not validated | Provider branch and transaction-compatible staging |

Each related project remains an independent Git repository. Histories must not be
flattened into EnthusiaStaff, and provider-owned API classes must not leak into the
Paper or Velocity runtime jars.

A cross-repository release candidate must use a release manifest containing one
authenticated revision per repository, with matching artifact hashes,
configuration checksums, environment versions and acceptance evidence. There is
no single global commit that can identify independent provider and website state.

## Current development route

The detailed path is maintained in:

```text
docs/development-blueprint.md
docs/wiki/pages/Development-Blueprint.md
reports/REQUIREMENTS-MATRIX.md
```

Immediate order:

1. Complete PR #37's implementation, full validation and review.
2. Finish PR #27 exact-head validation, quality disposition and review after reconciliation.
3. Establish a clean new `main` checkpoint and refresh this manifest.
4. Complete punishment history, appeal-linked decisions and durable notifications.
5. Finish modular configuration, operational modes, report UI/privacy and RoseChat evidence.
6. Stage staff mode, freeze, vanish, inventory and confiscation under real ownership and failure conditions.
7. Reconstruct providers and complete the private website.
8. Finish LiteBans recovery, seven-day shadow evidence, activation, emergency freeze and rollback.
9. Run one complete release-manifest acceptance candidate, then the mandatory 168-hour shadow period and final cutover rehearsal.

## Checkpoint update rules

At every coherent repository checkpoint record:

- repository and branch;
- base, implementation and final reviewed revisions;
- PR URL and state;
- exact validation commands;
- task, suite, total-test and MariaDB counts;
- runtime jar sizes and hashes;
- provider API source-type/leak inspection;
- hosted Codacy baseline, new/fixed issues, duplication and diff coverage;
- Wiki validation page count;
- exact staging run and tested revision;
- review findings and unresolved threads;
- blockers and unavailable acceptance groups.

For a cross-repository release candidate, additionally record one authenticated
revision per repository in the release manifest and test those revisions together.

A skipped, cancelled, superseded or different-revision run is never recorded as
passed. A merged PR is a development checkpoint, not deployment authorization.

## Release boundaries

- Keep LiteBans authoritative until full release-manifest acceptance, the exact
  168-hour shadow window, final reconciliation, cutover rehearsal and Founder
  authorization pass.
- Never combine evidence from undeclared revisions into one release candidate.
- Keep production credentials, private jars, databases, logs, evidence and
  runtime folders out of Git.
- Keep destructive operations configuration-gated and recovery-visible.
- Retain migration backups and legacy data through cutover; legacy removal is a
  later manual operation.
- Update this manifest, the requirements matrix, development blueprint and Wiki
  together when a root checkpoint changes.
