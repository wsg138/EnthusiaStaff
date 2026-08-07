# Wiki Maintenance

The main repository is the authoring source for the live GitHub Wiki. The separate `.wiki.git` repository is a publication target only; do not make a direct live-Wiki edit the only copy of a documentation change.

## Repository locations

| Location | Purpose |
| --- | --- |
| `docs/wiki/pages/*.md` | Publishable Wiki pages |
| `docs/wiki/README.md` | Repository-managed Wiki conventions |
| `docs/wiki/legacy/ea4f929/` | Preserved pre-migration pages |
| `scripts/wiki/validate_wiki.py` | Internal-link/page validation |
| `.github/workflows/wiki-validate.yml` | Pull-request/main Wiki validation |
| `.github/workflows/wiki-publish.yml` | Protected manual publication from `main` |

## Information architecture

Use progressive disclosure:

1. answer the reader's immediate question first;
2. keep staff/index pages short enough to scan;
3. link to a focused page for procedures or internals;
4. use [[Developer Code Guide]] for detailed source maps;
5. use [[Code Review Guide]] for cross-cutting review discipline;
6. use authoritative repository evidence for exact implementation/proof claims.

One primary owner per information type reduces drift:

| Information | Primary owner |
| --- | --- |
| Role/task routing | `Home.md` and `_Sidebar.md` |
| Merged-main product/evidence state | `Implementation-Status.md` |
| Core feature status and entry points | `Core-Platform-and-Infrastructure.md` |
| Moderation feature status and entry points | `Moderation,-Punishments,-and-Reports.md` |
| Staff/player-state feature status and entry points | `Staff-Tools,-Investigations,-and-Player-State-Safety.md` |
| Provider/migration/release status and entry points | `Integrations,-Migration-and-Release-Readiness.md` |
| Durable remaining-product map | `Development-Blueprint.md` |
| Developer task routing | `Developer-Guide-Index.md` |
| Detailed source map and feature traces | `Developer-Code-Guide.md` |
| Cross-cutting code-review checklist | `Code-Review-Guide.md` |
| Validation commands and evidence interpretation | `Build-and-Testing.md` |
| Staff procedures | focused staff page for that task |
| Admin references | commands, ranks, configuration and integrations pages |
| Operational procedures | installation, recovery, migration and cutover pages |
| Exact finished intent | `ENTHUSIASTAFF-GOALS.md` |
| Requirement/evidence ledger | `reports/REQUIREMENTS-MATRIX.md` plus current legitimate review/runtime evidence |
| Worker/package orchestration | `ai-agents/`; not general Wiki product pages |

Do not copy the same command table, source map, review checklist, release procedure, or transient worker/package state across many pages. Link to the owning page.

## Navigation rules

- `Home.md` should let Staff, Administration, Operations, Development, Code Review, and Troubleshooting readers choose a route quickly.
- `_Sidebar.md` should remain compact; it is navigation, not a complete index.
- Feature hubs should explain purpose, current merged-main state, important limitations and primary source entry points.
- Focused staff pages should lead with procedure/safety and defer internals.
- Developer index pages should route rather than duplicate the detailed source map.
- New deep-dive pages must be linked from their owning hub/index and from the sidebar only when they are common enough to deserve permanent placement.
- Preserve stable filenames when practical so existing Wiki links continue to work.
- Use a small `Related pages`, `See also`, or `Go deeper` section instead of repeating large navigation blocks.

## Source-of-truth discipline

When sources disagree:

1. `ENTHUSIASTAFF-GOALS.md` defines intended finished behavior.
2. Current merged code, configuration, migrations, tests and runtime evidence define implemented behavior.
3. The requirements matrix and current legitimate review/runtime evidence describe proof/blockers; reconcile them with live `main` after recent merges.
4. The Wiki explains the result for humans.

Do not describe unmerged code as available. If active development is useful context, label it explicitly as development/in progress and keep it out of the merged-main status table.

Do not call a feature staging-verified merely because unit, integration or Testcontainers tests exist. See [[Build and Testing]].

## Editing process

For a normal product change:

1. Determine the authoritative behavior and current merged implementation.
2. Update the focused Wiki page that owns the changed human-facing behavior.
3. Update the matching feature hub if state, limitation, source ownership, or navigation changed.
4. Update [[Developer Code Guide]] only when important source ownership/traces changed.
5. Update [[Code Review Guide]] only when a new cross-cutting invariant/review class is genuinely introduced.
6. Update Home/sidebar only when navigation should change.
7. Run `python scripts/wiki/validate_wiki.py`.
8. Manually inspect changed internal/source links, privacy, status wording, and duplication.
9. Review and merge the documentation through the normal repository process.
10. Publish the reviewed `main` source through **Publish Wiki**.

