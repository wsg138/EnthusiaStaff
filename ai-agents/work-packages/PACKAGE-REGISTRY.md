# Package registry

Last updated: 2026-08-10

Live GitHub overrides stale text. Historical evidence is retained in each package file and its canonical handoffs; this registry is the current routing authority.

## Rules

- Classify every incomplete package before selection and work exactly one package per worker.
- Existing `ACTIONABLE_CONTINUATION` work takes priority over newly `READY` work, regardless of numerical package priority.
- Use normal merge commits only; never relabel missing, skipped, cancelled, superseded, wrong-revision, or failed validation as passing evidence.
- Do not weaken staging, provenance, migration, review, static-analysis, privacy, or production-authority boundaries.
- Issue #43 remains open/deferred and LiteBans remains authoritative until separately approved.

## Canonical current state

`ES-P01`, `ES-P02`, `ES-P03`, `ES-P04`, `ES-P05`, `ES-P09`, `ES-P10`, `ES-P11`, `ES-X05`, `ES-R01`, `ES-R02`, and `ES-V01` are `COMPLETE`.

`ES-V01 — Private LiteBans representative-data verification` completed on final frozen PR head `de39e30232df9bd44d4b4df54a8922e815bada76`. PR `#110` merged normally as `9a6c7240a4f6fffd216af0239709867b79080ddc`; containment is proven and GitHub auto-deleted `package/es-v01-litebans-private-verification`. The private LiteBans database remained local. The seven malformed/rejected rows remain a later data-policy decision and were not silently repaired, discarded, or rewritten.

`ES-P07` and `ES-P06` are `READY`. With ES-V01 terminal, `ES-P07` is the highest-priority next package at priority 45. This ES-V01 worker must stop without activating it. `ES-X01` remains `BLOCKED` / `PARKED_BLOCKED`: its dependencies are complete, but the required supported RoseChat standalone repository/default branch/source/AGENTS remain unresolved.

## Canonical package index

