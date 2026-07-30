# Punishment System

EnthusiaStaff uses one punishment application service for commands, GUIs,
automation, website actions, and provider integrations. Every entry point
normalizes input, resolves current actor authority, calculates the configured
recommendation, and commits the case, sanctions, events, audit, alerts, and
outbox work transactionally.

## Authority

- **Developer** can read punishment, case, report, and diagnostic data but
  cannot create or modify punishment state.
- **Mod** can apply the authoritative configured step, lower a recommendation,
  end or revoke a sanction while retaining history, and request a full
  overturn.
- **Admin** can raise or lower a recommendation, use configured sanction types
  with custom durations, fully overturn, and decide overturn requests.
- **Founder** has full punishment and recovery authority.

Historical cases retain the actor and rank recorded when they were created.
Changing current policy never rewrites historical provenance.

## Review workflow

`/punish <player>` opens the central workflow. `/ban`, `/mute`, `/warn`,
`/kick`, and `/ipban` open the same workflow filtered to compatible reasons.

1. Select a category and configured reason.
2. Calculate the ladder step from eligible non-overturned related history.
3. Review the exact reason ID, policy version, ordinal and recency inputs,
   sanction types, durations, visibility, and internal explanation.
4. Confirm once.
5. Re-resolve the draft, reauthorize the actor, and recalculate before commit.

A changed recommendation is rejected as stale and requires a new review.
Punishments and warnings are public unless the authorized actor explicitly
selects private visibility. Internal explanations remain private.

## Durable drafts

Reason selection creates a MariaDB draft bound to the actor and target. It
contains the complete reviewed recommendation and expires after 24 hours. One
current draft is retained for each actor-target pair.

Drafts survive logout, server switch, process crash, and restart. Resume through
the clickable message or `/punish resume <player>` on a Paper backend connected
to the same database.

The draft UUID is also the punishment idempotency identity. Concurrent or
retried confirmations can create at most one case. A successful case commit
removes the draft; retrying after cleanup failure returns the already committed
case rather than duplicating it.

## Sanction changes

Ending, reducing, revoking, or overturning a punishment appends immutable
sanction events and audit history. It does not delete the original case.
Combined sanctions are changed through the central service so related parts
cannot diverge silently.

A Mod may request a full overturn but cannot approve it. Admin and Founder may
decide overturn requests according to current authorization. Full overturn
changes ordinary/public visibility while preserving privileged audit.

## Failure behavior

Preparation and confirmation require `ACTIVE` mode. Database, policy,
authorization, idempotency, or stale-state failures block the write.
Network/platform enforcement starts only after the durable transaction commits.
Delivery is retried through durable outboxes; ambiguous external outcomes must
be quarantined rather than guessed.

See the
[source-controlled workflow specification](https://github.com/wsg138/EnthusiaStaff/blob/main/docs/punishment-workflow.md)
for implementation details and current limitations.
