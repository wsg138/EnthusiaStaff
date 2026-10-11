# Staff-action Discord outbox recovery (review-only rollout)

Status: **implementation candidate; NOT deployed or production-verified**. Tracked by [#488](https://github.com/wsg138/EnthusiaStaff/issues/488).

## What this changes

New staff-action records contain one stable `eventId` in the local append-only JSONL audit and Discord journal. The immediate asynchronous MariaDB `discord_outbox` writer uses this exact ID in `message_id` and `idempotency_key=staff-action:<eventId>`; on duplicate it leaves the existing outbox row and its delivery state unchanged. The asynchronous journal reconciler checks local `discord-outbox/staff-actions-YYYY-MM-DD.jsonl` every 15 seconds, examines at most 200 complete records per pass (including a rejected database write), and stores a byte offset in a sibling `.jsonl.cursor` file only **after** database acceptance. After a database insertion failure, it stops the entire pass instead of attempting inserts from every later journal; malformed local records block only their own journal while other journals remain eligible.

Checkpoint updates force the temporary cursor file to storage before rename and attempt a parent-directory fsync where supported (directory sync is best-effort on Windows/unsupported providers). The journal append itself is not fsync-durable. This makes a worker rejection, temporary database outage, or crash between DB commit and cursor checkpoint *recoverable by offering the same event again*. The MySQL/MariaDB unique outbox constraint prevents a second database message row for the same event ID. Once it has entered the DB, the existing Velocity outbox machinery is responsible for its delivery attempts and dead letters.

## Important limitations

- The journal and audit files still write and flush synchronously inside existing staff-action event handlers; no claim of guaranteed disk durability after host power loss or that file IO is off the game thread.
- Staff actions logged **before this version** have no stable event ID; they cannot be replayed safely and are skipped with a warning. Preserve historical logs for manual investigation rather than inventing random replay IDs.
- If a local record is truncated, malformed, too large, or the cursor unreadable, processing stops at that entry until an operator inspects it. Do not blindly delete or truncate evidence. A partial final line is retried only after its newline is complete.
- Ordinary staff-action audit files rotate with 30-day retention. Discord replay journals are **not age-deleted while unacknowledged**, even after prolonged database downtime. Journals older than 30 days are deleted along with cursors only after the replay cursor reaches the end of the file AND a bounded, incremental full-journal validation confirms every record has a replay-safe event ID and timestamp. Journals containing any legacy, malformed, unidentified, or incomplete record are retained for manual inspection, even if an older version already checkpointed past that record. The 200-record per-pass budget is shared by replay and retention validation; large historical journals can require multiple passes to prune. This protects backlog but can grow disk usage indefinitely during an extended outage; monitor journal size and free space. Backlog is bounded to 200 records every 15 seconds; very high-volume staff mode logs need separate capacity/throughput testing.
- StaffActionLogger retains best-effort file writes, so simultaneous local disk and DB failure can still lose the event. Check server warnings and disk quota rather than assuming all action attempts were recorded.
- The recovery database insert is idempotent. Discord HTTP delivery is **at least once**, not guaranteed exactly once after ambiguous timeouts. PR #487 addresses repeated *role pings* separately.
- This does not expand staff-action event coverage beyond the existing listener and tiered staff-mode handlers. Event-by-event source inventory, privacy review, end-to-end private Discord channel checks, and replay/dead-letter health dashboards remain outstanding under #488.
- No new staff permissions, sanction authority, alt enforcement, DiscordSRV cutover, or public chat routing.

## Pre-deployment acceptance

1. Verify Paper tests and hosted checks on the exact candidate SHA, including MariaDB-backed integration tests for duplicate/no-op inserts and cursor recovery.
2. Review current raw staff-action capture paths for private data, permission boundaries and action spam; verify the private `logs-staffmode` destination and webhook setup.
3. In staging, simulate a worker rejection, DB outage, restart before checkpoint, and webhook 429/5xx. Match each stable `eventId` to one database row and at least one private Discord embed; ensure no duplicate staff-role mention.
4. Verify backlog drain rate, disk quota, warning counters and historical-journal retention. Simulate a backlog more than 30 days old, confirming it persists while DB is down and is cleaned only after durable DB acceptance. Add monitoring for events still pending replay and for dead-letter rows.
5. Obtain explicit owner approval before promoting the Paper JAR or scheduling a restart. Keep original JAR/config and a proven rollback path.

If replay blocks, preserve the original journal and cursor, gather sanitized logs and checksums, and investigate the precise line. Do not clear the outbox, disable logging, or bulk re-send historical staff actions without a privacy and duplicate assessment.
