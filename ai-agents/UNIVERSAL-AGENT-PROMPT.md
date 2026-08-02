# Universal EnthusiaStaff agent prompt

Copy the text below into a new ChatGPT or coding-agent channel.

---

Work on `wsg138/EnthusiaStaff` as the next repository agent.

Your job is to determine the current legitimate next step from the repository itself, complete exactly one logical work item, review it harshly, validate the exact final revision, merge it when every gate passes, leave a durable handoff, clean up the merged branch when safe, and stop.

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
- the live highest Flyway migration;
- recent merged work that may make the state file stale.

Reconcile any discrepancy between live state and the recorded state. Live GitHub and code win.

## Decide what to do

Follow the resume-first rules in `ai-agents/AGENTS.md`.

In practical terms:

1. If a relevant unfinished PR exists, resume and finish it instead of opening another PR.
2. If the active work is blocked, resolve that blocker when it is within repository scope.
3. If no unfinished work exists, select the recorded next work item from `WORKSPACE-STATE.md` after verifying its prerequisites and actual gaps.
4. If that item is already complete or invalid, select the next highest-priority incomplete prerequisite-ready item from the goals, blueprint, requirements matrix, and current code.

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
2. synchronize it with `main` using the repository's normal merge-commit workflow when required;
3. inspect the complete diff and current PR description;
4. inspect unresolved review threads and failed or incomplete checks;
5. identify what is genuinely unfinished;
6. complete the existing scope without broadening it unnecessarily;
7. do not replace the PR with a new branch unless the current branch is irrecoverably invalid and that conclusion is documented.

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
- all human, CodeRabbit, Codacy, and CI findings.

Classify findings as merge blockers, confirmed defects, optional cleanup, or unrelated future work.

Fix every merge blocker and confirmed defect. Do not add endless speculative cleanup merely to delay completion.

## Exact-head validation

Run or verify the repository's full applicable validation on the final reviewed feature head.

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

Never claim a check passed without direct evidence. Label skipped, superseded, merge-ref-only, runtime-equivalent, and different-revision results accurately.

## Merge and cleanup

Merge only when every gate in `ai-agents/AGENTS.md` passes.

Before merging:

1. update the PR description with the final scope and exact-head evidence;
2. update `ai-agents/WORKSPACE-STATE.md` for the intended post-merge state;
3. add a timestamped handoff under `ai-agents/reports/agent-handoffs/`;
4. update `ai-agents/reports/agent-handoffs/latest.md` to point to it;
5. ensure those records are included in the reviewed PR;
6. mark the PR ready only after it is actually ready.

Merge using a normal merge commit.

Do not rebase, squash, force-push, push directly to `main`, or enable auto-merge.

After merging:

1. verify the exact merge commit and resulting `main` SHA;
2. verify the feature head is contained in `main`;
3. delete the merged remote branch when it contains no unmerged work and available tooling permits deletion;
4. remove agent-created local branches, worktrees, and temporary files where applicable;
5. add the exact merge result to the PR description or a final PR comment;
6. report any branch-cleanup limitation honestly.

Do not create a direct follow-up commit to `main` merely to insert the merge SHA into the handoff. The next agent must reconcile the committed handoff with live GitHub.

## Blocked work

When a genuine blocker prevents completion:

- do not invent credentials, data, provider APIs, requirements, or evidence;
- do not begin unrelated work;
- record what was verified;
- record the exact blocker and required input;
- update the state and handoff on the active branch when appropriate;
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
- static-analysis results;
- Pi result or an honest statement that it did not run.

### Merge or blocker

For merged work:

- PR number and URL;
- merge commit;
- resulting `main`;
- normal merge confirmation;
- branch cleanup result.

For blocked work:

- exact blocker;
- required input;
- safe state left behind.

### Boundaries

Confirm no unauthorized deployment, production access, authority change, LiteBans removal, migration-history rewrite, direct `main` push, rebase, squash, or force-push occurred.

### Next work

State the next recommended item briefly, but do not create its branch or begin it.

Stop after this report.

---