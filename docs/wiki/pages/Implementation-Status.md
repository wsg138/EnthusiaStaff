# Feature Completion Status

This page answers one question: **how complete is each part of EnthusiaStaff?**

The page starts with four broad groups. Click a group to open its detailed table
of individual features, completion estimates and remaining work.

> **Overall verdict: NOT READY for production authority.** LiteBans and the
> existing production staff stack remain authoritative.

## How the percentages work

These are rounded planning estimates based on implemented behavior, automated
tests, available staging evidence and the unfinished requirements. They are not
automatically calculated and are not a release score.

| Mark | Meaning |
| --- | --- |
| ✅ **100%** | The intended repository feature is implemented and tested. |
| 🟢 **80–99%** | Core behavior is present; focused integration or staging remains. |
| 🟡 **50–79%** | Useful implementation exists, but important behavior is unfinished. |
| 🟠 **20–49%** | Early or partial implementation; substantial work remains. |
| 🔴 **0–19%** | Not started, contract-only or externally blocked. |

## Four feature groups

1. **Core platform and infrastructure — about 72%**
2. **Moderation, punishments and reports — about 56%**
3. **Staff tools, investigations and player-state safety — about 44%**
4. **Integrations, migration and release readiness — about 36%**

The group percentages summarize the tables below. They do not replace the
individual feature percentages.

<details>
<summary><strong>1. Core platform and infrastructure — about 72%</strong></summary>

| Specific feature | Complete | What is working | What remains |
| --- | ---: | --- | --- |
| Paper and Velocity runtime jars | ✅ **100%** | Exactly two Java 21 deployable jars are produced and inspected. | Repeat release-candidate inspection and real provider classloader checks. |
| Dependency packaging and provider API leak prevention | ✅ **100%** | Runtime-jar checks reject duplicated provider-owned API classes. | Verify the same behavior with every real provider plugin installed. |
| Module boundaries and dependency direction | 🟡 **75%** | Domain, persistence, protocol, Paper, Velocity and contracts are separated. | Finish remaining large coordinator/store splits and prevent branch reconciliation from restoring duplicate ownership. |
| Paper startup composition | 🟢 **80%** | Startup responsibilities are split across configuration, storage, commands, integrations, network and lifecycle collaborators. | Finish smaller ownership boundaries and full degraded-startup staging. |
| Paper shutdown and cleanup | 🟡 **70%** | Runtime cleanup, worker shutdown and several durable flush paths exist. | Prove partial-startup shutdown, timeout behavior and process-kill recovery. |
| Velocity startup composition | 🟡 **75%** | Configuration, storage, network workers, Discord and website bridge wiring are separated. | Complete dependency-specific degraded startup and production topology testing. |
| MariaDB schema and migrations | 🟢 **90%** | Migrations cover the current moderation, website, report, outbox and migration models. | Complete final indexes/schema review and upgrade testing against production-sized data. |
| JDBC stores, transactions and prepared statements | 🟢 **85%** | Broad MariaDB tests cover durable stores, rollback and concurrency paths. | Finish remaining large-store decomposition and multi-server/process-kill testing. |
| Revisions, leases, fencing and idempotency | 🟡 **70%** | Major destructive and delivery workflows use revisions, leases, unique IDs and replay-safe transitions. | Apply the same guarantees to every unfinished asset, notification, cutover and provider workflow. |
| Paper–Velocity TLS and authentication | 🟢 **85%** | Persistent authenticated transport, allowlisted identity and envelope validation are implemented and tested. | Prove certificate rotation and the real proxy/backend certificate chain. |
| ReplayGuard and envelope replay protection | 🟢 **90%** | Reused authenticated nonces are rejected within the bounded replay window. | Validate tuning and resource limits under production message rates. |
| Network inbox, outbox, acknowledgements and retry | 🟢 **80%** | Durable delivery, acknowledgement, reconnect and duplicate handling are covered. | Prove multi-backend backpressure, long outages and no-online-player operation. |
| Operational modes | 🟠 **45%** | Operational-state models and several fail-closed command paths exist. | Complete BOOTSTRAP, DEGRADED, SHADOW_MIGRATION, ACTIVE, MAINTENANCE and READ_ONLY_FAILURE transitions. |
| Dependency-specific degraded behavior | 🟠 **40%** | Some unavailable-storage and unavailable-integration paths fail closed. | Define and test the exact feature impact of MariaDB, Velocity, RoseChat, Voice, Currency, Market and Polar failures. |
| Modular configuration layout | 🟠 **30%** | Reason policy and current runtime configuration exist. | Build the required modular files, GUI files, integration files and punishment-category trees. |
| Atomic reload and immutable runtime models | 🟠 **40%** | Reason policies can be validated and atomically replaced. | Validate the entire config tree, preserve all durable sessions/drafts, report restart-only options and reject invalid reloads as one unit. |
| Player directory and UUID-first identity | 🟡 **60%** | UUID authority, persisted directory records and basic offline targets are supported. | Complete previous-name indexing, stale-name reconciliation and provider-backed identity checks. |
| Bedrock `*` aliases and bounded completion | 🟠 **35%** | Floodgate/Geyser integration foundations and offline presentation exist. | Add complete alias lookup, bounded fuzzy matching, ranking and an in-memory completion index with no SQL per keystroke. |
| Runtime health, verification and diagnostics | 🟡 **55%** | Paper/Velocity health models and several readiness messages exist. | Add complete dependency, queue, mode, schema, recovery and degraded-feature status without exposing internal noise to staff. |
| Automated build, MariaDB tests and quality checks | 🟢 **80%** | Clean builds, Testcontainers, jar inspection, coverage, Codacy and Wiki validation run in CI. | Add meaningful enforced coverage floors, mutation tests, load tests and full runtime acceptance. |

