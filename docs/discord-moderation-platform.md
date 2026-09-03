# Discord moderation platform specification

Status: **Planned / approved design; not implemented.**

This document defines the approved Discord moderation, identity-linking, AutoMod, staff-bot, public-bot, appeal, and migration expansion for EnthusiaStaff. It is intentionally separate from the existing webhook-only Discord delivery implementation in `docs/discord-delivery.md`.

## 1. Core authority and topology

- Discord and Minecraft are separate enforcement scopes. A Discord punishment does not silently punish Minecraft and a Minecraft punishment does not silently punish Discord.
- Cross-platform moderation is explicit. A staff action started in Discord defaults to Discord; one started in Minecraft defaults to Minecraft. Staff may deliberately change the scope to Minecraft, Discord, or both before confirmation.
- A cross-platform action creates independently enforceable platform sanctions under the same case/history context. Partial external enforcement must be represented honestly and retried/recovered independently.
- MariaDB and EnthusiaStaff domain services remain the authoritative moderation state. Discord itself is an enforcement surface, not the source of truth for history/policy.
- The existing durable Discord webhook delivery system remains a notification subsystem. It must not be repurposed as the interactive bot backend.
- The staff bot is a third first-class Java 21 runtime/process, isolated from Paper and Velocity so Discord failure/restart does not restart or disable the Minecraft network.
- The staff bot is restricted to the Enthusia Discord guild (`1410303324745371709`). It is not an installable public moderation bot.
- The public information bot is a separate Discord application that may be installed in arbitrary servers. It receives only sanitized public data and no privileged moderation/database credentials. An HTTP interaction endpoint such as Cloudflare Workers is preferred if no Gateway features are required.

## 2. Discord identities and account linking

The moderation model must support Discord-only users who have never joined Minecraft.

Linking cardinality:

- one Discord account may link multiple Minecraft UUIDs;
- one Minecraft UUID may have at most one current Discord account;
- all current Minecraft accounts linked to the same Discord account are treated as known/confirmed alts for staff investigation;
- unlinking never erases historical relationships from authorized staff history.

Normal self-service linking works in either direction:

- Discord `/link` generates a one-use code; the player completes it in Minecraft with `/link <code>`;
- Minecraft `/link` generates a one-use code; the player completes it in Discord with `/link <code>`;
- codes expire after five minutes;
- generating a replacement code invalidates the prior code;
- normal linking requires control of an online Minecraft account;
- unlinking may be initiated from either verified side with confirmation;
- staff have audited, permission-gated force-link/unlink/reassignment tools for recovery.

Aliases such as `/discordlink`, `/dclink`, `/discordunlink`, and equivalent clear forms may exist, but all aliases call the same authoritative service.

Staff moderation authority through Discord requires the staff member's Discord identity to be linked to their Enthusia staff identity. Discord roles may control command discovery/visibility, but roles alone are never authoritative permission.

### Main Minecraft account

- The first linked Minecraft account is initially the main account.
- The system uses PlayTimePlugin's public `PlaytimeService` active-playtime metric rather than total/AFK-prone playtime.
- An automatic main-account change occurs only when another linked account has at least 25% more lifetime active playtime than the current automatic main account.
- Staff may manually set and lock the main account through authorized Minecraft/staff tools. A staff override remains authoritative until removed.
- The main account is presentation/default identity only. It does not make other linked accounts non-alts.

### DiscordSRV migration and role sync

Existing DiscordSRV account-link pairs must be imported without requiring users to relink. The full legacy mapping may be supplied separately at implementation time.

DiscordSRV cannot represent the complete one-Discord-to-many-Minecraft relationship, so EnthusiaStaff becomes the new link authority. During migration the current main Minecraft account may be mirrored into DiscordSRV so existing role sync continues temporarily.

The target state is for the staff bot/platform to replace DiscordSRV role synchronization reliably. Minecraft/Enthusia state is one-way authoritative for synced Discord roles. Eligibility is evaluated across all currently linked Minecraft accounts, while moderation authorization remains a separate EnthusiaStaff domain decision. DiscordSRV may remain installed for console functionality if still useful.

## 3. Staff moderation surfaces

Primary Discord entry points:

