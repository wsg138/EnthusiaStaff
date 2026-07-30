from pathlib import Path

PATH = Path("ENTHUSIASTAFF-GOALS.md")


def replace_once(text: str, old: str, new: str) -> str:
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"Expected one exact match, found {count}: {old[:80]!r}")
    return text.replace(old, new, 1)


def replace_section(text: str, start_header: str, next_header: str, replacement: str) -> str:
    if text.count(start_header) != 1 or text.count(next_header) != 1:
        raise SystemExit(f"Section boundary is ambiguous: {start_header!r} -> {next_header!r}")
    start = text.index(start_header)
    end = text.index(next_header, start)
    return text[:start] + replacement.rstrip() + "\n\n" + text[end:]


text = PATH.read_text(encoding="utf-8")
text = replace_once(
    text,
    "Later corrections in this file override older assumptions. Most importantly, **Developer is a development role with no punishment creation or modification authority**.",
    "Later corrections in this file override older assumptions. Most importantly, **Helper is a tightly restricted trial moderation rank below Mod, and Developer is a separate development role that may prepare requests but has no direct punishment or approval authority**.",
)

text = replace_section(
    text,
    "## 17. Rank authority",
    "## 18. Removal and overturns",
    """## 17. Rank authority

Authorization is enforced in authoritative services, not only GUI/commands. Feature permissions do not silently become rank identity, and Developer is never treated as Mod or higher.

### Helper

Helper is the trial period before Mod and must remain tightly restricted.

May investigate reports, freeze when authorized, use staff chat, use restricted staff mode and vanish, inspect player information, view inventories and Ender chests read-only, and apply configured temporary punishment steps.

A configured result containing any permanent sanction must become a durable approval request rather than applying immediately. Helper cannot approve requests, raise/lower recommendations, use custom durations or combinations, edit or confiscate items/economy, remove or overturn punishments, persist client evidence, use owner/recovery controls, receive creative, give items, or move/take/receive items through staff tools, inventories, or Ender chests.

### Mod

May apply configured steps, including configured permanent steps; approve or deny Helper/Developer punishment requests; lower recommended punishment; end/revoke while preserving history; remove escalation contribution when configured; request full overturn.

Cannot raise above recommendation, create arbitrary sanctions/combinations, or directly fully overturn.

### Developer

Developer is development staff, not moderation staff and not part of the Mod-or-higher approval hierarchy.

May retain read-only cases/history/reports/diagnostics and non-punishment staff/development tools. Developer may prepare and submit a punishment request for authorized Mod/Admin/Founder review, but the request itself changes no punishment.

May not directly create, confirm, apply, approve, raise, lower, end, revoke, remove, unban, unmute, remove warnings, change punishment visibility, request overturn, approve/deny overturn, or otherwise mutate punishments through commands, GUIs, APIs, website, or integrations.

Historical Developer-issued cases remain valid and preserve original actor/rank.

### Admin

May apply configured punishments, raise/lower, use custom durations with configured types, fully overturn, approve/deny punishment and overturn requests, and reopen appeals as configured.

### Founder/Owner

May use unrestricted configured/custom combinations, fully overturn, approve/deny, use recovery tools, and perform emergency cutover controls.

---""",
)

text = replace_section(
    text,
    "## 24. Staff mode",
    "## 25. Vanish",
    """## 24. Staff mode

`/staff`.

Entry: CombatLogX check, durable full snapshot, verify commit, clear normal state, apply rank-specific staff state, mark active.

Snapshot inventory, armor, offhand, XP, health, hunger, saturation, effects, location, server, game mode, flight, metadata, checksum, revision.

Exit removes all staff items, restores exact state/location/server where safe, verifies, closes session.

Crash/reconnect resumes until normal exit, preserves original snapshot, resumes vanish, and prevents staff-item leakage.

Staff-mode/vanished players cannot be combat tagged or tag others.

Rank profiles:

- Helper: spectator only; no creative; no item pickup/drop/swap/movement; no inventory or Ender mutation; no giving, receiving, taking, or moving player items; no advanced cheat/client-evidence or recovery tools.
- Mod: no creative; Ender unavailable; only configured moderation tools.
- Developer: no creative; Ender unavailable; technical tools remain available but no direct punishment authority.
- Admin: creative allowed; Ender view-only unless separately authorized by a destructive workflow.
- Founder: creative and normal configured owner access.

An active staff session must reject or immediately correct any game-mode or inventory transition that exceeds the rank profile, even when another permission plugin accidentally grants the underlying vanilla command.

---""",
)

