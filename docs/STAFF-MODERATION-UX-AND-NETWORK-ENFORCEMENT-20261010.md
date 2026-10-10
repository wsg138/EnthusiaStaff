# Staff moderation usability and network enforcement audit — 2026-10-10

## Scope / safety

Owner-requested audit: ban evasion, network-address ban inheritance, sibling exceptions, punishment-request approvals, public/private announcements, staff hotbar launch, cooldowns, and shorter clearer messages.

This branch is a **review candidate**, not a verified live deployment. The installed production JAR/config and yesterday's approval event were not accessible through the current Blackboard repository connection; do not assert that production behavior matches source or redeploy solely from this report.

## Changes in this review candidate

- Ban-evasion offense in bundled **policy v1** moves from a progressive 30/60/90-day sequence to one permanent `NETWORK_BAN` step. Policy version updated to `2026-10-10.1`. This is **not yet a guaranteed permanent IP-wide block**.
- Staff cooldown bundled defaults: random teleport 250 ms, target action 100 ms, toggle 100 ms, menu 100 ms; existing generated configs using the four old default values are mapped to the new delays at startup; distinct custom values remain unchanged.
- Staff dashboard rocket: click to launch in viewing direction; `/stafftools launch`, or sneak + right-click the existing dashboard star in staff mode. Retains nine protected hotbar slots, rank/session/permission checks, logged use, 350 ms throttle.
- Successful direct GUI punishment commits and GUI approval commits now post a **local Paper backend** public chat notice for `PUBLIC` cases only, excluding idempotent replays; ban types display generically as *ban*, not *IP ban*. Private case and internal note content are not disclosed.
- Approval receipt distinguishes request submission (not applied) from approved durable case; approved or denied reviewer now sees a resolved-case GUI instead of disappearing to chat. Public/private toggle is already present and defaults to public.

## Important uncompleted issues (deployment blockers for the requested full outcome)

1. **Verify actual production software and case:** Compare live SMP Paper JAR SHA256/version with GitHub release; Velocity JAR/config, loaded `reason-policies.yml`, moderation mode, database case and sanction rows, approval and outbox events, and enforcement logs from the owner-reported developer request. Do not paste identity secrets or raw player IPs into GitHub.
2. **All-ban IP inheritance requires design/test before activation:** Existing protected IP records and `JdbcNetworkIdentityStore.observeAndInherit` inherit on confident / unambiguous relationships only, and explicitly protect `SHARED_HOUSEHOLD`, `APPROVED_ALT`, `NOT_RELATED` states. A shared IP is not proof of one individual (siblings, schools, CGNAT, public Wi-Fi, reused addresses). Implement explicit staff-confirmed shared-household exemptions with audit, enforce bans network-wide on login with fresh observation without leaking addresses, and test new and old accounts, active session kicks, reconnect, temporary vs permanent sanctions, revocations, and false positives.
3. **Ban-evasion permanent IP type is not yet wired:** Bundled v1 `evasion.ban` remains `NETWORK_BAN`, *not* `NETWORK_IDENTITY_BAN`. Special identity bans are currently a separate founder-only policy. Changing this requires explicit authorization/rank and compatibility audit to avoid silently granting staff founder-grade IP powers.
4. **Cross-server announcements:** The new notice currently broadcasts on the local Paper backend for **two GUI confirmation paths only**. Network-wide announcements, console/legacy commands, AI/automod/moderation API commits and offline approvers need a durable outbox subscriber carrying `CaseVisibility` and once-only delivered-case claims; never announce a failed/replayed/private case.
5. **Config migration:** Staff cooldown loader now maps exactly the prior generated default values. Existing external `reason-policies.yml` retains the prior ban-evasion ladder unless safely migrated; define a versioned policy migration, backup/preview, and restart gate.
6. **Message audit incomplete:** Review all player-facing output (commands, menus, warning/kick/ban screens, staff dashboard, request alerts, history, Discord); standardize a concise title/status/next action, and keep error internals in logs only.
7. **Runtime acceptance:** Confirm velocity direction/height with elytra and spectator/staff flight on Folia, dashboard slot and protected inventory interactions, cooldown spam tolerance, and public announcement visual readability under RoseChat/InteractiveChat.

## Evidence from source

- Bundled v1 `reason-policies.yml` had `evasion.ban` first step 30d `NETWORK_BAN`.
- `PunishmentRequestService.approve` delegates to transactional `JdbcPunishmentRequestStore.commitApproval`: `moderation.createPunishment(connection, plan)`, request resolution, audit/outbox, and approval result occur in one transaction. This supports a durable write on success, but does not prove that the reported live request was processed or the player disconnected.
- `PunishmentGuiController.prepare` has already used `CaseVisibility.PUBLIC` as its default and `VISIBILITY_SLOT` toggles private/public.
- `PaperPunishmentCommitEffects` already handles online kicking / warnings after commit and processes durable network events, but the existing notification payload lacks visibility and names.

## Test record

Java/Paper compile: PASS; new presentation and selected Staff settings/GUI tests: PASS. Broad Windows test selection: 272 tests, 267 pass, 5 existing source-scanning assertions fail from checked-out CRLF vs literal LF (tracked in existing PR #479). Compile + Shadow JAR tasks succeeded, but combined Gradle invocation reports FAILED until these test-only Windows assertions are resolved. No runtime player acceptance.