- `/moderate <user>`;
- user context command `Moderate User`;
- message context command `Moderate Message`;
- `/moderate-minecraft <player>` for an explicit Minecraft-target workflow from Discord;
- quick commands `/warn`, `/mute`, `/unmute`, `/kick`, `/ban`, `/unban`, `/restrict`, `/unrestrict`;
- `/linked <user>`, `/history <user>`, `/notes <user>`, `/case ...`, `/punishments`, AutoMod status/config surfaces allowed by permission;
- `/unlock <user>` for Account Security Lock removal, with clear aliases such as `/securityunlock`.

Minecraft receives corresponding Discord-target entry points such as `/discordmoderate` plus linking and `/linked` tools. Default members must not receive staff moderation commands.

Target resolution may use Discord mention/name/ID, Minecraft username/UUID, and previously seen Discord names. Ambiguous results must present a selector rather than guess.

### Default moderation panel

The first view stays compact and organized:

- Discord identity/status;
- main Minecraft account and linked-alt count;
- active Discord and Minecraft punishment summaries;
- recent history summary;
- buttons for Punish, History, Linked Accounts, Notes, Cases, and relevant evidence.

Detailed history uses organized filters/tabs rather than a wall of text: All, Discord, Minecraft, Cases, Notes.

### Punishment flow

1. Resolve target and identity.
2. Choose/confirm platform scope: Discord, Minecraft, or both. Default to the platform where moderation began.
3. Choose configured offense/reason family.
4. Show decay-aware recommended platform consequence.
5. Allow authorized changes to type, duration, explanation, channel targets, message deletion, and evidence options.
6. If both platforms are selected, show each platform consequence separately; they need not have the same duration/type.
7. Show final confirmation and reauthorize immediately before commit/enforcement.

Message-context moderation defaults `Delete offending message` on, but staff may turn it off before confirmation. Evidence is captured automatically.

A `Why this punishment?` detail may explain prior relevant offenses, ladder position, decay, and recommendation without cluttering the default panel.

`Repeat last action` may reuse a punishment template for another target, but never reuse the target and never skip confirmation.

If the requested cross-platform identity does not exist, the system must not guess. Staff must explicitly choose the missing platform identity.

## 4. Discord punishment model

Supported Discord punishment/enforcement types:

- warning;
- temporary mute;
- permanent mute;
- kick;
- temporary ban;
- permanent ban;
- channel/category restriction, either read-only or no-access, temporary or permanent.

Discord native Timeout is not used for normal Enthusia moderation because timed-out users must retain ticket/support access. Mutes use a bot-managed enforcement role/permission policy. The role is operational only, visually unobtrusive, and bot-managed. Permission reconciliation must ensure newly created ordinary channels do not accidentally bypass a mute.

Channel/category restrictions are first-class sanctions with duration, reason, history, appeal, audit, early removal, and restart recovery. Category-based restrictions should follow the category as channels change where Discord permission semantics make that reliable.

Discord bans continue to use Discord's native guild-ban mechanism as the actual enforcement side effect. EnthusiaStaff is the policy/history authority.

Configured offense families may be shared between Discord and Minecraft while each platform has its own consequences. Related Discord and Minecraft offenses may contribute to the same family escalation where policy says they should. Use the existing EnthusiaStaff decay semantics; unrelated offense families generally do not escalate each other.

Direct/quick commands and panels always call the same application/domain services.

### Rank policy for Discord

This is an explicit Discord-only exception to the existing Minecraft Developer rules.

- Helper: read/investigation access plus configured warnings and short temporary mutes only, within configured limits. No kicks, bans, permanent mute, channel restrictions, cross-platform punishment, custom unrestricted sanctions, or overturn authority.
- Mod: configured Discord punishments, temporary/custom temporary durations within policy, but no permanent Discord ban/mute/channel restriction and no unrestricted major overturn.
- Developer: same Discord moderation authority as Mod. This does **not** grant Minecraft punishment authority. A Developer cannot use Discord to create a Minecraft punishment unless the Developer independently has the required Minecraft/domain permission.
- Admin/Founder: permanent Discord ban, permanent mute, permanent channel restriction, and elevated overturn/custom authority according to the domain policy.

Self-punishment is blocked. Equal/higher staff targeting follows the established hierarchy/protection rules. Discord native role hierarchy is also checked as an enforcement precondition, but never replaces domain authorization.

## 5. Punishment notifications and appeals

Formal appeals are website-only.

Punishment DMs clearly state:

- affected platform(s);
- punishment type and duration/permanent state;
- reason and bounded explanation where appropriate;
- where to appeal.

