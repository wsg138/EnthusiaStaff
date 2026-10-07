# OWNER-OFFLINE-DISCORD-MUTE

## Assignment and boundaries

Owner directed this bug fix with "Work on that": linked Discord senders who are offline in Minecraft must not be blocked solely because the join-session mute cache is absent.
Authoritative Staff base: `ceb12e0f`; RoseChat provider base: `cd0290b` on `wsg138/Enthusia-RoseChat:master`.
Status: PARTIAL / ACTIONABLE_CONTINUATION. Isolated branch: `package/owner-offline-discord-mute`.
Existing Market, investigation and Discord packages are preserved. No merge, production upload, restart, authority change, database migration or configuration change is authorized.
This repair changes the existing Staff-owned integration contract and a matching provider callback in its existing fork network. No RoseChat source is imported into Staff; ES-X01's public aggregate-copy licensing blocker remains unchanged.

## Spec

- OD-01: WHEN a linked Discord message arrives, Staff SHALL asynchronously verify authoritative MUTE and PUBLIC_MUTE sanctions independently of Minecraft online presence before delivery.
- OD-02: IF lookup is unavailable, fails, times out, exceeds bounded admission or the integration closes/changes, the message SHALL remain blocked. Late completions SHALL NOT authorize delivery.
- OD-03: Minecraft chat cache behavior, public/private mute distinctions, freeze audience, vanish, ignore/spy, existing filters and linked-rank formatting SHALL remain unchanged.
- OD-04: Existing constructor descriptors and callbacks SHALL remain binary compatible. The additive async Discord callback SHALL default to the existing mute callback for older bridge implementations.
- OD-05: Lookup SHALL use the existing bounded Staff worker executor without blocking a server or region thread. Outstanding underlying work SHALL remain bounded even after caller timeout.

## SPEAR and evidence

Spec -> prove -> engine -> arch -> refine. No project-local EARS validator or SPEAR-specific state updater was found; this manual requirement/task/evidence record is used. The existing generic orchestration/Wiki validators are run separately and do not validate EARS behavior.
Failure evidence: the installed Staff bridge calls `cachedStatus`; quit removes cache; missing/expired cache returns UNVERIFIED, which blocks. This is distinct from intentional vanished-sender filtering, which is retained.
Regression tests are added before implementation. Source contract parity, focused/full tests, hosted checks and review must be recorded separately; local tests are not live acceptance.

## Tasks

- [x] Inspect/fetch current authoritative source and preserve existing checkouts.
- [x] Inspect existing integration, operational requirements, package state and live open PR overlap.
- [x] Prove offline lookup, mutes, timeout, overload, shutdown and legacy callback behavior.
- [x] Implement minimal bounded asynchronous ingress and review thread/lifecycle contracts.
- [ ] Validate, publish paired reviewable PRs and inspect exact-head checks/review.
- [ ] Test real offline linked delivery and muted/vanished suppression on an authorized test server.

Completion is pending reviewed canonical delivery and applicable gates. Production remains untouched.

## Local validation checkpoint

- Red before implementation: focused Staff test compilation failed because `DiscordMuteVerifier` did not yet exist. No historical behavioral red run is fabricated.
- Staff focused Discord/RoseChat suite: 27 tests, zero failures/errors/skips; Java 25 toolchain with Java 21 release and warnings-as-errors. Paper runtime JAR built. Its unmerged local SHA-256 is `3EFD623D3471B833E5EFF93509E99B4CE001A0BBF3125614579E63E788DD08D8`.
- Staff full Paper suite: 931 tests, five failures. The exact same five source-text wiring failures reproduce on unchanged base `ceb12e0f` (16 baseline tests, five failures). They match CRLF-sensitive multiline assertions; no unrelated Staff product or test behavior is changed here.
- Full root clean test/check/runtime run: failed on 75 MariaDB Testcontainers initialization failures because local Docker is unavailable. This is not a full-validation pass; hosted exact-head validation remains required.
- RoseChat Java 21 build/test succeeds; new async callback is additive. Existing API descriptors are retained, and normalized contract sources match exactly between provider and Staff. Staff does not shade provider API classes.
- Delivery returns to RoseChat's async scheduler, with registration revision checks before feedback and before delivery. Minecraft cache, freeze, recipient visibility and ignore/filter paths remain unchanged. No offline cache is retained by the new verifier.
- No database, server, configuration, merge, deployment or client acceptance occurred.
- Wiki validator passed (41 pages). Generic orchestration validator reports 460 errors on both this checkout and unchanged `ceb12e0f`; the error lists are identical. Existing orchestration debt is retained, not relabeled as passing or expanded into this repair.
