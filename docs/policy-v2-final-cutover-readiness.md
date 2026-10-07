# Policy v2 final cutover-readiness audit

Tracking: #400
Parent: #355

This document records the final W6 integration state. Policy v1 remains authoritative. Policy v2 remains a non-authoritative, additive system whose runtime modes are limited to `disabled` and `shadow`. Nothing in W6 authorizes deployment, restart, production shadow activation, or authority cutover.

## CODE COMPLETE

### Integration baseline and method

- Final W6 is reconciled from live `main` at `0091bc6f3c8242fe781725d9e4723b9799b77ffe`; the final PR must be refreshed again if `main` advances before the W6 head is published.
- Accepted W5D source: PR #418 at `4ae22b4815b23568da7a2a2699b167117594b186`.
- Accepted W5C source: PR #411 at `8f410881750a9daf45384c67bf63b5a779665ebd`.
- W5D is used as the primary integrated Policy v2 stack. Its accepted tree already contains W2, W3A, W3B, W3C, W5A, W5B, and W5D.
- W5C is ported as product delta only: the durable full-overturn domain/store/orchestrator, V30 migration, integration tests, and migration-regression update. Its obsolete W4 base is not merged.
- Concurrent live-main behavior is preserved during reconciliation rather than overwritten. In particular, current Paper startup compatibility and safe config seeding remain alongside Policy v2 runtime initialization.

### Authority boundary

- Policy v1 remains the only authoritative punishment path.
- `/punish` remains Policy v1.
- Policy v2 staff review remains a separate `/estaff policyv2` workflow.
- Policy v2 configuration supports only `disabled` and `shadow`.
- The bundled `policy-v2.yml` is `mode: disabled`, explicitly marked example/unapproved, and resolves its example offense to review rather than punishment.
- Shadow runtime can calculate outcomes, read complete Policy v2 history, store shadow evaluations, observe access/compliance conditions, and materialize sanitized public projections.
- Shadow runtime has no authority to ban, mute, kick, confiscate, cancel events/commands, deny access, mutate Market/reputation authority, or replace Policy v1 sanctions.

### Complete history and deterministic resolution

- Resolver-facing storage exposes `completeHistory(subjectId, asOf)`.
- The manual Policy v2 workflow consumes `completeHistory(...)`, not a bounded display read.
- Bounded history remains explicitly display/pagination-only.
- Complete history preserves deterministic chronological ordering and current effective finding state: overturned findings contribute nothing; reclassified findings use the replacement offense; sanction-only leniency does not alter behavioral history.
- Resolver behavior remains deterministic for the same finding, attributes, snapshot, relevant history, and incident time.
- Unknown/uncovered policy fails closed to `REQUIRES_REVIEW`; no punishment is synthesized.

### Decay and recurrence

- Exponential decay remains continuous and deterministic; non-decaying findings retain factor 1.0.
- Recurrence half-life growth and configured caps remain versioned policy inputs.
- Current implementation counts earlier related, non-overturned findings when determining the recurrence multiplier even when an ancient finding's eventual contribution at the later evaluation time is extremely close to zero. Therefore a fully/near-fully decayed ancient incident can still slow decay of a later related incident under the current model.
- Whether that behavior should remain owner policy is intentionally unresolved; W6 does not change it.

### Publication, reload, and shadow mode

The accepted W5B/W5D publication stack remains intact:

- validated atomic publication;
- duplicate-version/content collision handling;
- last-known-good fallback;
- historical snapshot retention and deterministic replay;
- restart recovery;
- concurrent reload fencing;
- stale open-review recalculation;
- `disabled -> shadow` and `shadow -> disabled` transitions;
- one shared W5B shadow gate, with no second Policy v2 feature mode.

### Access/compliance observation

- Username/profile compliance is observed from live player state without denying login in shadow mode.
- Corrected username/profile conditions may satisfy the shadow remedy lifecycle idempotently across restart/rejoin.
- VPN remains `UNKNOWN` without a reliable provider. No IP/proxy heuristic is introduced, and UNKNOWN is not treated as a violation.
- VPN condition and deliberate evasion remain distinct concepts.
- Report, Market, reputation, content, and asset capability scopes are observed/calculated without mutating live authority.

