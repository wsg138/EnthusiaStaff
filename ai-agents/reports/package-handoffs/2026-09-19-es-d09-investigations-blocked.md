# ES-D09 — Discord investigations — blocked / parked

Date: 2026-09-19

Status: `BLOCKED` / `PARKED_BLOCKED`.

## Preserved implementation

- Implementation PR: #203.
- Branch: `package/es-d09-discord-investigations`.
- Frozen executable/product head: `a48390c50c6968e75437abd2dd05c0faeece355d`.
- PR #203 remains open and unmerged at that exact executable head.
- Do not squash, rebase, renumber the D09 migration, absorb X03, or merge #203 while the migration-serialization blocker below remains.

## Implemented product scope

The frozen D09 implementation provides bounded Discord message-context evidence and edit history, `Capture more context`, private versioned notes scoped to person/Discord/Minecraft/case, automatic punishment-case association, investigation-only cases, meaningful activity tracking and 30-day inactive closure, evidence retention, durable linked-alt/evasion alerts, Minecraft and Discord staff alert delivery, and manual-decision-only alt/evasion handling. No automatic linked-alt punishment is introduced.

Message attachments are metadata-only. Private investigation data remains staff-private; the Paper alert route carries only a generic alert UUID rather than private evidence or linked-identity details.

## Exact validation evidence

Exact repair validation workflow `35387277563`, job `105737009460`, succeeded. It checked out the exact candidate, applied the reviewed repairs, and passed:

- Temurin/Java 21 clean build and tests;
- MariaDB/Testcontainers integration tests;
- StaffBot runtime verification;
- PMD with zero valid changed-code findings;
- changed-method complexity bounds;
- regression/diff bounds; and
- `git diff --check`.

The validated candidate was promoted unchanged as frozen executable/product head `a48390c50c6968e75437abd2dd05c0faeece355d`.

Exact hosted PR-head gates on that SHA are terminal green:

- Coverage run `35388283034`, job `105740323648`: `SUCCESS`;
- Staff Bot PR Artifact run `35388283005`: `SUCCESS`;
- Staff Bot Configuration Cache run `35388283022`: `SUCCESS`;
- Sentinel Restart Artifact run `35388282989`: `SUCCESS`;
- Codacy Static Code Analysis check `105740695905`: `SUCCESS`, zero annotations / zero new valid findings;
- Codacy Diff Coverage: `SUCCESS`;
- Codacy Coverage Variation: `SUCCESS`; and
- CodeRabbit commit status: `SUCCESS`.

All substantive product-code CodeRabbit findings are repaired and their review threads are resolved. The one older unresolved thread on `ai-agents/reports/package-handoffs/2026-09-15-es-d09-active.md` reports stale tracking text, not an unresolved product defect; this terminal state publication supersedes that active checkpoint without mutating the frozen executable PR merely for status text.

The final repair regressions cover:

- expired, future-expiring, and permanent evasion-alert candidates without aborting valid candidate batches;
- administrator-role privacy rejection for private Discord alert-channel viewer fencing;
- normal Minecraft punishment `cases.subject_id` persistence;
- LiteBans-import `cases.subject_id` persistence; and
- narrow compatibility fallback for pre-existing target-only case rows lacking `subject_id`.

## Migration serialization blocker

Fresh reconciliation on 2026-09-19 proves:

- live `main` is based on `9ad537c70eb34f885291afae0cb5cb8fe65e2106` at this status-publication start;
- canonical `main` contains Staff migrations only through V20;
- `ES-X03` / PR #139 remains open/unmerged and is the legitimate owner of branch-local `V21__market_compliance_journal.sql`;
- D09 remains the legitimate owner of `V22__discord_investigation_state.sql`.

The migration sequence therefore cannot safely accept D09 V22 yet. D09 must not rename V22 to V21 and must not take over or synchronize X03 merely to unblock itself.

Exact unblock:

Merge the legitimate owner of Staff migration V21 into `main`, then reconcile D09 with the resulting live migration chain, resolve only legitimate conflicts, rerun all exact-head executable gates affected by reconciliation, refresh review/Codacy evidence, and only then reconsider merging PR #203.

## Boundaries

No production Discord mutation, production/private data access, deployment, cutover, production Discord configuration/intents change, LiteBans authority change, AutoMod enforcement, cross-platform authority change, or issue #43 acceptance was performed. Production Message Content privileged-intent enablement remains outside this source package.

`ES-D13` / PR #178 remains independently `BLOCKED` / `PARKED_BLOCKED` and untouched. `ES-X03` / PR #139 remains separately owned and untouched. No unrelated worker branch or package was absorbed.

## Resume instruction

Resume D09 only when live `main` contains the legitimate Staff V21. Reconcile normally with current `main`, preserve merge history, keep D09 as V22, repair only legitimate conflicts, rerun every executable/static/review gate invalidated by reconciliation, freeze a new exact head, and proceed to a normal implementation merge only if all required gates are terminal green. Until then, preserve PR #203 open/unmerged at `a48390c50c6968e75437abd2dd05c0faeece355d`.
