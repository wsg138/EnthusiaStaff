# Guided punishment release gate ledger — 2026-10-30

**Status: NO-GO. This is a review/checklist artifact, not approval or deployment instructions.**

## Source identity and scope

- This ledger is based on the published draft stack through [#754](https://github.com/wsg138/EnthusiaStaff/pull/754), exact base commit `86cf82a563e8e4635de6c3c02c3b25412c4f553a`. The individual PRs are *stacked*; do not cherry-pick/merge them independently or substitute `main` as though equivalent.
- [#753](https://github.com/wsg138/EnthusiaStaff/pull/753) is the prior committed-punishment-effect owner-thread fix, at `f3ceebadd59289acf7c68aee63e666ed191be98d`.
- Other local or unpublished follow-up branches may exist. Before a future merge, inspect GitHub and all relevant worktrees again, rebase/reconcile deliberate changes, check ownership, and regenerate exact-source evidence.
- StaffBot is a separate project. This ledger grants no permission to change its code or operate production services.
- No live SMP, test server, production/custom policy, database, Discord integration, or running service is changed by this document.

## Evidence inventory — evidence is not release authorization

| Gate | Existing source evidence | Current judgment |
| --- | --- | --- |
| Paper/domain/staff-bot/Velocity offline tests on #754 | PR description reports 2,587 passing, 15 environment-specific skips, 0 failures; bounded candidate verifier attests clean source and 570 fresh artifacts | **SOURCE QA PASS**, as reported for that exact SHA; not rerun here |
| Hosted checks on #754 | PR description records three hosted workflows successful at the exact SHA | **HOSTED CHECKS PASS**, as reported; not a runtime client test |
| Disposable MariaDB integration | #753 reports full 333-test MariaDB 11.8.3 suite passing at `f3ceebad`; #754 explicitly reports no MariaDB execution at its own new SHA | **STALE FOR #754** — rerun full database suite at the eventual final exact SHA |
| Packaged guided flag | #754's inspected shaded Paper JAR reports `guided-punishment.enabled: false` | **SAFE DEFAULT VERIFIED FOR #754**; future packaged JAR needs its own check |
| Folia plugin admission | #754 still lacks the `folia-supported` metadata opt-in and documents blocking scheduler/restore paths | **BLOCKED**; do not merely add metadata |
| Actual Java/Geyser/Bedrock GUI and role acceptance | [Java/Bedrock UX matrix](../implementation-plans/staff-punishment-gui-ux-acceptance-20261015.md) marks runtime rows NOT RUN | **NOT RUN**; do not infer from mocks |
| Owner-approved severity | [Issue #616](https://github.com/wsg138/EnthusiaStaff/issues/616) remains open; the documented profile is `prototype-20261013-unapproved` | **BLOCKED**; guided mode OFF |
| Customized production policy compatibility | [#753](https://github.com/wsg138/EnthusiaStaff/pull/753) records `evasion.ban` difference; owner approved permanent network ban on 2026-10-30 | **POLICY INTENT APPROVED; MIGRATION NOT PERFORMED** |
| Rollback and live rank/permission verification | PR #754 documents these as unverified | **NOT RUN** |

## Material policy discrepancy — must not be silently normalized

The published read-only policy comparison in #753 reports that the installed `reason-policies.yml` (`2026-09-26.1`) and bundled candidate (`2026-10-10.1`) have the same **85 reason IDs**, defaults, and severity values, but materially differ for **`evasion.ban`**:

| Source | Reason | Severity | Network-ban ladder |
| --- | --- | ---: | --- |
| Installed custom snapshot, as recorded in #753 | `evasion.ban` | 80 | 30 days → 60 days → 90 days → permanent |
| Bundled candidate, as recorded in #753 | `evasion.ban` | 80 | immediately permanent |

These are **documented snapshot findings**, not a fresh read of current live configuration. Verify the actual installed file/version and its checksum again during authorized pre-release review. The bootstrap intentionally preserves the installed customized file and warns of version mismatch; no upgrade step should overwrite it.

### Read-only on-disk policy recheck — 2026-10-30

Read the **current SMP disk file** `plugins/EnthusiaStaff/reason-policies.yml` through the existing authorized SFTP read interface (no writes, downloads, restarts, log or secrets access). Compared it with **the exact bundled source YAML** at PR #762 commit `361b9a81cd74117f8c85ae0e4c3065b482864d84` (the same bundled policy in the #754 base).

- SMP file size: **48,772 bytes**; `version: "2026-09-26.1"`.
- Candidate file size: **48,523 bytes**; `version: "2026-10-10.1"`.
- **85 reason identifiers** in both, with no ID unique to either file.
- Comparing all YAML reason blocks after omitting blank lines/comments and trailing whitespace found **one differing reason block: `evasion.ban`**. The resulting progression is exactly the four-step versus immediate-permanent difference above. The normalized defaults/header were identical other than version.
- This is a **textual on-disk source comparison**, not a complete parser-based semantic proof, not a checksum of the active runtime policy, and not evidence that current deployed processes have loaded/reloaded the file. Exact runtime identity still must be verified before cutover.
- **No decision or policy update was made.** Preserve the installed file. Approve the desired ban-evasion behavior explicitly under #616 before any migration.

**Decision:** The owner approved immediate permanent network ban for confirmed ban evasion on 2026-10-30; see the decision record below and [issue #616](https://github.com/wsg138/EnthusiaStaff/issues/616). The on-disk policy has **not** been updated. Release requires an explicit versioned migration, tested rollback, and separate authorization.

### Owner policy decision — confirmed ban evasion (2026-10-30)

**Approved rule:** For a **confirmed ban-evasion offense** (`evasion.ban`), the sanction is an **immediate permanent network ban**, including the first confirmed occurrence. No warning, temporary ban, repeat-count progression, or 30/60/90-day ladder is intended.

**Direct owner statement:** “ban evading is a perm ban.” The approval is scoped to this sanction type/duration. It does **not** automatically approve the guided questionnaire's classification, autonomous enforcement, rank/approval override, proof standard, or other severity calibration outcomes. Keep the existing review-only classification boundary and authority/audit safeguards.

**Implementation gap:** Bundled candidate v`2026-10-10.1` already contains the intended one-step permanent `NETWORK_BAN`; installed SMP v`2026-09-26.1` still has the escalating 30d/60d/90d/permanent ladder. No production file was edited or reloaded by this record. Before any rollout, implement an explicit versioned migration with backup, staging acceptance and rollback, verify loaded policy and staff authority, and obtain separate deploy authorization. Never silently overwrite an installed policy.

**Source of approval:** owner's 2026-10-30 instruction, recorded in [issue #616](https://github.com/wsg138/EnthusiaStaff/issues/616). Issue #616 remains open for all other sanctions and the overall guided-mode release gate.

## Severity examples requiring explicit adjudication

The existing [guided calibration review](../implementation-plans/guided-severity-calibration-20261015.md) reports *prototype* results with first policy step, balanced preference and the bundled `2026-10-10.1` defaults. These are **not approved outcomes**:

| Example | Prototype outcome | Required owner judgment |
| --- | --- | --- |
| Isolated public-chat spam without warning | Warning | Confirm warning/no-sanction/manual-review choice |
| Isolated public-chat spam after a *verified ignored* warning | Warning | Decide if ignored warning has any automatic effect |
| Sustained message flooding without warning | 15-hour mute | Approve exact type and duration range or manual review |
| Sustained flooding after verified ignored warning | 15-hour mute | Resolve the same-tier collision; do not invent severity automatically |
| Large-scale flooding after verified ignored warning | 2-day mute | Approve whether the step increase is allowed |
| Isolated targeted verbal harassment | 2-day mute | Approve exact classification/threshold or manual review |
| Sustained voice-channel noise | Manual review | Remain manual unless voice enforcement is actually proven |
| Cheating/X-ray, major exploit, mixed market/reputation sanctions | Manual review | Do not automatically invent bans, confiscation, permanence or unsupported remedies |
| Confirmed ban evasion (`evasion.ban`) | Prototype manual review; candidate policy single permanent network ban | **Sanction type/duration OWNER APPROVED**: permanent network ban; detection/automatic issuance **not** approved |

Record separate **no warning**, **warning obeyed**, **verified ignored warning**, and **unknown** fact states. An unverified warning does not establish defiance. These examples intentionally exclude history/rank/approval effects; production must preserve authoritative history, decay, hierarchy, approval, audit and sanction type guards. No new owner policy is proposed by this ledger.

## Mandatory isolated acceptance before any guided rollout

For the exact eventual candidate commit and exact built JAR:

1. **Source and dependency reconciliation:** Inventory the full stacked PR ancestry and any newer local branch; review changed files, conflicts, security findings, state of `main`, independently maintained StaffBot, and deployment provenance. Run the offline evidence gate freshly. Do not treat older SHA results as newer SHA proof.
2. **Database:** Run the complete MariaDB/Testcontainers integration suite using disposable storage at the exact SHA; assert a clean result and archive JUnit evidence. Include mixed-case typed sanction scoping and approval/idempotency replay checks.
3. **Runtime GUI:** On an explicitly authorized isolated server, execute every item in the existing 20-row Java/Bedrock GUI matrix with real clients, real permissions, and test identities. Record PASS/FAIL/BLOCKED per client with exact versions/evidence. Test guided OFF and, only in isolated staging, ON. Do not fabricate Bedrock evidence from Java test proxies.
4. **Role/rank safety:** Test Mod/Admin/Founder/Developer, permitted and protected targets, permission loss during queued work, Staff Mode disengagement, target promotion, draft replay, approval-rank changes, rejected storage, expired requests, double click, disconnect, server switch and process restart. Confirm blocked actions create no sanctions.
5. **Folia admission and lifecycle:** Do not advertise Folia support until the RoseChat inbound legacy scheduler contract, synchronous StaffStateCodec teleport, and `StaffModeManager.onQuit` restore-before-native-save chain have a reviewed, proven design. Changing to unawaited `teleportAsync` during quit is not a validated fix. Validate the exact third-party version and player-state persistence under disconnect/reconnect, including region ownership.
6. **Production compatibility (read-only first):** Compare the exact customized policy and any actual deployed JAR/config checksums to the candidate. Identify changes requiring operator migration and potential downgrade/rollback. Never write over installed policy as a default procedure.
7. **Decision and rollback:** Secure written owner decision on #616 (including versioned policy/profile, allowed class/tier/duration changes, manual-review-only channels). Capture rollback procedure with startup, migrations, fail-safe guided OFF, original artifact/config retention, and validation of no unintended sanctions after rollback.
8. **Operator gate:** Only after all acceptance records are signed off may a **separate, explicit** authorized deploy/restart/enablement change be considered. Passing tests or merging a draft is never implicit deployment authorization.

## Mainline divergence and merge overlap preflight — 2026-10-30

A read-only GitHub three-way ancestry/file comparison used shared ancestor `081ca8a86958499183bcfe20d255d67f57a301d5`, current main `2fa08f0e3a86d34afdf040a864f60959e6bc6370`, and the review-only stacked candidate [#763](https://github.com/wsg138/EnthusiaStaff/pull/763) head `c0efe498acc7fee489a238122c75ae02c4e5e45c` (stacked on #762/#754).

- **151 candidate-only commits** and **33 main-only commits**; candidate modifies 164 paths since merge base, main 96.
- **Two overlapping paths by file name** (overlap does not establish an actual textual merge conflict):
  1. `paper/src/main/java/net/enthusia/staff/paper/EnthusiaStaffPaperPlugin.java`. Main changes the StaffBot bridge wiring/authority predicate near original line 672; candidate adds the shared punishment approval announcement observer near line 523 and one import. Preserve both; the edits are in different sections.
  2. `paper/src/main/java/net/enthusia/staff/paper/integration/InteractiveChatStagingArtifactProvider.java`. Main has later render-liveness/expiry and inventory diagnostic changes near `snapshotAndRender`, while candidate #754 removes an unsafe global `player.isOnline()` lookup near line 264 and checks it on the owning callback near line 289. **Preserve main's newer timeout/diagnostic logic while removing the global player check.** The callback must retain the owner-thread online check and output-expiry fences; simply selecting either entire version would lose important work.
- This is a source-level *preflight*, not a tested merge. A future integration checkout should perform a real three-way merge, inspect the exact result, regenerate tests/JARs and obtain peer review. Do **not** force a merge by taking one side wholesale.
- #763 adds a target-UUID guard **on the entity owner** and one regression test; it is draft, not a production release. The local worker's newer `e92d780f` InteractiveChat render-future changes were intentionally **not** ported into #763 because they require separate ownership/integration verification.
- All above comparison results refer to these pinned Git refs. Recheck if `main` or any PR head changes.

## Exact-head follow-up: committed punishment owner dispatch — 2026-10-30

- [#763](https://github.com/wsg138/EnthusiaStaff/pull/763) now stacks on [#762](https://github.com/wsg138/EnthusiaStaff/pull/762) and contains the formerly local target-UUID fence, with behavioral tests for **global → lookup → owner → eligibility/effect** ordering. Later head `a79837f90c606a31b8da91f9f17f8c96fa30ac2b` logs an invariant diagnostic for an incorrect lookup instead of silently swallowing the committed online effect. No sanction policy or DB logic is modified.
- **A preceding #763 head `c0efe498acc7fee489a238122c75ae02c4e5e45c` had a failed Staff state-reset runtime workflow** (run `41084969759`). Log evidence shows an invalid move packet, client disconnection, and rejected game-mode restoration during the proof fixture. The earlier #762 head had passed its corresponding proof. It is unknown whether the new failure is a fixture/timing problem or a code regression; do not treat the #763 stack as green merely because its MariaDB job and Sentinel workflow passed.
- **Fresh workflows** were triggered at `a79837f90c606a31b8da91f9f17f8c96fa30ac2b`; the prior failure is not waived. Record their complete outcomes, exact source SHA, and review findings before contemplating any release or merge.
- CodeRabbit's behavioral test coverage concern is addressed in the new #763 source, but real Java/Bedrock/Folia acceptance and the whole candidate's mainline reconciliation remain outstanding.

## Owner-approved ban-evasion migration proposal — 2026-10-30

- The owner approved **permanent `NETWORK_BAN` for the first confirmed ban-evasion offense**. The full guided classification/calibration remains unapproved; see [#616](https://github.com/wsg138/EnthusiaStaff/issues/616).
- [Draft PR #764](https://github.com/wsg138/EnthusiaStaff/pull/764) implements a default-read-only migration utility, exact-byte candidate check, SHA-256 compare-and-swap guard, exclusive backup and rollback runbook. It does **not** modify the bundled policy (which already has the approved permanent step) or the production YAML.
- On 2026-10-30, its **dedicated synthetic-fixture CI run 41089825803 passed 10/10 tests** at source head `836a2099382cea040d609ba0446968015addb99e`. Later documentation-only commits should not be treated as a fresh runtime test.
- The real on-disk SMP source and candidate were compared read-only: the intended version+reason substitutions yield a byte-identical candidate. This is not a production `--apply` execution or in-game policy load test.
- **No live migration has been performed.** Before release, conduct isolated actual-runtime staging, review backup/recovery, obtain exact production deployment approval and preserve the independently customized file until the operator-controlled cutover. This approval does not unblock Folia or real Java/Bedrock guided GUI acceptance.

### Migration safety hardening and quality gate — 2026-10-30

- [#764](https://github.com/wsg138/EnthusiaStaff/pull/764) now includes failure-injection tests, verified cleanup of an incomplete backup, POSIX directory fsync for backup creation/replacement, and an explicit **owner/group mismatch refusal** before overwriting the installed policy.
- The dedicated migration safety workflow run [41094625463](https://github.com/wsg138/EnthusiaStaff/actions/runs/41094625463) passed **15/15 offline tests** at exact documentation-adjusted head `2f426d9cf225f3d07d302ebc670fcbb740bae945`. This is **synthetic-fixture** evidence, not an on-server execution or a live migration.
- Earlier Codacy review on #764 reported **7 new issues (5 high, 2 medium)** before the latest safeguards. Do not claim these are cleared until exact-head quality results have been inspected; GitHub's existing read-only annotation dump was temporarily enabled to identify detailed findings.
- Production config remains unchanged. A passing migration-script test does **not** unblock guided punishment rollout or authorize server migration. The operator must still independently verify the exact current YAML, staging, policy load, rollback, and safety gates.

### Migration static-analysis remediation checkpoint — 2026-10-30

- [Draft #764](https://github.com/wsg138/EnthusiaStaff/pull/764) received a prior Codacy report of **seven issues**. Read-only GitHub annotations identified: two cyclomatic-complexity functions, three type-checking failures in the test module's dynamic loader, and two Bandit B101 warnings for bare test asserts. These are now **addressed in source**, using smaller helpers, a normal module import and explicit test-fixture exceptions.
- Exact current PR #764 source commit `84144b18e74a577c30ef8e306cee4a2f3c3f268f` passed the dedicated [migration safety workflow 41095152765](https://github.com/wsg138/EnthusiaStaff/actions/runs/41095152765): **15 tests passed, no failures**. This is synthetic-fixture validation, not production execution.
- Codacy's final renewed assessment and the independent coverage/Sentinel results were still pending at the last inspection. Do **not** mark release gates green based on source remediation alone.
- The temporary read-only annotation workflow trigger was restored to original content; PR #764 still has four intended changed files. No server config writes or restarts.

## Mainline-reconciled release candidate (draft #765) — 2026-10-30

Created independent [draft PR #765](https://github.com/wsg138/EnthusiaStaff/pull/765), head `23e66070154eda7b4eb5e682577c4588956887d7`, **without changing main**. This is an integration-only Git merge commit with two parents: punishment stack `84144b18e74a577c30ef8e306cee4a2f3c3f268f` (through #764) and main `2fa08f0e3a86d34afdf040a864f60959e6bc6370`, shared ancestor `081ca8a86958499183bcfe20d255d67f57a301d5`.

- Git tree reconciliation read the full recursive main, candidate, and final merged trees. Verified **all 94 main-only changed blob SHAs/modes** and **all 166 candidate-only changed blob SHAs/modes** are preserved exactly. There were **two** overlapping paths, manually reconciled:
  - `EnthusiaStaffPaperPlugin.java`: main StaffBot active-duty/authority wiring preserved, candidate approval-announcement observer added.
  - `InteractiveChatStagingArtifactProvider.java`: main's render completion, expiry and inventory diagnostics preserved, global off-owner `Player.isOnline()` check removed, owning callback guard retained.
- No missing/unexpected file paths were observed after merging; candidate is **ahead of main**, with no missing main commits. GitHub reports #765 mergeable.
- A passing **source/tree equivalence** check is not behavioral validation. As of the initial read, four new workflows were triggered: aggregate coverage, MariaDB integration, Sentinel proof, Staff state reset runtime proof and migration safety (workflow matrix). Record *exact-merge-SHA* final results before any release claim.
- **NO-GO**: This combines a broad StaffBot/Velocity/Paper feature stack, not solely the punishment GUI. Independent StaffBot owner review, remaining severity approvals under #616, real Java/Bedrock GUI tests, Folia lifecycle acceptance, and explicit maintenance/deployment authorization remain necessary. Do not treat GitHub mergeability as approval.
- No server, JAR, database, config file or production service was modified.

## Integrated security and CI evidence — 2026-10-30

- [Draft PR #765](https://github.com/wsg138/EnthusiaStaff/pull/765) exact head `23e66070154eda7b4eb5e682577c4588956887d7` passed dedicated migration safety, Sentinel Artifact, Staff state reset runtime proof, and the disposable MariaDB integration job. **Aggregate coverage still required a final completion read** when this checkpoint was written. Source tree identity is independently recorded above; runtime Java/Bedrock GUI and Folia admission are **not proven**.
- Codacy compared the entire combined branch against `main` and reported **173 new findings** (its PR summary surfaced 100 including 4 critical, 14 high, 80 medium, 2 minor). This is a broad multi-feature comparison; it does not mean the two manually reconciled sources introduced all findings. Follow-up read-only diagnostics via Codacy's public PR API identified the four *critical* findings: two JUnit XML parser/DTD/XXE warnings in `scripts/qa/punishment_offline_evidence.py`, and two Semgrep variable-argv audit warnings at the intentionally invoked Gradle wrapper in `scripts/qa/punishment_candidate_verify.py`. They are **offline QA tooling**, not the Minecraft enforcement path.
- [Draft PR #767](https://github.com/wsg138/EnthusiaStaff/pull/767), stacked on #765, replaces permissive JUnit XML parsing with bounded Expat parsing rejecting DTDs/entities, sets byte/depth caps, and validates that the Gradle wrapper is local/non-symlinked and always launched with fixed offline arguments and `shell=False`. Its dedicated [offline QA safety workflow](https://github.com/wsg138/EnthusiaStaff/actions/runs/41098469129) passed **18/18 synthetic tests** at exact head `b3cd0afa190ac7bedb24cc5677028ba9a80a1c58` (11 evidence/XML and 7 verifier tests). This is source QA, **not** runtime approval. Codacy and broad CI for #767 must be rechecked before claiming its security issues resolved.
- Codacy's GitHub check exposed 50 annotations (mostly complexity and test-code findings), while the public API exposed the full 173 issues. Do not treat GitHub's 50-annotation cap as a complete security audit.
- Diagnostic [PR #766](https://github.com/wsg138/EnthusiaStaff/pull/766) is temporary, read-only and **must never be merged** into the release stack.

**No production files, servers, databases, credentials, or running services were altered by this integration/security audit. Guided punishment remains OFF and the release is still NO-GO.**

## Completed integrated offline checks and scoped critical finding fix — 2026-10-30

- **Integration draft [#765](https://github.com/wsg138/EnthusiaStaff/pull/765)** exact head `23e66070154eda7b4eb5e682577c4588956887d7`: all four triggered GitHub Actions workflows finished **SUCCESS** (aggregate Coverage/build, dedicated MariaDB tests, Sentinel Restart Artifact, Staff state reset runtime proof, and migration-safety job). The candidate contains both current main and the separate punishment stack; it remains draft, not deployed.
- **Security follow-up draft [#767](https://github.com/wsg138/EnthusiaStaff/pull/767)** exact head `cd63c423001ce2db3e3b44b640c0e685b40bd465`: dedicated [offline QA safety workflow 41098920252](https://github.com/wsg138/EnthusiaStaff/actions/runs/41098920252) passed **18/18** focused tests and exact-head Codacy completed **SUCCESS with zero new issues** relative to #765. The enhanced XML parser rejects DTD/external entities and oversized/malformed JUnit reports; the build verifier refuses missing/symlinked wrappers and uses fixed no-shell argument execution. The required trusted-source build invocation has a *documented, narrowly scoped* static-analysis exception. This is not permission to run untrusted Gradle projects.
- #767 **inherits** the exact tested #765 integration tree but adds offline QA source changes. Its own full Coverage/Sentinel workflows were **still in progress** when this note was written; do not transfer the parent commit's CI results without exact-head revalidation.
- [Diagnostic PR #766](https://github.com/wsg138/EnthusiaStaff/pull/766) was closed **unmerged** after read-only inspection of public Codacy issue metadata. The broad #765 PR still has legacy/stack-wide warnings relative to main; #767's zero new issues represents a narrow source delta, not clearance of every broad historical warning.
- Real Java+Bedrock GUI tests (20 rows), Folia lifecycle acceptance, remaining guided severity rules and isolated policy migration/rollback are **not done**. Both draft PRs and all production services remain unchanged by this review.

## Folia legacy RoseChat scheduler fix — 2026-10-30

- [Draft PR #768](https://github.com/wsg138/EnthusiaStaff/pull/768) stacks on #767 and replaces RoseChat's legacy `BukkitScheduler.runTask` inbound delivery with Paper's `GlobalRegionScheduler.execute(plugin, task)`. It retains bounded ingress authentication, dedupe, and capacity handling; adds a behavioral scheduler test and failed-scheduling retry regression. No production settings or live resources are altered.
- A dedicated source-only Folia admission CI workflow runs existing heuristic tests, rejects reintroduction of the RoseChat legacy scheduler, and rejects accidental `folia-supported: true` opt-in. This workflow does not load the actual server/plugin and does **not** claim Folia support.
- **Remaining blockers** include synchronous staff-state teleport and quit-event restoration-before-save, plus provider-specific RoseChat delivery semantics and other thread/lifecycle paths requiring isolated runtime verification. Do not simply switch teleport to asynchronous; that could break the quit restoration guarantee.
- PR #767's complete exact-head workflow set, including Coverage, Sentinel, and 18/18 QA tests, finished **SUCCESS** at `cd63c423001ce2db3e3b44b640c0e685b40bd465`; Codacy reported zero new #767 issues. PR #768 at `d39462dec81c1bff499160f369e5a6e6d8eb6b55` requires **its own exact-head CI** before any claim of passing the new scheduler fix.
- Guided punishment remains OFF. The real Java/Bedrock GUI acceptance and Folia runtime proof are still NOT RUN. Maintain DRAFT / NO-GO and do not deploy.

## Folia admission and safe disconnect recovery — 2026-10-30

- [Draft PR #768](https://github.com/wsg138/EnthusiaStaff/pull/768), latest head `3eba4904e6304754ce127cc52f84b866787d5c3c`, migrated RoseChat inbound dispatch to Paper's global-region scheduler, added owner-preserving scheduling/failure tests and source admission preflight. Source audit at an earlier #768 head passed **7/7**, showed **0 RoseChat legacy scheduler findings**, and retained two known blockers: `StaffStateCodec.restore` synchronous teleport and `StaffModeManager.onQuit` restore before native save. Codacy subsequently reported **0 new issues** at the updated #768 head. Its aggregate CI was still running at last inspection; final exact-head results must be rechecked.
- [Draft PR #769](https://github.com/wsg138/EnthusiaStaff/pull/769), stacked on #768, adds a conservative audit rule and regression fixtures: replacing quit-time `restoreSavedState` with direct `player.teleportAsync()` or `player.teleport()` is **still forbidden**. This prevents a false Folia-load-ready signal from a naive async conversion. Includes [Folia restoration design notes](https://github.com/wsg138/EnthusiaStaff/blob/fix/folia-quit-teleport-admission-20261030/docs/qa/folia-staff-state-restore-design-gates-20261030.md) requiring durable snapshot retention, protected rejoin recovery, async owner-safe teleport after join and complete verified state restoration.
- [Folia maintainer guidance](https://github.com/PaperMC/Folia/issues/219): teleport during a quit event can crash; simply making it async is not safe. [PaperMC Folia scheduler contract](https://docs.papermc.io/paper/dev/folia-support/) distinguishes global from entity ownership and must be followed.
- **Release-track distinction:** Folia opt-in should remain OFF until full lifecycle and dependent-plugin runtime acceptance. If the intended production server is **Paper**, an authorized Paper-only release can be evaluated on its own explicit gates without falsely claiming Folia compatibility. Real Java/Bedrock GUI, policy safety, staff authorization and rollback remain mandatory regardless.
- This note is design/source-only; #769's exact-head CI was pending. No live server, player state, plugin JAR, database or policy was modified.

## Reviewed Folia scanner and GUI acceptance evidence gate — 2026-10-30

- [Draft PR #769](https://github.com/wsg138/EnthusiaStaff/pull/769) addresses both CodeRabbit findings on its first scan: the quit-event teleport heuristic now binds to the actual `PlayerQuitEvent` parameter (including renamed parameters and whitespace) instead of hardcoding `event`; a documentation statement about conditional quit-session detachment was corrected. Both threads were answered and resolved. At latest source head `20f4c504bd06c5e953ba3d4849ba8738c3013684`, the source-admission workflow passed; Sentinel also passed, with Coverage still running at last inspection. **Folia runtime is NOT proven and remains NO-GO.**
- [Draft PR #770](https://github.com/wsg138/EnthusiaStaff/pull/770) adds a read-only evidence-integrity validator and 14 synthetic unit tests for all **39 applicable observations** across the Java/Bedrock 20-row matrix (A19 is Bedrock only). Evidence must include a pinned source SHA and candidate JAR digest, explicit isolated-staging assertion, Java/Bedrock/Geyser/Floodgate versions and per-PASS reviewer/timestamp/real-client attestation/checksummed screenshot or video. Failed, blocked, missing, duplicated or tampered cases cannot produce a complete validation result.
- The initial [client evidence workflow](https://github.com/wsg138/EnthusiaStaff/actions/runs/41105987904) passed 14/14 **synthetic-only** tests. Its first Codacy scan flagged 5 maintainability/type-checking issues; these were corrected in source on PR #770. Its latest head `d77f3bc34953a46eb2b7957820bba4cd0cd4b97b` has refreshed exact-commit CI/Codacy pending at the latest inspection.
- PR #770's branch was updated with a two-parent integration commit incorporating PR #769's reviewed scanner fixes; it retains only four QA/doc-specific changed files relative to the updated base. Diagnostic PR #766 was again **closed unmerged**.
- **No real Java/Bedrock gameplay acceptance was performed** in any of these checks. Even checksum-matched files cannot prove a session is authentic; a human must review footage and actual isolated staging environment before any matrix PASS is accepted. Owner still needs to explicitly authorize a staging environment for live client checks. Production remains unchanged.

## Exact-commit GUI evidence gate closeout — 2026-10-30

- [Draft PR #769](https://github.com/wsg138/EnthusiaStaff/pull/769), head `20f4c504bd06c5e953ba3d4849ba8738c3013684`: **Coverage, Sentinel, and Folia admission-preflight workflows all SUCCESS**, plus 11/11 source-only admission tests. Folia remains blocked for staff-state restore and synchronous teleport; these are not actual Folia runtime checks.
- [Draft PR #770](https://github.com/wsg138/EnthusiaStaff/pull/770), initial refactored source head `d77f3bc34953a46eb2b7957820bba4cd0cd4b97b`: **Coverage, Sentinel and 14/14 evidence-contract unit tests SUCCESS**, but Codacy had one remaining medium complexity finding.
- Subsequent PR #770 head `fe702bfc3c4c3a8fc8fe1b4fdad571968ac339ea` further splits PASS validation, uses **streaming evidence SHA-256**, and provides a create-only manifest generator initialized with **all 39 rows BLOCKED**. The latest Codacy check reported **zero new issues**. New Python tests for streaming and safe template generation have been committed, but the **expanded exact-head GitHub Actions jobs were queued** at last inspection. Do not report a new test pass until completed.
- CodeRabbit was manually requested for #770 but was **rate-limited**; no independent new CodeRabbit approval exists. Manual review should include untrusted media/path handling and the limits of self-attestation.
- Real Java and Geyser/Bedrock GUI client acceptance remains **0/39 observed**, not a passed quality gate. Passing code/tests only ensures the future evidence validator fails closed. Staging server, actual clients, reviewed footage and approved server rollout plan remain prerequisites.

## Existing TEST environment and guided client test preparation — 2026-10-30

- [Draft PR #770](https://github.com/wsg138/EnthusiaStaff/pull/770) exact earlier head `fe702bfc3c4c3a8fc8fe1b4fdad571968ac339ea`: Codacy cleared with zero new issues, Sentinel success, and the dedicated client evidence test suite **19/19 PASS** (15 validation and 4 blocked-manifest generator tests). The broad Coverage workflow was still running when inspected; do not infer its result.
- Subsequent [PR #770](https://github.com/wsg138/EnthusiaStaff/pull/770) head `f4cdbc5e72ff7ed8c3867d581422345e10e731db` improves the generated manifest with **source-derived per-scenario A01–A20 test instructions** (all 39 cases remain BLOCKED by default), adds a mapping regression test, and updates the staging guide with a read-only TEST-site assessment. Exact-head CI and Codacy must be checked afresh; older passes do not transfer automatically.
- A read-only SFTP inspection of `bloom-test1` found hundreds of existing plugin entries, multiple other projects' `UNMERGED-TEST` JARs, a TEST README for a different Market release and a Leaf-based server installation. It is a **shared and possibly active TEST host, not proven isolated for punishment GUI/ban experiments**. No SFTP write, plugin load, restart or live config modification was made.
- **Do not deploy to `bloom-test1` solely because it is called TEST.** First reserve or provision a dedicated disposable Paper environment, isolate StaffBot/proxy/durable database/webhooks/permissions, verify actual Java and Bedrock connections, use throwaway targets and obtain explicit staging authorization.
- The 39 real client observations remain **NOT RUN**. No automated media integrity test can substitute for independently witnessed gameplay or prove that self-attested captures are authentic.

## Actual isolated Paper GUI protocol smoke candidate — 2026-10-30

- [Draft PR #771](https://github.com/wsg138/EnthusiaStaff/pull/771), stacked on #770, adds a **new non-sanctioning runtime proof**. It builds the exact candidate from its PR head, launches pinned Paper 26.2 build 129 on `127.0.0.1` in a disposable GitHub Actions runner, grants a temporary identity permission attachment to an offline test player, and runs **only `/punish` without a target**.
- Server-side `InventoryOpenEvent` must show a real `PunishmentTargetPickerHolder` with a 54-slot top inventory. A separately connected Mineflayer Java protocol client must receive a 54-slot inventory window packet. The workflow verifies both results and preserves an evidence log/JAR hashes.
- The tiny probe plugin refuses to enable without a dedicated JVM opt-in, loopback binding, offline mode and max two players. The CI test has no real server plugins or remote moderation service, never clicks a sanction, never accesses live SMP, and is disposable after the run.
- **Important distinction:** This tests an automated Java protocol client only, not a real human client or Bedrock. Even if CI passes, **none of A01–A20's 39 real-client rows are marked PASS**. No Geyser/Floodgate, approval-flow or sanction outcome is claimed. The actual client matrix remains **0/39 observed**.
- PR #770 latest exact earlier head `f4cdbc5e72ff7ed8c3867d581422345e10e731db` passed the focused **20/20** evidence-contract and generator suite and Sentinel, with Codacy **0 new issues**. Its aggregate Coverage workflow remained in progress at last read. PR #771 exact-head runtime results were **still running** when this checkpoint was written; do not transfer a test pass from another SHA.
- No production JAR, server, database, config or punishment policy was modified. PR #771 remains DRAFT/NO-GO, and no full release authorization is implied.

## CI completion and isolated GUI bootstrapping finding — 2026-10-30

- **[Draft PR #770](https://github.com/wsg138/EnthusiaStaff/pull/770) fully green at head `f4cdbc5e72ff7ed8c3867d581422345e10e731db`:** exact-head Coverage, Sentinel, and the dedicated 20/20 GUI evidence-contract/generator tests all SUCCESS; Codacy reports zero new issues. This is evidence tool certification only, **not** any of the 39 real-client acceptance observations.
- **[Draft PR #771](https://github.com/wsg138/EnthusiaStaff/pull/771)** adds a real disposable Paper/Java-protocol picker smoke. Its initial exact commit `a09d43ef7920389813d7a3044b516d48dc4b22bf` **FAILED**. The test client joined but no GUI window arrived. Downloaded workflow artifact shows `PUNISH_COMMAND_ACCEPTED=true` and `FAIL=expected_target_picker_not_open`. Actual Paper server log confirms **required database environment variables were missing; destructive actions disabled**, followed by **freeze storage unavailable; joining player remains restricted**. The runtime was correctly fail-closed; no user data was lost and no sanction was issued.
- CI harness was subsequently corrected to use an **ephemeral MariaDB 11.8.3 GitHub Actions service bound to localhost** with fixed disposable credentials and actual schema/bootstrap. This preserves the production authorization and freeze guards rather than bypassing them. The amended exact head `b8b24e2351371d1b9bf07cbd3c03ec249ee6a3f3` was running when this note was written; no smoke PASS has yet been claimed.
- Both versions require a 54-slot `PunishmentTargetPickerHolder` actual Paper inventory and a matching 54-slot Mineflayer Java protocol window packet. This limited automated protocol smoke is **not** a human Java or Bedrock test. The 39 human acceptance observations remain **NOT RUN**.
- This experiment uses ephemeral GitHub runners only, not `bloom-test1`, live SMP, any shared DB, or actual moderation accounts. Do not deploy or merge based solely on the isolated smoke.

## First actual Paper GUI runtime proof completed — 2026-10-30

- **[Draft PR #771](https://github.com/wsg138/EnthusiaStaff/pull/771)** produced a first **successful ephemeral Paper GUI protocol smoke** at exact head `b1c0560cb3783af278f704ccd81b88bcb8e5835d` ([run 41115731445](https://github.com/wsg138/EnthusiaStaff/actions/runs/41115731445)). The test built the real candidate plugin, started checksum-pinned Paper 26.2 build 129 with an **isolated local MariaDB 11.8.3**, had its disposable player enter Staff Mode using **/staff**, and issued only **/punish**. Both real Paper server and automated Mineflayer Java packet evidence were observed:
  - Server: `STAFF_COMMAND_ACCEPTED=true`, `PUNISH_COMMAND_ACCEPTED=true`, `INVENTORY_OPEN|title=Punish · Select Player|size=54|holder=PunishmentTargetPickerHolder`, `PICKER_OPEN=true`.
  - Java protocol client: `GUI_PACKET_OPEN|inventoryStart=54|slotCount=90|title=...Punish · Select Player`, `GUI_PACKET_54_SLOTS=true`, final `GUI_PROTOCOL_SMOKE_PASS=true`.
- A later code head `5703f0abca2d50c8f0bb5b648dfa24912a5cb426` added bounded client-message diagnostics and **also passed the same dedicated smoke**; Sentinel succeeded at that SHA, Coverage remained in progress.
- Earlier no-database and not-on-duty test attempts correctly failed: missing database disabled destructive actions and denied freeze storage, then active Staff Mode authority was required even for an owner-rank test identity. **These production guardrails were preserved**, not bypassed.
- The initial PR #771 Codacy check reported **one new high-severity issue**. The suspected hardcoded disposable test password strings were replaced with per-run GitHub Actions values in head `2f23148709b29940b0ca39b27dc427437c6f0bc0`; Codacy and exact-head CI were still in progress, so do not yet claim the issue is resolved.
- Important distinction: this is a **real disposable Paper server, but an automated Java protocol client**. No human Java mouse, Bedrock touch/Geyser, approval, sanction, moderation history or policy v2 path was tested. **A01–A20 acceptance remains 0/39 actually witnessed**, and Folia still is NOT supported. No production changes or punishments occurred.
- Temporary Codacy diagnostic PR #766 was **closed unmerged** after read-only inspection.

## Paper GUI protocol smoke security review and reproduction — 2026-10-30

- [Draft PR #771](https://github.com/wsg138/EnthusiaStaff/pull/771) has now passed the **actual disposable Paper + MariaDB + Java protocol GUI smoke** on multiple exact heads, including `2f23148709b29940b0ca39b27dc427437c6f0bc0` ([run 41116421230](https://github.com/wsg138/EnthusiaStaff/actions/runs/41116421230)). Sentinel also passed that SHA. Test is **no-sanction** and still not a human Java/Bedrock matrix observation.
- Read-only GH check annotation against a completed earlier PR #771 head identified its **single high Codacy issue** as workflow `.github/workflows/punishment-gui-paper-smoke.yml:57` using `gradle/actions/setup-gradle@v4`, an unpinned third-party action. It was **not** a production secret leak. [PR #771](https://github.com/wsg138/EnthusiaStaff/pull/771) now pins that action to existing audited commit `3f131e8634966bd73d06cc69884922b02e6faf92`.
- Latest #771 source head `472714135943b1e0ee43fed15d4ddc2e763b1692` needs a **fresh exact-head** Codacy and CI pass. Never transfer prior SHA quality results or infer release readiness from a narrower smoke.
- The test DB passwords were also changed from fixed disposable values to per-run generated CI values. The test helper refuses non-loopback, online-mode, large-player-limit or a different database URL. No live database, policy, player data or production server was modified.
- Temporary read-only Codacy diagnostic PR #766 was closed unmerged. Folia lifecycle, production policy migration, all other severity policies and 39 real client acceptance observations remain blocked.

## Additional off-duty Paper punishment GUI boundary proof — 2026-10-30

- A completed [PR #771](https://github.com/wsg138/EnthusiaStaff/pull/771) Codacy check pinpointed the sole remaining medium warning to **`PunishmentGuiSmoke.onEnable` complexity 9 vs limit 8**, not a production security breach. The helper's disposable-only startup checks were extracted intact into a private predicate on the current draft branch.
- PR #771 head `9d885015f3468effd01f484df37ca51a1714eb10` adds a **non-sanctioning off-duty denial protocol regression** before the existing on-duty picker smoke: invoke `/punish` while ranked but not in Staff Mode, require the top inventory to *not* become the punishment picker, flag even brief unauthorized picker-open events, then enter authentic Staff Mode through `/staff` and require the real 54-slot server inventory + client packet. No production authorization rule was changed.
- The new **exact-head CI/Codacy were still in progress** at ledger checkpoint. Previous exact heads conclusively proved positive on-duty picker delivery only. Do not claim the added off-duty boundary is proven until the new run has passed.
- Temporary Codacy diagnostic PR #766 again closed without merge. All 39 actual human Java/Bedrock GUI observations are still NOT RUN. Production remains untouched and guided punishment deployment remains NO-GO.

## Confirmed Paper off-duty denial and on-duty GUI delivery — 2026-10-30

- [PR #771 workflow 41118258436](https://github.com/wsg138/EnthusiaStaff/actions/runs/41118258436) at exact source `0700bbee0f8c372037a21bbe69d4aff0cd1a01c7` **SUCCESS** on an isolated Paper+MariaDB server. Server proof: `OFF_DUTY_PUNISH_HANDLED=true`, `OFF_DUTY_PICKER_BLOCKED=true`, `STAFF_COMMAND_ACCEPTED=true`, `PUNISH_COMMAND_ACCEPTED=true`, `PICKER_OPEN=true`; Java protocol client `GUI_PACKET_54_SLOTS=true`, final `GUI_PROTOCOL_SMOKE_PASS=true`. No `FAIL=` markers.
- The off-duty `performCommand` Boolean only indicates command handling; **the absence of a picker**, including an event-time gate against transient unauthorized openings, is the meaningful negative outcome. On-duty entry uses real Staff Mode, not a backdoor.
- The initial high Codacy pinning finding and medium `onEnable` complexity finding have been addressed. Codacy's latest PR #771 summary reports **zero new issues**. At latest head `9d885015f3468effd01f484df37ca51a1714eb10`, **Sentinel and the actual GUI smoke both SUCCESS** ([runtime run 41118311655](https://github.com/wsg138/EnthusiaStaff/actions/runs/41118311655)): off-duty picker blocked, Staff Mode command succeeded, real Paper 54-slot picker opened, and Mineflayer received the matching window. Codacy reports **zero new issues**. Coverage remained in progress at latest inspection.
- This is one scenario-specific automated Java protocol regression, **not** real human Java/Bedrock approval, not any of A01–A20's 39 required observations, and not Folia support. No sanctions were applied or deployed.

## Actual Java protocol Refresh and Close control proof — 2026-10-30

- Created [draft PR #772](https://github.com/wsg138/EnthusiaStaff/pull/772), stacked on #771, to exercise **only safe control slots** of the real 54-slot Paper punishment target picker. The Mineflayer client requires the slot-49 item to be `CLOCK` (Refresh Players) and slot-51 item to be `BARRIER` (Close) before clicking. It never clicks a player head, reason, confirmation or sanction.
- The isolated Paper proof plugin observes the two `InventoryClickEvent`s and checks their actual item types, then requires a **second** `PunishmentTargetPickerHolder` opening and an actual `InventoryCloseEvent` after the Close control. The client must also see the replacement window and close packet. No changes to production command, permissions, sanctions or policy.
- The previously verified #771 off-duty denial, real `/staff` activation, `/punish` opening and 54-slot protocol window remain mandatory prerequisites. The new automated Java protocol sequence **does not fulfill any human Java/Bedrock acceptance row**; all 39 witnessed-client cases remain NOT RUN.
- **PR #772 exact-head Paper runtime proof PASSED** at `eaccfb020da7fcbd06f98e1cdd2b5a31bbead73c` ([workflow 41119756901](https://github.com/wsg138/EnthusiaStaff/actions/runs/41119756901)). Server confirmed `REFRESH_CLICK_OBSERVED`, `PICKER_REFRESH_OPEN`, `CLOSE_CLICK_OBSERVED`, and `PICKER_CLOSED_AFTER_CONTROL`; Java protocol client confirmed `GUI_CLIENT_REFRESH_CLICK`, `GUI_CLIENT_REFRESHED_PICKER`, `GUI_CLIENT_CLOSE_CLICK`, and `GUI_CLIENT_REFRESH_AND_CLOSE`. Final `GUI_PROTOCOL_SMOKE_PASS=true` with no `FAIL=` marker. Sentinel succeeded at the same commit. Aggregate Coverage remains in progress, Codacy awaits final analysis, and independent CodeRabbit review is processing. PR #771 latest existing head `9d885015f3468effd01f484df37ca51a1714eb10` has successful smoke, Sentinel and Codacy; aggregate Coverage was still running. All releases remain DRAFT/NO-GO.

## Disposable online target to categories — 2026-10-30

- [Draft PR #772](https://github.com/wsg138/EnthusiaStaff/pull/772) at exact head `eaccfb020da7fcbd06f98e1cdd2b5a31bbead73c`: [run 41119756901](https://github.com/wsg138/EnthusiaStaff/actions/runs/41119756901) **PASSED** actual Paper/MariaDB + Java protocol Refresh (slot 49 CLOCK), refreshed picker opening, Close (slot 51 BARRIER) and server/client close confirmation. Sentinel also passed. Coverage and independent review not complete at this checkpoint; this is **not** human Java/Bedrock acceptance.
- [Draft PR #773](https://github.com/wsg138/EnthusiaStaff/pull/773) extends the disposable protocol smoke to a second named offline target `GuiTarget` on localhost. After the existing off-duty/on-duty and Refresh/Close proof, the Paper helper requires exactly two accounts connected, reopens the picker and only permits the verified `PLAYER_HEAD` at content slot **9**. The client clicks that one player head; the expected outcome is a **54-slot `PunishmentGuiHolder` category overview titled `Punish · GuiTarget`** with guide sign at slot 13. The test ends without clicking a category, reason, review, confirmation or sanction.
- PR #773's exact-head test runs were **queued** when this record was added. Do not claim target-selection success until actual runtime server and client markers pass. The source changes are test harness only, not enforcement code or production settings.
- Even with a successful PR #773 protocol smoke, all **39 actual human Java/Bedrock acceptance observations remain NOT RUN**; staged Geyser/Bedrock, reviewer rank hierarchy and sanction policy are still release blockers. The system remains **DRAFT / NO-GO**, with no live server or policy changes.

## Near-term worker queue

- **P0:** Reconcile [#754](https://github.com/wsg138/EnthusiaStaff/pull/754) with newer checked-out/unpublished punishment fixes and establish a single candidate SHA without changing main or live systems.
- **P1:** Review the remaining Folia lifecycle issues as a separate design/test workstream; do not silently activate `folia-supported`.
- **P1:** Prepare owner-judgment matrix and versioned policy decisions for #616 using the above prototype cases and actual *current* custom policy snapshot; keep outcomes unapproved until explicitly signed off.
- **P2:** Run isolated Java/Bedrock client matrix when an authorized staging environment and actual clients are available.
- **P2:** Rerun *exact-candidate* DB/unit/hosted acceptance and peer review, then present a release decision with exact artifact identity and rollback.

**Change-control note:** This document adds no code, tests, policy values, configuration defaults, sanction changes or runtime approval; it preserves the current NO-GO boundary.
