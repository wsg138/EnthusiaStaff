# MARKET-02 Staff recovery / orphan tool cleanup — historical PR #647

**Historical evidence only.** This record preserves dated development and debugging context, **not** a current agent assignment, server state, release instruction, or authorization to operate against players or production.

- Source PR: [#647](https://github.com/wsg138/EnthusiaStaff/pull/647), head [`383859b5740f25113b7b6b8455256717ebe3635a`](https://github.com/wsg138/EnthusiaStaff/commit/383859b5740f25113b7b6b8455256717ebe3635a)
- [Exact historical MARKET-02 handoff](ORIGINAL-MARKET02-HANDOFF.md) — copied **byte-for-byte**, SHA `592cb7fc5d101cac18d8b65c867860494f64a904`, from [the source at the #647 head](https://github.com/wsg138/EnthusiaStaff/blob/383859b5740f25113b7b6b8455256717ebe3635a/ai-agents/reports/agent-handoffs/2026-10-20-market-orphan-staff-tools.md).
- Other historical additions in the old PR: [workspace-state notes](https://github.com/wsg138/EnthusiaStaff/blob/383859b5740f25113b7b6b8455256717ebe3635a/ai-agents/WORKSPACE-STATE.md) (2026-10-25 and 2026-10-28 MARKET-02 recovery/inspector status) and [package-registry notes](https://github.com/wsg138/EnthusiaStaff/blob/383859b5740f25113b7b6b8455256717ebe3635a/ai-agents/work-packages/PACKAGE-REGISTRY.md) (same MARKET-02 status). These are pinned source records; do **not** replace the current live agent registry with their obsolete status.

## Original scope and independent reconciliation

The MARKET-02 work addressed orphan tagged Staff tools, safe recovery after a failed or absent durable session, retry fencing across reconnect, rankless destination joins, vanish/game-mode restoration, and a private console-only session inspector. The original source branch combined several rounds of implementation, tests, developer handoffs, source review and historical build results. Some of its test evidence was recorded against **intermediate** commits; read the pinned handoff for exact provenance and limitations.

- [Merged #787](https://github.com/wsg138/EnthusiaStaff/pull/787) supplied the mainline recovery attempt/lease/cleanup behavior.
- [Merged #778](https://github.com/wsg138/EnthusiaStaff/pull/778) protected the rankless destination join path.
- [Merged #801](https://github.com/wsg138/EnthusiaStaff/pull/801) ported the distinct inspector, no-record vanish-restoration guard, rankless handoff tests and associated sources onto fresh `main` without clobbering #787. Its exact final-head hosted Coverage/MariaDB, protocol, and Sentinel workflows succeeded; see [W7 reconciliation report](https://github.com/wsg138/EnthusiaStaff/issues/426#issuecomment-6811305554) and coordinator [integration checkpoint](https://github.com/wsg138/EnthusiaStaff/issues/426#issuecomment-6811519058).

The older #647 PR's 29 changed paths were reviewed by W7: 16 inspector paths, recovery/source paths already accounted for in the merged mainline lineage, rankless/vanish distinctions ported in #801, and three dated AI-agent coordination paths intentionally **not** copied over active agent state. This archive preserves the unique longer handoff, with the other status entries pinned by commit, while leaving current worker routing intact.

**Remaining unverified operations:** no source merge establishes what is installed or currently loaded on TEST, BUILD or SMP; real-player Java/Bedrock reconnect, cross-backend handoff, inventory restoration, database/account inspection, recovery under server restart, and production acceptance remain separate and require explicit validation. Do not infer a new release or JAR from this archive.

**Retention boundary:** Closing historical #647 must not imply deleting its branch, old commits, PR reviews, or these sources. No branch or remote deployment action is authorized by this document.
