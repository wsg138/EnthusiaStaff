# Latest AI handoff

Current package handoff:

[`2026-08-09-es-r01-release-freshness-repaired-public-build-blocked.md`](../package-handoffs/2026-08-09-es-r01-release-freshness-repaired-public-build-blocked.md)

Canonical package registry:

[`PACKAGE-REGISTRY.md`](../../work-packages/PACKAGE-REGISTRY.md)

`ES-R01 — Billing-independent staging bridge recovery` is `BLOCKED` / `PARKED_BLOCKED` after an owner-directed `ACTIONABLE_CONTINUATION` repaired the shared transient-release freshness verifier.

The old MariaDB blocker is stale. Historical ES-P05 proof at source `ebfbaa31d3de2b6a28b9dcbaf2c4366ee8e801e2` reached public run `31301426684` → private run `31301734048` / job `93215499833` on trusted `Lincoln-PI-4` runner ID `2`, with exact provenance, guarded pre-reset, two storage-ready Paper cycles through V18, clean shutdown/reap, restart/persistence, final guarded cleanup and sanitized artifact `9034945235` all successful.

The later ES-P05 failure at public run `31330788773` → private run `31331175023` / job `93289556545` exposed the repository-side defect: `release created_at is expired for the staging bridge`. GitHub Release `created_at` represents the commit date used for the release, not publication time. ES-R01 staging PR #75 therefore changed the release-publication freshness check to required Release `published_at` while retaining Release Asset `created_at`, the existing two-hour maximum age, future-skew guard and all exact provenance/digest/cleanup boundaries.

Staging PR #75 froze at `19e38d6851367d835cfe50fc29e9f95a0936f66d`, passed Staging Controls run `31332576934` / job `93293056853` on `Lincoln-PI-4` including the requested positive/negative freshness/provenance regressions and the broader safety suite, then merged normally as `af1bd6d3ae8214e58eb969c23972f872b15c1f18`. CodeRabbit was green and review threads were zero.

A new blocker is now upstream of the repaired bridge: current public `main` `140d10ef63f3d6761c95afccbead13db53888304` already failed its own canonical Pi build in run `31332055336` / job `93291754833` on two ReportStore integration assertions before any artifact could be produced. ES-R01's documentation-only head reproduced the same failures in Coverage `31332739840` (including an unchanged-head rerun) and canonical Pi run `31333070856` / build job `93294291022`; bridge execution was skipped and no private run was dispatched.

ES-R01 must not change Report Java product/tests or weaken the public build. Exact unblock: material evidence that current `EnthusiaStaff:main` again passes the canonical trusted public Java build, including those two ReportStore integration tests. Then resume ES-R01 first, obtain one fresh exact-current-main public→private provenance/DB/two-cycle Paper/cleanup proof, and mark `COMPLETE` only if that full chain succeeds.

ES-P05 PR #81 remains parked and untouched at `346e764f40b25c98e7d24ce7f863e5629773e814`; the repaired shared bridge does not validate that head. The next sequential worker must reconcile live routing and reconsider ES-P02/ES-P05 according to the current product-side public-build failure. V18 remains immutable/current; issue #43 remains deferred; LiteBans remains authoritative.
