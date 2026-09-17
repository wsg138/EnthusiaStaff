# ES-D07 — Discord punishment enforcement

Status: `COMPLETE`. Priority: 136. Depends on `ES-D03`, `ES-D05`, `ES-D06`. Internal package.

## Assignment and terminal identity

- Selected by the Discord-program worker on 2026-09-14 after live reconciliation.
- Package start/base `main`: `074c0ae0bee7222bcd5c9096f8db0071f5b84cdf`.
- Implementation PR: #201.
- Frozen executable product head: `aea6696cb97df4463b90abfcbbd8bfa4bb80b913`.
- Final pre-merge PR head: `e9d8a904c4192c9a4ab5fdfe34df8d2590567a95`.
- Normal implementation merge: `ca949a8531ea39efdf1becccc052b9c59cf30d24`.
- Merge parents are exactly pre-merge `main` `074c0ae0bee7222bcd5c9096f8db0071f5b84cdf` and final feature head `e9d8a904c4192c9a4ab5fdfe34df8d2590567a95`.
- Post-merge comparison proves exact containment: the merge is one commit ahead of the feature head with zero file differences.
- Temporary implementation branch `package/es-d07-discord-punishment-enforcement` is absent after merge.
- ES-D13 remains independently `BLOCKED` / `PARKED_BLOCKED` on PR #178 and was not modified, synchronized, merged, closed, or absorbed by D07.
- PR #199 Paper staff-menu work and PR #139 / ES-X03 Market work remain independent.

## Objective

Implement authoritative Discord-only punishment actions with durable external enforcement and recovery.

## Scope delivered

Warning; temporary/permanent managed-role mute preserving ticket/support access; kick; temporary/permanent native guild ban; temporary/permanent channel/category read-only/no-access restrictions; unmute/unban/unrestrict/end/revoke/overturn flows; duration parser/presets; confirmation and immediate reauthorization; reason/explanation/message-delete options; temp expiry; DMs and delivery outcomes; native-ban reconciliation; quick commands calling the same services; durable outbox/worker semantics and partial-failure truthfulness.

Permanent Discord ban/mute/restriction requires Admin+ under approved policy. Discord native Timeout is not the normal mute mechanism. Approval flows re-check concrete sanction authority at decision time, including current rank, target protection, consequence type, permanent/custom flags, and duration ceiling.

## Persistence and collision boundary

D01/D02 already merged the V19 Discord moderation schema, including `moderation_enforcement_targets`, `discord_reconciliation_state`, and `discord_maintenance_work`. D07 uses those durable primitives and the existing Discord identity model. D07 adds no Flyway migration.

## Exclusions

No automatic Minecraft punishment, AutoMod enforcement, cross-platform `Both` orchestration, production deployment/cutover, production Discord configuration/data mutation, LiteBans authority change, or issue #43 acceptance.

## Frozen validation contract and executable evidence

The required contract was frozen at package claim and included deterministic success/rejection/authorization/hierarchy/retry/recovery/expiry/idempotency/conflict/partial-failure/shutdown coverage; approval-escalation tests; MariaDB integration/recovery; convergence of quick-command and panel paths; immediate final reauthorization; explicit public projections; full build/static/review validation; and destructive Discord staging only where an authorized non-production harness was available.

Frozen executable product head `aea6696cb97df4463b90abfcbbd8bfa4bb80b913` passed review-repair workflow `34925882460`, job `104243730808`:

- Temurin Java `21.0.12+1`.
- full `clean build` plus `:staff-bot:verifyStaffBotRuntime` — PASS.
- repository PMD — PASS with zero findings.
- changed-Java Lizard — PASS at `CCN <= 8`, practical method length `<= 50`, and argument count `<= 8`.
- `DiscordPunishmentWorkerTest` bounded relevant line count — 431, PASS.
- `git diff --check` — PASS.

The four substantive CodeRabbit findings were repaired with regression coverage: invalid removal-state transitions/no-effect expiry, custom-duration maximum handling, JDA-compatible signed-long Discord ID validation, and current managed-mute ownership during reconciliation/removal. All four corresponding review threads were answered, independently rechecked where applicable, and resolved. CodeRabbit's generic docstring-coverage warning is advisory and is not a D07 correctness blocker.

