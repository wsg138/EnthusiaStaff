# Component metadata — enthusia-market

| Field | Value |
| --- | --- |
| Component ID | `COMP-MARKET` |
| Standalone repository | `wsg138/EnthusiaMarket` |
| Standalone default branch | `main` |
| Aggregate path | `components/enthusia-market/` |
| Verified standalone head at setup | `bc24f1010642d6042307bc13a32fb33cc94e8883` |
| Last synchronized external SHA | `addb0f53d4aeac3549ab9b3ee8af3a6950db201f` |
| Last synchronized aggregate-main SHA | `PENDING_ES_X03_MERGE` |
| Synchronization state | `SYNC_PENDING` |
| Product-tree hash | `PENDING_FINAL_CANONICAL_HASH` — the prior `8d27f4d9c64ca52feecd1df6200a45314610fa0df4b27da9d39b444152007c3b` applied to obsolete external candidate `62408695063d03303026766befb065a0f1f51044` and is not reused after later executable/test, workflow, or documentation changes. |
| Current parity evidence | The current source fix has identical Market/aggregate blob `83758cff61c998b8d56907b706a8339bddc78721`. The final build workflow has identical Market/aggregate blob `563ed55bb6f4496f2392f7bd82656922b6338c0a`; it consumes the BadgersMC LumaGuilds `v2.1.24` release, tag commit `f8f1f6f4673182586c141e2272c50aae851404b8`, verifies published asset SHA-256 `54ad645587f2ce895738eff3ee05123eb19e5687d80fa6d657aa3092031004c2`, bounds the release transfer with a 15-second connect timeout and 300-second total timeout, and explicitly checks out the pull-request head SHA for build, detekt, and security. The final Wiki workflow has identical Market/aggregate blob `424b57cad79bee95f07cbde4546baed2fdda6453` and explicitly checks out the pull-request head SHA in every job. Full canonical SHA-256 remains pending. |
| Content-hash method | `tools/component-sync/component_sync.py`; SHA-256 over sorted POSIX paths and raw bytes; `COMPONENT-METADATA.md` excluded as aggregate-only orchestration metadata. File modes are not part of the canonical content hash. |
| Current blockers | Fork-level Actions execution and action permissions are fixed. The in-scope Detekt defect was repaired, the reproducible third-party LumaGuilds source-build failure was removed by using the pinned release artifact, the download was bounded, and merge-ref-only validation was corrected to explicit PR-head checkout. Fresh exact-head Market and Staff validation, final canonical SHA-256, paired normal merges, and post-merge parity remain pending. |

The aggregate product tree is synchronized to the current standalone ES-X03 head above, excluding only aggregate-only `COMPONENT-METADATA.md`. No permanent component branch or isolated-component PR is required. The package must not treat the obsolete `6240869` hash as final evidence.
