# Roles and Permissions

This page explains the authority model behind EnthusiaStaff rank aggregates. Staff procedure belongs in [[Staff Handbook]] and the focused feature guides; exact command nodes belong in [[Commands and Permissions]].

Permission nodes control discovery and early denial. Important writes are reauthorized inside central services, so granting one node must not silently turn a role into a higher moderation rank.

> **Discord distinction:** the Discord-specific authorization **domain policy is merged**, including the deliberate Developer Discord-only exception. The interactive Discord staff-bot/runtime that would call that policy is not yet available on merged `main`. See [[Discord Moderation Platform]].

## Quick authority map

| Role | Minecraft moderation | Discord-specific domain policy | Approval / recovery |
| --- | --- | --- | --- |
| Helper | Configured temporary outcomes; permanent results become requests | Warning and configured short temporary mute only | No approval; restricted investigation tools |
| Mod | Configured ordinary outcomes, including configured permanent steps | Configured temporary Discord outcomes and custom temporary duration within policy | May review eligible requests; configured asset tools |
| Developer | Technical/request-preparation role; no automatic direct Minecraft punishment authority | Same temporary Discord moderation authority as Mod | No ordinary punishment approval; technical/investigation tools |
| Admin | Advanced configured/custom moderation authority | Includes permanent Discord ban/mute/channel restriction in the approved policy | Review/overturn and advanced tools as configured |
| Founder | Broadest configured/custom authority | Broadest configured Discord authority | Owner recovery, exceptional approval and cutover authority |

The table describes merged policy semantics, not a claim that every Discord surface or provider is operational. Live authority also depends on current LuckPerms configuration, current staff identity/rank, operational mode, feature/provider health and the runtime path invoking the domain service.

## Core rule: reauthorize the mutation

A command permission, GUI button, website route or Discord role is never final authority for a destructive moderation action.

Reviewers should expect the mutation path to resolve/recheck:

- current actor identity and explicit Enthusia rank;
- target identity and protected/higher-rank state;
- exact requested operation/consequence;
- platform and enforcement scope;
- duration/custom limits;
- issuing/current rank where relevant;
- operational/authority mode;
- external preconditions immediately before an external side effect;
- stale confirmation/revision state.

If those facts cannot be established, the action should fail closed rather than infer authority from presentation metadata.

## Helper

Helpers focus on ordinary moderation and investigation. Current Minecraft policy permits configured temporary results while a result containing a permanent sanction becomes an approval request.

The merged Discord authorization policy is intentionally narrower for Helper: warnings and configured short temporary mutes. Discord kicks, bans, permanent mutes/restrictions, broad custom actions and cross-platform effects require higher/independent authority.

Helpers cannot approve their own escalation path and should hand off severe or uncertain cases even when a surface is visible.

See [[Helper Guide]].

## Mod

Mods inherit the Helper toolset and add ordinary request review, network-ban permission, selected sanction changes, inventory editing and configured confiscation capabilities.

For Minecraft, the central policy may authorize configured punishment steps and supported corrections according to hierarchy and action rules.

For Discord, the merged policy allows configured temporary moderation and custom temporary durations within runtime-supplied limits. Permanent Discord ban, permanent mute and permanent channel restriction require Admin/Founder-level authority.

## Developer

Developer is a technical role, not a promotion step between Mod and Admin.

For Minecraft moderation, Developer does not gain direct punishment authority merely because a technical or investigation permission exposes a surface. Existing Minecraft/domain policy remains independently authoritative; Developer may prepare request-oriented work where the existing policy allows it.

For Discord, the merged `DiscordModerationAuthorizationService` deliberately treats Developer as Mod-equivalent for **Discord-only temporary moderation**. This does not turn the Developer rank into a Minecraft Mod rank and must not be implemented by altering the global rank hierarchy.

Cross-platform actions are especially important: each requested consequence/scope must satisfy its own platform policy. A Discord-origin action cannot use the Discord Developer exception to bypass Minecraft authority.

## Admin and Founder

Admin adds advanced diagnostics/configuration, authorized custom duration/raising/lowering/overturn behavior and selected provider restrictions. In the approved Discord policy, Admin is the minimum normal rank for permanent Discord ban, permanent mute and permanent channel restriction.

Founder has the broadest configured policy plus owner recovery and authority-transition responsibilities. Founder access remains audited; owner authority is not a reason to bypass durable intent, verification, recovery or history.

## Console and SYSTEM

Console/`SYSTEM` semantics must be explicit. Do not treat console as an unlimited ordinary staff member simply because it bypasses Bukkit command visibility.

Review every command/service that accepts console or an internal system actor for:

- which operations are allowed;
- whether hierarchy bypass exists and what it excludes;
- whether system-issued sanctions may be modified;
- what actor identity is written to audit/history;
- whether website/Discord/internal jobs accidentally inherit console-level power.

## Aggregate inheritance

Current Paper metadata defines:

```text
enthusiastaff.rank.helper
enthusiastaff.rank.mod
enthusiastaff.rank.developer
enthusiastaff.rank.admin
enthusiastaff.rank.founder
```

Normal inheritance is:

- Mod inherits Helper.
- Admin inherits Mod.
- Founder inherits Admin.
- Developer remains a separate technical aggregate.

Application policy may be stricter than the permission tree. The Discord Developer exception is modeled as a platform-specific authorization capability rather than by changing Developer into a Mod-equivalent global rank.

## Discord authorization source map

Merged `main` includes:

- `domain/src/main/java/net/enthusia/staff/domain/auth/DiscordModerationAuthorizationService.java`
- `DiscordAuthorizationRequest`, `DiscordAuthorizationSnapshot`, `DiscordAuthorizationDecision`
- `DiscordAuthorizationLimits`
- `DiscordOperationPolicy`, `DiscordConsequencePolicy`, `DiscordPreconditionPolicy`
- `DiscordMinecraftAuthorization`
- focused authorization/target-protection/operation-matrix/cross-platform tests
- [`docs/discord-authorization.md`](../../../docs/discord-authorization.md)

These prove the domain policy exists. They do not prove a Discord bot, command, role synchronization or live Discord side effect exists.

## Review checklist

Before changing rank or authorization behavior:

1. Identify the platform and exact consequence being authorized.
2. Compare presentation permissions/roles with central service policy.
3. Test self-target, equal/higher-rank, protected/system targets and accidental extra permissions.
4. Test stale actor/target snapshots and reauthorization immediately before mutation.
5. Test cross-platform requests as separate consequences, not a shared shortcut.
6. Check console/SYSTEM behavior explicitly.
7. Verify provider/Discord hierarchy preconditions are checked at the side-effect boundary.
8. Check fail-closed behavior when staff identity/rank/provider state cannot be resolved.
9. Keep authorization tests separate from claims of live runtime/staging acceptance.

Use [[Code Review Guide]] for the full cross-cutting checklist.

## Go deeper

- [[Commands and Permissions]] — registered commands/nodes.
- [[Discord Moderation Platform]] — merged Discord foundations versus future runtime.
- [[Punishment System]] — staff punishment procedure.
- [[Moderation, Punishments, and Reports]] — punishment/request implementation status.
- [[Staff Tools, Investigations, and Player-State Safety]] — staff-tool/player-state boundaries.
- [[Developer Code Guide]] — exact source navigation.
- [[Code Review Guide]] — authority review discipline.