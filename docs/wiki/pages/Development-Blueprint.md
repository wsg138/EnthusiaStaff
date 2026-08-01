# Remaining Development Map

This page groups all unfinished development into four sections. Click a section
to open its detailed work table.

- Percentages belong in [[Feature Completion Status|Implementation-Status]].
- Source ownership belongs in [[Developer Code Guide]].
- Validation procedures belong in [[Build and Testing]].
- Migration operator steps belong in [[LiteBans Migration]] and
  [[Shadow Mode and Cutover]].

## Four remaining-development groups

1. **Core platform and infrastructure**
2. **Moderation, punishments and reports**
3. **Staff tools, investigations and player-state safety**
4. **Integrations, migration and release readiness**

<details>
<summary><strong>1. Core platform and infrastructure</strong></summary>

| Unfinished area | Next development work | Section completion requirement |
| --- | --- | --- |
| Architecture ownership | Finish remaining coordinator/JDBC splits and reconcile overlapping branches without duplicate sources of truth. | Every major lifecycle, persistence and runtime responsibility has one clear owner. |
| Startup and shutdown | Complete degraded startup, partial-startup cleanup, bounded shutdown and process-kill recovery. | Paper and Velocity start, degrade and stop safely under missing or failed dependencies. |
| MariaDB durability | Finish index/resource review and add production-volume, process-kill and multi-server contention tests. | Durable state remains correct under concurrency, restart and interruption. |
| Paper–Velocity topology | Stage real certificates, rotation, multiple backends, no-player transport, backpressure and long outages. | The authenticated channel remains reliable across the actual network topology. |
| Operational modes | Complete all six modes, legal transitions, emergency freeze and dependency-specific feature gates. | Every write/read capability has a clear allowed or blocked state in every mode. |
| Modular configuration | Build the required file tree, GUI files, aliases, versioning and restart-only reporting. | Configuration is organized, understandable and complete for every feature. |
| Atomic reload | Validate all files/cross-references as one tree and preserve active durable state. | Invalid reloads change nothing; valid reloads swap one immutable model atomically. |
| Identity and completion | Complete previous names, Bedrock aliases, fuzzy matching, ranking and in-memory completion. | All supported targets resolve safely without SQL per keystroke. |
| Health and verification | Add operator-readable mode, dependency, queue, schema, recovery and degraded-feature status. | Operators can identify unsafe or unavailable capabilities without reading internal implementation noise. |
| Quality enforcement | Add meaningful coverage floors, mutation tests, load tests and broader runtime acceptance. | CI detects regressions beyond ordinary unit/integration coverage. |

</details>

<details>
<summary><strong>2. Moderation, punishments and reports</strong></summary>

| Unfinished area | Next development work | Section completion requirement |
| --- | --- | --- |
| PR #27 reconciliation | Preserve punishment-request notification work while aligning it to current `main`. | No lifecycle behavior is lost, duplicated or owned by parallel implementations. |
| Complete case timelines | Add `/history` and readable permission-safe case/sanction/audit timelines. | Staff can understand every action and state change from one history view. |
| Combined sanctions | Prove independent expirations, partial changes and no unrelated mutation. | Multi-sanction cases remain consistent through every change and retry. |
| Precise sanction changes | Finish reduction, ending, revocation, removal and contribution-removal semantics. | Every command changes only the selected sanction and preserves history. |
| Overturn workflow | Add durable request, expiry, approval/denial, unread alerts and Discord notification. | Full overturns require the correct authority and remain restart/idempotency safe. |
| Appeal decisions | Connect website/staff decisions to the central sanction-change and audit path. | Appeals cannot bypass authority, history, notification or consistency rules. |
| Request notifications | Deliver submitted, claimed, approved, denied, expired and fulfilled events durably. | Online/offline requester and approver delivery survives duplicate/restart conditions. |
| Escalation rules | Complete families, severity jumps, decay, recency and combined recommendations. | Recommendations match the authoritative rules for every configured reason. |
| Policy compatibility | Preserve versions, aliases, removed IDs, finite ladders and historical display. | Reload/config changes never corrupt or reinterpret old cases. |
| Punishment GUI/config | Finish every category/reason/review control and modular GUI configuration. | Java and Bedrock staff can complete every permitted punishment workflow. |
| Report GUI | Build queue sections, detail actions, refresh and Bedrock-safe presentation. | Staff can investigate and close reports without command-only gaps. |
| Report cooldowns and merge | Finish every configured limit and production-volume behavior. | Duplicate/spam reports are handled predictably without losing evidence. |
| RoseChat evidence | Implement supported public/private-message capture and ordering. | Relevant evidence is captured while private messages never reach Discord. |
| Evidence privacy and retention | Complete staff privacy review, controls and production retention validation. | Only authorized staff see retained evidence for the intended duration. |
| Strict automod | Integrate before broadcast and create case/evidence/audit/Discord output. | High-confidence matches are blocked before recipients with tested false-positive resistance. |
| Client evidence | Reconstruct providers and complete bounded capture/lookup behavior. | Staff see accurate point-in-time evidence with safe unknown/unavailable states. |

