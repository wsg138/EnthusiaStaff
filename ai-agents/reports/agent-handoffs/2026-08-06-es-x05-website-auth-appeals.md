# ES-X05 handoff — Website UX, authentication, and appeals

Date: 2026-08-06
Status: `COMPLETE`
Owner priority: `35`
Canonical package: `ES-X05`

## Recovery selection

Starting aggregate `main` was `9b1aac2677049ccc71dbddd963831f270c73dcd0`. ES-P02 was checked first because priority 20 wins when actionable. It remains `BLOCKED` / `PARKED_BLOCKED`: product head `d671fef9fd14f0c4ae711c83edb29bc9b08ea002` passed Coverage `31138550369` / `92743341861`, but private staging run `31139079620` failed before Ubuntu runner allocation in job `92744901730` with runner ID `0`, empty runner name, steps `[]`, and GitHub's explicit Billing & plans payment/spending-limit message; Pi job `92744908539` skipped.

That successful public Coverage run was material evidence that ordinary public Ubuntu runners recovered, changing ES-X05's unblock condition. ES-X05 was therefore the highest-priority `ACTIONABLE_CONTINUATION` allowed by the owner recovery instruction. No PLANNED or READY package was started.

## Integrated implementation and live standalone repair

- Aggregate ES-X05 implementation branch `package/es-x05-state-publication`; PR #73; exact hosted/review head `4c818bb3aea953d3f877efc8a48a9175ba219d38`; Coverage `31116854096` / `92668751419`; normal merge `345b7bbcec6facb45c7f96b0e6e181ac7a38e1da`.
- Standalone ES-X05 PR #2 merged normally as `b385f78c522f452cc48d78ed19fd2ee82573f64d` after hosted validation, Cloudflare deployments, Codacy, and review.
- Live standalone `main` then advanced via PR #3 from exact head `db8d4dc6836729b0558eaa2926f8bf4f362b8eaf` to merge `2fe7d59c1c5e12db0b7ba792fc9e2af4d24337c2`. Its sole delta deletes `functions/_middleware.js`, fixing a real ES-X05 defect where intended public-but-unlinked appeal/reviewer pages redirected when Access/login configuration was absent. Protected API authentication and reviewer authorization remain fail-closed.
- PR #3 passed site test `31118849099` / `92674874313`, Cloudflare Pages checks `92675068365` and `92674953189`, Codacy `92675034770`, and had zero review threads.

## Aggregate recovery and validation

- Existing PR #74 started at `96bf9ab21b114a4523582a5ca267e6c1d1370cb1` and was materially behind aggregate `main`.
- Current `main` was merged normally into the branch as `e9644c14e743f686758ee619ab347cbebe1b21ec`, preserving completed ES-P03 state and current ES-P02 evidence. The standalone middleware deletion was mirrored into `components/enthusia-site/`.
- Frozen finalization head: `ab59b8357b8e2eb146b60ff122e316112906746f`.
- Exact hosted Coverage run `31140188918`, job `92748299782`, succeeded on Ubuntu 24.04 / Temurin Java 21 through clean build, unit and MariaDB/Testcontainers integration/migration tests, JaCoCo, runtime-JAR/provider-leak checks, artifacts, and Codacy coverage upload.
- Runtime provider inspection: 24 checked, 0 leaks. Paper JAR SHA-256 `9880457c88f445de6f813f9bbee15544b59abc344d65420a6ae100d4ef5ab9d4`; Velocity JAR SHA-256 `74e0105a94c7f10fc371fe033f07ab46588a01c18bfeea832af3179e72f986d6`. Validation artifact `8979625925`, digest `42c9f835001de4847cd26961dbbe185a671b0239511d872a9553efeba44680f4`.
- CodeRabbit succeeded; the one historical PR #74 thread is resolved/outdated and zero valid unresolved review threads remain. Codacy static `92748599134`, coverage variation `92749330468`, and diff coverage `92749330613` succeeded.
- Wiki validation was not applicable because no wiki-trigger paths changed.

## Staging disposition

**OWNER-APPROVED INFRASTRUCTURE EXCEPTION — STAGING DEFERRED** to `ES-V02`; not a pass.

Original dispatcher `31116852061` / `92668521113` dispatched private run `31116860919`; build `92668551209` had runner ID `0`, empty runner name, steps `[]`; Pi `92668600472` skipped. No product validation executed.

No manual private-staging retry was requested during this recovery pass. Repository PR automation automatically dispatched wrapper `31140187754` / `92748257022`, private run `31140197043`, build `92748287250` for source `ab59b8357b8e2eb146b60ff122e316112906746f`; the build again received runner ID `0`, empty runner name, steps `[]`, and the same Billing & plans annotation; Pi `92748295072` skipped. This remains infrastructure-unavailable evidence only and does not reinterpret the owner deferral.

## Merge, containment, and parity

- PR #74 merged normally as `2bcf5d46ca6471fddac600f85020c66105b1c0f2` with frozen head unchanged.
- Compare frozen head -> merge reports no changed files; containment is complete and no unique branch work remains.
- Post-merge parity run `31140896890`, job `92750376952`, used `tools/component-sync/component_sync.py` against exact aggregate merge `2bcf5d46ca6471fddac600f85020c66105b1c0f2` and standalone `2fe7d59c1c5e12db0b7ba792fc9e2af4d24337c2`.
- Result: `parity: true`, added `[]`, missing `[]`, modified `[]`, aggregate hash = standalone hash = `780269847698d37c470cb7c241539b1c7387014225cc7eee9598548c9dc97f8b`. Artifact `8979748083` stores both manifests and `parity.json`, digest `aa7c1de4154b8d6dd4a888b4a876256b89ce66a5a93342762fe1ca50fc7aa7f5`.
- Harness-only history: `31140685623` / `92749749317` failed before compare due shallow-history precheck; `31140785772` / `92750046294` printed parity true but failed on a wrapper key typo; corrected run passed every step.

## Resulting routing and stop boundary

ES-X05 is `COMPLETE`. ES-P02 stays parked until its private Billing & plans restriction materially changes. ES-P04 and ES-P09 are dependency-ready because their sole declared dependency ES-P03 is complete; this recovery worker does not start either. Normal sequential routing may resume after this publication, with ES-P04 expected first at priority 40 absent a newly actionable continuation.

LiteBans remains authoritative. Issue #43 stays open/deferred. No production credentials/accounts/data/routes, private player data, Flyway rewrite, cutover, authority activation, ES-V02 execution, or new implementation package was performed.