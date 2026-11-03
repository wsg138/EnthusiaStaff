# Private StaffBot report notification: default-off delivery gate

Prepared 2026-10-20 for issue #625. This is **staging-only** development, not permission to notify staff.

## Scope of this change

`PrivateReportNotificationTemplate` is an inert, package-private formatting and routing guard
for a **future** automatic report notification using the actual StaffBot.
It cannot open a Discord channel, send a message, poll the database,
trigger staff actions, or read report evidence.

It composes a readable notification with the reporter/target names, human-readable
reason, bounded original description, full case UUID and an in-game
`/reports view <UUID>` instruction. The description is sanitized using
the existing #643 private report preview helper. Safety/privacy/account/personal/
sexual harassment classifications are redacted. Unknown usernames/states are
rendered safely. Full case IDs and descriptions never go into a public webhook.

`DestinationProof` fails closed unless every necessary *declared* predicate is
satisfied: **explicit owner enablement**, exact expected/actual Discord guild,
resolved channel, existing staff role, no @everyone view, staff can view,
no other-role view, and no unauthorized member override. This uses the existing
investigation-alert private-channel policy for consistent checks.
The proof must later be generated from **fresh authoritative JDA data**; arbitrary
booleans are not a security capability, and constructing a proof never
authorizes sending.

The default-off gate means a future caller must explicitly provide an
approved and current delivery proof. There is **no caller** in the production
notification service and **no configuration switch** in this PR.

## Still required for automatic notifications

1. Owner chooses/approves the specific Discord guild, private staff channel,
   allowed roles and behavior for restricted report types.
2. Actual StaffBot runtime verifies JDA guild/channel/role and all permission
   overrides against an explicitly configured destination on *every send*.
   Fail closed if effective permission data or cache coverage is unavailable.
   Use zero allowed mentions; do not ping staff from reporter-provided text.
3. Bind the notification to a **durable report commit/outbox**, including
   merged follow-ups when approved. Preserve idempotency across reboot,
   duplicate receipt, timeout, and retry. Dedupe messages and avoid duplicate pings.
4. Link existing `#637` reporter-follow-up provenance safely, without exposing
   staff action notes or old unverified messages.
5. Stage with synthetic Java/Bedrock names and untrusted report descriptions in
   an authorized test Discord channel. Verify account role loss and accidental
   cross-guild/public channel rejection.
6. Exact-head hosted tests and migration rehearsal on a disposable database clone,
   followed by explicit approval before any live Discord notifications.

This feature must **not** be described as finished merely because the text
composition tests pass. Never deploy or modify production JAR, config, Discord
credentials/channels or MariaDB as part of this draft.