</details>

<details>
<summary><strong>3. Staff tools, investigations and player-state safety</strong></summary>

| Unfinished area | Next development work | Section completion requirement |
| --- | --- | --- |
| Staff-mode snapshot | Capture and verify every required state field before clearing player state. | Entry cannot lose normal inventory, location, attributes or metadata. |
| Staff-mode restore | Finish exact cross-server restore, verification and quarantine fallback. | Exit restores the original state exactly or visibly enters safe recovery. |
| Staff-mode recovery | Reconcile PR #27 and prove reconnect, restart, disable and process-kill resume. | Staff items never leak and the original snapshot is never replaced. |
| Rank-profile enforcement | Block every vanilla/plugin bypass that exceeds the active rank profile. | Helper/Mod/Developer/Admin/Founder restrictions remain authoritative at runtime. |
| Freeze restrictions | Cover movement, damage, inventory, items, blocks, GUIs, teleport, commands and backend switches. | A frozen player has no unhandled interaction bypass. |
| Freeze communication/recovery | Implement staff-only chat, reconnect, offline expiry/extension and restart behavior. | Freeze state and communication remain correct without revealing staff-only routing. |
| Vanish tab and spectator presentation | Complete ProtocolLib masking and every supported protocol field/version. | Unauthorized clients cannot identify actual spectator or vanished state. |
| Vanish entity/integration hiding | Cover tracker packets, metadata/equipment, commands, chat, voice, effects, containers and public APIs. | No unsupported side channel reveals vanished staff to unauthorized users. |
| Vanish performance | Prove incremental updates under real player counts and reconnect storms. | Visibility reconciliation does not cause unsafe O(N²) routine work. |
| Online inventory editing | Finish main-thread dirty-slot updates, synchronization and audit. | Multiple viewers cannot overwrite or desynchronize newer player state. |
| Offline inventory editing | Prove owner scope, save-state checks, leases and atomic replacement. | Offline changes cannot race login/save or target the wrong server scope. |
| Nested containers | Complete shulker/bundle paths, fingerprints and stale-selection rejection. | Nested changes remain exact and retry-safe. |
| Queued patches and recovery | Apply before interaction and complete conflict/quarantine handling. | Deferred edits cannot silently overwrite newer data. |
| Item confiscation | Finish selection locks, commit verification, interruption recovery and restoration. | No item can be duplicated, lost or ambiguously removed. |
| Economy confiscation | Reconstruct Currency snapshots/plans and finish rollback/restoration conflicts. | Balance removal/restoration is exact, idempotent and provider-authoritative. |
| Alt confidence lifecycle | Implement evidence weighting, aging, maintenance suppression and network-change rules. | Confidence changes are explainable and stable over real session history. |
| Alt exceptions/inheritance | Complete approved, household, not-related, reopen and exact sanction inheritance. | Legitimate shared networks avoid inheritance while real evasion remains linked. |
| Alt GUI and alerts | Build evidence/action views, unread alerts and Bedrock presentation. | Staff can investigate and resolve alt relationships without raw network data. |
| Sensitive identity protection | Finish encryption, key rotation and no-raw-address verification. | Raw addresses never appear in logs, GUI, Discord, site or APIs. |
| Staff tools and inspector | Finish hotbar, tools menu and complete player-inspector data/actions. | Every tool respects rank, mode, vanish and state-restoration rules. |
| Cheat testers | Implement journaled Totem, No-fall, Velocity and Auto-armor tests. | Tests restore exact state and produce evidence without automatic punishment. |
| Fake entity | Implement target/staff-only spawn, evidence capture and cleanup. | Fake entities never persist or affect unrelated clients/world state. |
| Fake base | Add `/fakebase`, virtual blocks, isolation, warning, extend and cleanup. | The fake base is visible only to intended clients and never changes real blocks. |

