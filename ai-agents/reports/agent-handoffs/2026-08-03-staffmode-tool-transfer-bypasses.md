# Staff-mode staff-tool transfer bypass handoff

Date: 2026-08-03

## Repository and work item

- Repository: `wsg138/EnthusiaStaff`
- Work item: block staff-tool inventory number-key and offhand transfer bypasses
- Starting `main`: `d71759aa4f121c82f984e57d6fd0968a80c502ba`
- Branch: `fix/staffmode-tool-transfer-bypasses`
- Pull request: [#56 — Block staff-tool hotbar and offhand transfer bypasses](https://github.com/wsg138/EnthusiaStaff/pull/56)
- Expected committed state: `IDLE — PR #56 requires live merge verification`

## Live start-state reconciliation

- PR #55 merged normally as `d71759aa4f121c82f984e57d6fd0968a80c502ba` from exact feature head `c6380aae35cf8c56044faf6dea96c471b14634f3`.
- The PR #55 feature head is contained in `main` and its feature branch was removed.
- No pull request was open or draft before PR #56 was created.
- Only `main` existed as an active branch before the PR #56 branch was created.
- PR #55 exact-head Coverage `30812589989` and Validate Wiki `30812589424` completed successfully; its review threads were resolved.
- V16 is the live highest migration. V1–V16 remain immutable and PR #56 adds no migration.

## Confirmed gap

The prior `StaffModeManager` click guard inspected only the clicked item and cursor. A Bukkit number-key click can move the item in the referenced hotbar slot, and an inventory offhand-swap click can move the offhand item, while both the clicked item and cursor are unrelated. Admin and Founder staff sessions could therefore move protected staff tools through those hidden-source transfer paths.

## Implemented behavior

- `StaffToolTransferListener` is the focused active-session inventory-click guard.
- Current-item and cursor staff tools remain protected for every click type.
- `NUMBER_KEY` checks the exact referenced hotbar slot and blocks only when that source is a staff tool.
- `SWAP_OFFHAND` checks the offhand source and blocks only when that source is a staff tool.
- Unrelated click types do not become over-restrictive merely because another hotbar or offhand slot contains a staff tool.
- `StaffModeManager` continues to enforce transition-wide cancellation and rank-specific ordinary/Ender mutation rules.
- Helper all-inventory blocking, PR #55 Admin view-only Ender behavior, Founder owner access, drag protection, ordinary swap-hand blocking, drop/pickup protection and staff-tool cleanup remain unchanged.
- No command, permission, configuration, persistence, protocol or database behavior changes.

## Material files

- `paper/src/main/java/net/enthusia/staff/paper/staff/StaffToolTransferListener.java`
- `paper/src/main/java/net/enthusia/staff/paper/staff/StaffModeAccessPolicy.java`
- `paper/src/main/java/net/enthusia/staff/paper/staff/StaffModeManager.java`
- `paper/src/main/java/net/enthusia/staff/paper/PaperRuntimeComponents.java`
- `paper/src/test/java/net/enthusia/staff/paper/staff/StaffModeAccessPolicyTest.java`

## Tests

`StaffModeAccessPolicyTest` proves:

- current-item and cursor tools block for every Bukkit click type;
- number-key transfer blocks only for the referenced hotbar staff tool;
- inventory offhand swap blocks only for an offhand staff tool;
- ordinary clicks are not blocked because an unreferenced hotbar/offhand slot happens to contain a staff tool;
- all prior rank and Ender boundaries remain covered.

The listener is intentionally thin: it identifies the event sources and delegates the complete transfer decision to the directly tested policy.

## Separate harsh review

The complete PR diff was reviewed for scope, architecture consistency, duplicate systems, Paper event ordering/thread safety, lifecycle and restart effects, persistence/migration impact, rank enforcement, inventory transfer paths, failure behavior, sensitive data, tests and documentation.

### Confirmed defects fixed

1. The first implementation added the focused listener but left the manager's current-item/cursor staff-tool check as a parallel click guard. That duplicated authority and weakened the claim that one decision covered all click paths. The manager now handles only transition/rank mutation restrictions; the dedicated listener owns active-session staff-tool click transfer protection.
2. CodeRabbit correctly found that three workspace-routing records did not define one complete exact-head gate or reject every invalid workflow class. `WORKSPACE-MANIFEST.md`, `ai-agents/WORKSPACE-STATE.md` and `latest.md` now require every configured exact-head check, exact artifact and review evidence, normal merge evidence, and successful applicable public/private Pi staging or a verified recorded exception. They explicitly reject cancelled, superseded, skipped, different-revision and merge-ref-only runs.

### Merge blockers

None remain in tracked implementation before final exact-head validation.

### Optional cleanup

A full Paper event-object staging test would add runtime confidence beyond the thin source-extraction adapter and directly tested policy. It is not a confirmed defect in this bounded change and does not replace the required exact-head Pi/runtime validation.

CodeRabbit's docstring-coverage warning is optional repository-wide style tooling rather than a defect in this focused change. Its PMD execution error is a tool-ruleset compatibility/configuration failure under Java 17, while the repository's own configured Java 21 build, tests and Codacy coverage gate remain authoritative. Neither warning changes runtime behavior or justifies unrelated tool-configuration churn in PR #56.

### Unrelated future work

Rank-change lifecycle, plugin-disable recovery, general inventory editing, confiscation, vanish, freeze, staff-tool actions and broader staff-session acceptance remain separate work items.

## Validation and evidence routing

Tracked content is frozen again after the valid CodeRabbit finding was fixed. Read live PR metadata for revision-specific evidence:

- [PR #56 checks](https://github.com/wsg138/EnthusiaStaff/pull/56/checks)
- [PR #56 review conversation](https://github.com/wsg138/EnthusiaStaff/pull/56)
- [PR #56 commits](https://github.com/wsg138/EnthusiaStaff/pull/56/commits)

One unchanged final feature head must be synchronized with current `main` and receive terminal acceptable results for every configured Java 21 build/test, applicable MariaDB/Testcontainers and migration checksum/immutability check, runtime-JAR/provider-leak inspection, aggregate coverage, Codacy/static-analysis upload, wiki/documentation validation, applicable public/private Pi staging and review gate. Cancelled, superseded, skipped, different-revision and merge-ref-only runs are not exact-head evidence. A Pi exception is valid only when live workflow configuration proves Pi is not applicable or cannot be triggered for the exact feature head and the exception is recorded in PR metadata. Zero unresolved valid threads may remain.

Before merge, PR metadata must record the exact feature head, every run/job and artifact identity, hashes, synchronization evidence and review classification. After a normal merge commit, it must record the merge commit, resulting `main`, feature-head containment, absence of unmerged branch commits and branch cleanup.

## Migration, commands, permissions and configuration

- Highest migration: V16.
- PR #56 migration: none.
- Immutable history: V1–V16.
- Commands added or changed: none.
- Permissions added or changed: none.
- Configuration added or changed: none.

## Production boundary

LiteBans remains authoritative. PR #56 does not deploy a JAR, access production systems or data, use production credentials or Discord routes, activate EnthusiaStaff punishment authority, alter or remove LiteBans, start issue #43 acceptance, perform cutover, edit V1–V16, or use Flyway repair.

## Next recommended work after PR #56

After live merge verification and branch cleanup, continue owner priority one with one separate prerequisite-ready staff-mode lifecycle or restriction-enforcement gap, preferably rank-change correction or reload/disable recovery after fresh code inspection. Do not begin it in this PR.
