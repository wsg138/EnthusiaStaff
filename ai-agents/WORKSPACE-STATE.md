# EnthusiaStaff workspace state

Last updated: 2026-08-06

Live GitHub state overrides stale records, but persistent package state must be published to `main`.

## Current routing

| Field | Value |
| --- | --- |
| Completed package | `ES-P01 — Exact-sanction appeal isolation` |
| Parked packages | `ES-P02 — Runtime database recovery and Velocity reload`; `ES-X05 — Website UX, authentication, and appeals` |
| ES-P02 classification | `PARKED_BLOCKED` while its runner/authorization condition is unchanged |
| Preserved ES-P02 work | branch `package/es-p02-runtime-db-recovery`, open PR #70, current package-record head `80d4ea840f34017c09afb618f623581b31c6223d` |
| ES-X05 status/classification | `BLOCKED` / `PARKED_BLOCKED` |
| ES-X05 finalization | branch `package/es-x05-finalization`, open PR #74 |
| ES-X05 priority | `35` |
| Standalone repository/main | `wsg138/enthusia-site` at normal merge `b385f78c522f452cc48d78ed19fd2ee82573f64d` |
| Aggregate implementation merge/main | `345b7bbcec6facb45c7f96b0e6e181ac7a38e1da` via PR #73 |
| Exact hosted-validated product head | `4c818bb3aea953d3f877efc8a48a9175ba219d38` |
| Frozen aggregate product head | `96912301fc425ac6f5eff9349ee3b3d543d122eb` |
| Component parity | true at SHA-256 `9910dc90d22be68bf034f03def0cabd617bdf2e9953f87231f11af1166fc07e2`; no added, missing, or modified paths |
| Migration boundary | aggregate `main` includes immutable `V17__website_appeal_workflow.sql`; V1–V16 unchanged |
| Canonical handoff | [`2026-08-06-es-x05-website-auth-appeals.md`](reports/agent-handoffs/2026-08-06-es-x05-website-auth-appeals.md) |
| Active implementation package | `NONE` |

## ES-X05 integrated evidence

- Standalone PR #2 passed final-head site validation, production and preview Cloudflare deployments, Codacy, and review, then merged normally.
- Aggregate PR #73 implements matching signed private appeal routes, durable MariaDB exact-punishment workflow, atomic rate limiting, scoped idempotency/replay protection, optimistic decisions, audit events, and exact-sanction approval delegation.
- Coverage run `31116854096` passed Java 21 clean build, unit and MariaDB/Testcontainers tests/migrations, JaCoCo, runtime-JAR/provider-leak checks, artifact upload, and Codacy coverage upload on exact head `4c818bb3aea953d3f877efc8a48a9175ba219d38`.
- CodeRabbit passed with zero unresolved valid review threads.
- PR #73 merged normally as `345b7bbcec6facb45c7f96b0e6e181ac7a38e1da`; implementation containment and external parity passed.

## ES-X05 blocker

The required trusted staging/Pi gate has no passing evidence and no owner-approved infrastructure exception.

- PR #73 Pi dispatcher run `31116852061` dispatched staging run `31116860919` for exact product head `4c818bb3aea953d3f877efc8a48a9175ba219d38`.
- Staging build job `92668551209` had runner ID 0, empty runner name, and zero steps; the Pi boot/restart job was skipped. No product build or runtime step executed.
- Post-merge run `31117490156` failed during action preparation with `Service Unavailable` before product execution.
- PR #74 finalization Coverage run `31117820548` and Pi run `31117820542` were cancelled with runner ID 0, empty runner names, and zero steps.
- GitHub Status reported an active Actions partial outage affecting workflow starts and execution. These are infrastructure failures, not product failures, but they are not passes.

Exact unblock: wait for demonstrable Actions/staging recovery or another material runner/configuration change; then validate one frozen PR #74 head with successful hosted Coverage plus trusted staging build and Pi boot/restart, or obtain an explicit policy-valid owner disposition that does not relabel the missing ordinary hosted build as passed. Reconfirm review/parity, merge PR #74 normally, publish `COMPLETE`, verify containment, and stop. Do not rerun identical zero-runner gates without evidence of change.

## Safety boundaries

- No production credentials, Cloudflare secrets, punishment records, player records, or private database data are committed.
- Authentication, origin, reviewer role, rate-limit, replay, body-size, timeout, and upstream-service configuration fail closed.
- LiteBans remains authoritative; issue #43 and production cutover remain deferred and excluded.
- ES-P02 PR #70 and its branch were not modified.
- No follow-on package was selected or activated.
