# ES-D16 StaffBot live-acceptance blocker — 2026-09-09

Package: `ES-D16 — Moderation console real-data read bridge`
Status: `BLOCKED` / `PARKED_BLOCKED`
PR: #187, open/unmerged
Branch: `package/es-d16-moderation-read-bridge`
Frozen executable head: `f2b901f731558224e2df6ee1d8ed38d06063a150`
Base `main` at freeze: `423e72c764c9acfce6bb80918f07367fea2cfccf`

## Product checkpoint

D16 is fully implemented and repository-ready at the frozen executable head. The current UI and read bridge include the requested usability/product-language hardening while preserving D16's fail-closed read and non-destructive boundaries.

The frozen head preserves channel-bound launches, channel-scoped initial messages, neutral real identity loading, allowlisted linked-account/avatar/skin fields, bounded same-channel ±2-minute context reads, four-page-per-direction limits, non-advancing-cursor rejection, no cross-channel contamination, workflow resume behavior, Discord/chat versus In-game offense separation, and simulation-only destructive-action review.

The final UX hardening also provides whole-row message selection; exclusive `•••` menus; outside-click and keyboard dismissal/navigation; message-ID copying from the menu rather than row clutter; clearer author/message hierarchy; cleaned Discord launcher/product chrome; and removal of user-visible staging/preview/simulation labels across active and fallback layers except for one truthful Testing note that states the environment does not send punishments or DMs, change Discord permissions, or delete messages.

## Exact-head gates — PASS

For `f2b901f731558224e2df6ee1d8ed38d06063a150`:

- Coverage `34354348704` / job `102475585346`: success. Java 21 clean repository build/tests, runtime-JAR inspection, aggregate coverage, validation artifact upload, and Codacy coverage upload passed.
- Coverage artifact `10105492106`; digest `sha256:0d6729f201ff17b875b763edbc7022c28d00a75c13ce859cd118330ff56d257f`.
- Moderation Web Validation `34354348838`: success.
- Staff Bot PR Artifact `34354348823`: success.
- Staff Bot Configuration Cache `34354348705`: success.
- Sentinel Restart Artifact `34354348794`: success.
- Moderation Web Staging Deploy `34354344064`: success on the exact frozen head.
- Pi Staging Supersession `34354346426`: success on the exact frozen head.
- Codacy Static Code Analysis `102475650447`: success, zero annotations / zero new valid findings.
- All visible PR #187 inline review threads are resolved.
- Live reconciliation at freeze proves `main` `423e72c764c9acfce6bb80918f07367fea2cfccf` is the merge base, the branch is zero behind, and PR #187 is open, non-draft, unmerged, and mergeable.

## Exact StaffBot artifact and independent provenance inspection

Use Staff Bot PR Artifact run `34354348823`:

- artifact id `10105100891`;
- artifact name `staff-bot-pr-187-f2b901f731558224e2df6ee1d8ed38d06063a150`;
- artifact ZIP digest `sha256:15704b385d674e5c1536f1c78b8d4cc0d24094580aee74eb1211e76bd71b33ea`;
- `source.txt` source marker `f2b901f731558224e2df6ee1d8ed38d06063a150`;
- contained `EnthusiaStaff-StaffBot.jar` SHA-256 `a496a340e3bfbe76c7db2da146f021b7b9dce4e3275b2ae55bb42fc1eb5474e0`.

Independent archive inspection verified:

- downloaded ZIP SHA matches the GitHub artifact digest;
- source marker is the exact frozen head;
- contained JAR SHA matches its checksum file;
- manifest Main-Class is `net.enthusia.staff.discordbot.StaffBotApplication`;
- JDA and expected StaffBot runtime classes are packaged;
- required moderation assets, including context policy/pagination and final usability/review layers, are packaged;
- `RiverAsh`, `RiverAshMC`, and `sample-river-ash` are absent;
- old visible `STAGING PREVIEW`, `moderation preview`, `Confirm preview`, `Action preview`, `DM preview`, `Simulated deletions`, `simulation preview`, and `Real data, simulated actions` copy is absent from the active product assets;
- exactly one literal `Testing note` remains;
- browser mutation traffic remains limited to the deliberate non-destructive `/api/simulate` path; other inspected calls are read/session paths.

## Current external blocker

The historical Paper migration/classloader blocker is resolved and superseded. The current gate does **not** require Paper replacement or restart.

The only remaining gate is owner-operated **StaffBot-only live acceptance** on authorized non-production Bloom staging. The worker has no authenticated Bloom/Pterodactyl mutation surface and cannot safely perform that deployment.

Exact owner-operated unblock:

1. Replace only Bloom `EnthusiaStaff-StaffBot.jar` with artifact `10105100891`, or the contained JAR matching SHA-256 `a496a340e3bfbe76c7db2da146f021b7b9dce4e3275b2ae55bb42fc1eb5474e0`.
2. Restart StaffBot only. Preserve existing runtime files and flags.
3. Do not replace/restart Paper for this acceptance.
4. Open a fresh Discord-generated moderation link from the channel being investigated.
5. Verify, in sanitized form: real Discord/Minecraft identity; channel-scoped initial messages; avatar/action/context UX; whole-row/menu behavior; punishment workflow resume; Discord-vs-In-game offense split; Enthusia branding/product language; and that destructive moderation remains disabled/test-only.
6. Do not publish signed launch tokens, credentials, authority/component secrets, private request material, actor/guild IDs, raw player rows, or private message contents.

After sanitized acceptance passes, resume this same package/PR as the higher-priority `ACTIONABLE_CONTINUATION`, reconcile moving `main`, rerun any exact-head gates invalidated by changed executable state, merge PR #187 normally only after all required evidence is green, prove containment/cleanup, publish `COMPLETE`, and stop.

## Concurrency and authority containment

ES-D13 PR #178 and ES-X03 PR #139 remain separate and untouched. ES-D07 is not started. LiteBans remains authoritative. No destructive moderation, live message deletion, production Discord configuration change, issue #43 acceptance, cutover, or unrelated package work is authorized or performed.