</details>

<details>
<summary><strong>4. Integrations, migration and release readiness</strong></summary>

| Unfinished area | Next development work | Section completion requirement |
| --- | --- | --- |
| Discord routing | Route every punishment, report, staff and alert event with correct rendering/privacy. | All required events reach the right webhook without leaking sensitive data. |
| Discord failure handling | Add circuit status, dead-letter/manual recovery and live outage tests. | Long webhook failures remain bounded, visible and recoverable. |
| Private website | Build authenticated sessions, CSRF, rate limits, roles, media controls and tests. | Public and staff site actions enforce the same authority/privacy rules as the plugin. |
| Appeal/site integration | Finish decisions, reopening, notifications and end-to-end staging. | Website actions cannot bypass central case/sanction services. |
| Currency provider | Rebuild snapshots, exact plans, replay/conflicts and restoration. | Economy workflows use the supported API and never raw database writes. |
| Commend provider | Implement persistent blacklist enforcement across all write paths. | Blacklisted players cannot give reputation through any surface. |
| AutoClicker provider | Rebuild versioned handshake/evidence lookup and bounded retention. | Client evidence is accurate, versioned and safely unavailable when unsupported. |
| RoseChat provider | Obtain/define supported moderation, PM, staff-channel, mute/freeze and vanish APIs. | Chat-dependent features work without private-data or visibility leaks. |
| Market provider | Implement supported stall review/removal/restoration. | Moderation never bypasses Market's transaction model. |
| Provider compatibility | Stage all providers together and test isolated degraded behavior. | One missing provider disables only its dependent capabilities. |
| LiteBans cutover coordination | Complete PR #37, writer fencing, duplicate activation rejection and emergency freeze. | Only one authority can write at a time and every transition is audited. |
| Migration recovery | Prove interruption, resume, replay, reconciliation, orphans and rollback. | Every interrupted or ambiguous migration reaches a known recoverable state. |
| Real-data rehearsal | Run production-like dry run, rerun and final incremental import. | Counts, mappings, active state and decisions reconcile at realistic scale. |
| Multi-backend staging | Run Velocity, HUB and SMP with no-player transport and server switching. | Network punishments and staff state remain consistent across the topology. |
| Java/Bedrock/Folia acceptance | Execute complete workflows on supported Java, Geyser and Folia environments. | Platform-specific clients/schedulers do not expose or corrupt behavior. |
| Load and process-kill tests | Saturate DB/network/Discord queues and kill processes during destructive work. | Bounded recovery works without duplicate, loss or silent success. |
| Release manifest | Declare every repository revision, artifact hash, config and environment version. | All acceptance evidence belongs to one reproducible cross-repository candidate. |
| 168-hour shadow | Produce seven valid daily comparisons and resolve every mismatch. | The complete shadow record has no unexplained parity or enforcement difference. |
| Rollback rehearsal | Practice backup restore, emergency freeze and authority reversal. | Operators can return safely to the previous authoritative stack. |
| Production authorization | Complete all gates and record explicit approval before changing authority. | EnthusiaStaff becomes authoritative only through the approved release process. |

</details>

## Current development order

1. Finish PR #37 and reconcile PR #27.
2. Complete core mode/configuration/recovery gaps needed by other features.
3. Complete moderation history, decisions, reports and notifications.
4. Complete staff-state, inventory, asset and investigation safety.
5. Reconstruct providers, Discord and the private website.
6. Run migration, topology, platform, load and failure acceptance.
7. Complete the 168-hour shadow evidence and rollback rehearsal.

A serious correctness, security or data-integrity defect may interrupt this order.
