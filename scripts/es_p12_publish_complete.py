from pathlib import Path
import re

PACKAGE = Path("ai-agents/work-packages/packages/ES-P12.md")
REGISTRY = Path("ai-agents/work-packages/PACKAGE-REGISTRY.md")
HANDOFF = Path("ai-agents/reports/package-handoffs/2026-09-24-es-p12-staff-operational-hardening.md")

MERGE = "753ef35496c15abe361cad51704b610af540b0f0"
ACCEPTED = "e29bbab530f47473d9dbc9d7d60f57ec73dadea6"
PRODUCT = "b26eca2cd18bbd4148a02e9307fd81ada21a6fd3"
TREE = "5471919009529ec675708d60d49de3cf2ec11bd5"
BASE = "fd999968ed5ffbd2e47e041482dc9e936528d7a7"


def replace_once(text: str, old: str, new: str, label: str) -> str:
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"{label}: expected exactly one match, found {count}")
    return text.replace(old, new, 1)


def replace_section(text: str, heading: str, next_heading: str, body: str) -> str:
    pattern = re.compile(
        rf"{re.escape(heading)}\n.*?(?=\n{re.escape(next_heading)}\n)",
        re.DOTALL,
    )
    replacement = f"{heading}\n{body.rstrip()}\n"
    updated, count = pattern.subn(replacement, text, count=1)
    if count != 1:
        raise SystemExit(f"section {heading}: expected one match, found {count}")
    return updated


package = PACKAGE.read_text()
package = replace_section(
    package,
    "## 2. Status",
    "## 3. Objective",
    f"`COMPLETE` — PR #246 merged normally as `{MERGE}` from exact accepted head `{ACCEPTED}`. "
    f"Executable product head `{PRODUCT}` is contained unchanged; terminal documentation publication is the only remaining bookkeeping step.",
)
package = replace_section(
    package,
    "## 11. Required PR",
    "## 12. Implementation checklist",
    f"PR #246 merged normally into `main` as `{MERGE}` from accepted head `{ACCEPTED}`. "
    "No squash, rebase, force-push, auto-merge, or direct `main` push was used.",
)
package = replace_once(
    package,
    "- [ ] Run exact-head hosted clean build/tests/check/runtime-JAR, migration validation, static analysis, coverage, and Codacy after the latest executable repair; resolve all valid findings.\n- [ ] Reconcile concurrent PR changes and external review before final merge transition.",
    "- [x] Run exact-head hosted clean build/tests/check/runtime-JAR, migration validation, static analysis, coverage, and Codacy after the latest executable repair; resolve all valid findings.\n- [x] Reconcile concurrent PR changes and external review before final merge transition.",
    "completion checklist",
)
package = replace_section(
    package,
    "## 23. Resume state",
    "## 24. Exact-head evidence",
    "Terminal package state. Product implementation and acceptance are complete; do not resume ES-P12 implementation work. "
    "Canonical terminal handoff: `ai-agents/reports/package-handoffs/2026-09-24-es-p12-staff-operational-hardening.md`.",
)
package = replace_once(
    package,
    "\n## 25. Private acceptance boundary",
    f"\n- Final normal-actor acceptance head `{ACCEPTED}` passed Coverage `35938430400`, Validate Wiki `35938430413`, Sentinel Restart Artifact `35938430464`, Sentinel simulation 5/5, Pi staging supersession, and Codacy static with zero annotations; all visible review threads were resolved.\n"
    f"- PR #246 merged normally as `{MERGE}`. The merge parents are `{BASE}` and `{ACCEPTED}`, and both the accepted head and merge commit have tree `{TREE}`, proving exact containment with no conflict-resolution drift.\n"
    "\n## 25. Private acceptance boundary",
    "terminal acceptance evidence",
)
package = replace_section(
    package,
    "## 26. Merge and synchronization record",
    "## 27. Remaining package work",
    f"PR #246 merged normally as `{MERGE}` from accepted head `{ACCEPTED}` after live `main` was re-read at `{BASE}`. "
    f"The merge commit's second parent is the accepted head and both trees are `{TREE}`. PR #220's `VanishManager.persistState()` work was rechecked as hunk-disjoint from ES-P12's visibility/online-count additions; PR #244 had no exact changed-file collision. No ES-P12 migration exists.",
)
package = replace_section(
    package,
    "## 27. Remaining package work",
    "## 25. Private acceptance boundary" if False else "## 999. never",
    "",
) if False else package
# Last section has no following heading; replace directly.
package = re.sub(
    r"## 27\. Remaining package work\n.*\Z",
    "## 27. Remaining package work\nNo implementation, validation, review, merge, or synchronization work remains. This terminal publication records completion only.\n",
    package,
    count=1,
    flags=re.DOTALL,
)
PACKAGE.write_text(package)

