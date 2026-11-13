# EnthusiaStaff · Read-only AI decision history draft

**2026-10-09. Offline development only; do not deploy or enable moderation.**

This work extends the existing W15 AI review subsystem and does not create a
second authority. The central AI Moderation API draft
[wsg138/AI-Moderation-API#87](https://github.com/wsg138/AI-Moderation-API/pull/87)
provides staff-authorized paginated `GET /v1/decisions`.

## Staff command

- `/aireview history` — up to 10 recent finalized decisions from the central API
- `/aireview history <cursor>` — continue using the opaque `next_cursor` supplied on the previous page
- `/aireview view <event-id>` — existing authorized in-game event detail/correction flow

Like `/aireview list`, this command requires an enabled AI review integration
and queue-read permission. Players must also have **active staff duty**; the
async callback checks their current duty/permission before displaying results.
Network calls reuse W15's bounded executor, authentication, response byte
limit, retry backoff and fail-soft behavior. Console output contains only
minimized decision summaries (not raw message content).

The listing intentionally includes every **persisted FINAL** decision:
ordinary ALLOW, BLOCK, review-priority decisions, and committed fail-open
outcomes, with a visible marker for degraded or corrected results. It does not
treat historical ALLOW as verified correctness or show private text, player
identifiers, or full context. Existing event detail permissions continue to
protect raw text access.

The central API retains existing exemptions: staff/ticket channel data is
neither classified nor stored. Connection failure, saturation and database
failure cannot be guaranteed durably logged. Canonical mirrored messages
are represented by one event rather than duplicating training observations.

This first phase is a **staff command and typed HTTP read client**, not yet a
new in-game inventory GUI, and is not a reviewed dataset export. Full GUI
browse/filter, correction labelling, retention/backups and rights-checked,
anonymized training promotion are separate follow-up tasks. Never fine-tune
on unreviewed AI guesses or upload raw private-player conversations publicly.
