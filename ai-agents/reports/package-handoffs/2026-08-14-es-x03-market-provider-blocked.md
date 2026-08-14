# ES-X03 handoff — EnthusiaMarket destructive provider — BLOCKED

Date: 2026-08-14

Status: `BLOCKED` / `PARKED_BLOCKED`.

This handoff supersedes the earlier ES-X03 candidate handoff as current routing authority. It does not relabel any missing, stale-head, skipped, unavailable, failed, cancelled, or superseded validation as passing evidence.

## Starting live state

The continuation worker reconciled live GitHub before changing anything:

- EnthusiaStaff `main`: `49e5aa999b43193181aafabbb75811c820fa03c7`.
- Staff PR #139 starting head: `e6ad4cb4bf7d91ecdfaa43b3e278992c919347b2`.
- EnthusiaMarket `main`: `bc24f1010642d6042307bc13a32fb33cc94e8883`.
- Market PR #3 starting head: `556b4b42e0d730f74c8f5423de4453c6cd8946b4`.
- Earlier reviewed Market candidate: `62408695063d03303026766befb065a0f1f51044`.

The canonical Staff registry/workspace on `main` was stale at startup: it showed ES-X03 merely `READY` even though the two live implementation PRs already existed.

## Post-candidate Market reconciliation

The Market PR was 16 commits past the earlier reviewed candidate. Those commits were not accepted merely because they existed.

Retained as legitimate ES-X03 remediation:

- `825fc2cf5aa4981a8eb6c73c385e1118cb50f618` — removes X03 API analyzer findings, refactors only X03 contract tests, and pins the existing Codacy coverage action. It adds no private Enthusia infrastructure.

Separated from ES-X03 as unrelated/opportunistic Market cleanup or historical hardening:

- `45d6bf8c8ace0af4de41810388365d8f54fa1f94` — broad validation/lifecycle complexity cleanup;
- `18b271ecb4b8b8ba7225c82c0c184258f08b4d34` — broad shop command/sign simplification;
- `110297d9169c85af6624d8557dd2c4287ebcc0a1` — shop sign validation decomposition;
- `f4ea8a3d636ae08f670c5bbfc31ed55906f6d2aa` — ownership transfer decomposition;
- `3f450d9125d7c8e046e6971bbfe3a603a01cad48` — auction/rent lifecycle complexity cleanup;
- `237ddb30af248f97c76ca30e9d12fbd3e1bfab31` — container trade transaction decomposition;
- `984644ff40a5f4a3ac8e5efac1f72950851472bd` — public snapshot projection decomposition;
- `f8773a37c845d27a8ea0f07a34f7d94c2dd6b5de` — Bedrock shop creation simplification;
- `897cc8b65ec71bdf3f5eb452426724031d2183ab` — region resync decomposition;
- `b24c22a0439d618bd8f5a063cfa29928258cba51` — shop editor rendering decomposition;
- `ab15120a524a8b12cb794e99b9264e45fe23cc97` — purchase menu trade-flow simplification;
- `59cec9c44f5d2c14d077c31f7ee5f0950bff8b6b` — search rendering decomposition;
- `36d5bc2e4310114ed572acc9e05f7ba138434738` — historical legacy-NBT hardening;
- `b1adf502d5de840bb1d2ba124245b1b20dc996df` — HTTP request metadata encapsulation;
- `556b4b42e0d730f74c8f5423de4453c6cd8946b4` — shop creation input restructuring.

This broad delta was preserved intact on `wsg138/EnthusiaMarket:preserve/es-x03-post-candidate-556b4b4-20260814` before scope was restored. The ES-X03 branch then advanced through an ordinary forward commit whose tree restored the reviewed candidate plus the valid `825fc2c` remediation. No force-push, rebase, squash, destructive reset, or loss of unique work occurred.

Two Major review findings that appeared only in the separated broad refactor — failed post-eviction cleanup and sellback irreversible partial-state/refund ordering — became resolved/outdated when that executable refactor was removed from X03. Their history remains preserved on the preservation branch.

## Review findings and dispositions

All live Market inline review threads are resolved against the scoped tree. Staff PR #139 has no live inline review threads.

Valid findings fixed or verified fixed in current X03 code include:

