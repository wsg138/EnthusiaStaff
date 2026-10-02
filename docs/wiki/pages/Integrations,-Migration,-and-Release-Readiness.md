# Integrations, Migration, and Release Readiness

This hub covers provider boundaries, Discord/StaffBot, website/web APIs, LiteBans migration/shadow/cutover, distributed client/runtime acceptance, failure testing, and the evidence required before production authority moves.

For Discord product behavior use [[Discord Moderation Platform]]. For StaffBot deployment/recovery use [[Staff Bot Runtime and Operations]]. For the public site, Velocity API, and moderation web workspace use [[Website and Web API]].

## Quick status

| Area | Merged-main state | Main limitation |
| --- | --- | --- |
| Legacy Discord webhook delivery | **Available with limitations** | Separate at-least-once outbound subsystem; route/outage/operator acceptance remains. |
| StaffBot runtime | **Implemented** | Production destructive authority remains explicitly gated/default-off. |
| Discord punishment/reconciliation | **Implemented, not production-accepted** | Production cutover/authority acceptance is separate from implementation. |
| Discord/Minecraft account linking | **Implemented** | Full DiscordSRV retirement/role-sync/console/chat migration is separate work. |
| Provider-neutral managed-role API | **Implemented contract foundation** | Provider/consumer migration/parity is not complete merely because the contract exists. |
| Discord console replacement | **In development** | Authenticated command bridge is not merged current behavior. |
| Discord evidence/case/alert expansion | **In development** | Active draft work remains unmerged. |
| Cross-platform Discord/Minecraft moderation expansion | **In development** | Current merged scopes must not be silently widened. |
| Public Enthusia site + Velocity website API | **Implemented, not production-accepted as a complete service** | Deployment/security/provider/operational acceptance remains. |
| Staging moderation web workspace | **Available for accepted staging read/simulation scope** | Intentionally no destructive/production authority. |
| Enthusia-owned provider contracts | **Partial/provider-dependent** | Some provider-side APIs/implementations/acceptance remain incomplete. |
| LiteBans import/shadow/cutover | **Partial; production acceptance blocked** | Representative private data, accepted shadow/final reconciliation/owner cutover remain. |
| Full topology/release acceptance | **Blocked/incomplete** | One exact candidate still needs coherent distributed/provider/client/load/recovery/cutover acceptance. |

## Discord integration boundaries

Merged `main` has several Discord-related subsystems with different responsibilities.

### StaffBot

`staff-bot` is the standalone Java/JDA runtime that owns the privileged Discord Gateway, staff moderation/read UX, linked-staff actor resolution, Discord punishment execution/reconciliation where enabled, private moderation-read service, signed web-workspace launch issuance, and health/readiness.

See [[Discord Moderation Platform]] and [[Staff Bot Runtime and Operations]].

### Legacy webhook delivery

The older Velocity webhook subsystem remains a separate outbound notification path:

- moderation commits can enqueue durable Discord notification rows;
- `DiscordEventRenderer` creates bounded allowlisted output;
- Velocity leases/retries delivery;
- Discord delivery failure does not roll back an already valid moderation commit;
- at-least-once delivery can duplicate around a remote-success/local-crash window.

See [[Discord Delivery]].

### Managed-role platform contract

`discord-platform-api` defines a provider-neutral managed-role contract so consumer plugins do not depend directly on JDA/StaffBot internals.

It models namespaces/keys/claims/results and desired membership across multiple Minecraft identities. It does **not** by itself prove the StaffBot provider and all DiscordSRV role-sync consumers are migrated.

