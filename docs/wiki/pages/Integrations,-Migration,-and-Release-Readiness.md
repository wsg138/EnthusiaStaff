# Integrations, Migration, and Release Readiness

This hub covers provider boundaries, durable external delivery, the private punishment/appeal site, LiteBans migration and shadow comparison, cutover, distributed client/runtime acceptance, failure testing, and the evidence required before production authority can move.

For operator procedure, use [[Installation]], [[LiteBans Migration]], [[Shadow Mode and Cutover]], or [[Recovery and Troubleshooting]]. For provider behavior use [[Integrations]]. For source tracing and review use [[Developer Code Guide]] and [[Code Review Guide]].

## Quick status

| Area | Merged-main state | Main limitation |
| --- | --- | --- |
| Durable Discord delivery | **Partial** | Complete event routing/privacy/dead-letter/outage/operator acceptance remains. |
| Restricted website bridge | **Implemented, not staging-verified** | Private deployment/security/overload/secret-rotation/runtime acceptance remains. |
| Punishment/appeal website workflow | **Implemented, not staging-verified** | Aggregate source is present; private deployment/provider/public-launch acceptance remains. |
| Enthusia-owned provider contracts | **Partial / provider-dependent** | Several provider-side APIs/implementations are incomplete or unavailable. |
| Optional third-party integrations | **Available with limitations** | Exact-version/provider failure/classloader/client staging remains. |
| LiteBans schema inspection/import | **Implemented, not production-accepted** | Representative private data, volume, interruption/resume and final reconciliation remain. |
| Shadow comparison | **Implemented, not production-accepted** | The required 168-hour production-like observation has not been accepted. |
| Cutover/recovery coordination | **Implemented foundations; acceptance blocked** | Real final-import/writer-fence/restart/rollback/emergency-recovery acceptance remains. |
| Java/Bedrock/Folia/provider topology | **Not staging-verified as a complete candidate** | One exact release candidate still needs representative distributed acceptance. |
| Load/saturation/process-kill | **Incomplete** | High-risk workflows still need representative queue/load/kill/recovery evidence. |
| Production cutover/release | **Blocked pending acceptance** | LiteBans remains authoritative until all required evidence and owner authorization exist. |

## Durable Discord delivery

A committed moderation operation may write a sanitized Discord event to a durable outbox in the same transaction. Velocity leases and sends due events with bounded retry. Discord failure must not roll back a valid moderation commit or silently make a durable moderation action disappear.

Primary paths:

