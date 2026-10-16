# StaffBot private report description preview

Date: 2026-10-16. Issue: #625. Status: draft, staging only.

## Implemented

The existing StaffBot /review-queue view:reports command uses an ephemeral
deferred reply that is visible only to its requesting Discord user. After
Minecraft rank lookup, only MOD, ADMIN and FOUNDER use the opt-in
pendingReports(limit, true) read and see bounded previews of original
reports.description. Helpers and Developers stay metadata-only, as does the
general /review-queue view:all. Older pendingReports(limit) callers stay
metadata-only and do not select description from MariaDB.

The preview is limited to 240 Unicode code points per report, neutralizes
Discord mentions and Markdown, flattens line breaks, and leaves sensitive
safety/privacy/account/personal/sexual harassment categories redacted.
Mod+ ephemeral cards also include the exact case ID and /reports view command
for permission-gated full-description access; lower-rank cards do not.
It does not fetch message snapshots, merged reporter follow-ups or internal
staff case action notes. The real Minecraft reports permission remains the
route for full details.

## Not implemented / release gates

This is not automatic StaffBot notification of incoming reports. That
requires explicit approval for a private channel in the actual Enthusia
Discord, exact guild and role checks, safe deliver/retry semantics and
staging tests. Do not route freeform player text through Velocity webhooks,
the public DiscordSRV chat channel or arbitrary Discord destinations.

The user must authorize a staging test in the existing private test channel
with synthetic reports and accounts; verify Minecraft rank removal,
guild/channel access, Bedrock names, Unicode text, long content and safety
redaction. Do not send private production player reports to the test channel.

Local Domain/StaffBot tests and compile/run of the target build must pass;
hosted exact-head MariaDB integration must test that default reads are
metadata-only and privileged previews read original body. No live tokens,
live Discord sends, migrations, config edits or production JAR changes.
The separate history provenance issue #624/#637 remains independent.
