# Latest AI handoff

Current handoff:

[`2026-08-04-vanish-live-rank-reconciliation.md`](2026-08-04-vanish-live-rank-reconciliation.md)

Related PR:

[`#59 — Reconcile vanish visibility with live rank changes`](https://github.com/wsg138/EnthusiaStaff/pull/59)

## Summary

| Field | Value |
| --- | --- |
| Work item | Reconcile cached vanish viewer and target ranks with live explicit permissions and durable staff-session state |
| PR | `#59` |
| Branch | `fix/vanish-live-rank-reconciliation` |
| Starting main | `8bed23c521f907aa134453445e77f17df75a3743` |
| State | `ACTIVE — exact-head validation and review pending` |
| Implementation | One owner-scheduled periodic check per player; incremental viewer/target refresh; durable target-rank correction; rank-removal disable; bounded and token-fenced durable staff-session verification for lower ranks; retryable staff-mode-exit cleanup |
| Startup behavior | Open durable staff sessions preserve valid Helper/Mod/Developer vanish while asynchronous staff-mode recovery completes; confirmed missing sessions disable it |
| Failure behavior | In-memory authority fails safely, writes and session checks are bounded, failed operations back off and retry, and stale reconnect callbacks are discarded |
| Tests | Promotion/demotion matrix, missing/`SYSTEM` ranks, durable-session active/inactive/unknown/exited states, Admin/Founder independence, stale durable cleanup, viewer-owner scheduling and reconnect fencing |
| Harsh-review fixes | Quit-message ordering; startup recovery race; collided exit cleanup; crash-window durable verification; reconnect-fenced session checks; event-driven viewer refresh |
| Migration boundary | V16 is highest; PR #59 adds no migration; V1–V16 remain immutable |
| Configuration changes | None |
| External provider blocker | RoseChat private-message evidence remains blocked pending the supported provider callback/event contract, delivery lifecycle, identity/duplicate fields, threading guarantees, version coordinates, privacy fields, and provider-present/missing behavior. Do not route it through issue #43. |
| Production boundary | No deployment, authority activation, LiteBans change, production Discord use, production access, or cutover |
| Next owner-priority workstream | Freshly select one remaining staff mode, vanish, or freeze item only after PR #59 completes |

Exact validation, review, Pi, and merge evidence belongs in PR #59 live metadata. Reject cancelled, superseded, skipped, stale-head, different-revision, and merge-ref-only results.

The next agent must reconcile live GitHub state before acting, resume PR #59 rather than opening another branch, read the canonical handoff, resolve every valid review finding, require the complete exact-head gate, and stop after merge or a verified blocker. Do not edit V1–V16, use Flyway repair, deploy, access production data, alter LiteBans authority, begin issue #43 acceptance, or combine another feature into PR #59.
