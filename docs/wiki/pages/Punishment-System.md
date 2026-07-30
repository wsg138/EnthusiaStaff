# Punishment System

EnthusiaStaff routes commands, GUIs, automation, website actions, and provider
requests through one case and punishment policy. The goal is that every
sanction has one durable explanation, evidence chain, authority check, audit
history, and recovery path.

> **Current status:** The case model, policy engine, sanction mutations, and
> durable drafts exist in part and have automated coverage, but the complete
> GUI, `/history`, full overturn lifecycle, provider integrations, and staging
> verification remain incomplete. See [[Implementation Status]].

## Terms

- **Case** — the durable record containing the target, actor, reason, evidence,
  policy version, visibility, linked reports, sanctions, related actions,
  appeal state, and audit history.
- **Sanction** — an enforceable result such as warning, kick, mute, voice mute,
  network ban, IP/network ban, report restriction, or asset restriction.
- **Reason ID** — the stable configured identifier for the exact violation.
- **Family** — related reasons that contribute to escalation.
- **Ladder** — configured sequence of punishment steps.
- **Draft** — the actor-bound review state saved before final confirmation.
- **Revoke** — end or invalidate a sanction while preserving the case history.
- **Overturn** — determine that the punishment should no longer count as valid
  punishment history, while retaining the audit record.

## Standard workflow

`/punish <player>` opens the central workflow. `/ban`, `/mute`, `/warn`,
`/kick`, and `/ipban` open the same policy filtered to compatible sanctions.

1. **Resolve identity.** Confirm current name, UUID, previous names, platform,
   and relevant alt context.
2. **Choose category.** Select the broad rule area.
3. **Choose exact reason.** Match evidence to the configured examples and stable
   reason ID.
4. **Review history.** Read exact and related-family history, active sanctions,
   decayed history, recent reoffenses, and current ladder step.
5. **Review actions.** Confirm sanctions, durations, visibility, and only the
   relevant asset/network actions.
6. **Write internal explanation.** State what occurred and what evidence proves
   it.
7. **Confirm once.** The service re-resolves the draft, actor, target, policy,
   and recommendation before committing.

The durable commit should include the case, sanctions, sanction events, audit,
alerts, and network/Discord outbox work. Success must not be reported before the
authoritative transaction commits.

## Escalation

Each exact reason belongs to a family and ladder. The target design includes:

- Related less-serious offense: normally adds one step
- Related equal-severity offense: normally adds one step
- Related more-serious offense: normally adds two steps
- Reoffense within 30 days after a prior punishment ends: normally adds one step
- Configured decay may reduce contribution from minor or warning history
- Serious history normally does not decay
- History remains visible even when it no longer contributes

Do not manually “count punishments” from visible case totals. The policy engine
must use the stored reason family, severity, policy version, state, decay,
recency, and overturn status.

## Review colors

The intended reason review uses:

- Green — contributing history
- Aqua — decayed history
- Gold — recommended step
- Gray — future ladder steps
- Red — permanent step
- Purple — authorized override
- Dark red — extreme or zero-tolerance reason

Colors help explain policy; they are not authority by themselves.

## Durable drafts

A selected punishment is saved as a MariaDB draft bound to the actor and target.
The default lifetime is 24 hours. A draft is expected to survive closing the
GUI, logout, server switch, restart, and crash.

Resume with:

```text
/punish resume <player>
```

On resume and confirmation, the service must recalculate. If identity, policy,
history, rank, recommendation, or required integration changed, the old review
must be rejected as stale.

## Combined sanctions

Combined sanctions begin together and expire independently. For example, a
30-day mute combined with a 7-day ban leaves 23 days of mute when the player
returns, assuming no other change.

Changing one sanction must not silently change an unrelated sanction in the
same case.

## Public and private visibility

Punishments and warnings are public by default. Authorized staff may make an
eligible case private. Internal explanations, reporter identity, private
messages, coordinates, alt evidence, network identity, confiscated assets, and
sensitive automation remain private regardless of the public case state.

## Direct commands

These still use the central case system:

```text
/ban <player> [reason-id]
/mute <player> [reason-id]
/warn <player> [reason-id]
/kick <player> [reason-id]
/ipban <player> [reason-id]
```

They are not legacy shortcuts and must not bypass the configured reason,
history, authority, confirmation, or audit flow.

## Ending, reducing, revoking, and overturning

Use:

```text
/removepunishment <player|case> <action> [expiration] <reason> [CONFIRM]
/unban <player|case> <reason> [CONFIRM]
/unmute <player|case> <reason> [CONFIRM]
/removewarning <player|case> <reason> [CONFIRM]
/unwarn <player|case> <reason> [CONFIRM]
```

Every change should:

- Resolve the exact case and sanction
- Reauthorize the actor
- Preserve history
- Append audit
- Handle combined sanctions correctly
- Be idempotent across retry and restart
- Leave unrelated sanctions unchanged

A Mod requests a full overturn with a written explanation. The punishment
remains unchanged until an authorized approver decides it. Developer cannot
request or decide an overturn.

## When to stop

Do not confirm or repeat the operation when:

- The target resolution is ambiguous
- The recommendation changed after review
- The draft is stale
- MariaDB is unavailable
- Velocity is unavailable for a network sanction
- The service is not in an allowed operational mode
- A duplicate or conflicting request already exists
- The external provider result is uncertain
- The command is owned by another plugin unexpectedly

Record the case/draft/operation ID and use [[Recovery and Troubleshooting]].
