# ES-X01 — RoseChat provider and communication integration — BLOCKED

Date: 2026-08-26

## Terminal classification

`ES-X01` is `BLOCKED` / `PARKED_BLOCKED` after a valid `ACTIONABLE_CONTINUATION`.

The historical repository-resolution blocker changed, so this worker resumed X01. Live GitHub now verifies the supported provider as public `wsg138/Enthusia-RoseChat`, default branch `master`, with reconciliation head `8fcca5420b0f54207d6efa332327b9fd18edb8d8`.

Implementation then stopped at a newly verified license/public-aggregate boundary. This is not a product failure and is not being mislabeled as a validation pass.

## Live reconciliation

- Staff selection/base head: `37a2073b535cf32f89b2fc075699dca4e3420408`.
- Standalone: `wsg138/Enthusia-RoseChat`.
- Standalone default branch: `master`.
- Standalone verified head: `8fcca5420b0f54207d6efa332327b9fd18edb8d8`.
- GitHub repository state: public, writable by the connected account, fork=true.
- Fork chain: `wsg138/Enthusia-RoseChat` → `BadgersMC/Enthusia-RoseChat` → source `Rosewood-Development/RoseChat`.
- Provider `AGENTS.md`: none present in the verified source tree.
- Existing X01 implementation branches/PRs: none in Staff or RoseChat at reconciliation.
- Concurrent Staff work preserved: X03 PR #139, D04 PR #151, and D05 PR #160 were not modified, rebased, synchronized, merged, closed, or replaced. Website work was not touched.

## Provider/source findings

The verified RoseChat source exposes its existing chat/channel/message/event/ignore/social-spy implementation, but does not currently expose the `dev.rosewood.rosechat.api.staff` provider contract already present in EnthusiaStaff's `integration-contracts` module. Staff's current `RoseChatIntegration` is therefore still waiting for provider-side implementation.

The standalone repository's checked-in Rosewood Development `LICENSE` permits use, copy, modification, and merge while expressly excluding rights to publish, (re)distribute, sublicense, or sell copies. GitHub identifies the supported repository as a fork of the upstream RoseChat fork network; no checked-in exception or redistribution authorization was found in the verified repository/PR state.

`wsg138/EnthusiaStaff` is public. Canonical `BRANCH-AND-MIRROR-POLICY.md` requires an external-component package to publish a designated aggregate component copy and, after both normal merges, compare that aggregate directory against the standalone repository with `tools/component-sync/component_sync.py`. The parity comparison excludes only `.git` and aggregate-only `COMPONENT-METADATA.md`, so satisfying X01 as currently defined requires publishing the RoseChat source tree in a second public repository.

This worker does not infer that GitHub's fork mechanism grants permission to republish the source outside the fork network. No aggregate source import was attempted.

## Work performed

Only orchestration/state documentation was changed on Staff state branch `state/es-x01-license-blocker-20260826`:

- resolved `COMP-ROSECHAT` to the verified repository/default branch/head;
- replaced the obsolete unresolved-repository blocker with `BLOCKED_LICENSE_REDISTRIBUTION`;
- updated the X01 package resume/checkpoint/blocker/unblock record;
- updated workspace routing and the canonical package registry;
- updated component metadata and the universal handoff.

No RoseChat implementation branch was created. No Staff implementation branch was created. No provider source, product source, tests, migrations, workflows, runtime configuration, secrets, PM data, Discord implementation, website implementation, deployment, production route, authority state, or LiteBans cutover state changed.

## Exact unblock

Resume X01 only after one of these is durably and explicitly established:

1. verified license terms or written authorization represented in durable project/repository authority that permits publishing the required RoseChat source tree in public `wsg138/EnthusiaStaff`; or
2. an explicitly authorized canonical package/mirror-policy redesign that removes the republication requirement while retaining deterministic supported-source verification and does not weaken review/source-traceability guarantees.

Do not treat an informal assumption, repository ownership of the fork, or the existence of the GitHub fork itself as sufficient redistribution authorization.

After unblock, reconcile live heads again, create the normal same-ID implementation branches/PRs in both repositories, implement the provider and Staff behavior, run every applicable exact-head build/test/static/review/Sentinel/Pi/staging gate, merge both by normal merge commits only, prove the authorized synchronization model, clean contained temporary branches, and publish `COMPLETE`.

## Validation applicability

There is no X01 provider/product implementation head for this continuation. X01-specific provider behavior tests and distributed-runtime acceptance are therefore not applicable to this state-only continuation and are not claimed as product passes.

PR #165 nevertheless triggers EnthusiaStaff's repository automation against the unchanged Staff product tree. Those automatic Coverage/build, Sentinel artifact, and Pi source-build/staging-control results are recorded truthfully as state-publication validation evidence only; they do not prove the missing RoseChat provider implementation and do not resolve the license boundary. A failed, skipped, queued, superseded, or runtime-non-applicable result is never relabeled as passing X01 product evidence.

The first Sentinel artifact attempt on initial state head `738f46e78bd3ca0dae6bddb2fcafc5c9efce5c75` failed before compilation because the Gradle wrapper download was reset by the network (`java.net.SocketException: Connection reset`). That failed attempt remains non-passing history; an unchanged-SHA retry was requested rather than changing source to address an infrastructure download failure.

## State-publication evidence

State branch: `state/es-x01-license-blocker-20260826`.

State-publication PR: EnthusiaStaff #165, `[ES-X01] Publish RoseChat license redistribution blocker`.

The final merge-candidate SHA and terminal exact-head check/review results are recorded in PR #165 metadata/body after the state branch is frozen. This handoff intentionally does not require embedding its own final commit SHA inside the commit that contains it; PR metadata is the durable exact-head validation ledger. Initial/superseded workflow results remain historical evidence and are not reused for a later head.

## Production boundary

No production PMs, player data, database contents, Discord token/configuration, website deployment, migration execution, server deployment, LiteBans authority change, issue #43 acceptance, or cutover was accessed or changed. LiteBans remains authoritative and issue #43 remains open.

## Stop condition

After the state-only PR is validated under its applicable exact-head gates, merged normally, and containment is verified, this worker stops. It does not select another universal package in this run.
