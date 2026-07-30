# Repository-managed Wiki

The live GitHub Wiki is generated from the Markdown files in `docs/wiki/pages/`.
The main repository is the reviewable source of truth; the separate `.wiki.git`
repository is only the publication target.

## Why this exists

GitHub stores the Wiki in a separate Git repository. That makes direct edits easy,
but it separates operational and developer documentation from the code,
permissions, configuration, and tests that it describes.

This directory keeps changes reviewable:

1. Edit pages in `docs/wiki/pages/`.
2. Run `python scripts/wiki/validate_wiki.py`.
3. Open and review a pull request.
4. Merge the approved documentation change.
5. Run the **Publish Wiki** workflow manually from `main`.

The publish workflow does not run automatically. Before replacing live Wiki
Markdown, it creates and pushes a timestamped backup branch in the Wiki
repository and uploads a Git bundle as a workflow artifact.

## Directory layout

```text
docs/wiki/
├── README.md
├── pages/                       # Files published to the live Wiki root
│   ├── Home.md
│   ├── _Sidebar.md
│   ├── Staff-Handbook.md
│   ├── Developer-Code-Guide.md
│   └── ...
└── legacy/
    └── ea4f929/                 # Exact pre-migration Wiki snapshot
```

GitHub Wiki pages are flat. Keep every publishable page directly inside
`docs/wiki/pages/`; use page names and the sidebar for organization instead of
subdirectories.

## Audience sections

The Wiki separates:

- staff procedures and moderation guidance;
- commands, permissions, integrations, and implementation status;
- installation, migration, cutover, and recovery operations;
- developer architecture, source navigation, tests, and code-review guidance.

`Developer-Code-Guide.md` is the practical source map for reviewers. Update it
whenever important entry points, packages, stores, feature flows, or test
locations change.

## Source hierarchy

When documents disagree, use this order:

1. `ENTHUSIASTAFF-GOALS.md` for intended finished behavior.
2. Current code, configuration, tests, and runtime evidence for implemented behavior.
3. `reports/REQUIREMENTS-MATRIX.md` for conservative implementation status.
4. The Wiki for staff, operator, and developer instructions.

The Wiki must never claim a feature is production-ready merely because a command,
class, configuration key, or unit test exists.

## Status labels

Use these labels consistently:

- **Available** — implemented and verified in the relevant environment.
- **Available with limitations** — usable, but the listed limitations matter.
- **Implemented, not staging-verified** — code and tests exist, but live behavior is unproven.
- **Partial** — only part of the documented workflow exists.
- **Blocked** — an external dependency, provider, or required environment is missing.
- **Planned** — required by the goals document but not implemented.
- **Deprecated** — retained only for migration or compatibility.

## Editing rules

- Preserve stable page filenames when possible; changing a filename breaks Wiki links.
- Use `[[Page Name]]` links for Wiki pages.
- Link source-controlled technical documents to the exact repository path.
- Put staff-facing procedures before implementation detail.
- State required rank, permission, confirmation text, evidence, and failure behavior.
- Never include secrets, raw network addresses, private-message evidence, or real case data.
- Update `Implementation-Status.md` whenever a feature becomes available, blocked, or removed.
- Update command and permission pages when `plugin.yml` changes.
- Update the developer guide when code ownership or important review paths change.
- Update the Wiki in the same pull request as a behavior change whenever practical.

## Local validation

```bash
python scripts/wiki/validate_wiki.py
```

The validator checks required pages, flat layout, duplicate page names, UTF-8 and
line endings, headings, page size, Wiki links, relative Markdown links, and common
placeholder mistakes.

See `pages/Wiki-Maintenance.md` for the one-time GitHub environment and token
setup, manual publication, verification, and restore procedure.