text = replace_section(
    text,
    "## 25. Vanish",
    "## 26. Freeze",
    """## 25. Vanish

Separate from staff mode.

Helper/Mod/Developer require staff mode. Admin/Founder may vanish independently.

Default visibility: Helper sees Helper; Mod/Developer see Helper/Mod/Developer; Admin sees Helper/Mod/Developer/Admin but not Founder; Founder sees all. Configurable, but supervising ranks must never lose visibility of vanished Helpers during configuration migration.

Central `StaffVisibilityService` must cover tab, join/quit, player counts, `/seen`, teleport/message/pay completion/notifications, playtime, RoseChat, voice, sounds, particles, chest animations, entity tracking, and public APIs. Bukkit `hidePlayer` is only one layer.

Spectator/tab requirements:

- Developer, Admin, and Founder/Owner entering spectator are immediately removed from every viewer's tab list.
- They receive a clickable chat choice to enter full vanish or appear normally on tab while remaining actually in spectator.
- A staff member who appears on tab while actually spectating must be packet-presented as a normal non-spectator entry, using a normal game-mode value and preserving profile, display name, latency, hat, list order, and chat-session data.
- No listed staff entry may expose spectator game mode, gray spectator styling, spectator sorting, or other player-info metadata that reveals spectator state.
- Helper and Mod staff-mode spectator entries must also be masked when listed; no actual spectator entry may be exposed on tab.
- ProtocolLib player-info filtering must remove unauthorized vanished entries and mask listed spectator entries. If ProtocolLib is absent, incompatible, or the packet adapter fails, spectator staff remain unlisted and the appear-normally option is disabled fail-closed.
- Leaving spectator restores normal tab handling unless full vanish remains active. Disabling full vanish while still spectating returns the player to the hidden choice state.

Full vanish must suppress unauthorized player-info, entity spawn/tracking, metadata, equipment, join/quit, and integration exposure so ordinary clients and spectator-detection mods cannot determine that staff are watching. Authorized staff visibility remains rank-aware.

Visibility updates should be incremental per changed viewer/target pair; full O(N²) reconciliation is startup/recovery fallback only.

---""",
)

text = replace_once(
    text,
    "Tests: escalation/history/ladder/decay/permanent/combined/reduction/revocation/overturn, all rank boundaries including Developer denial, alt inheritance/exceptions/restart suppression, online/offline inventory races/atomic writes/queued patches, confiscation/economy recovery, staff crash/Ender restrictions/freeze reconnect, Discord circuit breaker, LiteBans dry run/idempotency/shadow mismatch/cutover, website auth/code/appeal/media/sanitization, duplicate/partial/database/network failures.",
    "Tests: escalation/history/ladder/decay/permanent/combined/reduction/revocation/overturn, all rank boundaries including restricted Helper and Developer request/direct-issue/approval denial, spectator tab masking and ProtocolLib fail-closed behavior, alt inheritance/exceptions/restart suppression, online/offline inventory races/atomic writes/queued patches, confiscation/economy recovery, staff crash/Ender restrictions/freeze reconnect, Discord circuit breaker, LiteBans dry run/idempotency/shadow mismatch/cutover, website auth/code/appeal/media/sanitization, duplicate/partial/database/network failures.",
)
text = replace_once(
    text,
    "11. Mod/Developer vanish staff-mode requirement and visibility hierarchy.",
    "11. Helper/Mod/Developer vanish staff-mode requirement, rank visibility hierarchy, senior-staff spectator auto-unlisting, clickable vanish/normal-tab choice, creative tab masking, and fail-closed behavior when packet masking is unavailable.",
)

PATH.write_text(text, encoding="utf-8")
