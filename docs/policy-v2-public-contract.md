# Policy v2 public punishment contract

W3C owns the canonical sanitized Policy v2 punishment representation. This work is stacked on the stable W1 contract and W2 persistence boundary; Policy v1 remains authoritative and no production route is switched by this package.

## Canonical representation

`PolicyV2PublicProjection` is the only object public Policy v2 consumers should receive. It is deliberately allowlisted and contains:

- stable public case ID;
- current and incident-time Minecraft names only when approved for public display;
- public category, offense label, and concise reason;
- optional simple related-history wording, never history scores or decay inputs;
- current public case status: `ACTIVE`, `EXPIRED`, `REDUCED`, `RECLASSIFIED`, `APPEALED`, or `OVERTURNED`;
- appeal status;
- issued/expiry timestamps;
- safe sanction summaries;
- safe remedy/compliance summaries;
- a sanitized public revision timeline;
- optional policy version;
- projection revision.

Internal sanction variants are collapsed. In particular, `NETWORK_IDENTITY_BAN` is published only as `BAN`. Remedy-like sanction types are rejected from the public-sanction path rather than serialized.

## Data that cannot cross the boundary

The public projection has no fields for:

- IP addresses or alt/linkage evidence;
- evidence or structured incident attributes;
- internal staff notes or private discussion;
- actor/staff IDs;
- appeal references;
- operation/idempotency keys;
- detection or anticheat internals;
- hidden history inputs, weights, decay math, or recurrence calculations;
- private moderation IDs when a public label exists.

`PolicyV2PublicProjector` intentionally ignores durable finding-revision reasons, sanction-revision reasons, remedy descriptions, appeal notes, actor IDs, and appeal references. Public timeline text is generated from fixed safe phrases and public offense labels.

## Consumer adapters

All consumers take the canonical projection instead of independently querying private records.

- `PolicyV2PublicApiAdapter` is the exact HTTP/website response allowlist.
- `PolicyV2PublicDiscordAdapter` produces a Discord-neutral message model from the same projection.
- `PolicyV2LegacyPublicPunishmentAdapter` is an explicit compatibility bridge to the existing v1 `PublicPunishment` contract for representable BAN/MUTE/WARNING records.

The existing live v1 website registry and Discord delivery remain unchanged. A later integration/cutover worker can feed the canonical Policy v2 projection into these adapters without giving those consumers access to `PolicyV2Store.CaseRecord` or JDBC rows.

## Status semantics

Status is a compact current public state, while the timeline preserves public revisions.

1. an overturned finding is `OVERTURNED`;
2. an open submitted/reviewing appeal is `APPEALED`;
3. a case whose public sanctions and remedies are all complete is `EXPIRED`;
4. otherwise the latest applicable factual reclassification or leniency state is `RECLASSIFIED` or `REDUCED`;
5. remaining cases are `ACTIVE`.

Appeal status is separate so a denied/approved/withdrawn appeal does not erase the underlying active/expired case state.

## Persistence and migration impact

No new migration is required. W2 V28 already stores `projection_json` plus a projection revision, so W3C expands the pre-cutover JSON contract without changing the table shape. A read-time compatibility decoder translates retained W2 projection JSON (including `RESOLVED`, `incidentAt`, and W2 sanction types) into the canonical safe representation; the next normal publish writes the new shape. No v1 case is rewritten or backfilled and no Policy v2 production cutover occurs here.

Before a future cutover, integration should materialize/publish the canonical projection through `PolicyV2Store.publishProjection(...)` and expose only `PolicyV2Store.publicProjection(...)` or another service that returns this safe type. Public API, Discord, and website code must not reconstruct public views from private Policy v2 tables independently.
