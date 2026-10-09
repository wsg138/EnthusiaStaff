# Latest agent handoff

Current coordinator handoff: **Configurability / repository cleanup / production acceptance — ACTIONABLE_CONTINUATION**.

Canonical handoff:

[2026-10-07-configurability-cleanup-coordinator.md](2026-10-07-configurability-cleanup-coordinator.md)

At handoff freeze:

- live `main`: `444c44a6f3868c66f5eff605d36c099cc8c0da06`;
- configurability parent: #425;
- cleanup parent: #426;
- C0 PR #428 is merged as `e41062728131fafa93c7eb639435607ce0fc8e53`;
- C1 tracker: #436;
- C1 PR #449 is OPEN/DRAFT on `config/c1-messages-foundation`;
- C1 exact head: `06c2d32b7761b7ac834b36d20445bfb865eee94f`;
- focused C1 tests pass locally on the current-main integration;
- hosted #449 checks/review must be read live before promotion/merge.

Do not overlap active Policy v2 PR #452 or the separate Discord/DiscordSRV workstreams.

Remaining hands-on acceptance includes #350 Spectator noclip, #392 HUB ProtocolLib warning proof, #395 Helper firework/projectile pass-through, and #343 after upstream LumaGuilds #209 merges.

The repository is live production software. Historical pre-release/LiteBans records remain historical evidence; current-facing docs should not present them as current authority.

Live GitHub is authoritative. Read the canonical handoff and the live PR/issue state before acting.


## Owner fork upstream delivery, 2026-10-08

Owner explicitly assigned continuation of `owner-punish-confirm-player` to merge validated fork PRs and submit remaining source fixes to canonical main. Fork PR1 and PR14 are merged; upstream reconciliation is REVIEW / ACTIONABLE_CONTINUATION in canonical PR #476 on `package/owner-upstream-remaining-20261008`. Current canonical bb8156ce is preserved, including rank policy, preferences, configured messages, recovery and authority controls. See `ai-agents/reports/package-handoffs/2026-10-08-owner-upstream-reconciliation.md` for bounded SPEAR requirements and evidence. Other packages are not reassigned. Upstream #339 remains the separate offline Discord mute submission. No production actions or canonical product merge authorized.
