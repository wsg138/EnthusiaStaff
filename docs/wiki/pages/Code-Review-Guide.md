# Code Review Guide

Use this page when reviewing an EnthusiaStaff change. It is a disciplined checklist and evidence guide, not a replacement for [[Developer Code Guide]] or [[Architecture]].

Start with the behavior the change claims to alter, identify the owning domain boundary, then trace state through persistence and the runtime adapter that performs the effect. A green unit test proves only what that test exercises; it does not prove real Paper/Folia, Velocity, MariaDB under load, Discord/JDA, browser/Cloudflare, provider, Java/Bedrock, or production behavior.

## Fast review path

1. Read the PR description and exact changed files. Separate product code, schema, tests, configuration, docs and orchestration records.
2. Reconcile the PR against current `main`; do not review against an old package snapshot.
3. Read the relevant finished-behavior requirement/focused specification.
4. Check [[Implementation Status]] so branch-only behavior is not confused with merged behavior.
5. Identify the domain/application service that owns the rule.
6. Identify durable state: port, JDBC store, transaction, tables, constraints, migration, leases/revisions/recovery state.
7. Identify runtime ownership: Paper scheduler, Velocity worker/event thread, StaffBot/JDA worker, authority bridge, provider callback, website API, Cloudflare/browser surface.
8. Review failure paths: stale state, duplicate delivery, timeout after commit, disconnect, shutdown/restart, partial outage, queue saturation, replay and ambiguous external outcomes.
9. Match each claim to the strongest available evidence in [[Build and Testing]].
10. State what remains unproved.

## Architecture and dependency boundaries

Review for:

- business policy in `domain/`, not duplicated in Paper/Velocity/StaffBot/web/provider handlers;
- persistence implementing domain ports without leaking JDBC policy upward;
- `integration-contracts/` containing supported provider contracts, not copied provider internals;
- `discord-platform-api/` remaining provider-neutral and free of JDA/StaffBot persistence/business-policy leakage;
- `paper-authority-bridge/` remaining narrow rather than becoming a second full Paper moderation runtime;
- public/browser adapters using explicit projections and services rather than direct table access;
- no cyclic/cross-module shortcut around the owning service;
- one authoritative implementation for hierarchy, sanction mutation, linking, report state, staff sessions, inventory safety and Discord authorization.

A useful question is: **if this rule changes, is there one authoritative place that must change?**

References: [[Architecture]], [[Developer Code Guide]], `settings.gradle.kts`, root `runtimeJars`.

## Paper / Leaf / Folia

Check that:

- JDBC/HTTP/filesystem/socket work never blocks the game/entity thread;
- player/entity reads/mutations return to the supported owning scheduler;
- region/entity/global schedulers are not treated as interchangeable;
- async callbacks are session/generation/revision fenced so reconnect cannot mutate a retired handle;
- disable stops intake before closing resources required by in-flight work;
- Staff Mode, vanish, freeze, inventory, Cheat Tester, teleport/follow/spectate preserve restoration contracts;
- failed scheduler handoff refuses safely instead of mutating from the wrong thread;
- player-originated destructive Paper mutations obey the current Staff Mode authority rule where applicable;
- console/SYSTEM or independent Discord/website authority is not accidentally forced through Paper-local Staff Mode semantics.

Mock scheduler tests help but do not prove real Folia region ownership.

## Paper authority bridge

Treat `paper-authority-bridge` as a transition runtime with strict containment.

Check that:

- required/forbidden runtime contents remain enforced by the build;
- it does not absorb ordinary Paper commands/GUI/business logic;
- it does not become an unaudited second path to punishment mutation;
- migration/authority requests still use central policy and durable state;
- configuration/secret scope is no broader than required;
- removing/replacing the transition path later remains possible.

## Velocity

Velocity event threads must remain non-blocking. Review bootstrap, reload/publication, workers and shutdown as a lifecycle.

Check that:

- login/server-switch events do not wait on JDBC/HTTP/filesystem/socket I/O;
- workers are bounded and have clear ownership/shutdown;
- reload validates a complete candidate before publication and failure keeps the prior valid runtime;
- startup failure closes resources already opened and does not publish a half-runtime;
- backend/player presence ordering cannot overwrite newer state after reconnect/server switch;
- website API listeners/authentication are published/retired safely;
- old workers/callbacks cannot mutate a replacement runtime after reload/shutdown.

