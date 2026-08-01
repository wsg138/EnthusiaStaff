# Implementation Status

This page separates the finished platform design from what is currently proven.
Use [[Development Blueprint]] for the ordered path from this checkpoint to
production authority.

> **Overall verdict: NOT READY for production authority.** LiteBans and the
> existing production staff stack remain authoritative until the complete
> migration, full acceptance suite, mandatory 168-hour shadow period, cutover
> rehearsal and final authorization gate pass.

## Snapshot basis

- Current `main`: `3f4e2a1164d570aadfb82522b07b4b32c9f2a7f9`
- Latest merged checkpoint: PR #36, LiteBans shadow-comparison decomposition
- Validated PR #36 head: `3afeffc926571170e8df18c7d096ca7f4d89ec1b`
- Clean Java 21 result: 40/40 tasks, 99 suites / 398 tests
- MariaDB 11.8.3 Testcontainers: 15 suites / 68 tests
- Hosted Codacy: zero new findings, three fixed, 92.59% diff coverage and no clone increase
- Exact-SHA Pi boot/restart staging: run `30709333535`
- Active draft PR #37: LiteBans cutover coordination; only its focused first checkpoint is currently claimed
- Separate draft PR #27: punishment-request notifications, staff mode and freeze; 95 commits requiring careful reconciliation
- Pre-migration Wiki backup: `ea4f929710d3281aac4a8087da1e947973c2d795`

Statuses follow `reports/REQUIREMENTS-MATRIX.md` conservatively. A passing branch
checkpoint proves only its tested scope. It does not establish provider,
multi-server, Bedrock, Folia, load, process-kill, complete migration, shadow or
production acceptance.

## Major progress since the previous Wiki snapshot

The repository is more than 199 commits beyond the old implementation snapshot.
Major changes include:

- Paper startup and shutdown composition split into focused runtime, command,
  integration, configuration, network and cleanup collaborators.
- Durable punishment request submission, review, approval, denial, confirmation,
  queue, permission and offline-target presentation paths.
- MariaDB website projections, punishment codes, appeals, report state,
  submission replay, query separation and evidence maintenance.
- Signed restricted website API routing and appeal failure contracts on Velocity.
- Report persistence concurrency, semantic conflict, rollback, privacy filtering,
  physical retention and replay scenarios.
- Vanish audience coordination, reconnect fencing, fail-closed behavior and
  entity-owner scheduling improvements.
- Protocol authentication, replay, envelope and validation-before-receipt tests.
- Deterministic LiteBans schema aliases/blockers and detailed shadow comparison
  across counts, checksums, active state, UUIDs, expirations and enforcement decisions.
- Exact-SHA Pi staging and expanded coverage/Codacy workflows.

This is substantial progress, but several remaining areas are among the highest
risk in the project.

## Tested checkpoints

| Area | Current proof | Remaining production work |
| --- | --- | --- |
| Exactly two Java 21 runtime jars | Clean build, aggregate coverage, jar inspection and standalone Paper loading | Real provider classloader compatibility and full Velocity/runtime topology |
| MariaDB authority and persistence | Migrations plus broad unit and Testcontainers coverage | Production volume, process-kill recovery and multi-server contention |
| Paper–Velocity protocol and network outbox | Authentication, replay, acknowledgement, reconnect, duplicate and validation-order tests | Multi-backend staging, production certificates, rotation, backpressure and no-player runtime behavior |
| Durable Discord outbox | Lease, retry, fencing and integration scenarios | Complete event routing, circuit/status controls, sanitization and live webhooks |
| Punishment request interfaces | Durable workflow, authority boundaries, queue/review presentation, offline targets and MariaDB tests | PR #27 reconciliation, reload/configuration, notifications, restart, website controls, Bedrock and multi-server staging |
| Provider API packaging | Runtime jars inspected for provider API leaks | Real provider plugins and cross-plugin runtime staging |
| LiteBans shadow comparison | Persisted comparisons and exact import/reconcile/replay/source-deletion lifecycle | PR #37 cutover coordination, realistic data, seven-day shadow, activation and rollback rehearsal |

## Validated root bridge, not complete website

The Velocity website bridge has tested signed loopback transport, request
validation, strict routing, lifecycle, overload input, public projections,
punishment codes and appeal contracts. The actual private site still requires
its own authenticated sessions, CSRF protection, rate limits, media controls,
restricted roles, tests and unpublished staging build.

## Partial and still requiring substantial work

