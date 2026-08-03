# Universal EnthusiaStaff agent prompt

Copy the text below into a new ChatGPT or coding-agent channel.

---

Work on `wsg138/EnthusiaStaff` as the next repository agent.

Your job is to determine the current legitimate next step from the repository itself, complete exactly one logical work item, review it harshly, validate the exact final revision, merge it when every gate passes, leave one canonical durable handoff, clean up the merged branch when safe, and stop.

Do not assume the previous chat's state is current.

## Required first actions

Use the GitHub connector and repository tools to read, in this order:

1. `ai-agents/AGENTS.md`
2. `ai-agents/WORKSPACE-STATE.md`
3. `ai-agents/reports/agent-handoffs/latest.md`
4. `ENTHUSIASTAFF-GOALS.md`
5. `WORKSPACE-MANIFEST.md`
6. relevant portions of `docs/wiki/pages/Development-Blueprint.md`
7. relevant portions of `reports/REQUIREMENTS-MATRIX.md`

Then inspect live GitHub state:

- current `main` SHA;
- open and draft pull requests;
- active branches;
- PR head/base and ahead/behind relations;
- unresolved review threads;
- current CI and exact-head evidence;
- pending, in-progress, cancelled and superseded Coverage/Pi runs;
- the live highest Flyway migration;
- recent merged work that may make the state file stale.

Reconcile every discrepancy. Live GitHub and code win over stale recorded state.

## Decide what to do

Follow the resume-first rules in `ai-agents/AGENTS.md`.

In practical terms:

1. If a relevant unfinished PR exists, resume and finish it instead of opening another PR.
2. If active work is blocked, resolve that blocker when it is within repository scope.
3. If no unfinished work exists, select the recorded next work item from `WORKSPACE-STATE.md` after verifying its prerequisites and actual gaps.
4. When prerequisites are comparable, follow the current owner priority order and selection guardrails recorded in `WORKSPACE-STATE.md` unless the owner gives a newer direct instruction in the current conversation.
5. If the recorded item is already complete or invalid, select the next highest-priority incomplete prerequisite-ready item from the goals, blueprint, requirements matrix, current code and current owner instructions.

Do not start a duplicate or competing feature.

Complete exactly one logical work item in this channel.

## New work workflow

When starting a new implementation item:

1. branch from the latest legitimate `main`;
2. create a focused descriptive branch;
3. open a draft PR early;
4. record the baseline, confirmed gaps, and implementation plan in the PR description;
5. implement a complete usable feature or fix, not placeholders;
6. keep the PR focused on that work item;
7. update tests, migrations, configuration, permissions, commands, documentation, and operational behavior required by the feature.

Use Java 21 and the repository's existing Paper, Velocity, MariaDB, authorization, audit, reload, player identity, and provider architecture.

Do not create parallel systems when a current service or repository should be extended.

## Existing PR workflow

When resuming a PR:

1. verify its exact current head and base;
2. determine whether another worker or process is still changing the branch;
3. inspect all pending, in-progress, failed, cancelled, superseded and successful checks for the current head;
4. synchronize it with `main` using the repository's normal merge-commit workflow when required;
5. inspect the complete diff and current PR description;
6. inspect unresolved review threads and incomplete checks;
7. identify what is genuinely unfinished;
8. complete the existing scope without broadening it unnecessarily;
9. do not replace the PR with a new branch unless the current branch is irrecoverably invalid and that conclusion is documented.

A cancelled or superseded run is not failure evidence, but it is not validation evidence either.

## Required harsh review

After implementation appears complete, enter a separate review phase.

Review the entire final PR diff for at least:

- scope completeness;
- architecture consistency;
- duplicate systems;
- startup, reload, shutdown, and restart behavior;
- Paper main-thread blocking;
- Velocity event behavior;
- database transactions and indexes;
- concurrency across multiple runtimes;
- row locks, revisions, idempotency, and retries;
- rollback and partial failures;
- migration safety;
- bounded queries, queues, caches, and memory;
- permissions and hierarchy at service boundaries;
- GUI stale-state and inventory-event safety where relevant;
- Java and Bedrock command usability;
- configuration validation and invalid reload behavior;
- sensitive-data exposure;
- tests that do not prove their claims;
- documentation accuracy;
- all human, CodeRabbit, Codacy, Coverage, Pi and CI findings.

Classify findings as merge blockers, confirmed defects, optional cleanup, or unrelated future work.

Fix every merge blocker and confirmed defect. Do not add endless speculative cleanup merely to delay completion.

After the final workflow-documentation batch or any other tracked change, inspect the complete diff again and fix only real defects.

## Freeze tracked content before final validation

Coverage and Pi may take roughly ten minutes, and a newer commit may cancel or supersede an earlier run. Batch all remaining tracked changes before final validation.

Before exact-head validation:

1. finish every code, test, migration, documentation, state, routing and handoff change;
2. finish the separate harsh review and every confirmed fix;
3. update `ai-agents/WORKSPACE-STATE.md` to an intended post-merge state such as `IDLE — PR #N requires live merge verification`;
4. maintain one canonical timestamped handoff under `ai-agents/reports/agent-handoffs/` and edit it during implementation and review;
5. update `ai-agents/reports/agent-handoffs/latest.md` to point to that one report and the PR;
6. make the handoff point readers to the PR description or final PR comment for exact-head and merge evidence;
7. make the remaining tracked changes in one batch when practical;
8. stop changing tracked files unless review or validation finds a real defect.

Do not create repeated `final`, `review-final`, or `validation-final` handoff variants merely because the head moved. Create a superseding file only when an earlier handoff was genuinely frozen and an immutable correction is required.

