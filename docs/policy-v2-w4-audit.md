# Policy v2 W4 integration and adversarial audit

Tracking: #361 under #355.

## Integration basis

W4 integrates these exact package heads onto live `main` without activating Policy v2:

- W1 #365 — `7e29685c96f04b29c11a24e2a1daca1115d05dbe`
- W2 #373 — `7be23a1fcf1a63ff2e1948540b897085823bf87d`
- W3A #385 — `97616019c90e1b0f401f42f17762a0711796001a`
- W3B #387 — `dd206f180a819f5ae4b09b0223185a74d57222b4`
- W3C #386 — `f528afbce03cbb1816b701b87ba2fec7e3ff77d0`

The package stack was 140 commits behind `main` when W4 began. A path-level comparison found zero overlap between those intervening main changes and the files changed by W1/W2/W3, so W4 constructed one composite integration commit on current main. Current `main` already contains `dump-codacy-annotations.yml`; W3B only adds `workflow_dispatch` to that diagnostic workflow. W4 temporarily reused the W3B variant to expose the exact hosted Codacy annotation, then restored the file byte-for-byte to the `main` version so the audit introduces no workflow delta.

## Authority and coexistence

Policy v1 remains authoritative. W4 also records a shadow evaluation against a legacy case and verifies that no authoritative Policy v2 case or behavioral-history row appears.

W3A's Policy v2 controller is not registered by the live Paper command registrar. Its only confirmation path calls `submitShadow`, which records a W2 shadow evaluation and does not create an authoritative case or call the live punishment service. This is a safe coexistence boundary, but it also means there is not yet an operationally accessible production shadow workflow.

W3B and W3C likewise provide enforcement/public contracts without replacing live v1 runtime wiring.

## Adversarial scenario matrix

| Scenario | W4 status | Evidence |
| --- | --- | --- |
| First offense | Covered | W4 domain matrix verifies zero prior contribution. |
| Second offense immediately | Covered | W4 domain matrix verifies near-full prior contribution. |
| Second offense after partial decay | Covered | W4 domain matrix verifies exact half-life behavior. |
| Recurrence after a long clean period | Covered / decision required | Direct contribution decays near zero; see repeat-slowdown semantic decision below. |
| Several rapid offenses | Covered | W4 verifies adaptive half-life growth and configured cap. |
| Repeated offenses after prior decay | Covered / decision required | Current contract still counts an old decayed offense toward later repeat slowdown. |
| Alternating related offenses | Covered | W4 verifies explicit cross-offense relationship weights. |
| Unrelated offenses | Covered | W4 verifies exclusion despite recency. |
| More-serious historical offense | Covered | W4 verifies an explicitly stronger severe-history relationship can outweigh ordinary related history. |
| Non-decaying history | Covered | W4 verifies contribution survives a multi-year clean period. |
| Multiple appeals | Covered | W4 persistence test records separate appeal lifecycles against one case. |
| 2-day leniency reduction without factual change | Covered | W4 persistence test reduces a five-day sanction to three days and verifies finding/history identity is unchanged. |
| Factual downgrade | Covered | W2 integration coverage reclassifies the effective finding while preserving original audit history. |
| Full overturn | Covered | W2 integration coverage removes future history contribution without deleting audit history. |
| Changed policy version | Covered at domain/storage layer | W4 verifies version-specific deterministic replay and rejects reuse of one version label for different immutable policy content; operational live reload remains blocked. |
| Brand-new unclassified conduct | Covered | W4 verifies fail-closed `policy-gap.unknown-offense` review. |
| Stale review recalculation | Covered | W3A re-resolves policy/history immediately before shadow submission and returns stale review on change. |
| Restart/idempotency/concurrency/rollback | Covered | W2/W3B integration suites exercise restart recovery, replay, operation collisions, optimistic concurrency and rollback. |
| Username/profile/VPN/remedy lifecycle | Covered at service/storage layer | W3B tests correction, recurrence separation, authorization, restart recovery and no-history-pollution reads. |
| Public/private separation | Covered | W3C canonical allowlist cannot represent private evidence, actor IDs, linkage/detection signals or hidden history math. |

## Findings and blockers

### Production blocker: bounded history reads can erase non-decaying severe history

W3A currently requests at most 200 entries through `PolicyV2ManualWorkflow.HISTORY_LIMIT`. W2 implements that request as the newest 200 Policy v2 cases using `ORDER BY incident_at DESC, case_id DESC LIMIT ?`. There is no cursor/pagination or policy-aware retained summary on the current W2 boundary.

After 200 newer cases, an older non-decaying severe finding can therefore be absent from the history supplied to W1. The same truncation can change adaptive repeat counts. Raising the constant would only move the failure point.

W4 reported this foundational defect to W2 PR #373 and W3A PR #385. Shadow operation and cutover are blocked until the history contract guarantees that semantically relevant history cannot be silently omitted and includes a >200-case regression test with an older non-decaying finding.

### Production blocker: no approved Policy v2 policy publication/reload path

W1 intentionally supplies the validated immutable `PolicySnapshot` contract but no owner-approved production policy dataset or runtime loader/publisher. W3A consumes a `Supplier<PolicySnapshot>`, so test-time snapshot replacement proves stale-review recalculation but not operational config reload.

Before shadow operation or cutover, the owner-approved W0 content must be converted into versioned policy data and loaded through a validated publication path. Reload must retain old snapshots for deterministic replay and must fail closed if validation fails.