registry = REGISTRY.read_text()
registry = replace_once(
    registry,
    "`ES-P01`, `ES-P02`, `ES-P03`, `ES-P04`, `ES-P05`, `ES-P06`, `ES-P07`, `ES-P08`, `ES-P09`, `ES-P10`, `ES-P11`, `ES-X02`, `ES-X04`, `ES-X05`, `ES-R01`, `ES-R02`, and `ES-V01` are `COMPLETE`.",
    "`ES-P01`, `ES-P02`, `ES-P03`, `ES-P04`, `ES-P05`, `ES-P06`, `ES-P07`, `ES-P08`, `ES-P09`, `ES-P10`, `ES-P11`, `ES-P12`, `ES-X02`, `ES-X04`, `ES-X05`, `ES-R01`, `ES-R02`, and `ES-V01` are `COMPLETE`.",
    "registry complete list",
)
old_p12 = (
    "`ES-P12 — Staff operational hardening` is `VALIDATING / ACTIONABLE_CONTINUATION` on "
    "`package/es-p12-staff-operational-hardening`, PR #246. Final executable head "
    "`b26eca2cd18bbd4148a02e9307fd81ada21a6fd3` includes the bounded CodeRabbit repair batch plus "
    "the exact freeze-generation notice fence; gated workflows `35936723820` and `35937174002` "
    "passed their applicable complexity, PMD, Paper test, runtime-JAR, and diff checks before publication. "
    "Normal-actor exact-head repository acceptance, Codacy, review reconciliation, and final concurrent-path "
    "reconciliation remain. Canonical handoff: `ai-agents/reports/package-handoffs/2026-09-24-es-p12-staff-operational-hardening.md`."
)
new_p12 = (
    f"`ES-P12 — Staff operational hardening` is `COMPLETE`. Executable product head `{PRODUCT}` was followed only by "
    f"package-state Markdown through accepted head `{ACCEPTED}`. Exact-head Coverage `35938430400`, Validate Wiki "
    "`35938430413`, Sentinel Restart Artifact `35938430464`, Sentinel simulation 5/5, Pi staging supersession, Codacy "
    f"zero-annotation static analysis, and resolved review threads passed. PR #246 merged normally as `{MERGE}`; the "
    f"accepted and merge trees are identical at `{TREE}`. Canonical terminal handoff: "
    "`ai-agents/reports/package-handoffs/2026-09-24-es-p12-staff-operational-hardening.md`."
)
registry = replace_once(registry, old_p12, new_p12, "registry ES-P12 summary")
registry = replace_once(
    registry,
    "Canonical ES-P12 current handoff: `ai-agents/reports/package-handoffs/2026-09-24-es-p12-staff-operational-hardening.md`.",
    "Canonical ES-P12 terminal handoff: `ai-agents/reports/package-handoffs/2026-09-24-es-p12-staff-operational-hardening.md`.",
    "registry handoff label",
)
registry = replace_once(
    registry,
    "| `ES-P12` | Staff operational hardening | `VALIDATING` | `ACTIONABLE_CONTINUATION` | 95 | current merged Staff runtime | PR #246 on `package/es-p12-staff-operational-hardening`; executable head `b26eca2cd18bbd4148a02e9307fd81ada21a6fd3` gated green; normal-actor exact-head acceptance/review pending |",
    f"| `ES-P12` | Staff operational hardening | `COMPLETE` | — | 95 | current merged Staff runtime | executable `{PRODUCT}`; accepted `{ACCEPTED}`; PR #246 merged normally as `{MERGE}` with exact tree containment |",
    "registry ES-P12 index row",
)
REGISTRY.write_text(registry)