No case ID is shown merely for player-facing reference.

If the user remains in the Discord, the message may link the support channel for help: `https://discord.com/channels/1410303324745371709/1511217148230373568`, while directing the formal appeal to the website.

If the user is not in the Discord, include the Discord invite `https://discord.gg/4ku4cTAf3v` for support plus the website appeal link. The website remains the guaranteed appeal path, including for banned users.

DMs are also sent for staff-made reductions/extensions/removals and when a timed punishment ends normally. DM delivery failure never rolls back the punishment; delivery outcome is recorded.

Discord tickets remain support/communication only. They do not become a second appeal record or decision system.

## 6. Cases, notes, and evidence

Normal punishments automatically create or attach to a case. Staff may also create investigation-only cases without punishment.

Cases automatically close/expire after 30 days with no meaningful activity. Meaningful activity resets the inactivity clock.

Private notes support person-level, Discord-level, Minecraft-level, and case-level context where useful. Normal staff and management-sensitive visibility may be distinguished. Edits preserve prior versions/audit; nothing silently disappears.

### Automatic Discord evidence

For message-context moderation, automatically capture a bounded evidence snapshot including:

- offending message content and identifiers;
- author/Discord identity;
- guild/channel and timestamp;
- attachments/metadata appropriate for evidence;
- message link/IDs;
- up to five messages before and five after when available;
- original and edited forms if the message is edited after capture;
- actor and moderation action;
- AutoMod detector/rule/classification information when applicable.

Staff should not normally have to manually save obvious message evidence. A `Capture more context` action may be offered.

Punishment-related evidence is retained until 30 days after the punishment ends. Evidence for a case with no punishment is retained until 30 days after the case closes for inactivity. No separate "important evidence" indefinite-retention flag is required.

## 7. Linked-alt/evasion alerts

Linked accounts are private staff moderation data. Public users and the public bot never reveal another user's linked accounts or historical links.

Staff `/linked` and staff panels may show current linked accounts, main account, historical links, relationship timestamps/status, and organized investigation context. Active playtime and active-punishment walls are not required in the normal linked display.

If an account has an active punishment and another linked account behaves in a way that may indicate evasion (for example, a linked Minecraft alt joins while another is banned, or speaks while another is muted), EnthusiaStaff does **not** automatically block or punish the alt solely from this signal.

Instead:

- create a durable staff alert with the relevant linked identities, active punishment, and triggering event;
- notify online Minecraft staff;
- send a Discord alert that pings all staff role `1497476349244211311`;
- provide quick investigation/moderation actions;
- leave the final evasion decision to staff.

Historical Discord links alone do not automatically transfer punishments to another Discord account. They may produce an investigation signal.

## 8. Discord AutoMod

The custom AutoMod replaces day-to-day reliance on Discord AutoMod only after shadow/validation proves acceptable behavior. Discord's built-in raid protections may remain; Enthusia does not need to recreate a general raid system.

All configured ticket/support channels/categories are exempt from **all automatic AutoMod scanning, deletion, and punishment**. Manual staff moderation still works there. Initial exempt IDs include:

- Support category: `1410328053481214072`
- General support: `1410326661274275972`
- Player Reports: `1502844099990917181`
- Bug Reports: `1502845099661459557`
- Management Ticket: `1502846745011556373`
- Appeal-Tickets: `1503824130833780886`

The exemption list is configuration, not hard-coded policy.

### Detection philosophy

Maximize useful detection until additional sensitivity would cause more harm through false positives than it prevents. The pipeline should be aggressive against intentional evasion but confidence-aware:

- Unicode normalization and confusable handling;
- word/token boundaries so prohibited sequences inside innocent words/names are not automatically matched;
- spacing/punctuation substitutions and common symbol/number substitutions;
- repeated-character and split-message evasion;
- exact/near-duplicate and cross-channel behavior;
- contextual rules for targeted abuse;
- message edits re-enter the same moderation pipeline.

Ordinary profanity is allowed. Targeted/severe abusive phrases, suicide encouragement, slurs, and hateful use are moderated. Identity terms that may be neutral in ordinary language must be evaluated in context rather than blanket-blocked.

When confidence is insufficient, flag for staff review and leave the message in place rather than guessing.

### OpenAI Moderation API

