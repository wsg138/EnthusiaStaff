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

The screens are represented by PolicyV2GuiState and rendered by PolicyV2GuiRenderer. PolicyV2GuiController handles inventory clicks, back/edit navigation, typed question capture, async policy/history evaluation, stale-result refresh, and final shadow submission. PolicyV2GuiNavigator keeps navigation/edit semantics separate from Bukkit events, while PolicyV2ManualWorkflow owns policy orchestration. Persistence is behind PolicyV2StoreAdapter and the W2 PolicyV2Store port.

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

Only attributes declared by the selected OffensePolicy are exposed. Clicking a question captures a bounded chat answer; boolean, integer, enum, and text inputs are parsed and then validated against the W1 type/range/allowed-value definition. Optional answers may be skipped. Going back from review preserves confirmed answers for editing, going back to exact conduct clears offense-specific answers, and going back to category clears all dependent offense state. This prevents facts from one offense being silently reused for another.

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
3. reloads the complete W2 behavioral history at the incident time through the completeness-safe contract;
4. resolves the finding again;
5. compares the refreshed evaluation with the review the staff member saw.

Any change returns a stale result containing the refreshed review. Nothing is persisted until the staff member reviews that recalculation.

Successful shadow submissions use PolicyV2Store.recordShadowEvaluation through PolicyV2StoreAdapter. Resolver reads use PolicyV2Store.completeHistory and never the bounded human-facing history API. W2 operation-key idempotency/conflict semantics remain authoritative. A typed W2 conflict is returned to the GUI orchestration as an explicit conflict result.

## W5B operational shadow integration

W5B supplies the active snapshot through the validated atomic publication runtime. The controller is registered once, but every open/click/chat boundary is feature-gated by the current Policy v2 mode. In `disabled`, the manual workflow is not exposed. In `shadow`, active-duty staff with the normal punishment authority can open it with `/estaff policyv2 <player>`.

Reloading to `disabled` fences already-open Policy v2 screens before another evaluation can be recorded. Reloading or publishing a new snapshot also triggers the existing stale-review protection because the confirmation recomputes against the publisher's current active snapshot.

W5B still does not alter `/punish`, create Policy v2 cases, issue sanctions, enforce remedies, or add an authoritative Policy v2 mode. The existing Policy v1 draft/request/enforcement path remains authoritative. Any later cutover must be a separate owner-authorized change and must not bypass `PolicyV2ManualWorkflow`'s stale-review and authorization checks.