Codacy Static Code Analysis on executable head `aea6696cb97df4463b90abfcbbd8bfa4bb80b913`, check `104245761385`, succeeded with zero annotations and reported that the pull request was up to standards. No broad analyzer exclusion was added.

## Final state-only head validation

The exact compare `aea6696cb97df4463b90abfcbbd8bfa4bb80b913` → `e9d8a904c4192c9a4ab5fdfe34df8d2590567a95` is three commits changing exactly three `ai-agents` Markdown tracking files:

- `ai-agents/reports/agent-handoffs/latest.md`;
- `ai-agents/reports/package-handoffs/2026-09-14-es-d07-active.md`;
- `ai-agents/work-packages/packages/ES-D07.md`.

No product source, product test, migration, workflow, build configuration, runtime configuration, dependency, artifact contract, or executable input changed in that delta. Executable evidence remains attributed to `aea6696c...` under `VALIDATION-POLICY.md`.

For exact final pre-merge head `e9d8a904c4192c9a4ab5fdfe34df8d2590567a95`, the normal hosted PR workflows completed successfully:

- Coverage `34926790460` — SUCCESS.
- Staff Bot PR Artifact `34926790540` — SUCCESS.
- Staff Bot Configuration Cache `34926790404` — SUCCESS.
- Sentinel Restart Artifact `34926790424` — SUCCESS.
- final state-only Codacy Static Code Analysis check `104247092167` — SUCCESS with zero annotations and “Your pull request is up to standards!”.
- all four substantive CodeRabbit threads — resolved; zero valid unresolved D07 review threads remained before merge.

## Staging availability

Isolated destructive Discord staging is `NOT RUN / unavailable`, not a pass. Live reconciliation found no authorized non-production D07 destructive target, fixture, or harness in the trusted staging repository. The package contract frozen at claim requires destructive staging only where such an authorized non-production harness is available. Production Discord is never a substitute. No production Discord action, configuration change, or data mutation was attempted.

## Merge, containment, and cleanup

PR #201 merged by normal merge commit `ca949a8531ea39efdf1becccc052b9c59cf30d24`; no squash, rebase, force push, or auto-merge was used. The merge has the exact expected two parents, and compare from feature head `e9d8a904...` to merge `ca949a85...` has one commit and `files []`, proving exact product/state containment.

The implementation branch is gone. Temporary branch `tmp/es-d07-codacy-inspect-20260914` contains only disposable D07 inspection/repair scripts/workflow state and no unique product work; it is safe to delete. The connected GitHub mutation surface exposes no branch-delete action, so that cleanup remains an explicit tooling limitation rather than unmerged D07 work.

## Completion checklist

- [x] Reconcile live `main`, registries, open PRs/branches, migrations, issue #43, and concurrent ownership.
- [x] Confirm dependency-complete D07 was the eligible Discord package while parked D13 remained untouched.
- [x] Claim one temporary implementation branch from exact live `main`.
- [x] Implement the complete D07 domain/application/persistence/JDA runtime surface with separated validation, persistence, orchestration, and presentation.
- [x] Add deterministic unit and MariaDB integration/recovery tests for required paths.
- [x] Perform harsh self-review and repair confirmed defects.
- [x] Open and complete the single D07 implementation PR.
- [x] Freeze one reviewed executable candidate and pass clean-build/static repair validation.
- [x] Complete final exact-head hosted CI, Codacy, and review with zero valid unresolved findings.
- [x] Reconcile current `main` before merge.
- [x] Merge PR #201 normally only after required gates were terminal/green or correctly non-applicable/unavailable under the frozen contract.
- [x] Prove exact containment and implementation-branch cleanup.
- [x] Publish terminal `COMPLETE` state without beginning another implementation package.

## Terminal routing

D07 is complete and has no active worker. `ES-D09` now has all package dependencies complete and is dependency-complete `READY` for a future Discord worker, but is not activated or started here. `ES-D08` remains `PLANNED` because its separate required live proof that current Minecraft moderation services can accept the integration without changing production authority has not been established. `ES-D12` remains `PLANNED` because D09 is not complete. `ES-D13` remains `BLOCKED` / `PARKED_BLOCKED` on its original DiscordSRV parity condition. This worker stops after D07 terminal publication.