</details>

<details>
<summary><strong>2. Moderation, punishments and reports — about 56%</strong></summary>

| Specific feature | Complete | What is working | What remains |
| --- | ---: | --- | --- |
| Case records and audit history | 🟡 **70%** | Durable cases, actors, reasons, visibility and several linked records exist. | Complete every evidence/action link and one readable full case timeline. |
| Sanction records and expiration | 🟡 **75%** | Multiple sanction types, active-state queries and expiration handling exist. | Complete all authoritative sanction types and real network enforcement staging. |
| Combined sanctions | 🟡 **55%** | The model supports cases containing multiple sanctions. | Prove shared start times, independent expirations, partial ending and no unrelated mutation. |
| Public/private punishment visibility | 🟡 **65%** | Public projections and safe public fields exist. | Complete mutation authorization, historical visibility and website/provider consistency. |
| Direct punishment commands | 🟡 **70%** | Ban, mute, warn, kick and IP-ban commands use central services. | Finish all offline, multi-server, failure, reload and conflict behavior. |
| Punishment category and reason GUI | 🟡 **75%** | Category, reason and review presentation foundations exist. | Complete every configured category, Bedrock layout and modular GUI configuration. |
| Punishment review and confirmation | 🟡 **70%** | Recommended action, target context and confirmation paths exist. | Finish every optional sanction control, visibility choice, notes and stale-state revalidation. |
| Durable punishment drafts and resume | 🟢 **85%** | Drafts persist and can survive ordinary interruption. | Complete full crash, logout, server-switch, expiry and configuration-version behavior. |
| Helper temporary punishment authority | 🟢 **85%** | Current in-game services enforce configured temporary Helper actions. | Complete website/provider parity and production-like staging. |
| Helper permanent punishment requests | 🟢 **85%** | Permanent outcomes become durable approval requests instead of direct punishment. | Finish requester notifications, offline delivery, restart behavior and website control. |
| Developer request-only authority | 🟢 **85%** | Developer can prepare requests but cannot directly mutate punishments. | Verify every external adapter, website route and future command preserves that boundary. |
| Request review queue and presentation | 🟢 **80%** | Queue ordering, pagination, empty states and request detail presentation exist. | Complete multi-server live refresh, filters and Bedrock presentation. |
| Request claim, approve and deny | 🟢 **80%** | Authority, self-approval, stale lease and idempotent decision paths are tested. | Reconcile PR #27 and stage contention/restart behavior in the real topology. |
| Request lifecycle notifications | 🟠 **35%** | Notification contracts and some draft-branch delivery work exist. | Deliver submitted, claimed, approved, denied, expired and fulfilled events durably to online/offline staff and Discord. |
| Escalation families and ladder selection | 🟡 **60%** | Stable reason policies and recommendation logic exist. | Complete every family relationship, severity jump and combined recommendation rule. |
| Decay, recency and repeat-offense behavior | 🟠 **45%** | Some escalation timing behavior is represented. | Implement and test the full clean-period decay and post-punishment recency rules. |
| Policy versions, aliases and removed reason IDs | 🟡 **50%** | Policy loading and frozen recommendation foundations exist. | Preserve old IDs, explicit renames, out-of-range ladders and historical display across reloads. |
| Punishment history command | 🟠 **20%** | Underlying case and sanction queries exist. | Register `/history`, add permission-safe views, filters, pagination and complete audit timelines. |
| Exact sanction reduction and ending | 🟠 **45%** | Sanction mutation services and focused tests exist. | Finish duration reduction, immediate ending, retries, combined-sanction safety and notifications. |
| Revocation and removal from escalation | 🟠 **40%** | Removal commands and mutation foundations exist. | Separate end/revoke/remove-contribution semantics and preserve all history. |
| Overturn request workflow | 🟠 **25%** | Approval-request patterns can be reused. | Add durable overturn requests, expiry, unread alerts, Discord notification, approval and denial. |
| Appeal-linked decisions | 🟠 **35%** | Appeal contracts and website bridge foundations exist. | Link appeal decisions to audited sanction changes, role controls and notifications. |
| Report submission and validation | 🟡 **70%** | Known/offline targets, no-self-report, persistence and basic validation exist. | Complete all configurable reasons, user feedback and real client/server context. |
| Report cooldowns and duplicate merging | 🟡 **65%** | Submission replay, semantic conflict and merge foundations are tested. | Finish all configured cooldown limits and production-volume behavior. |
| Report queues, claim and close state | 🟡 **70%** | Queue queries, revisions, claim and close transitions are durable. | Build complete staff GUI actions, live refresh and multi-server staging. |
| Public-chat evidence capture | 🟡 **70%** | Bounded chat context and report snapshots exist. | Verify RoseChat ordering, formatting, retention and cross-server context. |
| Private-message evidence capture | 🟠 **25%** | Privacy rules and storage boundaries are documented. | Implement the supported RoseChat bridge and ensure private evidence never reaches Discord. |
| Report evidence privacy and retention | 🟡 **65%** | Read-time filtering, physical retention and maintenance paths are tested. | Complete staff privacy review, operator controls and production retention verification. |
| Report queue/detail GUI | 🟠 **20%** | Commands and query models exist. | Build Open, Mine, Claimed, Review and Recent sections plus detail actions and Bedrock layouts. |
| Strict pre-broadcast automod | 🟠 **30%** | Strict normalized variant matching exists. | Integrate before RoseChat broadcast, create durable case/evidence/audit/Discord output and prove false-positive resistance. |
| Client evidence snapshots | 🟠 **40%** | Evidence models and Via/Floodgate/AutoClicker contracts exist. | Reconstruct providers, bound retention, add explicit staff capture and verify unknown/unavailable behavior. |

