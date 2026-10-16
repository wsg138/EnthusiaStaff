# Enthusia Staff punishment/moderation — independent QA release gate (Worker D)
**Audit date:** 2026-10-15 (America/Indiana/Indianapolis)  
**Decision:** **NO-GO for production and cross-system cutover.** This is documentation/evidence, not merge or deployment approval.

## Frozen evidence and ownership

- `main` at audit: `a565616c58816815d7f4ee72bb9160d71f331b54`; the GitHub branch object reported `protected: false`; repository rulesets listing was empty. The protection-detail endpoint returned 403, so do not extrapolate any unavailable settings.
- [#581](https://github.com/wsg138/EnthusiaStaff/pull/581) merged on 2026-10-13: six primary category navigation and reason search, **not** adaptive policy replacement.
- [#597](https://github.com/wsg138/EnthusiaStaff/pull/597) draft against main at `efc41ff466a2f4870e400aaaded046d2b66ef036`: guided questionnaire and Paper GUI, default-off flag. Hosted build, MariaDB and Codacy static checks succeeded. Codacy reported 58.15% diff coverage; CodeRabbit skipped automatic review because it is a draft.
- [#612](https://github.com/wsg138/EnthusiaStaff/pull/612) stacked on #597 at `05c2e094464f40eb0e98291e0e09e07b838a1b0c`: intermediate-tier, media-compatibility, duration-floor, and reason coverage hardening. Hosted aggregate build and selected MariaDB integration checks succeeded; **Codacy Static Code Analysis = action_required (two new medium findings)**; CodeRabbit skipped because the base is not the default branch. This is not an all-green, independently reviewed PR.
- [#601](https://github.com/wsg138/EnthusiaStaff/pull/601) draft `2b5d234877de36fbc026298cd85b97cdc2098f4a`: isolated case outboxes and private adapters, not bootstrapped. [#606](https://github.com/wsg138/EnthusiaStaff/pull/606) draft `7c369a877c4e3fdc6f6839cbe900b03c3c5f115f`: canonical-authority command adapter; no optimistic local update. [#611](https://github.com/wsg138/EnthusiaStaff/pull/611) draft `bbd4f03524084a47e5bd1c9a06c669cf04484b3b`: unregistered HTTP gateway. These PRs form a **stack**, not independent activation candidates.
- [#615](https://github.com/wsg138/EnthusiaStaff/pull/615) draft `b65b6d9494f181619b813049488149ef6a28b8ea`: unregistered Paper actor proof signer, no actual current-LuckPerms role attestor. Hosted protocol-state/Via matrix **failed** at [job 116249797339](https://github.com/wsg138/EnthusiaStaff/actions/runs/38682377038/job/116249797339). Determine whether failure is related/flaky before integrated release; this does not itself invalidate isolated guided unit tests.
- [#614](https://github.com/wsg138/EnthusiaStaff/pull/614) non-draft `c99499b84ff154aa106bd9e847b38f2b85d765c2`: separate managed-role SHADOW drift correction, not permission to cut over DiscordSRV.
- [#618](https://github.com/wsg138/EnthusiaStaff/pull/618) draft: report draft cancellation/full-description GUI (overlap with Paper GUI acceptance); [#619](https://github.com/wsg138/EnthusiaStaff/pull/619) draft: staff-rank SHADOW parity harness. Verify current heads/checks again before integration, since these branches are active.
- [#580](https://github.com/wsg138/EnthusiaStaff/pull/580) is an unmerged read-only escalation simulator; [#588](https://github.com/wsg138/EnthusiaStaff/pull/588) is an unmerged safety-check GUI prototype. Neither establishes production coverage.

**Review status:** no submitted review or inline review threads were returned for #597/#612. CodeRabbit's issue comments explicitly say those reviews were skipped. Codacy comments on #612 list 2 medium (1 BestPractice, 1 ErrorProne); retrieve exact issue annotations before closing the gate.

## Reproducible independent build record

Tested `05c2e094464f40eb0e98291e0e09e07b838a1b0c` in a distinct local Windows checkout:
`C:\Users\racec\dev\qa-staff-punishment-worker-d-20261015`. No production resource, staff bot listener, server, remote database, Discord channel or SFTP target was accessed or modified.

Command:
```bat
gradlew.bat --offline --no-daemon --rerun-tasks --console=plain :domain:test :paper:test :staff-bot:test :velocity:test :paper:shadowJar
```
**Observed:** `BUILD SUCCESSFUL in 54s`, `29 actionable tasks: 29 executed`. JUnit XML summarized:
| Module | Tests | Skipped | Failures/errors |
| --- | ---: | ---: | ---: |
| domain | 310 | 0 | 0 |
| paper | 1286 | 0 | 0 |
| staff-bot | 523 | 14 | 0 |
| velocity | 234 | 1 | 0 |
| **Total** | **2353** | **15** | **0** |

The 2,338 non-skipped test cases passed. Windows skipped real Python/FastAPI API tests, POSIX filesystem/security tests and one Velocity symlink test; do not call them passed. An initial cached run also succeeded but is **not** used as the independent test evidence above.

Built, **staging-only** `paper/build/libs/EnthusiaStaff-Paper-0.1.0-SNAPSHOT.jar`: **11,518,861 bytes**, **SHA-256 `74524bb2fca1e16670c2ba61d8ac947dd3a365e242fb0d2ccd03e5c8729faf41`**. This differs from the historic obsolete artifact hash `df9520721fad8c6f6d14911301a873d9a279524e5477067834ef2eff74d99516`. Neither hash authorizes a live JAR. Compile dependencies include Paper API `26.2.build.122-stable` and the Paper module explicitly targets **Java 25**; do not use a Java 21-only runtime because a CI step happens to be labeled "Set up Java 21".

Hosted #612 [coverage/MariaDB run 38682510585](https://github.com/wsg138/EnthusiaStaff/actions/runs/38682510585) completed successfully on Linux. Its MariaDB job runs eight named integration test classes; that **does not** prove full production MariaDB or complete external FastAPI integration, nor actual Paper/Leaf/Folia/Bedrock behavior. Refresh exact-head CI before release.

## Coverage/acceptance matrix

**Evidence levels:** IMPLEMENTED = source inspected; UNIT PASS = rerun JUnit pass; HOSTED SELECTED = CI selected integration job pass; SIMULATED = fake/stub transport only; STAGING RUNTIME = isolated real server exercised; USER ACCEPTED = staff/owner in-game signed off; PRODUCTION = intentionally rolled out. Never turn a lower level into a higher one.

| Case | Observed evidence | Still required / gate |
| --- | --- | --- |
| Six primary categories, Other fallback | IMPLEMENTED; domain/GUI unit suite PASS | Java/Bedrock category screenshot and clickable back/cancel acceptance |
| Reason mapping 84 manually selectable of 85 configured (Polar template automation-only) | UNIT PASS: full reachability and configured-ID equality, including 74 static + 10 contextual mappings | Independent policy-owner classification sampling; compare production policy file and version |
| Clean/low-level spam, repeated/flooding, ignored warning | UNIT PASS branching; different impact/history outcomes and typed durations | Owner-approved quantitative calibration and in-game outcome comparison |
| Warning **stopped** / **ignored** / **not applicable** / **unknown** | IMPLEMENTED as distinct answer IDs; test confirms ignored increases impact, stopped and N/A do not. Test **intentionally** expects unknown warning alone NOT to force review or increase score | Owner must explicitly accept unknown-warning treatment; never claim uncertainty always routes to review |
| Historical escalation, expiry and decay | Guided calculator receives existing `PunishmentAssessment`; UNIT PASS simulates different selected history step | Real isolated DB fixtures for recent/old/unrelated/expired histories and configured decay weights; refresh before confirm |
| Variable temporary mute duration, limited ranges, manual stronger/shorter note | UNIT PASS interpolation/range floor and rationale gate; intermediate restricted tier veto tested | Review policy-owner bounds, seconds granularity, custom-duration permissions and actual staff review presentation |
| Ban, permanent, mixed sanction and restricted intermediate rung | UNIT PASS rejection in GuidedPolicyResolution | Red-team high-rank/approval/hierarchy, including inherited historical rung and external policy changes |
| Harassment channel sign/book/voice/profile | UNIT PASS routes to manual review; chat/DM allowed for nonsensitive route | Cross-channel in-game and attached evidence verification |
| Target rank, staff mode, permissions, live policy | Existing `workflow.preview/prepare/confirmRouted`, `targetAllowed` and catalog checks inspected | Test helper/mod/admin/owner, offline target, target changing rank, staff duty changes, race/revocation before confirm |
| Sensitive classes, unclassified/offense unknown | Source routes safety, slur, threats, privacy and selected sensitive IDs to review | Verify every sensitive policy ID, private evidence visibility and fail-closed operator behavior |
| Cancelled preview/no write | UNIT PASS workflow.preview does not persist a draft; actual GUI's `workflow.prepare` intentionally persists a **draft** before confirm | Prove no **case, sanction or irreversible** write on close/back/cancel; measure draft-only DB writes separately. Do not say "no DB writes" for every cancellation |
| Resume a saved draft, edit notes/privacy, policy version changes | Guided audit note preserved and stale version guarded in source; unit tests PASS | Actual staging restart/reload and policy-change race tests, compare candidate to current policy |
| Paper/Leaf/Folia, Java/Geyser Bedrock, inventory + moving target | Not established in this audit | STAGING RUNTIME screenshots and several-minute moving-target checks; scheduler/thread assertions |
| `/report <player>` and private report UI | Command registered in plugin descriptor; separate #618 changes draft | Real Java/Bedrock report creation/cancel/restart, permission/visibility and no-leak tests |
| StaffBot private case cards and claim/resolve | SIMULATED only in draft #601/#606/#611/#615; no production listener | Canonical revision/idempotency/proof/LuckPerms gate, authenticated two-platform delivery and private channel E2E |
| Discord/MC exact retraction, historic receipts | Main includes offline eligibility planner; no live deletion | Verify genuine receipt/source identity, exact publication, revocation/replay, permission and no wrong-message deletion in isolated endpoints |
| Production | **NOT DEPLOYED/NOT ACCEPTED by this audit** | Every release gate below plus owner sign-off |

## NO-GO gates, prioritized

1. **CRITICAL — no production toggle.** `guided-punishment.enabled: true` on `/punish` is **not a display-only shadow**: the controller can `workflow.prepare` a persistent draft, and confirm routes to `workflow.confirmRouted` for real consequences in an active system. Keep off in production; flag-on is for isolated data/players only.
2. **HIGH — unapproved policy.** Owner must approve scores, weighting, warning unknown semantics, category/channel correctness, selected duration boundaries, and escalation-vs-history behavior. Existing repo `reason-policies.yml` version is `2026-10-10.1`; historically live `2026-09-26.1`. Do **not** replace live policy with the repo default.
3. **HIGH — cross-system authority.** Canonical case actor/current role checks, single authoritative revision, outbox durability, signed delivery and retraction verification are inactive drafts. No StaffBot startup wiring, role-sync cutover, or live deletion.
4. **HIGH — staging acceptance missing.** Test isolated Paper/Leaf/Folia (match actual target versions), Java+Geyser/Bedrock, target motion, GUI inventories, report flow, staff case events and restart/recovery. Obtain recorded pass/fail, screenshots and owner acceptance.
5. **MEDIUM — CI/review.** Resolve both #612 Codacy findings; request actual CodeRabbit or human review of stacked #597+#612; diagnose #615 failing protocol matrix before integrated release. Re-check exact final merged candidate SHA; docs-only green checks are not runtime gates.
6. **MEDIUM — operational rollback.** Owner-approved inventory of JARs, YAML, migrations, DB schema/data, bridge secrets, plugins and event/outbox state; immutable pre-change backups and rehearsed restore. No unreviewed main commit/PR merge: main currently lacks reported protections.

**Release verdict remains NO-GO** until all relevant rows show dated evidence and sign-off. See `docs/release/staff-punishment-isolated-staging-rollback-20261015.md` for executable test and rollback sequence.
