# Component metadata — enthusia-site

| Field | Value |
| --- | --- |
| Component ID | `COMP-SITE` |
| Standalone repository | `wsg138/enthusia-site` |
| Standalone default branch | `main` |
| Aggregate path | `components/enthusia-site/` |
| Prior synchronized external SHA | `b385f78c522f452cc48d78ed19fd2ee82573f64d` |
| Current standalone SHA | `2fe7d59c1c5e12db0b7ba792fc9e2af4d24337c2` via PR #3 |
| Prior synchronized aggregate-main SHA | `345b7bbcec6facb45c7f96b0e6e181ac7a38e1da` |
| Recovery aggregate base | `9b1aac2677049ccc71dbddd963831f270c73dcd0` |
| Synchronization delta | standalone PR #3 removes `functions/_middleware.js`; same path removed from aggregate component copy on PR #74 |
| Synchronization state | `PENDING_EXACT_FINALIZATION_HEAD_PARITY_RECHECK` |
| Current package | `ES-X05 — MERGE_PENDING / ACTIONABLE_CONTINUATION` |
| Previous content hash | `9910dc90d22be68bf034f03def0cabd617bdf2e9953f87231f11af1166fc07e2` |
| Content-hash method | `tools/component-sync/component_sync.py`; SHA-256 over sorted POSIX paths and raw bytes; `COMPONENT-METADATA.md` excluded as aggregate-only orchestration metadata |
| Parity evidence | `ai-agents/reports/package-handoffs/2026-08-06-es-x05-component-parity.json` |
| Staging disposition | **OWNER-APPROVED INFRASTRUCTURE EXCEPTION — STAGING DEFERRED** to `ES-V02`; not a pass |
| Finalization branch / PR | `package/es-x05-finalization`; PR #74 |

The finalization branch must re-run deterministic component parity against standalone `main` after the synchronized deletion and before merge. No permanent component branch or isolated-component PR exists or is required.