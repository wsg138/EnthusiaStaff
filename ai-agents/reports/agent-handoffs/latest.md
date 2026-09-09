# Latest agent handoff

Current handoff: `ES-D16 — Moderation console real-data read bridge` — `BLOCKED` / `PARKED_BLOCKED`.

Canonical package handoff: `ai-agents/reports/package-handoffs/2026-09-09-es-d16-staffbot-live-acceptance-blocked.md`.

Current product checkpoint:
- PR #187 remains open/unmerged and mergeable on `package/es-d16-moderation-read-bridge`;
- frozen executable head is `f2b901f731558224e2df6ee1d8ed38d06063a150`;
- exact frozen-head Coverage `34354348704`, web validation `34354348838`, StaffBot artifact `34354348823`, configuration-cache `34354348705`, Sentinel artifact `34354348794`, protected staging `34354344064`, Pi supersession `34354346426`, and Codacy static check `102475650447` all pass; Codacy has zero annotations / zero new valid findings;
- exact StaffBot live-test artifact id is `10105100891`, ZIP digest `sha256:15704b385d674e5c1536f1c78b8d4cc0d24094580aee74eb1211e76bd71b33ea`, contained JAR SHA-256 `a496a340e3bfbe76c7db2da146f021b7b9dce4e3275b2ae55bb42fc1eb5474e0`;
- independent artifact inspection confirms exact source provenance, runtime/JDA packaging, required moderation assets, no sample identity, no old visible staging/preview copy, one truthful Testing note, and simulation-only browser mutation;
- all visible PR #187 inline review threads are resolved;
- `main` remains `423e72c764c9acfce6bb80918f07367fea2cfccf` and is fully contained in the frozen branch head;
- the historical Paper migration/classloader blocker is resolved and superseded; **Paper does not need replacement or restart** for the current gate;
- the only remaining blocker is owner-operated **StaffBot-only** live acceptance on authorized non-production Bloom staging: replace only the StaffBot JAR with artifact `10105100891`, restart StaffBot only with existing runtime files/flags, open a fresh Discord-generated moderation link, and verify sanitized real identity/channel/context/workflow/offense-split/product-language behavior while destructive actions remain disabled;
- D07/D13, PR #178, PR #139, issue #43, LiteBans authority, and unrelated work remain untouched.

When sanitized StaffBot live acceptance succeeds, resume PR #187 as the same higher-priority `ACTIONABLE_CONTINUATION`; reconcile moving `main`, rerun any invalidated exact-head gates, merge normally only when green, prove containment/cleanup, publish `COMPLETE`, and stop without starting another package.
