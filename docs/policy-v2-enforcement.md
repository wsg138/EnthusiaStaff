# Policy v2 W3B — compliance, remedies, and enforcement

Tracking: #359
Parent: #355

This package implements the non-timed-outcome side of Policy v2 without activating it.
Policy v1 remains authoritative until W4 performs the final coexistence/shadow integration and an
owner-approved cutover occurs.

## Durable lifecycle

W2 remains the canonical case/remedy store. Its remedy status is intentionally limited to
`REQUIRED`, `SATISFIED`, and `WAIVED`.

W3B adds `policy_v2_remedy_enforcement` as a durable enforcement projection with:

`REQUIRED -> ENFORCED -> SATISFIED | WAIVED`

A remedy may also move directly from `REQUIRED` to `SATISFIED` when the condition is already
corrected before an enforcement action, or to `WAIVED` with authorization.

Every W3B lifecycle mutation:

- uses optimistic revision fencing;
- uses W2's global Policy v2 operation journal for exact replay/collision handling;
- validates successful retries against the original operation key instead of accepting a new key;
- preserves existing scope authority: Market/Reputation mutations stay Admin-level and asset
  restoration keeps the existing Founder-only restore boundary;
- writes an event to W2's append-only Policy v2 audit stream;
- is transactional with its W3B projection mutation.

Terminal completion updates the canonical W2 remedy first, then the W3B projection. If the second
transaction fails, retry observes the already-terminal canonical remedy and completes the W3B
projection. Tests cover this split-transaction recovery path.

Read-only access/capability checks do not write audit events. Routine reconnects, join denials,
server restarts, and repeated checks therefore do not manufacture moderation history.

## Compliance conditions

W3B supports these typed conditions:

| Condition | Behavior |
| --- | --- |
| Username | Network access remains blocked while the current username equals the prohibited value. A changed username becomes an automatic satisfaction candidate. |
| Reliable profile component | Access remains blocked until the named component can be observed reliably and differs from the prohibited value. Missing/unreliable observations fail closed rather than guessing. |
| VPN approval | Unapproved/unknown VPN state remains blocked. VPN disabled or positively approved becomes an automatic satisfaction candidate. |
| Manual | Used for conditions whose completion must be established by a trusted provider/staff workflow. |

The access coordinator persists an observed correction before returning an allow decision. Automatic
satisfaction is limited to observed network-compliance remedies. If persistence fails, the caller
does not receive a successful allow result.

Recurrence does not automatically become an evasion offense. A new violation creates a new
compliance case/remedy. Deliberate refusal or bypass must be classified separately by policy as a
behavioral finding.

## Hard policy separation

The W3B safety guard rejects a direct ban outcome for:

- `chat.language.non-english-public` (and the legacy v1 ID);
- `access.vpn-compliance`.

Warnings/mutes remain possible for the non-English-chat rule because this guard only forbids ban
sanctions. Mute bypass is a separate `evasion.mute` finding and is not restricted by the guard.

Likewise, `access.vpn-evasion` is deliberately separate from `access.vpn-compliance`. W3B does
not invent a duration or final owner value; if an owner-approved policy later permits a serious
sanction (including a 90-day option), the enforcement safety guard does not block that separate
evasion finding.

No griefing offense or remedy is introduced.

## Remedy adapters and gates

The binding policy is an explicit allowlist:

- `CORRECT_PROFILE` -> network access with username/reliable-profile observation;
- `ACCESS_RESTRICTION` -> network, report, Market, or reputation access;
- `REMOVE_CONTENT` -> content remediation;
- `CONFISCATE` -> asset remediation;
- `OTHER` is rejected until an explicit supported binding is added.

Configured remedies may now carry optional **versioned enforcement metadata**. The metadata stores
the W3B scope/condition type and, for username/profile conditions, the stable finding attribute IDs
that supply the prohibited value/component. `PolicyV2RemedyBindingResolver` resolves that metadata
only from the pinned remedy plus the stored `IncidentFinding`; it never parses remedy descriptions
or reads mutable current player state to reconstruct a historical binding. Old persisted remedies
without metadata remain readable but cannot silently enter typed enforcement until an explicit
binding is supplied by versioned policy.

