# Component metadata — enthusia-currency

| Field | Value |
| --- | --- |
| Component ID | `COMP-CURRENCY` |
| Standalone repository | `wsg138/EnthusiaCurrency` |
| Standalone default branch | `main` |
| Aggregate path | `components/enthusia-currency/` |
| Verified standalone head at setup | `9696501a01cc11f6e5220c5297a6f34b64204e61` |
| Last synchronized external SHA | `b922c5af30860a6c205f9ee16b817349a7677cd0` |
| Last synchronized aggregate-main SHA | `a3b6f2f7c1e9f6b7fe1667974aa0d050533605a9` |
| Synchronization state | `IN_SYNC` |
| Last verified content hash | `d6797acbd50bb6547ce724bff946974872795e9f2343c664c2c9e8bde28e5e2c` on both standalone and aggregate |
| Content-hash method | `tools/component-sync/component_sync.py`; SHA-256 over sorted POSIX paths and raw bytes; `COMPONENT-METADATA.md` excluded as aggregate-only orchestration metadata |
| Current blockers | None for ES-X02. Representative live destructive-balance acceptance remains assigned to `ES-V03`. |

Post-merge parity was verified with `tools/component-sync/component_sync.py compare` between aggregate merge `a3b6f2f7c1e9f6b7fe1667974aa0d050533605a9` and standalone Currency `main` `b922c5af30860a6c205f9ee16b817349a7677cd0`. The comparison reported `parity: true` with zero added, missing, or modified product files. Evidence: `ai-agents/reports/package-handoffs/2026-08-13-es-x02-component-parity.json`.
