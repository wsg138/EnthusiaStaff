# ES-D16 — Moderation console real-data read bridge

Status: `BLOCKED` / `PARKED_BLOCKED`
Priority: 135.5
Implementation PR: #187
Branch: `package/es-d16-moderation-read-bridge`
Frozen executable head: `3a79000eaa139ec107118d3fdb05b29e5e52097c`
Canonical handoff: `ai-agents/reports/package-handoffs/2026-09-10-es-d16-live-acceptance-current.md`

## Scope

ES-D16 extends the completed D05/D06 Discord moderation foundation with the real-data browser/Worker/StaffBot read bridge and the bounded transition support needed to exercise that bridge safely. It does not grant destructive moderation, message deletion, LiteBans cutover, production Discord configuration changes, or issue #43 acceptance.

## Implemented product state

The frozen product head delivers:

- signed, session-bound browser/Worker/StaffBot reads with explicit protected-route dispatch and fail-closed authorization;
- `/moderate-preview` with an optional player: no-player launches open the invoking channel with no target selected, while targeted launches retain player-specific moderation context;
- channel and player switching with target-specific History/Cases/Notes/Accounts/punishment controls disabled until a player is selected;
- no-target channel browse mapped without blocking per-author Discord REST member retrieval, using cached member data for display enrichment;
- Show Context that retrieves and displays surrounding messages from the same channel and all authors, centered on the selected message;
- Discord-like reply references: when Discord supplies the referenced message, the site renders a compact author/text preview; activating it scrolls to the referenced loaded row or loads same-channel around-message context and then scrolls;
- when no target is selected, player selection by double-click is restricted to the message author's name or avatar; the entire message row is not a player-selection trigger;
- author and message-text filters that preserve focus and caret across message-workspace rerenders so normal continuous typing works;
- whole-row evidence selection, exclusive `•••` menus, outside-click/keyboard dismissal, message-ID copy, Discord deep links, and clear author/message hierarchy;
- optimized message paging using pages of up to 50 where supported;
- arbitrary custom punishment durations such as `60 days`, `12 hours`, `90 minutes`, shorthand like `12h`, or `Permanent`, with invalid/zero durations rejected before review;
- punishment draft resume, Discord/chat versus In-game offense separation, final-review validation, explicit allowlisted public fields, and neutral loading/empty/error states;
- professional product copy with one truthful Testing note at the non-destructive boundary;
- Cloudflare packaging regression coverage that fails if a local page asset is omitted from the Worker bundle;
- a compact Discord launcher with the target shown once and channel shown once;
- simulation/test-only destructive review: D16 does not send punishments/DMs, mutate Discord permissions, delete messages, change LiteBans authority, or perform production cutover.

## Exact executable-head validation — PASS

For exact executable head `3a79000eaa139ec107118d3fdb05b29e5e52097c`:

- Coverage run `34434155437`: **SUCCESS**. Clean Java 21 repository build/tests, runtime-JAR inspection, aggregate coverage, validation-artifact upload, and Codacy coverage upload all passed.
- Moderation Web Validation `34434155472`: **SUCCESS**.
- Staff Bot PR Artifact `34434155529`: **SUCCESS**.
- Staff Bot Configuration Cache `34434155616`: **SUCCESS**.
- Sentinel Restart Artifact `34434155596`: **SUCCESS**.
- Pi Staging Supersession `34434153678`: **SUCCESS**.
- Moderation Web Staging Deploy `34434152711`: **SUCCESS** on the exact executable head, including the protected Cloudflare deployment path.
- Codacy Static Code Analysis `102735847147`: **SUCCESS**, zero annotations / zero new valid findings.
- Codacy Diff Coverage `102739125713`: **SUCCESS**, 52.54% diff coverage.
- Codacy Coverage Variation `102739126122`: **SUCCESS**, +0.04% against the -1.0% target.
- CodeRabbit exact-head status: **SUCCESS**.

## Exact StaffBot artifact

Owner live testing must use Staff Bot PR Artifact run `34434155529`:

- artifact id: `10135569887`;
- artifact name: `staff-bot-pr-187-3a79000eaa139ec107118d3fdb05b29e5e52097c`;
- artifact ZIP digest: `sha256:2eb7b2645110dac922aeb1e8f1d4d04ab510162e9e7d8ccb9d3ab0fa8c203498`;
- source marker: `3a79000eaa139ec107118d3fdb05b29e5e52097c`;
- contained `EnthusiaStaff-StaffBot.jar` SHA-256: `1f61d87b71341c4d9fe1d4702b164862359b29ea0808ce5daf7afa135dc6ecb3`.

Independent artifact inspection confirmed exact source provenance, the packaged checksum, runtime manifest/JDA classes, and the current channel-browse, reply-preview, and filter-focus resources.

## Moving-main checkpoint

`main` moved after the executable freeze. At the latest reconciliation before publication it was `06519c0c5acdcf6276278204201f3c8b20767805`. Owner live acceptance intentionally occurs against the exact frozen artifact first. After acceptance, the same D16 continuation must reconcile the then-current `main` normally, preserve concurrent work, rerun all gates invalidated by the changed executable state, and only then merge PR #187.

## Current blocker

The only remaining gate is owner-operated **StaffBot-only live acceptance** on authorized non-production Bloom staging. Protected Cloudflare staging is already deployed for the exact executable head. The connected worker has no authenticated Bloom/Pterodactyl mutation surface, so it cannot replace/restart the live StaffBot itself.

Exact unblock:

1. Replace only Bloom `EnthusiaStaff-StaffBot.jar` with artifact `10135569887`, or with the contained JAR matching the SHA-256 above.
2. Restart StaffBot only, preserving the existing runtime files and flags.
3. Do **not** replace or restart Paper.
4. Open a fresh `/moderate-preview` launch.
5. Verify sanitized live behavior for no-player launch speed, channel/player switching, Show Context with surrounding all-author messages, compact reply previews and click-to-jump/context-load navigation, double-click author selection only on name/avatar, continuous author/text-filter typing, prior message menus/selection, arbitrary custom durations, and the non-destructive final-review boundary.
6. Record only sanitized acceptance evidence. Do not expose signed launch material, credentials, private request data, raw private messages, or secrets.

If live acceptance passes, resume this same PR as the higher-priority `ACTIONABLE_CONTINUATION`, reconcile moving `main`, rerun invalidated exact-head gates, merge #187 normally only when green, prove containment/cleanup, publish `COMPLETE`, and stop.

## Concurrency / exclusions

- PR #187 remains intentionally open and unmerged while owner acceptance is outstanding.
- ES-D13 PR #178 and ES-X03 PR #139 remain separate and untouched.
- ES-D07 is not started by this worker.
- LiteBans remains authoritative and untouched.
- No production deployment/configuration/data access, destructive moderation, message deletion, issue #43 acceptance, or cutover is authorized by this package.
