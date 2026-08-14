# Component metadata — enthusia-market

| Field | Value |
| --- | --- |
| Component ID | `COMP-MARKET` |
| Standalone repository | `wsg138/EnthusiaMarket` |
| Standalone default branch | `main` |
| Aggregate path | `components/enthusia-market/` |
| Verified standalone head at setup | `bc24f1010642d6042307bc13a32fb33cc94e8883` |
| Last synchronized external SHA | `b17725ccfc9d13d0926fe09599978b4fc717efe7` |
| Last synchronized aggregate-main SHA | `PENDING_ES_X03_MERGE` |
| Synchronization state | `SYNC_PENDING` |
| Product-tree hash | `PENDING_FINAL_CANONICAL_HASH` — the prior `8d27f4d9c64ca52feecd1df6200a45314610fa0df4b27da9d39b444152007c3b` applied to obsolete external candidate `62408695063d03303026766befb065a0f1f51044` and is not reused after later executable/test, workflow, or documentation changes. |
| Current parity evidence | Prior aggregate/standalone content was synchronized at Market `0a04b995b2a05dfb2a98c77a7c4db0194da7b30c` / Staff `0727fba655cdcb4024c7988c529391487917eaf3`. The current source fix has identical Market/aggregate blob `83758cff61c998b8d56907b706a8339bddc78721`. The CI bootstrap repair also has identical Market/aggregate workflow blob `209e4815ebecac3174e1e22602dac678acba1264`; it consumes the BadgersMC LumaGuilds `v2.1.24` release, tag commit `f8f1f6f4673182586c141e2272c50aae851404b8`, and verifies published asset SHA-256 `54ad645587f2ce895738eff3ee05123eb19e5687d80fa6d657aa3092031004c2` before compiling RoseChat and Market. Full canonical SHA-256 remains pending. |
| Content-hash method | `tools/component-sync/component_sync.py`; SHA-256 over sorted POSIX paths and raw bytes; `COMPONENT-METADATA.md` excluded as aggregate-only orchestration metadata. File modes are not part of the canonical content hash. |
| Current blockers | Fork-level Actions execution and action permissions are fixed. Two exact-head Market attempts at `8af6a2243a997233277edcd995f1235cf2cd2376` failed before Market execution because the old CI rebuilt LumaGuilds through unavailable third-party repositories (Artillex HTTP 522 and JitPack HTTP 401). The current synchronized workflow removes that redundant external rebuild while retaining pinned-jar digest verification, RoseChat hook compilation, Market tests, detekt, and security. Fresh exact-head Market and Staff validation, final canonical SHA-256, paired normal merges, and post-merge parity remain pending. |

The aggregate product tree is synchronized to the current standalone ES-X03 head above, excluding only aggregate-only `COMPONENT-METADATA.md`. No permanent component branch or isolated-component PR is required. The package must not treat the obsolete `6240869` hash as final evidence.
