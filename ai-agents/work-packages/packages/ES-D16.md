# ES-D16 — Moderation console real-data read bridge

Status: `BLOCKED` / `PARKED_BLOCKED`
Priority: 135.5
Implementation PR: #187
Branch: `package/es-d16-moderation-read-bridge`
Frozen executable head: `8bef6775d411ac0c22321b0edd0485a0f9c84d5e`
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
- Cloudflare packaging of every local asset referenced by the moderation page. A regression test now fails if a page asset is omitted from the Worker build;
- a compact Discord launcher whose title is only `@username`, with the channel shown once below and no visible technical account ID;
- bounded transition collection and the previously required schema/classloader repair. That Paper migration issue has already been resolved and is not the current blocker.

## Exact frozen-head validation — PASS

For exact executable head `8bef6775d411ac0c22321b0edd0485a0f9c84d5e`:

- Coverage run `34364743352` / job `102510774307`: **SUCCESS**. Java 21 repository-wide build/tests, runtime-JAR inspection, aggregate coverage, validation-artifact upload, and Codacy coverage upload passed. Aggregate coverage: 52.32% lines / 42.47% branches / 54.56% instructions.
- Coverage validation artifact `10109772008`, digest `sha256:289fc8b3df9ef39c7a17035f0772eb280b5970c20c149a455e2e76fea09e8679`.
- Moderation Web Validation run `34364743253`: **SUCCESS**.
- Staff Bot PR Artifact run `34364743331`: **SUCCESS**.
- Staff Bot Configuration Cache run `34364743259`: **SUCCESS**.
- Sentinel Restart Artifact run `34364743324`: **SUCCESS**.
- Moderation Web Staging Deploy run `34364735886`: **SUCCESS** on the exact frozen head, including the corrected complete web-asset bundle.
- Pi Staging Supersession run `34364738724`: **SUCCESS** on the exact frozen head.
- Codacy Static Code Analysis check `102510993058`: **SUCCESS**, zero annotations / zero new valid findings.
- Codacy Diff Coverage `102514180788`: **SUCCESS** at 51.92% (no repository gate defined).
- Codacy Coverage Variation `102514180125`: **SUCCESS** at +0.01% against the -1.0% target.
- `main` `423e72c764c9acfce6bb80918f07367fea2cfccf` remains fully contained in the package branch; PR #187 remains open/non-draft/unmerged and mergeable.

## Exact StaffBot artifact

Owner live testing must use the StaffBot artifact from run `34364743331`:

- artifact id: `10109369813`;
- artifact name: `staff-bot-pr-187-8bef6775d411ac0c22321b0edd0485a0f9c84d5e`;
- artifact ZIP digest: `sha256:c420d192f2a145e838fb50fbe6517688213ac76466b07afcceefa935de19d402`;
- source marker: `8bef6775d411ac0c22321b0edd0485a0f9c84d5e`;
- contained `EnthusiaStaff-StaffBot.jar` SHA-256: `bb1acb90fffd7330c82063e5cd6bd4749d5cfef08cb732bca7b438769cf89c67`.

Independent artifact inspection confirms the downloaded ZIP digest and contained JAR checksum. This artifact supersedes the earlier `f2b901f...` owner-test JAR because the Discord launcher presentation changed after live review.

## Live-review repair checkpoint

Owner review exposed that the newer website usability layers were present in StaffBot resources but omitted by `moderation-web/scripts/build.mjs`, so the Worker deployment continued serving the older website behavior. This was a packaging defect, not a browser-cache issue. The frozen head fixes the build list, adds regression coverage that compares page asset references against the build inputs, and successfully redeploys the corrected bundle to the protected staging origin.

The same checkpoint removes duplicate channel text from the Discord launcher: its heading is now only `@username`; the channel is shown once in the Channel field below.

## Current blocker

The only remaining gate is owner-operated **StaffBot-only live acceptance** on authorized non-production Bloom staging. The connected worker has no authenticated Bloom/Pterodactyl mutation surface, so it cannot safely perform this deployment itself.

Exact unblock:

1. Replace **only** Bloom `EnthusiaStaff-StaffBot.jar` with artifact `10109369813`, or with the contained JAR whose SHA-256 is recorded above.
2. Restart **StaffBot only**, preserving the existing runtime files and flags.
3. Do **not** replace or restart Paper. The historical Paper migration/classloader blocker is resolved and superseded.
4. Open a fresh Discord-generated moderation link from the channel being investigated. The corrected Cloudflare bundle is already deployed; use a fresh link or hard refresh so the current page scripts load.
5. Verify sanitized live behavior for the corrected launcher, real Discord/Minecraft identity, channel-scoped initial messages, whole-row selection, exclusive/outside-dismissed message menus, message-ID copy from the menu, context UX, search/filter coverage, workflow resume, Discord-vs-In-game offense split, final-review validation, record empty/error states, product language, and the non-destructive boundary.
6. Record only sanitized acceptance evidence. Do not expose signed launch material, credentials, private request data, raw private messages, or secrets.

If live acceptance passes, resume this same PR as the higher-priority `ACTIONABLE_CONTINUATION`, reconcile moving `main`, rerun any gates invalidated by executable change, merge PR #187 normally only when all required evidence is green, prove containment/cleanup, publish `COMPLETE`, and stop.

## Concurrency / exclusions

- PR #187 remains intentionally open and unmerged while this external gate is outstanding.
- ES-D13 PR #178 and ES-X03 PR #139 remain separate and untouched.
- ES-D07 is not started by this worker.
- LiteBans remains authoritative and untouched.
- No production deployment/configuration/data access, destructive moderation, message deletion, or cutover is authorized by this package.
