# Wiki Maintenance

The main repository is the source for the live Wiki.

## Files

Publishable pages:

```text
docs/wiki/pages/*.md
```

Preserved pre-migration pages:

```text
docs/wiki/legacy/ea4f929/
```

Validator:

```text
scripts/wiki/validate_wiki.py
```

Workflows:

```text
.github/workflows/wiki-validate.yml
.github/workflows/wiki-publish.yml
```

## Editing process

1. Update code/config/status sources first.
2. Edit the relevant page in `docs/wiki/pages`.
3. Preserve stable filenames.
4. Update `_Sidebar.md` if a page is added or renamed.
5. Update `Implementation-Status.md` when availability changes.
6. Run `python scripts/wiki/validate_wiki.py`.
7. Open a pull request.
8. Review technical accuracy, staff clarity, privacy, and links.
9. Merge.
10. Manually run **Publish Wiki** from `main`.

## Publishing safety

Publishing is manual. The workflow:

1. Validates source pages.
2. Clones the live Wiki.
3. Creates a full Git bundle.
4. Pushes a timestamped backup branch.
5. Replaces only root Markdown pages.
6. Commits the exact source commit to Wiki history.
7. Pushes the Wiki default branch.
8. Uploads the pre-publish bundle as a workflow artifact.

If the built-in `GITHUB_TOKEN` cannot push the Wiki, configure a repository
secret named `WIKI_PUBLISH_TOKEN` with the minimum repository contents access
needed. Do not store the token in YAML.

## Restore

Preferred restore:

```bash
git clone https://github.com/wsg138/EnthusiaStaff.wiki.git
cd EnthusiaStaff.wiki
git reset --hard origin/backup/repo-managed-<timestamp>
git push --force-with-lease origin HEAD:master
```

Use an exact branch name shown in the publish workflow summary.

The original pre-migration state is also preserved at
`ea4f929710d3281aac4a8087da1e947973c2d795` and under
`docs/wiki/legacy/ea4f929/`.

## Review checklist

- Staff instructions match actual command usage
- Rank limits match service policy
- Planned behavior is not described as available
- Destructive workflows include stop conditions
- Private data is not exposed
- External integration limitations are stated
- Old page links still resolve
- No real player, case, address, secret, or credential data
- Requirements matrix and status page agree
