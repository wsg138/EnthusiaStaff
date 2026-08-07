# EnthusiaStaff workspace state

Last updated: 2026-08-06

Live GitHub state overrides stale records, but persistent package state must be published to `main`.

## Current routing

| Field | Value |
| --- | --- |
| Completed packages | `ES-P01 — Exact-sanction appeal isolation`; `ES-P03 — Bedrock identity correctness` |
| Parked package | `ES-P02 — Runtime database recovery and Velocity reload` |
| Active recovery package | `ES-X05 — Website UX, authentication, and appeals` |
| ES-P02 status | `BLOCKED` / `PARKED_BLOCKED`; branch `package/es-p02-runtime-db-recovery`; PR #70; records head `99da4103773e0c2ae43e0b0253200cd0d3d2c65c` |
| ES-P02 hosted evidence | exact product head `d671fef9fd14f0c4ae711c83edb29bc9b08ea002`; Coverage run `31138550369`, job `92743341861`, success |
| ES-P02 blocker | latest private staging run `31139079620`; Ubuntu build job `92744901730` received runner ID `0`, empty runner name, steps `[]`, and GitHub Billing & plans payment/spending-limit failure; Pi job `92744908539` skipped |
| ES-X05 status | `MERGE_PENDING` / `ACTIONABLE_CONTINUATION`; branch `package/es-x05-finalization`; PR #74 |
| ES-X05 actionability | public Ubuntu hosted runners materially recovered, proven by ES-P02 Coverage run `31138550369`; ES-P02 private-repository billing remained unchanged |
| ES-X05 implementation | aggregate PR #73 merge `345b7bbcec6facb45c7f96b0e6e181ac7a38e1da`; standalone package work through PR #2 merge `b385f78c522f452cc48d78ed19fd2ee82573f64d` |
| ES-X05 live standalone reconciliation | standalone `main` advanced to `2fe7d59c1c5e12db0b7ba792fc9e2af4d24337c2` via PR #3; its sole product delta removes `functions/_middleware.js`, and the same deletion is synchronized in PR #74 |
| ES-X05 staging disposition | **OWNER-APPROVED INFRASTRUCTURE EXCEPTION — STAGING DEFERRED** to `ES-V02`; not a pass and not rerun by this recovery worker |
| ES-P03 status | `COMPLETE`; product head `15608bc3099dc34aa080c80ca8e824ffd51cdae4`; merge `b960e91ea59627a870ff24f89c2f761d0cbb68ab` |
| ES-P03 validation | Coverage `31133176482` / `92726659126`; Wiki `31133176536` / `92726609318`; CodeRabbit success; Codacy 0 issues |
| Migration boundary | immutable V17; V1–V17 unchanged by this recovery reconciliation |
| Active implementation package | exactly `ES-X05`; no PLANNED/READY package activated |
| Production boundary | issue #43 remains open and deferred; LiteBans remains authoritative |

## ES-X05 recovery checkpoint

- Starting aggregate `main`: `9b1aac2677049ccc71dbddd963831f270c73dcd0`.
- Starting finalization head: `96bf9ab21b114a4523582a5ca267e6c1d1370cb1`.
- Current `main` was merged normally into the finalization branch; ES-P03 state was preserved.
- Live standalone PR #3 exposed a real ES-X05 routing defect: page-level middleware redirected the intended public-but-unlinked appeal/reviewer pages when Access claims or login configuration were unavailable. API authentication and reviewer authorization remain enforced. The exact one-file standalone fix is mirrored into the aggregate component copy.
- Exact-head hosted Coverage, static analysis, review, artifact/provider-leak gates, Markdown/package checks, mergeability, and deterministic component parity must pass before PR #74 merges.
- Private/Pi staging remains deferred to `ES-V02` under the existing owner approval and is never called passed.

## Safety boundaries

No production credentials, accounts, punishment/player records, raw addresses, private databases, production routes, deployment, Flyway repair/history rewrite, LiteBans removal, issue #43 acceptance, production migration, shadow window, cutover, or authority activation is authorized or performed.