- Unicode/internal whitespace rejection for operation/case/stall identifiers;
- complete immutable prepare replay identity, including deadlines and requested blacklist expiry;
- provider result/status mapping and operation-state handling;
- executor shutdown/interruption and timed region-access cleanup;
- SQL transaction contention classification including MariaDB 1205/1213;
- mixed stock-batch behavior that skips fenced rows without rolling back unrelated valid rows;
- lock-aware shop/history deletion paths;
- ordinary stall saves no longer advancing the moderation revision;
- snapshot JSON shape/null validation before decode;
- provider executor context-classloader behavior;
- bounded MariaDB concurrency futures, including the remaining acquisition future;
- stale blacklist restoration now requiring the current moderation operation identity and optimistic revision. A missing/newer/raced blacklist row is no longer inserted/overwritten by stale restore, and an already-original snapshot is replay-safe.

Suggestions rejected/documented as technically invalid rather than blindly applied:

1. Caller-supplied authentication in `MarketModerationApi`. This is an in-process Bukkit service for trusted installed plugins. EnthusiaStaff authenticates/authorizes the human operator before invocation; Market validates operation identity/target/stall/case/checksum/revision/state. A token exposed to arbitrary malicious code in the same JVM would not create an independent security boundary.
2. Adding a moderation-lock fence to insert-only `StallRepositorySql.create`. A live moderation lock requires an existing stall; creation of an absent primary key cannot replace that locked stall. Existing-stall mutation paths are fenced.
3. Proposed V025 composite foreign keys. They conflict with the provider's intentional lock-before-journal preparation ordering and standalone blacklist semantics.
4. Expiring durable moderation player fences automatically. The non-expiring moderation fence is intentional while an operation remains PREPARED/HOLD so restart cannot reopen acquisition before terminal recovery.
5. A ShopCreateListener sign-DENY observation was checked against Market `main`; the same blob is pre-existing and not introduced/worsened by ES-X03.

Zero valid unresolved live review-thread findings remain. The latest CodeRabbit incremental review after later commits was rate-limited; no approval is claimed for a review that did not execute. Manual current-code reconciliation was performed instead.

## Final scoped implementation heads

- Market PR #3: `aa7cf6025bd8634c1106e6457cd49e7baa182f51`.
- Staff PR #139: `fb0afbec22b68bdfb9ba910737f8ff254d23c4ce`.

Both PRs remain open and explicitly marked `BLOCKED — DO NOT MERGE` in their bodies.

## Aggregate/standalone parity

The current aggregate Market product content is synchronized to standalone `aa7cf6025bd8634c1106e6457cd49e7baa182f51` under the canonical aggregate-only `COMPONENT-METADATA.md` exclusion.

Git object byte-identity evidence:

- `src/`: `49a69707e465e9befeb6fb16d93ef64c629cb3bb` in both repositories;
- `src/main/`: `eafeefa085cd99463e898f445713535c5d4433cf` in both;
- `src/test/`: `2c3d1d612b0a89ca7c9f27758bb928f3c74a7d71` in both;
- all other top-level product blobs/subtrees match;
- standalone and aggregate `gradlew` have identical blob `f5feea6d6b116baaca5a2642d4d9fa1f47d574a7`; Git mode differs, and `tools/component-sync/component_sync.py` intentionally hashes paths + raw bytes, not modes.

The previously recorded normalized SHA-256 `8d27f4d9c64ca52feecd1df6200a45314610fa0df4b27da9d39b444152007c3b` belongs only to obsolete candidate `62408695063d03303026766befb065a0f1f51044`. It is not reused or represented as current. Candidate `COMPONENT-METADATA.md` now records `PENDING_FINAL_CANONICAL_HASH`; the canonical SHA-256 must be rerun before completion.

## Migration boundary

Market V001–V024 remain immutable. ES-X03 owns V025 only. No Flyway repair and no historical migration rewrite occurred. The production `MigrationRunner` V024→V025 path is the applicable provider upgrade boundary. The old V001 MariaDB clean-install indexed-`TEXT` limitation remains visible and separate.

Staff V1–V18 remain immutable. ES-X03 owns V19 only.

## Historical validation evidence — not final-head proof

The earlier Market candidate `6240869` had the following direct evidence:

- Java 21 clean graph: all 11 tasks completed;
- 120 suites / 637 tests, zero failures/errors; Windows evidence recorded 631 passes with expected Docker/remote-auth skips;
- separate disposable Docker/MariaDB 11.8.3 execution: all 5 provider tests passed;
- runtime JAR: 4,138,102 bytes; SHA-256 `ba821a7fdc509f2a94ba155d911351c04ab540c15f8da21e5f1c31dd333f9d6f`;
- standalone static baseline: Lizard 40 repository findings / 35 production, with touched legacy-NBT findings pre-existing; PMD 0; Trivy 0; Opengrep one pre-existing unpinned Codacy action. Retained `825fc2c` pins that action and removes X03 API analyzer findings without suppressing a rule or hiding first-party paths.