Source: [`discord-platform-api/`](https://github.com/wsg138/EnthusiaStaff/tree/main/discord-platform-api).

### Account linking

Merged V20 linking supports one-use short-lived codes, hashed-at-rest code storage, current/history ownership, unlink/reassignment and main-account behavior. DiscordSRV import/mirroring compatibility exists for transition.

Do not infer that DiscordSRV console, chat, or every role-sync consumer is retired just because account linking is implemented.

## Discord authority and cutover

Discord command visibility or a Discord role is not final moderation authority. StaffBot resolves the linked staff actor and uses central current authorization/hierarchy/target policy before privileged effects.

Destructive Discord enforcement is intentionally explicit/default-off in runtime configuration. Production authority should move only after the exact candidate’s Discord-specific staging/reconciliation/failure/cutover evidence is accepted.

Current open work for console replacement, role-sync parity, broader evidence/case/alerts and cross-platform moderation is development-only until merged.

## Website and web boundaries

There are three distinct web-facing product surfaces plus StaffBot’s private read service.

### Velocity website API

Velocity owns the authoritative plugin-side HTTP router for public punishment/search/case projections and authenticated punishment-code/appeal/reviewer workflows.

Primary paths:

- [WebsiteApiRuntime](https://github.com/wsg138/EnthusiaStaff/blob/main/velocity/src/main/java/net/enthusia/staff/velocity/WebsiteApiRuntime.java)
- [WebsiteApiServer](https://github.com/wsg138/EnthusiaStaff/blob/main/velocity/src/main/java/net/enthusia/staff/velocity/WebsiteApiServer.java)
- [WebsiteApiRouter](https://github.com/wsg138/EnthusiaStaff/blob/main/velocity/src/main/java/net/enthusia/staff/velocity/WebsiteApiRouter.java)
- [Website appeal workflow store](https://github.com/wsg138/EnthusiaStaff/blob/main/persistence/src/main/java/net/enthusia/staff/persistence/JdbcWebsiteAppealWorkflowStore.java)

Current merged route list and privacy boundary: [[Website and Web API]].

### Public site component

[`components/enthusia-site/`](https://github.com/wsg138/EnthusiaStaff/tree/main/components/enthusia-site) is the synchronized public static site/Cloudflare Pages Functions component. Server-side functions mediate approved API access; frontend/browser code must not become a direct privileged database client.

### Staging moderation web

[`moderation-web/`](https://github.com/wsg138/EnthusiaStaff/tree/main/moderation-web) is a separate staging-only Cloudflare Worker/static-assets staff workspace.

It uses short-lived signed launch tickets, one-time replay protection, secure browser sessions, CSRF material, signed/body-bound StaffBot reads, rate limits and allowlisted responses. Current merged behavior is read/simulation-oriented: it does not independently punish, delete Discord messages, mutate cases/notes/sanctions, enforce Minecraft, mutate LiteBans or take production authority.

### StaffBot private read API

The moderation workspace obtains privileged data through StaffBot’s authenticated/replay-protected private read service rather than direct MariaDB access.

The browser/Cloudflare boundary never becomes moderation authority merely because it can render privileged data.

## Website appeal workflow

Merged source includes V17 website appeal workflow persistence and exact-sanction appeal behavior. Current routes are documented in [[Website and Web API]].

Appeal acceptance must flow through central sanction authority and target the intended sanction. Draft work expanding edit/claim/reopen lifecycle is not merged current behavior.

## Public/private data boundary

External projections should use explicit allowlists. Public/site/Discord outputs must not casually expose:

- reporter identity;
- staff-private notes;
- private-message evidence;
- raw addresses/network identity;
- sensitive coordinates;
- link/alt history not approved for that audience;
- credentials/signing material/private service topology;
- internal recovery/quarantine/provider payloads.

See [[Privacy and Data Handling]].

## Enthusia-owned provider boundaries

Provider plugins remain authoritative for their own state. EnthusiaStaff should consume supported contracts, not raw provider SQL, reflective guessing, or command dispatch as a transaction protocol.

| Provider | Moderation boundary | Current direction |
| --- | --- | --- |
| EnthusiaCurrency | exact balance plan/apply/verify/restore | provider-side acceptance still matters |
| EnthusiaCommend | reputation blacklist/enforcement | provider-side acceptance still matters |
| EnthusiaAutoClicker | bounded/versioned client evidence | supported runtime/provider acceptance required |
| Enthusia-RoseChat | staff/chat/mute/freeze/PM-evidence/automod/visibility integration | use supported APIs; missing paths must degrade honestly |
| EnthusiaMarket | supported stall moderation/review/restoration | provider-side acceptance required |

Primary paths:

- [integration contracts](https://github.com/wsg138/EnthusiaStaff/tree/main/integration-contracts/src/main/java)
- [Paper integration adapters](https://github.com/wsg138/EnthusiaStaff/tree/main/paper/src/main/java/net/enthusia/staff/paper/integration)
- [Paper economy adapters](https://github.com/wsg138/EnthusiaStaff/tree/main/paper/src/main/java/net/enthusia/staff/paper/economy)

A missing optional provider should disable only dependent behavior where safe and surface a clear health/degradation state.

## Java and Bedrock identity

UUID remains authoritative. Supported verified Floodgate evidence may establish Java/Bedrock platform. Missing/incompatible evidence remains `UNKNOWN`; an unverified proxy observation cannot downgrade a verified platform record. `*` aliases remain lookup compatibility, not platform proof.

Representative Geyser/Floodgate client behavior remains a runtime acceptance requirement.

## LiteBans import and shadow comparison

Migration code inspects source schema, maps supported variants, preserves external IDs/identity/expiration state and records mapping/run state for idempotent dry-run/import/reconciliation.

Automated/synthetic import evidence does not replace representative private LiteBans data, production-like volume, interruption/resume, source-variant and final incremental import proof.

During shadow, LiteBans remains authoritative while EnthusiaStaff records comparisons. Final production acceptance requires the policy-defined continuous accepted non-enforcing observation window and explanation/fix of mismatches; automated shadow tests are not a substitute.

Operator pages: [[LiteBans Migration]] and [[Shadow Mode and Cutover]].

## Cutover and rollback

Before production authority moves, the exact candidate must prove, where applicable:

- final source snapshot/import/reconciliation;
- exactly one authoritative writer/effect path;
- authority/writer fencing and duplicate activation rejection;
- restart/outage/reconnect behavior;
- queue/outbox/reconciliation recovery;
- rollback/emergency procedure;
- explicit owner/operator acceptance.

After a destructive cutover, do not automatically fail back to an older authority if newer actions may exist only in EnthusiaStaff.

Discord, LiteBans, role-sync, console and website/public launch have distinct cutover boundaries. One subsystem being ready does not authorize the others.

## Distributed release acceptance

A full release candidate should exercise the intended topology, including the applicable combination of:

```text
Velocity
├── HUB + EnthusiaStaff-Paper
└── SMP + EnthusiaStaff-Paper

StaffBot
├── Discord Gateway/JDA
├── MariaDB
└── private authority/read services

Cloudflare/public web
├── enthusia-site
└── staging moderation-web (when validating that surface)
```

Acceptance should cover startup/shutdown, reconnect/outage, Java/Bedrock, supported providers, private service loss, queue saturation, StaffBot rate limits/reconciliation, website authentication/replay/privacy, and rollback/recovery appropriate to the candidate.

## Evidence discipline

A release decision should bind one exact candidate: source revisions/component parity, runtime artifact hashes, migration/config versions, CI/static/coverage, topology/provider versions, Java/Bedrock/Folia evidence, StaffBot/Discord evidence, web/API evidence, load/recovery, migration/shadow/cutover records, limitations, rollback plan and explicit approval.

Changing relevant source, migration, configuration, provider contract, signing/auth boundary or deployment artifact invalidates affected evidence until rerun.

## Go deeper

- [[Discord Moderation Platform]]
- [[Staff Bot Runtime and Operations]]
- [[Website and Web API]]
- [[Discord Delivery]]
- [[Integrations]]
- [[LiteBans Migration]]
- [[Shadow Mode and Cutover]]
- [[Recovery and Troubleshooting]]
- [[Protocol and Network Traffic]]
- [[Privacy and Data Handling]]
- [[Developer Code Guide]]
- [[Code Review Guide]]
- [[Build and Testing]]
- [[Implementation Status]]