### Production blocker: shadow mode is safe but not operationally wired

The current W3A controller is intentionally not registered. This proves Policy v2 cannot punish anyone accidentally, but it also prevents a real shadow evaluation period. After approved policy publication exists, add an explicit shadow-only feature gate that exposes evaluation without any authoritative sanction/remedy mutation, then run it long enough to compare v1 decisions with v2 results.

### Production blocker: full-overturn runtime orchestration is not yet atomic/recoverable

W2 correctly records factual overturns and removes future behavioral-history contribution. W2 sanction revisions and W3B remedy waiver/cleanup are deliberately separate primitives. No integrated Policy v2 appeal orchestrator currently guarantees that a successful full overturn also ends/revokes the active sanction and waives or cleans up every enforced remedy as one recoverable operation.

This is not a reason to couple the storage models. Before cutover, the appeal integration must coordinate the separate streams with durable idempotency/recovery so a crash cannot leave an overturned finding with an active punishment or compliance restriction.

### Production blocker: W3B/W3C end-to-end runtime integration is intentionally absent

W3B's service, persistence and authorization tests pass, but its own integration contract states that nothing is registered into live Paper/Velocity. The access coordinator, capability gates, provider adapters, remedy completion signals and confiscation lifecycle still require a shadow/non-authoritative runtime integration before W4 can claim end-to-end username, VPN, report, Market or reputation enforcement behavior.

W3C similarly provides the canonical safe projection and adapters while leaving the live v1 website/Discord consumers unchanged. Before cutover, a runtime publisher must materialize the canonical projection and public consumers must read only that projection/adapter boundary.

This absence is safe during development because it prevents accidental v2 authority. It is still a cutover blocker because service-level tests are not a substitute for final runtime wiring tests.

### Owner decision: recurrence slowdown after old history has decayed

The current W1 contract counts every earlier related finding when calculating adaptive half-life for a later finding. Therefore an ancient offense whose direct contribution has effectively decayed to zero can still increase the half-life of a later offense. This behavior is deterministic and documented by W1, but W0 does not define whether a long clean period should reset or reduce that repeat-offender slowdown.

The owner must explicitly choose one of these semantics before production policy is final. W4 does not change it locally.

### Owner decision: legacy v1 history continuity

W2 correctly preserves pre-v2 cases without fabricating Policy v2 metadata, and its migration tests verify those cases remain readable. The current v2 behavioral-history query intentionally returns only cases with Policy v2 metadata. Therefore a cutover would ignore all pre-v2 behavioral history unless a separate bridge is approved.

Owners must explicitly choose whether Policy v2 starts with a behavioral-history clean slate. If prior confirmed v1 history should carry forward, use a versioned conservative mapping/adapter from legacy reason IDs to approved v2 relationships; do not reconstruct old incident facts from the current policy.

### Owner policy matrix is not final

W0 identifies 18 owner decisions before the content can be called final. They include trade fraud/RMT/ordinary-alt policy, protected-area handling, malicious-content severity, credible-threat/doxxing zero-tolerance inconsistency, hate/extremism non-decay, non-English-chat limits, VPN escalation boundaries, profile enforcement semantics, and emergency reporting access while restricted.

These are policy prerequisites, not implementation failures.

## Cutover prerequisites

1. Resolve the W0 owner decisions and approve the exact Policy v2 offense/relationship/decay/sanction/remedy data.
2. Add and validate a versioned production policy loader/publication/reload path; failed reloads must leave the prior valid snapshot authoritative.
3. Fix the W2/W3A bounded-history defect so older non-decaying findings and repeat-count inputs cannot be truncated.
4. Decide whether legacy v1 behavioral history carries into v2 and, if so, implement an owner-approved versioned bridge without fabricating old incident facts.
5. Reconcile/land W1, W2, W3A, W3B and W3C on current main with clean CI and zero new valid Codacy findings.
6. Expose W3A through an explicit shadow-only runtime gate and complete a real evaluation period with no authoritative Policy v2 mutations.
7. Integrate W3B access/capability/remedy observers in shadow or otherwise non-authoritative mode, then exercise real login/access/provider lifecycle paths without mutating live v1 authority.
8. Materialize W3C's canonical public projection and route website/Discord/API consumers through its allowlisted adapters; do not let public consumers query private v2 records directly.
9. Add durable Policy v2 appeal orchestration so full overturn ends/revokes sanctions and waives/cleans enforced remedies with idempotent crash recovery.
10. Review shadow mismatches, including long-clean-period repeat slowdown, and resolve every foundational defect in its owning package.
11. Re-run migration upgrade/restart/idempotency/concurrency/rollback tests on the final merged stack.
12. Re-run privacy regression tests and verify every public consumer uses the canonical W3C projection/allowlist.
13. Re-run authorization and remedy-lifecycle tests on the final runtime wiring.
14. Require explicit owner approval for authority cutover in a separate change. Do not combine cutover with this audit PR.
15. Deploy/restart only after the separate cutover change is approved; W4 performs neither.

## Validation

W4 CI results are recorded on PR #390. Hosted Codacy initially flagged the shared persistence integration test after W4 additions pushed it to 519 non-comment lines. W4 split its scenarios into `PolicyV2PersistenceAdversarialIntegrationTest` instead of suppressing the valid finding. Final exact-head CI/Codacy on the rebased W4 head remains the release gate for this audit PR.
