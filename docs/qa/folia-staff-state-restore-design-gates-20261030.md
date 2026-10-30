# Folia staff-state restoration: design and safety gates

**Status: DESIGN ONLY / NO-GO for Folia.** No runtime implementation, plugin opt-in, policy migration, production configuration, or deployment is authorized by this document.

## What the present code guarantees on Paper

`StaffModeManager.onQuit(PlayerQuitEvent)` currently restores the original backend-local player snapshot **before** native player data is saved. It then submits a durable session-detach update. `StaffStateCodec.restore` first calls synchronous `player.teleport(decoded.location())`, then restores inventory, XP, hunger, effects, game mode, flight, health and other player properties. A failed teleport returns false without proceeding with the rest of the snapshot.

This sequence is important: writing the temporary Staff Mode inventory/location to the native player file can persist the wrong state after a disconnect or crash. Existing SHA-256 checks, ownership fences and durable recovery gates must remain intact.

## Why changing `teleport` to `teleportAsync` is insufficient

Folia supports asynchronous region/cross-world teleportation; its entity owner may move during teleport and the completion time is not guaranteed. Merely continuing inventory restoration in the original quit callback would race teleport and player saving. The Folia maintainer explicitly states: **teleporting during a quit event can crash**. This means that even an async teleport started during `PlayerQuitEvent` is not an acceptable quick fix.

Sources:
- [Folia overview — entity teleportation and region ownership](https://docs.papermc.io/folia/reference/overview/)
- [Folia maintainers, issue #219 — teleport during quit causes a crash](https://github.com/PaperMC/Folia/issues/219)
- [Paper/Folia scheduler contract](https://docs.papermc.io/paper/dev/folia-support/)

## Required design properties before any Folia opt-in

1. **Deny unsafe activation** until a complete Folia lifecycle exists. `folia-supported` must remain absent/false. Paper-only releases can be assessed separately without misrepresenting Folia readiness.
2. **Pre-disconnect voluntary exit:** Where the plugin controls a switch or explicit Staff Mode exit, finish a durable, verified restore *before* the connection transfer/exit. Do not start a teleport inside a quit callback.
3. **Abrupt disconnect and crash recovery:** The plugin cannot pre-detect a lost connection. Keep the authoritative original snapshot and durable lease. On the next suitable login, restrict interactions while recovery is pending, then perform any async teleport and player-state mutations on the entity's current owner, verify the full recovered state, and complete durable reconciliation. **Do not consider a restored location sufficient if inventory/XP/mode are still temporary.**
4. **No stale entity work:** Every async continuation needs target UUID, snapshot session/revision, current backend ID and cancellation/ownership checks. If a player quits, switches server or a new session supersedes the old one, callbacks must not change another session or player.
5. **Recovery after incomplete operations:** Failure, timeout, chunk load error, executor rejection, server restart and repeated disconnect must leave a recoverable durable snapshot. Do not clear recovery on an unverified restore or write the original snapshot into a mismatched server.
6. **Protected state during recovery:** No temporary Staff Mode tools, flight, inventory contents or location should be usable for gameplay during a pending native-state restoration. Define a strict no-interaction gate, including cross-server reconnect and permissions change.
7. **Third-party compatibility:** RoseChat's new global scheduling path does not by itself establish RoseChat Folia support. All dependent plugins and the broader plugin threading inventory must be validated independently.

## Tests required on an isolated Folia server

| Scenario | Mandatory proof |
| --- | --- |
| Graceful Staff Mode exit | Snapshot restored completely and verified before session lease finalization |
| Abrupt network disconnect during Staff Mode | No quit-time teleport, no irreversible state loss, original snapshot remains durable |
| Rejoin in a different region/world | Async owner-safe teleport completes, full inventory and state restored, checksum confirmed |
| Disconnect during pending async teleport | Original snapshot remains durable; stale callback performs no harmful mutation |
| Server restart during restore | Recovery retries safely without giving duplicate items or dropping original state |
| Velocity server switch and rapid reconnect | Backend/session fences block an old callback after transfer |
| Storage outage or worker rejection | No successful-exit claim, and recovery remains pending |
| Permission/rank revoked during recovery | Protected native state is still restored without elevating staff access |

The existing Java/Bedrock 20-row GUI acceptance matrix is a separate obligation. The Folia scanner is a **heuristic**, not a full proof of thread safety.

## Current change

The accompanying static audit now detects `player.teleport()` and `player.teleportAsync()` **within `StaffModeManager.onQuit`**, even if a developer removes the `restoreSavedState` call. Regression fixtures ensure that directly teleporting during quit cannot falsely clear the scanner. This hardens the NO-GO boundary; it is not the lifecycle redesign.

**Implementation next step:** A dedicated owner of the Staff Mode lifecycle should propose and prove the pre-exit/rejoin recovery state machine before any production code changes. Do not turn on `folia-supported` for testing without an isolated, expendable environment and explicit authorization.
