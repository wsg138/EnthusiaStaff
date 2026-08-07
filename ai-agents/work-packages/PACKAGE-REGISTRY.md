# Package registry

Last updated: 2026-08-06

Canonical current state: `ES-P01` and `ES-P03` are `COMPLETE`; `ES-P02` is `BLOCKED` / `PARKED_BLOCKED`; `ES-X05` is the selected `MERGE_PENDING` / `ACTIONABLE_CONTINUATION` recovery package. No PLANNED or READY package is active. Issue #43 remains open, deferred, and excluded.

## Rules

- Live GitHub overrides stale text and must be reconciled back here.
- Classify every incomplete package before selection; select one package and stop after its terminal state is persistently published.
- Park unchanged external blockers; do not repeat identical zero-runner attempts without a material condition change.
- Existing actionable continuation work takes priority over new implementation work.
- Owner-directed routing exceptions must be explicit and do not silently complete dependencies or propagate to later packages.
- Use normal merge commits only. Missing, skipped, cancelled, superseded, wrong-revision, billing-blocked, or zero-runner gates are not passes.
- An owner-approved staging deferral remains deferred evidence; it cannot excuse a missing ordinary GitHub-hosted build.

## ES-P03 routing exception

The ordinary graph requires ES-P02 complete before ES-P03. ES-P02 remains `BLOCKED` / `PARKED_BLOCKED`. On 2026-08-06 the repository owner directed the earlier worker to continue ES-P03 while ES-P02 and ES-X05 were parked. The exception permitted ES-P03 only; it did not alter ES-P02/ES-X05, waive gates, or automatically authorize later packages.

## Canonical package index

| ID | Title | Type | Status | Classification | Priority | Dependencies | Assignment / live work |
| --- | --- | --- | --- | --- | ---: | --- | --- |
| `ES-P01` | Exact-sanction appeal isolation | Internal | `COMPLETE` | — | 10 | — | merged PR #68 |
| `ES-P02` | Runtime database recovery and Velocity reload | Internal | `BLOCKED` | `PARKED_BLOCKED` | 20 | `ES-P01` | branch `package/es-p02-runtime-db-recovery`; PR #70; records head `99da4103773e0c2ae43e0b0253200cd0d3d2c65c`; private Actions billing blocker unchanged |
| `ES-P03` | Bedrock identity correctness | Internal | `COMPLETE` | — | 30 | ordinarily `ES-P02`; owner-directed narrow exception | PR #75 merged as `b960e91ea59627a870ff24f89c2f761d0cbb68ab` |
| `ES-X05` | Website UX, authentication, and appeals | External/multi-repository | `MERGE_PENDING` | `ACTIONABLE_CONTINUATION` | 35 | `ES-P01` | implementation integrated; finalization branch `package/es-x05-finalization`; PR #74; public runner recovery confirmed; live standalone PR #3 delta synchronized |
| `ES-P04` | Staff-mode operational tools | Internal | `PLANNED` | — | 40 | `ES-P03` | unassigned; not activated in this recovery run |
| `ES-P07` | Inventory and Ender editing runtime completion | Internal | `PLANNED` | — | 45 | `ES-P02` | unassigned |
| `ES-P05` | Report evidence and staff workflow completion | Internal | `PLANNED` | — | 50 | `ES-P03`, `ES-P04` | unassigned |
| `ES-P09` | Alt and network-identity completion | Internal | `PLANNED` | — | 55 | `ES-P03` | unassigned |
| `ES-P06` | Discord notification delivery completion | Internal | `PLANNED` | — | 60 | `ES-P05` | unassigned |
| `ES-P08` | Item confiscation and restoration | Internal | `PLANNED` | — | 70 | `ES-P07` | unassigned |
| `ES-P10` | Cheat tester and fake-entity system | Internal | `PLANNED` | — | 80 | `ES-P04` | unassigned |
| `ES-P11` | Fake-base generation and cleanup | Internal | `PLANNED` | — | 90 | `ES-P10` | unassigned |
| `ES-X01` | RoseChat provider and communication integration | External/multi-repository | `PLANNED` | — | 100 | `ES-P03`, `ES-P04`, `ES-P05` | unassigned; repository unresolved |
| `ES-X02` | EnthusiaCurrency destructive provider | External/multi-repository | `PLANNED` | — | 110 | `ES-P08` | unassigned |
| `ES-X03` | EnthusiaMarket destructive provider | External/multi-repository | `PLANNED` | — | 120 | `ES-P08`, `ES-X02` | unassigned |
| `ES-X04` | EnthusiaCommend reputation provider | External/multi-repository | `PLANNED` | — | 125 | `ES-P08`, `ES-X02` | unassigned |
| `ES-V01` | Private LiteBans representative-data verification | Private validation | `DEFERRED` | — | 200 | — | private/local environment required |
| `ES-V02` | Distributed and Java/Bedrock staging | Private validation | `DEFERRED` | — | 250 | `ES-P06`, `ES-P09`, `ES-P11`, `ES-X01`, `ES-X03`, `ES-X04`, `ES-X05` | owns representative Java/Bedrock and owner-deferred ES-X05 private/Pi staging |
| `ES-V03` | Destructive, latency, and load acceptance | Private validation | `DEFERRED` | — | 260 | `ES-P08`, `ES-X02`, `ES-X03`, `ES-X04` | private/local environment required |
| `ES-A01` | LiteBans cutover acceptance | Production acceptance | `DEFERRED` | — | 300 | `ES-V01`, `ES-V02`, `ES-V03` | owner authorization and issue #43 required |
| `ES-QA01` | Final repository and workflow audit | Final no-fix audit | `PLANNED` | — | 400 | `ES-A01` | unassigned |