OpenAI's Moderation API is the default AI moderation signal because it is free for API users and purpose-built for categories such as harassment, hate, threats, self-harm, sexual content, and violence.

It is a signal, not the Enthusia policy engine:

- local deterministic rules own server-specific policy, obfuscation, invite/link behavior, ticket exemptions, and known bad domains;
- Moderation API results may be used fairly broadly as a contextual signal;
- automatic deletion/warning/mute may combine AI confidence with deterministic/context rules;
- AI alone must never cause a severe punishment such as a ban;
- ambiguous AI-only results go to staff review;
- shadow-mode false positives/false negatives are recorded;
- the system does not silently retrain/rewrite moderation policy from staff overturns.

If a later contextual model is tested, it is optional and cost-bounded; no paid AI dependency is required for the initial design.

### AutoMod behavior

Possible configured outcomes are allow, log, flag/review, delete, warn, mute, create/attach case, or stronger configured action when deterministic evidence warrants it.

Examples:

- spam/flood: detect rapid flood, near-duplicates, sentence splitting, mention spam, repeated attachments, and small edits intended to evade duplicate detection; first ordinary offense may delete + warn, with ladder escalation;
- normal link: allowed by default;
- known malicious domain/link: delete and apply the configured malicious-link consequence unless behavior instead matches a likely compromised account;
- same/substantially similar link in three different non-exempt channels within 60 seconds: treat as likely account compromise, delete copies, apply Account Security Lock, save evidence, DM the user, and log to staff **without pinging staff**;
- external Discord invite in ordinary/general chat: delete with no warning/punishment;
- invites to the Enthusia Discord are allowed;
- allowed/blocked domains and channel/category exceptions are configurable;
- new Discord account age is a risk signal only and never an offense by itself;
- staff are never automatically punished by AutoMod, but prohibited staff messages may still be removed and logged;
- questionable username/global display name/profile/bio content is staff-flag-only; if server nicknames are enabled and a prohibited nickname is set, the bot may revert/reset the nickname and alert staff without punishment;
- ordinary reaction spam is allowed; when one user uses letter/regional-indicator-style reactions to construct a prohibited phrase, reconstruct the sequence, remove offending reactions, log/alert, and avoid automatic punishment initially unless a later approved rule explicitly permits it.

Every automatic punishment/action is logged in a dedicated staff channel. Only medium/high-risk or uncertain events need an all-staff ping; routine automatic actions should not train staff to ignore pings.

AutoMod staff messages act as mini moderation panels with actions such as Overturn, Change Punishment, View History, Add Note, Escalate, and Open Evidence. Overturn reverses the reversible sanction/history effects after confirmation while preserving the original evidence/audit. Deleted Discord messages cannot be reliably restored.

## 9. Account Security Lock

Account Security Lock is a safety state, not a punishment and does not contribute to the punishment ladder.

Use it when behavior strongly suggests a Discord account is compromised, especially repeated automated link/scam posting across channels.

A lock:

- removes/deletes the triggering scam/ad messages as configured;
- prevents normal-channel participation using the same managed restriction infrastructure while retaining support/ticket access;
- records the security event/evidence;
- DMs the user that suspicious automated activity was detected and directs them to secure the Discord account and contact support;
- logs the event to staff without pinging the all-staff role;
- remains until authorized staff remove it with `/unlock` or an alias.

No self-service unlock is required because the bot cannot reliably prove that the user actually secured their Discord account.

## 10. Discord ban migration and reconciliation

Existing guild bans must be migrated into EnthusiaStaff before the new system becomes authoritative for Discord moderation.

Migration rules:

- fetch the complete current Discord guild ban list;
- create a corresponding legacy/imported Discord ban sanction/history record for each banned Discord user;
- preserve Discord user ID and available Discord ban reason;
- recover issuing actor/timestamp/reason from available Discord audit history when reliably available;
- mark unavailable legacy fields as unknown/legacy rather than inventing data;
- never unban/reban users merely to migrate them;
- reconcile imported authoritative records against the native Discord ban list and block cutover on unexplained mismatches.

After cutover, staff should normally ban/unban through Enthusia commands/panels. The bot also observes native guild-ban add/remove events so a manual Discord moderation action cannot silently drift from EnthusiaStaff. Manual native changes are reconciled into authoritative history with audit/alert rather than ignored.

## 11. Role and permission synchronization

The new system should eventually replace DiscordSRV role synchronization.

