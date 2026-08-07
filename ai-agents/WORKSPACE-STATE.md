# EnthusiaStaff workspace state

Last updated: 2026-08-06

Live GitHub state overrides stale records, but persistent package state must be published through the package branch and merged normally only after all gates pass.

## Current routing

| Field | Value |
| --- | --- |
| Completed packages | `ES-P01 — Exact-sanction appeal isolation`; `ES-P03 — Bedrock identity correctness` |
| Parked packages | `ES-P02 — Runtime database recovery and Velocity reload`; `ES-X05 — Website UX, authentication, and appeals` |
| Active implementation package | `NONE` |
| ES-P02 status | `BLOCKED` / `PARKED_BLOCKED`; branch `package/es-p02-runtime-db-recovery`; PR #70 |
| ES-P02 synchronized base | current `main` `9b1aac2677049ccc71dbddd963831f270c73dcd0`; merge commit `b21cb81b81fdcf0bac5027ae6f6b7901f6b0c175` |
| ES-P02 exact hosted-validation head | `d671fef9fd14f0c4ae711c83edb29bc9b08ea002` |
| ES-P02 hosted validation | Coverage `31138550369` / job `92743341861`: success; Java 21 build/tests, MariaDB/Testcontainers, migrations, coverage, runtime-JAR/provider-leak checks, artifact upload, and Codacy upload passed |
| ES-P02 review | CodeRabbit success; Codacy success with zero issues; zero valid unresolved review threads |
| ES-P02 blocker | private staging repository billing/payment or Actions spending-limit restriction; staging run `31138555091` build jobs `92743314720` and `92743621264` received no runner and zero steps; Pi jobs skipped |
| ES-P02 required owner action | correct **Billing & plans** for private Actions usage, then rerun exact-head hosted and private staging/Pi gates; the ordinary hosted staging build cannot be excepted |
| ES-X05 status | `BLOCKED` / `PARKED_BLOCKED`; implementation merged; finalization branch `package/es-x05-finalization`; PR #74 |
| Production boundary | issue #43 remains open and deferred; LiteBans remains authoritative |
| Canonical handoff | [`2026-08-06-es-p02-resume-validation.md`](reports/package-handoffs/2026-08-06-es-p02-resume-validation.md) |

## ES-P02 resumed work

- Reconciled live GitHub and resumed ES-P02 under the resume-first package rules.
- Merged current `main` into the preserved package branch without rebasing or force-pushing.
- Resolved only eight package-governance conflicts. Paper and Velocity product code merged cleanly.
- Revalidated exact head `d671fef9fd14f0c4ae711c83edb29bc9b08ea002`; every executable hosted gate passed.
- Retried the dispatched private staging run once. Both ordinary build attempts failed before runner allocation.
- Read GitHub’s failure annotation, which identifies account payments or the Actions spending limit—not ES-P02 code—as the cause.
- Did not merge PR #70, weaken validation, use an invalid exception, modify product code, start ES-X05, access private data, or activate production authority.

## Safety boundaries

No production credentials, Cloudflare secrets, punishment records, player records, raw addresses, private databases, deployment, Flyway repair/history rewrite, LiteBans removal, issue #43 acceptance, production migration, shadow window, cutover, or authority activation was authorized or performed.
