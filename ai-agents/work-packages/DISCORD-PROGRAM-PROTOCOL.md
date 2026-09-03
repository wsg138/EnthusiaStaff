# Discord program worker protocol

This protocol is an owner-authorized specialization of the normal sequential package rules. It exists so repeated workers can finish the Discord moderation project without being diverted into unrelated package families.

## Startup

Use GitHub first. Read `ai-agents/AGENTS.md`, `WORKSPACE-STATE.md`, the global `PACKAGE-REGISTRY.md`, `WORKER-PROTOCOL.md`, `EXECUTION-ORDER.md`, `BRANCH-AND-MIRROR-POLICY.md`, `VALIDATION-POLICY.md`, `STAGING-TEST-OPERATING-GUIDE.md`, then this protocol, `DISCORD-PROGRAM-REGISTRY.md`, the approved Discord product/implementation docs, the selected package file, and its latest handoff. Inspect current `main`, recent commits, all relevant open PRs/branches, checks, review threads, highest Flyway migration, and overlapping workers before editing.

## Selection and ownership

When invoked by `DISCORD-PROGRAM-WORKER-PROMPT.md`, the current owner instruction assigns the worker to the Discord program lane. Select exactly one `ES-Dxx` package from `DISCORD-PROGRAM-REGISTRY.md`; do not select unrelated global packages. Still reconcile unrelated work to prevent collisions.

If a selected package is new, record it in the canonical package/workspace state as required by the normal protocol before substantive implementation. Preserve any existing branch/PR for actionable continuation instead of replacing it.

## Execution standard

Do not stop at planning. Inspect source and tests, implement the complete package, add/repair tests, perform harsh full-diff review, resolve valid review/CI/static findings, wait for and inspect ordinary exact-head checks, merge normally when every package gate is satisfied, verify containment/cleanup, publish terminal state, and then stop. If a real blocker cannot be resolved with available tools, finish every safe action first and publish the exact blocker/unblock condition.

Do not ask the owner for information GitHub/source/docs can resolve. Do not weaken a gate to get a pass. Never call missing/cancelled/stale/superseded evidence successful.

## Collision policy

The owner may independently work on website or competition features. Before each package claim, compare live changed paths and schemas. Never overwrite, force-push, rebase, or silently absorb their work. If overlap is material, park that package and select another dependency-complete Discord package if one exists; otherwise publish the blocker and stop.

`ES-D12` is especially sensitive to active website work. Competition work is out of scope for every Discord package unless an approved contract explicitly requires a shared primitive.

## Production boundary

The program authorization covers source implementation and safe CI/staging validation. It does not by itself authorize production Discord changes, production data access/migration, bot-token handling in Git, disabling native protections, LiteBans authority changes, issue #43, or final production cutover. `ES-D15` must distinguish implementation/staging acceptance from any separately authorized production action.