A committed file cannot safely contain its own PR's final SHA, final CI/Pi run IDs, artifact IDs and merge commit without changing the SHA again. Do not create that circular update loop.

Once final exact-head Coverage/Pi validation starts, make no more commits unless it exposes a real defect. If another commit is required, repeat the complete-diff harsh review and exact-head validation for the new final head.

## Exact-head validation

Run or verify the repository's full applicable validation on the final reviewed feature head after tracked content is frozen.

Normally include:

- clean Java 21 Gradle build;
- unit tests;
- Paper tests;
- Velocity tests;
- persistence and protocol tests;
- MariaDB/Testcontainers integration tests;
- clean-install and upgrade migration tests;
- migration checksum tests;
- configured warnings-as-errors;
- static analysis;
- aggregate coverage and configured upload;
- Paper and Velocity runtime JAR builds;
- JAR/ZIP integrity;
- provider API leak checks;
- wiki/document validation;
- current review-bot status;
- zero unresolved review threads;
- safe exact-head Pi boot/restart testing when the existing workflow supports it.

Inspect pending and in-progress workflows before saying Pi did not run. Pi may be queued or may start after Coverage completes.

Cancelled and superseded Coverage/Pi runs are neither failures nor evidence. Label skipped, different-revision, merge-ref-only and runtime-equivalent results accurately.

For implementation PRs, when an exact-head Pi workflow is configured and triggered, wait for its terminal result before merge. Do not merge while it is pending or in progress.

Documentation-only work may omit Pi when the workflow is not applicable or not configured for that change. Record that distinction and why Pi was not required.

Never claim a check passed without direct evidence.

Record the exact final feature SHA, workflow run and job IDs, Java version, build/test/migration results, coverage, JAR hashes, artifact identity, provider-leak result, static-analysis result, review-thread count and Pi result in the PR description or a final pre-merge PR comment. Updating PR text must not change the feature SHA.

## Merge and cleanup

Merge only when every gate in `ai-agents/AGENTS.md` passes.

Before merging:

1. ensure the tracked state and canonical handoff files are complete;
2. ensure tracked content was frozen before the exact-head checks;
3. ensure all configured and triggered implementation-PR Pi work reached a successful terminal result;
4. put final scope and exact-head evidence in the PR description or final pre-merge comment;
5. resolve all valid review findings;
6. mark the PR ready only after it is actually ready.

Merge using a normal merge commit.

Do not rebase, squash, force-push, push directly to `main`, enable auto-merge, or merge a draft PR.

After merging:

1. verify the exact merge commit and resulting `main` SHA;
2. verify the feature head is contained in `main`;
3. verify whether GitHub automatically deleted the merged branch;
4. delete the merged remote branch when it still exists, contains no unmerged work and available tooling permits deletion;
5. remove agent-created local branches, worktrees, and temporary files where applicable;
6. add the exact merge result to the PR description or a final PR comment;
7. report any branch-cleanup limitation honestly.

Do not create a direct follow-up commit to `main` merely to insert the merge SHA into the handoff. The next agent must reconcile the committed handoff with live GitHub.

## Blocked work

Issue #43 is specifically the LiteBans production-cutover acceptance issue and remains open. It is not the general bug-report or blocker queue.

When a genuine blocker prevents completion:

- do not invent credentials, data, provider APIs, requirements, or evidence;
- do not begin unrelated work;
- record what was verified;
- record the exact blocker and required input;
- normally create or update a focused GitHub blocker issue for an unavailable external API or provider dependency and record it in the normal handoff;
- do not put unrelated blockers in issue #43;
- do not open a standalone documentation PR solely to record a blocker unless repository routing would otherwise be materially incorrect or unsafe;
- update the state and canonical handoff on the active branch when appropriate;
- keep an incomplete PR draft;
- stop with a precise blocker report.

## Permanent boundaries

Unless the current user explicitly gives a separate production instruction, do not:

- deploy a JAR;
- access production databases or player data;
- use production credentials, Discord routes, or webhooks;
- activate EnthusiaStaff punishment authority;
- disable or remove LiteBans;
- run the production cutover or issue #43 acceptance window;
- use Flyway repair;
- rewrite migration history;
- edit an existing migration;
- delete persistent records to conceal failure;
- change the staging-controls repository unless the selected work item explicitly requires it.

LiteBans remains authoritative.

## Final response

After the work item is merged or accurately blocked, return:

### Selected work

- why this was the correct next item;
- starting `main`;
- resumed or created PR and branch.

### Implementation

- completed behavior;
- important architecture, persistence, migration, commands, permissions, configuration, identity, and operational changes.

### Harsh review

- defects found;
- defects fixed;
- optional or deferred findings;
- unresolved-review count.

### Validation

- final feature head;
- workflow run and job IDs;
- Java version;
- build and test results;
- coverage;
- migration results;
- JAR hashes and artifact identity where relevant;
- provider-leak and static-analysis results;
- terminal Pi result, or a verified and accurately scoped statement that Pi was not applicable.

### Merge or blocker

For merged work:

- PR number and URL;
- merge commit;
- resulting `main`;
- normal merge confirmation;
- feature-head containment;
- automatic or manual branch cleanup result.

For blocked work:

- exact blocker;
- focused issue or required input;
- safe state left behind.

### Boundaries

Confirm no unauthorized deployment, production access, authority change, LiteBans removal, migration-history rewrite, direct `main` push, rebase, squash, force-push, or auto-merge occurred.

### Next work

State the next owner-priority item briefly, but do not create its branch or begin it.

Stop after this report.

---
