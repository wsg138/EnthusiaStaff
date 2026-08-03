# PR #51 agent handoff — escalation clean-period decay

Date: 2026-08-02
Repository: `wsg138/EnthusiaStaff`
Pull request: `#51 — Fix escalation clean-period decay`
Branch: `fix/escalation-clean-period-decay`
Starting `main`: `ead1b5a02d3e8dc71eeb5ceb3c9505da1843e727`

This report records one bounded punishment-correctness fix. Exact final-head workflow IDs, hashes, analyzer results, review evidence, merge evidence and post-merge `main` evidence belong in PR #51 and must be read live.

## Live state reconciled before work

- PR #50 was already merged by normal merge commit `ead1b5a02d3e8dc71eeb5ceb3c9505da1843e727`.
- No pull request was open or in draft.
- Every pre-existing remote branch was verified `ahead_by: 0` relative to `main`; no unfinished work was displaced.
- PR #50 had zero unresolved review threads.
- PR #50 exact-head workflows `30775061520` (`Validate Wiki`) and `30775061525` (`Coverage`) completed successfully for head `e5d72a9809b7aabec39e95705e6e0a82f4a3f663`.
- The live highest migration remained `V14__punishment_history_and_exact_sanction_changes.sql`.
- The supported RoseChat private-message provider contract remained unavailable, so that blocked integration was not invented or combined with this work.

## Selected logical work item

The authoritative escalation rule uses clean days. The existing engine calculated each prior offense's decay from that individual offense's age. A recent related reoffense therefore failed to reset the older offense's clean-period clock and could make the recommendation too lenient.

This was selected ahead of lower-priority feature expansion because it is a current punishment-correctness defect, is prerequisite-complete inside EnthusiaStaff, and can be fixed without provider or production access.

## Implemented behavior

- The newest contributing, non-overturned offense in the same family now starts the clean-period clock.
- The resulting 90-day interval count is applied consistently to the related contribution set.
- A recent related reoffense resets decay for older related history.
- Non-decaying policies still report zero decay.
- Existing severity weighting, recency bonus, family filtering, overturn/contribution filtering, future-end filtering and final-step ladder clamping remain unchanged.
- Focused tests cover 89-day, 90-day and 180-day boundaries, recent-offense reset, shared clean-period decay, non-decaying policy behavior and the existing filtering/recency behavior.

## Material files

- `domain/src/main/java/net/enthusia/staff/domain/escalation/EscalationEngine.java`
- `domain/src/test/java/net/enthusia/staff/domain/escalation/EscalationEngineTest.java`
- `ai-agents/WORKSPACE-STATE.md`
- `WORKSPACE-MANIFEST.md`
- `docs/wiki/pages/Development-Blueprint.md`
- this immutable handoff and `latest.md`

## Persistence and migration boundary

- No schema change was required.
- No migration was added or edited.
- V1 through V14 remain immutable.
- The expected next migration number remains V15 unless live repository state shows a newer legitimate migration.

## Commands, permissions and configuration

- No command, permission, configuration key, provider contract or runtime dependency changed.
- No active punishment or stored case is rewritten; the fix affects future authoritative recommendation evaluation.

## Separate harsh review

The complete PR diff was reviewed for:

- requirement alignment with clean-day semantics;
- preservation of related-family filtering and explicit contribution removal;
- overturned and future-ended offense handling;
- severity weighting and 30-day recency behavior;
- exact 89/90/180-day boundaries;
- non-decaying policies;
- finite-ladder clamping;
- null/empty history behavior;
- Java 21 compatibility and absence of persistence/threading side effects;
- test claims and documentation boundaries.

No additional merge blocker or confirmed defect was found before the tracked-content freeze. Any later analyzer, human-review or CI finding must be resolved before merge and exact-head validation repeated if tracked files change.

## Validation contract

After tracked content is frozen, PR #51 must record direct evidence for:

- exact final feature head and branch/base relation;
- Java 21 clean Gradle build and all configured tests;
- MariaDB/Testcontainers and migration/checksum results;
- aggregate coverage and configured upload;
- Paper and Velocity runtime JAR integrity, hashes and provider API leak checks;
- static-analysis and wiki validation results that actually ran;
- current human/bot review status and zero unresolved valid review threads;
- exact-head Pi result, or an explicit statement that it did not run;
- normal merge result, resulting `main`, feature-head containment and honest branch-cleanup result.

Results from older heads, merge refs, skipped jobs or different revisions must not be represented as exact final-head evidence.

## Preserved boundaries

This work does not:

- deploy a JAR or access production systems, credentials, databases, backups or player evidence;
- activate EnthusiaStaff moderation authority;
- disable, remove or replace LiteBans;
- run issue #43 production acceptance;
- edit an existing Flyway migration, add a migration or use Flyway repair;
- push directly to `main`, rebase, squash, force-push or enable automatic merging;
- implement or reflect against the unavailable RoseChat provider API.

## Remaining work

The broader escalation requirement remains partial. Separate future work still includes versioned modular escalation configuration, explicit aliases for renamed IDs, removed-ID presentation, policy snapshot behavior across ladder edits, serious-offense decay metadata and wider combined-recommendation acceptance.

RoseChat private-message evidence remains externally blocked until a supported provider contract exists.

## Next recommended work

After verifying PR #51 live, first resume RoseChat only if its supported provider contract has become available. Otherwise complete one prerequisite-ready escalation-policy slice, beginning with versioned aliases and removed-ID readability, after fresh live reconciliation. Do not begin that work inside PR #51.
