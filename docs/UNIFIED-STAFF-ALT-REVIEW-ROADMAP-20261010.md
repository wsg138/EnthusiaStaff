# EnthusiaStaff — Unified Identity, Sanctions, and Review Center
Owner direction: 2026-10-10. Status: staged implementation plan; **not a completed production rollout**.

## Owner-approved behavior to implement

One private evidence graph should combine (1) verified current Discord ↔ Minecraft identity links, (2) protected network equality matches, and (3) audited staff-authored relationship decisions. It must preserve each evidence source and its age; a historical Discord link or shared network is not automatically proof of the same person.

Policy: automatically inherit active **ban / network ban / identity ban / mute / public mute** only for explicitly confirmed same-person alts, current same-Discord verified Minecraft accounts with no overriding manual exception, or manually adjudicated `VERY_CONFIDENT` (90%) relationships. The owner specified a minimum 85% threshold. Current numeric confidence values are fixed **policy grades, not calibrated probabilities**: no automatic classification from 25%-confidence IP equality may meet the threshold by itself. Review weaker matches; do **not** convert inheritance into a new permanent ban-evasion infraction.

`SHARED_HOUSEHOLD`, `NOT_RELATED`, and currently `APPROVED_ALT` suppress auto-inheritance on that **pair**, even when a Discord link is currently shared. Staff can manually change or reopen these decisions through audited `/alt` commands. Sibling/roommate exceptions must never become global IP whitelists.

## Original documentation and integration boundaries

- ES-P03 merged canonical Java/Floodgate identity implementation; ES-P09/PR #84 merged protected network observations, confidence states, manual decisions, retention and conservative sanction inheritance.
- V20/StaffBot adds currently verified Discord↔Minecraft links and main-account selection; they were historically displayed separately in `/alts`.
- ES-D09/PR #203 contains broad Discord evidence, case/notes, and *durable evasion-alert routing* but remains open. Its historic V22 migration ownership is stale relative to current main's V21, V24–V30. Rebase/reconcile migration IDs on a dedicated review branch; do not silently cherry-pick or overwrite V21/V22 ownership.
- Draft PR #482 contains Staff tool/GUI and separate ban-policy fixes. It is not merged; avoid overlapping commits without deliberate reconciliation.
- Existing `staff_alerts` low-confidence network records do not satisfy the punishment-request worker's audience/delivery contract; this is a **known notification delivery gap**.
- Bundled Velocity `network-identity.enabled=false`; actual live config, JARs, keys, authority, and past cases are not yet verified.
- ES-V02 representative Java/Bedrock/Folia/multi-proxy/private false-positive acceptance remains outstanding.

## Phase 1 — unified decision engine and login inheritance (in progress in this PR)

- [x] Align fixed relationship confidence mapping and threshold >=0.85 for auto-inheritance; only `VERY_CONFIDENT` and `CONFIRMED_ALT` qualify without current verified linking.
- [x] Remove old weak new-account-after-cutover automatic inheritance from network match policy. Low confidence instead creates review records; network-overlap alone cannot ban.
- [x] Include `PUBLIC_MUTE` in inheritable sanctions.
- [x] At ACTIVE Velocity login, read currently verified same-Discord Minecraft peers AND manually marked very-high-confidence/confirmed alts and inherit original active sanctions without requiring network-identity/HMAC activation. Respect explicit protected pair decisions. No automatically persisted `CONFIRMED_ALT` state is created by a current Discord link, so unlinking removes that evidence on subsequent reads.
- [x] Inherited sanctions are not used as new inheritance sources (no cascaded duplicate chain).
- [x] Domain tests and MariaDB fixture tests added; local domain compilation passes; **MariaDB/Testcontainers execution blocked by absent local Docker**, must pass hosted Linux checks before merge.
- [ ] Establish exact production configuration and stage-only verification.

## Phase 2 — all-active-account correctness (MUST complete before owner-ready)