HANDOFF.write_text(f'''# `ES-P12` package handoff — 2026-09-24

- Package ID: `ES-P12` — Staff operational hardening
- Canonical status: `COMPLETE`
- Starting Staff SHA: `{BASE}`
- Frozen executable product head: `{PRODUCT}`
- Final accepted PR head: `{ACCEPTED}`
- Implementation PR: #246, merged normally as `{MERGE}`
- Standalone PRs: `NOT_APPLICABLE`

## Completed work

The owner-requested staff operational hardening is complete: staff hierarchy protection, `/staffwho`, freeze/unfreeze staff alerts and frozen-player context, vanish-safe broadcasts/ping presentation, RoseChat-absent PM mute fallback, punishment-ladder context, clean staff-mode exit verification, no-currency item confiscation, and bare-Paper disconnect presence tracking.

Repeated review repaired scheduler/thread-ownership and correctness defects in `/staffwho`, vanished-player ping handling, freeze staff fanout, frozen movement orientation, read-only ban enforcement, disconnect retry behavior, staff-mode recovery fencing, and freeze notice delivery. The final notice path carries the exact freeze runtime generation, preventing release→re-freeze from reviving an older queued notice.

## Final validation

- Repair workflow `35936723820`: Lizard bounds PASS; repository PMD 6.55 PASS; full `:paper:test` PASS; `runtimeJars` PASS; `git diff --check` PASS.
- Freeze-generation fence workflow `35937174002`: Lizard bounds PASS; repository PMD 6.55 PASS; full `:paper:test` PASS; `runtimeJars` PASS; `git diff --check` PASS; published executable `{PRODUCT}`.
- Final normal-actor accepted head `{ACCEPTED}`: Coverage `35938430400` PASS, including full runtime build/tests, aggregate JaCoCo, runtime-JAR inspection, artifact upload, and Codacy coverage upload.
- Validate Wiki `35938430413`: PASS.
- Sentinel Restart Artifact `35938430464`: PASS.
- Sentinel simulation: PASS, 5/5 cases.
- Pi staging supersession: PASS.
- Codacy static: PASS with zero annotations; seven issues reported solved.
- Final visible review-thread count: zero unresolved.

Earlier failed, skipped, cancelled, superseded, or wrong-head checks remain historical non-passing evidence and were not relabeled as passes.

## Merge and containment

PR #246 merged normally as `{MERGE}`. Its parents are `{BASE}` and exact accepted head `{ACCEPTED}`. The accepted head and merge commit both use tree `{TREE}`, proving exact containment with no conflict-resolution drift.

Immediately before merge, live `main` was still `{BASE}` and PR #246 remained mergeable. PR #220's `VanishManager.persistState()` change was rechecked as file-level but hunk-disjoint from ES-P12's visibility/online-count additions. PR #244 had no exact changed-file collision with ES-P12. No migration was added by ES-P12.

## Remaining work

None for ES-P12 implementation, validation, review, or merge. This terminal publication only synchronizes package-state documentation. The former implementation branch is fully contained by `main`; if branch deletion is desired, it is cleanup only and not a package blocker.

## Systems and files not to disturb

Do not absorb ES-X03, Discord program packages, issue #216 repair packages, PR #220's transaction-owned vanish persistence work, or PR #244's mute scheduling repair. Their owners must reconcile against the new `main` independently.

## Private/production boundary

No production deployment, cutover, authority transfer, live player-data mutation, destructive production test, or private-data publication was performed or authorized by ES-P12.
''')
