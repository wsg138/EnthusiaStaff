# Feature Completion Status

This page answers one question: **how complete is each major EnthusiaStaff feature?**

Use [[Remaining Development Map|Development-Blueprint]] for the grouped map of
unfinished work. Use `reports/REQUIREMENTS-MATRIX.md` for exact files, tests,
blockers and evidence.

> **Overall verdict: NOT READY for production authority.** LiteBans and the
> existing production staff stack remain authoritative.

## How to read the percentages

The percentages are rounded planning estimates based on the current code,
automated tests, staging evidence and remaining requirements. They are not
calculated automatically and should not be treated as a release score.

| Mark | Meaning |
| --- | --- |
| ✅ **Complete** | The feature's intended repository scope is implemented and tested. |
| 🟢 **80–99%** | Core behavior is present; focused integration or staging work remains. |
| 🟡 **50–79%** | Useful foundations exist, but important behavior is unfinished. |
| 🟠 **20–49%** | Early or partial implementation; substantial work remains. |
| 🔴 **0–19%** | Not started, only represented by contracts, or externally blocked. |

A feature can be highly complete without being approved for production. Production
approval also requires the combined provider, topology, migration, security,
load, recovery and operational gates.

## Platform foundation

| Feature | Status | What is already present | What still needs to be done |
| --- | ---: | --- | --- |
| Two Java 21 runtime jars and dependency packaging | ✅ **Complete** | Paper and Velocity runtime jars build and are inspected; provider API leak checks pass. | Repeat packaging checks for release candidates and verify real provider classloaders. |
| Clean architecture and module ownership | 🟡 **75%** | Domain, persistence, protocol, Paper, Velocity and integration contracts are separated; several large startup and persistence responsibilities were split. | Finish remaining coordinator/JDBC decomposition and reconcile long-lived branches without restoring duplicate ownership. |
| MariaDB schema and durable persistence | 🟢 **90%** | Migrations, prepared statements, stores, transactions, leases, journals, inboxes and outboxes have broad MariaDB tests. | Add production-volume, process-kill and multi-server contention testing; finish resource/index review. |
| Paper–Velocity authenticated network channel | 🟢 **85%** | Persistent authenticated transport, replay protection, acknowledgements, retries and durable outbox behavior are tested. | Prove real multi-backend operation, certificate rotation, backpressure and no-player transport. |
| Operational modes and degraded behavior | 🟠 **45%** | Operational-state types and several fail-closed command paths exist. | Complete all six modes, transition rules, dependency-specific feature gates, emergency freeze and runtime verification. |
| Modular configuration and atomic reload | 🟠 **35%** | Reason-policy loading, validation and atomic policy replacement exist. | Build the required file tree, GUI configuration, aliases, complete cross-file validation, restart-required reporting and full state-preserving reload. |
| Identity, previous names, Bedrock aliases and offline lookup | 🟡 **50%** | UUID-first lookup, directory persistence and basic offline target presentation exist. | Add complete historical-name and `*` alias lookup, bounded fuzzy matching, ranking, cache refresh and no-SQL-per-keystroke completion. |

## Moderation and punishment features

| Feature | Status | What is already present | What still needs to be done |
| --- | ---: | --- | --- |
| Case and sanction core | 🟡 **70%** | Durable cases, sanctions, visibility, public projections, expiration and several sanction types exist. | Complete combined-sanction behavior, visibility mutation authorization, full history integration and provider/site validation. |
| Punishment GUI, durable drafts and approval requests | 🟢 **80%** | Category/reason/review flows, durable drafts, Helper permanent requests, Developer request-only behavior, queues and decisions are tested. | Reconcile PR #27, finish lifecycle notifications, modular GUI config, reconnect/restart behavior and Bedrock/multi-server staging. |
| Escalation ladders, families, decay and policy versions | 🟡 **55%** | Stable policy IDs, recommendation logic, frozen recommendations and loader tests exist. | Finish the authoritative escalation formula, decay/recency, aliases, removed IDs, finite ladders and combined recommendations. |
| Rank authority and approval boundaries | 🟢 **80%** | Helper, Mod, Developer, Admin and Founder boundaries are enforced in central services for current in-game flows. | Complete website/provider enforcement, offline alerts, notification delivery and production-like multi-server verification. |
| History, reduction, ending, revocation and overturns | 🟠 **40%** | Sanction mutation services and removal commands have focused tests. | Add `/history`, precise reduction/end/revoke/remove behavior, overturn requests, appeal-linked decisions, complete notifications and end-to-end staging. |
| Reports, evidence, queues and privacy | 🟡 **65%** | Submission, replay, conflict handling, queues, state transitions, chat context, privacy filtering and retention are persisted and tested. | Build the report GUI/config, RoseChat private-message bridge, Discord rendering, staff privacy review and real multi-server staging. |
| Client evidence and strict automod | 🟠 **35%** | Evidence models, integration contracts and strict variant matching exist. | Rebuild the AutoClicker and RoseChat adapters, prove pre-broadcast cancellation, bounded evidence, reload, audit/Discord creation and false-positive resistance. |

