# EnthusiaStaff agent handoff — Admin staff-mode Ender view-only

Date: 2026-08-03

## Repository and work item

- Repository: `wsg138/EnthusiaStaff`
- Work item: enforce the configured Admin staff-mode Ender chest boundary as view-only
- Starting `main`: `717d716d34f3e4e524d9b7c744cb5ece3cacaf04`
- Branch: `fix/admin-staffmode-ender-view-only`
- Pull request: `#55 — Enforce Admin staff-mode Ender view-only access`
- Current state: `IMPLEMENTING`

## Live baseline

- PR #54 merged normally into current `main` as `717d716d34f3e4e524d9b7c744cb5ece3cacaf04`.
- No pull request was open or draft when this branch was created.
- No non-main branch remained active.
- V16 is the live highest Flyway migration; V1–V16 are immutable for this work.
- LiteBans remains authoritative and startup remains non-`ACTIVE` by default.

## Confirmed gap

The authoritative rank contract says Admin staff mode may use creative mode but Ender chest access is view-only unless a separate destructive workflow authorizes mutation. The previous Paper policy used one predicate for both opening and mutation. It blocked Helper, Mod, and Developer from opening Ender chests, but permitted Admin and Founder to open and mutate them. An Admin could therefore move items into, out of, or within an Ender chest while staff mode was active, bypassing the player-state safety boundary.

## Implemented behavior

- Ender chest open access and mutation authority are separate policy decisions.
- Helper, Mod, and Developer remain unable to open an Ender chest while staff mode is active.
- Admin may open the Ender chest, but every click and drag in that inventory view is cancelled, including transfer attempts involving the bottom inventory.
- Founder retains normal configured owner access.
- General Admin creative-inventory interaction outside an Ender chest view remains available.
- Staff tools remain protected by the existing click/drag checks.

## Tests

`StaffModeAccessPolicyTest` now covers:

- Helper full inventory restriction and Ender denial;
- Mod Ender open and mutation denial;
- Developer Ender denial while preserving technical staff tools and request-only punishment authority;
- Admin creative mode with view-only Ender access;
- Founder creative mode with owner-level Ender access.

## Remaining work in this PR

- update requirements and workspace routing records;
- inspect hosted build/test/static-analysis results;
- perform and record a separate harsh review of the complete diff;
- fix every confirmed defect or merge blocker;
- freeze the canonical handoff and expected post-merge state;
- complete one unchanged exact-head validation cycle and merge only if every gate passes.

## Exclusions

This work does not implement a general inventory-inspection workflow, offline Ender editing, confiscation, new staff tools, vanish, freeze, modular staff-mode configuration, database schema changes, production deployment, punishment authority, LiteBans changes, or issue #43 acceptance.

## Validation contract

Before merge, the unchanged final feature head must satisfy the repository's configured Java 21 build and tests, Paper tests, runtime-JAR and provider-leak checks, static analysis, wiki/documentation validation, applicable exact-head Pi validation, and zero unresolved valid review threads. Exact SHA, workflow, job, artifact, hash, and merge evidence belongs in live PR metadata rather than this tracked file.

## Harsh-review findings

Not yet frozen. Findings will be classified here as merge blockers, confirmed defects, optional cleanup, or unrelated future work.

## Migration and production boundaries

- No migration is expected.
- V1–V16 must remain byte-identical.
- Do not use Flyway repair or rewrite migration history.
- Do not deploy, access production data or credentials, contact production Discord routes, activate EnthusiaStaff authority, disable LiteBans, or start issue #43 acceptance.

## Next recommended work

After this item is complete, select one separate bounded staff-mode lifecycle or restriction-enforcement gap after fresh live reconciliation. Do not begin it in this PR.
