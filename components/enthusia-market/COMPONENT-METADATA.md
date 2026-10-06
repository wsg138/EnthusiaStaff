# Component metadata — enthusia-market

| Field | Value |
| --- | --- |
| Component ID | `COMP-MARKET` |
| Standalone repository | `wsg138/EnthusiaMarket` |
| Standalone default branch | `main` |
| Aggregate path | `components/enthusia-market/` |
| Verified standalone head at setup | `bc24f1010642d6042307bc13a32fb33cc94e8883` |
| Last synchronized external SHA | `755d81042a9a53aee7184cce8eb2bf256fe7165c` |
| Last synchronized aggregate-main SHA | `PENDING_ES_X03_MERGE` |
| Synchronization state | `SYNC_PENDING` |
| Product-tree hash | `999fc8b40c7a808d417aab33b46f66f01598664f205c13854c145c72c7ba7f03` |
| Current parity evidence | Reconciliation run `36744990024` compared this aggregate component against standalone Market `755d81042a9a53aee7184cce8eb2bf256fe7165c` with `tools/component-sync/component_sync.py` and reported no added, missing, or modified product path. Aggregate and standalone hashes were both `999fc8b40c7a808d417aab33b46f66f01598664f205c13854c145c72c7ba7f03`. |
| Content-hash method | `tools/component-sync/component_sync.py`; SHA-256 over sorted POSIX paths and raw bytes; `COMPONENT-METADATA.md` excluded as aggregate-only orchestration metadata. File modes are not part of the canonical content hash. |
| Current blockers | Exact final PR-head hosted validation remains required after promotion: Coverage/build, Codacy static analysis with zero new valid findings, review/Sentinel gates, canonical private staging evidence where applicable, normal merge, and post-merge parity. |

The aggregate product tree is synchronized to the current authoritative ES-X03 standalone candidate above, excluding only aggregate-only `COMPONENT-METADATA.md`. Market PR #7 is retired and is not a source of truth. Preserved unpaired Market PR #6 remains separate from this paired package.
