# ES-D04 account linking and DiscordSRV migration — COMPLETE

Date: 2026-08-26
Status: `COMPLETE`
Package: `ES-D04 — Account linking and DiscordSRV migration`
Implementation PR: #151
Final reviewed/validated head: `da0371681f5a44c72a614c8d6637b85d9080291d`
Normal merge commit: `4e7621b7a42e812cc7bf806a029f37a753cdd9f3`

## Selection and unblock

The owner-authorized Discord worker reconciled live GitHub and selected D04 as the required highest-priority `ACTIONABLE_CONTINUATION`. Its historical blocker was not treated as permanently true: the canonical Pi path for the current exact head had become discoverable/executable and produced durable public/private run evidence, satisfying the unblock condition recorded in the historical D04 handoff.

D05 was already `COMPLETE`. D06 and D13 were still dependency-blocked by D04 at selection, so no new Discord package was started.

## Delivered product

D04 delivers authoritative Discord↔Minecraft account linking and DiscordSRV migration support: two-direction one-use five-minute codes with hash-only persistence; replacement invalidation; durable expiry/replay/restart handling; online-account proof and confirmed unlink; audited staff recovery/reassignment/main override; historical ownership; active-playtime main selection with 25% hysteresis; idempotent DiscordSRV public-API import; temporary main-link mirroring; transactional identity/main/audit mutations; and bounded deadlock retry.

Provider boundaries remain public-API only. Missing PlayTime active-time data preserves the existing automatic main instead of guessing zero. One Minecraft UUID cannot have two current Discord owners. No legacy production link data was committed or imported.

## Final exact-head validation

Every applicable executable gate was terminal and passing on exact reviewed head `da0371681f5a44c72a614c8d6637b85d9080291d` before merge:

- Coverage/full Java 21/MariaDB/Testcontainers run `33029697612`, job `98379158884`: `SUCCESS`. Exact checkout, Java 21 setup, runtime builds/tests, aggregate JaCoCo, runtime-JAR inspection, validation-artifact publication, and Codacy coverage upload all succeeded.
- Codacy Static Code Analysis check `98379478044`: `SUCCESS`, zero annotations and six prior findings solved.
- Codacy Diff Coverage check `98380515790`: `SUCCESS`, 73.86% diff coverage with no repository gate defined.
- Sentinel Restart Artifact run `33029697604`, job `98379112319`: `SUCCESS`; artifact `9629765037`, digest `sha256:57ece0dbccc120c69f6a846d80aa5bf60aab04472f1439af5abe799c19074b99`.
- Sentinel command comment `5433137415`, durable job `292`: terminal `PAPER_RESTART_OK`, proving readiness and clean stop twice against disposable state on the exact head.
- Canonical public Pi run `33029762105`: `SUCCESS`, including exact candidate/provenance revalidation, hosted build/package verification, bounded transfer, private dispatch, private verdict collection, transient-transfer cleanup, and terminal publication.
- Correlated private staging run `wsg138/EnthusiaStaff-Staging` `33030278019`, job `98380970512`: `SUCCESS` on trusted `Lincoln-PI-4` (`self-hosted`, Linux/ARM64, `enthusia-staging`), including exact bridge verification, guarded disposable Paper boot/restart, sanitized evidence publication, durable-evidence requirement, and cleanup.
- Sanitized Pi evidence identity: `artifact=enthusiastaff-paper-da0371681f5a-33029762105-1;runtime_sha256=1ade8ecd4114902038a41a290ff55c509f458692e69ef429838d86b9ce645152`.
- All visible PR #151 inline review threads were resolved; no valid unresolved review thread remained.

No missing, queued, failed, cancelled, superseded, or wrong-revision evidence was relabeled as passing.

## Merge, containment, and cleanup

Immediately before merge, canonical `main` was `36af6fc85052cbc38bb9840899b415fc53503af3`, already contained in the exact feature head (`behind_by: 0`). PR #151 was non-draft, clean, and mergeable.

PR #151 merged normally as `4e7621b7a42e812cc7bf806a029f37a753cdd9f3`; no squash, rebase, force-push, or auto-merge was used. The merge commit has parents pre-merge `main` `36af6fc85052cbc38bb9840899b415fc53503af3` and exact feature head `da0371681f5a44c72a614c8d6637b85d9080291d`.

The merge tree SHA is `63c2a0924d38ac9ce8e0a208f0eb79a671af37fc`, identical to the exact validated feature-head tree. Post-merge comparison from the feature head is one commit ahead and zero behind, so the validated product is exactly contained without a product-tree delta.

`persistence/src/main/resources/db/migration/V20__discord_account_linking.sql` is now canonical on `main`. The temporary branch `package/es-d04-account-linking` is absent after merge, so no unique D04 implementation work remains.

## Safety and production boundary

No production DiscordSRV import was executed. No production Discord configuration, production/private player data, PM data, deployment, punishment authority, AutoMod enforcement, role-sync replacement, LiteBans cutover, or issue #43 acceptance occurred. Issue #43 remains open and LiteBans remains authoritative. No Discord bot token or other secret was requested, displayed, copied, logged, committed, or placed on a command line.

## Concurrent-work containment and final routing

Independent ES-X03 Staff PR #139 and branch `package/es-x03-market-provider` were not edited, rebased, renumbered, merged, closed, or superseded. D04's normal merge serializes the legitimate V20 Discord migration and shared-file work onto `main`, satisfying the external condition that had parked X03; a future universal worker can now reconcile X03 normally, move its branch-local Market migration to the next free forward-only number, preserve both packages, and rerun its own exact-head gates.

D04 and D05 are both complete. `ES-D06 — Read-only staff moderation UX` and `ES-D13 — Discord role-sync replacement` are now dependency-complete `READY`. A future Discord worker selects D06 first by priority unless live GitHub exposes a higher-priority actionable continuation.

This worker stops here. No second Discord package is started.