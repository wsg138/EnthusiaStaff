# ES-D16 — Moderation console real-data read bridge

Status: `BLOCKED` / `PARKED_BLOCKED`
Priority: 135.5
Implementation PR: #187
Branch: `package/es-d16-moderation-read-bridge`
Frozen executable head: `f2b901f731558224e2df6ee1d8ed38d06063a150`
Canonical handoff: `ai-agents/reports/package-handoffs/2026-09-09-es-d16-staffbot-live-acceptance-blocked.md`

## Scope

ES-D16 extends the completed D05/D06 Discord moderation foundation with the real-data browser/Worker/StaffBot read bridge and the bounded transition support needed to exercise that bridge safely. It does not grant destructive moderation, message deletion, LiteBans cutover, production Discord configuration changes, or issue #43 acceptance.

## Implemented product state

The frozen product head delivers:

- signed, session-bound browser/Worker/StaffBot reads with explicit protected-route dispatch and fail-closed authorization;
- channel-bound Discord launches and channel-scoped initial message results;
- real Discord and linked Minecraft identity presentation using explicit allowlisted fields only;
- bounded same-channel context reads covering ±2 minutes, with at most four pages per direction and rejection of non-advancing cursors;
- filtering that cannot import messages from another channel into context;
- neutral loading/empty states with no sample identity;
- whole-row message selection plus an exclusive `•••` action menu, outside-click dismissal, keyboard handling, message-ID copy, Discord links, and readable author/message hierarchy;
- evidence selection kept separate from delete-on-confirm review state;
- punishment draft resume across message review and explicit abandonment only on close/cancel;
- Discord/chat offenses separated from the In-game cheating path;
- professional product copy across active and fallback UI layers, retaining one truthful Testing note for the non-destructive environment;
- a simulation-only action endpoint: no live punishment, DM, Discord permission mutation, or message deletion is enabled by D16;
- bounded transition collection and the previously required schema/classloader repair. That Paper migration issue has already been resolved and is not the current blocker.

## Exact frozen-head validation — PASS

For exact executable head `f2b901f731558224e2df6ee1d8ed38d06063a150`:

- Coverage run `34354348704` / job `102475585346`: **SUCCESS**. Java 21 repository-wide build/tests, runtime-JAR inspection, aggregate coverage, validation-artifact upload, and Codacy coverage upload passed.
- Coverage validation artifact `10105492106`, digest `sha256:0d6729f201ff17b875b763edbc7022c28d00a75c13ce859cd118330ff56d257f`.
- Moderation Web Validation run `34354348838`: **SUCCESS**.
- Staff Bot PR Artifact run `34354348823`: **SUCCESS**.
- Staff Bot Configuration Cache run `34354348705`: **SUCCESS**.
- Sentinel Restart Artifact run `34354348794`: **SUCCESS**.
- Moderation Web Staging Deploy run `34354344064`: **SUCCESS** on the exact frozen head.
- Pi Staging Supersession run `34354346426`: **SUCCESS** on the exact frozen head.
- Codacy Static Code Analysis check `102475650447`: **SUCCESS**, zero annotations / zero new valid findings.
- All visible PR #187 inline review threads are resolved; no unresolved actionable review thread remains.
- `main` `423e72c764c9acfce6bb80918f07367fea2cfccf` is the merge base and is fully contained in the frozen branch head; the branch is zero commits behind.

## Exact StaffBot artifact

Owner live testing must use the StaffBot artifact from run `34354348823`:

- artifact id: `10105100891`;
- artifact name: `staff-bot-pr-187-f2b901f731558224e2df6ee1d8ed38d06063a150`;
- artifact ZIP digest: `sha256:15704b385d674e5c1536f1c78b8d4cc0d24094580aee74eb1211e76bd71b33ea`;
- source marker: `f2b901f731558224e2df6ee1d8ed38d06063a150`;
- contained `EnthusiaStaff-StaffBot.jar` SHA-256: `a496a340e3bfbe76c7db2da146f021b7b9dce4e3275b2ae55bb42fc1eb5474e0`.

Independent artifact inspection confirmed the archive digest, source marker and JAR checksum agree; the manifest uses `net.enthusia.staff.discordbot.StaffBotApplication`; JDA/runtime classes and all required moderation assets are packaged; sample identities are absent; old user-visible staging/preview labels are absent; exactly one Testing note remains; and browser mutation traffic is limited to the deliberate non-destructive `/api/simulate` path.

## Current blocker

The only remaining gate is owner-operated **StaffBot-only live acceptance** on authorized non-production Bloom staging. The connected worker has no authenticated Bloom/Pterodactyl mutation surface, so it cannot safely perform this deployment itself.

Exact unblock:

1. Replace **only** Bloom `EnthusiaStaff-StaffBot.jar` with artifact `10105100891`, or with the contained JAR whose SHA-256 is recorded above.
2. Restart **StaffBot only**, preserving the existing runtime files and flags.
3. Do **not** replace or restart Paper. The historical Paper migration/classloader blocker is resolved and superseded.
4. Open a fresh Discord-generated moderation link from the channel being investigated.
5. Verify sanitized live behavior for real Discord/Minecraft identity, channel-scoped initial messages, message avatar/action/context UX, workflow resume, Discord-vs-In-game offense split, Enthusia branding/product language, and the non-destructive boundary.
6. Record only sanitized acceptance evidence. Do not expose signed launch material, credentials, private request data, raw private messages, or secrets.

If live acceptance passes, resume this same PR as the higher-priority `ACTIONABLE_CONTINUATION`, reconcile moving `main`, rerun any gates invalidated by executable change, merge PR #187 normally only when all required evidence is green, prove containment/cleanup, publish `COMPLETE`, and stop.

## Concurrency / exclusions

- PR #187 remains intentionally open and unmerged while this external gate is outstanding.
- ES-D13 PR #178 and ES-X03 PR #139 remain separate and untouched.
- ES-D07 is not started by this worker.
- LiteBans remains authoritative and untouched.
- No production deployment/configuration/data access, destructive moderation, message deletion, or cutover is authorized by this package.
