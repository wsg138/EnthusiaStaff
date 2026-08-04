# EnthusiaStaff workspace state

Last updated: 2026-08-04

This is a routing record, not a substitute for live GitHub reconciliation.

## Repository

| Field | Recorded value |
| --- | --- |
| Repository | `wsg138/EnthusiaStaff` |
| Default branch | `main` |
| Current legitimate `main` at PR #60 start | `a5cf73568310ee1d12bd961ed192945b1859884a` |
| Plugin version | `0.1.0-SNAPSHOT` |
| Java/runtime | Java 21; Paper-compatible backends, Velocity, MariaDB |

## Current work

| Field | Value |
| --- | --- |
| State | `ACTIVE — PR #60 exact-head validation and review pending` |
| Pull request | `#60 — Fence freeze recovery across reconnects and manual changes` |
| Feature branch | `fix/freeze-recovery-session-fencing` |
| Starting main | `a5cf73568310ee1d12bd961ed192945b1859884a` |
| Work item | Prevent stale asynchronous freeze recovery and delayed freeze/release side effects from crossing quit, reconnect, newer verification, manual freeze, or manual release boundaries |
| Current handoff | `ai-agents/reports/agent-handoffs/2026-08-04-freeze-recovery-session-fencing.md` |
| Migration boundary | V16 is highest; PR #60 adds no migration; V1–V16 remain immutable |
| Production authority | LiteBans remains authoritative; no deployment or cutover authority is granted |
| External blocker | RoseChat private-message evidence remains separately blocked pending the supported provider contract; never route that blocker through issue #43 |
| Intended post-merge status | Merge PR #60 normally only after all gates pass; verify resulting `main`, feature-head containment, and branch cleanup; do not deploy or access production |

## Live start-state reconciliation

- PR #59 merged normally as `a5cf73568310ee1d12bd961ed192945b1859884a`.
- Its feature head is contained in `main`, its branch was removed, and no pull request or non-`main` branch was active before PR #60.
- PR #60 began from exact `main` `a5cf73568310ee1d12bd961ed192945b1859884a`.
- V16 remains the live highest migration; PR #60 changes no schema or migration bytes.
- Owner priority remains staff mode, vanish, and freeze before report notifications and escalation policy.

## Confirmed defect

`FreezeManager` kept pending durable verification and confirmed frozen state in separate concurrent sets. An old durable lookup could finish after quit/reconnect, a newer verification, `/freeze`, or `/unfreeze` and overwrite the newer state. Delayed freeze/recovery or release messages could also run after the state changed again.

## Implemented behavior

PR #60 now:

- uses one per-player concurrent runtime state machine for pending verification, confirmed freeze, and released state;
- assigns a monotonic generation to every verification, manual freeze, and manual release;
- applies durable lookup results atomically only when their verification generation is still current;
- ignores stale active/inactive results after quit, reconnect, newer verification, manual freeze, or manual release;
- keeps a failed or storage-unavailable current verification fail-closed;
- exposes pending verification as restricted to RoseChat so provider-handled public/private chat remains staff-only;
- generation-fences delayed inventory closure, freeze messages, release messages, and staff alerts;
- retires all runtime state on quit;
- persists offline timeout only for a confirmed frozen session;
- preserves existing restrictions, commands, permissions, persistence schema, and offline expiry.

## Focused tests

`FreezeRuntimeStateTest` covers current active/inactive results, reconnect fencing, manual release/apply races, delayed frozen callback fencing, re-freeze suppression of delayed release notifications, pending and confirmed quit retirement, and fail-closed unresolved verification, including the provider-consumed restricted state.

## Harsh-review corrections already applied

1. Replaced two separately mutated sets with one atomic per-player state machine.
2. Prevented stale durable active/inactive results from applying across newer lifecycle state.
3. Retained a generation for confirmed frozen states so delayed callbacks can distinguish old recovery from a later freeze.
4. Guarded delayed entity and global scheduler freeze/recovery side effects at execution time.
5. Kept storage-unavailable verification and the RoseChat moderation bridge fail-closed while verification is pending.
6. Added generated released state so a later freeze invalidates a delayed release message.
7. Corrected the initial exact-head Java method-signature collision before restarting validation.

## Exact-head completion gate

Before merge, require one unchanged head synchronized with current `main` and direct terminal evidence for:

- Java 21 build and unit/integration tests;
- migration checksum and immutability checks with V1–V16 unchanged;
- exactly one valid Paper and one valid Velocity runtime JAR;
- JAR integrity, provider-class leak inspection, and SHA-256 identities;
- aggregate and diff coverage;
- configured Codacy/static analysis;
- Wiki/documentation validation when applicable;
- CodeRabbit, Codacy, and human review with zero valid unresolved threads;
- exact-head public Pi wrapper and correlated staging run when applicable, or direct Actions quota/platform evidence when it cannot execute;
- one consolidated exact-head evidence comment.

Cancelled, superseded, skipped, stale-head, different-revision, and merge-ref-only results are not acceptable evidence.

## Production boundary

PR #60 is dormant development work only. It does not authorize deployment, production data or credential access, production Discord use, authority activation, a real shadow window, LiteBans disablement/removal, final migration, issue #43 acceptance, or live cutover.

## Next route

1. Finish PR #60 only: complete final full-diff review, exact-head validation, and review resolution; merge normally only with zero unresolved valid threads and no known blocker.
2. Record the merge commit, resulting `main`, feature-head containment, no unmerged branch commits, and branch cleanup in PR metadata.
3. After PR #60 completes, freshly select one bounded remaining priority-one staff mode, vanish, or freeze item.
4. Keep the RoseChat provider blocker separate and do not use issue #43 as a general blocker queue.
5. Do not begin another feature in the PR #60 session.
