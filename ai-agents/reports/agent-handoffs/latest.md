# Latest AI handoff

Current handoff:

[`2026-08-02-pr51-escalation-clean-period-decay.md`](2026-08-02-pr51-escalation-clean-period-decay.md)

Related PR:

[`#51 — Fix escalation clean-period decay`](https://github.com/wsg138/EnthusiaStaff/pull/51)

## Summary

| Field | Value |
| --- | --- |
| Work item | Correct escalation decay so recent related reoffending resets the clean-period clock |
| PR | `#51` |
| Branch | `fix/escalation-clean-period-decay` |
| Recorded state | Implementation, focused tests and routing records frozen before exact-head validation |
| Exact final-head evidence | Read PR #51 live |
| Exact merge evidence | Read PR #51 live |
| Migration boundary | V14 remains latest; no migration added or edited |
| Production boundary | No deployment, authority activation, LiteBans change or production access |
| Remaining blocker | Supported RoseChat private-message provider contract remains unavailable |
| Next recommendation | Resume RoseChat only if its contract exists; otherwise select one prerequisite-ready escalation-policy slice after live reconciliation |

The next agent must verify live GitHub state before acting. Confirm whether PR #51 merged, inspect newer open PRs and review threads, verify current `main`, migration state, exact-head workflow evidence and branch cleanup, then read the full handoff. Do not invent or reflect against an unknown RoseChat API.