- Clean architecture and responsibility decomposition
- Safe failure, idempotency, fencing, recovery and quarantine
- Operational-mode transitions and dependency-specific degradation
- Modular versioned configuration and complete atomic reload
- Identity, historical names, aliases, Bedrock identity and bounded completion
- Complete case/sanction visibility and combined-sanction behavior
- Escalation families, decay, aliases, removed IDs and frozen policy versions
- Punishment history, precise changes, overturn and appeal-linked decisions
- Request notifications, configuration replacement, offline/restart and multi-server behavior
- Reports GUI, RoseChat evidence, Discord rendering and staff privacy review
- Client evidence and strict pre-broadcast automod
- Inventory and Ender editing under concurrency and offline ownership
- Item and economy confiscation/restoration under failure
- Staff mode, vanish, freeze and staff tools
- Alt confidence, exceptions, inheritance, GUI, alerts and key rotation
- Provider APIs and optional integration degradation
- LiteBans cutover evidence, recovery, emergency freeze and rollback
- Coverage enforcement, mutation tests, load tests and full operational documentation

## Blocked or externally constrained

- Complete RoseChat moderation/staff bridge: the required provider repository/API is unavailable.
- Polar automatic enforcement: no supported violation event contract is available.
- Full acceptance: provider branches, private site, multi-backend topology, Folia,
  Bedrock clients, secrets, production-like data and load/failure environments are incomplete.
- Mandatory 168-hour shadow period: real LiteBans data and the required private
  observation environment have not yet been supplied.

## Current command surfaces

### Paper commands registered in `plugin.yml`

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

Registration proves only that Bukkit can route a command. It does not prove the
complete workflow is staged or production-safe.

### Velocity commands registered at startup

```text
/estaff
/alts
/alt
```

`/alts` and `/alt` are no longer absent from the proxy command surface. The
underlying alt system remains partial: confidence lifecycle, evidence aging,
maintenance suppression, household/approved exceptions, key rotation, GUI,
alerts and complete staging are unfinished.

### Required command gaps

```text
/history
/fakebase
```

Additional recovery, status, namespaced fallback and command-conflict controls
remain incomplete even where a top-level command already exists.

## Immediate development gates

The maintained path is [[Development Blueprint]]. The current queue begins with:

1. Complete PR #37 implementation, full gates and review.
2. Rebase and reconcile PR #27 without losing or duplicating its 95-commit work.
3. Establish the next clean `main` checkpoint.
4. Complete history and appeal-linked decisions.
5. Connect request lifecycle notifications to durable delivery.
6. Finish modular configuration and operational-mode transitions.
7. Complete report UI/privacy and the real RoseChat evidence bridge.
8. Stage staff mode, freeze, vanish, inventory and confiscation safety.
9. Reconstruct providers and complete the private website.
10. Finish migration recovery, full shadow, emergency freeze and rollback.

## Production gates

Do not replace LiteBans or remove old plugins until one coherent release
candidate records all applicable evidence:

1. Java 21 clean build and complete tests
2. Exactly two inspected runtime jars and provider classloader safety
3. Paper–Velocity transport in the real multi-backend topology
4. Network ban/mute, voice mute, chat and server-switch enforcement
5. Restart, reload, reconnect, process-kill and partial-dependency recovery
6. Safe online/offline inventory behavior and concurrent viewers
7. No item/economy duplication, deletion or ambiguous silent success
8. Staff-state, vanish and freeze crash recovery
9. Complete report privacy and evidence capture
10. Alt confidence, inheritance, household exceptions and unread alerts
11. Discord retry, circuit breaker, sanitization, status and manual recovery
12. Complete command ownership, fallbacks and conflict verification
13. Website authentication, appeals, privacy, media and role controls
14. Provider and optional-integration compatibility
15. Java, Bedrock/Geyser, Folia, multi-server, load and failure staging
16. LiteBans count, checksum, identity, active-state, expiration and decision parity
17. Seven valid daily summaries spanning the exact 168-hour non-enforcing shadow period
18. Documented cutover, emergency freeze, rollback and post-cutover reconciliation
19. Codacy, CI, coverage, security and documentation requirements
20. Exact commit, jar hashes, configuration checksums, environment versions, logs and approval record

## Updating this page

When a feature or checkpoint changes:

1. Update its requirements-matrix evidence.
2. Update `WORKSPACE-MANIFEST.md` when the root checkpoint moves.
3. Update [[Development Blueprint]] and affected procedures.
4. Change status only to the highest level actually proven.
5. Record unavailable environments and remaining limitations.
6. Run `python scripts/wiki/validate_wiki.py` before publication.