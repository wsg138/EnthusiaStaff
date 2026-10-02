# Recovery and Troubleshooting

Recovery begins by preserving evidence and stopping conflicting work. It does **not** begin by repeatedly running the same destructive command or weakening an authentication/authority gate until the request succeeds.

## Find the affected system

| Symptom or area | Product/source hub | Procedure or deep dive |
| --- | --- | --- |
| Startup, MariaDB, protocol, modes, reload or identity | [[Core Platform and Infrastructure]] | [[Configuration]], [[Protocol and Network Traffic]] |
| Punishment, request, history, report or evidence | [[Moderation, Punishments, and Reports]] | [[Punishment System]], [[Reports and Evidence]] |
| Staff mode, vanish, freeze, inventory, confiscation, economy or alts | [[Staff Tools, Investigations, and Player-State Safety]] | [[Staff-Mode-Vanish-and-Freeze]], [[Inventory and Confiscation Safety]], [[Vanish Internals]] |
| StaffBot, Discord moderation, linking or Discord effects | [[Discord Moderation Platform]] | [[Staff Bot Runtime and Operations]], [[Discord Delivery]] |
| Public site, Velocity website API or moderation web workspace | [[Website and Web API]] | [[Privacy and Data Handling]], [[Configuration]] |
| Provider, migration, shadow or cutover | [[Integrations, Migration, and Release Readiness]] | [[Integrations]], [[LiteBans Migration]], [[Shadow Mode and Cutover]] |
| Code defect / source tracing | [[Developer Code Guide]] | [[Code Review Guide]], [[Build and Testing]] |

## First response

1. Stop repeated actions for the affected player, case, asset, Discord member, link, provider or migration.
2. Record exact time, relevant runtime (Paper/bridge/Velocity/StaffBot/web), actor and target identifiers that are safe to retain.
3. Record stable case, sanction, report, request, operation, message, session, reconciliation or migration IDs.
4. Capture the narrowest available health/status result.
5. Preserve bounded sanitized logs/stack traces and deployed artifact/revision identity.
6. Determine whether authoritative state committed before retrying anything.
7. Determine whether an external effect may already have happened.
8. Replay only a documented idempotent/reconcilable stage.
9. Quarantine/escalate ambiguity rather than guessing.

## Never do these as a first response

- Do not spam punishment, kick, ban, mute, removal, confiscation, restore or link commands.
- Do not manually edit MariaDB rows to make state “look finished.”
- Do not delete leases/reconciliation/outbox rows merely to clear a health warning.
- Do not enable destructive Discord enforcement just to see whether StaffBot responds.
- Do not make a private service public because a connection is failing.
- Do not weaken HMAC/replay/session/CSRF checks to get a website request through.
- Do not rebuild inventory/balance/link state from memory.
- Do not enable a second production authority while the original authority may still write.
- Do not publish tokens, raw addresses, private messages, link history, coordinates, staff notes or evidence in ordinary support channels.

## Operational and authority modes

Minecraft operational modes such as `BOOTSTRAP`, `DEGRADED`, `SHADOW_MIGRATION`, `ACTIVE`, `MAINTENANCE` and `READ_ONLY_FAILURE` are safety boundaries, not troubleshooting toggles.

StaffBot and other external-effect paths also have their own explicit enforcement/authority gates. A broken dependency is not a reason to turn an otherwise disabled destructive gate on.

## MariaDB unavailable

New destructive work whose durable authority cannot be established should stop. Safe status/history/recovery inspection may remain where explicitly supported.

Check:

- connection/TLS/credential scope;
- pool exhaustion;
- schema version/migration checksum;
- database time/clock skew;
- long transactions/deadlocks;
- lease/outbox/reconciliation backlog;
- storage host availability.

If the database may have committed before a client timed out, look up the exact idempotency/operation/result before retrying. Timeout-after-commit is a core recovery case, not evidence that “nothing happened.”

## Schema checksum or future version

Stop unsafe startup/work and compare migrations with the exact runtime artifact.

Current merged history is through:

```text
V20__discord_account_linking.sql
```

Recent milestones are V17 website appeals, V18 Cheat Tester recovery, V19 Discord moderation persistence, and V20 account linking.

Do not edit an applied migration or use Flyway repair merely to make a modified historical file pass. A V21+ file visible only on an open PR is not current merged schema.

