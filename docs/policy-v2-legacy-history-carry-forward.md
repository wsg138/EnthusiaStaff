# Policy v1 history carry-forward into Policy v2

Tracking: #433  
Owner policy: #423 / merged PR #424

## Purpose

Policy v2 must not treat every player as having a blank behavioral history on cutover, but it also must not invent Policy v2 facts that were never recorded under Policy v1.

The compatibility path is therefore **read-only and conservative**.

## Complete source contract

`JdbcPolicyV1BehavioralHistorySource` reads directly from authoritative Policy v1 `cases` joined to `punishment_steps`.

A legacy case is eligible only when:

- the target matches;
- `issued_at <= incidentAt`;
- the case is not `FULLY_OVERTURNED`;
- `punishment_steps.escalation_contributes = TRUE`.

There is no row-count limit. Human-facing `ModerationHistoryStore.page(...)` is not used for resolver history.

The reader does not modify legacy cases or create Policy v2 rows.

## Mapping contract

`PolicyV1HistoryCarryForward` contains an explicit decision for every reason currently configured in `reason-policies.yml`.

A decision is either:

- **mapped** to one canonical Policy v2 behavioral offense; or
- **skipped** with an explicit reason because the Policy v1 reason does not prove the factual distinction required by Policy v2.

Unknown future Policy v1 reason IDs are skipped by default.

Examples of intentional skips:

- `account.unapproved-vpn` — Policy v2 treats first detection as a compliance condition;
- `identity.inappropriate-profile` — old data does not identify username vs skin vs another surface;
- `account.unsafe-download` — old reason does not prove intentional malware/phishing;
- `exploit.duplicated-possession-unclear` — owner policy makes knowledge-unproven possession remedy-only;
- `market.compliance-failure` — retired generic selector;
- `reports.abusive-content` — old reason records the reporting surface, not the underlying abusive conduct.

`politics.extreme` maps conservatively to `chat.sensitive-topic-public`; the old severity label is not evidence of extremist advocacy.

The mapping catalogue is regression-tested against the live bundled v1 reason list so a newly added v1 reason cannot silently become carry-forward history without an explicit decision.

## Resulting history identity

Mapped v1 entries are represented only as resolver inputs:

`v1:<legacy-case-id>`

They are not fabricated Policy v2 cases.

No Policy v2 attributes, sanctions, remedies, appeal records, public projections, or policy-version claims are invented.

## Resolution semantics

The compatibility layer carries forward the **factual behavioral finding** only.

The current Policy v2 snapshot controls:

- relationship weights;
- direct decay;
- fading pattern memory;
- rule thresholds;
- sanction selection.

Old Policy v1 ladder ordinals and old punishment duration are not reinterpreted as Policy v2 severity.

This keeps the historical fact while avoiding fake Policy v2 metadata.

## Runtime behavior

The Policy v2 shadow workflow merges:

1. native complete Policy v2 history; and
2. mapped complete Policy v1 history.

The merged list is sorted deterministically by incident time and case identity before resolution.

Policy v1 remains authoritative. This compatibility read does not deploy, punish, enforce, or cut over Policy v2.
