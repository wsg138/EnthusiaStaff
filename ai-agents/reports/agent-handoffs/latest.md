# Latest AI handoff

Current persistent package handoff:

[`2026-08-06-es-x05-website-auth-appeals.md`](2026-08-06-es-x05-website-auth-appeals.md)

Canonical package registry:

[`PACKAGE-REGISTRY.md`](../../work-packages/PACKAGE-REGISTRY.md)

Current recovery state: `ES-P01` and `ES-P03` are `COMPLETE`; `ES-P02 — Runtime database recovery and Velocity reload` is `BLOCKED` / `PARKED_BLOCKED`; `ES-X05 — Website UX, authentication, and appeals` is the selected `MERGE_PENDING` / `ACTIONABLE_CONTINUATION`. No PLANNED/READY package is active.

ES-P02 is not actionable: exact product head `d671fef9fd14f0c4ae711c83edb29bc9b08ea002` passed Coverage `31138550369` / `92743341861`, but latest private run `31139079620` again failed before runner allocation in build job `92744901730` with GitHub's Billing & plans payment/spending-limit restriction; Pi job `92744908539` was skipped. Its branch remains `package/es-p02-runtime-db-recovery`, PR #70, records head `99da4103773e0c2ae43e0b0253200cd0d3d2c65c`.

ES-X05 became actionable because that successful public Coverage run proves ordinary public Ubuntu runner recovery. PR #74 is being reconciled against starting aggregate `main` `9b1aac2677049ccc71dbddd963831f270c73dcd0`, preserving ES-P03 completion and current P02 facts.

Live standalone reconciliation also found PR #3, merged as standalone `main` `2fe7d59c1c5e12db0b7ba792fc9e2af4d24337c2`. Its sole product delta removes page-level appeal/reviewer middleware that caused public-but-unlinked pages to redirect. The exact deletion is mirrored into `components/enthusia-site/`; API authentication and reviewer authorization remain protected. PR #3 exact head passed its site test workflow, both Cloudflare deployments, Codacy, and had zero review threads.

The owner-approved ES-X05 private/Pi staging deferral to `ES-V02` remains valid, deferred, and not passed. Freeze PR #74, run every applicable exact-head hosted/review/static/package/parity gate, merge normally only if they pass, verify containment, publish `COMPLETE`, clean safely, and stop without selecting another package.

LiteBans remains authoritative; issue #43 and production activation remain deferred.