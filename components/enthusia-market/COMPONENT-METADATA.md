# Component metadata — enthusia-market

| Field | Value |
| --- | --- |
| Component ID | `COMP-MARKET` |
| Standalone repository | `wsg138/EnthusiaMarket` |
| Standalone default branch | `main` |
| Aggregate path | `components/enthusia-market/` |
| Verified standalone head at setup | `bc24f1010642d6042307bc13a32fb33cc94e8883` |
| Last synchronized external SHA | `3818233cea1aeace0ddba0ea845188a3a3e94a35` |
| Last synchronized aggregate-main SHA | `PENDING_ES_X03_MERGE` |
| Synchronization state | `SYNC_PENDING` |
| Product-tree hash | `89afa19c977a639b2714d6d2dae318a9e0bade05bbc8a88e76e29f9027883d4b` |
| Current parity evidence | Validation run `37028116267` compared Staff product head `6bc16ab72862a88691ecdf1402d60a909c58c3be` against standalone Market `3818233cea1aeace0ddba0ea845188a3a3e94a35` with `tools/component-sync/component_sync.py` and reported no added, missing, or modified product path. Aggregate and standalone hashes were both `89afa19c977a639b2714d6d2dae318a9e0bade05bbc8a88e76e29f9027883d4b`. |
| Content-hash method | `tools/component-sync/component_sync.py`; SHA-256 over sorted POSIX paths and raw bytes; `COMPONENT-METADATA.md` excluded as aggregate-only orchestration metadata. File modes are not part of the canonical content hash. |
| Current blockers | Frozen product head `6bc16ab72862a88691ecdf1402d60a909c58c3be` passed aggregate Coverage/build, runtime-JAR inspection, Sentinel artifact, and exact component parity. Hosted Codacy remains `ACTION_REQUIRED` because importing the standalone tree makes pre-existing Market findings new to the aggregate; the detailed dump reports zero Staff-root findings. Canonical private staging evidence where applicable, a passing aggregate static-analysis disposition, normal merge, and post-merge parity remain required. |

The aggregate product tree is synchronized to the current authoritative ES-X03 standalone candidate above, excluding only aggregate-only `COMPONENT-METADATA.md`. Market PR #7 is retired and is not a source of truth. Preserved unpaired Market PR #6 remains separate from this paired package.
