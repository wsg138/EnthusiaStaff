# Staff-mode staff-tool transfer bypass handoff

Date: 2026-08-03

## Repository and work item

- Repository: `wsg138/EnthusiaStaff`
- Work item: block staff-tool inventory number-key and offhand transfer bypasses
- Starting `main`: `d71759aa4f121c82f984e57d6fd0968a80c502ba`
- Branch: `fix/staffmode-tool-transfer-bypasses`
- Pull request: draft PR to be opened from this branch
- Current state: `IMPLEMENTING`

## Live start-state reconciliation

- PR #55 merged normally as `d71759aa4f121c82f984e57d6fd0968a80c502ba` from exact feature head `c6380aae35cf8c56044faf6dea96c471b14634f3`.
- The PR #55 feature head is contained in `main`.
- No pull request was open or draft before this branch was created.
- Only `main` existed as an active branch before this branch was created; the PR #55 branch had been removed.
- PR #55 exact-head Coverage `30812589989` and Validate Wiki `30812589424` completed successfully; its review threads are resolved.
- V16 is the live highest migration. V1–V16 remain immutable and this work adds no migration.

## Confirmed gap

`StaffModeManager.onInventoryClick` currently blocks protected staff-tool transfers only when the clicked item or cursor is a staff tool. Bukkit inventory number-key swaps can move an item from the selected hotbar slot, and inventory offhand swaps can move the offhand item, while both `currentItem` and `cursor` are unrelated. An Admin or Founder staff session can therefore move a protected staff tool through those paths despite the intended no-leak guard.

## Scope

- Centralize the staff-tool inventory-click transfer decision.
- Block number-key/hotbar transfers when the referenced hotbar item is a staff tool.
- Block inventory offhand swaps when the offhand item is a staff tool.
- Preserve allowed non-tool hotbar and offhand swaps for ranks whose profile permits ordinary inventory mutation.
- Preserve Helper all-inventory blocking, transition blocking, drag protection, drop/swap-hand protection, and PR #55 Ender access rules.
- Add focused tests for every transfer path and negative cases.
- Update workspace state, manifest, requirements matrix, development blueprint, latest pointer, and this one canonical handoff before final validation.

## Exclusions

No rank-change lifecycle, plugin-disable recovery, general inventory editing, confiscation, vanish, freeze, new tools, permissions, configuration, migration, provider API, deployment, production access, punishment-authority change, LiteBans change, or issue #43 acceptance.

## Validation contract

After implementation and a separate harsh review, freeze tracked content and validate one unchanged exact feature head through the configured Java 21 build/tests, Paper tests, migration immutability/checksum checks, runtime-JAR/provider-leak checks, static analysis, documentation validation, exact-head Pi when configured and triggered, and all valid review gates with zero unresolved threads.

Exact SHA, run/job IDs, artifact hashes, review evidence, and merge evidence belong in live PR metadata rather than this tracked report.

## Production and migration boundary

LiteBans remains authoritative. This work does not deploy a JAR, access production systems or data, use production credentials or Discord routes, activate EnthusiaStaff punishment authority, alter or remove LiteBans, start issue #43 acceptance, perform cutover, edit V1–V16, or use Flyway repair.

## Next recommended work after this PR

After this single work item is complete, continue owner priority one with one separate prerequisite-ready staff-mode lifecycle or restriction-enforcement gap. Do not begin it in this PR.