| ID | Title | Status | Classification | Priority | Dependencies | Assignment / live work |
| --- | --- | --- | --- | ---: | --- | --- |
| `ES-P01` | Exact-sanction appeal isolation | `COMPLETE` | — | 10 | — | merged PR #68 |
| `ES-R01` | Billing-independent staging bridge recovery | `COMPLETE` | — | 15 | — | canonical public→private staging route proven; terminal handoff retained |
| `ES-R02` | Report integration fixture clock recovery | `COMPLETE` | — | 16 | — | PR #103 merged normally as `5220f21a44527fdd54bb469c767c40a2f232b171` |
| `ES-P02` | Runtime database recovery and Velocity reload | `COMPLETE` | — | 20 | `ES-P01` | PR #70 merged normally as `df9f4bf39ceda3911b7c084ac0c2caa188b82c7c` after exact hosted/review/canonical Pi proof |
| `ES-P03` | Bedrock identity correctness | `COMPLETE` | — | 30 | ordinarily `ES-P02`; owner-directed narrow exception | merged PR #75 as `b960e91ea59627a870ff24f89c2f761d0cbb68ab` |
| `ES-X05` | Website UX, authentication, and appeals | `COMPLETE` | — | 35 | `ES-P01` | merged PR #74 as `2bcf5d46ca6471fddac600f85020c66105b1c0f2` |
| `ES-P04` | Staff-mode operational tools | `COMPLETE` | — | 40 | `ES-P03` | merged PR #79 as `a530b992232a8a08cbbd13b0eed6606228ceb652` |
| `ES-P07` | Inventory and Ender editing runtime completion | `READY` | `READY` | 45 | `ES-P02` | dependency complete; highest-priority next package after ES-V01 terminal publication |
| `ES-P05` | Report evidence and staff workflow completion | `COMPLETE` | — | 50 | `ES-P03`, `ES-P04` | frozen head `9e6d5f8afc120b76f5f396a2e3e279bc5f851c85`; PR #81 merged normally as `52c0dc47efdc2296827b4b6b743d01a86f72c856` |
| `ES-P09` | Alt and network-identity completion | `COMPLETE` | — | 55 | `ES-P03` | merged PR #84 as `a88201524690848f778297f140f7ee2ba5b6ce36`; representative-network acceptance remains ES-V02 |
| `ES-P06` | Discord notification delivery completion | `READY` | `READY` | 60 | `ES-P05` | dependency complete; follows lower-priority-number READY ES-P07 unless live state changes |
| `ES-P08` | Item confiscation and restoration | `PLANNED` | `PARKED_BLOCKED` | 70 | `ES-P07` | dependency blocked until ES-P07 completes |
| `ES-P10` | Cheat tester and fake-entity system | `COMPLETE` | — | 80 | `ES-P04` | merged PR #86 as `e605d8ad6094b2ae6842044d209875e13c38906d`; representative acceptance remains ES-V02 |
| `ES-P11` | Fake-base generation and cleanup | `COMPLETE` | — | 90 | `ES-P10` | merged PR #88 as `6cd293d9f1abc3ca6ca8b70e953da936f4a22ab0`; representative acceptance remains ES-V02 |
| `ES-X01` | RoseChat provider and communication integration | `BLOCKED` | `PARKED_BLOCKED` | 100 | `ES-P03`, `ES-P04`, `ES-P05` | dependencies complete, but supported RoseChat standalone repository/default branch/source/AGENTS remain unresolved; do not invent an API or repository |
| `ES-X02` | EnthusiaCurrency destructive provider | `PLANNED` | `PARKED_BLOCKED` | 110 | `ES-P08` | dependency blocked |
| `ES-X03` | EnthusiaMarket destructive provider | `PLANNED` | `PARKED_BLOCKED` | 120 | `ES-P08`, `ES-X02` | dependencies blocked |
| `ES-X04` | EnthusiaCommend reputation provider | `PLANNED` | `PARKED_BLOCKED` | 125 | `ES-P08`, `ES-X02` | dependencies blocked |
| `ES-V01` | Private LiteBans representative-data verification | `COMPLETE` | — | 200 | — | final head `de39e30232df9bd44d4b4df54a8922e815bada76`; PR #110 merged normally as `9a6c7240a4f6fffd216af0239709867b79080ddc`; contained and branch cleaned |
| `ES-V02` | Distributed and Java/Bedrock staging | `DEFERRED` | `PARKED_BLOCKED` | 250 | `ES-P06`, `ES-P09`, `ES-P11`, `ES-X01`, `ES-X03`, `ES-X04`, `ES-X05` | incomplete dependencies plus representative distributed/Java/Bedrock private staging required |
| `ES-V03` | Destructive, latency, and load acceptance | `DEFERRED` | `PARKED_BLOCKED` | 260 | `ES-P08`, `ES-X02`, `ES-X03`, `ES-X04` | incomplete destructive-provider dependencies plus private acceptance required |
| `ES-A01` | LiteBans cutover acceptance | `DEFERRED` | `PARKED_BLOCKED` | 300 | `ES-V01`, `ES-V02`, `ES-V03` | ES-V01 complete, but ES-V02/ES-V03 plus owner authorization and issue #43 are still required |
| `ES-QA01` | Final repository and workflow audit | `PLANNED` | `PARKED_BLOCKED` | 400 | `ES-A01` | dependency blocked |

## ES-V01 terminal record