## Detailed current records

### `ES-P02`

- Status: `BLOCKED` / `PARKED_BLOCKED`.
- Branch/PR: `package/es-p02-runtime-db-recovery`; #70.
- Current records head: `99da4103773e0c2ae43e0b0253200cd0d3d2c65c`.
- Synchronized-main merge: `b21cb81b81fdcf0bac5027ae6f6b7901f6b0c175`.
- Exact hosted-validation product head: `d671fef9fd14f0c4ae711c83edb29bc9b08ea002`.
- Hosted validation: Coverage run `31138550369`, job `92743341861`, success for Java 21 build/tests, MariaDB/Testcontainers and migrations, coverage, runtime-JAR/provider-leak checks, artifact upload, and Codacy coverage. CodeRabbit and Codacy are clean with zero valid unresolved threads.
- Latest private staging: run `31139079620`; ordinary Ubuntu build job `92744901730` failed before runner allocation with runner ID `0`, empty runner name, steps `[]`; Pi job `92744908539` skipped. GitHub explicitly reported recent account payments failed or the Actions spending limit must be increased under Billing & plans.
- Exact unblock: resolve the private-repository Billing & plans restriction, then resume PR #70, reconcile any newer legitimate `main`, freeze one exact head, rerun every applicable hosted/review gate, obtain a successful ordinary private build followed by Pi safe Paper boot/restart for the same source revision, merge normally, verify containment, publish `COMPLETE`, and stop.
- Handoff: [`2026-08-05-es-p02-runtime-db-recovery.md`](../reports/package-handoffs/2026-08-05-es-p02-runtime-db-recovery.md).

### `ES-P03`

- Status: `COMPLETE`.
- Starting `main`: `345b7bbcec6facb45c7f96b0e6e181ac7a38e1da`.
- Exact reviewed/validated product head: `15608bc3099dc34aa080c80ca8e824ffd51cdae4`.
- Implementation PR/merge: #75 / `b960e91ea59627a870ff24f89c2f761d0cbb68ab`.
- Validation: Coverage `31133176482` / job `92726659126`; Wiki `31133176536` / job `92726609318`; CodeRabbit success and six resolved threads; Codacy 0 new issues.
- V1–V17 unchanged. Containment passed; no unique product work remains outside the merge.
- Handoff: [`2026-08-06-es-p03-bedrock-identity.md`](../reports/package-handoffs/2026-08-06-es-p03-bedrock-identity.md).

### `ES-X05`

- Status: `MERGE_PENDING` / `ACTIONABLE_CONTINUATION`.
- Starting recovery `main`: `9b1aac2677049ccc71dbddd963831f270c73dcd0`.
- Starting finalization head: `96bf9ab21b114a4523582a5ca267e6c1d1370cb1`.
- Aggregate implementation: branch `package/es-x05-state-publication`; PR #73; normal merge `345b7bbcec6facb45c7f96b0e6e181ac7a38e1da`; exact hosted/review product head `4c818bb3aea953d3f877efc8a48a9175ba219d38`; Coverage `31116854096` / `92668751419` success.
- Standalone implementation: PR #2 normal merge `b385f78c522f452cc48d78ed19fd2ee82573f64d` after hosted validation/deployments/review.
- Live standalone follow-up: PR #3 exact head `db8d4dc6836729b0558eaa2926f8bf4f362b8eaf`, merge/current `main` `2fe7d59c1c5e12db0b7ba792fc9e2af4d24337c2`; sole delta removes `functions/_middleware.js` so public-but-unlinked appeal/reviewer pages no longer redirect while API protection remains fail-closed. Site test workflow, both Cloudflare Pages checks, and Codacy succeeded; zero review threads.
- Finalization: branch `package/es-x05-finalization`; PR #74. Current `main` was merged normally and the PR #3 deletion is mirrored under `components/enthusia-site/`.
- Staging: **OWNER-APPROVED INFRASTRUCTURE EXCEPTION — STAGING DEFERRED** to `ES-V02`; no staging pass is claimed and this worker does not rerun/reinterpret it.
- Remaining gates: freeze the reconciled exact head; successful ordinary hosted Coverage/build/test/migration/coverage/runtime-JAR/provider-leak/artifact gates; applicable Wiki/Markdown/package/static-analysis and review; zero valid unresolved findings; deterministic standalone/aggregate parity; normal PR #74 merge; containment; final `COMPLETE` publication and safe cleanup.
- Handoff: [`2026-08-06-es-x05-website-auth-appeals.md`](../reports/agent-handoffs/2026-08-06-es-x05-website-auth-appeals.md).

## Next-worker boundary

This recovery worker selects exactly ES-X05 and no PLANNED/READY package. After ES-X05 reaches a truthful terminal state and is persistently published, stop. The ES-P03 routing exception does not waive dependencies for later packages.