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
- [#798](https://github.com/wsg138/EnthusiaStaff/pull/798) — Folia-safe sanction-change replies; three head workflows passed, source merged but no live release or JAR deployment.
- [#799](https://github.com/wsg138/EnthusiaStaff/pull/799) — preserved Policy v2 OTHER-remedy design proposal from retired #467 without activating Policy v2.
- [#801](https://github.com/wsg138/EnthusiaStaff/pull/801) — Staff session read-only console inspector, rankless-handoff tests, and vanish-restoration guard; final-head Coverage/MariaDB, protocol and Sentinel workflows passed. Runtime TEST/production acceptance still unverified.
- [#802](https://github.com/wsg138/EnthusiaStaff/pull/802) — classified five PR #53 historical handoffs and indexed their provenance; 289-path baseline now has 124 `UNKNOWN` files.
- [#803](https://github.com/wsg138/EnthusiaStaff/pull/803) — preserved byte-identical 445-line MARKET-02 PR #647 handoff and original registry/status provenance in the [Staff recovery historical archive](../history/archive/staff-recovery-market02-2026-10-20/).

The six original worker deliverables are **fully reported and reviewable on `main`**. Audits are provisional evidence records, not blanket permission for deletion or merging.

### Evidence-backed closures

| Original PR | Merged replacement | Decision |
| --- | --- | --- |
| [#630](https://github.com/wsg138/EnthusiaStaff/pull/630) | [#779](https://github.com/wsg138/EnthusiaStaff/pull/779) | Closed as superseded; cursor/follow/spectate source and tests retained |
| [#548](https://github.com/wsg138/EnthusiaStaff/pull/548) | [#782](https://github.com/wsg138/EnthusiaStaff/pull/782) | Closed as superseded; [performance issue #545](https://github.com/wsg138/EnthusiaStaff/issues/545) still needs real runtime acceptance |
| [#573](https://github.com/wsg138/EnthusiaStaff/pull/573) | [#785](https://github.com/wsg138/EnthusiaStaff/pull/785) | Closed as superseded; [audit issue #488](https://github.com/wsg138/EnthusiaStaff/issues/488) still needs end-to-end acceptance |
| [#517](https://github.com/wsg138/EnthusiaStaff/pull/517) | [#781](https://github.com/wsg138/EnthusiaStaff/pull/781) + archive [#796](https://github.com/wsg138/EnthusiaStaff/pull/796) | Closed after mainline source/test parity review and preservation of unique historical notes |
| [#762](https://github.com/wsg138/EnthusiaStaff/pull/762) | [#780](https://github.com/wsg138/EnthusiaStaff/pull/780) | Closed after full unified diffs matched byte-for-byte and current `main` has both resulting file blobs; dependent #763 base branch kept |
| [#467](https://github.com/wsg138/EnthusiaStaff/pull/467) | historical design preserved via [#799](https://github.com/wsg138/EnthusiaStaff/pull/799) | Closed without activating policy or provider operations |
| [#479](https://github.com/wsg138/EnthusiaStaff/pull/479) | equivalent current-main CRLF test helper | Closed after four-file content comparison; branch preserved |
| [#480](https://github.com/wsg138/EnthusiaStaff/pull/480) | [#798](https://github.com/wsg138/EnthusiaStaff/pull/798) | Closed as semantically superseded; dependent #481 base branch retained |
| [#777](https://github.com/wsg138/EnthusiaStaff/pull/777) | [#801](https://github.com/wsg138/EnthusiaStaff/pull/801) | GitHub closed alongside #801 merge; all 16 original inspector changed-file additions accounted for and final-head tests passed |
| [#647](https://github.com/wsg138/EnthusiaStaff/pull/647) | [#778](https://github.com/wsg138/EnthusiaStaff/pull/778), [#787](https://github.com/wsg138/EnthusiaStaff/pull/787), [#801](https://github.com/wsg138/EnthusiaStaff/pull/801) + archive [#803](https://github.com/wsg138/EnthusiaStaff/pull/803) | Closed after source/test lineage reconciliation and unique historical handoff preservation; original branch retained |

Original PR branches have **not** been deleted. The PR closure comments explain the evidence. These dispositions close PR conversations, **not** unreviewed issue acceptance or dependent branch history.

## Important unmerged / blocked work

- **Recovery and read-only diagnostics:** #647 and #777 are closed as superseded/archived. Merged [#801](https://github.com/wsg138/EnthusiaStaff/pull/801) now carries the console-only inspector, missing-vanish restoration guard and rankless-handoff regression tests on current `main`, without overwriting #787 recovery leases. Source CI passed; real client TEST/BUILD and production acceptance have not occurred. Historical [MARKET-02 handoff](../history/archive/staff-recovery-market02-2026-10-20/README.md) is archived.
- **Velocitab:** #517 is closed. The functional changes are on `main` via [#781](https://github.com/wsg138/EnthusiaStaff/pull/781), and unique historical handoffs were retained by [#796](https://github.com/wsg138/EnthusiaStaff/pull/796). No Velocity runtime or deployment acceptance is implied.
- **Policy drift:** #762 is closed after its entire two-file unified diff was confirmed identical to merged [#780](https://github.com/wsg138/EnthusiaStaff/pull/780), with both result blobs present on current `main`. Its branch remains because [#763](https://github.com/wsg138/EnthusiaStaff/pull/763) targets it, and #764 is stacked on #763. Do not delete/rebase those branches without independent dependency reconciliation.
- **Offline mute and unlink:** [#339](https://github.com/wsg138/EnthusiaStaff/pull/339) depends on external [RoseChat #22](https://github.com/wsg138/Enthusia-RoseChat/pull/22); [#486](https://github.com/wsg138/EnthusiaStaff/pull/486) has unique derived-alt sanction revocation. Both require source/database and dependency review.
- **Punishment stack:** [W9 #800](https://github.com/wsg138/EnthusiaStaff/pull/800) ports owner-thread/target-UUID checks and has passing exact-head Actions but **one unresolved HIGH ErrorProne Codacy finding**; it remains draft and unmerged. [#764](https://github.com/wsg138/EnthusiaStaff/pull/764) has a separate migration-tool failure-boundary concern. [#765](https://github.com/wsg138/EnthusiaStaff/pull/765) is a large, unready integration of dependent drafts. Do not merge the whole stack or apply a production migration as a shortcut.
- **Branches:** W3 recorded 65 distinct non-open-PR branches with unmerged commits; only six branches had `ahead_by=0` and were *conditional candidates*, not approved deletions.
- **Documentation:** 124 original scoped paths remain `UNKNOWN` following W8's five-file classification. Historical records and active agent files remain preserved; original branch/handoff sources have not been mass-deleted.
- **External source:** Site, Currency and Commend aggregate copies differ from standalone repository heads; links-only replacement and source relocation need an explicit policy change and consumer/rights review. RoseChat mirror redistribution has a separate license restriction.

## Next coordinated phases

1. **Navigation and status:** keep root README concise, link this index and the [documentation guide](../README.md). Reconcile the old operational manifests only with a tested consumer migration.
2. **PR disposition:** close more items only on a per-PR mainline equivalence and branch-dependency record; resolve #800's exact static finding before its merge, then separately reconcile #754/#764 and preserved punishment stack branches.
3. **Archive:** classify the remaining 124 `UNKNOWN` baseline paths in small reviewed batches; retain originals and preserve CI/tooling references, using source-linked historical archive indexes rather than wholesale deletion.
4. **Repo layout:** adopt the [staged module plan](MODULE-RELOCATION-PLAN.md) only with exact Gradle, CI, package, server deployment name, external repo and rollback tests. Do not touch running Paper 0.1.1.
5. **Wiki:** keep the source-controlled wiki current; protected live publication is a distinct operation and must be verified.

**Safety:** No mass PR closures, branch deletion, source module movement, provider mirror removal, production data, Cloudflare, LuckPerms, SFTP or live server changes have been approved by these audits.
