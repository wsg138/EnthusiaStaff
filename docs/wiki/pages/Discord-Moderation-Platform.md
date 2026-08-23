# Discord Moderation Platform

> **Status: Planned.** The current merged product has durable Discord webhook delivery, but the interactive moderation bot, account-link replacement, Discord AutoMod, Discord punishment enforcement, and public information bot described here are not implemented yet.

For the full technical/product contract, see [`docs/discord-moderation-platform.md`](../../../docs/discord-moderation-platform.md). For existing webhook delivery, see [[Discord Delivery]].

## What is being added

EnthusiaStaff is planned to become the authority for Discord moderation while keeping Discord and Minecraft as separate enforcement scopes. Staff actions started on Discord default to Discord; Minecraft actions default to Minecraft; applying to the other platform or both is always an explicit choice.

The design adds:

- a separate Java 21 staff Discord bot restricted to the Enthusia Discord;
- Discord warnings, managed-role mutes including permanent mute, kicks, temporary/permanent native bans, and temporary/permanent channel/category restrictions;
- clean `/moderate` panels, user/message context commands and quick punishment commands;
- linked Discord/Minecraft identity with multiple Minecraft accounts per Discord account and historical-link audit;
- DiscordSRV link migration and eventual replacement of DiscordSRV role sync;
- Discord message evidence, cases, notes and linked-alt/evasion alerts;
- custom AutoMod with local rules plus OpenAI Moderation API context signals;
- compromised-account Security Locks that are safety states rather than punishments;
- import/reconciliation of existing Discord bans;
- a separate installable public information bot;
- website-only formal appeals and expanded staff website review/configuration.

## Linking

Players may begin linking on either platform. A five-minute one-use code generated on one side is completed on the other. One Discord account may own multiple current Minecraft links; one Minecraft UUID may have only one current Discord account.

All current Minecraft accounts linked to the same Discord identity are visible to authorized staff as known alts. Unlinking removes the current link but does not erase staff-visible historical relationships.

The first linked Minecraft account starts as the main account. Automatic main-account selection uses PlayTimePlugin active playtime and changes only when another linked account has at least 25% more active playtime. Authorized staff can set/lock the main account manually.

Linked-account details are private. The public bot never reveals another player's Discord link, alts or historical links.

## Staff authority

Discord command visibility is not permission authority. A staff member must link their Discord account to their Enthusia staff identity, and every mutation is reauthorized through EnthusiaStaff domain policy.

Planned Discord authority differs deliberately from Minecraft in one place: **Developer has the same Discord moderation authority as Mod**, while retaining no automatic Minecraft punishment authority.

- Helper: investigation/read access, warnings and configured short temporary mutes only.
- Mod and Developer: configured temporary Discord punishments and custom temporary durations within policy.
- Admin/Founder: permanent Discord ban, permanent mute and permanent channel restriction plus elevated overturn/custom authority.

Self-punishment and protected hierarchy abuse remain blocked.

## Mutes, restrictions and ticket access

Normal Enthusia Discord mute will not use Discord's native Timeout. A bot-managed enforcement role/permission policy is used so muted users can still communicate in configured private support/ticket areas.

Channel restrictions are first-class sanctions and may be either:

- **Read-only** — visible but not writable/interactable as configured;
- **No access** — hidden/inaccessible.

They may target channels or categories and may be temporary or permanent.

Ticket/support areas are also fully exempt from automatic AutoMod enforcement. Initial exemption identifiers are documented in the technical specification and remain configurable.

## AutoMod

The AutoMod goal is aggressive useful detection without turning borderline matches into automatic punishment.

Local rules handle server-specific behavior such as:

- obfuscated prohibited phrases and word boundaries;
- spam/flood/near-duplicates and split-message evasion;
- message edits;
- Discord invites;
- known malicious links/domains;
- repeated cross-channel link posting;
- letter/reaction phrase construction;
- ticket/staff exemptions.

OpenAI Moderation API is the default AI context signal. It may help classify hate, harassment, threats, self-harm encouragement and similar content, but it does not replace Enthusia policy. Uncertain AI-only results are flagged for staff instead of guessed, and AI alone never causes severe punishment such as a ban.

Ordinary profanity is allowed. Neutral identity language is not blanket-blocked simply because it contains a word that can also be used abusively; context is considered. High-confidence targeted abuse can be removed/actioned according to configured ladders.

Staff are not automatically punished by AutoMod, although prohibited staff messages may still be removed and logged.

## Links and compromised accounts

Normal web links are allowed unless a domain/link is known malicious. External Discord invites in ordinary/general chat are deleted with no warning or punishment; Enthusia's own invites are allowed.

Posting the same/substantially similar link in three different non-exempt channels within 60 seconds is treated as likely account compromise. The system deletes the copies, applies an **Account Security Lock**, DMs the user with account-security/support instructions, and logs the event without pinging all staff.

A Security Lock is not a punishment and does not affect the punishment ladder. Staff remove it with `/unlock` or an alias after the user secures the account.

## Evidence and cases

Message-context moderation captures the offending message, identifiers, attachments, edit history when available, and up to five nearby messages before and after. Staff should not normally need to remember to save obvious evidence manually.

Cases close automatically after 30 days with no meaningful activity. Punishment evidence is retained until 30 days after the punishment ends; non-punishment case evidence is retained until 30 days after the case closes.

## Alt/evasion signals

If a linked account appears to be evading an active punishment, the planned behavior is **alert and investigate**, not automatic punishment or blocking. Online Minecraft staff are notified and the Discord alert pings the all-staff role with the relevant identities, active punishment and triggering event.

## Ban migration

Discord's native guild ban remains the actual mechanism that prevents a banned account from joining. EnthusiaStaff becomes the authority that records why, when and under what policy the ban exists.

Before cutover, every current native Discord ban is imported without unbanning/rebanning anyone. Available audit information is preserved; unavailable legacy fields are marked unknown rather than invented. The imported set is reconciled against Discord before authority cutover.

After cutover, manual native Discord bans/unbans are observed and reconciled so Discord and EnthusiaStaff cannot silently drift apart.

## Appeals

Formal appeals are website-only. Punishment DMs state the affected platform and provide the website appeal path. Discord support remains available for questions/help but is not a second appeal system.

## Public information bot

The public bot is a separate application that may be installed on any Discord server. Planned commands include `/player`, `/whois`, `/guild`, `/baltop`, `/playtime`, `/leaderboards`, `/store`, `/website`, `/discord`, `/rules`, and `/ip`.

It uses sanitized public APIs only. It never exposes linked accounts/alts, historical links, private cases/notes/evidence or privileged staff data.

## Implementation order

The planned worker sequence is documented in [`docs/implementation-plans/discord-moderation-platform.md`](../../../docs/implementation-plans/discord-moderation-platform.md). Identity/scope/persistence/authorization come first, followed by linking and a read-only bot runtime, then Discord punishments, cross-platform integration, evidence, AutoMod shadow/enforcement, website/role sync/public bot, and final migration/cutover acceptance.