</details>

<details>
<summary><strong>3. Staff tools, investigations and player-state safety — about 44%</strong></summary>

| Specific feature | Complete | What is working | What remains |
| --- | ---: | --- | --- |
| Staff-mode entry snapshot | 🟡 **55%** | Persistent sessions, access policy and state-codec foundations exist. | Capture and verify every required inventory, attribute, effect, location, mode and metadata field before clearing state. |
| Staff-mode rank profiles | 🟡 **65%** | Helper/Mod/Developer/Admin/Founder restrictions are represented. | Enforce every accidental vanilla/plugin bypass during an active session. |
| Staff-mode tools and inventory | 🟠 **45%** | Some configured staff inventory and controls exist. | Finish exact hotbar profiles, no unauthorized item movement and no item leakage. |
| Staff-mode exit and restore | 🟡 **50%** | Restore foundations and persistent session records exist. | Complete exact state/location/server restore, verification and safe fallback/quarantine. |
| Staff-mode crash and reconnect recovery | 🟠 **40%** | Durable session state can survive restart. | Resume sessions safely, preserve original snapshots and prove disable/crash/process-kill recovery. |
| CombatLogX and combat-state integration | 🟠 **30%** | Integration boundary is identified. | Block unsafe entry and ensure staff-mode/vanished players cannot create or receive combat tags. |
| Freeze persistence and commands | 🟡 **60%** | Freeze/unfreeze commands and durable state foundations exist. | Reconcile PR #27 and complete restart/reconnect/offline-extension behavior. |
| Freeze movement and world restrictions | 🟠 **45%** | Several movement and interaction restrictions exist. | Cover damage, containers, items, blocks, teleport, backend switching and every command exception. |
| Freeze inventory and GUI restrictions | 🟠 **40%** | Listener foundations exist. | Close GUIs and block every inventory/drop/pickup/use bypass across Java and Bedrock. |
| Freeze staff-only communication | 🟠 **25%** | Required behavior is defined. | Route frozen-player chat only to self and staff without revealing the filtering. |
| Vanish persistent state and toggle | 🟡 **70%** | State storage, toggle flow, join/quit suppression and refresh exist. | Complete recovery/transition edge cases and all configuration migration behavior. |
| Rank-aware vanish visibility matrix | 🟡 **75%** | Viewer-target visibility decisions and supervising-rank rules exist. | Verify every rank migration and live permission/rank change. |
| Paper hide/show visibility layer | 🟢 **80%** | Paper visibility calls update viewer-target pairs. | Prove large-player-count incremental performance and all reconnect cases. |
| ProtocolLib spectator tab masking | 🟡 **55%** | Packet masking and fail-closed foundations exist. | Preserve every player-info field, cover all protocol versions and stage Java/Bedrock behavior. |
| Vanished entity spawn/tracking suppression | 🟡 **50%** | Packet/entity-owner coordination foundations exist. | Cover metadata, equipment, tracker resends, server switches and spectator-detection clients. |
| Vanish command/completion/integration hiding | 🟠 **30%** | Central visibility intent and known gaps are documented. | Hide `/seen`, teleport, message, pay, playtime, RoseChat, voice and public-API exposure. |
| Vanish sounds, particles and container effects | 🟠 **20%** | Required coverage is identified. | Suppress unauthorized sound, particles, lid animation and related side channels. |
| Online inventory and Ender viewing | 🟡 **60%** | Inventory images and command/controller foundations exist. | Complete all armor/offhand/container views and real target synchronization. |
| Online inventory editing | 🟡 **50%** | Operation context and mutation foundations exist. | Enforce exact main-thread dirty-slot updates, audit and viewer synchronization. |
| Multiple concurrent staff viewers | 🟠 **40%** | Coordinator concepts exist. | Prove one target coordinator, revision conflicts, synchronized viewers and safe close behavior. |
| Nested shulker and bundle editing | 🟠 **35%** | Path/fingerprint foundations exist. | Complete nested mutation, stale fingerprint rejection and Bedrock-safe presentation. |
| Offline inventory/Ender editing | 🟠 **40%** | Lease, ownership, image and journal foundations exist. | Prove network-wide offline state, owner scope, active-save rejection and atomic file replacement. |
| Queued inventory patches | 🟡 **55%** | Pending patch and transition foundations exist. | Apply before interaction, handle conflicts/quarantine and verify retention/cleanup. |
| Inventory crash recovery and quarantine | 🟠 **35%** | Journals and recovery states exist. | Run interruption at every write stage and provide complete operator resolution. |
| Item confiscation selection and snapshots | 🟡 **55%** | Case-linked snapshots, lifecycle states and exact paths exist. | Finish full-container selection, stale reselection and movement-lock coverage. |
| Item confiscation commit and recovery | 🟠 **45%** | Durable journal foundations exist. | Complete crash recovery, verification, rollback/quarantine and live server-switch behavior. |
| Item restoration | 🟡 **60%** | Idempotent case-linked restoration foundations exist. | Prove no duplicates/loss under changed inventories, nested containers and retries. |
| Economy removal planning | 🟠 **45%** | Exact plan, codec and rollback-integrity foundations exist. | Reconstruct Currency snapshots, configurable order and all personal-balance sources. |
| Economy restoration and conflict handling | 🟠 **40%** | Journal and rollback concepts exist. | Implement provider replay/conflict states, exact after-verification and quarantine recovery. |
| Alt relationship and evidence storage | 🟡 **50%** | Relationship, evidence and identity-token persistence foundations exist. | Complete full lifecycle, maintenance suppression and production-like data review. |
| Alt confidence aging and network change | 🟠 **35%** | Confidence states are defined. | Implement evidence weighting, simultaneous-play reduction and multi-session network-change decay. |
| Approved-alt and household exceptions | 🟠 **30%** | Required states and command surface are defined. | Implement durable approval, household, not-related, reopen and inheritance suppression. |
| Alt sanction inheritance | 🟠 **40%** | Inheritance rules and domain foundations exist. | Apply exact remaining ban/mute state idempotently with original-case links and alerts. |
| Alt GUI and unread alerts | 🟠 **25%** | `/alts` and `/alt` are registered. | Build relationship/evidence GUI, action controls, unread alerts and Bedrock presentation. |
| Sensitive identity encryption and key rotation | 🟠 **30%** | HMAC/token and sensitive-data boundaries exist. | Finish recoverable encryption, no-raw-address guarantees and tested key rotation. |
| Player inspector and `/client` | 🟠 **45%** | Inspection and client-information foundations exist. | Combine all required identity, punishment, report, alt, provider and action views. |
| Staff hotbar and tools menu | 🟠 **40%** | Some staff tools and slot concepts exist. | Finish all nine slots, permissions, state restoration and safe interactions. |
| Cheat testers | 🟠 **30%** | Tester concepts and some foundations exist. | Implement Totem, No-fall, Velocity and Auto-armor with exact journaled restore and no automatic punishment. |
| Fake entity | 🟠 **25%** | Feature requirements and packet foundations exist. | Build target/staff-only behavior, evidence capture, cleanup and Java/Bedrock testing. |
| Fake base and `/fakebase` | 🔴 **10%** | Requirements are defined. | Implement virtual blocks/schematic display, isolation, cleanup, warning, extend and teleport controls without real world changes. |

