# Moderation, Punishments, and Reports

This hub covers cases, sanctions, punishment selection and approval, escalation, history, appeals, player reports, retained evidence, and strict automod/client evidence.

For staff procedure, start with [[Punishment System]] or [[Reports and Evidence]]. For detailed source tracing, use [[Developer Code Guide]]. For review invariants, use [[Code Review Guide]].

## Quick status

| Area | Merged-main state | Main limitation |
| --- | --- | --- |
| Cases, sanctions and audit | **Implemented, not staging-verified** | Representative enforcement/provider/site/runtime acceptance remains. |
| Punishment commands, GUI and durable drafts | **Implemented, not staging-verified** | Broader modular GUI/configuration and Java/Bedrock multi-runtime usability remain. |
| Rank authority and approval requests | **Implemented, not staging-verified** | Website/provider parity and distributed reviewer/requester acceptance remain. |
| Request notifications | **Available with limitations** | Durable recipient-specific delivery foundations are merged; complete external/Discord/runtime presentation remains. |
| Escalation policy | **Partial** | Broader family relationships, combined recommendations and modular policy configuration remain. |
| History and exact sanction changes | **Implemented, not staging-verified** | Representative staff/provider/site acceptance remains. |
| Appeals and website moderation workflow | **Implemented, not staging-verified** | Private site deployment/security/provider/runtime acceptance remains. |
| Report submission, queues and GUI | **Available with limitations** | RoseChat private-message provider and complete distributed notification/provider staging remain. |
| Evidence capture/privacy/retention | **Available with limitations** | Provider-specific PM/client evidence and full operational privacy review remain. |
| Strict automod/client evidence | **Partial** | Supported RoseChat/AutoClicker provider contracts and representative false-positive/runtime validation remain. |

## Cases, sanctions and authoritative writes

A **case** is the durable explanation for a moderation decision. It links the actor, target, stable reason, evidence references, visibility, sanctions, requests, appeals and audit history. A **sanction** is one enforceable outcome such as a warning, mute, ban or network ban.

Primary source:

