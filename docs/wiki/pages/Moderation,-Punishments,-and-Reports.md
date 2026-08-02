# Moderation, Punishments, and Reports

**Estimated group completion: about 56%.**

This group covers the moderation record model, punishment selection and approval,
escalation policy, sanction changes, appeals, player reports, retained evidence,
client evidence and strict automod.

- Return to [[Feature Completion Status|Implementation-Status]].
- Staff procedures: [[Punishment System]] and [[Reports and Evidence]].
- Commands and permission nodes: [[Commands and Permissions]].
- Source-level traces: [[Developer Code Guide]].

> Percentages are rounded planning estimates. Exact evidence and blockers remain
> in the
> [requirements matrix](https://github.com/wsg138/EnthusiaStaff/blob/main/reports/REQUIREMENTS-MATRIX.md).

## Find a moderation area

| Area | Complete | What it does | Jump to details |
| --- | ---: | --- | --- |
| Cases, sanctions and audit | **70%** | Records who acted, what happened, why, which sanctions applied and how state changed. | [Cases and sanctions](#cases-sanctions-and-audit) |
| Punishment commands and GUI | **72%** | Guides staff from a target and reason to an authorized, reviewed punishment. | [Punishment interface](#punishment-commands-and-gui) |
| Durable drafts and resume | **85%** | Preserves unfinished punishment work across ordinary interruption. | [Drafts](#durable-drafts-and-resume) |
| Rank authority and approval requests | **82%** | Prevents Helpers and Developers from bypassing required review. | [Authority](#rank-authority-and-approval-requests) |
| Request notifications and recovery | **35%** | Notifies requesters and eligible reviewers without losing or duplicating delivery. | [Notifications](#request-notifications-and-recovery) |
| Escalation policy | **52%** | Selects configured steps from reason families, history, recency and decay. | [Escalation](#escalation-policy) |
| History and sanction changes | **34%** | Reads the full timeline and precisely ends, reduces, revokes or overturns one sanction. | [History and changes](#history-and-sanction-changes) |
| Appeals | **35%** | Connects player appeals and reviewer decisions to audited sanction state. | [Appeals](#appeals) |
| Report submission and queues | **68%** | Accepts private player reports and coordinates staff claims and closure. | [Reports](#report-submission-and-queues) |
| Evidence capture, privacy and retention | **56%** | Stores bounded chat/client context while keeping private evidence internal. | [Evidence](#evidence-capture-privacy-and-retention) |
| Report GUI | **20%** | Provides staff queue, detail and action interfaces. | [Report GUI](#report-queue-and-detail-gui) |
| Strict automod and client evidence | **35%** | Detects exact high-confidence chat violations and records point-in-time client information. | [Automod and clients](#strict-automod-and-client-evidence) |

## Cases, sanctions, and audit

### What it does

A case is the durable explanation for a moderation decision. It links the target,
actor, stable reason, public/private visibility, evidence, sanctions, provider
actions, appeal state and audit history. A sanction is one enforceable result,
such as a warning, mute, ban or network ban.

### Staff-facing surfaces

- `/punish`, `/ban`, `/mute`, `/warn`, `/kick`, `/ipban`
- `/removepunishment`, `/unban`, `/unmute`, `/removewarning`
- public punishment projections and the future `/history` view

### Primary files

- [Punishment service](https://github.com/wsg138/EnthusiaStaff/blob/main/domain/src/main/java/net/enthusia/staff/domain/application/PunishmentService.java)
- [Case domain](https://github.com/wsg138/EnthusiaStaff/tree/main/domain/src/main/java/net/enthusia/staff/domain/casefile)
- [Sanction domain](https://github.com/wsg138/EnthusiaStaff/tree/main/domain/src/main/java/net/enthusia/staff/domain/sanction)
- [Moderation store](https://github.com/wsg138/EnthusiaStaff/blob/main/persistence/src/main/java/net/enthusia/staff/persistence/JdbcModerationStore.java)
- [Case review store](https://github.com/wsg138/EnthusiaStaff/blob/main/persistence/src/main/java/net/enthusia/staff/persistence/JdbcCaseReviewStore.java)
- [Public punishment registry](https://github.com/wsg138/EnthusiaStaff/blob/main/persistence/src/main/java/net/enthusia/staff/persistence/JdbcPublicPunishmentRegistry.java)

### What remains

Complete all sanction types, combined-sanction behavior, readable full timelines,
visibility mutation authorization and real Paper/Velocity/provider enforcement.

## Punishment commands and GUI

### What it does

The central punishment workflow resolves the target, selects a stable reason,
loads relevant history, calculates a recommendation, checks rank authority,
collects notes/evidence and commits one durable case after confirmation. Direct
commands are filtered entry points into the same policy path.

### Primary files

- [Punishment command](https://github.com/wsg138/EnthusiaStaff/blob/main/paper/src/main/java/net/enthusia/staff/paper/command/PunishmentCommand.java)
- [Punishment GUI package](https://github.com/wsg138/EnthusiaStaff/tree/main/paper/src/main/java/net/enthusia/staff/paper/punishment)
- [Punishment draft workflow](https://github.com/wsg138/EnthusiaStaff/blob/main/domain/src/main/java/net/enthusia/staff/domain/application/PunishmentDraftWorkflow.java)
- [Punishment service](https://github.com/wsg138/EnthusiaStaff/blob/main/domain/src/main/java/net/enthusia/staff/domain/application/PunishmentService.java)
- [Reason policies](https://github.com/wsg138/EnthusiaStaff/blob/main/paper/src/main/resources/reason-policies.yml)

### Related pages

- [[Punishment System]]
- [[Staff Quick Start|Moderator-Quick-Start]]
- [[Roles and Permissions|Rank-Authority]]

### What remains

Complete every category/reason/review screen, optional sanction control, modular
GUI configuration, stale-state revalidation, Bedrock layouts, offline behavior,
reload behavior and real multi-server staging.

## Durable drafts and resume

### What it does

An unfinished punishment draft is stored so staff can close the interface and
resume later without reconstructing the target, reason and note from memory. The
workflow must revalidate the recommendation when history or policy changes.

### Primary files

- [Draft workflow](https://github.com/wsg138/EnthusiaStaff/blob/main/domain/src/main/java/net/enthusia/staff/domain/application/PunishmentDraftWorkflow.java)
- [Draft domain models](https://github.com/wsg138/EnthusiaStaff/tree/main/domain/src/main/java/net/enthusia/staff/domain/application)
- [JDBC draft store](https://github.com/wsg138/EnthusiaStaff/blob/main/persistence/src/main/java/net/enthusia/staff/persistence/JdbcPunishmentDraftStore.java)
- [Punishment GUI package](https://github.com/wsg138/EnthusiaStaff/tree/main/paper/src/main/java/net/enthusia/staff/paper/punishment)

### What remains

Prove complete crash, logout, server-switch, expiration, policy-version and
multi-server ownership behavior.

## Rank authority and approval requests

### What it does

Authorization is rechecked in central services rather than trusting a command or
permission node. Helpers can apply authorized temporary results, but permanent
results become approval requests. Developers may prepare requests but cannot
mutate punishment state or approve their own work.

### Primary files

- [Authorization policy](https://github.com/wsg138/EnthusiaStaff/blob/main/domain/src/main/java/net/enthusia/staff/domain/auth/DefaultAuthorizationPolicy.java)
- [Punishment approval rules](https://github.com/wsg138/EnthusiaStaff/blob/main/domain/src/main/java/net/enthusia/staff/domain/application/PunishmentApprovalRules.java)
- [Punishment request service](https://github.com/wsg138/EnthusiaStaff/blob/main/domain/src/main/java/net/enthusia/staff/domain/application/PunishmentRequestService.java)
- [Paper request command handler](https://github.com/wsg138/EnthusiaStaff/blob/main/paper/src/main/java/net/enthusia/staff/paper/command/PunishmentRequestCommandHandler.java)
- [Request GUI package](https://github.com/wsg138/EnthusiaStaff/tree/main/paper/src/main/java/net/enthusia/staff/paper/punishment)
- [JDBC request store](https://github.com/wsg138/EnthusiaStaff/blob/main/persistence/src/main/java/net/enthusia/staff/persistence/JdbcPunishmentRequestStore.java)

### What remains

Complete external website/provider parity, live queue refresh, Bedrock
presentation, offline reviewer flows, multi-server contention and production
staging.

## Request notifications and recovery

### What it does

The finished notification lifecycle informs the requester, eligible reviewers and
operational administrators when a request is submitted, claimed, approved,
denied, expired or externally fulfilled. Delivery must survive restart, avoid
premature acknowledgement and recheck recipient authority immediately before
presentation.

### Current development

PR #27 contains extensive durable recipient-specific alert, reconnect, recovery,
configuration and Folia-safe delivery work. Until merged, it is active branch
evidence rather than the behavior of `main`.

### Primary areas

- [Punishment request domain](https://github.com/wsg138/EnthusiaStaff/tree/main/domain/src/main/java/net/enthusia/staff/domain/application)
- [Discord delivery domain](https://github.com/wsg138/EnthusiaStaff/tree/main/domain/src/main/java/net/enthusia/staff/domain/discord)
- [Persistence stores](https://github.com/wsg138/EnthusiaStaff/tree/main/persistence/src/main/java/net/enthusia/staff/persistence)
- [Paper punishment presentation](https://github.com/wsg138/EnthusiaStaff/tree/main/paper/src/main/java/net/enthusia/staff/paper/punishment)
- [Velocity Discord worker](https://github.com/wsg138/EnthusiaStaff/blob/main/velocity/src/main/java/net/enthusia/staff/velocity/DiscordOutboxWorker.java)

### What remains

Merge/reconcile the active work, then prove online/offline delivery, restart,
lease recovery, duplicate safety, authorization changes, Discord delivery and
real multi-server/Folia behavior.

## Escalation policy

### What it does

Each stable reason belongs to a family and ladder. Relevant historical actions,
severity relationships, recent reoffending and configured decay determine the
recommended step. Existing sanctions retain their original expectation when
policy files change.

### Primary files

- [Escalation domain](https://github.com/wsg138/EnthusiaStaff/tree/main/domain/src/main/java/net/enthusia/staff/domain/escalation)
- [Escalation engine](https://github.com/wsg138/EnthusiaStaff/blob/main/domain/src/main/java/net/enthusia/staff/domain/escalation/EscalationEngine.java)
- [Reason policy loader](https://github.com/wsg138/EnthusiaStaff/blob/main/paper/src/main/java/net/enthusia/staff/paper/config/ReasonPolicyConfigurationLoader.java)
- [Reason policies](https://github.com/wsg138/EnthusiaStaff/blob/main/paper/src/main/resources/reason-policies.yml)

### What remains

Complete all family relationships, severity jumps, clean-period decay, recency,
finite/permanent ladder behavior, renamed/removed IDs, aliases and combined
recommendations.

## History and sanction changes

### What it does

History should show the complete case/sanction timeline without leaking private
evidence. Mutation workflows select one exact sanction and distinguish reduction,
ending, revocation, removal from escalation and full overturn while preserving
the original record.

### Commands

- `/removepunishment`
- `/unban`
- `/unmute`
- `/removewarning` and `/unwarn`
- required but not yet registered: `/history`

### Primary files

- [Sanction change service](https://github.com/wsg138/EnthusiaStaff/blob/main/domain/src/main/java/net/enthusia/staff/domain/application/SanctionChangeService.java)
- [Sanction command](https://github.com/wsg138/EnthusiaStaff/blob/main/paper/src/main/java/net/enthusia/staff/paper/command/SanctionChangeCommand.java)
- [Sanction UI package](https://github.com/wsg138/EnthusiaStaff/tree/main/paper/src/main/java/net/enthusia/staff/paper/sanction)
- [Sanction mutation store](https://github.com/wsg138/EnthusiaStaff/blob/main/persistence/src/main/java/net/enthusia/staff/persistence/JdbcSanctionMutationStore.java)
- [Case review store](https://github.com/wsg138/EnthusiaStaff/blob/main/persistence/src/main/java/net/enthusia/staff/persistence/JdbcCaseReviewStore.java)

### What remains

Register and build `/history`; complete exact semantics for every change type;
add durable overturn requests, expiry and reviewer alerts; prove combined-sanction
safety, retries, notifications and end-to-end staging.

## Appeals

### What it does

Players submit appeals through a private site. Authorized reviewers inspect a
sanitized case projection and issue an audited decision that may leave the
sanction unchanged or invoke the central sanction-change service.

### Primary files

- [Website domain](https://github.com/wsg138/EnthusiaStaff/tree/main/domain/src/main/java/net/enthusia/staff/domain/website)
- [Website moderation store](https://github.com/wsg138/EnthusiaStaff/blob/main/persistence/src/main/java/net/enthusia/staff/persistence/JdbcWebsiteModerationStore.java)
- [Website appeal store](https://github.com/wsg138/EnthusiaStaff/blob/main/persistence/src/main/java/net/enthusia/staff/persistence/JdbcWebsiteAppealStore.java)
- [Website appeal endpoint](https://github.com/wsg138/EnthusiaStaff/blob/main/velocity/src/main/java/net/enthusia/staff/velocity/WebsiteAppealEndpoint.java)
- [Website API router](https://github.com/wsg138/EnthusiaStaff/blob/main/velocity/src/main/java/net/enthusia/staff/velocity/WebsiteApiRouter.java)

### What remains

Complete authenticated site sessions, role controls, reviewer decisions,
reopening, notifications, CSRF/rate limits and one audited path into sanction
changes.

## Report submission and queues

### What it does

Players submit private reports. The system validates the target, prevents
self-reporting, applies cooldown/merge rules, stores bounded context and exposes
revisioned staff queues. Staff claim, investigate and close one current revision
without overwriting newer work.

### Commands

- `/report <player> <reason-id> <description>`
- `/reports`
- `/reports view`, `claim`, `close`, `no-violation`, `review`

### Primary files

- [Report command](https://github.com/wsg138/EnthusiaStaff/blob/main/paper/src/main/java/net/enthusia/staff/paper/command/ReportCommand.java)
- [Reports command](https://github.com/wsg138/EnthusiaStaff/blob/main/paper/src/main/java/net/enthusia/staff/paper/command/ReportsCommand.java)
- [Report domain](https://github.com/wsg138/EnthusiaStaff/tree/main/domain/src/main/java/net/enthusia/staff/domain/report)
- [JDBC report facade](https://github.com/wsg138/EnthusiaStaff/blob/main/persistence/src/main/java/net/enthusia/staff/persistence/JdbcReportStore.java)
- [Submission store](https://github.com/wsg138/EnthusiaStaff/blob/main/persistence/src/main/java/net/enthusia/staff/persistence/JdbcReportSubmissionStore.java)
- [Submission replay](https://github.com/wsg138/EnthusiaStaff/blob/main/persistence/src/main/java/net/enthusia/staff/persistence/JdbcReportSubmissionReplay.java)
- [Query store](https://github.com/wsg138/EnthusiaStaff/blob/main/persistence/src/main/java/net/enthusia/staff/persistence/JdbcReportQueryStore.java)
- [State store](https://github.com/wsg138/EnthusiaStaff/blob/main/persistence/src/main/java/net/enthusia/staff/persistence/JdbcReportStateStore.java)

### Related pages

- [[Reports and Evidence]]
- [[Privacy and Data Handling]]

### What remains

Complete configurable reasons/cooldowns, live refresh, full GUI actions,
multi-server contention, production-volume behavior and client/server context.

## Evidence capture, privacy, and retention

### What it does

Reports can retain bounded public-chat context, private-message context supplied
by a supported provider, coordinates and a point-in-time client snapshot. Private
messages, reporter identity and coordinates remain staff-only and must never enter
public punishment output or ordinary Discord payloads.

### Primary files

- [Chat context buffer](https://github.com/wsg138/EnthusiaStaff/blob/main/paper/src/main/java/net/enthusia/staff/paper/report/ChatContextBuffer.java)
- [Paper evidence maintenance](https://github.com/wsg138/EnthusiaStaff/blob/main/paper/src/main/java/net/enthusia/staff/paper/report/ReportEvidenceMaintenance.java)
- [JDBC evidence maintenance](https://github.com/wsg138/EnthusiaStaff/blob/main/persistence/src/main/java/net/enthusia/staff/persistence/JdbcReportEvidenceMaintenance.java)
- [Client integrations](https://github.com/wsg138/EnthusiaStaff/tree/main/paper/src/main/java/net/enthusia/staff/paper/client)

### What remains

Implement the supported RoseChat private-message bridge, verify message ordering
and cross-server context, finish staff privacy review and stage physical retention
under production conditions.

## Report queue and detail GUI

### What it does

The planned staff GUI organizes Open, Mine, Claimed, Awaiting Review and Recently
Closed reports. Detail actions include claim, spectate, teleport, freeze, punish,
close and no violation.

### Primary areas

- [Report domain](https://github.com/wsg138/EnthusiaStaff/tree/main/domain/src/main/java/net/enthusia/staff/domain/report)
- [Paper report package](https://github.com/wsg138/EnthusiaStaff/tree/main/paper/src/main/java/net/enthusia/staff/paper/report)
- [Reports command](https://github.com/wsg138/EnthusiaStaff/blob/main/paper/src/main/java/net/enthusia/staff/paper/command/ReportsCommand.java)

### What remains

Build the GUI, detail actions, modular configuration, pagination/live refresh,
permissions and Java/Bedrock layouts.

## Strict automod and client evidence

### What it does

Strict automod is intended to cancel exact high-confidence violations before
ordinary RoseChat recipients see them, then create durable case/evidence/audit and
Discord output. Client evidence records protocol, platform, reported brand and
supported AutoClicker handshake information without treating spoofable metadata as
proof by itself.

### Primary files

- [Automod package](https://github.com/wsg138/EnthusiaStaff/tree/main/paper/src/main/java/net/enthusia/staff/paper/automod)
- [Client package](https://github.com/wsg138/EnthusiaStaff/tree/main/paper/src/main/java/net/enthusia/staff/paper/client)
- [Evidence domain](https://github.com/wsg138/EnthusiaStaff/tree/main/domain/src/main/java/net/enthusia/staff/domain/evidence)
- [AutoClicker contract](https://github.com/wsg138/EnthusiaStaff/blob/main/integration-contracts/src/main/java/net/enthusia/staff/integration/contracts/EnthusiaAutoClickerClientApi.java)
- [Client command](https://github.com/wsg138/EnthusiaStaff/blob/main/paper/src/main/java/net/enthusia/staff/paper/command/ClientCommand.java)

### What remains

Reconstruct AutoClicker and RoseChat providers, prove pre-broadcast ordering,
false-positive resistance, bounded retention, explicit capture, reload behavior,
and durable case/audit/Discord creation.

## Related pages

- [[Feature Completion Status|Implementation-Status]]
- [[Remaining Development Map|Development-Blueprint]]
- [[Punishment System]]
- [[Reports and Evidence]]
- [[Commands and Permissions]]
- [[Roles and Permissions|Rank-Authority]]
- [[Privacy and Data Handling]]
- [[Developer Code Guide]]
