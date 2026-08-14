# Component metadata — enthusia-market

| Field | Value |
| --- | --- |
| Component ID | `COMP-MARKET` |
| Standalone repository | `wsg138/EnthusiaMarket` |
| Standalone default branch | `main` |
| Aggregate path | `components/enthusia-market/` |
| Verified standalone head at setup | `bc24f1010642d6042307bc13a32fb33cc94e8883` |
| Last synchronized external SHA | `0a04b995b2a05dfb2a98c77a7c4db0194da7b30c` |
| Last synchronized aggregate-main SHA | `PENDING_ES_X03_MERGE` |
| Synchronization state | `SYNC_PENDING` |
| Product-tree hash | `PENDING_FINAL_CANONICAL_HASH` — the prior `8d27f4d9c64ca52feecd1df6200a45314610fa0df4b27da9d39b444152007c3b` applied to obsolete external candidate `62408695063d03303026766befb065a0f1f51044` and is not reused after later executable/test or documentation changes. |
| Current parity evidence | Git object content identity verifies the executable/source subtrees remain unchanged from standalone `aa7cf6025bd8634c1106e6457cd49e7baa182f51`: `src/` is `49a69707e465e9befeb6fb16d93ef64c629cb3bb`, `src/main/` is `eafeefa085cd99463e898f445713535c5d4433cf`, and `src/test/` is `2c3d1d612b0a89ca7c9f27758bb928f3c74a7d71` in both copies. Standalone `0a04b995b2a05dfb2a98c77a7c4db0194da7b30c` differs from `aa7cf6025bd8634c1106e6457cd49e7baa182f51` only by `docs/moderation-provider.md`, and that exact documentation blob is mirrored into the aggregate. Full canonical SHA-256 remains pending. |
| Content-hash method | `tools/component-sync/component_sync.py`; SHA-256 over sorted POSIX paths and raw bytes; `COMPONENT-METADATA.md` excluded as aggregate-only orchestration metadata. File modes are not part of the canonical content hash. |
| Current blockers | Repository Actions is enabled and the ordinary `build` workflow is active, but GitHub created no run after reopening PR #3 or after the GitHub-worker-authored synchronization commit `0a04b995b2a05dfb2a98c77a7c4db0194da7b30c`. Exact-head ordinary Market validation therefore still lacks a run. Final canonical SHA-256, paired normal merges, and post-merge parity remain pending. |

The aggregate product tree is synchronized to the current standalone ES-X03 head above, excluding only aggregate-only `COMPONENT-METADATA.md`. No permanent component branch or isolated-component PR is required. The package must not treat the obsolete `6240869` hash as final evidence.
