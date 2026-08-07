# ES-X05 handoff — Website UX, authentication, and appeals

Date: 2026-08-06
Status: `MERGE_PENDING`
Classification: `ACTIONABLE_CONTINUATION`
Owner priority: `35`
Canonical package: `ES-X05`

## Recovery selection

Starting aggregate `main` is `9b1aac2677049ccc71dbddd963831f270c73dcd0`. ES-P02 was checked first because priority 20 wins when actionable. It remains `BLOCKED` / `PARKED_BLOCKED`: its newer hosted product head `d671fef9fd14f0c4ae711c83edb29bc9b08ea002` passed Coverage `31138550369` / `92743341861`, but latest private staging run `31139079620` again failed before Ubuntu runner allocation in job `92744901730` with runner ID `0`, empty runner name, steps `[]`, and GitHub's explicit Billing & plans payment/spending-limit message; Pi job `92744908539` was skipped.

The successful public Coverage run is material evidence that ordinary public Ubuntu runners recovered, changing ES-X05's exact unblock condition. Therefore ES-X05 is the highest-priority `ACTIONABLE_CONTINUATION` allowed by the owner recovery instruction. No PLANNED or READY package was started.

## Integrated implementation evidence

- Standalone ES-X05 PR #2 reviewed head `1a45b32e372cf6939c078a0d7986655e7ed639d6`; hosted validation `31113188453`, production/preview deployments, Codacy, and review passed; normal merge `b385f78c522f452cc48d78ed19fd2ee82573f64d`.
- Aggregate implementation PR #73 exact hosted/review head `4c818bb3aea953d3f877efc8a48a9175ba219d38`; Coverage `31116854096` / `92668751419` passed Java 21 clean build, unit and MariaDB/Testcontainers migration/integration tests, JaCoCo, runtime-JAR/provider-leak inspection, artifacts, and Codacy coverage. CodeRabbit passed with zero valid unresolved threads. PR #73 merged normally as `345b7bbcec6facb45c7f96b0e6e181ac7a38e1da`.
- V17 is the only ES-X05 migration; V1–V16 remain unchanged.

## Live standalone reconciliation

Live standalone `main` is no longer `b385f78...`. PR #3 merged normally as `2fe7d59c1c5e12db0b7ba792fc9e2af4d24337c2` from exact head `db8d4dc6836729b0558eaa2926f8bf4f362b8eaf`.

Its sole delta is deletion of `functions/_middleware.js`. The middleware redirected `/appeal`, `/appeal.html`, and `/reviewer/*` to `/` when Access claims or `ACCESS_LOGIN_URL` were unavailable, contrary to the intended public-but-unlinked page rollout. Protected API handlers continue to authenticate linked identity and reviewer role. PR #3 exact head passed site test workflow `31118849099` / job `92674874313`, both Cloudflare Pages checks (`92675068365`, `92674953189`), Codacy `92675034770`, and had zero review threads.

The same one-file deletion is synchronized into `components/enthusia-site/` on PR #74. No other standalone file changed between `b385f78...` and current standalone `main`.

## Finalization branch reconciliation

PR #74 started this recovery pass at `96bf9ab21b114a4523582a5ca267e6c1d1370cb1`, 28 commits behind current aggregate `main`. Current `main` was merged normally into the existing branch; state conflicts were resolved from current `main` so ES-P03 completion is preserved. ES-P02's newer branch head, hosted evidence, and unchanged billing blocker are recorded without modifying ES-P02 product work.

Freeze the resulting finalization head after these records are committed. Its exact SHA and automatically generated checks belong in PR #74/final report rather than a tracked-file self-reference.

## Owner-approved staging deferral

Evidence label: **OWNER-APPROVED INFRASTRUCTURE EXCEPTION — STAGING DEFERRED**.

Owner `wsg138` explicitly approved deferral on 2026-08-06 to `ES-V02 — Distributed and Java/Bedrock staging`. Original dispatcher `31116852061` / `92668521113` dispatched private run `31116860919`; build `92668551209` received runner ID `0`, empty runner name, steps `[]`; Pi job `92668600472` was skipped. No private checkout/build/test/artifact/Paper boot/restart/migration executed. This gate remains deferred and is not a pass. This recovery pass does not rerun or reinterpret it.

## Required exact-head gates

1. Successful ordinary hosted Coverage/build/test/migration/coverage/runtime-JAR/provider-leak/artifact validation on the frozen PR #74 head.
2. Applicable Wiki/Markdown/package/static-analysis checks on that same revision.
3. CodeRabbit/human/Codacy review with zero valid unresolved findings.
4. Deterministic standalone/aggregate component parity against standalone `2fe7d59c1c5e12db0b7ba792fc9e2af4d24337c2`.
5. Normal merge of PR #74 with its validated head unchanged.
6. Post-merge containment/no-unique-work verification, persistent `COMPLETE` publication, safe branch cleanup where supported, then stop.

## Safety and stop boundary

LiteBans remains authoritative. Issue #43 is open and deferred. No production credentials/accounts/data/routes, production/private player data, Flyway rewrite, cutover, authority activation, ES-V02 execution, or new implementation package is authorized.