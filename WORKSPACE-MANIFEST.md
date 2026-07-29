# EnthusiaStaff workspace manifest

Last updated: 2026-07-29 (America/Indianapolis)

This manifest records review, recovery, and validation state. PRs #1 through
#7 have been merged into `main`; nothing listed here has been deployed,
released, or applied to production data.

## Repository checkpoint

| Repository | Remote URL | Default branch | Working branch | Latest local SHA | Latest pushed SHA | Pull request | Build status | Test status | Docker/Testcontainers | Codacy grade | Codacy issue count | Current blockers |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| EnthusiaStaff | `https://github.com/wsg138/EnthusiaStaff.git` | `main` | `section/persistence-economy-rollback-integrity`, based on current `main` | `2867ad30b8ee725701874fa34596502e75ba7105` plus the uncommitted next-section changes | `origin/main` at `2867ad30b8ee725701874fa34596502e75ba7105` | [PRs #1–#7 merged](https://github.com/wsg138/EnthusiaStaff/pulls?q=is%3Apr+is%3Amerged) | PASS: clean Java 21 `clean test check runtimeJars` checkpoint | PASS: 127 tests in 50 suites, 0 failures/errors/skips | PASS: 18 tests across 6 MariaDB Testcontainers suites | A, verified from the current hosted badge | 446 active issues on the exact `main` commit | Economy rollback evidence is the next bounded cleanup; production-like concurrency and crash staging remain unavailable. |
| enthusia-site | `https://github.com/wsg138/enthusia-site.git` | `main` (`1657a0a`) | `agent/punishment-platform` (expected; absent remotely) | No target-layout clone; sibling checkout is `1657a0a` on `deploy/market-experimental` | No feature-branch SHA | None | NOT_RUN | NOT_RUN | N/A | UNVERIFIED | UNKNOWN | Must be cloned under `related-repos`; the sibling checkout has untracked deployment-tool state that must not be lost or committed. |
| EnthusiaCurrency | `https://github.com/wsg138/EnthusiaCurrency.git` | `main` (`9696501`) | `agent/moderation-api` (expected; absent remotely) | No target-layout clone; sibling checkout is `9696501` on `main` | No feature-branch SHA | None | NOT_RUN | NOT_RUN | NOT_RUN | UNVERIFIED | UNKNOWN | Provider branch must be reconstructed from current main and EnthusiaStaff contracts after root cleanup. |
| EnthusiaCommend | `https://github.com/wsg138/EnthusiaCommend.git` | `main` (`25ea8cb`) | `agent/reputation-blacklist-api` (expected; absent remotely) | No target-layout clone; sibling checkout is `25ea8cb` on `main` | No feature-branch SHA | None | NOT_RUN | NOT_RUN | NOT_RUN | UNVERIFIED | UNKNOWN | Provider branch must be reconstructed from current main and EnthusiaStaff contracts after root cleanup. |
| EnthusiaAutoClicker | `https://github.com/wsg138/EnthusiaAutoClicker.git` | `main` (`5d7f926`) | `agent/client-evidence-api` (expected; absent remotely) | No target-layout clone; sibling checkout is `5d7f926` on `main` | No feature-branch SHA | None | NOT_RUN | NOT_RUN | NOT_RUN | UNVERIFIED | UNKNOWN | Provider branch must be reconstructed from current main and EnthusiaStaff contracts after root cleanup. |
| Enthusia-RoseChat | Missing: `wsg138/Enthusia-RoseChat` returned HTTP 404 | UNKNOWN | `agent/staff-bridge-api` (expected) | None | None | None | BLOCKED | BLOCKED | BLOCKED | UNVERIFIED | UNKNOWN | Intended `wsg138` repository does not exist or is inaccessible. Do not invent a remote or push to an upstream owner. |
| EnthusiaMarket | `https://github.com/wsg138/EnthusiaMarket.git` | `main` (`bc24f10`) | `agent/moderation-api` (expected; absent remotely) | No target-layout clone; sibling checkout is detached at `2c06a1a` | No feature-branch SHA | None | NOT_RUN | NOT_RUN | NOT_RUN | UNVERIFIED | UNKNOWN | Provider branch must be reconstructed from current main. Existing sibling checkout is detached and has an upstream BadgersMC remote; no Enthusia work may be pushed there. |

## Root recovery evidence

- Canonical remote: `wsg138/EnthusiaStaff`
- Remote default branch: `main`
- Remote feature branch: `agent/complete-staff-platform`
- `main`: `b87a13bbe6aaa62500b578c78e557e0bf1a4c705`
- Feature branch: `c4fd4129f7a34ad011f87f146fb72c236e611b89`
- GitHub comparison at recovery: 9 commits ahead, 0 behind `main`
- Existing history action: no rebase, reset, or force-push was required
- Merge commit: `b5e55ed9ffd7309cacabf6b0a07af220068f3c30`
- PR #1 outcome: merged with history preserved; the source branch was retained
- Current `main`: `2867ad30b8ee725701874fa34596502e75ba7105`
- Completed section history: PRs #2 through #7 merged without rewriting `main`
- Pre-existing goals file SHA-256 before and after checkout: `746DD1B37BBAF517F008441102A6CBF688AABEC09E8196F863509BF484277F9A`
- Root local exclusion: `related-repos/` is present in `.git/info/exclude`

## Workspace layout status

The root worktree and reports are represented by `EnthusiaStaff.code-workspace`.
Related repository paths are declared under `related-repos/`, but cloning is
intentionally deferred until the required EnthusiaStaff Codacy cleanup
checkpoint. Existing sibling worktrees are read-only recovery references and
are not treated as the requested final layout.

## Wiki status

The GitHub Wiki setting is enabled. The separate Wiki Git remote exists at
`https://github.com/wsg138/EnthusiaStaff.wiki.git` with `master` at
`d52d69fdc98ab6ad489e553ad5c258754137f9fe`. Its page inventory and content
have not yet been incorporated into the development checkpoints. Initial
architecture, development, installation, configuration, and recovery pages
will be maintained incrementally after the active economy-integrity section is
clean and merged.

## Checkpoint rules

- Update every SHA, PR, build, test, Docker, coverage, Codacy, and blocker field after each coherent checkpoint.
- Begin each new section on a fresh bounded branch from the latest `origin/main`.
- A skipped Docker test is recorded as skipped, never passed.
- A Codacy grade remains unverified until the actual branch analysis is read.
- Keep each nested repository independent; never stage `related-repos/` in the root repository.