- [ ] Durable, idempotent **source-sanction reconciliation event** emitted in the same transaction whenever a ban/mute is CREATED, extended, reduced, ended, revoked, overturned, or expired. Never rely only on alt login.
- [ ] Reconcile peer groups after manual `/alt` decisions, confirmed-link changes/unlinks/reassignments, and protected network/identity-evidence changes. Lock/retry appropriately, stop at 100 bounded linked accounts, and audit every effect.
- [ ] Automatic inheritance never changes a punishment's case, reason, duration, or public/private visibility. Inherited copies reference **original** sanctions, not transitive copies.
- [ ] When source punishment is removed or shortened, reconcile **every inherited derivative**, including offline identities and existing online sessions. A sibling exemption added later must remove only correctly derived effects, without removing independently imposed sanctions.
- [ ] Audit network/banned-account masks so non-qualified shared IPs are **not** accidentally blocked by separate global IP-ban enforcement.
- [ ] Cross-server login, online kick/mute, proxy switch, and immediate post-commit effects must agree. Fail closed on authoritative DB loss but not by asserting false-positive identity.

## Phase 3 — common durable Review Center (MUST complete)

Provide one persistent case/review item contract keyed by source event/revision and allowed audience: `REPORT`, `PUNISHMENT_APPROVAL`, `ALT_EVASION`, `AI_MODERATION`, `OPERATIONS`. Use distinct per-category statuses: NEW, CLAIMED, WAITING, RESOLVED, EXPIRED. Acknowledge individual staff notices, but preserve item globally until resolved. A review request is not an applied punishment.

- [ ] Query and list counts of **only currently actionable items**, grouped by type, with last-change timestamps. Do not conflate review and unread notifications.
- [ ] In-game Staff dashboard: clear Review Center item with counts; concise two-line join/live notice, click to filtered, paginated GUI; refresh, claim, resolve, and case evidence actions. Provide text fallback for Bedrock/console.
- [ ] Discord StaffBot: private staff channel only, one short alert card with actor/target/source/reason, review button, current queue/claim state. Ping authorized staff role for urgent alt/evasion, with dedup/cooldown; no public link/history/IP leakage. Per-recipient delivery, expiry, retry, outage recovery and dead-letter repair.
- [ ] Integrate ES-D09's evidence and durable evasion-notification work into current schema, not a parallel second alert store. Disable stale old `staff_alerts` writes once migrated and audited.
- [ ] Real report submission and punishment-approval integration. Ensure staff can see whether a request was approved, committed, enforced on Velocity/Paper, or needs reconciliation.

## Phase 4 — professionalism and comprehensive Staff UX

- [ ] Inventory every message, menu, command, target-picker, login denial, sanction screen, Discord embed, audit view, recovery screen. Replace raw enums/UUID-only UI with player name, compact status, public reason, optional details and one obvious next action.
- [ ] Standard Staff presentation: short heading, status, target, reason, case reference, clickable relevant action; no bulky multiline lore. Distinguish success vs accepted-for-processing vs pending vs failed.
- [ ] Consistent public/private GUI confirmation. Public announcements must be emitted only from a durable committed authoritative event and rendered once per network, not once per Paper server or separately from staff GUI actions.
- [ ] Reconcile #482 launch action, cooldowns and punishment approval clarity with unified review GUI. Validate Folia scheduler, ability/rank changes and abuse limits.
- [ ] Full command/discovery/permission/documentation audit and real Minecraft staff walkthrough.

## Phase 5 — validation and staged release (required before ACTIVE)

1. Hosted clean Java/test static gates, MariaDB/Testcontainers, migration checks and review for exact SHA.
2. Representative isolated HUB/SMP/Velocity/StaffBot topology, Java/Floodgate clients, Discord role scoping, outage/restart/rejoin tests.
3. Test matrix: 3 current Discord links; 2 different IPs; 2 siblings; shared school/CGNAT; ambiguous IP with 21 peers; 75%, 90%, 100% states; banned online/offline; mute and public mute; source expiry/reduce/revoke/overturn; relink/unlink; two simultaneous proxy observations; repeated webhook deliveries; dead letters; actor permission downgrade; proof no private IP/text leakage.
4. Compare shadow recommendations with staff-reviewed private historical cases. False-positive rate and alert delivery must be measured; no fictional accuracy percentage.
5. Pin actual deployed Paper/Velocity/StaffBot SHA256, config versions, secrets present/absent and migration head. Backup, canary, observe, rollback; get owner approval before switching any destructive authority.

## Current branch limitation

This branch is a **first vertical slice** for the new decision policy and linked-login inheritance, not release acceptance. It does **not yet** provide all-active-account immediate propagation, a merged Discord alert service, a unified Review Center, migration coordination, or a complete plugin-wide UX rewrite. Keep the PR a **DRAFT**, not ACTIVE, and do not deploy directly.