Package/workspace state has separate ownership. A Wiki worker or ordinary documentation fix should not rewrite package routing/status merely because it finds stale prose elsewhere.

## Writing rules

- State what the page is for near the top.
- Give the useful answer or common action before implementation detail.
- Distinguish merged repository behavior, staging evidence and production acceptance.
- Prefer meaningful states/limitations over invented exact percentages.
- Explain a feature before listing source files.
- Put exhaustive source maps on [[Developer Code Guide]], not staff quick-start pages.
- Put review invariants on [[Code Review Guide]], not every feature page.
- For destructive workflows, give explicit stop/escalation/recovery conditions.
- Keep examples sanitized; never use real punishment/case records, raw addresses, credentials, private-message evidence or private server details.
- Do not claim a check, provider, staging environment or production gate passed without exact evidence.

## Validation

Run from repository root:

```bash
python scripts/wiki/validate_wiki.py
```

The validator checks structural rules such as required pages, flat layout, UTF-8/LF, H1 headings, duplicate normalized page names, internal Wiki links, relative Markdown links, size limits and placeholder tokens.

It does **not** prove technical truth, external source links, privacy judgment, implementation status, or staging evidence. Manually verify those.

Before merge confirm:

- every new page is reachable;
- Home/sidebar destinations exist;
- aliases use the correct Wiki filename/slug;
- source links still name real current files;
- no active unmerged feature is described as merged;
- no sensitive/private data was copied into the Wiki;
- focused pages still own focused procedure rather than duplicating an index/hub.

## Publication setup

The publisher uses the protected `wiki-production` environment and requires write access to the Wiki repository. Repository/organization policy may require a reviewer.

The workflow uses the protected environment secret `WIKI_PUBLISH_TOKEN` when it is configured. Otherwise, it uses the job's `GITHUB_TOKEN`:

```text
WIKI_PUBLISH_TOKEN
```

Use the narrowest token that can write `wsg138/EnthusiaStaff.wiki.git`. Never place the token in source, issues, Wiki pages or logs.

## Publishing

After the documentation PR is merged to `main`:

1. Open **Actions** -> **Publish Wiki**.
2. Run it on `main`.
3. Enter `PUBLISH` exactly.
4. Approve `wiki-production` if required.
5. Confirm Wiki validation succeeds.
6. Confirm the workflow clones the live Wiki, creates a timestamped backup branch and uploads the pre-publish bundle.
7. Confirm it replaces the live Markdown from `docs/wiki/pages/` and records the source revision.
8. Verify the live Home, sidebar and changed pages.

Merging source alone does not update `.wiki.git`.

## Publication safety and restore

Before replacing live pages, the workflow:

1. validates repository-managed pages;
2. clones the live Wiki;
3. creates a full Git bundle;
4. pushes a timestamped backup branch;
5. uploads that bundle as an artifact;
6. replaces Wiki Markdown from reviewed source;
7. commits the source revision;
8. pushes the detected Wiki default branch.

The live Wiki should remain unchanged if validation, clone or backup creation fails.

Preferred restore uses the exact backup branch reported by the workflow:

```bash
git clone https://github.com/wsg138/EnthusiaStaff.wiki.git
cd EnthusiaStaff.wiki
git reset --hard origin/backup/<exact-branch-from-workflow>
git push --force-with-lease origin HEAD:<reported-default-branch>
```

Do not guess branch names or bypass a failed protected publisher by hand-editing the live Wiki.

## Final documentation review

Ask:

- Can each audience find its starting page in one step?
- Is the useful answer above the deep implementation detail?
- Does each technical subject have one clear owning page?
- Are merged behavior, limitations, automated evidence, staging and production acceptance distinguished?
- Do developer pages answer where policy, platform glue, persistence, tests and remaining runtime evidence live?
- Does the review guide point reviewers to the right risks without duplicating the source map?
- Are recovery instructions conservative and free of blind retry/raw-storage advice?
- Is all sensitive information excluded?
- Did `python scripts/wiki/validate_wiki.py` pass on the final exact PR head?
- Were live pages verified after publication?