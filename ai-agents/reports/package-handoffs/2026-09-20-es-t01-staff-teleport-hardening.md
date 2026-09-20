# ES-T01 — staff teleport hardening handoff

Status: `ACTIVE` / `ACTIONABLE_CONTINUATION`.

Owner authorization: 2026-09-20 request to create review/bug-finding/fixing work useful for immediate plugin testing.

Start `main`: `c1054da6a8f89b312df2e05e25edc958fceda7ef`.
Branch: `package/es-t01-staff-teleport-hardening`.
PR: pending initial coherent checkpoint.

Collision preflight: active X03 PR #139, D08 PR #214, parked D09 PR #203, and parked D13 PR #178 do not own `paper/src/main/java/net/enthusia/staff/paper/staff/StaffToolRandomTeleportService.java` or `paper/src/test/java/net/enthusia/staff/paper/staff/StaffToolTargetPolicyTest.java`. No migration is added.

Finding `T01-STAFF-RTP-001`: candidate eligibility and location were captured during the initial multi-player collection phase. The later random choice reused that stale snapshot without revalidating the selected target, allowing a player that had since become vanished/frozen/staff/exempt/otherwise invalid to remain a teleport destination.

Repair: store eligible IDs during collection, shuffle once, then revalidate the selected target on its entity scheduler. Invalid/offline candidates are discarded and the next candidate is attempted. The current target location is captured only after that final revalidation. Actor authorization is still rechecked on the actor scheduler before `teleportAsync`.

Focused regression coverage extends `StaffToolTargetPolicyTest` with a candidate that is initially eligible and then becomes vanished/frozen before execution.

Local validation: Java 21.0.6 focused tests PASS; full `:paper:test` PASS; Lizard zero warnings at CCN <= 8 / method length <= 50 / args <= 8; `git diff --check` PASS. The repository orchestration validator reports pre-existing Dxx/X03 format debt, while its 8 validator unit tests pass and no T01-specific error is emitted.

Next action: checkpoint and open the PR, then complete exact-head hosted/static/review gates.

Do not disturb X03/D08/D09/D13, V21/V22 ownership, private staging configuration, production authority, or issue #43.
