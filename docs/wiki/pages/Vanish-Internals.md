# Vanish Internals

This page describes what the current vanish implementation actually does. It
separates implemented behavior from broader goals so reviewers do not assume that
one `hidePlayer` call covers every plugin, packet, command, and visual effect.

## Quick navigation

- [[Main files|Vanish-Internals#main-files]]
- [[State and startup|Vanish-Internals#state-and-startup]]
- [[Toggle flow|Vanish-Internals#toggle-flow]]
- [[Events handled directly|Vanish-Internals#events-handled-directly]]
- [[Visibility decisions|Vanish-Internals#visibility-decisions]]
- [[Packets and Paper visibility|Vanish-Internals#packets-and-paper-visibility]]
- [[What is not currently intercepted|Vanish-Internals#what-is-not-currently-intercepted]]
- [[Helper behavior|Vanish-Internals#helper-behavior]]
- [[Performance and threading|Vanish-Internals#performance-and-threading]]
- [[Review and staging checklist|Vanish-Internals#review-and-staging-checklist]]

## Main files

```text
paper/src/main/java/net/enthusia/staff/paper/visibility/VanishManager.java
paper/src/main/java/net/enthusia/staff/paper/visibility/DefaultStaffVisibilityService.java
paper/src/main/java/net/enthusia/staff/paper/api/StaffVisibilityService.java
persistence/src/main/java/net/enthusia/staff/persistence/JdbcVanishStore.java
paper/src/main/java/net/enthusia/staff/paper/staff/StaffModeManager.java
paper/src/main/java/net/enthusia/staff/paper/auth/PaperStaffRankResolver.java
```

`VanishManager` owns lifecycle and Bukkit application. The visibility service owns
the current in-memory decision. `JdbcVanishStore` persists whether a staff member
should remain vanished across restart.

## State and startup

During initialization, `VanishManager` performs database work on the bounded
worker executor. It loads up to 10,000 active vanish records and copies their UUID
and recorded rank into `DefaultStaffVisibilityService`.

It then schedules a Paper-safe operation that:

1. records the current rank of every online staff viewer;
2. recalculates visibility for every online viewer and target.

If storage is unavailable, the initialization does not invent vanish state. The
feature remains incomplete or degraded until the store is ready.

## Toggle flow

`/vanish` reaches `VanishManager.toggle(Player)`.

The current flow is:

1. Resolve the player's explicit EnthusiaStaff rank from permissions.
2. Reject the request when no supported rank can be resolved.
3. Require active staff mode for Helper, Mod, and Developer.
4. Calculate the opposite of the current vanish state.
5. Persist the new state asynchronously in `VanishStore`.
6. When staff mode is active, update the staff-session record too.
7. Return to a Paper-safe scheduler.
8. Update the viewer rank and vanished map.
9. Run `refreshAll()`.
10. Send the player the enabled or disabled confirmation.

The visible state is changed only after persistence succeeds. A storage failure
leaves the existing visibility decision in place and reports an error rather than
pretending the toggle succeeded.

## Staff-mode exit

`StaffModeManager` calls `VanishManager.staffModeExited(UUID)` through its exit
listener. If the player is online, currently vanished, and their rank requires
staff mode, vanish is disabled through the normal persisted `set` flow.

In the Helper branch, Helper, Mod, and Developer require staff mode. Admin and
Founder may remain vanished independently.

## Events handled directly

The current class directly listens to only two Bukkit/Paper player events:

### `PlayerJoinEvent`

Registered at `EventPriority.HIGHEST`.

- Records the joining player's staff rank for visibility decisions.
- Suppresses the join message when that joining player is already marked vanished.
- Calls `refreshAll()` so all online viewer-target relationships are reapplied.

### `PlayerQuitEvent`

Registered at `EventPriority.HIGHEST`.

- Suppresses the quit message when the leaving player is vanished.
- Removes that player from the in-memory viewer-rank map.

The current `VanishManager` does not directly listen for chat, command completion,
teleport, entity-tracking, sound, particle, inventory, damage, pickup, advancement,
scoreboard, or voice events.

## Visibility decisions

`DefaultStaffVisibilityService` keeps two concurrent maps:

```text
vanished player UUID -> vanished player's staff rank
viewer UUID           -> viewer's staff rank
```

`canSee(viewer, target)` returns true when:

- the target is not vanished;
- the viewer is the target; or
- the viewer has a staff rank whose configured matrix includes the target's rank.

A non-staff viewer has no entry in the viewer map and therefore cannot see a
vanished target.

### Current default matrix on the Helper branch

| Viewer | Vanished ranks visible to that viewer |
| --- | --- |
| Helper | Helper |
| Mod | Helper, Mod, Developer |
| Developer | Helper, Mod, Developer |
| Admin | Helper, Mod, Developer, Admin |
| Founder | Helper, Mod, Developer, Admin, Founder |

The player can always see themselves. The matrix is loaded from configuration and
must define every supported staff viewer rank.

## Applying visibility

`refreshAll()` performs a nested loop over all online players:

```text
for each viewer
  for each target
    canSee(viewer, target)
      -> viewer.showPlayer(plugin, target)
      or viewer.hidePlayer(plugin, target)
```

This is the only direct entity/player visibility application in the current
manager.

## Packets and Paper visibility

The vanish implementation does **not** register a ProtocolLib `PacketAdapter`,
intercept Netty channels, or manually cancel named Minecraft packets.

It calls the Paper/Bukkit APIs:

```text
Player#hidePlayer(plugin, target)
Player#showPlayer(plugin, target)
```

Paper is responsible for translating those API calls into its internal player
tracking and client updates. The exact packet sequence is an implementation detail
of the supported Paper build and should not be documented as a stable
EnthusiaStaff contract.

A reviewer should therefore distinguish:

- **What EnthusiaStaff requests:** hide or show one player to one viewer through
  the Paper API.
- **What Paper sends:** the version-specific client tracking updates necessary to
  apply that request.
- **What other plugins expose:** their own tab lists, chat recipients, command
  suggestions, voice channels, APIs, or cached player lists unless they consult
  the shared visibility service.

Do not claim that EnthusiaStaff is cancelling a specific entity-destroy, player
info, spawn-player, or metadata packet unless direct packet-handling code and tests
are added for the supported Paper versions.

## What is not currently intercepted

The current vanish manager does not itself guarantee hiding from:

- custom tab-list plugins or cached player-count displays;
- `/seen`, `/list`, message, teleport, pay, or other command completions;
- RoseChat recipient selection or private-message lookup;
- voice-chat recipient discovery;
- particles, sounds, block changes, or container animations caused by unrelated
  plugins;
- scoreboard teams, boss bars, advancements, death messages, or custom joins;
- external web APIs, Discord messages, analytics, or playtime systems;
- plugins that ignore Bukkit `canSee` and the EnthusiaStaff visibility service.

These require integration adapters or consumers of `StaffVisibilityService`.
The requirements matrix correctly treats full visibility coverage as partial until
those integrations and staging checks exist.

## Public visibility service

`StaffVisibilityService` is the stable in-process boundary other plugins should
use instead of reading vanish storage or duplicating rank rules.

Consumers should ask whether a viewer may see a target before:

- including the target in a player list or completion result;
- delivering chat or voice presence;
- rendering staff-sensitive notifications;
- exposing online status through an API;
- showing teleport, message, or payment suggestions.

Adapters must fail conservatively when they cannot obtain a trustworthy visibility
decision.

## Helper behavior

The active Helper branch adds `StaffRank.HELPER` to persistence, rank resolution,
the visibility matrix, and staff-mode access policy.

Intended behavior:

- Helpers may enter staff mode and use vanish.
- Helpers must be in staff mode before vanishing.
- Leaving staff mode removes their vanish state.
- Helpers can see vanished Helpers under the current default matrix.
- Higher ranks see Helpers according to the matrix above.

The final live behavior depends on the Helper branch being merged and deployed.

## Performance and threading

Database reads and writes run on the bounded worker executor. Bukkit visibility
calls are scheduled back through Paper's global-region scheduler.

`refreshAll()` is O(n²) because it evaluates every online viewer-target pair. This
is simple and correct for small staff populations, but reviewers should watch for:

- repeated full refreshes during join bursts or rapid toggles;
- Folia/region-thread ownership requirements for player visibility calls;
- integrations triggering additional full scans;
- stale viewer ranks after permission changes without reconnect or refresh;
- the 10,000-record startup bound and whether old records are cleaned correctly.

Any optimization must preserve rank changes, self-visibility, newly joined players,
and removal of stale hidden state.

## Failure behavior

- Missing storage prevents a new toggle from being applied.
- Database exceptions leave the previous in-memory decision unchanged.
- A full worker queue skips the requested asynchronous operation and logs a warning.
- The current implementation does not automatically prove that every external
  visibility provider is healthy.
- A persisted vanish record can be restored after restart, but complete visual and
  integration coverage still requires staging.

## Review and staging checklist

Reviewers should verify:

- rank resolution and permission changes;
- staff-mode requirement for each role;
- persisted enable, disable, restart, reconnect, and staff-mode exit;
- join and quit message suppression;
- viewer hierarchy in every pairwise combination;
- normal players never seeing vanished staff;
- self-visibility;
- tab list and entity visibility on each supported Paper version;
- RoseChat, voice, `/seen`, commands, player counts, and public APIs;
- sounds, particles, containers, damage, pickup, and other observable effects;
- Java and Bedrock clients;
- reload and plugin-disable behavior;
- multiple backend servers and server switching;
- performance with realistic player counts;
- whether any direct packet claim is backed by actual packet code and tests.

Related staff instructions are in
[[Staff Mode, Vanish, and Freeze|Staff-Mode-Vanish-and-Freeze]].