The earlier provider-integrated Staff candidate had:

- Java 21 clean graph: 39 tasks;
- 222 suites / 951 tests;
- 50 integration suites / 192 tests, no skips/failures;
- Paper artifact SHA-256 `e275fd6912dd8b282d65ea735a72eb4f258a8e4e7ed5b9224abe44cb5be35d15`;
- Velocity artifact SHA-256 `85fee16bbdaf4eb8916f1a64506dd4dcd3b3b195a383ab1adb5d7c3c632affac`;
- provider API leakage: zero.

These are historical candidate results only. The final scoped Market tree later changed executable/test behavior for blacklist restore fencing and bounded concurrency tests. They therefore cannot satisfy exact-head validation.

Staff aggregate Codacy also exposes a large historical Market analyzer inventory as newly visible because the provider source is newly imported into the aggregate. That debt is not called clean, and ES-X03 did not expand into a whole-Market analyzer-refactor campaign. X03-introduced/worsened findings were handled separately.

## Current hosted evidence and hard blocker

At this handoff's publication point, Staff exact head `fb0afbec22b68bdfb9ba910737f8ff254d23c4ce` has:

- `Validate Wiki` run `31777937947`: `success`;
- `Sentinel Restart Artifact` run `31777937952`: `success`;
- `Coverage` run `31777937958`: still running at the time this blocker state was first written and therefore not counted as passing unless a later recorded result proves success.

Market exact head `aa7cf6025bd8634c1106e6457cd49e7baa182f51` has no ordinary Actions evidence. Repository Actions history returns `total_count: 0`. The connected GitHub App cannot dispatch a workflow, and the repository-owned build workflow has no `workflow_dispatch` trigger. Attempts to obtain a normal PR event did not produce a run. This is a required exact-head gate, so ES-X03 cannot be `COMPLETE` and neither implementation PR may merge.

Unblock condition: restore/enable ordinary repository-owned GitHub Actions execution for `wsg138/EnthusiaMarket`, or expose an existing repository-owned workflow through connected tooling; run the exact-head Java 21 build/test graph, disposable Docker/MariaDB tests, detekt/static/security, Wiki/docs, and runtime artifact inspection; apply only valid in-scope repairs; resynchronize Staff and rerun invalidated gates; recompute canonical parity; then merge both PRs normally.

## Private staging boundary

No private Enthusia staging capability was added, copied, referenced, or configured in Market or a BadgersMC repository. Specifically, no private Pi runner labels/configuration, Staff-Staging repository reference, public→private bridge implementation, private workflow dispatch, staging secrets/secret names, private DB routes/hosts/tokens/topology, private artifact-transfer mechanism, or private Sentinel credentials/service details were added.

Market remains ordinary `ubuntu-latest` repository CI. The Market PR patch contains no `Lincoln-PI-4`, `EnthusiaStaff-Staging`, or staging reference. Private Pi staging is not used as a substitute for the missing Market gate. Representative destructive/load/process-kill acceptance stays assigned to ES-V03 on owner-controlled infrastructure.

## Merge, containment, and branch state

- No ES-X03 implementation PR merged.
- Market `main` remains `bc24f1010642d6042307bc13a32fb33cc94e8883`.
- Staff product `main` remained `49e5aa999b43193181aafabbb75811c820fa03c7` before this documentation-only blocker publication.
- Market PR #3 remains open at `aa7cf6025bd8634c1106e6457cd49e7baa182f51`.
- Staff PR #139 remains open at `fb0afbec22b68bdfb9ba910737f8ff254d23c4ce`.
- Market preservation branch `preserve/es-x03-post-candidate-556b4b4-20260814` remains intentionally retained because it contains unique unrelated cleanup work.
- Both implementation `package/es-x03-market-provider` branches remain intentionally retained because their work is unmerged.
- No temporary implementation branch is eligible for cleanup yet.

## Production boundary

No real player listing, balance, item, private player row, production database, deployment, cutover, or production authority was changed. LiteBans remains authoritative. Issue #43 remains open/deferred. ES-V03 destructive/load acceptance was not started.

## Next routing

This worker stops on ES-X03 after publishing the durable blocker state. It does not start ES-X04, ES-X01, V02, V03, or any other package.

Because ES-X03 is now parked blocked and the canonical rules allow unrelated dependency-complete work to proceed, `ES-X04 — EnthusiaCommend reputation provider` remains `READY` and is the next sequential eligible package for a separate fresh worker after live reconciliation. ES-X01 remains independently `BLOCKED` / `PARKED_BLOCKED`.
