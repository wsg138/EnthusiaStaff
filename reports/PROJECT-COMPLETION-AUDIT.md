# EnthusiaStaff Project Completion Audit

## 1. Audit identity

- **Audit date:** 2026-08-05
- **Audited repository:** `wsg138/EnthusiaStaff`
- **Exact audited `main`:** `dddc8352aed5aac1eeead3a670680cd647b1b9c2`
- **Default branch:** `main`
- **Audit branch:** `audit/full-project-completion-state`
- **Audit pull request:** [#66](https://github.com/wsg138/EnthusiaStaff/pull/66)
- **Highest Flyway migration:** `V16__punishment_decay_eligibility_snapshots.sql`
- **Java:** Temurin 21.0.11+10 in exact-main CI
- **Platform metadata:** Paper API 1.21; Velocity plugin runtime; project version `0.1.0-SNAPSHOT`
- **Authority boundary:** LiteBans remains authoritative; EnthusiaStaff punishment authority was not activated; issue #43 acceptance was not started.
- **Audit limitations:** no production access, no credentials, no private database/player data, no deployment, no provider production routes, no local network checkout. Source was inspected through the GitHub connector and exact-main CI artifact; local DNS was unavailable.

### Methodology

The audit followed the repository authority chain, reconciled live GitHub, inspected current production source, migrations, resources, plugin metadata, configuration, workflows, exact-main CI logs, JaCoCo output, runtime JAR contents, and representative current tests. Existing plans, handoffs, percentages, schemas, interfaces, class names, and PR descriptions were treated as leads rather than proof. Unmerged work is classified separately; no unmerged product work existed at audit start.

Every ledger item receives one primary completion category and one proof level. `TESTED` means current automated tests exercised meaningful behavior; it does not mean platform or production acceptance. `IMPLEMENTED_UNVERIFIED` means code exists but important execution-path proof is absent.

## 2. Executive assessment

EnthusiaStaff is **structurally established and feature-incomplete**. It is not release-candidate ready and not production-ready as a replacement for LiteBans.

The strongest areas are the Java/Gradle architecture, shaded runtime artifacts, protocol authentication/replay primitives, service-boundary authorization, escalation policy engine, durable moderation/report persistence, inventory journal foundations, website request authentication, and dormant LiteBans migration/cutover machinery.

The largest incomplete areas are functional staff-mode tools, cheat testers/fake entities/fake bases, provider-backed market/reputation confiscation, complete currency/item destructive validation, RoseChat-dependent staff communication and private-message evidence, Velocity reload, Bedrock identity correctness, current distributed staging, and all later LiteBans acceptance/cutover stages.

The highest-risk confirmed defect is **AUD-APPEAL-003**: accepting an appeal for one punishment can end every active sanction in the same combined case. Other high-risk deficiencies are incorrect Bedrock platform persistence, one-shot startup database bootstrap, weak direct proof for runtime composition/commands/destructive coordinators, and the absence of current distributed/Bedrock acceptance.

External blockers include a compatible RoseChat provider, an EnthusiaCurrency provider and private destructive staging, market/reputation provider contracts, `enthusia-site` for website UX, GitHub billing/spending-limit restoration for Pi staging, representative private LiteBans data, and owner production authorization.

Development can continue from strong foundations, but the repository must not be described as feature-complete merely because the exact-main build is green.

## 3. Classification summary

### Primary categories

| Category | Count |
|---|---:|
| COMPLETE_GOOD | 19 |
| COMPLETE_WITH_ISSUES | 47 |
| PARTIAL | 12 |
| NOT_STARTED | 6 |
| BLOCKED | 3 |
| DEFERRED_ACCEPTANCE | 10 |
| OUTSIDE_THIS_REPOSITORY | 2 |
| **Total** | **99** |

### Proof levels

| Proof level | Count |
|---|---:|
| NOT_STARTED | 6 |
| PARTIAL | 2 |
| IMPLEMENTED_UNVERIFIED | 23 |
| TESTED | 65 |
| STAGING_VERIFIED | 0 |
| BLOCKED | 3 |
| **Total** | **99** |

No item is classified `STAGING_VERIFIED` for the audited SHA because the current Pi workflow did not execute a runner and no representative distributed/Bedrock/private-data staging evidence was produced.

## 4. Live GitHub and build state

### GitHub state at audit start

- `main`: `dddc8352aed5aac1eeead3a670680cd647b1b9c2`
- Default branch: `main`
- Open pull requests before audit branch creation: none
- Draft pull requests before audit branch creation: none
- Remote branches before audit branch creation: `main` only
- Open issues: #43 only, reserved for later LiteBans production-like acceptance
- No active worker branch or unresolved review thread existed at audit start.
- Recent merged work included freeze interaction/mount recovery, staff-mode world safety and recovery, vanish live-rank reconciliation, database-dump protections, and routing reconciliation.
- No abandoned remote branch with unmerged work was visible.

### Exact-main validation

| Check | Evidence | Result |
|---|---|---|
| Java/build/tests | Coverage run `31035608062`, job `92406803247` | SUCCESS |
| Command | `./gradlew clean build jacocoAggregateReport runtimeJars --no-daemon --no-build-cache --no-configuration-cache --console=plain` | SUCCESS in 5m35s |
| Java | Temurin 21.0.11+10 | SUCCESS |
| Module tests | common, domain, paper, persistence, protocol, velocity, integration-tests | SUCCESS |
| MariaDB/Testcontainers | `integration-tests:test` in exact-main build | SUCCESS |
| Runtime JAR integrity | artifact `8942603945` | SUCCESS |
| Wiki validation | run `31035607322` | SUCCESS |
| Codacy coverage upload | exact-main coverage job | SUCCESS |
| Exact-main Codacy analysis verdict | no check exposed on commit | UNAVAILABLE |
| Pi boot/restart | main run `31035607011`, staging run `31035615198` | BLOCKED before execution |

The staging annotation states the build job was not started because recent account payments failed or the spending limit needed to be increased. The runner ID was `0`, steps were empty, and the Pi boot job was skipped. This is infrastructure evidence, not evidence of a product boot failure.

### Coverage and artifacts

- Aggregate lines: **46.89%**
- Aggregate branches: **37.83%**
- Aggregate instructions: **49.65%**
- Paper JAR: 8,894,001 bytes, SHA-256 `9e5e584f0e2ecb070be39e14654ae62c561a2202feb493bea61bd59face71bee`, 4,747 entries
- Velocity JAR: 7,786,131 bytes, SHA-256 `b34fc9c1c71de772ce213704b352892a4efe513a84a71cc4c13bc31bb910f50f`, 4,120 entries
- Provider API source types checked: 24
- Provider class leaks: 0
- Migration boundary in both runtime JARs: V1 through V16

A passing build proves that current tests passed. It does not prove missing requirements exist or that provider/distributed/production behavior works.

## 5. Requirement-by-requirement ledger

| ID | Workstream | Requirement | Primary category | Proof level | Evidence | Gap or defect | Dependencies | Weight | Risk |
|---|---|---|---|---|---|---|---|---|---|
| AUD-ARCH-001 | Repository and architecture | Module boundaries and dependency direction | COMPLETE_GOOD | TESTED | `settings.gradle.kts`; `build.gradle.kts`; modules `common`, `domain`, `integration-contracts`, `persistence`, `protocol`, `paper`, `velocity`, `integration-tests` | No confirmed boundary inversion in audited source. | — | MEDIUM | LOW |
| AUD-ARCH-002 | Repository and architecture | Java 21 and warnings-as-errors build | COMPLETE_GOOD | TESTED | Root Gradle toolchain 21; `-Xlint:all -Werror`; exact-main Coverage run 31035608062 | No current defect. | — | SMALL | LOW |
| AUD-ARCH-003 | Repository and architecture | Paper and Velocity deployable JARs and provider leakage | COMPLETE_GOOD | TESTED | `runtimeJars`; exact-main artifact 8942603945; one Paper and one Velocity JAR; 24 provider types checked; zero leaks | No current defect. | — | SMALL | LOW |
| AUD-ARCH-004 | Repository and architecture | Test architecture, aggregate coverage, and thresholds | COMPLETE_WITH_ISSUES | TESTED | Exact-main JaCoCo: 46.89% line, 37.83% branch; runtime composition roots, commands, and workers include many 0% classes | Below documented 70/60 overall and 80/70 critical targets; no enforced aggregate threshold. | Most runtime workstreams | LARGE | HIGH |
| AUD-ARCH-005 | Repository and architecture | CI, Wiki validation, and static analysis | COMPLETE_WITH_ISSUES | TESTED | Coverage and Validate Wiki checks succeeded on audited SHA; Codacy coverage upload succeeded | No exact-main Codacy analysis verdict was exposed as a commit check; historical baseline is stale. | GitHub/Codacy | MEDIUM | MEDIUM |
| AUD-RUNTIME-001 | Runtime lifecycle | Paper enable, disable, startup recovery, and shutdown | COMPLETE_WITH_ISSUES | TESTED | `EnthusiaStaffPaperPlugin`; `StorageBootstrapCoordinator`; `PaperShutdownCoordinator`; lifecycle tests | Initial MariaDB bootstrap is one-shot; failure leaves Paper degraded until restart. | MariaDB | MEDIUM | HIGH |
| AUD-RUNTIME-002 | Runtime lifecycle | Velocity enable, disable, and operational refresh | COMPLETE_WITH_ISSUES | TESTED | `EnthusiaStaffVelocityPlugin`; exact-main build and runtime-health tests | Initial MariaDB/config bootstrap is one-shot and Velocity has no reload path. | MariaDB; configuration | MEDIUM | HIGH |
| AUD-RUNTIME-003 | Runtime lifecycle | Bounded executors, rejection, draining, and Folia-safe scheduling | COMPLETE_GOOD | TESTED | `BoundedExecutorFactory`; `PaperShutdownCoordinator`; `WorkerExecutor`; `GlobalScheduler`; executor/lifecycle tests | No confirmed unbounded production executor. | — | MEDIUM | LOW |
| AUD-RUNTIME-004 | Runtime lifecycle | Multi-Paper/multi-Velocity distributed operation | PARTIAL | IMPLEMENTED_UNVERIFIED | Persistent channel, durable stores, server fences, and backend secrets exist | No representative multi-Paper plus multi-Velocity staging proof; several runtime roots have zero coverage. | Distributed staging | VERY_LARGE | HIGH |
| AUD-RUNTIME-005 | Runtime lifecycle | Backend authentication and replay protection | COMPLETE_WITH_ISSUES | TESTED | `EnvelopeAuthenticator`; `ReplayGuard`; `PersistentChannelClient`; `PersistentChannelServer` | Protocol is tested, but live TLS/backend authentication and reconnect behavior are not current-SHA staging verified. | TLS material; staging | LARGE | HIGH |
| AUD-RUNTIME-006 | Runtime lifecycle | HUB/SMP separation and server switching | COMPLETE_WITH_ISSUES | TESTED | Inventory scope IDs; Velocity asset/freeze/staff-session switch fences | Contract exists, but distributed HUB/SMP behavior is not staged on current SHA. | Distributed staging | LARGE | HIGH |
| AUD-ID-001 | Identity and authorization | UUID/name history and offline target resolution | COMPLETE_WITH_ISSUES | TESTED | `JdbcPlayerDirectory`; Paper/Velocity directory writes; command target resolution | Runtime command paths are weakly covered; live username/UUID edge cases remain unstaged. | Player directory | MEDIUM | MEDIUM |
| AUD-ID-002 | Identity and authorization | Staff rank resolution and hierarchy | COMPLETE_GOOD | TESTED | `PaperStaffRankResolver`; `DefaultAuthorizationPolicy`; permission-policy tests | Developer precedence and Founder/console semantics are explicit; no confirmed hierarchy defect. | LuckPerms/Bukkit permissions | MEDIUM | LOW |
| AUD-ID-003 | Identity and authorization | Service-boundary authorization, console, and SYSTEM | COMPLETE_GOOD | TESTED | `AuthorizationPolicy`; `Actor`; `PaperActorResolver`; sanction services | SYSTEM is reserved for policy operations; console maps to Founder actor. No confirmed bypass. | — | MEDIUM | LOW |
| AUD-ID-004 | Identity and authorization | Java and Bedrock platform identity | COMPLETE_WITH_ISSUES | IMPLEMENTED_UNVERIFIED | `ClientEvidenceCollector` can detect Floodgate; Velocity and mute join writes inspected | Velocity and `MuteEnforcementListener` always persist `PlayerPlatform.JAVA`; Bedrock platform metadata is wrong. | Floodgate/Geyser | MEDIUM | HIGH |
| AUD-ID-005 | Identity and authorization | Online permission and rank changes | COMPLETE_WITH_ISSUES | TESTED | Staff-mode and vanish rank reconciliation policies and scheduled reconciliation | Core reconciliation is tested, but end-to-end LuckPerms/runtime changes are not staged. | LuckPerms; staging | MEDIUM | MEDIUM |
| AUD-STAFF-001 | Staff mode | Durable entry, exit, exact snapshot, and restoration | COMPLETE_WITH_ISSUES | TESTED | `StaffModeManager`; `StaffModeActivationCoordinator`; `JdbcStaffSessionStore`; codec/recovery tests | Runtime manager itself is not meaningfully covered end-to-end; platform acceptance remains. | Paper staging | LARGE | HIGH |
| AUD-STAFF-002 | Staff mode | Reconnect, reload, shutdown, and rank-change recovery | COMPLETE_WITH_ISSUES | TESTED | Startup/join recovery, recovery-required state, disable fencing, live rank reconciliation | Current-SHA live restart and reconnect proof is absent because Pi staging did not run. | Pi/distributed staging | LARGE | HIGH |
| AUD-STAFF-003 | Staff mode | Inventory, damage, world mutation, and transfer isolation | COMPLETE_WITH_ISSUES | TESTED | Staff-mode listeners for inventory, transfer, combat, game mode, and world interaction | Broad listener coverage exists in code/tests, but full creative/Bedrock interaction matrix is not live verified. | Paper/Bedrock staging | LARGE | HIGH |
| AUD-STAFF-004 | Staff mode | Functional hotbar staff tools | PARTIAL | IMPLEMENTED_UNVERIFIED | `StaffModeManager` creates PDC-tagged teleport, inspector, freeze, reports, spectate, vanish, chat, tester, and menu items | Only transfer protection reads `staff_tool`; no interaction dispatcher activates the tools. | Freeze/report/vanish/inspect | LARGE | HIGH |
| AUD-STAFF-005 | Staff mode | Cross-server and Java/Bedrock usability | DEFERRED_ACCEPTANCE | IMPLEMENTED_UNVERIFIED | Velocity blocks backend switches with active snapshots; command fallback exists | Representative Java/Bedrock and distributed server-switch acceptance has not occurred. | Distributed Java/Bedrock staging | LARGE | HIGH |
| AUD-VANISH-001 | Vanish | Durable state, hierarchy, reconnect, and startup recovery | COMPLETE_WITH_ISSUES | TESTED | `VanishManager`; `JdbcVanishStore`; `VanishRankReconciliationPolicy`; audience tests | Persistence store coverage is weak and live multi-backend recovery is unstaged. | MariaDB; staging | LARGE | HIGH |
| AUD-VANISH-002 | Vanish | Entity visibility, tab list, spectator presentation, join/quit suppression | COMPLETE_WITH_ISSUES | TESTED | Bukkit hide/show, `PlayerInfoTabMasker`, ProtocolLib adapter, audience coordinator | ProtocolLib unavailable behavior is defensive, but packet behavior is not current-SHA live verified. | ProtocolLib | LARGE | HIGH |
| AUD-VANISH-003 | Vanish | Player-count, voice, chat, and provider integrations | PARTIAL | IMPLEMENTED_UNVERIFIED | RoseChat presence hooks and basic visibility audiences exist | No complete player-count or voice-chat integration contract is proved; RoseChat provider remains external. | RoseChat/voice provider | LARGE | MEDIUM |
| AUD-VANISH-004 | Vanish | Distributed and Bedrock acceptance | DEFERRED_ACCEPTANCE | IMPLEMENTED_UNVERIFIED | Durable DB state and backend-local reapplication exist | No representative Velocity/HUB/SMP Java/Bedrock acceptance for current SHA. | Distributed Java/Bedrock staging | LARGE | HIGH |
| AUD-FREEZE-001 | Freeze | Activation, release, persistence, reconnect, and offline expiration | COMPLETE_WITH_ISSUES | TESTED | `FreezeManager`; `FreezeRuntimeState`; `JdbcFreezeStore`; recovery tests | Store coverage is low; current-SHA boot/restart acceptance was blocked. | Pi/distributed staging | LARGE | HIGH |
| AUD-FREEZE-002 | Freeze | Movement, mount, inventory, command, chat, damage, and interaction restrictions | COMPLETE_WITH_ISSUES | TESTED | Freeze listeners and `FreezeInteractionCoverageTest`; mounted-movement and world-interaction coverage | Code covers the required interaction families; Java/Bedrock runtime proof remains incomplete. | Paper/Bedrock staging | VERY_LARGE | HIGH |
| AUD-FREEZE-003 | Freeze | Proxy/backend switching and fail-closed lookup fencing | COMPLETE_WITH_ISSUES | TESTED | Velocity pre-connect checks; Paper runtime verification; active-authority failure handling | Multi-proxy failover and DB-latency behavior are not staged. | Distributed staging | LARGE | HIGH |
| AUD-FREEZE-004 | Freeze | Java and Bedrock usability acceptance | DEFERRED_ACCEPTANCE | IMPLEMENTED_UNVERIFIED | Command and chat fallback are text-based | No representative Bedrock acceptance exists for the audited SHA. | Geyser/Floodgate staging | MEDIUM | HIGH |
| AUD-COMMS-001 | Staff communication | Staff-chat permissions, formatting, audiences, and routing | BLOCKED | BLOCKED | `StaffChatCommand`; `RoseChatIntegration`; `RoseChatStaffService` contract | A supported RoseChat implementation/API is unavailable in this repository; no first-party fallback channel exists. | RoseChat provider repository | LARGE | HIGH |
| AUD-COMMS-002 | Staff communication | Provider-present/provider-missing safety | COMPLETE_GOOD | TESTED | Integration checks contract/version/ownership and reports unavailable rather than silently routing | No confirmed fail-open privacy defect when provider is absent. | RoseChat contract | MEDIUM | LOW |
| AUD-COMMS-003 | Staff communication | Cross-server duplication, disconnect, and Bedrock readability | BLOCKED | BLOCKED | Contract surfaces exist, but runtime routing is provider-owned | Cannot prove audience privacy, duplication, or cross-server behavior without the provider implementation and staging. | RoseChat; distributed staging | LARGE | HIGH |
| AUD-REPORT-001 | Reports and evidence | Submission, target resolution, cooldown, merging, and duplicate prevention | COMPLETE_WITH_ISSUES | TESTED | `ReportCommand`; `JdbcReportSubmissionStore`; `ReportPolicy`; replay/cooldown persistence tests | Command/platform execution path has zero aggregate coverage; Bedrock command usability unstaged. | Player directory; Paper | LARGE | MEDIUM |
| AUD-REPORT-002 | Reports and evidence | Queue, detail GUI, fallback, notes, status revisions, and stale-state protection | COMPLETE_WITH_ISSUES | TESTED | `ReportsCommand`; report GUI classes; `JdbcReportQueryStore`; `JdbcReportStateStore` | Persistence is well tested; GUI/command wiring has weak direct proof. | Paper GUI; Bedrock | LARGE | MEDIUM |
| AUD-REPORT-003 | Reports and evidence | Chat, private-message, coordinate, client, and attachment evidence | PARTIAL | TESTED | `ChatContextBuffer`; client evidence persistence; RoseChat PM snapshot contract; world/coordinate capture | No attachment workflow exists; PM evidence is blocked by RoseChat provider availability. | RoseChat; attachment contract | LARGE | HIGH |
| AUD-REPORT-004 | Reports and evidence | Notifications, retries, dead letters, reload, and cross-server behavior | COMPLETE_WITH_ISSUES | TESTED | Report submission/state stores enqueue durable Discord events; outbox retry/dead-letter storage exists | Production route delivery and distributed notification behavior are not accepted. | Discord route; distributed staging | LARGE | MEDIUM |
| AUD-REPORT-005 | Reports and evidence | Retention and purge | COMPLETE_GOOD | TESTED | `JdbcReportEvidenceMaintenance`; maintenance scheduler; high coverage and persistence tests | No confirmed retention defect. | — | MEDIUM | LOW |
| AUD-PUNISH-001 | Cases and punishments | Durable drafts, requests, approvals, direct authority, and audit | COMPLETE_WITH_ISSUES | TESTED | `PunishmentDraftWorkflow`; `PunishmentRequestService`; JDBC request/draft stores; services | Runtime commands and presentation are weakly covered; production authority remains disabled. | Reason policy; ACTIVE cutover | VERY_LARGE | HIGH |
| AUD-PUNISH-002 | Cases and punishments | Punishment families, ladders, combinations, and history | COMPLETE_WITH_ISSUES | TESTED | `PunishmentService`; reason policies; history store; public registry | Core domain/persistence is strong; platform command/GUI and provider behavior remain unstaged. | Escalation; Paper | VERY_LARGE | HIGH |
| AUD-PUNISH-003 | Cases and punishments | Case detail, reduction, ending, revocation, overturn, and exact sanction targeting | COMPLETE_WITH_ISSUES | TESTED | `JdbcExactSanctionMutationStore`; sanction lifecycle commands; case/history projections | General case-wide mutation store remains and the appeal endpoint misuses it; see AUD-APPEAL-003. | Appeals; exact-sanction store | VERY_LARGE | CRITICAL |
| AUD-PUNISH-004 | Cases and punishments | Idempotency, concurrency, rollback, expiration, and restart | COMPLETE_WITH_ISSUES | TESTED | Transactional stores, idempotency keys, revisions, leases, event/outbox writes, integration tests | Some older mutation stores have very low coverage and live DB interruption behavior is not staged. | MariaDB staging | LARGE | HIGH |
| AUD-PUNISH-005 | Cases and punishments | Bedrock-safe command fallback | COMPLETE_WITH_ISSUES | IMPLEMENTED_UNVERIFIED | Text commands exist alongside GUIs for punishment/history/case actions | No representative Bedrock review confirms every GUI workflow has a usable fallback. | Bedrock staging | LARGE | MEDIUM |
| AUD-ESC-001 | Escalation policy | Stable reason IDs, aliases, removed IDs, and validation | COMPLETE_GOOD | TESTED | `reason-policies.yml`; `ReasonPolicyConfigurationLoader`; repository and parser tests | No confirmed defect. | — | MEDIUM | LOW |
| AUD-ESC-002 | Escalation policy | Policy versions and recommendation snapshots | COMPLETE_GOOD | TESTED | V15; `JdbcModerationStore`; policy snapshot domain/persistence tests | No confirmed defect. | V15 | MEDIUM | LOW |
| AUD-ESC-003 | Escalation policy | Decay, clean periods, recency, and serious-offense handling | COMPLETE_GOOD | TESTED | V16; `EscalationEngine`; decay eligibility and serious-offense tests | No confirmed defect in the policy engine. | V16 | LARGE | LOW |
| AUD-ESC-004 | Escalation policy | Finite ladders and combined recommendations | COMPLETE_GOOD | TESTED | `EscalationEngine`; reason-policy sanctions; domain tests | No confirmed defect. | — | MEDIUM | LOW |
| AUD-ESC-005 | Escalation policy | Reload and legacy-data behavior | COMPLETE_WITH_ISSUES | TESTED | Atomic reason-policy repository; reload coordinator; nullable legacy snapshots | Paper reload is tested, but Velocity has no reload and live session behavior remains unstaged. | Configuration | MEDIUM | MEDIUM |
| AUD-APPEAL-001 | Appeals | Player submission, authentication, ownership, and reviewer UI | OUTSIDE_THIS_REPOSITORY | PARTIAL | Private website API contract and punishment claim/revalidation stores exist here | The user-facing submission/authentication/reviewer workflow belongs to `enthusia-site`. | enthusia-site | VERY_LARGE | HIGH |
| AUD-APPEAL-002 | Appeals | Acceptance authorization, preparation, audit, and idempotency | COMPLETE_WITH_ISSUES | TESTED | `WebsiteAppealEndpoint`; `JdbcWebsiteAppealStore`; HMAC website API tests | Acceptance preparation is strong, but its mutation target is unsafe; see AUD-APPEAL-003. | Website API; sanctions | LARGE | HIGH |
| AUD-APPEAL-003 | Appeals | Punishment-specific overturn/end behavior | COMPLETE_WITH_ISSUES | TESTED | Endpoint validates `punishmentId`, then submits case-wide `END_EARLY`; `JdbcSanctionMutationStore.changeSanctions` updates all active sanctions for `case_id` | Accepting one punishment appeal in a combined case can end unrelated active sanctions in that case. | Sanction mutation | MEDIUM | CRITICAL |
| AUD-APPEAL-004 | Appeals | Privacy, notifications, retries, and concurrency | PARTIAL | IMPLEMENTED_UNVERIFIED | Private loopback API and appeal request state exist | Site-side notification/privacy lifecycle is external; production-route behavior is not verified. | enthusia-site; Discord | LARGE | HIGH |
| AUD-INV-001 | Inventory and Ender chest | Online/offline access and read/edit permissions | COMPLETE_WITH_ISSUES | TESTED | `InventoryCommand`; `InventoryCoordinator`; view/edit permission split; journal tests | Command/GUI wiring is 0% covered and Bedrock usability is unstaged. | Paper/Bedrock | VERY_LARGE | HIGH |
| AUD-INV-002 | Inventory and Ender chest | Locks, revisions, stale writes, cursor/transfer behavior, and crash recovery | COMPLETE_WITH_ISSUES | TESTED | `JdbcInventoryJournalStore`; patch transitions; prelogin recovery; inventory listeners | Persistence is strong; live disconnect/crash behavior remains current-SHA unverified. | MariaDB/Paper staging | VERY_LARGE | HIGH |
| AUD-INV-003 | Inventory and Ender chest | HUB/SMP separation and server-switch fences | COMPLETE_WITH_ISSUES | TESTED | Inventory scope IDs and Velocity ownership checks | Distributed multi-backend acceptance has not occurred. | Distributed staging | LARGE | HIGH |
| AUD-INV-004 | Inventory and Ender chest | Large inventory bounds and Java/Bedrock fallback | DEFERRED_ACCEPTANCE | IMPLEMENTED_UNVERIFIED | Bounded payloads/patches and command fallback exist | Representative large nested inventories and Bedrock UI behavior require staging. | Bedrock/private staging | LARGE | MEDIUM |
| AUD-ASSET-001 | Asset confiscation/restoration | Item confiscation, snapshots, reservations, rollback, and restoration | PARTIAL | IMPLEMENTED_UNVERIFIED | `ConfiscationCoordinator`; `JdbcInventoryJournalStore`; V6; nested asset codecs | Substantial code exists, but coordinator and codecs are 0% covered and live destructive recovery is unproved. | Inventory; MariaDB | VERY_LARGE | CRITICAL |
| AUD-ASSET-002 | Asset confiscation/restoration | EnthusiaCurrency removal and restore | BLOCKED | BLOCKED | `EconomyCoordinator`; `EnthusiaCurrencyGateway`; compiled provider contract | Requires a compatible deployed EnthusiaCurrency provider and private destructive staging; runtime path has 0% coverage. | EnthusiaCurrency repository/provider | VERY_LARGE | CRITICAL |
| AUD-ASSET-003 | Asset confiscation/restoration | Market confiscation/restriction workflow | NOT_STARTED | NOT_STARTED | Read-only reflective `MarketIntegration` and permissions exist | No durable destructive market reservation, snapshot, rollback, or restoration path. | EnthusiaMarket provider contract | VERY_LARGE | HIGH |
| AUD-ASSET-004 | Asset confiscation/restoration | Reputation confiscation/restriction workflow | NOT_STARTED | NOT_STARTED | `ReputationIntegration` exposes provider calls but no registered moderation workflow invokes mutations | No durable reservation/snapshot/rollback/restoration application path. | EnthusiaCommend provider contract | LARGE | HIGH |
| AUD-ASSET-005 | Asset confiscation/restoration | Owner recovery, duplicate prevention, and bounded work | PARTIAL | TESTED | Idempotent journals, reservations, case restore command, bounded database operations | Only item/economy foundations exist; provider-backed cross-system rollback is incomplete. | External providers | VERY_LARGE | CRITICAL |
| AUD-ALT-001 | Alt and network identity | Protected address HMAC/encryption and raw-address handling | COMPLETE_WITH_ISSUES | TESTED | `NetworkIdentityProtector`; Velocity zeroes raw address bytes; security tests | Production keys and operational rotation are not verified. | Secrets/staging | LARGE | HIGH |
| AUD-ALT-002 | Alt and network identity | Identity graph, confidence, manual relationships, and ambiguity controls | PARTIAL | IMPLEMENTED_UNVERIFIED | `JdbcNetworkIdentityStore`; Velocity `/alts` and `/alt`; confidence/manual state models | Store has very low coverage and no production-like false-positive evaluation. | Private network data staging | VERY_LARGE | HIGH |
| AUD-ALT-003 | Alt and network identity | IP/network bans and inheritance | COMPLETE_WITH_ISSUES | TESTED | Network-sanction type; login lookup; protected identity inheritance; outbox/audit | Live multi-proxy and ambiguous-address behavior is not staged. | Velocity/MariaDB | LARGE | CRITICAL |
| AUD-ALT-004 | Alt and network identity | Privacy, retention, restart, and migration interaction | PARTIAL | IMPLEMENTED_UNVERIFIED | Protected tokens and migration hooks exist; raw addresses are not logged | No representative private-data retention/false-positive acceptance; platform metadata bug affects Bedrock records. | Private data; AUD-ID-004 | LARGE | HIGH |
| AUD-TESTER-001 | Cheat testers and fake entities | Cheat tester workflow | NOT_STARTED | NOT_STARTED | Only an inert staff-mode item labeled `cheat-tester` exists | No command, service, listener, packet implementation, lifecycle, cleanup, or tests. | Staff tools | VERY_LARGE | HIGH |
| AUD-TESTER-002 | Cheat testers and fake entities | Fake player/entity tooling | NOT_STARTED | NOT_STARTED | No production class, command, listener, or configuration path found | Required fake-entity behavior is absent. | Packet API decision | VERY_LARGE | HIGH |
| AUD-TESTER-003 | Cheat testers and fake entities | Fake base generation and cleanup | NOT_STARTED | NOT_STARTED | No production class, command, listener, schema, or configuration path found | Required fake-base behavior is absent. | World safety/design decision | VERY_LARGE | HIGH |
| AUD-DISCORD-001 | Discord and notifications | Durable event production and outbox schema | COMPLETE_WITH_ISSUES | TESTED | Punishment, request, report, freeze, vanish, staff-session, and identity stores insert into `discord_outbox` | Not every event family has live delivery proof, but producer paths are present. | MariaDB | LARGE | MEDIUM |
| AUD-DISCORD-002 | Discord and notifications | Delivery, retries, circuit state, dead letters, and duplicate prevention | COMPLETE_WITH_ISSUES | TESTED | `JdbcDiscordOutboxStore`; `DiscordOutboxWorker`; retry support | Store is tested; Velocity worker has 0% coverage and no current live route acceptance. | Discord webhook route | LARGE | HIGH |
| AUD-DISCORD-003 | Discord and notifications | Secrets, privacy, formatting, rate limits, and production-route isolation | DEFERRED_ACCEPTANCE | IMPLEMENTED_UNVERIFIED | Environment-based route configuration; disabled by default; sanitized status | Requires isolated non-production route testing and production authorization. | Discord credentials/routes | MEDIUM | HIGH |
| AUD-WEB-001 | Website and API | Loopback authentication, HMAC, replay, body bounds, and private boundary | COMPLETE_GOOD | TESTED | `WebsiteApiServer`; request decoder/authenticator; nonce store; website API tests | No confirmed API-boundary defect. | Velocity; secrets | LARGE | LOW |
| AUD-WEB-002 | Website and API | Punishment/history/case/appeal contracts and visibility | COMPLETE_WITH_ISSUES | TESTED | Public punishment registry, claim/revalidate, case and appeal endpoints | Appeal mutation target is unsafe; public/private semantics still require site integration acceptance. | AUD-APPEAL-003; enthusia-site | LARGE | CRITICAL |
| AUD-WEB-003 | Website and API | Pagination and rate limiting | PARTIAL | IMPLEMENTED_UNVERIFIED | Cursor/limit bounds and loopback-only binding exist | No explicit request-rate limiter exists; trust is delegated to local site boundary. | enthusia-site/reverse proxy | MEDIUM | MEDIUM |
| AUD-WEB-004 | Website and API | Website UX and external integration | OUTSIDE_THIS_REPOSITORY | PARTIAL | Repository exposes private contracts only | Pages, authentication UX, appeal forms, and staff web UI belong to `enthusia-site`. | enthusia-site | VERY_LARGE | HIGH |
| AUD-MIG-001 | LiteBans migration | Dormant reader, schema inspection, mappings, protected identities, and history | COMPLETE_WITH_ISSUES | TESTED | `LiteBansReader`; `LiteBansSchemaInspector`; migration service; V1-V16 | Strong synthetic proof, but no representative private database verification. | Representative LiteBans dump | VERY_LARGE | HIGH |
| AUD-MIG-002 | LiteBans migration | Synthetic clean-install, upgrade, checksum, immutability, dry-run, replay, and idempotency | COMPLETE_GOOD | TESTED | Exact-main integration tests, `LiteBansCutoverRestartRecoveryIntegrationTest`, checksum/cutover tests | No confirmed synthetic-test defect. | Docker/Testcontainers | LARGE | LOW |
| AUD-MIG-003 | LiteBans migration | Representative local-data verification | DEFERRED_ACCEPTANCE | IMPLEMENTED_UNVERIFIED | Runbook and dormant migration code exist | No private/live-derived database was accessed or requested by this audit. | Owner-provided local sanitized copy | LARGE | HIGH |
| AUD-MIG-004 | LiteBans migration | Interrupted-run recovery and disappeared/rejected-row handling | COMPLETE_WITH_ISSUES | TESTED | High-water marks, reconciliation tracking, restart recovery integration tests | Synthetic behavior is proved; representative data interruption remains deferred. | Representative local data | LARGE | MEDIUM |
| AUD-MIG-005 | LiteBans migration | Shadow comparison and 168-hour acceptance | DEFERRED_ACCEPTANCE | IMPLEMENTED_UNVERIFIED | Shadow comparator and cutover evidence stores exist | Issue #43 window has not started; no seven-day current acceptance evidence. | Issue #43; production-like staging | VERY_LARGE | CRITICAL |
| AUD-MIG-006 | LiteBans migration | Final rehearsal, activation, emergency freeze, and rollback | DEFERRED_ACCEPTANCE | IMPLEMENTED_UNVERIFIED | Cutover coordinator, authority fencing, rollback documentation, and operational modes exist | No rehearsal, authorization, activation, freeze, or rollback was performed. | Owner authorization; staging | VERY_LARGE | CRITICAL |
| AUD-MIG-007 | LiteBans migration | Distributed Velocity/HUB/SMP cutover staging | DEFERRED_ACCEPTANCE | IMPLEMENTED_UNVERIFIED | Protocol and authority fences exist | No distributed staging proof for the audited SHA. | Distributed staging | VERY_LARGE | CRITICAL |
| AUD-CONFIG-001 | Configuration and verification | Defaults, validation, secret indirection, and startup rejection | COMPLETE_GOOD | TESTED | Paper/Velocity configuration loaders, default resources, parser tests | No confirmed validation defect. | — | LARGE | LOW |
| AUD-CONFIG-002 | Configuration and verification | Paper atomic reload and invalid-candidate rollback | PARTIAL | TESTED | `ConfigurationReloadCoordinator`; atomic publishers; reload tests | Only a subset is reloadable; many provider/network/runtime values require restart by design. | Paper runtime | LARGE | MEDIUM |
| AUD-CONFIG-003 | Configuration and verification | Velocity reload | NOT_STARTED | NOT_STARTED | Velocity loads configuration only in `initializeStorage` | No command or runtime path reloads Velocity configuration. | Velocity runtime | LARGE | HIGH |
| AUD-CONFIG-004 | Configuration and verification | Health, verify, redaction, and operator feedback | COMPLETE_WITH_ISSUES | TESTED | `/estaff status/verify/reload`; `RuntimeHealth`; sanitized reload details | Command/runtime roots have weak coverage and live operator acceptance is absent. | Paper/Velocity staging | MEDIUM | MEDIUM |
| AUD-SEC-001 | Security and privacy | Prepared SQL and injection resistance | COMPLETE_GOOD | TESTED | JDBC stores use prepared statements; dynamic identifiers are constrained; integration tests | No confirmed injection path in audited production source. | — | LARGE | LOW |
| AUD-SEC-002 | Security and privacy | Secrets, sensitive logging, evidence, notes, and IP handling | COMPLETE_WITH_ISSUES | TESTED | Environment indirection, protected tokens, sanitized health messages, raw-address zeroing | Operational secret rotation and private-data staging were not inspected. | Private environment | LARGE | HIGH |
| AUD-SEC-003 | Security and privacy | Website spoofing/replay protection | COMPLETE_GOOD | TESTED | Loopback bind, bearer, HMAC timestamp/nonce/body digest, replay store | No confirmed defect. | — | LARGE | LOW |
| AUD-SEC-004 | Security and privacy | Backend authentication and authority fail-closed behavior | COMPLETE_WITH_ISSUES | TESTED | TLS persistent channel, per-backend secrets, ACTIVE cutover gates, login/switch fail-closed paths | Live TLS, multi-proxy replay, and disconnect behavior are not staged. | Distributed staging | VERY_LARGE | CRITICAL |
| AUD-SEC-005 | Security and privacy | Appeal authority isolation | COMPLETE_WITH_ISSUES | TESTED | Website reviewer authorization is enforced | Exact-sanction isolation fails; see AUD-APPEAL-003. | AUD-APPEAL-003 | MEDIUM | CRITICAL |
| AUD-PERF-001 | Performance and scalability | Indexes, pagination, and bounded database reads | COMPLETE_WITH_ISSUES | TESTED | V1-V16 indexes; paginated history/report/public APIs; bounded claim limits | No representative query-plan/load evidence for production-sized data. | Production-like dataset | LARGE | MEDIUM |
| AUD-PERF-002 | Performance and scalability | Executor, queue, cache, and scheduled-task bounds | COMPLETE_GOOD | TESTED | Bounded worker queues, batch limits, shutdown cancellation, executor tests | No confirmed unbounded executor or obvious unbounded hot collection. | — | LARGE | LOW |
| AUD-PERF-003 | Performance and scalability | Platform-thread safety and synchronous I/O | COMPLETE_WITH_ISSUES | IMPLEMENTED_UNVERIFIED | Most JDBC work is worker-dispatched; Folia entity/global schedulers are used | Composition and command paths have weak coverage; no latency-injection staging proves all callbacks stay platform-safe. | Latency staging | VERY_LARGE | HIGH |
| AUD-PERF-004 | Performance and scalability | 100+ player and distributed load behavior | DEFERRED_ACCEPTANCE | IMPLEMENTED_UNVERIFIED | Design uses bounds, pagination, and durable queues | No 100+ player, provider-outage, or multi-server load test exists for current SHA. | Load environment | VERY_LARGE | HIGH |
| AUD-PERF-005 | Performance and scalability | Database latency, reconnect, and startup recovery | PARTIAL | IMPLEMENTED_UNVERIFIED | Hikari pool reconnects after successful initialization; runtime health degrades on failures | Initial bootstrap is one-shot and no chaos/reconnect acceptance is current. | MariaDB staging | LARGE | HIGH |
| AUD-DOC-001 | Documentation and operations | Installation, permissions, commands, configuration, and troubleshooting | COMPLETE_WITH_ISSUES | TESTED | Wiki/operator pages and plugin metadata; Validate Wiki run 31035607322 | Docs are broad, but several completion claims exceed current proof and some provider behavior is aspirational. | Documentation discrepancies | LARGE | MEDIUM |
| AUD-DOC-002 | Documentation and operations | Database, migration, backup, rollback, staging, and security handling | COMPLETE_WITH_ISSUES | TESTED | `docs/database.md`; `docs/cutover-acceptance.md`; security/runbook pages | Operational steps are documented, but representative and production acceptance is intentionally absent. | Staging/authorization | LARGE | HIGH |
| AUD-DOC-003 | Documentation and operations | Requirements matrix, implementation status, manifests, and handoff accuracy | COMPLETE_WITH_ISSUES | IMPLEMENTED_UNVERIFIED | Authority documents inspected against current main | `UPGRADE-MANIFEST.md`, `CODACY-BASELINE.md`, percentages/status pages and some handoffs are stale or historical. | Planning | MEDIUM | HIGH |
| AUD-DOC-004 | Documentation and operations | Canonical completion audit and post-merge planning state | COMPLETE_GOOD | TESTED | This report, audit handoff, and workspace-state routing in PR #66 | Applies to audit documentation only, not product completion. | — | SMALL | LOW |

## 6. Detailed subsystem assessments

### A. Repository and architecture

**Required behavior.** Java 21 multi-module repository, clean dependency direction, two deployable runtime artifacts, warnings-as-errors, provider isolation, meaningful tests, CI, static analysis, coverage, and validated documentation.

**Current implementation and proof.** Modules are `common`, `domain`, `integration-contracts`, `persistence`, `protocol`, `paper`, `velocity`, and `integration-tests`. Exact-main CI ran Java 21 with `-Xlint:all -Werror`, built both shaded artifacts, passed unit/integration tests and Wiki validation, and found zero provider API leaks.

**Complete and good.** Module layout, artifact boundary, Java/compiler strictness, and provider isolation are strong foundations.

**Confirmed deficiencies.** AUD-ARCH-004: 46.89% line and 37.83% branch coverage are below documented targets; major runtime composition, commands, workers, and destructive coordinators are at 0%. AUD-ARCH-005: exact-main Codacy analysis verdict is unavailable and the historical baseline is stale. No aggregate threshold enforces the targets.

**Overlap.** Runtime-test work spans nearly every subsystem and should accompany bounded feature work rather than chase percentages alone.

### B. Runtime lifecycle and distributed operation

**Required behavior.** Safe Paper/Velocity startup, reload, shutdown, reconnect, executor lifecycle, Folia scheduling, authenticated distributed operation, server switching, HUB/SMP separation, and deliberate failure modes.

**Current implementation and proof.** Paper uses validated startup, bounded workers, asynchronous storage bootstrap, freeze/staff/vanish recovery, operational refresh, and ordered shutdown. Velocity initializes storage asynchronously, refreshes authority, fences login/server switching, and closes channel/outbox/API/database resources. Lifecycle, executor, protocol, and MariaDB integration tests pass.

**Complete and good.** Executor bounds, rejection handling, shutdown/draining, entity/global scheduler abstractions, and authority fencing are sound.

**Confirmed deficiencies.** AUD-RUNTIME-001/002: initial MariaDB bootstrap is attempted once; temporary startup failure requires restart. AUD-RUNTIME-004/005: multi-Paper/multi-Velocity, TLS reconnect, duplicate delivery, and failover are implemented but unverified.

**Blocked/deferred.** Pi boot/restart was blocked by GitHub billing before execution. Distributed staging remains deferred.

**Overlap.** Lifecycle code is shared by authority, channel, freeze, staff mode, inventory, economy, and migration; changes should be sequenced.

### C. Identity, staff ranks, and authorization

**Required behavior.** UUID/name history, Floodgate/Geyser identity, complete rank semantics, service-boundary hierarchy, online rank changes, audit attribution, and offline targets.

**Current implementation and proof.** `JdbcPlayerDirectory`, rank resolvers, `DefaultAuthorizationPolicy`, and command target resolution exist. Service authorization and rank policies are tested. Console maps to Founder; SYSTEM remains reserved.

**Complete and good.** Hierarchy enforcement exists below the command layer and Developer precedence avoids stale moderation grants elevating technical staff.

**Confirmed defect.** AUD-ID-004: Velocity and a Paper join path always store `PlayerPlatform.JAVA`; Bedrock players are persistently mislabeled despite Floodgate detection in client evidence.

**Blocked/deferred.** End-to-end LuckPerms and Java/Bedrock identity behavior require provider staging.

**Overlap.** The platform defect affects reports, alt analysis, client evidence, audit display, and future Bedrock fallbacks.

### D. Staff mode

**Required behavior.** Durable safe entry/exit, exact restoration, reconnect/restart recovery, rank reconciliation, complete interaction isolation, usable tools, server-switch safety, and Java/Bedrock support.

**Current implementation and proof.** `StaffModeManager` persists a checksum-protected snapshot before mutation, restores exact state, marks recovery-required failures, recovers at startup/join, reconciles ranks, and uses listeners for inventory, transfer, combat, game mode, and world interaction. Velocity fences active sessions. Snapshot/recovery/listener tests pass.

**Complete and good.** Snapshot-before-mutation, ownership fencing, recovery-required state, and exact restore are strong.

**Confirmed deficiency.** AUD-STAFF-004: hotbar items are tagged with tool IDs, but no interaction dispatcher reads them. The teleport, inspect, freeze, reports, spectate, vanish, staff-chat, tester, and menu items are inert; only transfer protection recognizes the tag.

**Not implemented/deferred.** Cheat/fake tooling is absent. Current-SHA restart, distributed switching, and Bedrock acceptance are deferred.

**Overlap.** Staff tools touch freeze, reports, vanish, inspect, teleport, and future cheat tooling and should share one coordinated dispatcher.

### E. Vanish

**Required behavior.** Durable hierarchy-aware invisibility, tab/entity correctness, join/quit privacy, spectator behavior, reconnect/startup recovery, switching, integrations, cleanup, and Bedrock support.

**Current implementation and proof.** `VanishManager`, durable store, rank reconciliation, Bukkit hide/show, tab masker, ProtocolLib adapter, and presence hooks exist. Rank/audience/tab policies are well covered; persistence/runtime coverage is weak.

**Complete and good.** Hierarchy-aware visibility and fail-closed unlisting when packet support is unavailable are good foundations.

**Deficiencies/partial.** Player-count and voice integration are incomplete; RoseChat is external; distributed continuity and Bedrock behavior lack current-SHA staging.

**Overlap.** Vanish shares staff mode, rank reconciliation, RoseChat, ProtocolLib, proxy presence, and player-count surfaces.

### F. Freeze

**Required behavior.** Durable activation/release, reconnect/expiration, fail-closed fencing, comprehensive restrictions, proxy switch prevention, alerts, lifecycle safety, and Bedrock support.

**Current implementation and proof.** `FreezeManager`, runtime state, listeners, and JDBC store cover movement, mounts, teleport, inventory, interactions, projectiles/damage, commands, chat, offline expiration, and Velocity switching. Freeze runtime, interaction, mount, and recovery tests pass.

**Complete and good.** The fail-closed model and broad interaction matrix are strong.

**Deficiencies.** Store/manager runtime coverage is weaker than policy/listener coverage. Multi-proxy DB-latency behavior and Bedrock parity are unstaged.

**Deferred.** Current-SHA Pi restart and Java/Bedrock acceptance.

**Overlap.** Freeze shares interaction listeners, proxy switching, staff tools, alerts, Discord, and identity lookup.

### G. Staff chat and staff communication

**Required behavior.** Private rank-aware Paper/Velocity communication, formatting/audiences, provider safety, reload, no duplication, disconnect safety, and Bedrock readability.

**Current implementation and proof.** `StaffChatCommand` delegates to a versioned `RoseChatStaffService` through `RoseChatIntegration`. Missing/incompatible provider state is surfaced as unavailable rather than falling back publicly.

**Complete and good.** Provider-missing privacy fails closed.

**Blocked.** AUD-COMMS-001/003: there is no first-party channel and the compatible RoseChat implementation is outside this repository. Routing, duplication, cross-server audiences, and disconnect behavior cannot be proved without it.

**Overlap.** Staff chat shares vanish presence, report PM evidence, automod, provider reload, and Bedrock readability.

### H. Reports and evidence

**Required behavior.** Submission, targets, cooldown/duplicate control, queue/detail UI and fallback, revisions/notes, privacy, evidence, alerts, retention, retries, reload, restart, and cross-server behavior.

**Current implementation and proof.** Report command/GUI and JDBC stores implement durable submission, merge/replay protection, pagination, revisions, notes, chat/world/coordinate/client evidence, retention, and Discord outbox events. Persistence is strongly covered; command/GUI paths are weak.

**Complete and good.** Durable IDs, cooldown/merge semantics, stale-state protection, and retention are reusable.

**Confirmed deficiencies.** AUD-REPORT-003: no attachment workflow exists. PM evidence is blocked by RoseChat. Cross-server notification delivery and Bedrock UI behavior are unstaged.

**Overlap.** Reports touch RoseChat, client evidence, Discord, identity, staff tools, and cases.

### I. Cases, punishments, sanctions, and history

**Required behavior.** Durable drafts/approvals/direct authority, families/ladders/combinations, history/case detail, exact lifecycle changes, visibility, idempotency, concurrency, rollback, expiration/restart, and Bedrock fallback.

**Current implementation and proof.** Domain/persistence provide drafts, requests, leases, approvals, cases, sanctions, history, public projections, exact and case-wide mutation, authority gates, and transactional events/outboxes. Paper commands/GUI exist. Domain/JDBC tests are strong; runtime commands are at 0% coverage.

**Complete and good.** Durable drafts/requests, revisions, policy snapshots, idempotency, and exact-sanction infrastructure are strong.

**Critical defect.** AUD-PUNISH-003/AUD-APPEAL-003: website appeal validation is sanction-specific, but mutation is case-wide and can end unrelated combined sanctions.

**Deferred.** ACTIVE authority, distributed operation, and Java/Bedrock acceptance.

**Overlap.** Punishments share escalation, appeals, reports, identity, Discord, website, migration, and authority mode. Exact and case-wide mutation work must be sequential.

### J. Escalation policy

**Required behavior.** Stable IDs/aliases, removed IDs, versions/snapshots, decay, clean periods, serious offenses, recency/families/severity, finite ladders, combined recommendations, reload, legacy handling, tests, and docs.

**Current implementation and proof.** Validated YAML is atomically published. `EscalationEngine`, V15 recommendation snapshots, V16 decay eligibility, and legacy-null handling are extensively tested.

**Complete and good.** This is one of the strongest areas. Stable IDs, finite ladders, snapshots, clean periods, and serious-offense behavior should be preserved.

**Deficiency.** Runtime reload proof is Paper-only because Velocity has no reload.

**Overlap.** Policy, history, punishment, and migration share snapshot semantics and should be sequenced.

### K. Appeals

**Required behavior.** Authenticated ownership/submission, staff review, exact case/sanction linkage, status transitions, overturn/end behavior, privacy, audit, notifications, retries, and concurrency.

**Current implementation and proof.** The repository exposes a loopback HMAC website bridge; punishment codes bind an account to a sanction/case; acceptance preparation is idempotent. Website UX is external. Endpoint/store coverage is strong.

**Complete and good.** Private boundary, account binding, conflict handling, and reviewer authorization are sound.

**Critical defect.** AUD-APPEAL-003: `WebsiteAppealEndpoint` discards the validated `punishmentId`, submits case-wide `END_EARLY`, and `JdbcSanctionMutationStore` updates all pending/active sanctions for the case.

**Outside this repository.** Submission/authentication/reviewer UX and site notifications belong to `enthusia-site`.

**Overlap.** Exact-sanction correction touches sanction services, public punishment IDs, appeal state, audit/events, and combined sanctions.

### L. Inventory and Ender chest inspection/editing

**Required behavior.** Online/offline access, HUB/SMP separation, locks/revisions, stale-write prevention, cursor/creative/disconnect handling, crash recovery, audit, permission separation, bounds, and Bedrock fallback.

**Current implementation and proof.** `InventoryCoordinator`, journal store, snapshots/patches, prelogin recovery, ownership fences, Velocity switching, and `/invsee`/`/endersee` exist. Journal and transition tests are extensive; coordinator/GUI/commands are at 0% coverage.

**Complete and good.** Durable revisions, scopes, stale-write rejection, prelogin recovery, and audit journals are strong.

**Deficiencies/deferred.** Live creative/cursor/disconnect, large nested inventory, distributed switching, and Bedrock fallback are under-proved.

**Overlap.** Inventory shares journals/fences with confiscation, staff mode, proxy switching, and restoration.

### M. Item, economy, market, and reputation confiscation/restoration

**Required behavior.** Provider contracts, reservations, exact snapshots, partial-failure rollback, idempotency, crash recovery, duplicates, outage handling, bounds, audit, and authorization.

**Current implementation and proof.** Item and currency coordinators plus journals/schema exist. `ConfiscationCoordinator` handles nested item snapshots. `EconomyCoordinator` and currency gateway model prepare/apply/reconcile/restore. Market/reputation adapters do not form complete registered destructive workflows.

**Complete and good.** Durable reservation/idempotency concepts, owner recovery permission, and inventory journal foundations are reusable.

**Critical/high deficiencies.** AUD-ASSET-001: item destructive coordinator/codecs are effectively uncovered. AUD-ASSET-002: currency requires external provider/private staging and is unproved. AUD-ASSET-003/004: market and reputation end-to-end workflows are absent; schemas/permissions/adapters are not workflows.

**Overlap.** All destructive assets share journals, cases, rollback, providers, Discord/audit, and switch fences and should remain sequential.

### N. Alt and network identity

**Required behavior.** Protected addresses, HMAC/encryption, graph/confidence/manual relationships, inheritance, IP/network bans, ambiguity protection, privacy/retention, cross-server/restart/migration, and audit.

**Current implementation and proof.** Velocity protects addresses, zeroes temporary bytes, records observations, supports alt commands, and can inherit network sanctions. Cryptographic protection is well tested; JDBC identity store coverage is very low.

**Complete and good.** Raw addresses are not intentionally persisted/logged, and protected matching is disabled without keys/config.

**Deficiencies.** Bedrock metadata is wrong; false positives, manual transitions, multi-proxy concurrency, retention, and representative private-data behavior are unverified.

**Overlap.** Velocity login, IP bans, migration, privacy, Discord, and Bedrock identity.

### O. Cheat testers, fake entities, and fake bases

**Required behavior.** Permissioned testing tools, fake entities/bases, lifecycle/cleanup, player safety, packet behavior, performance, Java/Bedrock support, and tests.

**Current implementation and proof.** No command, service, listener, packet adapter, scheduler, schema, config, or tests implement these features. Only an inert `cheat-tester` hotbar item exists.

**Not implemented.** AUD-TESTER-001 cheat tester, AUD-TESTER-002 fake entities, and AUD-TESTER-003 fake bases.

**Overlap.** Staff tools, packet API, world mutation, cleanup, performance, and Bedrock compatibility. The features are too coupled for casual concurrent work.

### P. Discord and external notifications

**Required behavior.** Private logging/alerts, secrets, durable outbox, retries/dead letters, deduplication, outage/reload, route isolation, formatting, and rate limits.

**Current implementation and proof.** Punishment, request, report, freeze, vanish, staff-session, and identity stores produce `discord_outbox` rows. JDBC outbox/retry/circuit state and Velocity delivery worker exist. Store coverage is strong; worker coverage is 0%.

**Complete and good.** Transactional producers and durable retry/dead-letter state are correct foundations; routes are disabled by default and environment-based.

**Deficiencies/deferred.** No current live non-production route acceptance proves formatting, privacy, duplicate handling, rate limits, outage, or production isolation.

**Overlap.** Producers are embedded in many transactional stores, so schema/event changes overlap multiple workstreams.

### Q. Website and external API contracts

**Required behavior.** Punishment/history/appeal APIs, authentication, visibility, pagination, rate limits, audit, and clear site boundary.

**Current implementation and proof.** Velocity provides loopback-only bearer/HMAC/timestamp/nonce/body-digest authentication, bounded bodies, claim/revalidate, public punishment/case/history projections, and appeal acceptance. API and store tests are strong.

**Complete and good.** The loopback cryptographic boundary is strong.

**Deficiencies.** The exact-sanction appeal defect is exposed through the API. Limits/pagination exist, but there is no explicit application rate limiter; trust is delegated to loopback/site/reverse proxy.

**Outside this repository.** UX, user authentication, forms, and staff pages belong to `enthusia-site`.

### R. LiteBans migration and authority cutover

**Required behavior.** Dormant implementation, synthetic and representative verification, interruption recovery, shadow comparison, 168-hour acceptance, rehearsal, activation, emergency freeze, rollback, distributed staging, and authorization.

**Current implementation and proof.** Schema inspection, reader/mapping, checksums, high-water marks, reconciliation/rejection tracking, protected identities, dry-run/shadow, replay/idempotency, restart recovery, comparator, cutover evidence, operational modes, and rollback docs exist. Exact-main migration/integration tests pass; both JARs contain immutable V1-V16.

**Complete and good.** Dormant migration architecture and synthetic verification are strong. LiteBans remains authoritative and ACTIVE is refused without authorized evidence.

**Deferred.** Representative local data, issue #43/168-hour shadow, distributed staging, final rehearsal, activation, emergency freeze, rollback, and production authorization. None occurred in this audit.

**Overlap.** Identity, sanctions, history, policy snapshots, public codes, authority, protocol, and rollback. Migration must remain sequential and representative-data work must be private/local.

### S. Configuration, reload, and verification

**Required behavior.** Defaults, strict validation, atomic publication, invalid reload rollback, session-safe snapshots, provider toggles, feedback, verification/health, redaction, and docs.

**Current implementation and proof.** Paper atomically reloads policy/report/moderation/alert subsets and rejects invalid candidates; DB/workers/network/visibility/automod/RoseChat/economy values are restart-required. Velocity loads once. Parser/publisher/reload/health tests pass.

**Complete and good.** Secret indirection, strict unknown-key validation, atomic policies, and invalid-candidate rollback.

**Deficiencies.** AUD-CONFIG-002: Paper reload is partial. AUD-CONFIG-003: Velocity reload is not implemented. Operator command paths remain weakly covered.

**Overlap.** Policies, report GUIs, alerts, provider toggles, visibility, channel, and lifecycle.

### T. Security and privacy

**Required behavior.** Permissions/hierarchy, safe SQL, secrets, protected IP/evidence/notes, spoofing/replay defense, backend authentication, rate limiting, DoS bounds, and fail-closed authority.

**Current implementation and proof.** Prepared JDBC, environment secrets, protected addresses, sanitized health, loopback HMAC/replay, TLS/per-backend channel secrets, and ACTIVE fail-closed paths exist. Authorization/security/protocol/integration tests pass.

**Complete and good.** Service authorization, website replay protection, migration fencing, and address protection are strong.

**Critical defect.** AUD-SEC-005/AUD-APPEAL-003: exact-sanction authority isolation fails in appeal acceptance.

**Unverified.** Live TLS/multi-proxy replay/disconnect, key rotation, private retention, and rate-limit behavior.

**Overlap.** Sanctions, website, channel, identity, Discord, config, and authority mode.

### U. Performance and scalability

**Required behavior.** Indexes, pagination, bounded resources, platform-safe I/O, no duplicate distributed work, cleanup, 100+ players, and DB latency/reconnect tolerance.

**Current implementation and proof.** Migrations define indexes, APIs paginate, claims/queues/pools are bounded, scheduled tasks close, JDBC is generally worker-dispatched, and platform mutations use scheduler abstractions. Executor/persistence/channel/shutdown tests pass.

**Complete and good.** Explicit bounds and batch limits are widespread; no unbounded production executor was found.

**Deficiencies.** Initial DB bootstrap does not retry; composition/commands are weakly covered; no production-volume query plan, 100+ player, provider outage, distributed duplicate-work, or latency-injection evidence exists.

**Overlap.** Performance spans every persistent workstream and should use realistic data rather than speculative rewrites.

### V. Documentation and operational readiness

**Required behavior.** Accurate installation, permissions, commands, config, database, migration, backup/rollback, staging, build/test, troubleshooting, security, blueprint, status, and requirements tracking.

**Current implementation and proof.** Extensive Wiki/operator/developer docs exist and Wiki validation passes. All required authority documents were compared against current source, migrations, CI, artifacts, and live GitHub.

**Complete and good.** Migration/security operations documentation is detailed and reusable.

**Discrepancies.** `UPGRADE-MANIFEST.md` and `CODACY-BASELINE.md` are historical. The matrix/status pages and some handoffs claim more completion than code/tests prove. Schemas/interfaces/soft dependencies are sometimes treated as workflows. The failed Pi check is infrastructure, not a product boot failure.

**Overlap.** Goals/matrix/blueprint/status were intentionally not edited; their disagreement is audit evidence.

## 7. Cross-cutting defects and architectural risks

### CRITICAL — appeal mutation authority is broader than the accepted punishment

`WebsiteAppealEndpoint.accept` validates and binds `punishmentId`, but `applyChange` discards it and sends case-wide `END_EARLY`. `JdbcSanctionMutationStore.changeSanctions` locks and updates all pending/active sanctions for the case. This can terminate a ban, mute, warning, or network sanction not covered by the accepted appeal. The defect overlaps website API, appeals, case-wide mutation, exact-sanction mutation, audit/event generation, and combined sanctions.

### HIGH — runtime proof is much weaker than domain/persistence proof

The exact-main aggregate report shows many key runtime classes at 0% line coverage, including both plugin composition roots, most Paper commands, the Velocity Discord worker, inventory/confiscation/economy coordinators, and provider adapters. This creates risk that interfaces and tested stores exist without correctly registered platform paths.

### HIGH — Bedrock identity is persistently mislabeled

Velocity and a Paper join path always write `PlayerPlatform.JAVA`. This affects identity history, reporting, alt analysis, and future Bedrock-specific behavior.

### HIGH — startup database recovery is one-shot

Both runtimes degrade safely when initial storage startup fails, but neither re-attempts initialization. Temporary startup DB outages require a full server/proxy restart.

### HIGH — distributed authority remains unproved

Persistent-channel security, server-switch fences, outboxes, and durable state exist, but no current-SHA multi-Velocity/HUB/SMP acceptance proves ordering, reconnect, duplicates, or failover.

### HIGH — destructive asset workflows share unproved runtime seams

Item/currency coordinators are large, coupled, and effectively uncovered. Market/reputation workflows are absent. They should not be implemented concurrently against shared journals and case lifecycle.

### MEDIUM — configuration behavior differs by runtime

Paper supports a bounded atomic reload subset; Velocity supports startup load only.

### MEDIUM — documentation and proof diverge

Matrix, status, manifests, historical Codacy baseline, and handoffs contain claims broader or older than current proof.

## 8. Confirmed-good foundations

- Java 21 modules, warnings-as-errors, one Paper and one Velocity shaded JAR, and zero provider leakage.
- Service-boundary rank/hierarchy authorization.
- Authenticated/replay-protected protocol and bounded channels.
- Stable escalation IDs, finite ladders, recommendation/decay snapshots, and clean-period rules.
- Transactional cases, sanctions, history, requests, leases, idempotency, outboxes, and exact-sanction infrastructure.
- Report cooldown/merge/replay, revisions, retention, and durable notification producers.
- Inventory revisions, scopes, ownership fences, recovery, and audit journals.
- Loopback bearer/HMAC/timestamp/nonce/body-digest website boundary.
- LiteBans schema/reader/mapping/checksum/high-water/reconciliation/restart/shadow/cutover foundations.
- ACTIVE authority fencing and fail-closed critical lookups.

These foundations have ledger limitations and should be extended rather than replaced without evidence.

## 9. External blockers and repository boundaries

| Blocker/boundary | Affected audit IDs | Exact unblock condition |
|---|---|---|
| RoseChat provider | AUD-COMMS-001, AUD-COMMS-003, AUD-REPORT-003, AUD-VANISH-003 | Compatible versioned provider in controlled staging |
| EnthusiaCurrency | AUD-ASSET-002, AUD-ASSET-005 | Compatible provider plus private destructive rollback staging |
| EnthusiaMarket | AUD-ASSET-003 | Contract for reservation, snapshot, apply, rollback, restore |
| EnthusiaCommend | AUD-ASSET-004 | Contract for exact reputation mutation/restoration |
| `enthusia-site` | AUD-APPEAL-001, AUD-APPEAL-004, AUD-WEB-004 | Site implementation and contract integration tests |
| GitHub Pi billing/spend | current boot/restart proof | Restore Actions capacity and rerun trusted staging |
| Representative LiteBans data | AUD-MIG-003/004 | Owner-provided protected local copy in private environment |
| Distributed staging | runtime/visibility/freeze/inventory/network/cutover | Isolated Velocity + HUB + SMP + MariaDB + providers |
| Java/Bedrock staging | identity/staff/vanish/freeze/report/inventory | Representative Java and Floodgate/Geyser clients |
| Production authorization | AUD-MIG-005 through 007 | Owner approval after earlier gates pass |

## 10. Deferred staging and production acceptance

1. Pi boot/restart on the exact final feature SHA.
2. Representative local LiteBans dry-run/import/comparison/interruption recovery.
3. Distributed Velocity/HUB/SMP with authenticated channels.
4. Java/Bedrock command, GUI, and interaction acceptance.
5. Provider-present/missing staging for RoseChat, currency, ProtocolLib, Floodgate, ViaVersion, and other integrations.
6. Non-production Discord outage/retry/rate-limit testing.
7. Production-like query/load testing with 100+ players and realistic history.
8. Issue #43 and its 168-hour shadow window.
9. Final rehearsal, emergency freeze, rollback, activation, and production authorization.

None occurred or started during this audit.

## 11. Documentation discrepancies

- `UPGRADE-MANIFEST.md` records an earlier migration boundary and is historical.
- `reports/CODACY-BASELINE.md` is not an exact-main analysis verdict.
- `reports/REQUIREMENTS-MATRIX.md` and `Implementation-Status.md` omit the appeal defect, inert tools, Bedrock bug, absent fake tooling, absent market/reputation workflows, low coverage, and blocked staging.
- `Development-Blueprint.md` is intended architecture, not proof of provider activation or completed workflows.
- Handoffs prove prior reports at prior SHAs, not current behavior by themselves.
- Schemas such as `case_evidence`, `market_compliance_cases`, `reputation_blacklists`, and `configuration_versions` do not establish functioning application workflows.
- Soft dependencies in `plugin.yml` do not prove integrations.
- Current source does contain Discord producers; a producerless-outbox claim would be wrong.
- The Pi check is red because the staging build never started due to billing/spend status, not because Paper failed to boot.

Goals, matrix, blueprint, status, and historical manifests were intentionally not edited to make them agree.

## 12. Audit limitations and uncertainty

- Local DNS could not clone GitHub; inspection used the connector plus exact-main JaCoCo source and packaged JARs.
- No production system, credential, database, player data, Discord route, hosting, or private provider was accessed.
- Branch-protection settings were inaccessible to the connector.
- Codacy coverage upload succeeded, but exact-main analysis was not exposed as a commit check.
- No current-SHA Pi execution occurred because the build never received a runner.
- Optional provider internals are outside this repository.
- No production-volume benchmark was run.
- Bedrock behavior was inspected but not executed.
- Plausible but unproved concerns were classified `IMPLEMENTED_UNVERIFIED`, not confirmed defects.

## 13. Planning handoff

### Highest-risk incomplete IDs

AUD-APPEAL-003, AUD-PUNISH-003, AUD-SEC-005, AUD-WEB-002, AUD-ID-004, AUD-STAFF-004, AUD-RUNTIME-001, AUD-RUNTIME-002, AUD-ARCH-004, and AUD-ASSET-001 through AUD-ASSET-005.

### Dependencies

- Appeals depend on exact-sanction mutation, website punishment IDs, cases/history, and audit events.
- Staff tools depend on staff mode, freeze, reports, vanish, inspect, teleport, and cheat-tool decisions.
- Reports depend on identity, RoseChat PM evidence, client evidence, Discord, and cases.
- Assets depend on journals, provider contracts, cases, rollback, and switch fences.
- Network identity depends on Velocity login, keys, retention, sanctions, migration, and Bedrock correctness.
- LiteBans acceptance depends on migration, identity, sanctions/history, distributed staging, and authorization.

### Sequential overlap

- `WebsiteAppealEndpoint`, sanction mutation services/stores, and combined-sanction history.
- `StaffModeManager` and hotbar tool listeners.
- Inventory journal, confiscation, economy journal, provider rollback, and restoration.
- Persistent channel, authority mode, switch fences, and cutover.
- Policy snapshots, migration mapping, and history rendering.

### Parallel-safe when bounded

- Velocity reload design versus fake-base design.
- `enthusia-site` UX versus internal runtime testing, provided appeal mutation contract is frozen until corrected.
- Documentation reconciliation versus product work.
- Bedrock test planning versus migration implementation.

### Size guidance

Too large for one normal worker: all provider-backed asset workflows; complete cheat/fake suite; distributed cutover staging; repository-wide runtime coverage closure.

Likely focused packages: exact-sanction appeal mutation; Bedrock platform recording; staff-tool dispatcher excluding cheat/fake features; one-runtime startup re-bootstrap; Velocity reload; one bounded platform-test family.

Needs Codex/private environment: representative LiteBans data, destructive rollback, multi-process staging, Java/Bedrock/provider acceptance, broad runtime test expansion.

Needs external repositories: `enthusia-site`, RoseChat provider, EnthusiaCurrency, EnthusiaMarket, EnthusiaCommend.

This is not the final implementation package list.

## 14. Final conclusion

- All requested source areas A through V were audited.
- All major requirements were classified in a 99-item ledger.
- No product code, migration, workflow, plugin metadata, runtime configuration, staging control, or deployment behavior was changed.
- The audit is complete as a documentation artifact.
- The project is **not complete**. It is structurally established and feature-incomplete.
- The next step is owner/planner review followed by bounded implementation-package definition. No package is preselected by this audit.
