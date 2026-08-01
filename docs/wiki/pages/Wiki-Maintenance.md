# Wiki Maintenance

The main repository is the source for the live GitHub Wiki. Do not make the live
Wiki the only copy of a change.

## Repository locations

| Location | Purpose |
| --- | --- |
| `docs/wiki/pages/*.md` | Publishable Wiki pages |
| `docs/wiki/legacy/ea4f929/` | Preserved pre-migration pages |
| `scripts/wiki/validate_wiki.py` | Internal-link/page validation |
| `.github/workflows/wiki-validate.yml` | Pull-request and main validation |
| `.github/workflows/wiki-publish.yml` | Protected manual publication from `main` |

## Information architecture

Keep each kind of information in one primary location:

| Information | Primary owner |
| --- | --- |
| Main navigation and task routing | `Home.md` and `_Sidebar.md` |
| Overall percentages and group directory | `Implementation-Status.md` |
| Core feature descriptions/files | `Core-Platform-and-Infrastructure.md` |
| Moderation feature descriptions/files | `Moderation-Punishments-and-Reports.md` |
| Staff-tool feature descriptions/files | `Staff-Tools-Investigations-and-Player-State-Safety.md` |
| Integration/release descriptions/files | `Integrations-Migration-and-Release-Readiness.md` |
| Cross-group development order | `Development-Blueprint.md` |
| Complete source map and feature traces | `Developer-Code-Guide.md` |
| Staff procedures | Focused staff guide for the task |
| Administrator references | Commands, ranks, configuration and integrations pages |
| Operational procedures | Installation, recovery, migration and cutover pages |
| Validation commands/evidence | `Build-and-Testing.md` |
| Exact implementation proof/blockers | `reports/REQUIREMENTS-MATRIX.md` |

Do not copy the same percentage table, command list, source-file map or release
procedure into multiple pages. Link to the owning page.

## Navigation rules

- `Home.md` should answer “where do I start?” by role and task.
- `_Sidebar.md` should remain compact and expose the four feature hubs directly.
- Every focused staff/admin page should link to its matching feature hub.
- Every feature hub should link to important source files, related procedures and
  the requirements matrix.
- New pages must be reachable from Home, the sidebar or an owning index.
- Preserve stable filenames when possible so old Wiki links continue to work.
- Use headings that describe the question answered by the section.

## Editing process

1. Update code, configuration and exact status sources first.
2. Update the relevant `reports/REQUIREMENTS-MATRIX.md` row.
3. Edit only the Wiki page that owns the changed information.
4. Update the matching feature hub when purpose, percentage, files or remaining
   work changed.
5. Update `Implementation-Status.md` only when the group summary changed.
6. Update `_Sidebar.md` and Home when a page is added, removed or renamed.
7. Run `python scripts/wiki/validate_wiki.py`.
8. Open a pull request and inspect every changed link/path.
9. Review technical accuracy, staff clarity, privacy, duplication and navigation.
10. Merge the approved pull request.
11. Publish the reviewed Wiki source from `main`.

## Writing style

- Start with what the page is for and who should use it.
- Put a short navigation section near the top.
- Separate live/staff procedure from intended or incomplete behavior.
- Explain a feature in plain language before listing implementation files.
- For destructive actions, include explicit stop/escalation conditions.
- Keep private information and secrets out of examples.
- Do not claim tests/staging passed without exact evidence.
- Prefer direct source-file links on feature hubs; use the Developer Code Guide for
  complete end-to-end traces.

## One-time GitHub setup

### Workflow permission

Repository settings:

```text
Settings -> Actions -> General -> Workflow permissions
```

The publisher needs write permission. Repository/organization policy may require
an owner to allow it.

### Protected environment

Create an environment named exactly:

```text
wiki-production
```

Recommended protection:

- allow deployments only from `main`;
- require a trusted reviewer when available;
- prevent self-review when publication should require a second person.

### Wiki token fallback

The permanent publisher first tries the job's `GITHUB_TOKEN`. If the Wiki push is
rejected, configure an environment secret named:

```text
WIKI_PUBLISH_TOKEN
```

Use the narrowest token that can write the repository's `.wiki.git` remote. Never
place the token in source, issues, Wiki pages or logs. Set an expiration and rotate
or remove it when no longer required.

### Keep the Wiki enabled

Publication target:

```text
https://github.com/wsg138/EnthusiaStaff.wiki.git
```

Visible Wiki:

```text
https://github.com/wsg138/EnthusiaStaff/wiki
```

## Publishing

After the source pull request is merged:

1. Open **Actions**.
2. Select **Publish Wiki**.
3. Choose **Run workflow** on `main`.
4. Enter `PUBLISH` exactly.
5. Approve `wiki-production` when required.
6. Confirm validation, clone, backup branch, artifact upload, commit and push.
7. Open the live Wiki and verify Home, sidebar, feature hubs and changed pages.

Merging the source repository alone does not update the separate Wiki repository.

## Publication safety

The protected publisher:

1. validates repository-managed pages;
2. clones the live Wiki;
3. creates a full Git bundle;
4. pushes a timestamped backup branch;
5. uploads the pre-publish bundle as an artifact;
6. replaces root Markdown pages from reviewed source;
7. records the source revision in Wiki history;
8. pushes the detected Wiki default branch.

The live Wiki should remain unchanged when validation, clone or backup creation
fails.

## Confirming publication

The workflow summary should record:

- source revision;
- backup branch;
- backup artifact;
- published Wiki branch.

Verify:

- Home displays the role/task navigation;
- sidebar includes all four feature hubs;
- Feature Completion Status links to each hub;
- internal Wiki links open the expected page;
- direct source links point to current files/directories;
- stale pages were replaced rather than duplicated.

## Restore

Preferred restore uses the automatically created backup branch:

```bash
git clone https://github.com/wsg138/EnthusiaStaff.wiki.git
cd EnthusiaStaff.wiki
git reset --hard origin/backup/<exact-branch-from-workflow>
git push --force-with-lease origin HEAD:<reported-default-branch>
```

Use the exact branch names from the workflow summary. The original pre-migration
state is also preserved at `ea4f929710d3281aac4a8087da1e947973c2d795` and under
`docs/wiki/legacy/ea4f929/`.

## Review checklist

### Navigation and ownership

- Home routes by role and task.
- Sidebar is compact and complete.
- Every new page is reachable.
- Feature hubs link to source files and focused procedures.
- The same table/description is not copied across several pages.

### Accuracy

- Staff instructions match actual command usage.
- Rank limits match central policy and `plugin.yml`.
- Planned behavior is not described as deployed.
- Percentages agree with the requirements matrix.
- Developer file paths still match the source tree.
- Active branch work is not counted as merged behavior.

### Safety and privacy

- Destructive workflows include stop conditions.
- Private evidence, addresses, coordinates and secrets are not exposed.
- Optional-provider limitations are explicit.
- Recovery advice does not recommend blind retries or raw storage edits.

### Validation

- `python scripts/wiki/validate_wiki.py` passes.
- Changed external source links were manually checked.
- Wiki validation and normal repository checks pass on the final PR head.
