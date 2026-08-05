# Package planning setup handoff

- Date: 2026-08-05
- Work item: canonical package registry and aggregate/standalone component synchronization model
- Starting `main`: `af9aa3d0d54afc84de7c90cb3fdc5ce3cdf9118a`
- Branch: `package/es-setup-workspace-orchestration`
- Pull request: `PENDING`
- Current status: `REVIEW` until exact-head validation and merge; intended post-merge state is `PACKAGE-PLANNING READY`

## Completed work

- Reconciled live GitHub: no open PRs, only `main`, V16 highest, issue #43 open/deferred.
- Verified site, Currency, Market, and Commend repositories and heads; RoseChat unresolved.
- Defined 21 packages, dependency graph, status machine including `SYNC_PENDING`, assigned-worker resume protocol, validation policy, templates, all 99 audit routes, aggregate component metadata, and deterministic aggregate-versus-standalone comparison tooling.
- Updated AGENTS, universal assigned-package prompt, workspace state, manifest, and Wiki routing.
- Removed the abandoned long-lived component-branch design before creating any branch. No permanent component branch or isolated PR was created.

## Incomplete work

- Create and review the setup PR.
- Run exact-head documentation/tooling validation and inspect review bots/threads.
- Merge normally, verify containment, and delete the temporary setup branch when tooling permits.

## Exact next action

Open the focused setup PR, replace `PENDING` references with the PR number in the same setup branch, validate the final unchanged head, resolve every valid review finding, merge normally, and stop. Do not start `ES-P01` in this channel.

## Systems not to disturb

Product Java/Kotlin/SQL, migrations V1–V16, runtime configuration, workflows, production systems, private data, LiteBans authority, and issue #43 acceptance.