### Remedy lifecycle

W3B lifecycle behavior remains integrated:

`REQUIRED -> ENFORCED -> SATISFIED / WAIVED`

with restart recovery, idempotent operation keys, optimistic fencing, provider operation IDs, and correction lifecycle handling. Passive reads remain observational and do not create audit/history noise.

### Appeals and full overturn

Sanction-only leniency, factual reclassification, and factual full overturn remain separate operations.

Full overturn durably converges through:

`STARTED -> FINDING_OVERTURNED -> SANCTIONS_TERMINATED -> REMEDIES_CLEANED -> COMPLETED`

and converges to:

- finding OVERTURNED;
- zero future behavioral-history contribution;
- no active Policy v2 sanctions;
- no REQUIRED/enforced remedy left active;
- APPROVED / REVISION_APPLIED appeal records;
- immutable audit retained.

The W5C integration test covers failures before/after each externally significant stage, reconstructs the MariaDB-backed runtime between failures, reuses deterministic provider UUIDs, and verifies eventual one-logical-effect convergence. Concurrent leniency/reclassification fences and insufficient-authority rejection remain separate and tested.

### Canonical public projection and privacy

- Policy v2 public consumers use the canonical allowlisted public projection contract rather than `PolicyV2Store.CaseRecord`.
- Website/API and Discord adapters consume the sanitized projection seam.
- Public lifecycle publication fails closed when its bounded lifecycle read reaches the safety ceiling rather than publishing truncated history as truth.
- Private evidence, internal notes, IP/alt signals, detection internals, appeal notes, and exploit-detection details are not members of the public projection contract.
- Existing Policy v1 website/API/Discord authority remains unchanged.

### Legacy compatibility and migrations

Policy v2 is additive to existing Policy v1 records. It does not fabricate Policy v2 incident attributes/history for legacy cases and does not rewrite old Policy v1 history.

Final Policy v2 migration sequence:

- V28 — Policy v2 persistence;
- V29 — remedy/enforcement runtime;
- V30 — full-overturn orchestration.

The current live schema history has no V30+ collision. V28/V29 are preserved; V30 is additive. Migration regression now verifies the V30 table and latest version 30 while preserving existing v1 data.

The separate owner decision about whether pre-v2 behavioral history contributes to Policy v2 remains unresolved and blocks production policy cutover.

### Authorization

Existing explicit rank/action checks remain in force for:

- opening/reviewing Policy v2 shadow work;
- outcome preview;
- remedy satisfaction/waiver;
- full overturn;
- privileged remedy cleanup;
- asset restoration;
- policy-gap review.

Founder-only boundaries remain where required, including privileged asset restoration.

### Validation evidence

Accepted source heads were independently green before W6 integration:

- W5D #418 exact accepted head: hosted Coverage, Staff Bot Configuration Cache, Staff Bot PR Artifact, Sentinel Restart Artifact, and Staff-state runtime proof all passed.
- W5C #411 exact accepted head: clean Coverage/MariaDB/Testcontainers/runtime-JAR validation and Sentinel artifact passed; its accepted report records Codacy with zero new findings, diff coverage 87.8%, coverage variation +0.21%, PMD zero changed-code findings, complexity gates passing, `git diff --check` passing, and zero unresolved review threads.

W6 local reconciliation checks:

- `git diff --check`: pass.
- Domain, persistence, Velocity, and staff-bot test suites: pass.
- Policy v2-focused domain/Paper/Velocity/staff-bot suites: pass.
- Integration-test source compilation, including W5C: pass.
- Broad Paper test execution on Windows on the `0091bc6f` live-main base: 1,045/1,052 pass. An untouched `0091bc6f` worktree reproduces the exact same seven failures across 1,006 Paper tests, so they are live-main source/wiring-text baseline failures rather than Policy v2 regressions.

