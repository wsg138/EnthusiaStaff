# Package planning setup handoff

- Date: 2026-08-05
- Work item: canonical package registry and aggregate/standalone component synchronization model
- Starting `main`: `af9aa3d0d54afc84de7c90cb3fdc5ce3cdf9118a`
- Branch: `package/es-setup-workspace-orchestration`
- Pull request: `#67 — Establish work-package orchestration and component synchronization`
- Current status: `REVIEW`; intended post-merge state is `PACKAGE-PLANNING READY`

## Completed work

- Reconciled live GitHub before branch creation: no open PRs, only `main`, V16 highest, issue #43 open/deferred.
- Verified site, Currency, Market, and Commend repositories and heads; RoseChat remains unresolved rather than invented.
- Defined 21 packages, an acyclic execution graph, the full status machine including `SYNC_PENDING`, assigned-worker resume/checkpoint protocol, validation policy, templates, all 99 audit routes, aggregate component metadata, and deterministic aggregate-versus-standalone comparison tooling.
- Updated AGENTS, universal assigned-package prompt, workspace state/manifest, handoff routing, and Wiki status/development routing.
- Opened draft PR #67 from the one temporary setup branch.
- Removed the abandoned long-lived component-branch design before creating any branch. No permanent component branch or isolated PR was created.

## Incomplete work

- Harshly review the complete PR #67 diff and fix only confirmed defects.
- Run exact-head documentation/tooling validation and inspect every check/review bot/thread.
- Record exact final-head evidence in PR text/comment, mark ready, merge normally, verify containment, and delete the temporary setup branch when tooling permits.

## Exact next action

Freeze tracked content at the commit containing this handoff, harshly review PR #67, validate the exact unchanged head, resolve every valid finding, merge normally, and stop. Do not start `ES-P01` in this channel.

## Systems not to disturb

Product Java/Kotlin/SQL, migrations V1–V16, runtime configuration, workflows, production systems, private data, LiteBans authority, and issue #43 acceptance.
