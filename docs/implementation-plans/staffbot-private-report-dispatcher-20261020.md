# StaffBot notification dispatcher: isolated integration proof (2026-10-20)

This is a **draft, default-disabled composition test**, not a production sender.

## Purpose

This stacked branch combines:
- #649's transactionally durable, reference-only StaffBot intent journal and
  lease ownership/quarantine logic (V33).
- #648's private-channel eligibility policy, safely formatted original
  report descriptions, and sensitive-category redaction.
- A package-private `PrivateReportNotificationDispatcher` that demonstrates
  their intended integration behind `Journal`, `ReportReader`, `DestinationVerifier`
  and `Transport` interfaces. A private JDA adapter now implements both interfaces, but remains unregistered and has no production caller.

## Execution safety

1. `ownerApproved=false` is the default *calling convention*; the dispatcher
   is never registered, instantiated, scheduled or invoked by StaffBotRuntime.
2. It checks an explicitly supplied current private-destination proof
   **before** claiming a durable intent, and repeats that check before
   reading/sending player descriptions.
3. Only intents already in `ARMED` may be leased; V33 writes every historical
   and new report intent as `INERT`, and no production method promotes them.
4. It loads bounded report content by reference via an injected reader rather
   than copying descriptions or evidence to the intent table.
5. It acknowledges only explicit `CONFIRMED` outcomes. Discord timeout,
   uncertain send, lost report, permission change or exception leads to
   `REVIEW_REQUIRED`. Neither the dispatcher nor the ledger auto-retries
   ambiguous messages.
6. The unregistered JDA adapter already validates guild/channel/role
   permissions, enforces member-override cache coverage and sets zero allowed
   mentions. Before production registration, any confirmed Discord message ID
   still must be persisted in a durable audit before acknowledging delivery.
   The boolean destination proof alone is not authority: a live send requires
   fresh JDA verification against an explicitly owner-approved destination.

## Tests

Unit tests use fake journals, transport, proof sources and report readers to
check no DB access or sends while disabled, blocked private-channel criteria,
single acknowledged send, no retry after uncertainty, permission revocation
between preflight and send, missing cases and exceptions.

Hosted exact-head StaffBot, MariaDB, static-analysis, coverage and protocol
checks remain mandatory. The Windows development host subsequently recovered; local unit tests, integration compilation and the Paper shaded build passed, but hosted Linux/MariaDB validation remains required.

## Additional isolated delivery transport and read model

The draft now also includes `JdaPrivateReportNotificationTransport`. It is a
package-private, **unregistered** JDA adapter using the configured guild,
private channel and staff role IDs. When explicitly approved by a future
caller, it fetches current channel/role permission data, refuses missing or
wrong-guild destinations, requires member-override cache coverage, excludes
everyone/extra role/member viewers, and rechecks just before send. Any send
uses `setAllowedMentions(List.of())` (zero pings) and treats timeout, REST
errors and unknown outcomes as **UNCERTAIN** for journal quarantine.
Synthetic tests prove disabled and unresolved destinations cannot contact
Discord. No actual Discord messages are sent by these tests.

An opt-in `reportForPrivateNotification(reportId)` read-only lookup now
loads only a bounded 256-character excerpt of the original description
for a committed OPEN/CLAIMED/AWAITING_REVIEW case, by exact UUID. It does
not load merged reporter evidence, staff action notes, closed cases, or
any extra snapshots. Hosted MariaDB integration exercises this path.
The dispatcher must check the private destination before calling it.

All these classes remain disconnected from the running StaffBot process.
Their presence on a branch does **not** activate a notification feature.

## Release blockers

Issue #625 is **not closed**: there is no scheduler, configured destination,
approved and registered JDA transport, report-by-ID reader wired to StaffBotRuntime, genuine
test-channel send, or permission-revocation drill yet. Owner must approve the
exact private Discord destination and allowed staff roles. Migration V33 must
be rehearsed on a disposable clone before *any* live database rollout.
Existing guided-punishment and V32 report migration release gates also apply.

Never deploy these draft PRs or enable live Discord notifications from CI
success alone.
