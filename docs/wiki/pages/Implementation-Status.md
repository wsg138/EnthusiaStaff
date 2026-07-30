# Implementation Status

This page separates the finished platform design from what is currently proven.

> **Overall verdict: NOT READY for production authority.** LiteBans and the
> existing production staff stack remain authoritative until the complete
> migration, 168-hour shadow period, staging acceptance suite, and cutover gate
> pass.

## Snapshot basis

- Main repository reviewed at commit `35114534f5481088b79b1e316d89e72d2f64b16f`
  when the repository-managed Wiki branch was refreshed.
- Pre-migration Wiki preserved at commit
  `ea4f929710d3281aac4a8087da1e947973c2d795`.
- Statuses follow `reports/REQUIREMENTS-MATRIX.md` conservatively.
- The matrix itself may lag the latest main commit; runtime evidence always
  overrides optimistic documentation.

## Status summary

### Tested locally, still requiring staging

- Exactly two Java 21 runtime jars and packaging checks
- Authenticated persistent Paper–Velocity communication and durable outbox
- Durable Discord outbox lease/retry/fencing behavior

“Tested” here does not mean production-ready. Real plugin loading, provider
compatibility, certificates, multi-backend behavior, live Discord routing, and
failure staging remain.

### Partial

- Architecture and bounded contexts
- MariaDB schema and persistence
- Operational modes and degraded behavior
- Modular configuration and atomic reload
- Player identity and offline lookup
- Cases, sanctions, visibility, combined sanctions
- Punishment GUI and durable drafts
- Escalation, decay, and policy versioning
- Rank authorization
- Punishment removal and overturns
- Reports and evidence capture
- Client evidence and strict automod
- Inventory and Ender editing
- Item and economy confiscation/restoration
- Staff mode, vanish, and freeze
- Staff tools and inspector
- Alt tracking and inheritance
- Provider APIs
- Website and appeals
- LiteBans migration, shadow, cutover, and rollback
- Command registration and full verification
- Security, performance, CI, and documentation

### Blocked

- Complete RoseChat moderation/staff bridge: provider repository/API unavailable
- Polar automatic enforcement: supported violation event API unavailable
- Full acceptance and staging suite: required environment and feature scope
  incomplete

## Registered commands

Current Paper metadata registers:

```text
/estaff
/punish
/ban
/mute
/warn
/kick
/ipban
/removepunishment
/unban
/unmute
/removewarning
/unwarn
/report
/reports
/freeze
/unfreeze
/staff
/vanish
/staffchat
/client
/invsee
/endersee
/inspect
/case
```

Registration only proves that Bukkit can route the command. It does not prove
the complete workflow is usable.

## Missing required commands

```text
/history
/alts
/alt
/fakebase
```

## Production gates

Do not replace LiteBans or remove old plugins until all of these are recorded:

1. Java 21 clean build and tests
2. Exactly two inspected runtime jars
3. Paper–Velocity transport without an online player
4. Network ban/mute and voice mute
5. Restart/reload survival
6. Safe online/offline inventory behavior
7. No item duplication or deletion under concurrency
8. Staff-state and vanish crash recovery
9. Complete freeze restrictions and reconnect
10. Report privacy and evidence capture
11. Alt inheritance and household exceptions
12. Discord circuit breaker
13. Command conflict verification
14. Exact 168-hour non-enforcing shadow period
15. LiteBans count, identity, active-state, and expiration parity
16. Website authentication, codes, appeals, privacy, and media controls
17. Codacy and CI requirements
18. Documented rollback and emergency freeze
19. Staging evidence with exact commit and jar hashes

## How to update this page

When a feature changes:

1. Update its requirements-matrix row.
2. Link direct test or staging evidence.
3. Update the relevant Wiki procedure.
4. Change status only to the highest level actually proven.
5. Record limitations and disabled integrations.