`PolicyV2CapabilityGate` supplies side-effect-free report/Market/reputation checks.
`PolicyV2RemedyActions` supplies idempotent operation IDs to content-removal and external
restriction gateways so provider retries cannot create a second logical operation.

Paper's `PolicyV2AssetRemedyAdapter` routes confiscation into the existing
`ConfiscationCoordinator`; it does not touch player inventory directly and deliberately does not
claim lifecycle success merely because the selection UI opened. Durable asset completion remains
the evidence required before a caller advances the remedy.

## W4 integration boundary

Nothing in W3B is registered into the live Paper/Velocity runtime and no v1 authority is replaced.
W4 should:

1. construct `JdbcPolicyV2EnforcementStore` beside W2's store;
2. attach the access coordinator to the supported login/access observation path in shadow/non-live
   mode first;
3. wrap report/Market/reputation entry points with the capability gate and provider adapters;
4. connect durable content/provider completion to `PolicyV2RemedyService.enforce/satisfy`;
5. connect committed confiscation evidence, not UI-open events, to lifecycle completion;
6. run the W4 adversarial/coexistence matrix before any owner-approved cutover.

The Market/Reputation providers remain the authorities for their own durable blacklist state.
W3B does not write provider tables directly.

## Pinned-condition registration boundary

The supported registration path for configured remedies is
`PolicyV2RemedyService.registerConfigured`. It reads the persisted case's effective finding
and remedy (including the retained versioned binding), resolves the typed W3B scope/condition,
then invokes the existing authorized, operation-key-fenced registration lifecycle.

The older explicit `register` entry point remains compatible with historical unbound remedies;
when binding metadata is present, it must match the exact scope and condition derived from the
persisted case. A caller cannot replace a VPN approval condition with another allowlisted
condition such as `MANUAL`, or substitute different profile information.
An overturned finding cannot enter registration.

This guards the domain registration boundary only. Provider execution, shadow/live wiring,
external acknowledgement, and the remaining unsupported `OTHER` remedies are still separate
cutover requirements.

## Canonical case subject identity

The persisted Policy v2 case read model carries the immutable canonical subject UUID from
`cases.target_id`, selected by joining the legacy canonical `cases` record to
`policy_v2_cases`. The read fails closed when the referenced case is not present. A configured
remedy registration derives the subject UUID directly from that stored case rather than taking
it from an arbitrary caller request. The legacy explicit registration API also checks the
caller-supplied subject UUID against the persisted case subject **before** registering anything.
A case/subject mismatch throws without writing a W3B enforcement row. No policy mode is changed.

Coverage must include wrong-player attempts (including explicit legacy registration), idempotent
retry, and preservation of the original case identity across revisions/restarts.

## Owner snapshot binding publication

The first disabled owner snapshot (`owner.2026-10-07.1`) remains archived unchanged. The next
disabled snapshot (`owner.2026-10-07.2`) makes W3B-compatible remedy bindings explicit:

- username compliance: requires the prohibited username as a text finding attribute;
- skin/profile compliance: requires a prohibited value, plus a typed component name where needed;
- VPN compliance: binds to the typed VPN approval condition;
- content removal and confiscation: bind to their existing manual-completion W3B provider scopes.

Required content-removal/confiscation remedies remain attached across the offense's related,
pattern, chronic, and heavy-history tiers, rather than disappearing after the baseline tier.

`OTHER` remedies (technical cleanup, Market/stall cleanup, restoration, or safety containment)
remain **unbound** because W3B explicitly disallows them. These need an approved, dedicated
adapter or an explicit manual workflow before authoritative Policy v2 cutover. A configured
`manual` condition is still a typed W3B condition, **not** proof that an external provider
executed anything. The cutover must separately prove provider operations, authorized completion,
replay/retry behavior, correction satisfaction, and overturn cleanup.

The snapshot's root feature mode remains `disabled`; no remedy bindings execute on the server
as a consequence of publication.