## StaffBot / JDA lifecycle

StaffBot is a real standalone runtime, not future architecture.

Review:

- exactly one intended privileged Gateway/JDA owner;
- staging/production application identity, guild and staging-channel fences fail closed;
- no privileged intent is enabled without an explicit product need;
- gateway reconnect/backoff/rate-limit handling is bounded;
- blocking database/private-service work does not block JDA event handling;
- worker pools/queues are bounded and reject/degrade predictably;
- shutdown stops interaction intake/workers before dependencies disappear;
- stale callbacks from an old generation cannot publish into a restarted runtime;
- health/readiness distinguishes authentication, DB/authority/read-service and worker degradation without leaking credentials/private data;
- deployment/startup modes do not allow staging file-backed configuration to bypass production fences.

Deep dive: [[Staff Bot Runtime and Operations]].

## Discord actor authority

Discord role membership and command visibility are not final moderation authority.

Verify:

- the Discord user resolves to the current linked Enthusia staff identity;
- current rank/permission/target hierarchy is evaluated centrally;
- self-target, equal/higher-rank and protected-target restrictions fail closed;
- stale confirmations reauthorize immediately before effects;
- external Discord hierarchy/preconditions are checked at execution time;
- Developer’s allowed Discord-only authority cannot leak into Minecraft authority;
- requested platform/scope cannot silently widen from Discord-only to cross-platform;
- failed identity/authority resolution does not fall back to “role says mod.”

References: [[Discord Moderation Platform]], [[Rank Authority]].

## Discord punishment external effects

Warn/mute/kick/ban/restriction actions cross a remote API boundary. Review the durable effect protocol, not just the JDA call.

Check that:

- intent/idempotency/reconciliation state exists where required;
- a successful Discord response followed by local timeout/restart is recoverable;
- a local DB commit followed by Discord failure remains reconcilable;
- an ambiguous kick is not blindly repeated if the member may already have been removed;
- native-ban state is verified/reconciled instead of assumed from one request;
- bot-owned mute/restriction changes are distinguished from unrelated external/human changes;
- audit-log lookup is correctly paginated/bounded and selects the newest relevant event;
- retries distinguish safely repeatable requests from ambiguous non-idempotent effects;
- expiry/end/revoke/overturn cannot remove state the bot no longer owns;
- terminal ambiguity is auditable and does not become false success.

## Discord/Minecraft account linking

Current merged schema/linking runtime is V20-backed.

Review:

- one-use link codes are short-lived and only hashes are persisted;
- replacement invalidates old active challenges;
- completion is atomic against replay/restart/concurrency;
- one current Discord owner per Minecraft identity is preserved;
- multiple Minecraft identities under one Discord subject remain deliberate;
- unlink/reassignment preserves audit/history rather than rewriting history away;
- online proof/self-service requirements are enforced;
- staff force-link/reassignment requires authoritative permission and audit;
- main-account selection/hysteresis/override is deterministic;
- DiscordSRV import/mirroring cannot create duplicate/ambiguous ownership;
- public output never exposes private link/alt history without an explicit approved projection.

Key sources: `AccountLinkingService`, `PaperAccountLinkRuntime`, `JdbcAccountLinkingStore`, `JdbcDiscordLinkRepository`, V20 integration tests.

## Provider-neutral Discord role API

For `discord-platform-api` or consumers/providers verify:

- no JDA/provider implementation types leak into consumer contracts;
- namespaces/role keys cannot collide across consumers;
- desired membership semantics handle multiple linked Minecraft identities correctly;
- consumer retries are idempotent;
- provider absence/degradation is explicit;
- role ownership/reconciliation cannot remove unrelated externally managed roles;
- a compiling contract is not misrepresented as completed role-sync migration/parity.

## MariaDB and transactions

For every durable operation find the actual transaction boundary.

Check:

