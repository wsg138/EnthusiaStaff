# EnthusiaStaff AI agent operating rules

These rules apply to every AI-assisted work session that uses this repository.

They govern how an agent selects work, resumes pull requests, reviews changes, validates the exact final revision, merges, cleans up, and leaves a durable handoff.

## 1. Core operating principle

Complete exactly one logical work item per session.

A logical work item is one of:

- finishing an existing pull request;
- implementing one focused feature or fix through merge;
- resolving one clearly bounded blocker;
- performing one explicitly requested repository review without implementation.

Do not begin a second feature after the first work item is merged. Record the next recommended work and stop.

## 2. Required reading order

At the beginning of every session, read:

1. `ai-agents/AGENTS.md`
2. `ai-agents/WORKSPACE-STATE.md`
3. `ai-agents/reports/agent-handoffs/latest.md`
4. the current pull request description and review threads, when a PR exists;
5. relevant portions of `ENTHUSIASTAFF-GOALS.md`;
6. `WORKSPACE-MANIFEST.md`;
7. relevant portions of `docs/wiki/pages/Development-Blueprint.md`;
8. relevant portions of `reports/REQUIREMENTS-MATRIX.md`.

Then inspect live GitHub and repository state.

Never rely on the files alone for:

- the current `main` SHA;
- whether a PR is open, draft, ready, merged, or closed;
- the current PR head;
- ahead/behind status;
- unresolved review threads;
- exact-head CI status;
- branch existence;
- the latest Flyway migration.

Live repository state overrides stale recorded state. Reconcile discrepancies explicitly.

## 3. Source-of-truth boundaries

- Live code and GitHub state determine what is actually implemented.
- `ENTHUSIASTAFF-GOALS.md` defines the intended finished product.
- This file defines the agent workflow and safety process.
- `WORKSPACE-STATE.md` identifies the expected current step but may be stale.
- Handoff reports provide context and route the next agent to the relevant PR evidence. They do not override code, tests, requirements, or current GitHub state.

Do not claim a feature exists because it appears in a plan, PR description, handoff, matrix, or class name. Verify behavior in code and tests.

## 4. Resume-first work selection

Before creating a new branch, inspect:

- open pull requests;
- draft pull requests;
- active remote branches;
- unresolved review threads;
- failed, pending, skipped, or superseded checks;
- `WORKSPACE-STATE.md` active-work fields.

Use this order:

1. Resume an explicitly active PR recorded in `WORKSPACE-STATE.md` when it still exists.
2. Otherwise finish the oldest relevant unfinished EnthusiaStaff PR that is part of the current roadmap.
3. Otherwise address a recorded blocker that prevents the current work item.
4. Otherwise select the next incomplete work item from `WORKSPACE-STATE.md`.
5. If the state file has no valid next item, use the goals, development blueprint, requirements matrix, and current implementation to choose the highest-priority prerequisite-complete feature.

Do not open a competing PR for work already active elsewhere.

Do not start unrelated work merely because the current item is difficult.

## 5. Work-state model

Classify the current work as one of:

- `IDLE`
- `PLANNING`
- `IMPLEMENTING`
- `REVIEWING`
- `FIXING_REVIEW`
- `VALIDATING`
- `READY_TO_MERGE`
- `MERGING`
- `MERGED`
- `BLOCKED`

Update `WORKSPACE-STATE.md` when the state materially changes and ensure the final version included in the PR describes the intended post-merge state.

## 6. Git and pull-request policy

- Branch from the latest legitimate `main`.
- Verify the exact starting SHA.
- Use a focused descriptive branch.
- Open a draft PR early for new implementation work.
- Keep one logical feature or fix per PR.
- Use intentional commits.
- Never push directly to `main`.
- Never rebase a shared feature branch.
- Never squash the final PR.
- Never force-push.
- Merge using a normal merge commit.
- Do not enable automatic merging.
- Do not merge a draft PR.
- Do not merge while the branch is behind `main` unless the repository's current documented workflow explicitly permits it and the final exact revision is retested.
- Do not represent a merge-ref-only run as exact feature-head evidence unless the check is specifically defined to validate the merge result and this distinction is recorded.

The universal agent prompt is explicit authorization to manually merge the single current work item after every merge gate in this file passes. It is not authorization to weaken those gates or enable auto-merge.

## 7. Branch cleanup

After a successful merge:

1. verify the merge commit is on `main`;
2. verify the feature head is contained in the merged history;
3. verify no unmerged commits remain on the branch;
4. delete the remote feature branch when tooling and repository permissions permit;
5. delete agent-created local branches, worktrees, and temporary files when applicable;
6. record whether cleanup succeeded.

