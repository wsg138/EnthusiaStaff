# Legacy Wiki snapshot

This directory preserves the exact Markdown tree that was live before the Wiki
became repository-managed.

- Wiki repository: `wsg138/EnthusiaStaff.wiki`
- Preserved Wiki commit: `ea4f929710d3281aac4a8087da1e947973c2d795`
- Latest preserved commit message: `Clarify idempotent audit row counts`
- Snapshot date: 2026-07-30
- Files: 14 Markdown pages
- History verified from the uploaded Git bundle: 10 commits

The uploaded backup bundle was verified by cloning it and comparing its checked
out files with the uploaded Wiki ZIP after normalizing CRLF/LF line endings.
The bundle is intentionally not committed to the main source repository because
it contains a complete separate Git repository.

## Restore the exact old Wiki

With the original bundle:

```bash
git clone EnthusiaStaff-wiki-backup.bundle restored-wiki
cd restored-wiki
git remote set-url origin https://github.com/wsg138/EnthusiaStaff.wiki.git
git push --force-with-lease origin ea4f929710d3281aac4a8087da1e947973c2d795:master
```

Prefer restoring the automatically created `backup/repo-managed-*` branch from
the Wiki repository instead of force-pushing a local bundle when that branch is
available.

The files below are exact LF-normalized copies of the preserved Wiki tree.
