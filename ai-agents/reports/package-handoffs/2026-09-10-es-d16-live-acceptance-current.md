# ES-D16 StaffBot live-acceptance checkpoint — 2026-09-10

Package: `ES-D16 — Moderation console real-data read bridge`
Status: `BLOCKED` / `PARKED_BLOCKED`
PR: #187, open/unmerged
Branch: `package/es-d16-moderation-read-bridge`
Frozen executable head: `3a79000eaa139ec107118d3fdb05b29e5e52097c`

## Product checkpoint

The exact executable candidate incorporates the owner-review follow-ups without enabling destructive moderation:

- `/moderate-preview` with no player opens a channel-browse workspace with no player selected;
- no-target channel browse avoids blocking per-author Discord REST member lookups and uses cached member data while mapping messages;
- channel/player selectors and per-message player selection remain available, with double-click player selection restricted to the message author's name or avatar when no target is selected;
- Show Context presents same-channel surrounding messages from all authors rather than collapsing to the selected message;
- Discord replies render a compact preview of the referenced message when Discord supplies it in the read response; activating that preview scrolls to the referenced loaded message or loads same-channel around-message context first when necessary;
- author and message-text filter inputs preserve focus and caret position while their filtered message workspace re-renders, so staff can type full queries continuously;
- paging uses the optimized 50-message path, arbitrary custom punishment durations remain supported, and prior message-menu/selection/product-language hardening remains intact;
- browser moderation remains non-destructive/test-only: D16 does not send punishments/DMs, change Discord permissions, delete messages, change LiteBans authority, or authorize production cutover.

## Exact executable-head gates — PASS

For `3a79000eaa139ec107118d3fdb05b29e5e52097c`:

- Coverage run `34434155437`: **SUCCESS**; clean Java 21 repository build/tests, runtime-JAR inspection, aggregate coverage, validation artifact upload, and Codacy coverage upload completed successfully.
- Moderation Web Validation `34434155472`: **SUCCESS**.
- Staff Bot PR Artifact `34434155529`: **SUCCESS**.
- Staff Bot Configuration Cache `34434155616`: **SUCCESS**.
- Sentinel Restart Artifact `34434155596`: **SUCCESS**.
- Pi Staging Supersession `34434153678`: **SUCCESS**.
- Protected Moderation Web Staging Deploy `34434152711`: **SUCCESS** on the exact executable head.
- Codacy Static Code Analysis `102735847147`: **SUCCESS**, zero annotations / zero new valid findings.
- Codacy Diff Coverage `102739125713`: **SUCCESS**, 52.54% diff coverage.
- Codacy Coverage Variation `102739126122`: **SUCCESS**, +0.04% against the -1.0% target.
- CodeRabbit exact-head status: **SUCCESS**.

## Exact StaffBot artifact

Use Staff Bot PR Artifact run `34434155529`:

- artifact id `10135569887`;
- artifact name `staff-bot-pr-187-3a79000eaa139ec107118d3fdb05b29e5e52097c`;
- artifact ZIP digest `sha256:2eb7b2645110dac922aeb1e8f1d4d04ab510162e9e7d8ccb9d3ab0fa8c203498`;
- source marker `3a79000eaa139ec107118d3fdb05b29e5e52097c`;
- contained `EnthusiaStaff-StaffBot.jar` SHA-256 `1f61d87b71341c4d9fe1d4702b164862359b29ea0808ce5daf7afa135dc6ecb3`.

Independent artifact inspection confirmed the source marker, packaged checksum, runtime manifest/JDA classes, and the current browse/reply/filter-focus resources.

## Moving-main state

Canonical `main` moved after the executable candidate was frozen. At this checkpoint it is `06519c0c5acdcf6276278204201f3c8b20767805`. The owner live test intentionally precedes final moving-main reconciliation; after live acceptance, the same D16 continuation must merge the then-current `main` normally, preserve concurrent work, rerun every gate invalidated by the resulting executable head, and only then merge PR #187.

## Current blocker / exact unblock

The sole current blocker is owner-operated **StaffBot-only live acceptance** on authorized non-production Bloom staging. The protected website deployment is already current. The connected worker has no authenticated Bloom/Pterodactyl mutation surface.

1. Replace only Bloom `EnthusiaStaff-StaffBot.jar` with artifact `10135569887`, or the contained JAR matching the SHA-256 above.
2. Restart StaffBot only, preserving existing runtime files and flags. Do not replace or restart Paper.
3. Open a fresh `/moderate-preview` launch.
4. Verify no-player launch speed; channel switching/player selection; Show Context with surrounding all-author messages; compact reply previews and click-to-jump/context-load behavior; double-click selection only from author name/avatar; continuous author/text filter typing; prior message selection/menu behavior; custom punishment durations; and the non-destructive final-review boundary.
5. Publish only sanitized acceptance evidence. Do not expose signed launch material, credentials, private request data, raw private messages, or secrets.

After sanitized acceptance succeeds, resume this same package as the higher-priority `ACTIONABLE_CONTINUATION`, reconcile moving `main`, rerun invalidated exact-head gates, merge #187 normally only when green, prove containment/cleanup, publish `COMPLETE`, and stop without starting another package.

## Containment

ES-D13 PR #178 and ES-X03 PR #139 remain separate and untouched. ES-D07 was not started. LiteBans remains authoritative. No production Discord configuration change, destructive moderation, live deletion, issue #43 acceptance, or cutover is authorized by D16.
