# StaffBot confirmed Discord message receipts — isolated draft

**2026-10-20 • Not registered or deployed.** This branch stacks on #654
and introduces V34 for a Discord delivery receipt ID alongside V33's
reference-only, default-INERT StaffBot report notification journal.

Previously the isolated JDA transport could confirm a Discord message
but discarded its ID. The revised `DeliveryReceipt` requires a validated
Discord snowflake returned from the JDA message response. A missing,
malformed, timed-out or uncertain response cannot be acknowledged.

The JDBC ledger now requires the same unexpired lease owner and the
confirmed Discord message ID. It writes `DELIVERED`, timestamp and
`discord_message_id` **atomically**. V34 enforces receipt presence for
DELIVERED rows, absence for all other states, and uniqueness. No player
report descriptions, snapshots, case evidence, or tokens are copied
into this table.

Verification requirements:

- Unit tests: synthetic JDA response includes an ID; zero allowed mentions;
  invalid receipts rejected; uncertain/rejected acknowledgement quarantined.
- Hosted MariaDB tests: V33->V34 migration, default-INERT report intents,
  synthetic lease, invalid-ID rejection, durable receipt ID, terminal delivery,
  and no replay after restart.
- Hosted Codacy, StaffBot JAR, coverage and Paper compatibility must pass
  on the exact PR head.

This does not register a listener, scheduler or active JDA sender.
V34 must be rehearsed only on a disposable DB clone with full rollback.
No migration to the live server is authorized. StaffBot report notifications
must remain disabled until the exact private Enthusia Discord destination,
permissions, staff roles, and safe test-server isolation are approved.

**Limit:** A crash between Discord accepting a message and the transaction
recording its ID is still ambiguous. Such a lease must remain
REVIEW_REQUIRED. Do not automatically resend; a human-authorized
reconciliation workflow will be required before production activation.

## Additional report provenance gates

V33 journals both original reports and merged reporter follow-ups, but
the read-only private StaffBot lookup exposes the original description,
not the new follow-up message. Until an approved and authenticated
follow-up lookup exists, the unregistered dispatcher rejects and
quarantines all event types other than REPORT_CREATED. It never
presents an old original body as a new merged report update.

The fetched report must also have the exact UUID named in the claimed
intent; a mismatched ID fails closed with no Discord send. Synthetic
negative tests cover both paths and confirm no text reaches the sender.
These are deliberate no-send states requiring review, not retries.

## V34 migration review resolution (2026-10-20)

The independent review identified historical V33 DELIVERED rows without any
Discord receipt and a PCRE SQL end-anchor mismatch. V34 now explicitly
handles both instead of fabricating message IDs:

1. Add nullable discord_message_id and legacy_delivered_at columns.
2. For any existing V33 DELIVERED row with no receipt, archive its original
   delivered_at into legacy_delivered_at, clear delivered_at/lease state and
   move it to REVIEW_REQUIRED. This **never rearms or resends** the intent
   and preserves the original timestamp for a human reconciliation decision.
3. Add uniqueness and a strict SQL CHECK. Its regex uses MariaDB PCRE
   absolute end-of-subject \z, escaped for the SQL string as \\z.
   Unlike $, this does not match before a final newline.
4. All newly DELIVERED rows require a validated unique Discord message ID.
   The read-only record of historical delivery is intentionally not
   equated with a verified current receipt.

The isolated StaffBotReceiptV34MigrationIntegrationTest explicitly migrates
a synthetic V33 DELIVERED row to V34 and asserts the quarantine and archived
timestamp, then attempts to use a newline-suffixed receipt via direct SQL.
The latter must violate the SQL CHECK, matching Java's whole-string
validation. Windows compiles this integration test but its local
Testcontainers Docker engine is unavailable; require hosted MariaDB
integration before proceeding.

The migration comprises multiple MariaDB DDL/DML statements; do not assume
all-or-nothing DDL rollback. Rehearse V33->V34 backup, interrupted migration
recovery and restoration against a disposable snapshot before any deployment.