- [Discord domain](https://github.com/wsg138/EnthusiaStaff/tree/main/domain/src/main/java/net/enthusia/staff/domain/discord)
- [JdbcDiscordOutboxStore](https://github.com/wsg138/EnthusiaStaff/blob/main/persistence/src/main/java/net/enthusia/staff/persistence/JdbcDiscordOutboxStore.java)
- [DiscordOutboxWorker](https://github.com/wsg138/EnthusiaStaff/blob/main/velocity/src/main/java/net/enthusia/staff/velocity/DiscordOutboxWorker.java)
- [VelocityConfiguration](https://github.com/wsg138/EnthusiaStaff/blob/main/velocity/src/main/java/net/enthusia/staff/velocity/VelocityConfiguration.java)

Review producer-side privacy, lease/fence behavior, bounded backoff, terminal/dead-letter handling, manual recovery and outage behavior. The worker should not be relied on as a universal late-stage privacy scrubber.

## Restricted website bridge

The Velocity website bridge is a restricted inbound boundary for the trusted site component, not a general public moderation API.

Primary paths:

- [WebsiteApiRuntime](https://github.com/wsg138/EnthusiaStaff/blob/main/velocity/src/main/java/net/enthusia/staff/velocity/WebsiteApiRuntime.java)
- [WebsiteApiServer](https://github.com/wsg138/EnthusiaStaff/blob/main/velocity/src/main/java/net/enthusia/staff/velocity/WebsiteApiServer.java)
- [WebsiteApiRequestDecoder](https://github.com/wsg138/EnthusiaStaff/blob/main/velocity/src/main/java/net/enthusia/staff/velocity/WebsiteApiRequestDecoder.java)
- [WebsiteApiRouter](https://github.com/wsg138/EnthusiaStaff/blob/main/velocity/src/main/java/net/enthusia/staff/velocity/WebsiteApiRouter.java)
- [website domain](https://github.com/wsg138/EnthusiaStaff/tree/main/domain/src/main/java/net/enthusia/staff/domain/website)
- [JdbcWebsiteModerationStore](https://github.com/wsg138/EnthusiaStaff/blob/main/persistence/src/main/java/net/enthusia/staff/persistence/JdbcWebsiteModerationStore.java)

Requests must remain authenticated, bounded and replay-resistant. Only sanitized projections may leave the moderation core. Private-message evidence, reporter identity, coordinates, raw network identity, staff notes, confiscation detail and other sensitive internals do not belong in public projections.

## Punishment and appeal website workflow

The aggregate repository now contains scoped website/appeal component work; the old statement that the private site is absent is no longer accurate.

Current bridge/persistence paths include:

- [WebsiteAppealEndpoint](https://github.com/wsg138/EnthusiaStaff/blob/main/velocity/src/main/java/net/enthusia/staff/velocity/WebsiteAppealEndpoint.java)
- [WebsiteAppealWorkflowEndpoint](https://github.com/wsg138/EnthusiaStaff/blob/main/velocity/src/main/java/net/enthusia/staff/velocity/WebsiteAppealWorkflowEndpoint.java)
- [JdbcWebsiteAppealWorkflowStore](https://github.com/wsg138/EnthusiaStaff/blob/main/persistence/src/main/java/net/enthusia/staff/persistence/JdbcWebsiteAppealWorkflowStore.java)
- `V17__website_appeal_workflow.sql`
- [website component area](https://github.com/wsg138/EnthusiaStaff/tree/main/components)

Appeal acceptance/review must target the intended exact sanction and pass through central sanction authority rather than creating a website-only punishment mutation path.

Source presence still does not establish a live site. Private deployment authentication, sessions, CSRF/rate/media controls, provider integration, operational monitoring, privacy/security review and public/production launch remain separate acceptance gates.

## Enthusia-owned provider boundaries

Provider plugins remain authoritative for their own state. EnthusiaStaff should consume a supported contract, not raw provider SQL, reflective guessing, or command dispatch as a transaction protocol.

| Provider | Moderation boundary | Current direction |
| --- | --- | --- |
| EnthusiaCurrency | exact balance plan/apply/verify/restore under an external operation | provider-side completion/acceptance still required |
| EnthusiaCommend | persistent reputation blacklist/enforcement | provider-side completion/acceptance still required |
| EnthusiaAutoClicker | versioned bounded client evidence | provider contract/runtime acceptance incomplete |
| Enthusia-RoseChat | staff/chat/mute/freeze/PM-evidence/automod/visibility integration | The supported API required for all intended paths remains incomplete or unavailable. |
| EnthusiaMarket | supported stall moderation/review/restoration | provider-side completion/acceptance still required |

Primary paths:

- [integration contracts](https://github.com/wsg138/EnthusiaStaff/tree/main/integration-contracts/src/main/java)
- [Paper integration adapters](https://github.com/wsg138/EnthusiaStaff/tree/main/paper/src/main/java/net/enthusia/staff/paper/integration)
- [Paper economy adapters](https://github.com/wsg138/EnthusiaStaff/tree/main/paper/src/main/java/net/enthusia/staff/paper/economy)
- [Paper client adapters](https://github.com/wsg138/EnthusiaStaff/tree/main/paper/src/main/java/net/enthusia/staff/paper/client)

See [[Integrations]] for operator-facing degradation behavior.

## Optional third-party integrations

Current integration points include capabilities such as Simple Voice Chat, ViaVersion/ViaBackwards, Floodgate/Geyser, CombatLogX, ProtocolLib, Polar, Discord-related delivery and permission/provider surfaces.

Review every provider in at least these states:

- present and compatible;
- missing;
- present but incompatible/unavailable;
- failing during use;
- restart/reload boundary where applicable.

A missing optional provider should disable only the dependent behavior when that can be done safely and should surface a clear health/verification state.

### Java and Bedrock identity

Merged identity persistence now uses supported Floodgate evidence rather than username shape:

- UUID remains authoritative;
- verified Floodgate evidence may establish Java/Bedrock platform;
- unavailable/incompatible evidence remains `UNKNOWN`;
- unverified proxy observations cannot downgrade a verified platform record;
- `*` current/historical names remain lookup aliases, not platform proof.

Representative Geyser/Floodgate client behavior is still a staging requirement.

### Polar

Do not invent a violation/punishment callback that the supported provider API does not expose. If no compatible event contract exists, automated enforcement stays disabled and only supported evidence/integration behavior may be claimed.

## LiteBans schema inspection and import

Migration code inspects the source schema, maps supported variants, preserves external IDs/identity/expiration state and records mapping/run state for idempotent dry run/import/reconciliation.

Primary areas:

- [migration domain](https://github.com/wsg138/EnthusiaStaff/tree/main/domain/src/main/java/net/enthusia/staff/domain/migration)
- [migration persistence](https://github.com/wsg138/EnthusiaStaff/tree/main/persistence/src/main/java/net/enthusia/staff/persistence/migration)
- [Velocity migration runtime](https://github.com/wsg138/EnthusiaStaff/tree/main/velocity/src/main/java/net/enthusia/staff/velocity)
- [integration tests](https://github.com/wsg138/EnthusiaStaff/tree/main/integration-tests/src/test/java)

Automated/synthetic import evidence does not replace representative private LiteBans data, production-like volume, interruption/resume, source-variant and final incremental import proof.

Operator runbook: [[LiteBans Migration]].

## Shadow comparison

During shadow, LiteBans remains authoritative. EnthusiaStaff calculates and records comparisons without enforcing its own result.

At minimum compare:

- total/active records and mappings;
- stable external IDs;
- UUID/name interpretation;
- exact issue/expiration times;
- active/expired state;
- ban login decisions;
- mute/chat decisions;
- network/IP decisions;
- new source actions during the observation window;
- recovery/quarantine/mismatch state.

A “close” count is not acceptable parity. Every mismatch needs an explanation or fix.

The final production acceptance requires the policy-defined **168 continuous hours** of accepted non-enforcing observation; automated shadow tests or historical synthetic runs do not satisfy that gate.

Runbook: [[Shadow Mode and Cutover]].

## Cutover and rollback boundary

Merged source contains substantial cutover/recovery coordination foundations. Treat them as implementation, not production authorization.

Before production authority moves, the exact candidate must prove:

- final source snapshot/incremental import;
- no unresolved mismatch;
- exactly one authoritative writer/enforcement path;
- writer fencing and duplicate activation rejection;
- maintenance/activation/emergency-freeze transitions;
- restart/recovery behavior;
- queue/outbox reconciliation;
- rollback/emergency procedures;
- operator/owner acceptance.

After activation, an unsafe outcome enters `READ_ONLY_FAILURE`; do not automatically fail back to LiteBans while post-cutover actions may exist only in EnthusiaStaff.

## Distributed runtime and client acceptance

A full release candidate needs representative testing of the real topology rather than one plugin in isolation:

```text
Velocity
├── HUB + EnthusiaStaff-Paper
└── SMP + EnthusiaStaff-Paper
```

The acceptance set should include:

- login and server-switch enforcement;
- no-online-player transport;
- backend reconnect/outage;
- distinct backend/player-data scopes;
- Paper/Velocity/provider startup and shutdown;
- staff, punishment, report and recovery workflows;
- supported Java clients;
- Bedrock/Geyser/Floodgate identity and UI fallback;
- vanish/packet/client behavior;
- Folia-compatible owner/scheduler behavior where supported;
- provider present/missing/failure cases.

Historical standalone Paper boot/restart evidence is useful only for the exact recorded Paper scenario and SHA. It is not complete distributed staging.

## Load, saturation and process-kill evidence

Release confidence also requires the workflows with destructive or distributed state to behave safely under resource pressure and abrupt interruption.

Exercise, as relevant:

- bounded worker/executor saturation;
- DB pool/lock contention;
- network and Discord queue pressure;
- provider latency/timeouts;
- reconnect storms/backoff;
- process termination between durable intent, side effect, verification and terminal commit;
- restart recovery and duplicate replay;
- stale lease/fence owners;
- inventory/economy/confiscation ambiguity and quarantine.

A unit test that injects one exception is not a general process-kill/load acceptance result.

## Release evidence and approval

A release decision should bind one exact candidate:

- repository revisions/component parity;
- runtime JAR hashes;
- migration/configuration versions/checksums;
- exact CI/static/coverage results;
- runtime topology and provider versions;
- Java/Bedrock/Folia evidence;
- load/recovery evidence;
- migration/shadow/cutover records;
- unresolved warnings/known limitations;
- rollback/recovery plan;
- explicit operational/owner approval.

Changing relevant source, migration, configuration or provider contracts after an acceptance run invalidates the affected evidence until it is rerun.

## Go deeper

- [[Integrations]] — provider/operator behavior.
- [[Installation]] — installation/staging entry point.
- [[LiteBans Migration]] — migration procedure.
- [[Shadow Mode and Cutover]] — authority transition procedure.
- [[Recovery and Troubleshooting]] — outage/recovery procedure.
- [[Protocol and Network Traffic]] — distributed transport details.
- [[Privacy and Data Handling]] — sensitive/public data boundaries.
- [[Developer Code Guide]] — source traces.
- [[Code Review Guide]] — distributed/provider/security review.
- [[Build and Testing]] — evidence interpretation.
- [[Implementation Status]] — overall merged-main status.