## Staff, investigation and asset-safety features

| Feature | Status | What is already present | What still needs to be done |
| --- | ---: | --- | --- |
| Online/offline inventory and Ender editing | 🟡 **50%** | Inventory images, operation journals, revisions, leases and queued-patch foundations exist. | Complete concurrent viewers, dirty-slot synchronization, nested containers, offline atomic replacement, ownership/save races and recovery staging. |
| Item confiscation and restoration | 🟡 **55%** | Case-linked snapshots, lifecycle states, journals and idempotent restoration foundations exist. | Finish nested selection, movement bypass protection, stale selection handling, crash recovery, quarantine resolution and live Paper testing. |
| Economy confiscation and restoration | 🟠 **45%** | Economy plans, journals, codecs and rollback-integrity tests exist. | Reconstruct the Currency API, exact provider snapshots, replay/conflict handling, custom removal order and provider staging. |
| Staff mode | 🟡 **50%** | Rank access policy, persistent session storage and snapshot/restore foundations exist. | Reconcile PR #27, complete checksums/revisions, crash resume, cross-server location restore, CombatLogX handling and item-leak prevention. |
| Vanish and spectator masking | 🟡 **60%** | Rank-aware audience coordination, reconnect fencing, fail-closed behavior, entity-owner scheduling and packet/Paper layers exist. | Cover every command/integration exposure, sounds/effects/containers/voice, full spectator presentation, Java/Bedrock/Folia staging and performance verification. |
| Freeze | 🟡 **50%** | Freeze commands, persistence and several restrictions exist; PR #27 contains additional lifecycle work. | Reconcile PR #27 and complete every interaction restriction, staff-only chat, backend-switch prevention, reconnect/offline expiry and restart staging. |
| Staff tools, cheat testers and fake systems | 🟠 **30%** | Some staff hotbar, inspection and test foundations exist. | Finish the full hotbar lifecycle, safe tester journals/restoration, fake entity behavior, virtual fake base and `/fakebase`, including Bedrock testing. |
| Alt detection, confidence and sanction inheritance | 🟠 **40%** | `/alts` and `/alt` are registered; relationship, evidence and identity-token foundations exist. | Complete confidence aging, maintenance suppression, exceptions, inheritance, aliases, GUI, unread alerts, encryption/key rotation and real-data staging. |

## Integrations, migration and release readiness

| Feature | Status | What is already present | What still needs to be done |
| --- | ---: | --- | --- |
| Discord delivery and alerts | 🟠 **45%** | Durable outbox, leasing, retry and fencing foundations exist. | Route all required events, finish sanitization, circuit/status controls, manual recovery and live webhook verification. |
| Punishment and appeal website | 🟠 **35%** | Signed restricted Velocity transport, public projections, punishment codes and appeal contracts exist. | Build the private site with authenticated sessions, CSRF, rate limits, restricted roles, safe media and integration/staging tests. |
| Related plugin APIs and adapters | 🟠 **25%** | EnthusiaStaff contracts exist for Currency, Commend, AutoClicker, RoseChat and Market. | Reconstruct provider implementations, enforce every write path, document degraded behavior and run cross-plugin classloader/runtime staging. |
| LiteBans migration, shadow comparison and cutover | 🟡 **55%** | Schema inspection, import/reconcile/replay/source-deletion paths and persisted comparison dimensions are tested. | Complete PR #37, interruption/resume/rollback, writer fencing, realistic data rehearsal, seven daily summaries over 168 hours and final cutover recovery. |
| Verification, staging and operational readiness | 🟡 **50%** | Clean builds, broad tests, coverage reporting, Codacy, Wiki validation and standalone Pi staging exist. | Add full Paper/Velocity topology, providers, Java/Bedrock/Folia, load, process-kill, upgrade/recovery and release-manifest acceptance. |

## Current command gaps

The following required top-level commands are not yet registered:

```text
/history
/fakebase
```

`/alts` and `/alt` are registered on Velocity, but their underlying feature is
still only about 40% complete.

## External blockers

- The supported RoseChat provider repository/API is unavailable.
- Polar does not expose the required supported violation-event contract for
  automatic enforcement.
- Full provider branches, the private site, production-like data, Bedrock/Folia
  clients and the complete multi-server failure environment are unavailable.
- The required real-data 168-hour LiteBans shadow observation has not run.

## Updating the estimates

When implementation changes:

1. Update `reports/REQUIREMENTS-MATRIX.md` with exact evidence first.
2. Adjust the percentage only when a meaningful requirement group is added,
   completed, invalidated or staged.
3. Update [[Remaining Development Map|Development-Blueprint]] when the unfinished
   section list changes.
4. Keep test procedures in [[Build and Testing]] and code ownership in
   [[Developer Code Guide]] rather than repeating them here.
