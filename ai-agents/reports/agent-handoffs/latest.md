# Latest AI handoff

Current handoff:

[`2026-08-03-pr54-serious-offense-decay-metadata.md`](2026-08-03-pr54-serious-offense-decay-metadata.md)

Related PR:

[`#54 — Preserve serious-offense decay eligibility in escalation history`](https://github.com/wsg138/EnthusiaStaff/pull/54)

## Summary

| Field | Value |
| --- | --- |
| Work item | Persist each new punishment step's configured decay eligibility and evaluate later history from that immutable value |
| PR | `#54` |
| Branch | `feature/serious-offense-decay-metadata` |
| Starting main | `fc1e94bd7317d59a33d297a049a94fd2eb3f1c5e` |
| Expected committed state | `IDLE — PR #54 requires live merge verification` |
| Harsh-review fix | Added authoritative service-path proof that both eligible and ineligible policy values reach the committed punishment plan |
| Exact final-head evidence | Read PR #54 live |
| Exact merge evidence | Read PR #54 live |
| Pi wrapper evidence | `https://github.com/wsg138/EnthusiaStaff/actions/runs/30794945133` |
| Pi failure artifact | `https://github.com/wsg138/EnthusiaStaff/actions/runs/30794945133/artifacts/8848768264` |
| Private Pi staging repository | `https://github.com/wsg138/EnthusiaStaff-Staging` |
| Failed private staging run | `https://github.com/wsg138/EnthusiaStaff-Staging/actions/runs/30794966760` |
| Staging isolation fix | Private PR #7 merged normally as `635423c64a2254d137002fce32652eb20770db34` |
| Migration boundary | PR #54 adds V16; V1–V15 remain immutable |
| Configuration changes | V16 schema only; no new runtime keys, permissions, environment variables or provider dependencies |
| Production boundary | No deployment, authority activation, LiteBans change or production access |
| Remaining external blocker | Supported RoseChat private-message provider contract remains unavailable; use a focused blocker issue and the normal handoff, not issue #43 |
| Next owner-priority workstream | Staff mode, vanish, and freeze; report notification completion is second and escalation-policy completion is third |

The Pi wrapper uses `pull_request_target`, so commit-scoped workflow listings may omit it. The failed wrapper artifact records the dispatched private run, job state, sanitized logs and nested Pi evidence. The failure was caused by reused disposable staging-database history from an earlier mutable V16, not by a reason to edit V16 or use Flyway repair. The private staging fix now clears only a guarded staging/test/Pi database before and after each two-boot cycle while preserving it between boots for restart testing.

The next agent must verify live GitHub state before acting. Confirm whether PR #54 merged, inspect newer open PRs and review threads, verify current `main`, migration state, exact-head Coverage/Wiki/Pi/Codacy evidence and branch cleanup, then read the canonical handoff. For Pi, inspect both the public wrapper and `wsg138/EnthusiaStaff-Staging` directly. Do not reinterpret pre-V16 null eligibility, edit V1–V15, use Flyway repair, treat issue #43 as a general blocker queue, or begin another escalation-policy slice immediately after PR #54 without a recorded exception or direct owner approval.
