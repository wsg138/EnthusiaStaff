# Staff-action Discord outbox recovery (review-only rollout)

Status: **implementation candidate; NOT deployed or production-verified**. Tracked by [#488](https://github.com/wsg138/EnthusiaStaff/issues/488).

## What this changes

New staff-action records contain one stable `eventId` in the local append-only JSONL audit and Discord journal. The immediate asynchronous MariaDB `discord_outbox` writer uses this exact ID in `message_id` and `idempotency_key=staff-action:<eventId>`; on duplicate it leaves the existing outbox row and its delivery state unchanged. The asynchronous journal reconciler checks local `discord-outbox/staff-actions-YYYY-MM-DD.jsonl` every 15 seconds, processes at most 200 complete records per pass, and stores a byte offset in a sibling `.jsonl.cursor` file only **after** database acceptance.

This makes a worker rejection, temporary database outage, or crash between DB commit and cursor checkpoint *recoverable by offering the same event again*. The MySQL/MariaDB unique outbox constraint prevents a second database message row for the same event ID. Once it has entered the DB, the existing Velocity outbox machinery is responsible for its delivery attempts and dead letters.

## Important limitations

- The journal and audit files still write and flush synchronously inside existing staff-action event handlers; no claim of guaranteed disk durability after host power loss or that file IO is off the game thread.
- Staff actions logged **before this version** have no stable event ID; they cannot be replayed safely and are skipped with a warning. Preserve historical logs for manual investigation rather than inventing random replay IDs.
- If a local record is truncated, malformed, too large, or the cursor unreadable, processing stops at that entry until an operator inspects it. Do not blindly delete or truncate evidence. A partial final line is retried only after its newline is complete.
- The local journal files are retained for 30 days, including checkpoints. Sustained DB downtime exceeding this retention can still cause permanent omissions. Backlog is bounded to 200 records every 15 seconds; very high-volume staff mode logs need separate capacity/throughput testing.
- StaffActionLogger retains best-effort file writes, so simultaneous local disk and DB failure can still lose the event. Check server warnings and disk quota rather than assuming all action attempts were recorded.
- The recovery database insert is idempotent. Discord HTTP delivery is **at least once**, not guaranteed exactly once after ambiguous timeouts. PR #487 addresses repeated *role pings* separately.
- This does not expand staff-action event coverage beyond the existing listener and tiered staff-mode handlers. Event-by-event source inventory, privacy review, end-to-end private Discord channel checks, and replay/dead-letter health dashboards remain outstanding under #488.
- No new staff permissions, sanction authority, alt enforcement, DiscordSRV cutover, or public chat routing.

## Pre-deployment acceptance

1. Verify Paper tests and hosted checks on the exact candidate SHA, including MariaDB-backed integration tests for duplicate/no-op inserts and cursor recovery.
2. Review current raw staff-action capture paths for private data, permission boundaries and action spam; verify the private `logs-staffmode` destination and webhook setup.
3. In staging, simulate a worker rejection, DB outage, restart before checkpoint, and webhook 429/5xx. Match each stable `eventId` to one database row and at least one private Discord embed; ensure no duplicate staff-role mention.
4. Verify backlog drain rate, disk quota, warning counters and retained-day safety. Add monitoring for events still pending replay and for dead-letter rows.
5. Obtain explicit owner approval before promoting the Paper JAR or scheduling a restart. Keep original JAR/config and a proven rollback path.

If replay blocks, preserve the original journal and cursor, gather sanitized logs and checksums, and investigate the precise line. Do not clear the outbox, disable logging, or bulk re-send historical staff actions without a privacy and duplicate assessment.
