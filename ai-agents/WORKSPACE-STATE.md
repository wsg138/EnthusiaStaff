# Workspace state

Last updated: 2026-08-14

Live GitHub overrides stale records. Detailed package evidence remains in the registry, package record, canonical handoff, and PR verification ledgers.

## Current routing

| Field | Value |
| --- | --- |
| Completed packages | `ES-P01`, `ES-P02`, `ES-P03`, `ES-P04`, `ES-P05`, `ES-P06`, `ES-P07`, `ES-P08`, `ES-P09`, `ES-P10`, `ES-P11`, `ES-X02`, `ES-X05`, `ES-R01`, `ES-R02`, `ES-V01` |
| Active package | None. `ES-X03 — EnthusiaMarket destructive provider` is `BLOCKED` / `PARKED_BLOCKED`; all safe actionable repository work is complete and the remaining unblock requires materially improved owner-controlled runtime-host conditions. |
| ES-X03 live PRs | Market PR #3 is open/mergeable at `addb0f53d4aeac3549ab9b3ee8af3a6950db201f`. Staff PR #139 is open/mergeable at `5b003225b305db76b47db7d75cf5b6a2943934df`. Neither implementation PR merged. |
| Scope reconciliation | Valid X03 remediation remains; unrelated historical Market refactor history remains preserved on `preserve/es-x03-post-candidate-556b4b4-20260814`. No force-push, rebase, squash, or destructive reset occurred. |
| Market exact-head state | Fork Actions execution/permissions are fixed. Market head `addb0f53...` passed Wiki `31852806668` and build `31852806638`; the build log proves checkout of the actual PR head, Java 21, pinned LumaGuilds digest verification, current RoseChat compilation, Market tests/shadowJar/JaCoCo, Detekt, Semgrep, and Trivy. Final Market CodeRabbit status is green and zero valid inline threads remain. |
| Staff hosted state | Staff head `5b003225...` passed Wiki `31852845661`, Coverage/full build `31852845645`, and Sentinel Restart Artifact `31852845696`. Paper/Velocity runtime artifacts and provider-leak checks passed; zero live Staff inline threads remain. |
| Runtime blocker | Canonical Pi staging `31852844656` reached the verified exact Staff artifact and executed Paper, but the first server start did not reach the trusted readiness marker within the configured 240-second window. Sanitized evidence showed severe thermal/resource pressure and no ES-X03 exception or migration failure before timeout. Independent Sentinel restart job `174` for the same exact SHA also ended `RESTART_CYCLE_1_PAPER_START_TIMEOUT`. Because Paper executed, this is non-passing runtime evidence and is not eligible for the zero-execution infrastructure exception. |
| Candidate synchronization | Current Market/aggregate synchronized blobs: `MarketRestrictionJournal.kt` `83758cff61c998b8d56907b706a8339bddc78721`; build workflow `563ed55bb6f4496f2392f7bd82656922b6338c0a`; Wiki workflow `424b57cad79bee95f07cbde4546baed2fdda6453`. Component metadata remains `SYNC_PENDING` with final canonical SHA-256 pending until paired merges. |
| Privacy boundary | No private Pi/staging runner configuration, private runner labels, private bridge/dispatch implementation, secrets/topology/credentials, artifact-transfer mechanism, or Sentinel infrastructure was added to Market or any BadgersMC repository. |
| Downstream routing | `ES-X04 — EnthusiaCommend reputation provider` remains `READY` for a separate worker because ES-X03 is parked. `ES-X01` remains independently `BLOCKED` / `PARKED_BLOCKED`. This worker does not start either package. |
| Production boundary | No production listing, balance, item, private player row, database, deployment, cutover, or authority state changed. Issue #43 remains deferred and LiteBans remains authoritative. |
| Canonical handoff | `ai-agents/reports/package-handoffs/2026-08-14-es-x03-market-provider-blocked.md` |

## Unblock condition

Restore sufficient cooling/runtime capacity on the owner-controlled validation host so Paper can start within the existing trusted readiness window. Obtain live evidence that the host condition materially changed, then rerun the exact frozen Staff runtime gates. Do not weaken the readiness timeout merely to pass validation. Only after both runtime gates pass may the worker recheck the frozen PRs, merge both normally, compute canonical post-merge parity, publish terminal component/package state, and clean safely contained temporary branches.
