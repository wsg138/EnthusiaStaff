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

1. Update code, configuration, and status sources first.
2. Edit the relevant page in `docs/wiki/pages`.
3. Preserve stable filenames when possible.
4. Update `_Sidebar.md` if a page is added or renamed.
5. Update `Implementation-Status.md` when availability changes.
6. Run `python scripts/wiki/validate_wiki.py`.
7. Open a pull request.
8. Review technical accuracy, staff clarity, privacy, and links.
9. Merge the approved pull request.
10. Manually run **Publish Wiki** from `main`.

## One-time GitHub setup

Complete these steps after the repository-managed Wiki pull request is merged.

### 1. Allow the workflow to write

Open the repository and go to:

```text
Settings -> Actions -> General -> Workflow permissions
```

Select **Read and write permissions** and save. Repository or organization policy
may prevent this setting from being changed; in that case an owner must allow the
required write permission.

### 2. Create the protected publication environment

Go to:

```text
Settings -> Environments -> New environment
```

Create an environment named exactly:

```text
wiki-production
```

Recommended protection:

- allow deployments only from `main`;
- add a required reviewer when another trusted maintainer is available;
- prevent self-review if publication should always require a second person.

The publication job references this environment and cannot start until its
protection rules pass.

### 3. Configure a Wiki token only if needed

The workflow first tries the job's built-in `GITHUB_TOKEN`. Run one publication
with no custom secret. If cloning succeeds but creating the backup branch or
pushing the Wiki returns `403`, add a secret named exactly:

```text
WIKI_PUBLISH_TOKEN
```

Store it preferably as an environment secret inside `wiki-production`:

```text
Settings -> Environments -> wiki-production -> Environment secrets
```

Because this repository is public, a short-lived personal access token (classic)
with only the `public_repo` scope is the reliable fallback for HTTPS Git pushes.
The token owner must retain write access to `wsg138/EnthusiaStaff`. Do not place
the token in a workflow file, commit, issue, Wiki page, or log. Set an expiration
and rotate or delete it when it is no longer needed.

A fine-grained token may also work when restricted to `EnthusiaStaff` with
repository **Contents: Read and write**, but use the classic `public_repo`
fallback if GitHub does not authorize the associated `.wiki.git` repository.

### 4. Keep the GitHub Wiki enabled

The repository Wiki must exist and remain enabled. The publication target is:

```text
https://github.com/wsg138/EnthusiaStaff.wiki.git
```

The visible page is:

```text
https://github.com/wsg138/EnthusiaStaff/wiki
```

Do not manually delete the Wiki repository before publishing.

## Publishing the new pages

After the source pull request is merged:

1. Open the repository's **Actions** tab.
2. Select **Publish Wiki**.
3. Choose **Run workflow**.
4. Select branch `main`.
5. Enter `PUBLISH` exactly in the confirmation field.
6. Start the run.
7. Approve the `wiki-production` deployment if the environment requires review.
8. Confirm that validation, cloning, backup, artifact upload, commit, and push all
   complete successfully.
9. Open the visible Wiki and confirm the new Home page and sidebar appear.

The old pages remain visible until this workflow completes. Merging the source
pull request alone does not change the separate GitHub Wiki repository.

## Publishing safety

Publishing is manual. The workflow:

1. Validates source pages.
2. Clones the live Wiki.
3. Creates a full Git bundle.
4. Pushes a timestamped backup branch.
5. Uploads the pre-publish bundle as a workflow artifact.
6. Replaces only root Markdown pages.
7. Commits the exact source commit to Wiki history.
8. Pushes the detected Wiki default branch.

The live Wiki is not modified if validation, cloning, bundle creation, or backup
branch creation fails.

## Confirming a successful publication

The workflow summary records:

- the main-repository source commit;
- the timestamped Wiki backup branch;
- the backup artifact name;
- the Wiki branch that received the publication.

The visible Wiki should show:

- the new repository-managed `Home.md`;
- the staff handbook section;
- the operator/reference pages;
- the developer and reviewer section, including [[Developer Code Guide]];
- the new `_Sidebar.md` navigation.

If GitHub still shows an old page, hard-refresh the browser and verify that the
workflow pushed the actual Wiki default branch reported in the summary.

## Restore

Preferred restore from the automatically created backup branch:

```bash
git clone https://github.com/wsg138/EnthusiaStaff.wiki.git
cd EnthusiaStaff.wiki
git reset --hard origin/backup/repo-managed-<timestamp>
git push --force-with-lease origin HEAD:master
```

Use the exact branch and default-branch names shown in the publication workflow
summary. Do not assume the live Wiki branch is always `master` if GitHub reports a
different branch.

The original pre-migration state is also preserved at
`ea4f929710d3281aac4a8087da1e947973c2d795` and under
`docs/wiki/legacy/ea4f929/`.

## Review checklist

- Staff instructions match actual command usage.
- Rank limits match service policy.
- Planned behavior is not described as available.
- Destructive workflows include stop conditions.
- Private data is not exposed.
- External integration limitations are stated.
- Developer file paths and feature traces still match the source tree.
- Old page links still resolve.
- No real player, case, address, secret, or credential data is present.
- Requirements matrix and implementation-status page agree.
