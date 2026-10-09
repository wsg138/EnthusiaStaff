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

The base draft PR #477 provides a **staff command and typed HTTP read
client**. The separate stacked GUI draft adds an inventory browser for players:
`/aireview history` opens **All Decisions**, and the existing flagged queue
has an **All Decisions** switch. ALLOW/BLOCK entries use distinguishable items;
previous/next controls load cursor pages, and clicking an event opens the
existing authorized detail view. Back from that view returns to the same
cached history page, **including after visiting the label picker or correction
confirmation**. Successful corrections refresh the originating history cursor
page. History-related failure/conflict handling preserves the originating
page instead of unexpectedly redirecting to the flagged queue. The history
inventory is read-only and only records decision summaries, not raw chat.

GUI loads use the existing bounded async client and generation fencing:
active staff duty, queue permission and the current view generation are
checked again in the final player-scheduler task before rendering and before
responding to clicks. Event detail access still
requires the separate detail permission. All stored pages are immutable,
with cursor stacks bounded to 50 previous pages. No additional staff or
console permissions are granted. Explicit-cursor player commands and console
requests retain the compact text pagination path.

## Staging acceptance — not executed

Before anybody enables or merges these draft changes, use an **isolated,
authorized staging server** with a central API sandbox holding synthetic
audit events only. Do not point the test at live player private messages.

1. Verify an active-duty staff member with queue-read permission can switch
   between **Flagged Queue** and **All Decisions**.
2. Verify an unflagged ALLOW shows green, an enforced BLOCK shows red, an
   ALLOW requiring staff review shows yellow, and a stored FAIL_OPEN shows
   gray with the explicit note that it was **not verified safe**.
3. Generate enough synthetic events for three pages. Test Next → Next →
   Previous → Previous and Refresh. Check that no item is skipped, repeated
   unexpectedly, or paired with the wrong event.
4. Open an event from page two, then return. Verify the same cursor page
   remains visible; repeat through label selection, confirmation, and
   correction failure/conflict. Confirm an accepted correction refreshes the
   origin page without mutating the original AI decision.
5. Revoke active staff duty or read permission while a history request is
   outstanding. Ensure the result is not opened or disclosed. Revoke detail
   permission before clicking an event and confirm sensitive content stays
   inaccessible.
6. Simulate an unknown/expired cursor, unavailable API, and fail-open
   condition. History browsing must not back off unrelated review actions or
   claim messages were blocked when they were delivered.
7. Confirm the history list has **no raw chat text, sender IDs or neighbor
   messages**, and ticket/staff-exempt content remains absent entirely.
8. Exercise a queued correction only with synthetic records and authorized
   staff. Verify the existing correction quorum still controls writes and
   no action creates punishment or changes live moderation policy.

Capture exact draft SHAs, test output, and a **redacted** staging receipt.
Any failed acceptance step keeps the PR draft. Staging acceptance is separate
from GitHub CI and does not authorize production deployment.

This is not a reviewed dataset export. Advanced GUI filtering, retention /
backups and rights-checked anonymized training promotion remain follow-ups.
Never fine-tune on unreviewed AI guesses or upload raw private-player
conversations publicly.
