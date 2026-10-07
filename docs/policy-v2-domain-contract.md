# Policy v2 domain contract

Policy v2 is additive. The existing Policy v1 reason ladder remains authoritative until a later cutover explicitly changes that.

## Stable consumer API

Workers W2/W3 should treat `net.enthusia.staff.domain.policyv2` as the Policy v2 boundary.

- `PolicySnapshot` is the immutable, versioned configuration snapshot. Configuration loaders must construct a complete snapshot and allow its constructor validation to fail closed; they must not partially publish an invalid snapshot.
- `OffensePolicy` identifies a finding independently from GUI navigation. `navigationGroupId` is presentation metadata only and is never used by the resolver to infer relationships or sanctions.
- `IncidentAttributeDefinition` and `IncidentAttributeValue` define offense-specific structured facts. `IncidentFinding` contains only an offense ID and those facts.
- `HistoryPolicy` contains explicit offense-to-offense relationship weights plus the offense's `DecayPolicy`. There is no category-equality fallback.
- `PolicyResolver.resolve(snapshot, finding, incidentAt, history)` is the deterministic entry point. It uses the supplied incident time rather than wall-clock time.
- `PolicyAction.Exact`, `PolicyAction.Bounded`, and `PolicyAction.RequiresReview` are the only resolution action shapes. Bounded discretion is a finite set of configured sanction options plus the minimum authorized rank.
- `RemedySpec` is returned separately from `PolicyAction`. Confiscation/content-removal actions are rejected from v2 punitive sanction lists so callers cannot accidentally merge remedies into sentencing.
- `BehavioralHistoryEntry` represents the current factual finding state. Overturned findings contribute nothing; reclassified findings contribute under the replacement offense. Sanction-only changes do not alter this history record.
- `CaseRevision.SanctionRevision`, `FindingReclassification`, and `FindingOverturn` are intentionally different domain types so persistence and appeal code cannot treat leniency as a factual correction.

## History and decay semantics

History is an explainable weighted sum, not an opaque risk score. For each prior non-overturned finding related to the current offense:

1. the current offense's configured relationship weight selects how relevant that prior finding is;
2. the prior offense's decay policy determines how much of that finding remains at `incidentAt`;
3. exponential policies use `2^(-age/effectiveHalfLife)`;
4. repeated earlier findings that are related to that prior offense increase its effective half-life by the configured per-prior increment, capped by the configured maximum multiplier;
5. non-decaying policies always use a decay factor of `1.0`.

`HistoryAssessment.Contribution` exposes the relationship weight, decay factor, half-life multiplier, recurrence count, and final contribution for every included case. Rule thresholds operate on the sum of those visible contributions.

## Fail-closed behavior

A resolution becomes `REQUIRES_REVIEW` when the offense is unknown, required/typed incident attributes are invalid, the supplied history snapshot is temporally invalid, or no configured rule matches. Policy snapshots reject duplicate IDs, unknown history relationship targets, invalid attribute predicates, malformed decay settings, and overlapping resolution rules. Overlap rejection prevents file order from becoming hidden policy priority.

## W2 persistence expectations

Persist the policy version used for every evaluated/committed v2 case, the original finding, effective finding state, incident attributes, and the history snapshot inputs needed for audit/replay. Persist sanction revisions separately from factual finding revisions/overturns. Do not mutate old snapshots or rewrite v1 cases.

## W3 workflow expectations

The GUI/workflow chooses an offense and collects only that offense's declared attributes. It should display the resolver's exact action, bounded choices, remedies, and `HistoryAssessment` explanation. A `RequiresReview` result must route to the authorized review path rather than enabling arbitrary staff sentencing.

## Intentionally unresolved owner policy

This package contains no production punishment durations or owner policy guesses. W0/owners still need to supply the final offense set, navigation grouping, attribute choices, relationship matrix, decay half-lives, recurrence increments/caps, non-decaying offenses, rule thresholds, sanction options, remedy requirements, and any bounded-discretion authority choices. A later configuration loader may map YAML/JSON into these domain types, but the resulting `PolicySnapshot` must pass the same validation before publication.
