# ES-D16 moderation real-data read bridge — merge-pending checkpoint

Status: `MERGE_PENDING`
Repository: `wsg138/EnthusiaStaff`
Implementation PR: #187
Implementation branch: `package/es-d16-moderation-read-bridge`
Frozen reconciled executable head: `8811294c17532825aeae1d271fe2a3163042ba9c`
Owner-accepted D16 UI candidate: `3a79000eaa139ec107118d3fdb05b29e5e52097c`

## Owner acceptance

The owner reviewed the current moderation UI and stated: `The UI looks good.`

Live GitHub confirms the accepted D16 implementation remained unchanged through checkpoint `a0bec2d4071ee46c8f55bea1ade7cb03cd021960`: all four post-candidate commits were documentation/process-only. The required later merge from current `main` preserved D16 product files. The owner acceptance therefore clears the prior visual/live-UI blocker; the merged current-main runtime state is separately subject to fresh exact-head automation.

No signed launch URLs or tokens, Discord bot token, Cloudflare secrets, backend request signatures, credentials, private message bodies, or private moderation records are recorded here.

## Moving-main reconciliation

At reconciliation, `main` was `06519c0c5acdcf6276278204201f3c8b20767805` and the D16 checkpoint head was `a0bec2d4071ee46c8f55bea1ade7cb03cd021960` with common base `423e72c764c9acfce6bb80918f07367fea2cfccf`.

Fresh comparison found 29 current-main paths, no exact path collision with the D16 product diff, and no Flyway migration collision. The worker created normal two-parent merge commit `8811294c17532825aeae1d271fe2a3163042ba9c` with parents:

1. `a0bec2d4071ee46c8f55bea1ade7cb03cd021960`;
2. `06519c0c5acdcf6276278204201f3c8b20767805`.

A post-write comparison from the old D16 tip to `8811294c...` contains exactly those 29 current-main paths and nothing else. No rebase, squash, force push, migration renumbering, or concurrent-package absorption occurred.

## Exact-head evidence at reconciled executable

For exact head `8811294c17532825aeae1d271fe2a3163042ba9c`, terminal evidence already available when this checkpoint was prepared:

- Moderation Web Validation `34765815458`: **SUCCESS**.
- Staff Bot PR Artifact `34765815488`: **SUCCESS**. Exact-head artifact `10320286377`, name `staff-bot-pr-187-8811294c17532825aeae1d271fe2a3163042ba9c`, ZIP digest `sha256:9a92c853ffee5b13889dd03742e494326c6eaaa95ba0447325b6985d91d35a9e`.
- Staff Bot Configuration Cache `34765815539`: **SUCCESS**.
- Sentinel Restart Artifact `34765815467`: **SUCCESS**.
- Pi Staging Supersession `34765813881`: **SUCCESS**.
- Codacy Static Code Analysis check `103746859043`: **SUCCESS**, zero annotations / zero new valid findings.

Coverage `34765815457` was still executing and is **not** counted as passing in this checkpoint.

CodeRabbit's automatic status on `8811294c...` said that a manual review was required. That skip is **not** counted as an exact-head review pass. A real CodeRabbit re-review is required on the final pre-merge head.

## Protected staging refresh

The permanent Moderation Web Staging Deploy workflow is push-path filtered to moderation-web assets, preview assets, or its workflow file, and also supports `workflow_dispatch`. The moving-main merge itself did not touch one of those paths, so no protected staging run was fabricated or inferred for `8811294c...`.

This checkpoint accompanies a durable operator clarification in `moderation-web/README.md`; because that file is under `moderation-web/**`, the checkpoint push intentionally causes the protected Cloudflare staging workflow to execute on the new documentation-only pre-merge head. That run must finish successfully and prove the fixed private-read transport checks before merge.

## Product boundary remains unchanged

D16 remains read-only/simulation-only. It may read real Discord, linked-account, sanctions/history, cases, and private-note data through the bounded authenticated read bridge, but it does not perform warnings, mutes, kicks, bans, restrictions, reversals, message deletion, Discord permission overrides, punishment/case/note mutation, Minecraft enforcement, LiteBans mutation, or production Discord cutover.

The architecture remains browser → Cloudflare same-origin Worker → signed/replay-resistant request → fixed private Bloom moderation-read origin. Server-side actor/guild/target binding, rate limiting, private/no-store responses, Message Content entitlement fencing, zero unnecessary Gateway intents, bounded Discord REST reads, and independent evidence/violating/future-delete selection remain required.

## Concurrent work preserved

ES-D13 PR #178, ES-X03 PR #139, website/competition/wiki/provider/hosting work, and Market work remain separate. ES-D07 is not started in this worker.

## Exact next action

Treat the upcoming checkpoint commit as documentation-only relative to frozen executable `8811294c...`. Wait for every applicable final-head hosted/staging gate to become terminal, inspect any failure or analyzer finding, obtain a real CodeRabbit exact-head review with no unresolved valid thread, re-read `main`, update PR #187 to terminal truth, and merge #187 normally only if all conditions are green. Then prove containment/health, clean safe temporary state, publish the durable `COMPLETE` handoff/status, and stop.