# Roles and Permissions

This page explains EnthusiaStaff authority, rank identity and the difference between permission discovery, active duty context, and final application-service authorization.

Exact command nodes belong in [[Commands and Permissions]]. Discord product behavior belongs in [[Discord Moderation Platform]].

## Quick rule

A LuckPerms node, Discord role, visible command/button, active Staff Mode context, website route, or console command entry point is **not automatically final destructive authority**. Important mutations are reauthorized against current actor/target/state in the owning application service and transaction/external-effect boundary.

## Rank identity

Current merged identity nodes include permanent Enthusia staff identity such as:

```text
enthusiastaff.identity.helper
enthusiastaff.identity.mod
enthusiastaff.identity.developer
enthusiastaff.identity.admin
enthusiastaff.identity.owner
```

Legacy rank aggregates/fallback behavior remain relevant during transition. The identity node answers **who this staff member is**, not whether every action is currently authorized.

## Active duty / Staff Mode context

Paper publishes an active duty context:

```text
enthusiastaff-duty=active
```

That context lets LuckPerms-aware permissions distinguish on-duty Paper behavior from permanent staff identity.

Important distinction:

- permanent identity node = durable staff role identity;
- active duty context = current Paper Staff Mode state;
- final destructive authority = central service policy using current actor, target, operation, platform, mode and other preconditions.

Player-originated destructive Paper mutations may require active Staff Mode. Console/SYSTEM, Discord/global services and website authority are not automatically forced through the Paper-local duty context.

## Quick authority map

| Role | Minecraft/Paper policy | Discord policy | Approval/recovery |
| --- | --- | --- | --- |
| Helper | configured limited/temporary moderation and investigation; severe/permanent outcomes route upward as policy requires | warning and configured short temporary mute scope | no ordinary approval; restricted investigation tools |
| Mod | ordinary configured moderation, supported corrections and investigation tools | configured temporary Discord outcomes/custom temporary durations within policy | may review eligible requests |
| Developer | separate technical role; does not inherit normal Minecraft Mod punishment authority merely from technical access | deliberately Mod-equivalent for approved **Discord-only temporary** moderation scope | technical/investigation/recovery access as configured |
| Admin | advanced/custom moderation and correction/overturn authority as configured | permanent Discord ban/mute/channel restriction plus temporary actions | advanced review/recovery |
| Founder/Owner | broadest configured authority and cutover/recovery responsibility | broadest configured Discord authority | owner-level exceptional recovery/acceptance |

The table describes policy semantics. Live authority still depends on current identity, permission/rank state, Staff Mode where required, operational/authority mode, provider/external health and the exact service performing the action.

## Reauthorize the mutation

Reviewers should expect important writes to recheck:

- current actor identity/rank;
- target identity/protection/higher-rank state;
- exact requested operation and consequence;
- platform/enforcement scope;
- duration/custom limits;
- issuing/current rank where relevant;
- Staff Mode duty context for Paper-local actions that require it;
- operational/authority mode;
- external Discord/provider preconditions;
- stale confirmation/revision state.

If those facts cannot be established, fail closed rather than infer authority from presentation metadata.

## Discord authority is now operational code

Merged StaffBot uses the Discord authorization policy through linked-staff resolution and action-time reauthorization. The old statement that only domain policy existed while no runtime used it is no longer true.

Important rules:

- Discord roles may influence discovery/presentation but never independently grant moderation authority.
- A Discord actor must resolve to the current linked Enthusia staff identity.
- Confirmation flows reauthorize before the external effect.
- Discord hierarchy/target preconditions are checked at execution time.
- Developer’s Discord-only temporary exception does not change the global Minecraft rank hierarchy.
- Cross-platform consequences are authorized independently; there is no blanket “Discord authorized, therefore Minecraft authorized” shortcut.

See [[Discord Moderation Platform]].

## Helper

Helpers focus on limited moderation/investigation. Minecraft and Discord scopes are intentionally constrained, and severe/uncertain outcomes should route through the higher-authority workflow rather than relying on a visible UI surface.

## Mod

Mods inherit the normal Helper aggregate and receive broader configured moderation/review capability. Discord policy allows configured temporary moderation/custom temporary duration within limits; permanent Discord outcomes require higher authority.

## Developer

Developer remains a separate technical identity, not a promotion between Mod and Admin.

The approved Discord policy deliberately permits Developer to perform the same configured **Discord-only temporary moderation** scope as Mod. That exception must remain platform-specific and must not grant direct Minecraft Mod authority.

## Admin / Founder / Owner

Admin owns advanced configured/custom moderation, correction and overturn paths. Admin is the normal minimum rank for permanent Discord ban, permanent mute and permanent channel restriction under the current policy.

Founder/Owner has the broadest configured authority plus owner recovery/cutover responsibilities. Owner authority does not waive durable intent, audit, idempotency, reconciliation or privacy rules.

## Console and SYSTEM

Console and `SYSTEM` semantics must be explicit. Bukkit/console visibility bypass is not equivalent to unlimited policy authority.

Review:

- which operations allow console/system actors;
- hierarchy bypass and its limits;
- modification of system/higher-rank sanctions;
- audit identity;
- whether website/Discord/internal workers accidentally inherit console-level power;
- whether Paper Staff Mode requirements are correctly skipped only for the actor types/services that are supposed to bypass local duty state.

## Permission inheritance

Legacy/current aggregate nodes may include:

```text
enthusiastaff.rank.helper
enthusiastaff.rank.mod
enthusiastaff.rank.developer
enthusiastaff.rank.admin
enthusiastaff.rank.founder
```

Normal aggregate inheritance remains Mod → Helper, Admin → Mod, Founder → Admin, while Developer remains separate. Permanent `enthusiastaff.identity.*` nodes provide clearer role identity during the newer authority model.

Application policy can always be stricter than the permission tree.

## Discord authorization source map

Key domain areas include `DiscordModerationAuthorizationService` and related authorization request/snapshot/limit/operation/consequence/precondition policy types under `domain/.../auth/`.

Runtime use is in StaffBot through linked actor resolution and punishment/moderation services. See [[Developer Code Guide]] for the current source trace.

## Review checklist

Before changing authority:

1. Identify platform and exact consequence.
2. Separate permanent identity, active duty context, discovery permission and final mutation authority.
3. Test self/equal/higher/protected/system targets.
4. Test stale actor/target snapshots and final reauthorization.
5. Test cross-platform consequences independently.
6. Check console/SYSTEM semantics.
7. Check Staff Mode requirements for player-originated Paper mutations.
8. Check Discord/provider hierarchy preconditions at the side-effect boundary.
9. Fail closed when identity/rank/provider state cannot be established.
10. Keep authorization tests separate from production-acceptance claims.

## See also

- [[Commands and Permissions]]
- [[Discord Moderation Platform]]
- [[Staff Bot Runtime and Operations]]
- [[Staff Mode, Vanish, and Freeze|Staff-Mode-Vanish-and-Freeze]]
- [[Punishment System]]
- [[Code Review Guide]]
- [[Developer Code Guide]]