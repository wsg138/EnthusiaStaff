# Website and Web API

EnthusiaStaff now has several web-facing components. They serve different audiences and have different trust boundaries; treating them as one generic “website API” hides important security and ownership rules.

Use this page to understand the current merged website/API architecture. For Discord product behavior use [[Discord Moderation Platform]]. For the bot runtime that backs the staging moderation workspace, use [[Staff Bot Runtime and Operations]].

## Quick orientation

| Surface | Purpose | Current boundary |
| --- | --- | --- |
| `components/enthusia-site/` | Public Enthusia site and Cloudflare Pages Functions | Public site/component; server-side functions mediate trusted API calls |
| Velocity website API | Public punishment projections plus authenticated punishment-code/appeal/reviewer workflow | Authoritative EnthusiaStaff-side HTTP boundary |
| `moderation-web/` | Staff moderation browser workspace | **Staging-only**, read-only/simulation-focused Cloudflare Worker workspace |
| StaffBot moderation read API | Private data source for the staging moderation workspace | Private authenticated, signed, replay-protected read boundary |

The staging moderation workspace is **not** the public Enthusia website, and the public site must not receive StaffBot credentials or privileged moderation internals.

## Public site component

The aggregate repository contains the synchronized Enthusia website component under:

[`components/enthusia-site/`](https://github.com/wsg138/EnthusiaStaff/tree/main/components/enthusia-site)

It is a static public site with Cloudflare Pages Functions. The component includes frontend source, functions, tests, deployment support, and Cloudflare configuration.

Current API-function areas include:

- appeal submission/eligibility;
- reviewer appeal views/actions;
- health;
- leaderboard proxying;
- other public site routes owned by the site component.

The aggregate component is synchronized from the standalone `wsg138/enthusia-site` source under the repository’s component-parity process. Do not independently rewrite generated/synchronized component code as part of Wiki maintenance.

## Velocity website API

The Velocity runtime owns the authoritative plugin-side HTTP bridge for website/public moderation projections and appeal workflow.

Primary composition paths:

- [`WebsiteApiRuntime.java`](https://github.com/wsg138/EnthusiaStaff/blob/main/velocity/src/main/java/net/enthusia/staff/velocity/WebsiteApiRuntime.java)
- [`WebsiteApiServer.java`](https://github.com/wsg138/EnthusiaStaff/blob/main/velocity/src/main/java/net/enthusia/staff/velocity/WebsiteApiServer.java)
- [`WebsiteApiAuthenticator.java`](https://github.com/wsg138/EnthusiaStaff/blob/main/velocity/src/main/java/net/enthusia/staff/velocity/WebsiteApiAuthenticator.java)
- [`WebsiteApiRequestDecoder.java`](https://github.com/wsg138/EnthusiaStaff/blob/main/velocity/src/main/java/net/enthusia/staff/velocity/WebsiteApiRequestDecoder.java)
- [`WebsiteApiRouter.java`](https://github.com/wsg138/EnthusiaStaff/blob/main/velocity/src/main/java/net/enthusia/staff/velocity/WebsiteApiRouter.java)

### Current merged routes

Public read routes:

```text
GET /v1/public/punishments
GET /v1/public/search
GET /v1/public/cases/{caseId}
```

Authenticated website/workflow routes:

```text
POST /v1/website/punishment-codes/claim
POST /v1/website/punishment-codes/revalidate
POST /v1/website/appeals/eligible
POST /v1/website/appeals/submit
POST /v1/website/appeals/reviewer/list
POST /v1/website/appeals/reviewer/{appealId}/decision
POST /v1/website/appeals/accept
```

These are the current merged routes. Do not document draft appeal-edit/claim/reopen endpoints as available until the corresponding work is merged.

## Public-data boundary

Public routes expose sanitized projections, not raw moderation records.

Do not expose through the public site/API:

- reporter identity;
- staff-private notes;
- private-message evidence;
- raw IP/network identity;
- exact sensitive coordinates;
- internal provider payloads;
- credentials/signing material;
- hidden recovery/quarantine state that is not part of the approved public projection.

Public output should be allowlisted and bounded. “It exists in MariaDB” is not a reason to serialize it.

## Appeal workflow

The merged website workflow uses durable server-side state rather than trusting browser state as authority.

Important implementation paths:

- [`WebsiteAppealEndpoint.java`](https://github.com/wsg138/EnthusiaStaff/blob/main/velocity/src/main/java/net/enthusia/staff/velocity/WebsiteAppealEndpoint.java)
- [`WebsiteAppealWorkflowEndpoint.java`](https://github.com/wsg138/EnthusiaStaff/blob/main/velocity/src/main/java/net/enthusia/staff/velocity/WebsiteAppealWorkflowEndpoint.java)
- [`JdbcWebsiteAppealWorkflowStore.java`](https://github.com/wsg138/EnthusiaStaff/blob/main/persistence/src/main/java/net/enthusia/staff/persistence/JdbcWebsiteAppealWorkflowStore.java)
- [`V17__website_appeal_workflow.sql`](https://github.com/wsg138/EnthusiaStaff/blob/main/persistence/src/main/resources/db/migration/V17__website_appeal_workflow.sql)

An accepted appeal must act on the intended sanction through central sanction authority. The website does not get a separate “just edit punishment rows” path.

Current draft work expands the appeal lifecycle further. Until it is merged, the route list above is authoritative for current `main`.

## Staging moderation web workspace

`moderation-web/` is a separate **staging-only staff moderation workspace** hosted as a Cloudflare Worker/static-assets application.

Source:

- [`moderation-web/`](https://github.com/wsg138/EnthusiaStaff/tree/main/moderation-web)
- [`moderation-web/README.md`](https://github.com/wsg138/EnthusiaStaff/blob/main/moderation-web/README.md)

Its current job is to provide a browser-first moderation UX without turning the browser or Cloudflare into moderation authority.

### Security model

The merged staging workspace uses:

- short-lived HMAC-signed launch tickets;
- actor/guild/target binding;
- one-time launch replay protection in a Durable Object;
- secure server-side browser sessions;
- `Secure`, `HttpOnly`, `SameSite=Strict` cookies;
- server-side CSRF material;
- separately signed/body-bound direct moderation reads;
- replay protection and rate limiting on the StaffBot read API;
- private/no-store responses;
- allowlisted response fields.

The raw Discord bot token is not deployed to the browser or Cloudflare application. The current staging bootstrap derives domain-separated signing material rather than forwarding the token. Dedicated independent signing secrets remain the stronger long-term production design.

### What the workspace can currently do

The staging workspace can browse bounded moderation information such as:

- channel/player navigation;
- bounded Discord message context;
- reply/jump-to-context views;
- filters and paging;
- linked accounts;
- punishment/history data;
- cases and notes exposed by the approved read model.

### What it does not do

The current merged moderation web workspace does **not** independently:

- issue live punishments;
- delete Discord messages;
- mutate punishment/case/note records;
- change Discord permissions;
- enforce Minecraft actions;
- mutate LiteBans;
- take production authority.

Do not infer write authority from the presence of buttons/previews or from the fact that the UI is hosted.

## StaffBot read bridge

The staging web workspace obtains privileged read data through StaffBot’s private moderation-read service rather than connecting a browser directly to MariaDB.

Important paths:

- [`ModerationReadApiServer.java`](https://github.com/wsg138/EnthusiaStaff/blob/main/staff-bot/src/main/java/net/enthusia/staff/discordbot/ModerationReadApiServer.java)
- [`ModerationReadApiAuthenticator.java`](https://github.com/wsg138/EnthusiaStaff/blob/main/staff-bot/src/main/java/net/enthusia/staff/discordbot/ModerationReadApiAuthenticator.java)
- [`ModerationReadRequestAuthorizer.java`](https://github.com/wsg138/EnthusiaStaff/blob/main/staff-bot/src/main/java/net/enthusia/staff/discordbot/ModerationReadRequestAuthorizer.java)
- [`ModerationReadReplayGuard.java`](https://github.com/wsg138/EnthusiaStaff/blob/main/staff-bot/src/main/java/net/enthusia/staff/discordbot/ModerationReadReplayGuard.java)
- [`ModerationReadApiRateLimiter.java`](https://github.com/wsg138/EnthusiaStaff/blob/main/staff-bot/src/main/java/net/enthusia/staff/discordbot/ModerationReadApiRateLimiter.java)

The browser/Worker is not allowed to turn this into an arbitrary SQL/query proxy. Targets, response models, authorization and field visibility remain explicit.

## Build and validation

For the Java website/API boundaries, use the normal repository validation plus focused Velocity/persistence tests.

For the Cloudflare moderation workspace:

```bash
cd moderation-web
npm install --no-package-lock --ignore-scripts
npm run check
```

The protected staging workflow additionally exercises health/origin behavior, unauthenticated rejection, signed first use, session establishment, read CORS, unauthorized requests, and replay rejection.

A successful staging moderation-web deployment proves only the recorded **staging read workspace** behavior. It does not prove production website launch or destructive Discord/Minecraft moderation.

## Review checklist

When changing the website/web boundary, verify:

- public and privileged routes remain clearly separated;
- every privileged request is authenticated and bounded;
- browser state is never treated as moderation authority;
- public projections use explicit allowlists;
- replay windows/nonces cannot be silently bypassed;
- rate limits and payload/body limits are enforced before expensive work;
- errors do not leak secrets or private evidence;
- appeal decisions still use central sanction authority;
- Cloudflare/session signing keys are not confused with the Discord bot token;
- frontend changes do not invent backend routes that are only present on an unmerged branch;
- staging evidence is not described as production acceptance.

## Current development boundary

The repository has active work expanding the website appeal lifecycle. That work is **in development** and should remain documented as such until merged. The current route list and V17 workflow above describe merged `main`.

## See also

- [[Discord Moderation Platform]]
- [[Staff Bot Runtime and Operations]]
- [[Architecture]]
- [[Privacy and Data Handling]]
- [[Integrations, Migration, and Release Readiness]]
- [[Build and Testing]]
- [[Code Review Guide]]
- [[Implementation Status]]