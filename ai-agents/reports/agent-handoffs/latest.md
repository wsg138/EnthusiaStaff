# Latest agent handoff

Current handoff: `ES-D16 — Moderation console real-data read bridge` — `BLOCKED` / `PARKED_BLOCKED`.

Canonical package handoff: `ai-agents/reports/package-handoffs/2026-09-09-es-d16-staffbot-live-acceptance-blocked.md`.

Current product checkpoint:
- PR #187 remains open/unmerged and mergeable on `package/es-d16-moderation-read-bridge`;
- frozen executable head is `8bef6775d411ac0c22321b0edd0485a0f9c84d5e`; current state-only head is this file's containing commit and differs after the product freeze only in `ai-agents` Markdown records;
- owner review exposed and repaired a real Cloudflare packaging defect: the latest usability scripts were present in StaffBot resources but omitted from the Worker build. The build now copies every page-referenced local asset and regression coverage guards that contract;
- the Discord launcher now has an `@username`-only title and shows the channel once below;
- exact frozen-head Coverage `34364743352`, web validation `34364743253`, StaffBot artifact `34364743331`, configuration-cache `34364743259`, Sentinel artifact `34364743324`, protected staging `34364735886`, Pi supersession `34364738724`, Codacy static `102510993058`, diff coverage `102514180788`, and coverage variation `102514180125` all pass; Codacy static has zero annotations / zero new valid findings;
- exact StaffBot live-test artifact id is `10109369813`, ZIP digest `sha256:c420d192f2a145e838fb50fbe6517688213ac76466b07afcceefa935de19d402`, contained JAR SHA-256 `bb1acb90fffd7330c82063e5cd6bd4749d5cfef08cb732bca7b438769cf89c67`;
- the corrected website bundle is already deployed to protected `staff-staging.enthusia.info`; a fresh moderation link or hard refresh loads the new scripts;
- `main` remains `423e72c764c9acfce6bb80918f07367fea2cfccf` and is fully contained in the product branch history;
- the historical Paper migration/classloader blocker is resolved and superseded; **Paper does not need replacement or restart** for the current gate;
- the only remaining blocker is owner-operated **StaffBot-only** live acceptance on authorized non-production Bloom staging: replace only the StaffBot JAR with artifact `10109369813`, restart StaffBot only with existing runtime files/flags, open a fresh Discord-generated moderation link, and verify the corrected launcher plus real identity/channel/message/context/search/workflow/final-review/product-language behavior while destructive actions remain disabled;
- D07/D13, PR #178, PR #139, issue #43, LiteBans authority, and unrelated work remain untouched.

When sanitized StaffBot live acceptance succeeds, resume PR #187 as the same higher-priority `ACTIONABLE_CONTINUATION`; reconcile moving `main`, rerun any invalidated exact-head gates, merge normally only when green, prove containment/cleanup, publish `COMPLETE`, and stop without starting another package.
