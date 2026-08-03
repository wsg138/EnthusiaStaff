# Latest AI handoff

Current handoff:

[`2026-08-03-staffmode-live-rank-reconciliation.md`](2026-08-03-staffmode-live-rank-reconciliation.md)

Related PR:

[`#57 — Reconcile active staff mode with live rank changes`](https://github.com/wsg138/EnthusiaStaff/pull/57)

## Summary

| Field | Value |
| --- | --- |
| Work item | Reconcile active staff-mode authority and temporary profile state with the current explicit player rank |
| PR | `#57` |
| Branch | `fix/staffmode-live-rank-reconciliation` |
| Starting main | `8c63f29923cf6c01624371adffcfceb3ddf71a0c` |
| Expected committed state | `IDLE — PR #57 requires live merge verification` |
| Implementation | Action-time live-rank checks plus one bounded periodic entity check; serialized profile replacement for promotions/demotions; existing durable exit and exact restoration for rank removal |
| Harsh-review fixes | Verify required game mode before publishing the new cached profile; bound pending periodic checks to one per active player |
| Tests | `StaffModeRankReconciliationPolicyTest` covers unchanged, every player-rank transition, missing cached state, rank removal and `SYSTEM`; `StaffModeAccessPolicyTest` preserves prior boundaries and proves unresolved/`SYSTEM` fail closed |
| Migration boundary | V16 is highest; PR #57 adds no migration; V1–V16 remain immutable |
| Configuration changes | None |
| Production boundary | No deployment, authority activation, LiteBans change, production Discord use or production access |
| Next owner-priority workstream | Freshly select one separate staff mode, vanish or freeze item after PR #57; do not begin it in this PR |

Exact validation, review, Pi and merge evidence belongs in PR #57 live metadata. Reject cancelled, superseded, skipped, stale-head, different-revision and merge-ref-only results.

The next agent must reconcile live GitHub state before acting, resume PR #57 rather than opening another branch, read the canonical handoff, resolve every valid review finding, require the complete exact-head gate, and stop after merge or a verified blocker. Do not edit V1–V16, use Flyway repair, deploy, access production data, alter LiteBans authority, begin issue #43 acceptance, or combine another feature into PR #57.
