# EnthusiaMarket Moderation Coordination

EnthusiaStaff records case-linked moderation intent and coordinates the supported
EnthusiaMarket provider API. EnthusiaMarket remains the sole authority for stall,
ownership, shop, acquisition-blacklist, and restoration mutations.

## Runtime boundary

The shared version 1 contract lives in `integration-contracts` under
`net.enthusia.market.api.moderation`. Staff compiles against that contract and discovers
the provider through Paper's services manager. The Paper runtime JAR must not contain the
provider API classes; the Market plugin supplies the one runtime copy.

Staff authenticates the command actor and authorizes each case action before calling the
provider. The Paper services boundary assumes installed plugins are trusted code; it is
not a sandbox against a malicious co-resident plugin that can inspect JVM services,
memory, or server configuration. Operators must restrict plugin installation and update
access to trusted artifacts and administrators. Market independently validates request
identity, checksums, revisions, and state transitions rather than trusting caller-supplied
outcomes.

New writes fail closed when any of these conditions is true:

- moderation mode is not `ACTIVE`;
- the actor lacks the required action authority;
- Staff storage or case lookup is unavailable;
- the Market service is absent, unavailable, or not API version 1; or
- the case target does not match the requested player.

Local `/marketcase status` reads remain available during a Market outage when Staff's
durable journal is available. This is intentional recovery visibility, not permission to
mutate provider state.

## Staff journal lifecycle

Staff writes a `PREPARING` intent before calling Market. The provider then supplies the
authoritative state and checksums.

| Staff state | Meaning |
| --- | --- |
| `PREPARING` | Durable intent exists; the provider call may not have completed. |
| `PREPARED` | Market reserved the stall, captured the snapshot, froze shops, and retained ownership pending review. |
| `MODERATION_HOLD` | An explicitly identified reviewer approved confiscation against the exact snapshot checksum. |
| `RESTORED` | Market verified held state, restored the original snapshot, and released reservations. |
| `RELEASED` | A prepared operation was cancelled without removing ownership. |
| `QUARANTINED` | Identity, checksum, revision, transition, or recovery evidence was ambiguous. No automatic destructive continuation occurs. |

Recovery may replay `PREPARING`, `PREPARED`, and `MODERATION_HOLD` provider calls. It never
advances `PREPARED` to `MODERATION_HOLD`; confiscation always requires an explicit human
approval command.

## Commands and authority

All mutating forms require the literal uppercase token `CONFIRM`.

| Command | Purpose |
| --- | --- |
| `/marketcase prepare <player\|uuid> <case-id> <stall-id> CONFIRM` | Record intent and prepare a target-owned stall for review. |
| `/marketcase approve <operation-id> CONFIRM` | Approve the exact prepared snapshot and enter a moderation hold. |
| `/marketcase release <operation-id> CONFIRM` | Cancel a preparation and restore its original non-held state. |
| `/marketcase restore <operation-id> CONFIRM` | Founder-only exact restoration of a reviewed hold. |
| `/marketcase blacklist <player\|uuid> <case-id> <permanent\|ISO-8601> CONFIRM` | Apply a case-linked acquisition blacklist. |
| `/marketcase unblacklist <player\|uuid> <case-id> <expected-revision> CONFIRM` | Remove a blacklist only at the expected provider revision. |
| `/marketcase status <operation-id>` | Read the Staff journal, including during provider outages. |

`enthusiastaff.market.restrict` is the Bukkit discovery and early-denial permission for
the command. The coordinator independently applies `MODIFY_MARKET_RESTRICTION` authority.
Restoration additionally requires `enthusiastaff.market.restore` and Founder-level
`RESTORE_ASSETS` authority.

## Review alerts and restart recovery

Migration `V19__market_compliance_journal.sql` adds idempotency, recovery-window,
revision, creation-time, and review-alert metadata to `market_compliance_cases`. New
operations always populate those fields; legacy rows remain readable but are not treated
as recoverable ES-X03 operations without an idempotency key.

The bounded recovery worker processes at most 64 rows per pass and does not overlap with
itself. Due `PREPARED` reviews create a Staff alert and a durable Discord outbox message in
the same Staff transaction that claims the review alert. Exact retries replay the stored
result; stale journal or provider revisions return a conflict or quarantine outcome.

## Operator recovery

1. Stop repeated commands for the affected operation, player, and stall.
2. Run `/marketcase status <operation-id>` and record state, case, target, stall, Staff
   revision, provider revision, reviewer, and checksums.
3. Restore Market/provider availability before attempting a mutation.
4. For `PREPARING`, allow bounded recovery to reconcile by operation ID.
5. For `PREPARED`, choose explicit approval or release after reviewing the case.
6. For `MODERATION_HOLD`, restore only with Founder approval and the exact held checksum.
7. For `QUARANTINED`, preserve both journals and all provider locks. Do not edit SQL rows
   to make the operation appear terminal.

## Validation boundary

The root clean build runs Java 21 unit and MariaDB Testcontainers coverage, including the
V18-to-V19 upgrade and durable market journal lifecycle. Market independently tests V025,
concurrent preparation, snapshot verification, and exact restoration. These tests do not
replace representative destructive, process-kill, latency, or load acceptance, which is
owned by the later ES-V03 validation package. No production listings or player data are
required for development validation.
