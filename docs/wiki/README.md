# Repository-managed Wiki

The live GitHub Wiki is generated from the Markdown files in `docs/wiki/pages/`.

## Why this exists

GitHub stores the Wiki in a separate Git repository. That makes Wiki edits easy
to publish without review, but it also separates operational documentation from
the code, permissions, configuration, and tests that it describes.

This directory makes the main repository the reviewable source of truth:

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
├── pages/                  # Files published to the live Wiki root
└── legacy/
    └── ea4f929/            # Exact pre-migration Wiki snapshot
```

GitHub Wiki pages are flat. Keep every publishable page directly inside
`docs/wiki/pages/`; use page names and the sidebar for organization instead of
subdirectories.

## Source hierarchy

When documents disagree, use this order:

1. `ENTHUSIASTAFF-GOALS.md` for intended finished behavior.
2. Current code, configuration, tests, and runtime evidence for implemented behavior.
3. `reports/REQUIREMENTS-MATRIX.md` for conservative implementation status.
4. The Wiki for staff and operator instructions.

The Wiki must never claim a feature is production-ready merely because a
command, class, configuration key, or unit test exists.

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
- Update the Wiki in the same pull request as a behavior change whenever practical.

## Local validation

```bash
python scripts/wiki/validate_wiki.py
```

The validator checks required pages, duplicate page names, headings, Wiki links,
relative Markdown links, and common placeholder mistakes.