- Worker-start `main`: `b78a62de3876bfde7fa5f57860fedc1415ef3c53`.
- Private representative source: MariaDB 10.11.6 with `litebans_` prefix; 102 bans, 53 mutes, 1,747 history rows. Private data remained local and was not uploaded.
- Representative result: 153 supported sanctions imported; replay of all 153 created no duplicate cases/events; zero mapped issue/expiry mismatches; abandoned-run recovery passed.
- Intentional unsupported/audit-only input: 49 warnings and 44 kicks. Historical username parsing ignored 322 invalid usernames while usable UUID/network information remained.
- Explicit rejections: 2 `INVALID_SOURCE_ROW` and 5 `INVALID_HISTORY_ROW`; all seven remain a later data-policy decision.
- Local UUID-only repair `22934e33` was reproduced as `ea07f55a`; review later expanded regression coverage so UUID-backed bans and mutes plus the IP-only ban path are all exercised.
- Pre-review head `2485c8b7a4a80ae306216eb9f66f1e9415d9eac0` passed hosted gates, then substantive review found valid follow-up issues.
- Final frozen head: `de39e30232df9bd44d4b4df54a8922e815bada76`.
- Review fix: `de39e30232df9bd44d4b4df54a8922e815bada76` corrected three routing/scope documentation inconsistencies and added the missing UUID-backed ban integration fixture. All three substantive review threads are resolved/outdated and marked addressed; valid unresolved count is zero. The later incremental CodeRabbit rescan was rate-limited and is not represented as a fresh full review.
- Final exact-head Coverage: run `31353964138`, job `93349968412`, success under Java 21 with full tests and MariaDB/Testcontainers.
- Final exact-head Codacy: static `93347267178` success; diff coverage `93350870761` success at 100.0%; coverage variation `93350870850` success at +0.01%.
- Final canonical Pi public run `31353964382`: exact runtime build `93349969346` success; bridge `93350945971` success; fork-boundary helper `93349969918` skipped/not applicable to this authorized same-repository PR path.
- Correlated private Pi run `31354311211` / job `93350973876`: success on trusted `Lincoln-PI-4`; exact provenance/freshness and artifact checksum passed; guarded disposable DB reset passed; first Paper cycle applied V1–V18 and entered `SHADOW_MIGRATION`; restart verified schema v18 current/no-op and re-entered `SHADOW_MIGRATION`; both clean shutdowns/failure scans and post-reset cleanup passed.
- Sanitized Pi evidence artifact `9050381344`, digest `sha256:34f77c0fe32fee5c79872daf9487371b17404f3308c4212b736b6f011a194bd0`.
- Historical non-passing final-head staging remains recorded: private run `31353309582` failed before a completed Paper/storage-ready cycle during shared-host resource contention; private run `31353848239` was rejected by provenance before Paper because a workflow rerun's requested attempt did not match the attempt-bound manifest. Neither is counted as passing evidence.
- PR #110 merged with a normal merge commit as `9a6c7240a4f6fffd216af0239709867b79080ddc`. The merge has the frozen head as its second parent; no unique feature-tree delta remains. GitHub auto-deleted the package branch.
- ES-V01 changed no Flyway migration. V18 remains current and immutable. Issue #43 remains deferred and LiteBans remains authoritative.

## Owner-provided LiteBans representative-data routing evidence

The owner supplied sanitized aggregate results from a private local Codex execution using the repository's actual migration service against a private MariaDB 10.11.6 LiteBans copy. The private source was not modified or uploaded.

The execution found one repository-side schema-compatibility defect and fixed it locally as `22934e33 Support UUID-only LiteBans sanctions`. After the fix, 153 supported sanctions imported and replayed idempotently with no duplicate cases/events and no mapped timestamp/expiry mismatches. The supplied run also examined 1,747 history rows. Warnings and kicks remain intentionally unsupported/audit-only under the current contract. Seven malformed source/history rows remain rejected and require a separate pre-rehearsal data-policy decision; those rejections are not authorization to rewrite or silently skip source history.

This evidence belongs to ES-V01 because that package owns representative private schema inspection, mapping/rejection verification, rerun/idempotency, and sanitized migration conclusions (`AUD-MIG-003` / `AUD-MIG-004`). It does not authorize production cutover, issue #43 acceptance, a real shadow migration, source-data modification, or punishment-authority changes.

## Next sequential action

ES-V01 is terminal `COMPLETE`. No package is active in this worker. A new sequential worker should reconcile live GitHub and then select `ES-P07 — Inventory and Ender editing runtime completion` as the highest-priority `READY` package if live state still agrees. `ES-P06` remains `READY` behind it; `ES-X01` remains blocked/parked.

Do not activate ES-P07, ES-P06, ES-X01, or any other package in this ES-V01 terminal-publication worker.
