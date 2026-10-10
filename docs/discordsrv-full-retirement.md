# DiscordSRV full retirement: evidence and release gates

**Status: BLOCKED / PREPARATION ONLY (2026-10-08).** Umbrella: #264; chat: #268;
public-chat identity: #421; existing draft stack: #422, #465, #466.

This document distinguishes **chat cutover** from **removing DiscordSRV from
all Paper servers**. Neither passing tests nor preparing a SHADOW configuration
authorizes production deployment, restarts, role mutations, or deletion.

## Dependency and acceptance matrix

| Consumer / capability | Current evidence | Gate before physical removal |
| --- | --- | --- |
| Existing DiscordSRV chat | Owner confirmed operational on 2026-10-08. Keep it as rollback. | Replacement SHADOW and then AUTHORITATIVE tested in both directions, one delivery per event, route isolation and automatic readiness loss. |
| Enthusia SMP public-chat JDA identity | Draft stack #422 → #465 → #466, local private outbound-only SHADOW candidate prepared; not deployed. | Prove StaffBot-container DNS/TCP/TLS/HMAC, correct Discord identity/guild/channel, actual outbound and inbound tests with approved deployments. |
| Canonical account links | EnthusiaStaff ES-D04 owns canonical links; DiscordSRV compatibility and mirror paths remain. | Real linked/unlinked/relinked/multiple-account tests with DiscordSRV absent; verify all consumer link queries use canonical authority, and legacy mirror can be safely disabled without data loss. |
| Staff rank → Discord role sync (ES-D13) | Historical PR #178 closed because merged PR #330 superseded it; implementation and tests exist on `main`, but no live parity/cutover is proven. | Configure a trusted SHADOW against the protected real legacy mapping, prove zero unexplained drift, then independently approve enforcement. Discord roles never grant staff authority. |
| Shared managed-role service | Work tracked in closed #341; provider-neutral API and Paper implementation exist. | Demonstrate runtime StaffBot writer/claim persistence and reconciliation under fail/retry/restart and multi-linked accounts; no accidental role deletion. |
| LumaGuilds Discord guild roles | PR #6 merged into an *integration branch* for shadow publication; this is not production retirement. | Reconcile current deployed Luma artifact, migrate remaining DiscordSRV link + JDA role writers, shadow-compare, enable owned role mutations, verify guild lifecycle and safe rollback. |
| PlayTime numeral roles | PR #30 merged provider-neutral seam; #27 remains open; default branch still has `DiscordSrvNumeralRoleProvider` and explicit legacy preference when installed. | Agree authoritative branch/artifact; remove live DiscordSRV writer and direct imports for final consumer; prove all tier/alt/link/unlink/restart and highest-role parity. |
| Discord console | Replacement work tracked in closed #267; legacy DiscordSRV console/command forwarding is enabled in the inspected live config. | Prove audited, authorized, allowlisted bridge in staging and approve new production console command ownership; verify no legacy command behavior disappears. |
| InteractiveChat rich artifacts | Existing `InteractiveChatStagingArtifactProvider` reflects into the hard-dependent Discord addon. Draft #475 adds an opt-in independent Java2D slot-card fallback (item names/counts, not sprites). It is built and unit-tested but NOT production-tested or pixel-fidelity accepted. Dedicated work: #474. | Verify independent renderer and explicit visual acceptance in a real isolated environment with **both DiscordSRV and addon absent**. Do not confuse compiled fallback with feature parity. |
| RoseChat / remaining plugins | Provider-neutral outbound/inbound bridge exists; other plugin hooks and runtime configuration may still depend on DiscordSRV. | Scan all live server/plugin manifests and code/config; verify mute/AI moderation, private/staff isolation, reconnect, mentions, and no duplicated messages. |

**Closed GitHub issues represent implementation checkpoints, not proof of live
cutover.** Likewise, a merged integration branch is not proof that the production
JAR contains the feature. Record exact deployed artifact hashes and versions.

## Automated manifest inventory

A Java 21, read-only scanner lives at
[`tools/discordsrv-retirement-preflight/`](../tools/discordsrv-retirement-preflight/README.md).
Run it against a complete **authorized staging copy** of every Paper server's
`plugins` folder. Record `HARD_DEPENDENCY`, `SOFT_DEPENDENCY`, `REFERENCE`,
`BYTECODE_REFERENCE`, and `UNVERIFIED` findings. All findings remain fail-closed; even `SOFT_DEPENDENCY`
is **not** proof that removing the plugin preserves its feature behavior.
There is deliberately no deletion mode.

**Full SMP static JAR inventory, October 9, 2026:** the owner ran the
bounded read-only SFTP snapshot of all **103** live SMP plugin JARs into a
private local directory. The upgraded analyzer completed on the downloaded
copies, with **0 unverifiable JARs** and **0 bytecode-only additional plugins**.
It reported one installed DiscordSRV JAR, **one hard dependency**, and **nine
soft dependencies**. Every one of the ten dependent plugin JARs also contained
the `DiscordSRV` literal in compiled class bytes; bytecode matches **overlap**
the manifest classifications and are not ten more plugins. No server files,
private configurations, processes, or Discord roles were changed.