Never delete a branch that contains unmerged work.

If the available connector cannot delete the remote branch, report that limitation clearly rather than pretending cleanup occurred.

## 8. Implementation standards

Unless the current work item is documentation-only, inspect and account for:

- Java 21 compatibility;
- Paper-compatible server-thread rules;
- Velocity event and scheduler behavior;
- asynchronous database work;
- MariaDB transactions and indexes;
- multiple Paper and Velocity processes operating concurrently;
- idempotency and retry safety;
- restart and shutdown behavior;
- bounded queues, queries, caches, and memory;
- permissions and hierarchy enforcement at service boundaries;
- configuration validation and safe reload behavior;
- Java and Geyser/Floodgate usability;
- logging and sensitive-data handling;
- provider-present and provider-missing behavior where relevant;
- audit completeness;
- failure rollback and partial-operation recovery;
- operational-mode and authoritative-write fencing where moderation authority is involved.

Do not deliver only interfaces, placeholders, TODOs, schemas, or command stubs when the work item calls for a complete feature.

## 9. Flyway and persistence rules

- Existing deployed migrations are immutable.
- Determine the current highest migration live before adding another.
- Never edit an existing migration merely to satisfy a scanner or test.
- Never use Flyway repair to hide a checksum mismatch.
- Never rewrite migration history.
- Never delete persistent records to conceal a failed migration or test.
- Add a new migration for schema changes.
- Preserve migration checksum tests.
- Test both clean installation and upgrade from the previous migration where relevant.
- Add indexes for normal query paths and prove pagination is database-bounded.

`WORKSPACE-STATE.md` records the currently known migration boundary, but the agent must verify it against the repository before editing.

## 10. Production and authority boundary

Unless the user gives a separate explicit production instruction in the current conversation, do not:

- deploy a JAR;
- access production databases;
- use production credentials;
- use production player data;
- contact production Discord or webhook routes;
- alter Bloom or other production services;
- activate EnthusiaStaff punishment authority;
- disable or remove LiteBans;
- run the production cutover;
- start the issue #43 168-hour acceptance window;
- create or restore a production-derived backup;
- claim isolated CI or Pi testing is production acceptance.

Merging dormant development code is not deployment or cutover authorization.

LiteBans remains authoritative until issue #43 is separately completed and approved.

## 11. Required harsh-review phase

Implementation completion is not the same as review completion.

After the feature or fix appears complete, move into a separate `REVIEWING` phase and inspect the entire final PR diff, not only the last commit.

Review at minimum:

- scope completeness;
- architecture consistency;
- duplicated or parallel systems;
- lifecycle, startup, reload, and shutdown;
- thread safety and main-thread blocking;
- transaction boundaries;
- row locking and optimistic revision behavior;
- concurrency across multiple runtimes;
- idempotency and duplicate prevention;
- rollback after checked and unchecked failures;
- restart recovery;
- database query bounds and indexes;
- migration safety;
- permission and hierarchy enforcement;
- console/system actor behavior;
- GUI stale-state and inventory-event safety;
- Bedrock command fallbacks;
- configuration defaults and invalid reloads;
- sensitive-data exposure in chat, logs, artifacts, and exceptions;
- tests that pass without proving the intended behavior;
- documentation accuracy;
- unresolved human, CodeRabbit, Codacy, or CI findings.

Classify findings as:

- merge blocker;
- confirmed defect to fix now;
- safe optional cleanup;
- unrelated future work.

Fix merge blockers and confirmed defects. Do not create endless speculative cleanup merely to postpone completion.

## 12. Freeze tracked content before exact-head validation

A tracked file inside a PR cannot safely contain that same PR's final SHA, final CI run IDs, or merge commit without changing the revision again. Do not create a self-referential validation loop.

Before starting final exact-head validation:

1. finish all code, tests, migrations, documentation, state, and handoff-file changes;
2. update `ai-agents/WORKSPACE-STATE.md` to the intended post-merge state without embedding a not-yet-existing merge SHA;
3. add the timestamped handoff report;
4. update `ai-agents/reports/agent-handoffs/latest.md` to point to it and the PR;
5. ensure the handoff explains where exact-head and merge evidence will be recorded;
6. stop changing tracked files unless validation or review finds a real defect.

If a real defect requires another commit, repeat the harsh review and exact-head validation for the new final head.

## 13. Exact-head validation

Validation must apply to the final reviewed feature head after tracked content is frozen.