## Paper–Velocity channel failure

New network sanctions must remain blocked when network enforcement cannot be proved.

Check certificate/hostname/trust, server identity/allowlist, protocol version, replay/clock state, queue/backoff, durable inbox/outbox ACK state and stale consumer sessions.

A successful socket write is not necessarily a durable consumer acknowledgement.

See [[Protocol and Network Traffic]].

## StaffBot unhealthy

First identify the health category rather than restarting repeatedly.

Check, as applicable:

- Discord authentication/application identity;
- guild/staging-channel environment fences;
- gateway reconnect/rate-limit state;
- MariaDB connectivity/schema;
- private authority/read-service connectivity;
- worker-pool/queue saturation;
- punishment/reconciliation backlog;
- health listener configuration;
- exact deployed StaffBot artifact/release identity.

Do **not** enable punishment enforcement merely to test liveness. Use health/read-only behavior first. If the current release is unhealthy and the cause is not a safe configuration correction, use the documented known-good rollback/update path from [[Staff Bot Runtime and Operations]].

After restart/rollback, reconcile outstanding durable punishment/retry/expiry state before resuming destructive work.

## Discord interaction says unauthorized

Do not solve this by adding a Discord role blindly.

Verify:

- the Discord user is linked to the intended Enthusia staff identity;
- current staff identity/rank/permission is available;
- target hierarchy/protection permits the operation;
- requested platform/scope/duration is allowed;
- confirmation has not gone stale;
- external Discord hierarchy/preconditions still hold;
- the correct staging/production application/guild is being used.

Discord command visibility and Discord roles are presentation/discovery inputs, not final authority.

## Discord punishment outcome is ambiguous

An external timeout may occur before or after Discord applied the effect.

Do not blindly repeat the action. Inspect the durable intent/reconciliation state and the relevant remote truth:

- native ban state;
- member presence after kick;
- bot-owned mute/restriction role state;
- expected expiry/end/revoke state;
- newest relevant audit/ownership evidence where the workflow uses it;
- terminal error/reconciliation status.

For an ambiguous kick in particular, a retry may be semantically wrong because the member may already be gone. Let the owning reconciliation model decide whether the operation is complete, retryable or terminally ambiguous.

## Account linking problem

Do not manually rewrite Discord/Minecraft link rows to “fix” ownership.

Check:

- whether the challenge expired or was already consumed/replaced;
- actor-side online/identity proof required by the flow;
- current Discord owner for the Minecraft identity;
- current Minecraft accounts under the Discord identity;
- unlink/reassignment audit/history;
- main-account selection/override state;
- DiscordSRV import/mirroring state if the issue is transitional;
- restart/replay/concurrency evidence for the specific challenge/operation.

Raw link codes should not be recovered from storage; only their hashes are durable. Issue a new supported challenge when appropriate rather than trying to resurrect an old raw code.

## Punishment/request/report conflict

For punishment/request timeouts, look up the draft/request/case/sanction/idempotency state before rerunning. Same-key replay should return the existing committed result where that is the defined behavior; changed-content reuse should conflict.

For report revision conflicts, reopen the report and work from the newest revision rather than forcing a stale confirmation.

## Inventory, confiscation or economy operation incomplete

Stop edits/transfers/server switching for the affected scope. Inspect operation state, idempotency key, lease/fence, before snapshot/checksum, selected item/economy plan, external side-effect result, after-verification and quarantine/audit state.

A failed external call before any effect is different from a timeout after the effect may have occurred. Do not choose retry/rollback until the workflow distinguishes those cases.

## Staff-mode restoration failure

Keep the staff member out of normal gameplay and preserve the original durable session snapshot. Do not toggle Staff Mode repeatedly or overwrite recovery evidence with the broken current state.

Record session/revision, backend/location, inventory/armor/offhand, XP/effects/health/hunger/game mode, reconnect timing and leaked staff items. Escalate using the documented recovery path.

## Vanish exposure / freeze bypass

For vanish, record the exact leak surface: tab/player-info, entity packets, command suggestions, chat/voice, particles/sound/container effects, external APIs/providers, client family and protocol version.

For freeze, record the exact bypass sequence/client/backend. Do not repeatedly exercise an unsafe bypass on a live player merely to collect more attempts.

