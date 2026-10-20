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
  and `Transport` interfaces. The transport has no JDA implementation.

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
6. A real future `Transport` MUST perform independent, current JDA permission
   checks against the owner-approved guild/channel/role, set **zero allowed
   mentions**, enforce destination/cache completeness, obtain a confirmed
   Discord message ID, and keep it in a durable audit before acknowledging.
   The boolean `DestinationProof` in the current template is not authority
   on its own and is only an injectable test representation.

## Tests

Unit tests use fake journals, transport, proof sources and report readers to
check no DB access or sends while disabled, blocked private-channel criteria,
single acknowledged send, no retry after uncertainty, permission revocation
between preflight and send, missing cases and exceptions.

Hosted exact-head StaffBot, MariaDB, static-analysis, coverage and protocol
checks remain mandatory. The Windows Blackboard host has been temporarily
unavailable, so no local Windows build is claimed for this change.

## Release blockers

Issue #625 is **not closed**: there is no scheduler, configured destination,
trusted JDA transport, report-by-ID reader wired to StaffBotRuntime, genuine
test-channel send, or permission-revocation drill yet. Owner must approve the
exact private Discord destination and allowed staff roles. Migration V33 must
be rehearsed on a disposable clone before *any* live database rollout.
Existing guided-punishment and V32 report migration release gates also apply.

Never deploy these draft PRs or enable live Discord notifications from CI
success alone.
