# Escalation recommendation snapshots — August 2, 2026 (PR #53)

**Historical evidence only.** This guide condenses the five dated PR #53 handoffs without editing, relocating or replacing their original source. [PR #53](https://github.com/wsg138/EnthusiaStaff/pull/53) is **merged**; its handoff merge instructions, branch-cleanup instructions and pre-release/LiteBans assumptions must not be executed today.

**Provenance:** all five original files were read on `main` and independently linked at the [W4 pinned source snapshot](https://github.com/wsg138/EnthusiaStaff/blob/8d6ca75909a3568664ac543852a5ac0d94dda4b6/ai-agents/reports/agent-handoffs/2026-08-02-pr53-escalation-policy-snapshots-validation-final.md). This W8 review is tracked in [#426](https://github.com/wsg138/EnthusiaStaff/issues/426). The [current database guide](../../../database.md) describes V15 alongside later migrations; merged source/tests, not a handoff, establish today's code.

## Historical result and its boundary

PR #53 addressed a loss of historical punishment-policy context when staff overrides or later ladder edits changed the relationship between a configured recommendation and the sanctions actually applied. Flyway V15 introduced nullable `selected_ordinal` and `recommended_sanctions_json` on `punishment_steps`, preserving an exact selected step and recommendation. New rows write the snapshot with the case, applied sanctions, audit and outbox transaction; legacy rows keep both values null rather than inventing old recommendations. One-sided or malformed snapshots are rejected; the applied sanction remains the authority for its actual lifecycle.

The dated tests claimed finite-ladder clamping, override-versus-recommendation separation, restart persistence, V14-to-V15 upgrade preservation, and corrupt/partial snapshot failures. The last handoff added tested staff-readable duration formatting. These are **dated implementation/test records**, not independent evidence that any current production server is running the feature.

## Five-file sequence and classification

| Original pinned source | Content-level classification | Historical value and authority |
| --- | --- | --- |
| [Initial handoff](https://github.com/wsg138/EnthusiaStaff/blob/8d6ca75909a3568664ac543852a5ac0d94dda4b6/ai-agents/reports/agent-handoffs/2026-08-02-pr53-escalation-policy-snapshots.md) | `HISTORICAL_UNIQUE` | Original problem and initial two review corrections: selected-step ambiguity and unstable generic serialization; pre-final baseline, **not current instructions** |
| [Four-finding final iteration](https://github.com/wsg138/EnthusiaStaff/blob/8d6ca75909a3568664ac543852a5ac0d94dda4b6/ai-agents/reports/agent-handoffs/2026-08-02-pr53-escalation-policy-snapshots-final.md) | `SUPERSEDED_DUPLICATE` | Interim work-status summary including incomplete snapshot pairs and requirements-matrix restoration; superseded by terminal consolidated findings |
| [Five-finding CI iteration](https://github.com/wsg138/EnthusiaStaff/blob/8d6ca75909a3568664ac543852a5ac0d94dda4b6/ai-agents/reports/agent-handoffs/2026-08-02-pr53-escalation-policy-snapshots-ci-final.md) | `SUPERSEDED_DUPLICATE` | Interim status adds failed test-fixture evidence; terminal handoff retains that failure and its correction |
| [Eight-finding review iteration](https://github.com/wsg138/EnthusiaStaff/blob/8d6ca75909a3568664ac543852a5ac0d94dda4b6/ai-agents/reports/agent-handoffs/2026-08-02-pr53-escalation-policy-snapshots-review-final.md) | `SUPERSEDED_DUPLICATE` | Interim review/correction status (including raw-ordinal assertion, older PR #37 directions and handoff metadata); terminal handoff consolidates it |
| [Ten-finding validation iteration](https://github.com/wsg138/EnthusiaStaff/blob/8d6ca75909a3568664ac543852a5ac0d94dda4b6/ai-agents/reports/agent-handoffs/2026-08-02-pr53-escalation-policy-snapshots-validation-final.md) | `HISTORICAL_UNIQUE` | Terminal historical record: final defects, exact validation command, failed-run evidence, human-readable duration correction and focused tests |

`SUPERSEDED_DUPLICATE` here means **redundant for choosing current next actions**, not permission to delete a record. The intermediate snapshots still establish the sequence in which findings were discovered. All originals remain byte-for-byte in their existing `ai-agents/reports/agent-handoffs/` paths and can still be followed from older PR discussions.

## Historical negative evidence worth retaining

[Coverage run 30782286201](https://github.com/wsg138/EnthusiaStaff/actions/runs/30782286201), [job 91589254050](https://github.com/wsg138/EnthusiaStaff/actions/runs/30782286201/job/91589254050), on `7a01745d747aa52778d6ee723a2401de0ab9967d` **failed** four fixture validations because test identifiers contained forbidden Crockford `O` characters. It did **not** validate the database behavior or downstream packaging; later handoffs document fixture corrections. Do not count the failed run as a green build or infer final exact-head CI from these Markdown reports.

## Incoming-reference and consumer check (W8 scope)

Default-branch code search for the five exact basenames found the W4 catalog as a named cross-reference, while [`latest.md`](https://github.com/wsg138/EnthusiaStaff/blob/8d6ca75909a3568664ac543852a5ac0d94dda4b6/ai-agents/reports/agent-handoffs/latest.md) currently points to the later October 7 coordinator handoff. [`ai-agents/AGENTS.md`](https://github.com/wsg138/EnthusiaStaff/blob/8d6ca75909a3568664ac543852a5ac0d94dda4b6/ai-agents/AGENTS.md) governs **current** package handoffs generally, not these five PR #53 files by exact name. No exact-filename CI/workflow or script consumer was found in the scoped default-branch search, but that does **not** prove there are no external, historical or unindexed consumers.

**Action:** retain all five original files; use this summary as a discovery entrypoint; separately confirm any future file relocation or deletion against the complete historical and external consumer graph. No branch, release, wiki publication or runtime change is part of this archive.
