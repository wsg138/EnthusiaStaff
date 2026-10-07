# Policy v2 manual punishment GUI

Tracking: #358 under #355.

## Authority boundary

This W3A implementation is intentionally additive. Policy v1 remains the authoritative live punishment workflow. The Policy v2 manual workflow can calculate and persist W2 shadow evaluations, but it does not call the authoritative punishment service, create a Policy v2 enforcement case, apply sanctions, or deploy/cut over a command path.

That boundary is deliberate while the owner policy matrix and an approved active Policy v2 snapshot publication path are still unresolved. Registering a second live punishment path before those inputs exist would violate the Policy v1 authority requirement.

## Manual workflow

The presentation/state model supports:

1. player;
2. one of the explicit Policy v2 navigation categories;
3. exact configured conduct from the W1 PolicySnapshot;
4. only the IncidentAttributeDefinition questions declared by that offense;
5. staff review of the selected conduct and confirmed facts;
6. deterministic W1 resolution using W2 behavioral history;
7. a human-readable policy result;
8. a final shadow submission that shows whether the eventual authoritative route would be direct confirmation, an approval request, or Admin/Founder review.

The screens are represented by PolicyV2GuiState and rendered by PolicyV2GuiRenderer. The orchestration is owned by PolicyV2ManualWorkflow. Persistence is behind PolicyV2StoreAdapter and the W2 PolicyV2Store port.

## Navigation

The UI contains exactly these owner-requested groups:

- Chat & Spam
- Harassment & Abuse
- Hate & Extremism
- Sexual/Inappropriate Content
- Safety/Threats/Privacy
- Advertising/Scams
- Cheating
- Exploits/Bug Abuse/Duplication
- Server Disruption
- Accounts/VPN/Access
- Evasion/Alt Abuse
- Profiles/Identity
- Reports/Evidence/Staff Cooperation
- Economy/Market
- Reputation
- Policy Gap

There is no griefing category.

Configured offenses are browsed by OffensePolicy.navigationGroupId. The GUI never infers sanction relationships from category membership.

## Questions and editing

Only attributes declared by the selected OffensePolicy are exposed. Answers are validated against the W1 attribute type/range/allowed values before being accepted. Going back to edit the exact offense clears offense-specific answers; going back to edit the category clears all dependent offense state. This prevents facts from one offense being silently reused for another.

Policy Gap is separate from configured offenses. It requires a short factual description and always routes to Admin/Founder review. It never exposes a free-form punishment or duration control.

## Review presentation

Ordinary staff presentation includes:

- what happened;
- confirmed attributes;
- relevant-history explanation;
- remedies;
- exact or bounded sanction recommendation;
- why the configured policy reached the result;
- policy version;
- eventual confirmation/approval route.

Stable attribute IDs are humanized for display. Raw matched-rule IDs, case IDs, relationship weights, decay factors, total contribution values, and other internal policy math are not shown.

History wording is intentionally qualitative: for example, whether related history is still strongly relevant, has partly decayed, or is mostly decayed, and whether a repeated pattern keeps older conduct relevant longer.

## Stale confirmation and persistence

Before a shadow confirmation is recorded, PolicyV2ManualWorkflow:

1. rechecks current actor authority;
2. reloads the current PolicySnapshot;
3. reloads W2 behavioral history at the incident time;
4. resolves the finding again;
5. compares the refreshed evaluation with the review the staff member saw.

Any change returns a stale result containing the refreshed review. Nothing is persisted until the staff member reviews that recalculation.

Successful shadow submissions use PolicyV2Store.recordShadowEvaluation through PolicyV2StoreAdapter. W2 operation-key idempotency/conflict semantics remain authoritative. A typed W2 conflict is returned to the GUI orchestration as an explicit conflict result.

## Integration handoff

A later authorized integration/cutover step must provide the active validated PolicySnapshot source and register the event/command adapter. That step must not bypass PolicyV2ManualWorkflow's stale-review and authorization checks.

Until such a cutover is explicitly approved, the existing /punish command and Policy v1 draft/request/enforcement path remain unchanged.
