# Latest agent handoff

Current handoff: `ES-D16 — Moderation console real-data read bridge` — `BLOCKED` / `PARKED_BLOCKED`.

Canonical package handoff: `ai-agents/reports/package-handoffs/2026-09-10-es-d16-live-acceptance-current.md`.

Current product checkpoint:
- PR #187 remains open/unmerged on `package/es-d16-moderation-read-bridge`;
- frozen executable head is `3a79000eaa139ec107118d3fdb05b29e5e52097c`;
- no-target channel browse avoids blocking per-author Discord REST member retrieval;
- reply rows show compact referenced-message previews when available; activating one scrolls to the referenced message or loads its same-channel surrounding context first;
- when no target is selected, double-clicking only an author's name/avatar selects that player;
- author and text filters preserve focus/caret across workspace renders so complete queries can be typed continuously;
- existing all-author Show Context, channel/player browse, optimized 50-message paging, arbitrary custom punishment durations, message menus/selection, and non-destructive product boundary remain intact;
- exact executable-head Coverage `34434155437`, web validation `34434155472`, StaffBot artifact `34434155529`, configuration-cache `34434155616`, Sentinel `34434155596`, Pi supersession `34434153678`, protected staging `34434152711`, Codacy static `102735847147`, diff coverage `102739125713`, coverage variation `102739126122`, and CodeRabbit all pass; Codacy static has zero annotations / zero new valid findings;
- exact StaffBot artifact id is `10135569887`, ZIP digest `sha256:2eb7b2645110dac922aeb1e8f1d4d04ab510162e9e7d8ccb9d3ab0fa8c203498`, contained JAR SHA-256 `1f61d87b71341c4d9fe1d4702b164862359b29ea0808ce5daf7afa135dc6ecb3`;
- protected Cloudflare staging is already deployed for the exact executable head;
- `main` has moved since executable freeze; at this checkpoint it is `06519c0c5acdcf6276278204201f3c8b20767805`. Final moving-main reconciliation is intentionally deferred until owner live acceptance;
- the only remaining current blocker is owner-operated **StaffBot-only** live acceptance. Replace/restart StaffBot only; do not replace/restart Paper;
- ES-D13 PR #178, ES-X03 PR #139, ES-D07, issue #43, and LiteBans authority remain untouched.

After sanitized StaffBot live acceptance succeeds, resume PR #187 as the same higher-priority `ACTIONABLE_CONTINUATION`; reconcile the then-current `main` normally, preserve concurrent work, rerun every invalidated exact-head gate, merge normally only when green, prove containment/cleanup, publish `COMPLETE`, and stop without starting another package.