See [[Vanish Internals]] and [[Staff-Mode-Vanish-and-Freeze]].

## Legacy Discord webhook delivery stalled

This is separate from StaffBot.

Check due time/attempt count, lease owner/fence, sanitized error, configured destination, circuit/dead-letter state, consumer result and whether the producer transaction committed.

Never delete a row blindly when the remote webhook may already have accepted it. See [[Discord Delivery]].

## Velocity website API failure

First determine whether the failing route is **public** or **privileged**.

Check:

- API listener/runtime health;
- route actually exists on current merged `main`;
- authentication/signature/replay state for privileged routes;
- timestamp/nonce/body-size/rate limits;
- exact request body/field contract using sanitized evidence;
- MariaDB/appeal workflow state;
- central sanction authority for appeal acceptance/decisions;
- whether a site/frontend deploy expects a route that exists only on an unmerged branch.

Do not weaken authentication/replay controls or serialize internal records directly as a workaround.

## Public site failure

Confirm whether the failure is static frontend, Cloudflare Pages Function, backend API, provider proxy or deployment mismatch.

Check aggregate/standalone component revision/parity where relevant. A working frontend with a mismatched backend route is not a safe reason to invent a browser-side fallback for privileged behavior.

## Moderation web launch/session failure

The staging workspace uses one-time signed launches. A consumed/replayed/expired launch should be rejected by design.

If a launch fails:

1. do not reuse the same launch URL repeatedly as a workaround;
2. issue a new supported signed launch after checking the root cause;
3. verify Worker/Durable Object/session health;
4. verify the private StaffBot read service is reachable through the approved protected path;
5. inspect signature/body/replay/rate failure categories without logging secret material;
6. confirm cookies/session/CSRF are behaving as designed;
7. keep the raw Discord bot token out of Cloudflare/browser debugging.

The current moderation workspace is read/simulation-oriented. Do not add an emergency direct MariaDB or punishment-write path to “restore” functionality.

## Optional provider unavailable

Confirm only the dependent feature is disabled where safe. Do not substitute raw provider SQL/reflection or guess an unsupported API. See [[Integrations]].

## Migration mismatch / cutover incident

Keep the current production authority in place until the mismatch is understood. Compare exact source IDs/mappings/timestamps/expiration/identity/decision state and correct the source interpretation or implementation rather than editing mappings solely to force parity.

After a destructive cutover, an emergency read-only/freeze mode may be appropriate. Do not automatically fail back to an old authority if newer actions may exist only in EnthusiaStaff; reconcile into one selected authority first.

## Maintainer debugging path

1. identify the owning hub/deep dive;
2. locate the exact source trace in [[Developer Code Guide]];
3. identify the failed invariant in [[Code Review Guide]];
4. preserve durable/external evidence before reproducing a destructive path;
5. reproduce in a disposable environment when possible;
6. add the narrow test proving the bug/fix;
7. rerun the strongest applicable runtime/staging layer from [[Build and Testing]];
8. do not declare the original incident resolved until the relevant acceptance has been rerun for the fixed candidate.

## Sanitized support evidence

Provide only what is necessary:

- exact source revision/artifact hashes;
- Java/Paper/Velocity/StaffBot/MariaDB/provider versions as applicable;
- public site/moderation-web deployment revision when relevant;
- non-secret environment/application/guild identity needed to disambiguate staging vs production;
- operational/health categories;
- stable case/operation/reconciliation/session/migration IDs;
- timestamps and bounded sanitized errors;
- reproduction and restart/reconnect effects;
- actions already attempted.

Keep credentials, raw addresses, private messages, link/alt history, coordinates, staff notes, confiscated contents and appeal media out of ordinary support channels.

## Related pages

- [[Core Platform and Infrastructure]]
- [[Discord Moderation Platform]]
- [[Staff Bot Runtime and Operations]]
- [[Website and Web API]]
- [[Discord Delivery]]
- [[Moderation, Punishments, and Reports]]
- [[Staff Tools, Investigations, and Player-State Safety]]
- [[Integrations, Migration, and Release Readiness]]
- [[Developer Code Guide]]
- [[Code Review Guide]]
- [[Build and Testing]]
- [[Configuration]]
- [[Privacy and Data Handling]]
- [[Protocol and Network Traffic]]
- [[LiteBans Migration]]
- [[Shadow Mode and Cutover]]