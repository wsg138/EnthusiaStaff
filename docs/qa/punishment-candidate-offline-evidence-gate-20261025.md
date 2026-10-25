# Punishment candidate offline evidence gate — 2026-10-25

**STATUS: DRAFT / NO-GO FOR RELEASE.** This check does not merge, deploy, connect to servers, query live policies or databases, or enable guided punishment. It does not schedule GitHub Actions.

## Purpose

The punishment feature stack has many draft branches. A passing offline test count and shaded Paper JAR have been manually reported after each proposed change. Missing JUnit modules, skipped integration execution, default guided-mode changes, or an invalid shaded JAR must not be characterized as a fully accepted runtime candidate.

The read-only Python verifier at scripts/qa/punishment_offline_evidence.py produces a local JSON record. It reads TEST-*.xml from domain, paper, staff-bot and velocity; counts passes/skips/errors/failures; verifies the exact packaged Paper JAR SHA256; and checks packaged config.yml for exactly one boolean guided-punishment.enabled within the top-level section. Missing/ambiguous evidence fails closed.

The generated record ALWAYS says release_status = NO_GO. Offline evidence does not constitute production approval.

## Reproducible use

From a clean candidate checkout in Windows:

1. Run: gradlew.bat --offline --no-daemon --rerun-tasks :domain:test :paper:test :staff-bot:test :velocity:test :integration-tests:compileTestJava :paper:shadowJar
2. Run: python -m unittest discover -s scripts/qa -p test_punishment_offline_evidence.py -v
3. Run: python scripts/qa/punishment_offline_evidence.py --root .
4. Optional file output: python scripts/qa/punishment_offline_evidence.py --root . --output build/punishment-offline-evidence.json

On Linux, use ./gradlew with the same Gradle arguments. Python's standard library alone is sufficient. Always rerun Gradle with --rerun-tasks and preferably use a clean checkout to prevent prior JUnit XML contaminating reported totals. Independently check working tree state and PR exact SHA.

Nonzero exit means missing evidence, failed test results or guided default-on. Skips are counted, never hidden. This program does not establish MariaDB integration execution, Folia scheduling, Java/Bedrock GUI behavior, hosted CI or customized live policies.

## Required later release evidence, NOT provided by this script

- Owner signoff on calibrated severity scenarios, warning behavior, repeat-history/duration/rank safety and manual-review boundaries (GitHub issue #616).
- Read-only comparison against the actual customized production reason-policies.yml, not only bundled example policies, and owner approval before deployment.
- Disposable MariaDB integration tests executing draft persistence, idempotent replay, approval lease expiration/release, ambiguous acknowledgement and rollback. Compilation of integration test Java does not count.
- Isolated Linux Paper/Folia startup, entity scheduler and global region scheduler behavior, promotion/demotion and Staff Mode transitions during asynchronous work.
- Real Java and Geyser/Bedrock GUI testing according to docs/implementation-plans/staff-punishment-gui-ux-acceptance-20261015.md. Include exact versions, screenshots or recordings; mark unexecuted rows BLOCKED, never PASS.
- Isolated recovery and rollback rehearsal: no duplicate cases, safe network enforcement, no stranded leases, and no impact on unrelated staff tooling.
- Explicit final owner approval, versions, limited staging-to-live rollout and rollback criteria. No part of this document grants any authorization to deploy.

## Provenance and caution

The initial tool branch is based on draft PR #738 exact head bead51950ca5bcb486418eaa2db48865550368f8. Earlier draft stack includes #736 Folia-safe typed replies, #735 late target hierarchy, #734 owner-thread approval reauthorization, #733 stale review recovery, #732 async read rechecks, #731 confirmation inventory, #730 guided late hierarchy, #729 Staff Mode worker rechecks, #728 actor rank drift, plus older guided/GUI QA. Source lineage as observed October 25, 2026; it does not instruct automatic merges.

Evidence is bound to the exact tested checkout and local report files, not to the user's deployed network. No merge, deployment, restart, server/database write, Discord event, guided-mode enablement or policy approval occurs.
