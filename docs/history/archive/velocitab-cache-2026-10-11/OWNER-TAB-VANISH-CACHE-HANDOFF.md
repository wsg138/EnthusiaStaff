> Historical copy from [Staff PR #517](https://github.com/wsg138/EnthusiaStaff/pull/517), commit [`9b5323451645e1a6424fa97cf503f49a5fa2a25f`](https://github.com/wsg138/EnthusiaStaff/commit/9b5323451645e1a6424fa97cf503f49a5fa2a25f), original path [`ai-agents/reports/package-handoffs/2026-10-11-owner-tab-vanish-cache.md`](https://github.com/wsg138/EnthusiaStaff/blob/9b5323451645e1a6424fa97cf503f49a5fa2a25f/ai-agents/reports/package-handoffs/2026-10-11-owner-tab-vanish-cache.md). Preserved for review; **not** current package routing or proof of production acceptance.

# OWNER-TAB-VANISH-CACHE handoff

Base fc813a535a23eabfcb695b5e12a7e9d55c9fb676; branch package/owner-tab-vanish-cache. Owner assigned source repair and TEST acceptance, forbidding prod changes. Package contract carries requirements, evidence and outstanding gates. No previous handoff exists for this assignment.

Baseline expiry regression reproduced ordinary player suppression. Atomic verified snapshot and restricted public-player fallback fix the defect while preserving vanished-staff privacy, unknown-entry rejection, previous-provider restrictions and rank hierarchy. Identity permission checks prevent the exception from treating identity-only staff as ordinary. No migration/provider dependency change. Other user checkouts preserved.

Java25 clean build/runtimeJars with all unit suites passed; integration-tests execution excluded (local Docker unavailable). Focused Velocity matrix passed. Baseline red and subsequent build/test evidence are private operations/tab-vanish-diagnosis-20261011 files.

TEST-only TAB global list disabled after backup; successful TAB reload log at20:53:38 EDT. No players connected, so actual-client A/B acceptance not claimed. No Staff artifact installed because TEST currently shares production proxy. Owner question for isolated TEST proxy remains pending. No production config/plugin/DB, restart, deployment or external messages.

PR476 has separate staff-mode privacy-policy edits in overlapping bridge/presence files; reconcile safely if it lands first. This patch preserves current-main policy. Reviewable PR and exact-head CI follow this checkpoint; no merge or full delivery claimed until remaining runtime/review gates complete.
