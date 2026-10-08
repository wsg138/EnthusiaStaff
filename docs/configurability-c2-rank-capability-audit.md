# C2-A audit — current rank and capability authority

Status: research baseline only. Tracking: [#459](https://github.com/wsg138/EnthusiaStaff/issues/459), parent [#425](https://github.com/wsg138/EnthusiaStaff/issues/425).

Source baseline: `main` `11f029f0`, 2026-10-07. Live GitHub and later test results supersede this snapshot.

## Scope and non-goals

This document maps **existing** rank-sensitive decisions that C2 must preserve before replacing any gate with `ranks.yml`. It does not grant a new capability, add a new permission, change LuckPerms, change the punishment policy, or activate any new authorization path.

**Identity, authority, session duty, moderation target hierarchy, visibility audience, gameplay mode, and UI presentation are different concerns.** A generic `rank >= x` helper or GUI permission toggle cannot safely replace all of them.

## Current rank topology

`domain/src/main/java/net/enthusia/staff/domain/auth/StaffRank.java` defines `HELPER`, `MOD`, `DEVELOPER`, `ADMIN`, `FOUNDER`, `SYSTEM`.

- The moderation inheritance ladder is HELPER → MOD → ADMIN → FOUNDER.
- DEVELOPER is a **separate technical branch**, not numerically between any two moderation ranks. Some specialized actions are permitted to Developer and denied to Admin.
- SYSTEM is a dedicated service identity, not a player staff rank. It is excluded from the ordinary Staff Mode tool set and vanish eligibility.
- `StaffRank.atLeast()` is an explicit switch rather than ordinal comparison and cannot be rewritten as enum ordering.

Permanent identity resolution: `paper/.../auth/PaperStaffRankResolver.java` tries `enthusiastaff.identity.owner/developer/admin/mod/helper` first and falls back to legacy `enthusiastaff.rank.*` permissions. The identity node intentionally does **not** imply duty/active authority.

## Observed policy and enforcement surfaces

| Area | Source of current decisions | C2 safety requirement |
| --- | --- | --- |
| Player rank identity | `PaperStaffRankResolver` | Preserve identity precedence, legacy compatibility, and ambiguous-rank precedence |
| Rank inheritance | `StaffRank.atLeast` | Preserve Developer branch and SYSTEM isolation; reject inheritance cycles |
| Staff Mode initial/allowed gamemodes | `StaffModeAccessPolicy` | Keep active session authorization and reconcile existing gamemode after capability changes |
| Staff Mode inventory/ender chest | `StaffModeAccessPolicy` | Keep mutation protections and staff-tool PDC transfer protections regardless of configuration |
| Staff Mode hotbar tools | `StaffToolDefinition` | Separate tool visibility from command/service authorization; PDC IDs and slots are not authority |
| Vanish eligibility | `VanishRankReconciliationPolicy.mayVanish` and `VanishManager` | Helper remains visible by default even with stale direct permission, saved vanish, transfer or async persistence |
| Audience/vanish visibility | `StaffVanishVisibility`, `DefaultStaffVisibilityService`, `VisibilityMatrixLoader` | Preserve asymmetric viewer/target visibility and network-wide consistency |
| Punishment approval/issuance | `PunishmentApprovalRules`, `PunishmentService`, `PunishmentProposal`, `PunishmentGuiCatalog`, `StaffWebPunishmentService` | Do not allow a configurable GUI or rank label to bypass authoritative sanction policy and review boundaries |
| Staff target protection | `StaffTargetHierarchyPolicy`, `StaffHierarchy` | Independent target and issuer hierarchy checks; non-comparable Developer needs explicit treatment |
| Discord-linked actor operations | `DiscordOperationPolicy`, `DiscordMinecraftAuthorization`, `DiscordConsequencePolicy` | Separate permanent identity, linked account identity, operation, duty requirements, and independent provider authentication |
| Commands and service entrypoints | `PaperCommandRegistrar`, `CommandPermissionGate` plus individual service checks | Revalidate at execution time, not just when the UI/hotbar was built |

## Confirmed Staff Mode parity examples

From `StaffModeAccessPolicy` (not a universal capability matrix):

| Rank | Initial Staff Mode gamemode | Permitted real gamemodes | Ender chest open | Ender chest mutate | Advanced staff tools | Combat testing |
| --- | --- | --- | --- | --- | --- | --- |
| Helper | Survival | Survival, Spectator | No | No | No | No |
| Mod | Survival | Survival, Spectator | No | No | Yes | No |
| Developer | Creative | All | Yes | Yes | Yes | Yes |
| Admin | Creative | All | Yes | No | Yes | No |
| Founder | Creative | All | Yes | Yes | Yes | No |

Additional condition: `blocksAllInventoryMutation` denies general inventory mutation to Helper and SYSTEM, while Mod+ has other restrictions. Do **not** interpret `blocksEnderChestMutation=false` as unconstrained mutation anywhere. The table describes isolated rank-specific policy methods, not a complete authorization grant.

Current VANISH hotbar eligibility: Helper denied; Mod, Developer, Admin, Founder permitted *at that rank-policy step*, subject to explicit permission and session/visibility checks. `VanishRankReconciliationPolicy` independently forbids Helper and SYSTEM.

## Existing parity test inventory (reuse before adding duplicate fixtures)

- `paper/src/test/java/net/enthusia/staff/paper/staff/StaffModeAccessPolicyTest.java`: Helper/Mod mode limits, Developer and Admin/Founder real gamemode access, inventory/ender chest view-versus-edit, tool transfer protection, null/SYSTEM deny paths, and reconciliation.
- `paper/src/test/java/net/enthusia/staff/paper/staff/StaffToolDefinitionTest.java`: stable IDs, slots, hotbar availability, Helper no-vanish, and advanced-tool policy.
- `paper/src/test/java/net/enthusia/staff/paper/visibility/VanishRankReconciliationPolicyTest.java`: promotions/demotions, durable state corrections, Helper no-vanish even when unrestricted, inactive sessions, unknown sessions, null/SYSTEM and persisted rank restoration.
- `paper/src/test/java/net/enthusia/staff/paper/auth/PaperStaffRankResolverTest.java`: identity/legacy precedence.
- `domain/src/test/java/net/enthusia/staff/domain/auth/StaffHierarchyTest.java` and `StaffTargetHierarchyPolicyTest.java`: issuer/target hierarchy decisions.

C2 shadow parity should reuse these exact assertions as the legacy oracle before enabling any configured decision. New tests should cover *cross-subsystem* and *configuration-reload* scenarios not presently covered, especially revocation while an async operation is in flight.

## Capability model proposed for C2-B (not deployed)

Keep a stable typed ID per action, e.g. `STAFF_MODE_ENTER`, `VANISH`, `SPECTATE`, `REPORT_MANAGE`, `FREEZE`, `INVENTORY_VIEW`, `INVENTORY_EDIT`, `ENDER_CHEST_VIEW`, `ENDER_CHEST_EDIT`, `PUNISH_REQUEST`, `PUNISH_ISSUE`, `PUNISH_REVIEW`, `CHEAT_TEST`, `STAFF_TOOL_TELEPORT`, `SENSITIVE_HISTORY`, `RECOVERY_ADMIN`. Names need a design review before becoming persisted schema IDs.

Each operation must evaluate a complete context, **not** capability membership alone:

1. resolved permanent identity;
2. current configured rank/capability snapshot and direct permission if required;
3. active-duty/session and operational-mode checks;
4. target hierarchy and action-specific policy;
5. state/transaction/idempotency/authorization recheck immediately before mutation;
6. revocation/reconciliation if an authority snapshot changes during a player session.

`ranks.yml` should describe owner policy, *not* replace enforcement against stale sessions, tampered GUI items, saved vanish, unsafe inventory writes, or active punitive remedies.

## C2-C shadow parity requirements

Before switching an enforcement path:

- Run a complete rank × capability matrix, including null rank and SYSTEM.
- Test Developer as a separate branch (never ordinal).
- Test a Helper with direct `enthusiastaff.vanish` permission and stale saved/transfer vanish state: remain visible.
- Test a rank upgrade and downgrade while duty mode is active and while an async state write is outstanding.
- Test broken/missing LuckPerms identity and conflicting identity/legacy nodes; fail closed for authority.
- Test real gamemode transition, selected mode persistence, ender chest edit/open separation, and target hierarchy.
- Test existing GUI/tool access against **service** permission parity; hidden icons alone do not enforce authorization.
- Test negative and malicious `ranks.yml`: unknown capability, inheritance cycle, duplicate key, illegal SYSTEM inheritance, malformed direct permission, invalid version and partially rejected reload.
- Verify that validation never changes active state and failed reload preserves the last-good immutable capability snapshot.
- Audit configured authorization policy changes and provide a safe rollback/restart story.

## Migration ordering and collision boundaries

1. C2-A audit (this file + follow-up matrix fixtures).
2. C2-B add read-only `ranks.yml` parser, validation, and immutable candidate with no enforcement change.
3. C2-C shadow-decision comparison against current rank/duty decisions with zero behavioral impact.
4. C2-D implement one vertical slice only after parity and owner review, covering commands, UI, service, durable data, and reconciliation.
5. C2-E progressively migrate the remaining slices with production acceptance.

Coordinate with **#271 Staff Mode Authority v2** and **#355 Policy v2**. Do not modify those workers' active enforcement branches or create a second punishment/Discord policy stack. The preexisting rank checks are not all feature flags—some are correctness/safety constraints.

This audit is deliberately a map of known enforcement points, not a claim to have enumerated every permission string across all modules. A follow-up inventory should enumerate `plugin.yml`, Bukkit permission checks, web/Discord authority boundaries, and workflow tests mechanically before the data schema is finalized.