Use the repository's actual configured checks. Normally verify:

- clean Java 21 build;
- unit tests;
- Paper tests;
- Velocity tests;
- persistence tests;
- protocol tests;
- MariaDB/Testcontainers integration tests;
- clean-install migration tests;
- upgrade migration tests;
- migration checksum tests;
- compiler warnings as errors where configured;
- static analysis;
- coverage generation and upload where configured;
- Paper runtime JAR;
- Velocity runtime JAR;
- ZIP/JAR integrity;
- provider API leak checks;
- wiki and documentation validation;
- current review-bot status;
- zero unresolved review threads.

Run safe Pi boot/restart validation when the existing workflow supports the exact current head.

Never claim a check passed without direct evidence.

Record exact-head evidence in the PR description or a final pre-merge PR comment, because editing those does not change the feature SHA.

Record at minimum:

- final feature SHA;
- workflow run and job IDs;
- Java version;
- build result;
- test result;
- coverage;
- JAR hashes;
- artifact identity;
- migration result;
- static-analysis result;
- review-thread count;
- Pi result or an explicit statement that no exact-head Pi result exists.

A skipped, cancelled, superseded, different-revision, merge-ref-only, or merely runtime-equivalent run must be labeled accurately.

## 14. Merge gate

Merge only when all of the following are true:

- the PR scope is complete and usable;
- the entire final diff received the harsh review;
- every merge blocker and confirmed defect was addressed;
- the branch is synchronized appropriately with `main`;
- exact final-head validation is green;
- migrations are safe and old migrations are unchanged;
- permissions and configuration are documented;
- no unresolved review thread remains;
- the PR description or final pre-merge comment contains the exact final-head evidence;
- `WORKSPACE-STATE.md` is updated for the intended post-merge state;
- a durable handoff report and latest pointer are included;
- no known release blocker remains for this specific PR.

Production acceptance requirements block a development merge only when the PR is itself a production deployment or cutover action. They do not automatically block dormant, reviewed implementation code.

## 15. State and handoff files

Before final validation, update `ai-agents/WORKSPACE-STATE.md` with:

- the PR number;
- branch;
- intended post-merge status;
- completed-work summary;
- next recommended work item;
- current migration boundary;
- remaining blockers;
- handoff link.

Do not require the state file to contain the final feature SHA, final CI run IDs, or merge commit. Those values are live evidence and belong in the PR description or comments.

The timestamped handoff report must include:

- repository and work item;
- starting `main`;
- branch and PR;
- implemented behavior;
- files or architecture materially changed;
- migrations added and immutable migration boundary;
- commands, permissions, and configuration added;
- review findings and fixes;
- validation requirements and a link to the PR's exact-head evidence;
- merge readiness or blocker;
- production boundary;
- remaining work;
- next recommended work item.

`latest.md` must point to the report and PR and state that exact-head and merge evidence must be read live from GitHub.

Do not include secrets, raw production data, private player information, credentials, or sensitive evidence.

## 16. Post-merge verification

After merging:

- verify the actual merge commit and resulting `main` SHA;
- verify the final feature head is contained in `main`;
- put the exact merge result in the PR description or a final PR comment;
- include the exact result in the final user response;
- perform safe branch cleanup;
- do not make a direct follow-up commit to `main` merely to insert the merge SHA into the handoff;
- the next agent must reconcile the committed state and handoff with live GitHub before acting.

This keeps the handoff in the reviewed PR while preserving exact live evidence without a circular commit sequence.

## 17. Blocked work

When work cannot proceed because of a genuine blocker:

- do not invent credentials, data, requirements, source files, provider APIs, or test evidence;
- do not open unrelated work to stay busy;
- record the precise blocker;
- record what was verified;
- record the exact human or external input required;
- update `WORKSPACE-STATE.md` to `BLOCKED` on the active branch when appropriate;
- add a handoff report;
- leave the PR draft when the work is not mergeable;
- stop.

## 18. Safeguard against self-approval

An agent may improve these workflow documents, but it may not weaken them merely to make its current PR mergeable.

Changes to any of the following require explicit justification in the PR and separate review:

- merge gates;
- exact-head evidence requirements;
- migration immutability;
- production boundaries;
- authority activation requirements;
- review requirements;
- branch policy;
- handoff requirements.

Editing a checklist does not prove the underlying requirement is satisfied.

## 19. Stop condition

After the single work item is merged or accurately recorded as blocked:

- verify and report the final state;
- recommend the next logical item briefly;
- do not create the next branch;
- do not begin implementation of the next feature;
- stop.