Before W6 may merge, the exact final PR head must still pass the repository's hosted clean Coverage/build, MariaDB/Testcontainers, runtime-JAR inspection, Paper runtime proof, Sentinel artifact/simulation, staff-bot artifact/configuration cache, Velocity checks, Codacy/static analysis, diff coverage, coverage variation, repository quality gates, and unresolved-review-thread check. Hosted CI is authoritative for the Windows source-text baseline discrepancy.

## OWNER POLICY DECISIONS STILL REQUIRED

These are content/authority decisions, not missing implementation. W6 does not invent answers for them.

1. **Player-to-player scams / trade fraud** — decide whether deceptive in-game trading is punishable or part of permissive gameplay.
2. **Real-money trading / external transactions** — decide allowed/prohibited scope and applicable surfaces.
3. **Ordinary alt accounts** — define baseline policy separately from headless alts, Market-stall abuse, and sanction evasion.
4. **Protected-area enforcement** — decide which Spawn/Market conduct is a finding versus technical block/restoration only.
5. **Malicious links/files** — reconcile malicious-file zero tolerance with the current malicious-link ladder and decide whether both share severe-safety treatment.
6. **Credible threats / doxxing** — reconcile public permanent-ban zero-tolerance language with current temporary first outcomes.
7. **Hate/extremism decay** — choose extended decay versus non-decay for severe advocacy/glorification.
8. **Non-English public chat** — define exceptions and maximum chat-only consequence; this offense itself must never become a network ban.
9. **VPN escalation** — define the boundary from access/compliance condition to deliberate VPN evasion and the allowed sanction bounds.
10. **Profile enforcement** — define restrict-until-corrected surfaces and whether deliberate repeat violations create a separate behavioral escalation.
11. **Emergency reporting while report-restricted** — define the guaranteed safety/emergency reporting path.
12. **Reasonable staff instructions** — define scope, authority, and when declining optional investigative questions is allowed.
13. **Bug-reporting window** — define a reasonable operational meaning of “immediately.”
14. **Uncertain duplicated-item possession** — approve or reject a remedy-only/confiscation path when knowledge cannot be established.
15. **Autoclickers** — confirm the rule boundary between permitted non-combat use and prohibited combat use.
16. **Politics vs extremism** — choose the final stable offense structure without allowing ordinary politics classification to absorb hate/extremist advocacy.
17. **Market compliance** — approve retirement of the generic `market.compliance-failure` selector in favor of explicit compliance facts.
18. **Legacy v1 mapping** — decide which v1 IDs become canonical aliases and which remain history-only/retired mappings.
19. **Pre-v2 history carry-forward** — decide whether Policy v1 behavioral history contributes to Policy v2 resolution after cutover.
20. **Ancient fully-decayed recurrence effect** — decide whether an ancient related incident whose direct contribution has effectively decayed away should still increase recurrence half-life for later incidents.

Until those decisions are approved and encoded in a versioned owner policy, the bundled example policy must remain non-production.

## PRODUCTION CUTOVER STEPS

W6 does not perform these steps. After owner policy approval and a green final W6 head, the future cutover sequence is:

1. Finalize the owner-approved Policy v2 taxonomy, sanctions, remedies, relationships, decay parameters, discretion bounds, and legacy mapping in versioned policy configuration.
2. Validate and publish that owner-approved policy version without making it authoritative.
3. Run a production Policy v2 shadow period while Policy v1 remains authoritative.
4. Review shadow mismatches, policy gaps, privacy output, operational behavior, and escalation/decay outcomes; revise through a new policy version where needed.
5. Obtain explicit owner approval of the final policy version and cutover results.
6. Create a separate authority-cutover PR that deliberately changes the authority boundary; do not hide authority cutover inside configuration or W6.
7. Run final exact-head build, migration rehearsal, runtime proof, rollback proof, and deployment/restart approval for that cutover candidate.
8. Deploy/restart only with explicit production approval, then explicitly activate Policy v2 authority and verify live behavior/rollback readiness.

No production deployment, restart, production shadow activation, or Policy v2 authority cutover is authorized by this document.