| Manifest classification | Actual SMP plugin JAR | Retirement work to verify |
| --- | --- | --- |
| HARD | InteractiveChatDiscordSrvAddon 2026.1.1.0 | Must be removed/replaced on a separately tested DiscordSRV-free staging Paper build; compare native renderer to current graphical behavior. |
| SOFT | EnthusiaAdvancements pilot.6 | Audit advancement-to-Discord side effects and whether they must be migrated to provider-neutral transport. |
| SOFT | EnthusiaPlaytime 3.7.2 | Eliminate legacy numeral-role writes and DiscordSRV account lookup; exercise link, alt and only-highest-role parity. |
| SOFT | EnthusiaStaff AuthorityBridge | Verify account-link compatibility/transition importer can be retired without losing canonical links. |
| SOFT | EnthusiaStaff Paper | Remove remaining optional legacy hooks only after replacement services and readiness handling are accepted. |
| SOFT | InteractiveChat 2026.1.1.0 | Verify native Minecraft placeholder/chat behavior when DiscordSRV and addon are absent. |
| SOFT | LumaGuilds 3.0.23 | Audit link and guild-role mutation dependency; keep live LumaGuilds unchanged until separately authorized adapter and parity are ready. |
| SOFT | OreAnnouncer 2.8.5 | Inspect what Discord notifications, if any, depend on DiscordSRV; preserve intended behavior or explicitly approve its retirement. |
| SOFT | Plan 5.8 | Inspect its DiscordSRV extension/hook; confirm the analytics behavior without legacy plugin. |
| SOFT | RoseChat RC-4 | Preserve mute/moderation and chat source behavior; verify legacy suppression/fallback and incoming routing. |

**Static inventory result: BLOCKED.** All 103 SMP JAR copies were inspected,
but this is **not** a complete network inventory, nor a functional test. Check
every other Minecraft Paper server separately, and verify each item above under
a live, isolated staging runtime. Do not infer compatibility solely from a
soft dependency or a compiled symbol. The 2026-10-09 scan supersedes the
earlier four-JAR sample, which was not exhaustive.

The scanner also checks bounded compiled class bytes for `DiscordSRV`
(case-insensitive). Matches can be harmless constants; misses can occur through
obfuscation, dynamic loading or configuration-only integration. Search each
Enthusia repository, deployment inventory and running configuration for
DiscordSRV hooks and the old addon, then inspect actual behavior.

## Gates (every item required, with proof)

1. **Inventory:** trusted full plugin artifact/JAR list on SMP and every other
   affected Paper server; manifest scan results; current running versions/hashes;
   owner of every remaining DiscordSRV integration.
2. **Configuration isolation:** no private tokens or passwords in GitHub,
   logs, chat, CLI arguments, or screenshots. Keep the canonical link database,
   preserved legacy mappings, role IDs, active channel IDs, permissions and
   current command allowlists in restricted owner-local storage only.
3. **Static readiness:** reviewed exact-head JARs for Paper, Velocity, StaffBot
   and affected consumers; tests/CI/Codacy green; all required replacements
   tested even with the DiscordSRV JAR/addon physically missing in staging.
4. **Network proof:** actual StaffBot container must resolve and reach Velocity
   and validate TLS hostname; after explicit deployment approval, prove mutual
   HMAC/peer authorization, reconnect, wrong-peer rejection and no durable chat
   replay. A Windows-origin TLS handshake alone does not pass this gate.
5. **Private SHADOW:** existing DiscordSRV stays live. First send only
   `SMP/global` to the pinned private Discord test channel, no ingress. Test
   duplicate suppression, attachment/rich output, failure fallback, rate limits,
   privacy, logging and production moderation isolation.
6. **Two-way staging:** enable separately authorized private inbound routing
   for linked/unlinked users, mute enforcement, server and channel allowlists,
   bot/webhook loop suppression, and all InteractiveChat formats.
7. **Non-chat parity:** prove managed-role snapshots and effective roles for
   D13, LumaGuilds, and PlayTime with multi-linked accounts, stale/unlinked
   identities, restart, rate limiting and ambiguous Discord responses. Prove
   account links and the secure console bridge independently.
8. **Production chat cutover:** separate owner approval and rollback image;
   enable `AUTHORITATIVE` with explicit acknowledgements. Verify exactly one
   message each way, readiness leases, legacy suppression and recovery.
9. **DiscordSRV-free staging:** test **entire** plugin suite and high-value
   game flows with neither DiscordSRV nor the addon installed. This includes
   startup, dependency resolution, account link/login, staff ranks, guilds,
   playtime, console, moderation and rich chat.
10. **Final physical uninstall:** only after written owner approval, a scheduled
    maintenance window, verified backups and an explicit restoration plan.
    Remove/disable the JAR in the authorized change; retain secured configuration
    and database backups until rollback period ends. Verify services after
    restart; do not delete user link or role records as cleanup.

A single missing proof is a **NO-GO**. Passing the scanner alone is never GO.

## Rollback criteria and retained material

Do not proceed if the public chat identity, authenticated channel, rich renderer,
account link queries, console, role writer, guild writer, or PlayTime writer has
unexplained drift. Preserve exact previous StaffBot/Velocity/Paper and consumer
JARs, startup parameters, channel secrets, DiscordSRV config, link database,
managed-role state and any required old plugin JARs, in private storage.

For chat rollback, follow
[`discord-chat-cutover.md`](discord-chat-cutover.md). For physical plugin
removal rollback, restore known-good JAR/config **and required dependent addon**
as a unit with controlled service restarts, then retest links, chat and roles.
Never roll back the database blindly over newer player/link changes.

## Minimum evidence record per gate

Store public-safe metadata only: gate ID, exact commit/artifact SHA-256,
environment, operator, test time, pass/fail, route names (not secret IDs),
observed failures, rollback outcome and a non-sensitive pointer to restricted
evidence. Do not mark GO from a comment, a code merge, or a test performed in
the wrong network namespace.
