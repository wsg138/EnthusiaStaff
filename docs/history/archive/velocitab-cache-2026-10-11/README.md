# Velocitab stale-cache repair — historical PR #517 evidence

**Source:** [#517](https://github.com/wsg138/EnthusiaStaff/pull/517), reviewed at [`9b5323451645e1a6424fa97cf503f49a5fa2a25f`](https://github.com/wsg138/EnthusiaStaff/commit/9b5323451645e1a6424fa97cf503f49a5fa2a25f) (October 11, 2026 worker record; coordinator archive November 11, 2026). This is **historical provenance**, not an active worker assignment.

## What was implemented

A stale presence cache could incorrectly suppress ordinary online players from Velocitab. The proposed and subsequently merged implementation protects vanished or unknown staff while permitting retention of **previously verified ordinary non-staff** through bounded staleness. A callback reads one immutable verification generation; rank hierarchy and previous-provider restrictions remain authoritative.

**Current source implementation was already merged through [#781](https://github.com/wsg138/EnthusiaStaff/pull/781)**. Independent exact GitHub blob comparison against `main` on November 11 confirmed both `velocity/src/main/java/net/enthusia/staff/velocity/VelocitabStaffBridge.java` and `velocity/src/test/java/net/enthusia/staff/velocity/VelocitabStaffBridgeTest.java` are byte-for-byte identical to the #517 head. No old runtime patch is being merged again.

## Unique source preserved

Two documents only present on the unmerged #517 branch have been copied into this **archive**, retaining their wording with a clearly marked historical header:

- [Original 2026-10-11 handoff](OWNER-TAB-VANISH-CACHE-HANDOFF.md) — failure reproduction, implementation claims, local tests and staging limitations.
- [Original package contract](OWNER-TAB-VANISH-CACHE-CONTRACT.md) — behavior and privacy acceptance gates, baseline verification and TEST-only operations.

The other two changed `ai-agents/` files in #517 are **historical status annotations**, not appropriate replacements for active current package routing:

- [`ai-agents/WORKSPACE-STATE.md` at #517 head](https://github.com/wsg138/EnthusiaStaff/blob/9b5323451645e1a6424fa97cf503f49a5fa2a25f/ai-agents/WORKSPACE-STATE.md) prepended the 2026-10-11 owner assignment/status note.
- [`ai-agents/work-packages/PACKAGE-REGISTRY.md` at #517 head](https://github.com/wsg138/EnthusiaStaff/blob/9b5323451645e1a6424fa97cf503f49a5fa2a25f/ai-agents/work-packages/PACKAGE-REGISTRY.md) prepended the matching `OWNER-TAB-VANISH-CACHE` package-status note.

These exact dated variants remain pinned by SHA, and the original PR preserves comments, review and commits. They should **not** overwrite `main`'s actively used workspace/registry files, which are consumed by component-sync/orchestration tooling.

## Remaining acceptance

The PR record confirms local focused tests, but not hosted MariaDB acceptance or live two-client delayed-refresh A/B verification. TEST shared the production Velocity proxy, so client acceptance was explicitly deferred; no production installation was performed in #517. If live validation is still needed, track it separately from this duplicate PR closure.

**Archive rule:** this record preserves old design and evidence. For current behavior, consult merged `main`, [#781](https://github.com/wsg138/EnthusiaStaff/pull/781), current module tests and verified production status. No live server, Velocity, TAB, Cloudflare or database state was touched during archiving.
