# Latest AI handoff

Current persistent package handoff:

[`2026-08-06-es-p03-bedrock-identity.md`](../package-handoffs/2026-08-06-es-p03-bedrock-identity.md)

Canonical package registry:

[`PACKAGE-REGISTRY.md`](../../work-packages/PACKAGE-REGISTRY.md)

Current state: `ES-P01` is `COMPLETE`; `ES-P02 — Runtime database recovery and Velocity reload` and `ES-X05 — Website UX, authentication, and appeals` are `BLOCKED` / `PARKED_BLOCKED`; `ES-P03 — Bedrock identity correctness` is the only `ACTIVE` implementation package.

The owner explicitly directed the sequential worker to continue another productive package while leaving ES-P02 and ES-X05 parked until GitHub-hosted runners recover. Because no ordinary implementation package was dependency-complete, the worker recorded a narrow routing exception and selected ES-P03 from current legitimate `main` `345b7bbcec6facb45c7f96b0e6e181ac7a38e1da`. This does not mark ES-P02 complete, import its unmerged lifecycle/reload work, waive ES-P03 validation, or authorize any later package.

Preserved parked work remains untouched:

- ES-P02 branch `package/es-p02-runtime-db-recovery`, PR #70, package-record head `80d4ea840f34017c09afb618f623581b31c6223d`.
- ES-X05 branch `package/es-x05-finalization`, PR #74, head `96bf9ab21b114a4523582a5ca267e6c1d1370cb1`.

ES-P03 branch: `package/es-p03-bedrock-identity`. Confirmed defects are unconditional Java platform writes in Paper mute join and Velocity backend connect, rejection of configured `*` Bedrock aliases, and platform upserts that can downgrade stronger identity evidence. The package owns verified platform observations, deterministic current/history alias handling, and duplicate/out-of-order persistence safety. ES-P09 retains alt graph/inheritance and ES-V02 retains representative Java/Bedrock staging.

LiteBans remains authoritative; no production account, credential, data, route, issue #43 action, cutover, or authority activation is authorized.
