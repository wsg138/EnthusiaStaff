# Component metadata — enthusia-market

| Field | Value |
| --- | --- |
| Component ID | `COMP-MARKET` |
| Standalone repository | `wsg138/EnthusiaMarket` |
| Standalone default branch | `main` |
| Aggregate path | `components/enthusia-market/` |
| Verified standalone head at setup | `bc24f1010642d6042307bc13a32fb33cc94e8883` |
| Last synchronized external SHA | `ffb6d0b63714671aa4418ac56a658a5d861d7678` |
| Last synchronized aggregate-main SHA | `PENDING_ES_X03_MERGE` |
| Synchronization state | `SYNC_PENDING` |
| Product-tree hash | `PENDING_FINAL_CANONICAL_HASH` — the prior `8d27f4d9c64ca52feecd1df6200a45314610fa0df4b27da9d39b444152007c3b` applied to obsolete external candidate `62408695063d03303026766befb065a0f1f51044` and is not reused after later executable changes. |
| Current parity evidence | Git object content identity verifies the current aggregate product files match standalone `ffb6d0b63714671aa4418ac56a658a5d861d7678`, excluding only aggregate-only `COMPONENT-METADATA.md`. The current `src/` tree is `ad3b488e0ccf2f1e52d63f761edf5e71aac4db9f` in both copies and `src/test/` is `e1273515aa576bab2a23c962a8c2037222458f8e` in both. `gradlew` bytes are identical; its Git executable bit differs, which the canonical content comparator intentionally does not hash. |
| Content-hash method | `tools/component-sync/component_sync.py`; SHA-256 over sorted POSIX paths and raw bytes; `COMPONENT-METADATA.md` excluded as aggregate-only orchestration metadata. File modes are not part of the canonical content hash. |
| Current blockers | Exact-head ordinary GitHub Actions validation is unavailable in `wsg138/EnthusiaMarket`; its Actions history contains no runs and the connected GitHub worker has no workflow-dispatch path. Final canonical SHA-256 rerun, paired normal merges, and post-merge parity therefore remain pending. |

The aggregate product tree is synchronized to the current standalone ES-X03 head above. No permanent component branch or isolated-component PR is required. The package must not treat the obsolete `6240869` hash as final evidence.
