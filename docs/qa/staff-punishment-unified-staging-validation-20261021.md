# Unified Staff punishment / reporting validation candidate — 2026-10-21

**Status: DRAFT, NO-GO for live server or Discord delivery.**

## Pinned input revisions

- Final staff punishment + report-notification receipt audit: `97ad5b4b0b23cae050c8a2e843a41c9d94f9646e` (draft source #657, stacked on #654, #649, #645 and owner-calibration #621).
- Current `main` at the time of the independent integration: `3d07ec0651fd59f4c0ff189570204ffb3c82900b`.
- Independent merged integration worktree: `df531f3d899d5962b851dbb955baf889ff4be09b`.
- The source feature branches remain drafts and are the authoritative place for individual changes. This worktree used a normal no-fast-forward merge, no force push, no rebase, and no production mutation.

## Repeatable checks

Local Windows offline execution on this combined tree:

`gradlew.bat --offline --no-daemon :domain:test :paper:test :staff-bot:test :velocity:test :integration-tests:compileTestJava :paper:shadowJar`

**PASS** (BUILD SUCCESSFUL). `git diff --check` PASS. The existing `tools/staffbot-chat-preflight` synthetic unittest suite **15/15 PASS**, incorporating newer main-branch staging and production-shadow acknowledgement checks.

Windows does not prove Linux-specific FastAPI/POSIX suites or actual disposable MariaDB runtime. **Hosted exact-head MariaDB, coverage, Codacy and protocol/compatibility checks remain mandatory**.

## Read-only test1 isolation audit (2026-10-21)

A separate read-only pinned-key SFTP comparison of `bloom-test1` and `bloom-smp` confirmed:

- Legacy DiscordSRV global and staff channel maps match production exactly.
- Legacy DiscordSRV console destination also matches production and is still populated in test1.
- On-disk test1 Minecraft-to-Discord, Discord-to-Minecraft and console-command settings are all disabled.
- Test1 Paper EnthusiaStaff has a backend identifier and channel section, but has **no dedicated `discord-chat-bridge` SHADOW section**.
- Runtime loaded state, isolated StaffBot app identity, JDA guild/role/member access and effective endpoint isolation **were not verified**.

This inspection did not expose or copy channel IDs, tokens or database credentials into GitHub and performed no server writes, Discord sends or console commands. Disabled forwarding flags alone do not establish a permanent safety boundary if a plugin restarts or configuration changes.

## Hard blockers before staging or production

1. StaffBot automatic reporting remains an unregistered, default-INERT integration proof. No live sender may be scheduled or enabled on this candidate.
2. Confirm the owner's approved exact private StaffBot testing destination, allowed ranks, and restrictions on sensitive report content, then independently validate current JDA permissions. Shared production-like DiscordSRV destinations on test1 must be contained by an authorized test-only isolation/rollback change **before any real network send**.
3. Rehearse V32 report-message, V33 INERT intent, and V34 confirmed-receipt migrations on a **disposable** MariaDB copy. Demonstrate backup/rollback and interrupted DDL recovery. Preserve legacy V33 delivered timestamps in REVIEW_REQUIRED; never fabricate a Discord ID.
4. Confirm new report-created notifications only; `REPORT_MERGED` is deliberately quarantined without send until authorized follow-up text fetching and policy are separately implemented. Never substitute original report body for a merged follow-up.
5. Verify Java/Bedrock/Geyser and Paper/Leaf/Folia permissioned GUI/follow/report workflows in isolated staging, including role revocation and distinct DB/HTTP bridges.
6. Owner approves the real `reason-policies.yml` differences, severity thresholds, weights, sanction lengths and policy family review rules (#616). Guided punishment stays OFF until written policy signoff.
7. Obtain independent review and exact deployment JAR hash, restart, and rollback evidence before promotion.

No source PR is authorized for immediate merge/deployment merely because this integration builds.