</details>

<details>
<summary><strong>4. Integrations, migration and release readiness — about 36%</strong></summary>

| Specific feature | Complete | What is working | What remains |
| --- | ---: | --- | --- |
| Durable Discord outbox | 🟡 **70%** | Leasing, retry, fencing and persistence foundations exist. | Finish complete event coverage and production outage testing. |
| Discord event routing and rendering | 🟠 **40%** | Webhook categories and worker delivery exist. | Route every required punishment/report/staff/alert event with correct privacy and formatting. |
| Discord sanitization and mention safety | 🟠 **45%** | Producer-side sanitization rules and disabled automatic mentions are documented. | Enforce sanitization consistently at every producer and add hostile-content tests. |
| Discord circuit breaker, status and manual recovery | 🟠 **30%** | Retry/backoff foundations exist. | Add open/half-open state, operator status, dead-letter recovery and live webhook staging. |
| Velocity website bridge | 🟡 **65%** | Signed restricted transport, routing, lifecycle and validation foundations exist. | Complete production authentication boundaries, overload behavior and site integration. |
| Public punishment projections | 🟡 **70%** | Safe public fields, pagination, lookup and expiration behavior are tested. | Complete every sanction/public-visibility rule and real site rendering. |
| Punishment access codes | 🟡 **70%** | Durable generation, claim and rollback/conflict tests exist. | Complete operator controls, expiry policy and site flow. |
| Appeal API and storage contracts | 🟡 **60%** | Appeal submission/query foundations and failure contracts exist. | Add authenticated sessions, decisions, reopening, notifications and full role enforcement. |
| Private punishment/appeal website | 🔴 **15%** | Root bridge and contracts exist. | Build the private site, sessions, CSRF, rate limits, restricted staff roles, media controls and tests. |
| EnthusiaCurrency moderation provider | 🟠 **25%** | Contracts and gateway expectations exist. | Rebuild durable snapshots, exact plans, replay/conflicts, offline operation and restoration. |
| EnthusiaCommend provider | 🟠 **20%** | Required blacklist contract is defined. | Implement persistence and enforce it across GUI, command and API write paths. |
| EnthusiaAutoClicker provider | 🟠 **25%** | Versioned evidence contract exists. | Rebuild handshake/version/evidence storage, bounded lookup and unknown/unavailable states. |
| RoseChat moderation/staff provider | 🔴 **10%** | Required capabilities and privacy rules are defined. | Obtain or define a supported API for pre-broadcast automod, PM evidence, staff channels, mute/freeze and vanish-aware recipients. |
| EnthusiaMarket moderation provider | 🟠 **20%** | Moderation contracts and expected transaction boundary exist. | Implement supported stall actions, review scheduling and restoration without raw bypasses. |
| Provider classloader and degraded-mode compatibility | 🟠 **20%** | Runtime jars are checked for provider API leaks. | Install every provider together, verify service discovery and prove isolated optional failure. |
| LiteBans schema inspection and blockers | 🟢 **85%** | Deterministic aliases, required columns and explicit blocker reporting are tested. | Validate additional real production variants and operator presentation. |
| LiteBans import, mapping and reconciliation | 🟡 **75%** | Import/reconcile/replay/source-deletion lifecycle paths have MariaDB coverage. | Complete production-volume behavior, interruption at every stage and orphan/conflict controls. |
| LiteBans shadow comparison dimensions | 🟡 **75%** | Counts, checksums, active state, UUIDs, expirations and enforcement decisions can be persisted and compared. | Run continuous real-data comparisons and operator mismatch workflows. |
| Cutover coordination and writer fencing | 🟡 **50%** | PR #37 contains active transition/freeze coordination work. | Finish, merge and validate activation fencing, duplicate activation rejection and auditable state transitions. |
| Migration interruption, resume and rollback | 🟠 **40%** | Replay and reconciliation foundations exist. | Prove process interruption, restart resume, rollback, emergency freeze and ambiguous-outcome quarantine. |
| Real LiteBans data rehearsal | 🟠 **20%** | Test schemas and synthetic scenarios exist. | Run dry-run/rerun/final-incremental import against private production-like data. |
| Mandatory 168-hour shadow period | 🔴 **0%** | Required comparison dimensions exist. | Produce seven valid daily summaries spanning at least 168 hours and resolve every mismatch. |
| Full Paper–Velocity multi-backend staging | 🟠 **20%** | Standalone Paper boot/restart staging exists. | Run HUB/SMP/Velocity topology, server switching, network enforcement and no-player transport. |
| Java client acceptance | 🟡 **55%** | Paper-side commands and selected runtime paths have standalone staging. | Run complete staff, punishment, report, inventory, vanish and recovery acceptance on supported versions. |
| Bedrock/Geyser acceptance | 🔴 **10%** | Floodgate/Geyser integration foundations exist. | Test every GUI, identity alias, packet presentation, fake system and staff workflow with real Bedrock clients. |
| Folia ownership and scheduling | 🔴 **10%** | Some entity-owner scheduling improvements exist. | Stage all player/entity mutations and recovery paths on Folia-compatible ownership rules. |
| Load, saturation and backpressure | 🟠 **20%** | Bounded queues/executors and some overload validation exist. | Test DB pools, network/Discord queues, reconnect storms, GUI/report load and circuit behavior. |
| Process-kill and destructive failure injection | 🟠 **20%** | Focused rollback/failure tests exist. | Kill processes during every asset, punishment, migration and notification stage and verify recovery. |
| Release manifest and cross-repository evidence | 🟠 **20%** | The required manifest model is documented. | Produce one signed manifest with exact revisions, hashes, configs, environment versions and combined acceptance. |
| Installation, upgrade and rollback operations | 🟡 **50%** | Installation, migration, shadow and recovery documentation exists. | Validate clean install, upgrade, downgrade/rollback, backup restore and operator drills on the final manifest. |
| Production authorization and legacy retirement | 🔴 **0%** | Authority and safety requirements are documented. | Complete every release gate, receive explicit authorization, observe production and retire legacy plugins only as a later manual decision. |

</details>

## Current command gaps

The following required top-level commands are not registered:

```text
/history
/fakebase
```

`/alts` and `/alt` are registered on Velocity, but the detailed table shows the
underlying alt features are still incomplete.

## External blockers

- The supported RoseChat provider repository/API is unavailable.
- Polar does not expose the supported violation event required for automatic
  enforcement.
- Full provider branches, the private site, production-like data, Bedrock/Folia
  clients and the complete multi-server failure environment are unavailable.
- The real-data 168-hour LiteBans shadow observation has not run.

## Updating this page

1. Update `reports/REQUIREMENTS-MATRIX.md` with exact evidence first.
2. Change an estimate only when meaningful behavior, tests or staging changed.
3. Keep percentages in rounded five-point increments except 0% and 100%.
4. Update [[Remaining Development Map|Development-Blueprint]] when the four
   unfinished groups or their order changes.
5. Keep source paths in [[Developer Code Guide]] and test procedures in
   [[Build and Testing]] instead of repeating them here.
