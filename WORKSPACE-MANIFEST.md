# EnthusiaStaff workspace manifest

Last updated: 2026-08-01 (America/Indianapolis)

This manifest records repository, validation and blocker state for development
coordination. Nothing listed here authorizes a production deployment, release,
LiteBans replacement or production-data change.

## Root repository checkpoint

| Field | Current value |
| --- | --- |
| Repository | `wsg138/EnthusiaStaff` |
| Default branch | `main` |
| Current `main` | `3f4e2a1164d570aadfb82522b07b4b32c9f2a7f9` |
| Latest merged checkpoint | PR #36 — **Decompose LiteBans shadow comparison** |
| Validated implementation head | `3afeffc926571170e8df18c7d096ca7f4d89ec1b` |
| Clean Java validation | 40/40 tasks; 99 suites / 398 tests; no failures, errors or skips |
| MariaDB validation | 15 MariaDB 11.8.3 Testcontainers suites / 68 tests |
| Runtime artifacts | Paper SHA-256 `83D457FCA65839B6E674CC937F37E63782620D023E9B202F89ED3A88CF4D5060`; Velocity SHA-256 `FA17E9F891286250FEC21AD19CD425540C15CC163D58D65DF5E177856AEBDBD9` |
| Hosted quality result | Zero new Codacy findings, three fixed, 92.59% diff coverage, +0.103 coverage variation, no clone increase |
| Exact-SHA Pi staging | PASS — run `30709333535` |
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
| Base | `main` at `3f4e2a1164d570aadfb82522b07b4b32c9f2a7f9` |
| Current head | `511c92f7a36a8d892002e5904501d9dbb36cf4a6` |
| Current scope | Maintenance entry, duplicate rejection, abort, emergency freeze, transition audit, exact 168-hour/seven-summary gate, final incremental import, activation linkage and duplicate activation rejection |
| Current evidence | Focused MariaDB Testcontainers class passed; local PMD/Lizard clean for the new test |
| Status boundary | First focused checkpoint only; production refactor and complete validation remain unfinished |

PR #37 is the immediate LiteBans workstream. Do not represent it as complete until
its implementation, complete clean build, full MariaDB suite, hosted quality
checks, Wiki validation, exact-head staging and review are finished.

### PR #27 — Durable punishment request notifications and recovery

| Field | Current value |
| --- | --- |
| State | Draft, open; must remain unmerged until reconciled and completed |
| Branch | `section/punishment-request-notifications-recovery` |
| Current visible head | `094da12f11bfbf9f486186a624258d2159c64bfd` |
| Size | 95 commits; long-lived concurrent history |
| Implemented checkpoint | Paper punishment-request alert delivery, reconnect processing, maintenance and lifecycle foundations |
| Validated internal checkpoint | `a5ab8b9b543ecd78facbf29a2b8824b30220c6c3` completed the recorded Java build and runtime-jar checks |
| Deferred | Complete YAML parsing, atomic reload/worker replacement, final quality cleanup, review, Pi/live staging, Discord sending and production configuration |

PR #27 predates many merged root refactors. Reconciliation must preserve its
notification, staff-mode and freeze behavior while adopting current composition,
persistence, scheduling, configuration and quality boundaries. Do not discard its
history or merge duplicate implementations.

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

## Current development route

The detailed path is maintained in:

```text
docs/development-blueprint.md
docs/wiki/pages/Development-Blueprint.md
reports/REQUIREMENTS-MATRIX.md
```

Immediate order:

1. Complete PR #37's implementation, full validation and review.
2. Rebase and reconcile PR #27 without losing or duplicating its work.
3. Establish a clean new `main` checkpoint and refresh this manifest.
4. Complete punishment history, appeal-linked decisions and durable notifications.
5. Finish modular configuration, operational modes, report UI/privacy and RoseChat evidence.
6. Stage staff mode, freeze, vanish, inventory and confiscation under real ownership and failure conditions.
7. Reconstruct providers and complete the private website.
8. Finish LiteBans recovery, seven-day shadow evidence, activation, emergency freeze and rollback.
9. Run one complete acceptance candidate, then the mandatory 168-hour shadow period and final cutover rehearsal.

## Checkpoint update rules

At every coherent checkpoint record:

- repository and branch;
- base, implementation and final reviewed SHAs;
- PR URL and state;
- exact validation commands;
- task, suite, total-test and MariaDB counts;
- runtime jar sizes and hashes;
- provider API source-type/leak inspection;
- hosted Codacy baseline, new/fixed issues, duplication and diff coverage;
- Wiki validation page count;
- exact staging run and tested SHA;
- review findings and unresolved threads;
- blockers and unavailable acceptance groups.

A skipped, cancelled, superseded or different-SHA run is never recorded as passed.
A merged PR is a development checkpoint, not deployment authorization.

## Release boundaries

- Keep LiteBans authoritative until full acceptance, the exact 168-hour shadow
  window, final reconciliation, cutover rehearsal and Founder authorization pass.
- Never combine evidence from different commits into one release candidate.
- Keep production credentials, private jars, databases, logs, evidence and
  runtime folders out of Git.
- Keep destructive operations configuration-gated and recovery-visible.
- Retain migration backups and legacy data through cutover; legacy removal is a
  later manual operation.
- Update this manifest, the requirements matrix, development blueprint and Wiki
  together when a root checkpoint changes.