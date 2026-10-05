# Vanish Internals

This page describes what the current vanish implementation actually does. It
separates implemented behavior from broader goals so reviewers do not assume that
one `hidePlayer` call covers every plugin, packet, command, and visual effect.

## Quick navigation

- [[Main files|Vanish-Internals#main-files]]
- [[State and startup|Vanish-Internals#state-and-startup]]
- [[Toggle flow|Vanish-Internals#toggle-flow]]
- [[Live rank reconciliation|Vanish-Internals#live-rank-reconciliation]]
- [[Events handled directly|Vanish-Internals#events-handled-directly]]
- [[Visibility decisions|Vanish-Internals#visibility-decisions]]
- [[Packets and Paper visibility|Vanish-Internals#packets-and-paper-visibility]]
- [[What is not currently intercepted|Vanish-Internals#what-is-not-currently-intercepted]]
- [[Performance and threading|Vanish-Internals#performance-and-threading]]
- [[Review and staging checklist|Vanish-Internals#review-and-staging-checklist]]

## Main files

```text
paper/src/main/java/net/enthusia/staff/paper/visibility/VanishManager.java
paper/src/main/java/net/enthusia/staff/paper/visibility/VanishAudienceCoordinator.java
paper/src/main/java/net/enthusia/staff/paper/visibility/VanishRankReconciliationPolicy.java
paper/src/main/java/net/enthusia/staff/paper/visibility/DefaultStaffVisibilityService.java
paper/src/main/java/net/enthusia/staff/paper/visibility/ProtocolLibSpectatorTabPacketAdapter.java
paper/src/main/java/net/enthusia/staff/paper/api/StaffVisibilityService.java
persistence/src/main/java/net/enthusia/staff/persistence/JdbcVanishStore.java
paper/src/main/java/net/enthusia/staff/paper/staff/StaffModeManager.java
paper/src/main/java/net/enthusia/staff/paper/auth/PaperStaffRankResolver.java
```

`VanishManager` owns lifecycle, persistence coordination, live rank
reconciliation, and Bukkit application. `DefaultStaffVisibilityService` owns the
current in-memory visibility decision. `JdbcVanishStore` persists whether a staff
member should remain vanished and the rank used for visibility hierarchy.

## State and startup

During initialization, `VanishManager` performs database work on the bounded
worker executor. It loads up to 10,000 active vanish records and copies their UUID
and recorded rank into the in-memory visibility service and durable-rank cache.

It then uses the global-region scheduler only to discover the current online
players. Each player is handed to that player's entity scheduler before the
manager reads live permissions or game mode and registers the player with the
audience coordinator. Incremental viewer and target refreshes eventually rebuild
every online relationship without mutating a player from another region.

Each audience registration receives a monotonically increasing session identifier.
Queued owner callbacks verify that identifier before running, so a disconnect and
reconnect cannot apply work through the retired `Player` handle.

Startup staff-mode recovery is asynchronous. Helper, Mod, and Developer vanish is
therefore not disabled merely because the in-memory staff-mode cache is not ready.
When required, vanish checks the durable `StaffSessionStore` on the bounded worker
executor. An open durable staff session preserves the vanish state while recovery
continues; a confirmed missing session disables it.

## Toggle flow

`/vanish` reaches `VanishManager.toggle(Player)`.

The current flow is:

1. Resolve the player's explicit EnthusiaStaff rank from permissions.
2. Reject the request when no supported rank can be resolved.
3. Require active staff mode for Helper, Mod, and Developer.
4. Calculate the opposite of the current vanish state.
5. Permit only one state write for that player.
6. Persist the new state asynchronously in `VanishStore`.
7. When staff mode is active, update the staff-session vanish mirror too.
8. Update the concurrent viewer-rank and vanished-state maps.
9. Return to the current player's entity scheduler through the session-fenced
   audience coordinator.
10. Refresh that player as a target and refresh them as a viewer if their rank
    changed.
11. Send the enabled or disabled confirmation on the owning thread.

The visible state is changed only after ordinary toggle persistence succeeds. A
storage failure leaves the existing visibility decision in place and reports an
error rather than pretending the toggle succeeded.

## Live rank reconciliation

One plugin-owned global task runs every second. It uses tiered selection:

- known online staff, active staff-mode players, persisted vanished players, and
  pending exit cleanup remain eligible every second;
- every fifth pass includes the complete online audience so newly promoted players
  are discovered within five seconds;
- ordinary untracked players are skipped between full-discovery passes.

This avoids five permission checks per second for every ordinary online player
without making promotion detection depend on reconnecting. Each selected UUID may
have only one queued entity check. The entity check:

1. resolves the live explicit EnthusiaStaff rank;
2. compares it with the cached viewer rank;
3. updates or removes viewer authority immediately;
4. refreshes only that viewer's current relationships;
5. compares the vanished target's live rank with the durable rank;
6. updates target classification and persists a changed rank;
7. disables vanish after rank removal or invalid `SYSTEM` resolution;
8. verifies lower-rank staff-session authority durably when in-memory recovery is
   not yet established.

Durable staff-session checks are bounded to one in-flight check per player. Each
check has a unique token; disconnect invalidates that token, so an old result
cannot mutate a replacement session. Failed checks back off before retrying.

Helper, Mod, and Developer normally leave vanish when staff mode exits. If that
cleanup collides with another state write or fails, a pending cleanup marker keeps
the disable operation eligible for retry. A later restart also detects the closed
staff session through durable verification, covering the crash window between
staff-session closure and vanish disable. Transient cleanup markers are removed on
quit; reconnect re-establishes authority from durable storage.

Viewer authority and target classification are separate:

- a viewer promotion or demotion changes what that viewer may see;
- a vanished target promotion or demotion changes which viewers may see that
  target;
- both are refreshed incrementally rather than through a full online matrix scan.

## Staff-mode exit

`StaffModeManager` calls `VanishManager.staffModeExited(UUID)` only after verified
durable staff-session closure. If the current live rank requires staff mode,
vanish is disabled through the normal persisted path. Admin and Founder may remain
vanished independently.

## Events handled directly

### `PlayerJoinEvent`

Registered at `EventPriority.HIGHEST`.

- Registers the current player handle and game mode with a new session.
- Records the joining player's live viewer rank.
- Reconciles persisted target rank and lower-rank session authority.
- Suppresses the join message when the player remains vanished.
- Refreshes the joining player as both viewer and target.

### `PlayerQuitEvent`

Registered at `EventPriority.HIGHEST`.

- Suppresses the quit message before retiring runtime state when the player is
  vanished.
- Removes that audience session, viewer rank, spectator-tab state, queued rank
  marker, pending exit marker, and transient session-verification caches.
- Does not clear durable vanish state.

### `PlayerGameModeChangeEvent`

Registered at `EventPriority.HIGHEST` with cancelled changes ignored.

- Admin and Founder keep any real authoritative Bukkit/Paper game mode they select
  while vanished, including Survival, Creative, Adventure, or Spectator. Vanish
  does not rewrite that mode.
- Helper, Mod, and Developer remain restricted by Staff Mode policy to protected
  Survival or real Spectator; vanish does not add a second game-mode restriction.
- The bounded Staff Mode snapshot-restoration phase preserves the exact pre-Staff
  mode while visibility state is reconciled independently.
- Re-resolves live viewer rank, re-evaluates spectator-tab policy, updates the
  coordinator's cached game mode, and refreshes affected audience state.

When the real mode is Spectator, vanish uses normal vanilla/Paper spectator
phasing; it does not use `noPhysics`, NMS/reflection, or a client-only fake game
mode. Admin/Founder Survival, Creative, and Adventure remain those exact real
modes while vanished. The selected real mode is persisted with durable vanish
state. The
manager does not directly listen for
chat, command completion, sound, particle, inventory, pickup, advancement,
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

A non-staff viewer has no viewer entry and therefore cannot see a vanished target.

### Current default matrix

| Viewer | Vanished ranks visible to that viewer |
| --- | --- |
| Helper | Helper, Mod, Developer |
| Mod | Helper, Mod, Developer |
| Developer | Helper, Mod, Developer, Admin |
| Admin | Helper, Mod, Developer, Admin |
| Founder | Helper, Mod, Developer, Admin, Founder |

The player can always see themselves. Configuration must define every supported
viewer rank. Supervising ranks cannot lose visibility of vanished Helpers through
a legacy configuration migration.

## Applying visibility

`VanishAudienceCoordinator` owns online player handles, cached game modes, and
session identifiers. Visibility operations execute on the viewer's entity
scheduler:

```text
for each changed viewer/target pair
  canSee(viewer, target)
    -> viewer.showPlayer(plugin, target)
    or viewer.hidePlayer(plugin, target)
```

Normal join, toggle, rank reconciliation, game-mode, and packet-failure paths use
incremental viewer or target refreshes. `refreshAll()` remains available for
explicit startup or recovery fallback.

## Packets and Paper visibility

Entity visibility uses Paper/Bukkit APIs:

```text
Player#hidePlayer(plugin, target)
Player#showPlayer(plugin, target)
```

Paper translates those calls into version-specific client tracking updates. The
exact packet sequence is not an EnthusiaStaff contract.

When ProtocolLib is available, EnthusiaStaff registers one narrowly scoped
`PLAYER_INFO` adapter. It removes unauthorized entries and masks visible staff
spectator entries as creative while preserving remaining player-info fields. A
packet rewrite failure disables the adapter and recalculates spectator tab state
fail-closed on owning entity threads. Without a healthy adapter, affected
spectator staff remain unlisted.

Do not extend that claim to entity-destroy, spawn-player, metadata, equipment, or
other visibility packets. ProtocolLib is limited to observer-facing player-info
masking; EnthusiaStaff does not send a self-only fake game-mode packet. For
Admin/Founder, the authoritative Bukkit/Paper game mode while vanished is the
actual selected Survival, Creative, or Spectator mode.

## What is not currently intercepted

The current vanish manager does not itself guarantee hiding from:

- custom tab-list plugins other than the optional Velocitab integration, or cached player-count displays;
- `/seen`, `/list`, message, teleport, pay, or other command completions;
- RoseChat recipient selection or private-message lookup;
- voice-chat recipient discovery;
- particles, sounds, block changes, or container animations caused by unrelated
  plugins;
- scoreboard teams, boss bars, advancements, death messages, or custom joins;
- external web APIs, Discord messages, analytics, or playtime systems;
- plugins that ignore Bukkit `canSee` and `StaffVisibilityService`.

These require integration adapters or consumers of the public visibility service.
The requirements matrix therefore remains `PARTIAL` for complete visibility
coverage.

## Public visibility service

`StaffVisibilityService` is the stable in-process boundary other plugins should
use instead of reading vanish storage or duplicating rank rules. Consumers should
ask whether a viewer may see a target before listing, completing, messaging,
notifying, or publishing online state.

Adapters must fail conservatively when they cannot obtain a trustworthy decision.

## Performance and threading

Database reads and writes run on the bounded worker executor. Permission reads,
game-mode reads, messages, and viewer visibility/tab mutations run on the relevant
player's entity scheduler.

Bounds include:

- one-second rank checks for tracked staff/vanish state;
- one full online rank-discovery pass every five seconds;
- one queued entity rank check per selected player;
- one vanish state write per player;
- one durable staff-session verification per player;
- retry backoff after failed persistence or session verification;
- reconnect fencing for queued owner callbacks and session-check results.

`refreshAll()` is O(n²), but ordinary changes refresh one viewer or one target.
Reviewers should still watch explicit full refreshes, supported Paper/Folia
ownership behavior, worker saturation, the 10,000-record startup bound, and
provider integrations that trigger additional scans.

## Failure behavior

- Missing vanish storage prevents a new toggle from being applied.
- Database exceptions leave ordinary toggle memory unchanged.
- Rank-removal and unauthorized lower-rank decisions fail safe in memory and retry
  durable correction.
- A full worker queue skips the operation, logs the condition, and leaves retryable
  reconciliation state where applicable.
- Failed durable session verification leaves current visibility unchanged and
  retries after backoff.
- A persisted vanish record restores visibility/privacy state and its selected
  gameplay mode on startup/reconnect.
- For Admin/Founder, vanish-on/off preserves any real selected game mode. For
  Helper/Mod/Developer, it preserves protected Survival or Spectator.
- Spectator phasing is provided only by real Spectator mode; vanish itself does
  not claim noclip in Survival, Creative, or Adventure.
- Complete visual and integration coverage still requires staging.

## Review and staging checklist

Reviewers should verify:

- every viewer and target promotion/demotion combination;
- promotion discovery within the full-scan interval;
- rank removal and `SYSTEM` handling;
- staff-mode exit, collided writes, restart, reconnect, and crash-window cleanup;
- durable staff-session active, absent, exiting, and recovery-required states;
- join and quit message suppression;
- viewer hierarchy in every pairwise combination;
- normal players never seeing vanished staff;
- self-visibility;
- tab list and entity visibility on each supported Paper version;
- Admin/Founder real Survival, Creative, Adventure, and Spectator modes remain
  unchanged when vanish is toggled on or off;
- Helper/Mod/Developer remain limited to protected Survival or Spectator;
- game-mode changes while vanished preserve vanish and Staff Mode state;
- wall, floor, and ceiling phasing while vanished when the real mode is Spectator,
  with no phasing claim in Survival, Creative, or Adventure;
- ProtocolLib present, absent, incompatible, and runtime failure paths;
- RoseChat, voice, `/seen`, commands, player counts, and public APIs;
- sounds, particles, containers, damage, pickup, and other observable effects;
- Java and Bedrock clients;
- reload and plugin-disable behavior;
- queued work racing with disconnect and reconnect;
- Folia/entity-region ownership using a real compatible server build;
- multiple backend servers and server switching;
- performance with realistic player counts.

Related staff instructions are in
[[Staff Mode, Vanish, and Freeze|Staff-Mode-Vanish-and-Freeze]].

## Velocitab integration

Velocity optionally connects to Velocitab's public vanish and custom-name APIs.
It reads durable vanish and active staff-session state on a worker once per
second; packet visibility callbacks use only the verified immutable cache and
current permissions. Staff-mode and vanish add `[STAFF]` and `[V]` markers without
changing LuckPerms rank prefixes or permissions. Existing custom names are
preserved and restored only while the integration still owns the value.

The proxy uses the default rank matrix above. Keep the backend
`visibility.matrix` configuration aligned with it. Helper/Mod/Developer see each
other, Developer additionally sees Admin, Admin sees Admin and lower ranks, and
Founder sees every staff rank. Ordinary players see no vanished staff. Existing
Velocitab server-group boundaries still apply.

If storage cannot verify presence for five seconds, proxy tab visibility fails
closed until verification recovers. Other players' entries may temporarily
disappear during that outage. Recovery refreshes both visibility and names.
The Paper packet hook relists authorized vanished staff even when spectator
entries were originally unlisted; unrelated spectator tab choices remain intact.

Restart Paper and Velocity to apply this integration and visibility configuration.
Use the network restart queue and its player warnings for production rollout.
