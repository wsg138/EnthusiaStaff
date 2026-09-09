# ES-D16 StaffBot live-acceptance blocker — 2026-09-09

Package: `ES-D16 — Moderation console real-data read bridge`
Status: `BLOCKED` / `PARKED_BLOCKED`
PR: #187, open/unmerged
Branch: `package/es-d16-moderation-read-bridge`
Frozen executable head: `8bef6775d411ac0c22321b0edd0485a0f9c84d5e`
Base `main` at freeze: `423e72c764c9acfce6bb80918f07367fea2cfccf`

## Product checkpoint

D16 is fully implemented and repository-ready at the frozen executable head. Owner review found and fixed one deployment-packaging defect after the prior checkpoint: the newer moderation usability assets existed in StaffBot resources but were omitted from `moderation-web/scripts/build.mjs`, so Cloudflare continued serving older page behavior. The frozen head now copies every local asset referenced by the moderation page and has regression coverage that fails if a referenced asset is omitted.

The frozen head preserves channel-bound launches, channel-scoped initial messages, neutral real identity loading, allowlisted linked-account/avatar/skin fields, bounded same-channel ±2-minute context reads, four-page-per-direction limits, non-advancing-cursor rejection, no cross-channel contamination, workflow resume behavior, Discord/chat versus In-game offense separation, and simulation-only destructive-action review.

The final UX hardening provides whole-row message selection; exclusive `•••` menus; outside-click and keyboard dismissal/navigation; message-ID copying from the menu rather than row clutter; clearer author/message hierarchy; cleaned product chrome; search/filter and record-state hardening; and removal of old staging/preview language except for one truthful Testing note at the destructive boundary. The Discord launcher title is now only `@username`; its channel appears once below.

## Exact-head gates — PASS

For `8bef6775d411ac0c22321b0edd0485a0f9c84d5e`:

- Coverage `34364743352` / job `102510774307`: success. Java 21 clean repository build/tests, runtime-JAR inspection, aggregate coverage, validation artifact upload, and Codacy coverage upload passed. Coverage is 52.32% lines / 42.47% branches / 54.56% instructions.
- Coverage artifact `10109772008`; digest `sha256:289fc8b3df9ef39c7a17035f0772eb280b5970c20c149a455e2e76fea09e8679`.
- Moderation Web Validation `34364743253`: success.
- Staff Bot PR Artifact `34364743331`: success.
- Staff Bot Configuration Cache `34364743259`: success.
- Sentinel Restart Artifact `34364743324`: success.
- Moderation Web Staging Deploy `34364735886`: success on the exact frozen head, including validation, corrected Worker deployment, permanent-origin verification, and signed launch/direct-read/replay checks.
- Pi Staging Supersession `34364738724`: success on the exact frozen head.
- Codacy Static Code Analysis `102510993058`: success, zero annotations / zero new valid findings.
- Codacy Diff Coverage `102514180788`: success at 51.92% (no repository gate defined).
- Codacy Coverage Variation `102514180125`: success at +0.01% against the -1.0% target.
- `main` `423e72c764c9acfce6bb80918f07367fea2cfccf` remains fully contained; PR #187 remains open, non-draft, unmerged, and mergeable.

## Exact StaffBot artifact and provenance

Use Staff Bot PR Artifact run `34364743331`:

- artifact id `10109369813`;
- artifact name `staff-bot-pr-187-8bef6775d411ac0c22321b0edd0485a0f9c84d5e`;
- artifact ZIP digest `sha256:c420d192f2a145e838fb50fbe6517688213ac76466b07afcceefa935de19d402`;
- `source.txt` source marker `8bef6775d411ac0c22321b0edd0485a0f9c84d5e`;
- contained `EnthusiaStaff-StaffBot.jar` SHA-256 `bb1acb90fffd7330c82063e5cd6bd4749d5cfef08cb732bca7b438769cf89c67`.

Independent download verification confirmed the ZIP and contained JAR hashes. This artifact supersedes the earlier `f2b901f...` owner-test artifact because the Discord launcher presentation changed after owner review.

## Current external blocker

The historical Paper migration/classloader blocker is resolved and superseded. The current gate does **not** require Paper replacement or restart.

The only remaining gate is owner-operated **StaffBot-only live acceptance** on authorized non-production Bloom staging. The Worker-side website repair is already deployed to the protected staging origin; the connected worker still has no authenticated Bloom/Pterodactyl mutation surface for replacing/restarting StaffBot itself.

Exact owner-operated unblock:

1. Replace only Bloom `EnthusiaStaff-StaffBot.jar` with artifact `10109369813`, or the contained JAR matching SHA-256 `bb1acb90fffd7330c82063e5cd6bd4749d5cfef08cb732bca7b438769cf89c67`.
2. Restart StaffBot only. Preserve existing runtime files and flags.
3. Do not replace/restart Paper for this acceptance.
4. Open a fresh Discord-generated moderation link from the channel being investigated. Use a fresh link or hard refresh to load the corrected deployed scripts.
5. Verify, in sanitized form: the `@username`-only launcher heading with one channel field; real Discord/Minecraft identity; channel-scoped initial messages; whole-row selection; exclusive/outside-dismissed `•••` menus; message-ID copy from the menu; context/search/filter behavior; workflow resume; final-review validation; History/Cases/Notes states; Discord-vs-In-game offense split; product language; and that destructive moderation remains disabled/test-only.
6. Do not publish signed launch tokens, credentials, authority/component secrets, private request material, actor/guild IDs, raw player rows, or private message contents.

After sanitized acceptance passes, resume this same package/PR as the higher-priority `ACTIONABLE_CONTINUATION`, reconcile moving `main`, rerun any exact-head gates invalidated by changed executable state, merge PR #187 normally only after all required evidence is green, prove containment/cleanup, publish `COMPLETE`, and stop.

## Concurrency and authority containment

ES-D13 PR #178 and ES-X03 PR #139 remain separate and untouched. ES-D07 is not started. LiteBans remains authoritative. No destructive moderation, live message deletion, production Discord configuration change, issue #43 acceptance, cutover, or unrelated package work is authorized or performed.
