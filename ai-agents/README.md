# AI agent workspace

All future implementation and validation work is routed through the package system:

1. read `AGENTS.md` and `WORKSPACE-STATE.md`;
2. read `work-packages/PACKAGE-REGISTRY.md` as the canonical status index;
3. accept exactly one assigned package ID;
4. read the assigned package file and latest package handoff;
5. follow the worker, temporary-branch/synchronization, and validation policies;
6. stop after the same package is complete, blocked, partial, deferred, or the requested review/audit ends.

The universal prompt is `UNIVERSAL-AGENT-PROMPT.md`. Do not select work from stale handoffs or informal priority lists. There are no permanent component branches or isolated-component PRs.
