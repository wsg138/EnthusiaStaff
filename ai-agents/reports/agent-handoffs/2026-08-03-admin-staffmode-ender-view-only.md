# EnthusiaStaff agent handoff — Admin staff-mode Ender view-only

Date: 2026-08-03

## Repository and work item

- Repository: `wsg138/EnthusiaStaff`
- Work item: enforce the configured Admin staff-mode Ender chest boundary as view-only
- Starting `main`: `717d716d34f3e4e524d9b7c744cb5ece3cacaf04`
- Branch: `fix/admin-staffmode-ender-view-only`
- Pull request: draft PR to be assigned
- Current state: `PLANNING`

## Live baseline

- PR #54 merged normally into current `main` as `717d716d34f3e4e524d9b7c744cb5ece3cacaf04`.
- No pull request was open or draft when this branch was created.
- No non-main branch remained active.
- V16 is the live highest Flyway migration; V1–V16 are immutable for this work.
- LiteBans remains authoritative and startup remains non-`ACTIVE` by default.

## Confirmed gap

The authoritative rank contract says Admin staff mode may use creative mode but Ender chest access is view-only unless a separate destructive workflow authorizes mutation. The current Paper policy uses one predicate for both opening and mutation. It blocks Helper, Mod, and Developer from opening Ender chests, but permits Admin and Founder to open and mutate them. An Admin can therefore move items into, out of, or within an Ender chest while staff mode is active, bypassing the player-state safety boundary.

## Scope

- separate Ender chest open access from Ender chest mutation authority;
- keep Helper, Mod, and Developer unable to open Ender chests in staff mode;
- allow Admin to open an Ender chest while cancelling every inventory click or drag in that view;
- preserve Founder owner-level configured access;
- add focused policy tests covering every rank boundary;
- update repository routing, requirements evidence, workspace state, manifest, and this canonical handoff;
- perform a separate harsh review of the complete PR diff and exact-head validation before merge.

## Exclusions

This work does not implement a general inventory-inspection workflow, offline Ender editing, confiscation, new staff tools, vanish, freeze, modular staff-mode configuration, database schema changes, production deployment, punishment authority, LiteBans changes, or issue #43 acceptance.

## Validation contract

Before merge, the unchanged final feature head must satisfy the repository's configured Java 21 build and tests, Paper tests, runtime-JAR and provider-leak checks, static analysis, wiki/documentation validation, applicable exact-head Pi validation, and zero unresolved valid review threads. Exact SHA, workflow, job, artifact, hash, and merge evidence belongs in live PR metadata rather than this tracked file.

## Migration and production boundaries

- No migration is expected.
- V1–V16 must remain byte-identical.
- Do not use Flyway repair or rewrite migration history.
- Do not deploy, access production data or credentials, contact production Discord routes, activate EnthusiaStaff authority, disable LiteBans, or start issue #43 acceptance.

## Next recommended work

After this item is complete, select one separate bounded staff-mode lifecycle or restriction-enforcement gap after fresh live reconciliation. Do not begin it in this PR.