- authorization/revision checks that must be atomic occur inside the locked transaction;
- state/audit/outbox writes commit/rollback together where required;
- unique constraints/idempotency keys block duplicates;
- optimistic revisions reject stale writes;
- leases use owner/fence checks for claim/renew/transition/release;
- timeout-after-commit can rediscover an existing result;
- queries/batches/workers/caches are bounded;
- hot lookup/claim paths have indexes/constraints;
- JDBC resources close on success/failure;
- DB retry policy is not reused blindly for external non-idempotent effects;
- restart can recover in-flight/claimed state without inventing a second owner.

## Flyway migrations

Current merged migration ceiling is **V20**.

Recent milestones:

- V17 website appeal workflow;
- V18 Cheat Tester session journal;
- V19 Discord moderation persistence;
- V20 Discord account linking.

Review that:

- live `main` migration ceiling was reconciled immediately before choosing a new version;
- existing migrations remain immutable;
- new schema uses a later forward migration;
- clean-install and relevant upgrade tests run;
- constraints/indexes encode important invariants where practical;
- existing data is handled deliberately rather than silently invented;
- Flyway repair/history rewrite is not used as a convenience shortcut.

Open PRs may contain V21+ migrations; those are not merged schema until their PR lands.

## Paper–Velocity distributed behavior

The transport is at-least-once. Review:

- identity allowlists/authentication/version/size/timestamp/nonce replay checks;
- ACK only after the represented durable outcome is accepted;
- duplicate delivery before/after restart;
- reconnect/backend outage/stale session;
- one backend success while another is unavailable;
- outbox/inbox lease fencing/redelivery;
- bounded backpressure/retry/dead-letter behavior;
- no reliance on an online player as network transport.

See [[Protocol and Network Traffic]].

## Website API

Velocity’s website API is an authoritative service boundary.

Review:

- public routes and privileged workflow routes remain clearly separated;
- authentication/replay/body/rate bounds are enforced before privileged work;
- route decoders use explicit field allowlists;
- public responses cannot serialize arbitrary store/domain objects;
- appeal decisions target the exact sanction and flow through central sanction authority;
- browser/request state cannot directly mutate punishment tables;
- errors do not leak credentials, private bodies or internal topology;
- draft-only routes are not accidentally documented/consumed as merged production API.

Deep dive: [[Website and Web API]].

## Public site component

For `components/enthusia-site/` review both the product change and component ownership.

Check:

- aggregate/standalone parity rules are respected;
- frontend code does not receive privileged API credentials;
- Cloudflare Pages Functions mediate sensitive server-side requests;
- public pages expose only approved projections;
- appeal/reviewer functions use the real supported backend routes;
- CSRF/session/rate/media/security controls match the route’s trust level;
- deployment assumptions are not inferred from source presence alone.

## Staging moderation web

`moderation-web/` is a staging-only staff workspace. Review its boundary as untrusted browser + protected Worker/session + private StaffBot read API.

Check:

- launch tickets are short-lived, signed, actor/guild/target-bound and one-time;
- Durable Object/session state actually prevents replay;
- cookies stay `Secure`, `HttpOnly`, `SameSite=Strict` and host-only as designed;
- CSRF material is server-side and checked on applicable requests;
- StaffBot read requests are independently signed/body-bound/replay-protected/rate-limited;
- responses are private/no-store and explicitly allowlisted;
- raw Discord bot token is never sent to Cloudflare/browser/logs/artifacts;
- staging token-derived signing bootstrap is not described as ideal production key architecture;
- UI previews cannot silently perform live punishment/message deletion/case mutation/Minecraft enforcement;
- a successful staging web deployment is not called production moderation acceptance.

## Moderation authority

Across command, GUI, Discord, website and integration entry points verify:

- Helper/Mod/Developer/Admin/Founder/console/SYSTEM semantics separately;
- self-target/self-approval restrictions;
- target/issuer hierarchy;
- higher-rank/system-issued sanction protections;
- bypass permissions remain narrow;
- actor authority is rechecked after asynchronous work before commit;
- operational/authority fencing fails closed;
- cross-platform consequences are independently authorized.

## Player safety

Inventory/Ender/confiscation/Staff Mode/Cheat Tester/freeze/vanish/teleport/follow/spectate are destructive or privacy-sensitive transitions.

