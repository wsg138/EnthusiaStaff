# Aggregate external component copies

`components/` is the tracked aggregate view of external Enthusia components inside `wsg138/EnthusiaStaff:main`. It is not a submodule collection and must never contain nested `.git` directories.

Each directory begins with `COMPONENT-METADATA.md` only. Importing source is a separate assigned package action that must verify the standalone repository, exact source SHA, license/history, ignored artifacts, secrets, generated files, and reviewability.

The authoritative representations for an external component are:

1. its standalone GitHub repository; and
2. its designated aggregate directory in `wsg138/EnthusiaStaff:main`.

There is no third representation, no permanent component branch, and no isolated-component PR. External product changes use one temporary branch/PR in the standalone repository and one temporary branch/PR to the aggregate workspace. Both PRs use the same package ID and cross-reference one another. The package remains `SYNC_PENDING` until both merge and deterministic parity passes.

See `ai-agents/work-packages/COMPONENT-REGISTRY.md`, `BRANCH-AND-MIRROR-POLICY.md`, and `tools/component-sync/`.
