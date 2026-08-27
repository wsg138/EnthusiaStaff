# ES-D04 — Account linking and DiscordSRV migration

Status: `COMPLETE`. Priority: 133. Depends on `ES-D01`–`ES-D03` (all `COMPLETE`). Internal package; external provider contracts were inspected before implementation.

## Terminal state

Selected again on 2026-08-26 as the highest-priority Discord `ACTIONABLE_CONTINUATION` after the historical canonical-Pi observability blocker materially changed. D04 completed through exact-head review/hosted/static/Sentinel/Pi validation, normal merge, containment, cleanup, and terminal publication.

- Starting claim `main`: `783925e2b49ab4567bd3c3869e43fc03ff6d285f`.
- Final pre-merge `main`: `36af6fc85052cbc38bb9840899b415fc53503af3`.
- Implementation PR: #151.
- Final reviewed/validated product head: `da0371681f5a44c72a614c8d6637b85d9080291d`.
- Normal merge commit: `4e7621b7a42e812cc7bf806a029f37a753cdd9f3`.
- Merge parents: pre-merge `main` `36af6fc85052cbc38bb9840899b415fc53503af3` and exact feature head `da0371681f5a44c72a614c8d6637b85d9080291d`.
- Merge tree and validated feature tree are both `63c2a0924d38ac9ce8e0a208f0eb79a671af37fc`; containment is exact.
- Post-merge compare from the feature head is one commit ahead and zero behind with no product-tree delta.
- Temporary implementation branch `package/es-d04-account-linking` is absent after merge.
- Canonical `main` now owns forward-only `V20__discord_account_linking.sql`.
- Issue #43 remains open and LiteBans remains authoritative.

Canonical terminal handoff: `ai-agents/reports/package-handoffs/2026-08-26-es-d04-complete.md`.
Historical blocked handoff: `ai-agents/reports/package-handoffs/2026-08-24-es-d04-account-linking-blocked.md`.

## Delivered scope

D04 makes EnthusiaStaff the durable Discord↔Minecraft link authority while retaining a safe migration path from DiscordSRV without forcing relink. Delivered scope includes two-direction one-use five-minute link codes with only SHA-256 hashes persisted; replacement invalidation; durable expiry/replay/restart handling; online-account proof; confirmed self-unlink; permission-gated audited staff force-link/unlink/reassignment and main overrides; historical ownership; lifetime active-playtime main selection with 25% hysteresis and deterministic fallback; idempotent DiscordSRV public-API snapshot import; and temporary main-link mirroring for legacy role sync.

One Minecraft UUID cannot have two current Discord owners. Races, replays, and restarts fail closed. PlayTime integration uses only public `PlaytimeService#getLifetime(UUID).activeMinutes`; if active-playtime data is unavailable the existing automatic main is preserved instead of treating missing data as zero. DiscordSRV integration uses only the public `AccountLinkManager` API. No legacy production link data is committed.

The persistence implementation has one authoritative transactional owner for link/unlink/reassignment. Unlink/reassignment replacement-main planning is validated and committed in the same JDBC transaction as the identity mutation. Staff recovery and main-account override mutations are atomic with their audits. Discord link mutations use bounded deadlock retry around complete transactions.

## Final exact-head gates — PASS

Exact validated head: `da0371681f5a44c72a614c8d6637b85d9080291d`.

- Coverage/full Java 21/MariaDB/Testcontainers run `33029697612`, job `98379158884`: `SUCCESS`; exact checkout, Java 21 setup, full runtime build/tests, aggregate JaCoCo, runtime-JAR inspection, validation-artifact upload, and Codacy coverage upload all passed.
- Codacy Static Code Analysis check `98379478044`: `SUCCESS`, zero annotations, six prior findings solved.
- Codacy Diff Coverage check `98380515790`: `SUCCESS`, 73.86% diff coverage; no repository gate is defined.
- Sentinel Restart Artifact run `33029697604`, job `98379112319`: `SUCCESS`; artifact `9629765037`, digest `sha256:57ece0dbccc120c69f6a846d80aa5bf60aab04472f1439af5abe799c19074b99`.
- Sentinel command comment `5433137415`, durable job `292`: terminal `PAPER_RESTART_OK` on the exact head.
- Canonical public Pi run `33029762105`: `SUCCESS`, including exact source/package validation, private dispatch, private verdict collection, transient-transfer removal, and terminal result publication.
- Correlated private staging run `wsg138/EnthusiaStaff-Staging` `33030278019`, job `98380970512`: `SUCCESS` on trusted `Lincoln-PI-4` (`self-hosted`, Linux/ARM64, `enthusia-staging`), including exact bridge verification, guarded disposable Paper boot/restart, sanitized evidence publication, durable-evidence requirement, and cleanup.
- Sanitized Pi artifact identity: `artifact=enthusiastaff-paper-da0371681f5a-33029762105-1;runtime_sha256=1ade8ecd4114902038a41a290ff55c509f458692e69ef429838d86b9ce645152`.
- All visible PR #151 inline review threads were resolved before merge; no valid unresolved review thread remained.

## Completion checklist

- [x] Implement authoritative Discord↔Minecraft linking, unlinking, history, recovery, main selection, DiscordSRV import, and temporary mirroring.
- [x] Preserve provider/public-API, transactionality, replay, restart, privacy, and production-authority boundaries.
- [x] Reconcile current `main` without overwriting concurrent work and retain D04 as forward-only V20 migration owner.
- [x] Resolve valid harsh-review/static-analysis findings.
- [x] Pass exact-head hosted Java 21/MariaDB/Testcontainers validation and static analysis.
- [x] Pass independent exact-head Sentinel restart validation.
- [x] Pass canonical public/private Pi staging with trusted runner, provenance, cleanup, and durable sanitized evidence.
- [x] Merge PR #151 normally and prove exact containment.
- [x] Confirm the temporary implementation branch is absent and publish terminal state.

## Exclusions and routing

No bot moderation panel, punishment enforcement, role-sync replacement, AutoMod enforcement, production DiscordSRV import execution, production Discord configuration/data access, deployment, LiteBans authority change, issue #43 acceptance, or cutover was performed.

`ES-D04` and `ES-D05` are now both `COMPLETE`. Therefore `ES-D06 — Read-only staff moderation UX` and `ES-D13 — Discord role-sync replacement` are dependency-complete `READY`; a future Discord worker selects D06 first by priority unless a new higher-priority actionable continuation exists. This worker stops without starting either package.

Independent `ES-X03` was not edited, rebased, renumbered, merged, or closed. D04's serialization onto `main` removes X03's former D04/V20 ambiguity, so a future universal worker may safely reconcile that existing branch against current `main`, renumber its branch-local Market migration to the next free version, and revalidate it under its own protocol.