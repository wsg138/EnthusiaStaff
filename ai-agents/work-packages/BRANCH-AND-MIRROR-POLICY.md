# Temporary branch and aggregate synchronization policy

The filename is retained for stable links. This policy does **not** create permanent mirror branches.

## Prohibited design

Do not create or use `component/enthusia-staff`, `component/enthusia-site`, `component/enthusia-rosechat`, `component/enthusia-currency`, `component/enthusia-market`, `component/enthusia-commend`, generated split/subtree branches, isolated-component PRs, component-only branch allowlists, or branch-mirror synchronization jobs.

## Temporary package branches

Use `package/<package-id-lowercase>-<short-name>`. Branch from the exact legitimate default-branch head recorded at package start. Never push directly to a default branch, rebase a shared package branch, force-push, squash the final merge, or enable auto-merge.

Temporary package branches are deleted after their PRs merge and after head containment and absence of unique work are verified. Never delete a branch with unmerged work or an open dependent PR.

## Internal EnthusiaStaff package: one PR

An internal package normally uses:

1. one temporary package branch in `wsg138/EnthusiaStaff`; and
2. one PR targeting `wsg138/EnthusiaStaff:main`.

Completion requires that PR to merge normally, exact-head evidence to be recorded, the temporary branch to be cleaned up, and all package acceptance criteria to pass.

## External component package: two PRs

An external package normally uses:

1. one temporary same-ID branch and PR in the standalone component repository; and
2. one temporary same-ID branch and PR in `wsg138/EnthusiaStaff`, targeting `main` and updating the designated `components/<component>/` copy plus directly necessary EnthusiaStaff integration, tests, and package-state documentation.

The two PRs must use the same package ID, cross-reference each other, record exact base/source SHAs, follow each repository's own `AGENTS` and CI rules, and implement the same component behavior. There is no third or isolated PR.

## Synchronization and divergence

After both PRs merge, compare the aggregate component directory with a checkout of the standalone repository using `tools/component-sync/component_sync.py`. The comparison excludes only `.git` and the aggregate-only `COMPONENT-METADATA.md`; it refuses parity when generated, private, secret, database, log, runtime, cache, build, or package artifacts are detected.

Record both merge commits, resulting default-branch heads, file manifests, and content hashes in the package handoff and component metadata. A package cannot become `COMPLETE` until parity is true.

When one PR merges first, set `SYNC_PENDING`. If both sides diverge, do not choose a winner, overwrite silently, force-push, rewrite history, or automate a merge. Reconcile through reviewed follow-up commits on the same package branches/PRs when possible; otherwise record a precise `BLOCKED` state.

## Setup boundary

The setup package creates metadata-only component directories. It does not import source, create component branches, initialize permanent mirrors, or start an implementation package.
