# Repository-managed Wiki

The live GitHub Wiki is generated from Markdown under `docs/wiki/pages/`. The main repository is the reviewable authoring source; the separate `.wiki.git` repository is only the publication target.

## Workflow

1. Edit repository-managed pages.
2. Run `python scripts/wiki/validate_wiki.py`.
3. Open and review a documentation pull request.
4. Merge the approved source change to `main`.
5. Run **Publish Wiki** manually from `main` with the required `PUBLISH` confirmation.
6. Verify the live Home, sidebar and changed pages.

The publisher validates first, then creates a timestamped live-Wiki backup branch and Git bundle before replacing Markdown.

## Directory layout

```text
docs/wiki/
├── README.md
├── pages/                       # published to the live Wiki root
│   ├── Home.md
│   ├── _Sidebar.md
│   ├── Developer-Guide-Index.md
│   ├── Developer-Code-Guide.md
│   ├── Code-Review-Guide.md
│   └── ...
└── legacy/
    └── ea4f929/                 # preserved pre-migration Wiki snapshot
```

GitHub Wiki pages are flat. Keep publishable pages directly in `docs/wiki/pages/`; organize them through filenames, hubs and links rather than subdirectories.

## Audience and progressive disclosure

The Wiki supports:

- staff procedures;
- administrator configuration/reference;
- operations, release and recovery;
- developer setup/architecture/source navigation;
- code review;
- quick answers that can continue into deep implementation detail.

Keep the primary page readable. Put exhaustive source maps in `Developer-Code-Guide.md`, cross-cutting review discipline in `Code-Review-Guide.md`, validation/evidence interpretation in `Build-and-Testing.md`, and complex internals on focused deep-dive pages.

`Developer-Guide-Index.md` is the developer task router; it should not grow into a second source map.

## Source hierarchy

When documents disagree:

1. `ENTHUSIASTAFF-GOALS.md` defines intended finished behavior.
2. Current merged code, configuration, migrations, tests and runtime evidence define implemented behavior.
3. `reports/REQUIREMENTS-MATRIX.md` plus current legitimate review/runtime evidence describe conservative proof/blockers; reconcile them with live `main` after recent merges.
4. The Wiki explains the result for staff, operators, developers and reviewers.

The Wiki must not claim production readiness merely because a class, command, configuration key, unit test or merged PR exists.

## Status labels

Use these consistently:

- **Available** — implemented and verified in the environment relevant to the claim.
- **Available with limitations** — usable for the stated scope, with material limitations listed.
- **Implemented, not staging-verified** — merged code and relevant automated evidence exist, but representative runtime staging has not established the full claim.
- **Partial** — meaningful foundations exist, but the documented workflow is incomplete.
- **Blocked** — a required dependency, environment or authority gate is unavailable.
- **Planned** — required by the goals but not implemented.
- **Deprecated** — retained only for migration/compatibility.

Prefer these meaningful states and an explicit remaining-work sentence over invented exact percentages.

## Editing rules

- Preserve stable page filenames when practical.
- Use `[[Page Name]]` or `[[Label|Page-Filename]]` for internal Wiki navigation.
- Put staff-facing procedure before implementation detail.
- Link source-controlled technical documents to current repository paths.
- Never include secrets, raw network addresses, private-message evidence, real case/punishment data, credentials or private server information.
- Describe unmerged work as development/in-progress if it must be mentioned at all.
- Update `Implementation-Status.md` when the merged product/evidence state materially changes.
- Update commands/permissions/configuration pages when their authoritative source changes.
- Update `Developer-Code-Guide.md` when important code ownership or traces move.
- Update `Code-Review-Guide.md` only for genuinely cross-cutting review concerns, not every feature-specific implementation detail.
- Keep transient package/worker routing in `ai-agents/`, not general Wiki product pages.
- Update the Wiki in the same PR as a behavior change when practical; documentation-only reconciliation is also valid when it does not modify package state.

## Validation

```bash
python scripts/wiki/validate_wiki.py
```

The validator checks required pages, flat layout, duplicate normalized page names, UTF-8/LF, H1 headings, page size, internal Wiki links, relative Markdown links and placeholder mistakes.

It does not prove technical truth, source-link freshness, privacy judgment, staging or production readiness. Those still require manual review.

See `pages/Wiki-Maintenance.md` for page ownership, writing/navigation rules, protected publication, verification and restore.