# Repository cleanup — coordinator status

**Tracked by [issue #426](https://github.com/wsg138/EnthusiaStaff/issues/426).**  
**Kickoff:** 2026-11-11, `main` at `2d0e425ed0b59c8ecd1a2485514b873263782390`; workstream start at `8d6ca75909a3568664ac543852a5ac0d94dda4b6`. This status is a dated coordinator summary, not a real-time GitHub count.

## Completed and merged

- [#788](https://github.com/wsg138/EnthusiaStaff/pull/788) — approved six-workstream scope and frozen list of **142 open PRs**.
- [#789](https://github.com/wsg138/EnthusiaStaff/pull/789) — 7 source-controlled wiki pages; **exact-head Validate Wiki passed**. **Live wiki publication remains separate and is not confirmed.**
- [#790](https://github.com/wsg138/EnthusiaStaff/pull/790) — checked module graph, path-consumer migration plan, and external component/fork catalog.
- [#791](https://github.com/wsg138/EnthusiaStaff/pull/791) — source-linked project timeline, current spec authority guide and 289-path historic/active document catalog.
- [#792](https://github.com/wsg138/EnthusiaStaff/pull/792) — [W1 audit: 71 PRs #697+](W1-PR-AUDIT-NEWER-2026-11-11.md).
- [#793](https://github.com/wsg138/EnthusiaStaff/pull/793) — [W2 audit: 71 PRs #696 and below](W2-OLDER-PR-AUDIT-2026-11-11.md).
- [#794](https://github.com/wsg138/EnthusiaStaff/pull/794) — [W3 audit: 35 issues and 213 branch refs in its own snapshot](W3-ISSUES-AND-BRANCHES-2026-11-11.md). This count includes newly created worker branches; original kickoff had 208.
- [#795](https://github.com/wsg138/EnthusiaStaff/pull/795) — concise root README, role-based [documentation index](../README.md), and this coordinator status page; 103 internal links checked against merged sources.
- [#796](https://github.com/wsg138/EnthusiaStaff/pull/796) — preserved unique historical Velocitab handoffs from old #517 under [the historical archive](../history/archive/velocitab-cache-2026-10-11/) before closing the superseded PR.

The six original worker deliverables are **fully reported and reviewable on `main`**. Audits are provisional evidence records, not blanket permission for deletion or merging.

### Evidence-backed closures

| Original PR | Merged replacement | Decision |
| --- | --- | --- |
| [#630](https://github.com/wsg138/EnthusiaStaff/pull/630) | [#779](https://github.com/wsg138/EnthusiaStaff/pull/779) | Closed as superseded; cursor/follow/spectate source and tests retained |
| [#548](https://github.com/wsg138/EnthusiaStaff/pull/548) | [#782](https://github.com/wsg138/EnthusiaStaff/pull/782) | Closed as superseded; [performance issue #545](https://github.com/wsg138/EnthusiaStaff/issues/545) still needs real runtime acceptance |
| [#573](https://github.com/wsg138/EnthusiaStaff/pull/573) | [#785](https://github.com/wsg138/EnthusiaStaff/pull/785) | Closed as superseded; [audit issue #488](https://github.com/wsg138/EnthusiaStaff/issues/488) still needs end-to-end acceptance |
| [#517](https://github.com/wsg138/EnthusiaStaff/pull/517) | [#781](https://github.com/wsg138/EnthusiaStaff/pull/781) + archive [#796](https://github.com/wsg138/EnthusiaStaff/pull/796) | Closed after mainline source/test parity review and preservation of unique historical notes |
| [#762](https://github.com/wsg138/EnthusiaStaff/pull/762) | [#780](https://github.com/wsg138/EnthusiaStaff/pull/780) | Closed after full unified diffs matched byte-for-byte and current `main` has both resulting file blobs; dependent #763 base branch kept |

Original PR branches have **not** been deleted. The PR closure comments explain the evidence. These dispositions close PR conversations, **not** unreviewed issue acceptance or dependent branch history.

## Important unmerged / blocked work

- **Recovery and read-only diagnostics:** [#647](https://github.com/wsg138/EnthusiaStaff/pull/647) is only partly superseded by merged [#787](https://github.com/wsg138/EnthusiaStaff/pull/787). [#777](https://github.com/wsg138/EnthusiaStaff/pull/777) contains a separable console session inspector. The unmatched rankless-recovery test and vanish restoration guard need exact-head behavioral review before closure/merge.
- **Velocitab:** #517 is closed. The functional changes are on `main` via [#781](https://github.com/wsg138/EnthusiaStaff/pull/781), and unique historical handoffs were retained by [#796](https://github.com/wsg138/EnthusiaStaff/pull/796). No Velocity runtime or deployment acceptance is implied.
- **Policy drift:** #762 is closed after its entire two-file unified diff was confirmed identical to merged [#780](https://github.com/wsg138/EnthusiaStaff/pull/780), with both result blobs present on current `main`. Its branch remains because [#763](https://github.com/wsg138/EnthusiaStaff/pull/763) targets it, and #764 is stacked on #763. Do not delete/rebase those branches without independent dependency reconciliation.
- **Offline mute and unlink:** [#339](https://github.com/wsg138/EnthusiaStaff/pull/339) depends on external [RoseChat #22](https://github.com/wsg138/Enthusia-RoseChat/pull/22); [#486](https://github.com/wsg138/EnthusiaStaff/pull/486) has unique derived-alt sanction revocation. Both require source/database and dependency review.
- **Punishment stack:** [#765](https://github.com/wsg138/EnthusiaStaff/pull/765) is a large, unready integration of dependent drafts. Do not merge the whole stack as one shortcut.
- **Branches:** W3 recorded 65 distinct non-open-PR branches with unmerged commits; only six branches had `ahead_by=0` and were *conditional candidates*, not approved deletions.
- **Documentation:** 129 catalogued paths remain `UNKNOWN` for content-level archival classification. Historical records are kept rather than wholesale rewritten or discarded.
- **External source:** Site, Currency and Commend aggregate copies differ from standalone repository heads; links-only replacement and source relocation need an explicit policy change and consumer/rights review. RoseChat mirror redistribution has a separate license restriction.

## Next coordinated phases

1. **Navigation and status:** keep root README concise, link this index and the [documentation guide](../README.md). Reconcile the old operational manifests only with a tested consumer migration.
2. **PR disposition:** close more items only on a per-PR mainline equivalence and branch-dependency record; review the narrow correctness-sensitive PRs independently, especially #777 and #647.
3. **Archive:** identify content-level unique material among the 129 unknown files; move completed handoffs into `docs/history/archive/` with redirects/index updates and preserved CI/tooling references.
4. **Repo layout:** adopt the [staged module plan](MODULE-RELOCATION-PLAN.md) only with exact Gradle, CI, package, server deployment name, external repo and rollback tests. Do not touch running Paper 0.1.1.
5. **Wiki:** keep the source-controlled wiki current; protected live publication is a distinct operation and must be verified.

**Safety:** No mass PR closures, branch deletion, source module movement, provider mirror removal, production data, Cloudflare, LuckPerms, SFTP or live server changes have been approved by these audits.