Review for durable before-state where required, revisions/fingerprints, concurrent ownership/viewers, nested containers, disconnect/server-switch/restart, idempotent restoration, no item duplication/loss, staff-item leakage prevention, and recovery evidence preservation.

## Java and Bedrock

Do not infer platform from username text.

Check:

- UUID authority;
- verified Floodgate evidence for Java/Bedrock and `UNKNOWN` fallback;
- unverified observations cannot downgrade verified platform state;
- current/historical aliases remain lookup-only evidence;
- duplicate/out-of-order observations cannot overwrite newer state;
- UI has text/command fallback where Java click/hover assumptions are not portable;
- provider absence/incompatibility degrades explicitly.

Representative Geyser/Floodgate runtime testing is still required for client claims.

## Integrations/providers

Provider plugins remain authoritative for their own state.

Check:

- present/missing/incompatible/failing states;
- safe dependent-only degradation;
- supported public contracts instead of raw provider SQL/reflection/command dispatch as a transaction protocol;
- bounded timeout/retry/idempotency;
- external outcome verification;
- no accidental provider API shading;
- stale provider handles removed on reload/shutdown;
- provider failure cannot widen authority or leak vanished/private state.

## Security and privacy

Check the data boundary, not only credentials files:

- no tokens/passwords/TLS/signing material/webhook URLs/private topology in source/logs/tests/evidence;
- raw addresses/network identity stored/projected only through approved protected mechanisms;
- reporter identity/private messages/coordinates/staff notes/confiscation/link-alt evidence remain restricted;
- public/Discord/site projections are explicit allowlists;
- HMAC/signature/bearer/session auth occurs before privileged handling;
- nonce/timestamp replay and body/frame limits are enforced;
- logs/exceptions do not echo sensitive request bodies;
- public bot/site boundaries never expose linked accounts/private moderation data.

## Evidence ladder

| Evidence | Supports | Does not prove alone |
| --- | --- | --- |
| Unit tests | pure policy/parsing/state/authorization | real runtime/JDBC/provider/network behavior |
| Module tests | adapter/service logic | representative distributed topology |
| MariaDB/Testcontainers | exercised SQL/constraints/transactions/migrations/restart | production volume/latency/arbitrary process kill |
| Concurrency/failure injection | simulated races/failures | every real scheduler/network/API race |
| Runtime-JAR checks | expected artifacts/contents/provider-leak scan | real provider/classloader behavior |
| StaffBot smoke test | non-network packaged startup/wiring | Discord/MariaDB/authority behavior |
| Static analysis/coverage | analyzer findings/executed code | behavioral correctness/staging readiness |
| Wiki validation | link/format/source structure | factual product truth |
| Private Paper/bridge staging | recorded exact artifact scenario | StaffBot/Velocity/Bedrock/Folia/full topology |
| StaffBot/Discord staging | recorded exact Discord/API/effect scenarios | broader production authority |
| Moderation-web staging | recorded signed launch/session/read behavior | destructive moderation/public launch |
| Distributed Java/Bedrock/provider staging | tested topology behavior | untested production load/data/cutover |
| Production acceptance | explicitly accepted exact candidate/config | later revisions/config changes |

Never combine evidence from different SHAs into one exact-head claim. Skipped/unavailable jobs are not passes.

## Final reviewer questions

Before approving, be able to answer:

1. What exact business rule changed?
2. Which module/service owns it?
3. What durable state/transaction owns correctness?
4. Which runtime performs the effect?
5. What happens on timeout/replay/reconnect/restart/partial failure?
6. What private/public boundary changed?
7. What proves the policy, persistence and runtime separately?
8. What remains unverified after CI is green?
9. Does any documentation claim branch-only behavior is merged?
10. Does the change accidentally authorize production behavior that was only meant to be implemented/staged?

## See also

- [[Developer Guide Index]]
- [[Architecture]]
- [[Developer Code Guide]]
- [[Discord Moderation Platform]]
- [[Staff Bot Runtime and Operations]]
- [[Website and Web API]]
- [[Protocol and Network Traffic]]
- [[Privacy and Data Handling]]
- [[Build and Testing]]
- [[Recovery and Troubleshooting]]