- [PunishmentService](https://github.com/wsg138/EnthusiaStaff/blob/main/domain/src/main/java/net/enthusia/staff/domain/application/PunishmentService.java)
- [case domain](https://github.com/wsg138/EnthusiaStaff/tree/main/domain/src/main/java/net/enthusia/staff/domain/casefile)
- [sanction domain](https://github.com/wsg138/EnthusiaStaff/tree/main/domain/src/main/java/net/enthusia/staff/domain/sanction)
- [JdbcModerationStore](https://github.com/wsg138/EnthusiaStaff/blob/main/persistence/src/main/java/net/enthusia/staff/persistence/JdbcModerationStore.java)
- [JdbcCaseReviewStore](https://github.com/wsg138/EnthusiaStaff/blob/main/persistence/src/main/java/net/enthusia/staff/persistence/JdbcCaseReviewStore.java)

Important review properties are combined-sanction atomicity, idempotency, locked authority checks, stable reason identity, append-only history, safe public projections and durable network/notification state. See [[Code Review Guide]].

## Punishment interface and durable drafts

The main staff path starts with `/punish <player>` or a filtered command such as `/ban`, `/mute`, `/warn`, `/kick`, or `/ipban`. Presentation should lead into the same central policy rather than contain a second punishment implementation.

Primary paths:

- [PunishmentCommand](https://github.com/wsg138/EnthusiaStaff/blob/main/paper/src/main/java/net/enthusia/staff/paper/command/PunishmentCommand.java)
- [paper punishment package](https://github.com/wsg138/EnthusiaStaff/tree/main/paper/src/main/java/net/enthusia/staff/paper/punishment)
- [PunishmentDraftWorkflow](https://github.com/wsg138/EnthusiaStaff/blob/main/domain/src/main/java/net/enthusia/staff/domain/application/PunishmentDraftWorkflow.java)
- [JdbcPunishmentDraftStore](https://github.com/wsg138/EnthusiaStaff/blob/main/persistence/src/main/java/net/enthusia/staff/persistence/JdbcPunishmentDraftStore.java)
- [reason policies](https://github.com/wsg138/EnthusiaStaff/blob/main/paper/src/main/resources/reason-policies.yml)

Drafts are durable and resumable, but a resumed draft must still revalidate current history, policy, target state and authority before final commit.

Staff procedure: [[Punishment System]].

## Authority and approval requests

Permission nodes are an early boundary, not the final authority decision. Central services enforce rank semantics and hierarchy again around authoritative writes.

Current policy includes:

- Helper may apply authorized temporary outcomes; permanent outcomes become approval requests.
- Developer is a technical/request-preparation role and cannot directly punish or approve.
- Self-approval and unauthorized review are blocked.
- Higher-rank and issuing-rank rules remain part of service/transaction authorization.
- Console/`SYSTEM` semantics must be explicit rather than treated as unlimited ordinary staff authority.

Primary paths:

- [DefaultAuthorizationPolicy](https://github.com/wsg138/EnthusiaStaff/blob/main/domain/src/main/java/net/enthusia/staff/domain/auth/DefaultAuthorizationPolicy.java)
- [PunishmentApprovalRules](https://github.com/wsg138/EnthusiaStaff/blob/main/domain/src/main/java/net/enthusia/staff/domain/application/PunishmentApprovalRules.java)
- [PunishmentRequestService](https://github.com/wsg138/EnthusiaStaff/blob/main/domain/src/main/java/net/enthusia/staff/domain/application/PunishmentRequestService.java)
- [JdbcPunishmentRequestStore](https://github.com/wsg138/EnthusiaStaff/blob/main/persistence/src/main/java/net/enthusia/staff/persistence/JdbcPunishmentRequestStore.java)

See [[Roles and Permissions|Rank-Authority]].

## Request notifications

Recipient-specific alert/delivery state is durable so a request can survive disconnect/restart without treating presentation as the authoritative decision. Reviewers should verify that recipient authority is checked when an alert is presented and that retries do not duplicate a moderation action.

Relevant areas:

- `domain/application/` request workflow
- `domain/discord/`
- persistence request/alert stores
- `paper/punishment/`
- `velocity/DiscordOutboxWorker.java`

The remaining gap is primarily complete provider/Discord/multi-runtime presentation and acceptance, not an unmerged historical request-notification branch.

## Escalation policy

Stable reason IDs, families, ladder ordinals, aliases/removed metadata, recommendation snapshots and explicit decay eligibility are represented in merged code. The current system still has broader policy/configuration work before every goal-defined escalation relationship is complete.

Primary paths:

- [escalation domain](https://github.com/wsg138/EnthusiaStaff/tree/main/domain/src/main/java/net/enthusia/staff/domain/escalation)
- [EscalationEngine](https://github.com/wsg138/EnthusiaStaff/blob/main/domain/src/main/java/net/enthusia/staff/domain/escalation/EscalationEngine.java)
- [ReasonPolicyConfigurationLoader](https://github.com/wsg138/EnthusiaStaff/blob/main/paper/src/main/java/net/enthusia/staff/paper/config/ReasonPolicyConfigurationLoader.java)
- [reason policies](https://github.com/wsg138/EnthusiaStaff/blob/main/paper/src/main/resources/reason-policies.yml)

A policy-file edit must not silently reinterpret the original stored decision behind an existing sanction.

## History and exact sanction lifecycle

Merged history/lifecycle behavior includes:

```text
/history <player|uuid> [page]
/case [view] <case-id>
/estaff sanction reduce <sanction-id> <expiration-or-duration> <reason>
/estaff sanction end <sanction-id> <reason>
/estaff sanction revoke <sanction-id> <reason>
/estaff sanction overturn <sanction-id> [--appeal <appeal-id>] <reason>
```

The lifecycle targets one exact sanction. Reduction, early ending, revocation and overturn preserve the original case/sanction history and append the new decision rather than deleting history. Optional appeal/request linkage must belong to the same relevant case/sanction.

Primary paths:

- `domain/history/`
- [SanctionChangeService](https://github.com/wsg138/EnthusiaStaff/blob/main/domain/src/main/java/net/enthusia/staff/domain/application/SanctionChangeService.java)
- `paper/command/HistoryCommand.java`
- `paper/command/CaseCommand.java`
- [SanctionLifecycleCommand](https://github.com/wsg138/EnthusiaStaff/blob/main/paper/src/main/java/net/enthusia/staff/paper/command/SanctionLifecycleCommand.java)
- [JdbcModerationHistoryStore](https://github.com/wsg138/EnthusiaStaff/blob/main/persistence/src/main/java/net/enthusia/staff/persistence/JdbcModerationHistoryStore.java)
- [JdbcExactSanctionMutationStore](https://github.com/wsg138/EnthusiaStaff/blob/main/persistence/src/main/java/net/enthusia/staff/persistence/JdbcExactSanctionMutationStore.java)
- `V14__punishment_history_and_exact_sanction_changes.sql`

## Appeals and website moderation

Current aggregate source includes a scoped punishment/appeal workflow rather than only a future bridge. V17 adds appeal-workflow persistence and the Velocity bridge includes dedicated appeal workflow handling.

Primary paths:

- [website domain](https://github.com/wsg138/EnthusiaStaff/tree/main/domain/src/main/java/net/enthusia/staff/domain/website)
- [JdbcWebsiteModerationStore](https://github.com/wsg138/EnthusiaStaff/blob/main/persistence/src/main/java/net/enthusia/staff/persistence/JdbcWebsiteModerationStore.java)
- [JdbcWebsiteAppealWorkflowStore](https://github.com/wsg138/EnthusiaStaff/blob/main/persistence/src/main/java/net/enthusia/staff/persistence/JdbcWebsiteAppealWorkflowStore.java)
- [WebsiteApiRouter](https://github.com/wsg138/EnthusiaStaff/blob/main/velocity/src/main/java/net/enthusia/staff/velocity/WebsiteApiRouter.java)
- [WebsiteAppealWorkflowEndpoint](https://github.com/wsg138/EnthusiaStaff/blob/main/velocity/src/main/java/net/enthusia/staff/velocity/WebsiteAppealWorkflowEndpoint.java)
- `V17__website_appeal_workflow.sql`

This is still **not** evidence that the private site is deployed, publicly launched, or production-accepted. Authentication, runtime/provider integration, operational deployment and privacy/security acceptance remain separate gates.

## Reports and evidence

Current merged report behavior includes player submission, bounded queues, detail/action GUI, text/Bedrock fallbacks, optimistic revision fencing, configurable policy/GUI snapshots, bounded retained evidence and cleanup.

Primary paths:

- [ReportCommand](https://github.com/wsg138/EnthusiaStaff/blob/main/paper/src/main/java/net/enthusia/staff/paper/command/ReportCommand.java)
- [ReportsCommand](https://github.com/wsg138/EnthusiaStaff/blob/main/paper/src/main/java/net/enthusia/staff/paper/command/ReportsCommand.java)
- [report domain](https://github.com/wsg138/EnthusiaStaff/tree/main/domain/src/main/java/net/enthusia/staff/domain/report)
- [JdbcReportStore](https://github.com/wsg138/EnthusiaStaff/blob/main/persistence/src/main/java/net/enthusia/staff/persistence/JdbcReportStore.java)
- `JdbcReportSubmissionStore.java`
- `JdbcReportSubmissionReplay.java`
- `JdbcReportQueryStore.java`
- `JdbcReportStateStore.java`
- `JdbcReportEvidenceMaintenance.java`
- [paper report package](https://github.com/wsg138/EnthusiaStaff/tree/main/paper/src/main/java/net/enthusia/staff/paper/report)

The supported RoseChat private-message bridge and complete provider/Discord/distributed staging are still limitations. A report is investigation input, not proof of a violation.

Staff procedure: [[Reports and Evidence]]. Configuration: [[Report Configuration]]. Privacy: [[Privacy and Data Handling]].

## Strict automod and client evidence

Automod/client-evidence foundations exist, but the finished behavior depends on supported provider contracts and false-positive-resistant runtime integration.

Relevant areas:

- `domain/evidence/`
- `paper/client/`
- `paper/automod/`
- `integration-contracts/`

Do not invent RoseChat or client-provider APIs. If the required provider contract is unavailable, the dependent feature should degrade explicitly instead of guessing through reflection, raw SQL or command dispatch.

## Go deeper

- [[Punishment System]] — staff punishment procedure.
- [[Reports and Evidence]] — report/evidence procedure.
- [[Report Configuration]] — report policy/GUI configuration.
- [[Roles and Permissions|Rank-Authority]] — rank authority.
- [[Privacy and Data Handling]] — evidence/public-data boundaries.
- [[Developer Code Guide]] — detailed source traces.
- [[Code Review Guide]] — authority, transaction, distributed and privacy review checklist.
- [[Build and Testing]] — what automated versus runtime evidence proves.
- [[Implementation Status]] — overall product status.