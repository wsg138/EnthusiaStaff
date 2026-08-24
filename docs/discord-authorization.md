# Discord moderation authorization contract

`ES-D03` adds the domain authorization boundary used by later Discord entry points. It does not add a bot, Discord API calls, production permissions, or punishment side effects.

## Authority source

`DiscordModerationAuthorizationService` is the single domain policy for staff actions initiated through Discord. Discord roles and command visibility are deliberately absent from its authorization inputs. A Discord role may hide/show commands and the service may return `DISCORD_ROLE_HIERARCHY` as an external enforcement precondition, but satisfying that precondition never grants permission.

The caller must resolve the Discord invoker to an authoritative linked Enthusia `Actor` before calling this service; Discord role membership alone cannot construct authority. The caller must also resolve target staff status authoritatively before authorization. `Optional.empty()` for target staff is valid only after identity resolution proves the target is not protected staff; it must not be used as a placeholder for an unresolved target. The service also does not accept command origin as authority. The final selected platform set is explicit. Origin may choose a UI default only; it cannot silently broaden a request to both platforms.

## Rank policy

| Rank | Discord authority |
| --- | --- |
| Helper | Investigation/read capabilities plus configured warnings and temporary mutes no longer than the injected helper ceiling. No custom duration, kick, ban, channel restriction, permanent action, overturn, or Minecraft/cross-platform mutation. |
| Mod | Read capabilities; configured warning/mute/kick/ban/channel-restriction actions; custom temporary duration within the injected per-action ceiling; end/revoke; sanction-request approval; overturn request. No permanent Discord mute/ban/restriction, unrestricted custom consequence, or full/approval overturn. |
| Developer | Exactly the Mod Discord policy. Discord Developer rank itself grants no Minecraft punishment authority; Minecraft authorization is evaluated independently through the existing `AuthorizationPolicy`, so a Developer may mutate Minecraft only when that Minecraft/domain policy independently permits the required action. |
| Admin | Full Discord operation set, including permanent Discord mute/ban/restriction and custom Discord consequences/durations. Minecraft mutations still pass the existing Minecraft authorization checks. |
| Founder | Full Discord operation set and existing Founder Minecraft authority. |
| SYSTEM | No interactive Discord staff authority. Automated systems must use their separately reviewed service paths. |

D03 deliberately defines no production duration numbers. `DiscordAuthorizationLimits` must be supplied by later approved runtime/configuration code.

## Explicit cross-platform plans

Sanction issuance uses one `DiscordConsequenceIntent` per selected platform. The selected platform set must exactly match those intents and only one intent may exist per platform. This allows Discord and Minecraft to have different consequences and prevents a magic `BOTH` value from silently cloning one consequence across platforms.

Any Minecraft mutation is checked against the existing `AuthorizationPolicy`. Minecraft custom-duration and custom-combination flags require the corresponding existing Minecraft capabilities. In addition, every allowed Minecraft mutation returns `MINECRAFT_PUNISHMENT_POLICY_REVALIDATION`: later Discord/cross-platform code must pass the exact final Minecraft action through the existing authoritative Minecraft punishment/case policy immediately before commit. D03 never authorizes applying its raw Minecraft consequence intent directly. This prevents Discord UI flags or stale recommendation state from bypassing Minecraft escalation/custom-consequence rules.

## Target protection

Mutation requests block self-targeting and equal/higher staff targets. For Discord protection only, Developer and Mod are peers; Admin is above both; Founder is above Admin; SYSTEM is protected. Read-only investigation is not treated as a target mutation.

## Confirmation and stale state

A permitted request may be captured as `DiscordAuthorizationSnapshot` when the confirmation UI is shown. Immediately before commit/enforcement, callers must use `reauthorize(...)` with the current authoritative actor and current target-staff state. Rank or target identity/rank changes invalidate the snapshot and return `STALE_AUTHORIZATION`; the caller must rebuild confirmation rather than reusing stale UI authority.

## External enforcement preconditions

For Discord actions that require native role-sensitive side effects, an allowed decision includes `DISCORD_ROLE_HIERARCHY`. For Minecraft mutations, an allowed decision includes `MINECRAFT_PUNISHMENT_POLICY_REVALIDATION`. Later adapters/orchestrators must satisfy every returned precondition immediately before the corresponding commit/side effect. The precondition set is empty on denied decisions and can never be used to convert a denial into authorization.
