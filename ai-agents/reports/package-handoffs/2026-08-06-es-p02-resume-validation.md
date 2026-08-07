# ES-P02 resumed validation handoff

- Package: `ES-P02 — Runtime database recovery and Velocity reload`
- Final status: `BLOCKED` / `PARKED_BLOCKED`
- PR: #70
- Branch: `package/es-p02-runtime-db-recovery`
- Synchronized `main`: `9b1aac2677049ccc71dbddd963831f270c73dcd0`
- Synchronization merge: `b21cb81b81fdcf0bac5027ae6f6b7901f6b0c175`
- Exact hosted-validation head: `d671fef9fd14f0c4ae711c83edb29bc9b08ea002`

## Work completed

The prior paused package was resumed through the canonical resume-first rules. Current `main` was merged into the preserved ES-P02 branch by an ordinary merge commit. The only conflicts were eight package-governance records; current `main` was retained for global rules, registry, workspace state, and the global latest handoff, while the package-specific ES-P02 record and handoff were preserved. All Paper and Velocity runtime changes merged automatically without conflicts.

No product-code defect was introduced or discovered during synchronization, so product code was not changed.

## Exact hosted validation

Coverage workflow run `31138550369`, job `92743341861`, tested exact head `d671fef9fd14f0c4ae711c83edb29bc9b08ea002` and completed successfully.

Successful steps include:
- Java 21 setup;
- runtime JAR build and all configured tests;
- MariaDB/Testcontainers and migration-integrity validation;
- aggregate JaCoCo generation and changed-code coverage enforcement;
- runtime-JAR integrity and provider/API leak inspection;
- validation artifact upload;
- Codacy coverage upload.

Artifact:
- name: `java-21-validation`;
- ID: `8979036747`;
- digest: `sha256:18810296fc08695fb5d5f8497f008052161b7a0b0536fc898d27a22d69f65d70`.

Additional exact-head results:
- Codacy Static Code Analysis: success, zero issues/annotations;
- CodeRabbit: success;
- valid unresolved review threads: zero.

## Private staging failure

Parent workflow run `31138550480`, job `92743298476`, dispatched staging run `31138555091` in private repository `wsg138/EnthusiaStaff-Staging` for exact source SHA `d671fef9fd14f0c4ae711c83edb29bc9b08ea002`.

Attempt 1:
- build job `92743314720`, `ubuntu-latest`: failure before runner allocation;
- runner ID `0`, empty runner name, zero steps;
- Pi job `92743321663`: skipped.

Attempt 2, after one direct rerun:
- build job `92743621264`, `ubuntu-latest`: failure before runner allocation;
- runner ID `0`, empty runner name, zero steps;
- Pi job `92743627928`: skipped.

The check-run annotation states that the job was not started because recent account payments failed or the spending limit must be increased, and instructs the owner to check **Billing & plans**.

No staging product build, test, artifact check, Pi boot, or restart step executed. This is infrastructure-unavailable evidence rather than a product result. It is not a pass. Because the missing job is an ordinary GitHub-hosted build that the private staging repository normally executes, `VALIDATION-POLICY.md` prohibits an owner-approved infrastructure exception for it.

## Required owner action and exact resume condition

1. Resolve failed payment or increase the GitHub Actions spending limit for private repository `wsg138/EnthusiaStaff-Staging` under **Billing & plans**.
2. Resume PR #70 and branch `package/es-p02-runtime-db-recovery`.
3. Reconcile any newer `main` through a normal merge commit; do not rebase or force-push.
4. Freeze the resulting exact head and rerun all applicable hosted build/test/migration/coverage/static-analysis/review/artifact/provider-leak gates.
5. Obtain a successful ordinary private-staging build and successful Pi safe-boot/restart result for that same exact head.
6. Require PR mergeability and zero valid unresolved review findings.
7. Merge normally, verify resulting heads and feature-head containment, clean the temporary branch when safe, update all records, and stop.

Do not modify product code without a newly confirmed defect. Do not call either failed staging attempt passed. Do not use the specialized-runner exception to bypass the ordinary hosted build. Do not start a second package in the same worker run.

## Boundaries preserved

No production database, credentials, private player data, deployment, authority activation, Flyway repair/history rewrite, issue #43 acceptance, shadow period, production migration, cutover, rollback, provider invention, ES-X05 implementation, or second package. LiteBans remains authoritative.
