# EnthusiaStaff workspace manifest

Last updated: 2026-07-27 (America/Indianapolis)

This manifest records review and recovery state only. Nothing listed here has been merged, deployed, published, released, or applied to production data.

## Repository checkpoint

| Repository | Remote URL | Default branch | Working branch | Latest local SHA | Latest pushed SHA | Pull request | Build status | Test status | Docker/Testcontainers | Codacy grade | Codacy issue count | Current blockers |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| EnthusiaStaff | `https://github.com/wsg138/EnthusiaStaff.git` | `main` (`b87a13b`) | `agent/complete-staff-platform` | `c4fd412` | `c4fd412` | [Draft PR #1](https://github.com/wsg138/EnthusiaStaff/pull/1) | PENDING clean Java 21 build | PENDING | PENDING Docker availability check | UNVERIFIED; PR check is `ACTION_REQUIRED` | PENDING baseline | Recovered worktree was absent locally; Codacy baseline and cleanup are pending. |
| enthusia-site | `https://github.com/wsg138/enthusia-site.git` | `main` (`1657a0a`) | `agent/punishment-platform` (expected; absent remotely) | No target-layout clone; sibling checkout is `1657a0a` on `deploy/market-experimental` | No feature-branch SHA | None | NOT_RUN | NOT_RUN | N/A | UNVERIFIED | UNKNOWN | Must be cloned under `related-repos`; sibling checkout has untracked `.wrangler/` state that must not be lost or committed. |
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
- Pre-existing goals file SHA-256 before and after checkout: `746DD1B37BBAF517F008441102A6CBF688AABEC09E8196F863509BF484277F9A`
- Root local exclusion: `related-repos/` is present in `.git/info/exclude`

## Workspace layout status

The root worktree and reports are represented by `EnthusiaStaff.code-workspace`. Related repository paths are declared under `related-repos/`, but cloning is intentionally deferred until the required EnthusiaStaff Codacy cleanup checkpoint. Existing sibling worktrees under `C:\Dev\Enthusia` are read-only recovery references and are not treated as the requested final layout.

## Checkpoint rules

- Update every SHA, PR, build, test, Docker, coverage, Codacy, and blocker field after each coherent checkpoint.
- A skipped Docker test is recorded as skipped, never passed.
- A Codacy grade remains unverified until the actual branch analysis is read.
- Keep each nested repository independent; never stage `related-repos/` in the root repository.
