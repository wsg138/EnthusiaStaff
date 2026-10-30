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
| Customized production policy compatibility | [#753](https://github.com/wsg138/EnthusiaStaff/pull/753) read-only comparison records material `evasion.ban` difference | **OWNER REVIEW REQUIRED** |
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

**Owner decision needed:** retain the installed escalating ladder, deliberately choose the bundled immediate-permanent ladder, or require manual review until a different versioned policy is accepted. Record the chosen exact ladder, policy version, rationale, and approval in issue #616 or a linked decision record. Do not infer permission from an unanswered question.

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
| Cheating/X-ray, major exploit, ban evasion, mixed market/reputation sanctions | Manual review | Do not automatically invent ban, confiscation, permanence or unsupported remedies |

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

## Near-term worker queue

- **P0:** Reconcile [#754](https://github.com/wsg138/EnthusiaStaff/pull/754) with newer checked-out/unpublished punishment fixes and establish a single candidate SHA without changing main or live systems.
- **P1:** Review the remaining Folia lifecycle issues as a separate design/test workstream; do not silently activate `folia-supported`.
- **P1:** Prepare owner-judgment matrix and versioned policy decisions for #616 using the above prototype cases and actual *current* custom policy snapshot; keep outcomes unapproved until explicitly signed off.
- **P2:** Run isolated Java/Bedrock client matrix when an authorized staging environment and actual clients are available.
- **P2:** Rerun *exact-candidate* DB/unit/hosted acceptance and peer review, then present a release decision with exact artifact identity and rollback.

**Change-control note:** This document adds no code, tests, policy values, configuration defaults, sanction changes or runtime approval; it preserves the current NO-GO boundary.