- Role sync is one-way from authoritative Minecraft/Enthusia state to Discord.
- Evaluate legitimate role eligibility across all current linked Minecraft accounts.
- Manual Discord role changes do not grant Minecraft rank/permissions and may be reconciled back to the authoritative state.
- Moderation authority is still decided by EnthusiaStaff domain policy; a synced role is not sufficient authorization.
- Bot/native Discord hierarchy preconditions are checked before an external action and reported clearly if Discord prevents enforcement.

## 12. Public information bot

The public bot is a separate, freely installable application with no access to private moderation/link history.

Initial commands:

- `/player <name>`
- `/whois <name>`
- `/guild <name>`
- `/baltop`
- `/playtime <name>`
- `/leaderboards` (link to the website leaderboard page rather than duplicating every board)
- `/store`
- `/website`
- `/discord`
- `/rules`
- `/ip`

A future `/status` may expose public network/player status if useful.

Public `/whois` never reveals Discord links, alts, historical links, staff notes, cases, punishments beyond deliberately public punishment surfaces, or any private moderation data. Static/link commands use clean embeds/buttons rather than bare URLs where practical.

The public bot reads only sanitized public service/API contracts for EnthusiaStaff identity, EnthusiaCurrency, EnthusiaCommend, guild data, PlayTimePlugin, and other approved sources. Compromise of the public bot must not expose the staff moderation API or privileged MariaDB access.

## 13. Website responsibilities

Formal appeals live entirely on the website. The website also remains the preferred surface for complex investigation, search/filtering, evidence review, audit, permission/configuration management, and AutoMod configuration.

Fast everyday moderation belongs in Discord/Minecraft panels and quick commands; complex review belongs on the website. All surfaces call the same authoritative moderation services.

Search should support Minecraft name/UUID, Discord username/ID, case, staff member, reason, date range, platform, and punishment state where authorized.

Player reports may feed the same case system so reports, cases, evidence, and resulting sanctions do not become disconnected records. Investigation cases may be claimed/reassigned by staff; related cases may be linked informationally without automatically punishing anyone.

## 14. Reliability, privacy, and security requirements

- Persistent Gateway reconnect/backoff, Discord REST rate-limit handling, bounded executors/queues, and graceful shutdown/recovery are required.
- Use stable idempotency keys for destructive interactions and protect buttons/selects/modals from replay/stale component actions.
- Reauthorize every mutation at the domain/service boundary; command visibility is not security.
- Preserve audit for manual and automatic actions, link changes, role sync, migration, reconciliation, notes, evidence access, and sanction changes.
- Discord downtime must not break Minecraft moderation; Minecraft downtime must not break safe Discord-only reads/actions that do not require unavailable Minecraft enforcement.
- Cross-platform partial failure is explicit and recoverable; never report "both applied" when only one platform succeeded.
- Avoid continuously archiving Discord. Store only bounded moderation evidence/case information required by the approved retention rules.
- Public bot, staff bot, website, and Minecraft runtimes use least-privilege credentials and separate trust boundaries.
- Production/staging Discord application IDs, tokens, webhook routes, and allowed guilds/channels are configuration/secrets, never source constants except documented non-secret guild/channel identifiers that are intended as defaults.

## 15. Validation and cutover

Before Discord moderation authority cutover, validate at minimum:

- account-link migration and one-Discord-to-many-Minecraft cardinality;
- staff-link authorization and rank boundaries, including the Discord-only Developer exception;
- managed mute/restriction role correctness including ticket-channel access and newly created channel reconciliation;
- native ban import parity and manual native ban/unban reconciliation;
- temp punishment expiry across restart/outage;
- cross-platform independent enforcement and partial failure recovery;
- message/user context flows, stale component replay, confirmation, evidence capture, and retention cleanup;
- AutoMod normalization/obfuscation, innocent boundary cases, edits, links/invites, compromised-account locks, reaction phrases, ticket exemptions, staff exemption, and OpenAI Moderation integration;
- AutoMod shadow-mode false-positive/false-negative review before enforcement activation;
- rate limits, Discord outage/reconnect, MariaDB outage, duplicate delivery/action, process kill, and restart recovery;
- public-bot data sanitization and credential isolation;
- website-only appeal links/eligibility and notification flows.

Discord AutoMod replacement becomes authoritative only after shadow validation is accepted. Existing Discord raid protections may remain defense in depth.
