# ES-D16 moderation real-data read bridge — blocked handoff

Status: `BLOCKED` / `PARKED_BLOCKED`.

Date: 2026-09-01.
Repository: `wsg138/EnthusiaStaff`.
Implementation PR: #187 (open/draft/unmerged).
Implementation branch: `package/es-d16-moderation-read-bridge`.
Frozen reviewed executable head: `a009f4f5f857cf86a859be0d314264568d181670`.
Canonical main during terminal publication: `a6ee52ca69cf50f39bd7a18237bbc8cdfc9fc51b`.

## Terminal outcome for this worker
ES-D16 implementation and every repository-controlled exact-head gate are complete, but the package cannot become `COMPLETE` because its required live read staging, which must be sanitized, is blocked by external Cloudflare account authorization. The protected staging API token receives HTTP 403 on the first account-level named-tunnel API request. The hard private-tunnel gate was preserved; PR #187 was not merged and the failure was not relabeled as a pass.

This is a genuine external blocker after all safe repository actions were exhausted. D07 and D13 implementation, X03 PR #139/product work, production Discord configuration/data, LiteBans authority, issue #43, and cutover remain outside this worker's product changes. This docs-only publication synchronizes X03's canonical routing status because D04 has already cleared its historical serialization blocker.

## Product delivered at the frozen head
`a009f4f5f857cf86a859be0d314264568d181670` provides the owner-approved read-only bridge from the Cloudflare moderation console to real Enthusia staff/moderation and bounded Discord data. It preserves the D03 authority model and simulation-only destructive controls. Key properties include explicit browser DTO allowlists, actor/guild/target binding, short-lived HMAC-authenticated service requests, replay resistance, bounded body/page/rate controls, loopback-only private API binding, bounded JDA REST reads, target-bound hosted launch/session flow, private no-store responses, real linked-account/history/sanction/case/note/channel/message presentation, and no reachable punishment/message-deletion/permission-override mutation path.

## Review repair history
CodeRabbit full review on earlier executable head `07dcf93359d7be419c6e2af784a1ebf034d1cdcf` produced three valid correctness findings. They were repaired without broad exclusions:

1. no-channel message queries now honor author/text/date filters and the bounded requested limit;
2. message-context hosted launches validate the target author snowflake before ticket serialization;
3. malformed Jackson request JSON enters the existing HTTP-400 invalid-request path while retaining the parse exception as its cause.

Regression tests were added for each path. Follow-up Codacy findings caused by the repair (two proxy classloader findings and one lost-exception-information finding) were fixed structurally. Exact-head CodeRabbit review `5080380849`, submitted 2026-09-01 16:13 UTC, is bound to `a009f4f5f857cf86a859be0d314264568d181670` and contains no new finding. Every visible earlier review thread is resolved or outdated and explicitly confirmed addressed.

## Exact-head hosted evidence — PASS
All items here are bound to `a009f4f5f857cf86a859be0d314264568d181670`:

- Coverage/full Java 21 run `33529631291`, job `99929426510`: `SUCCESS`.
  - Exact detached checkout of `a009f4f5f857cf86a859be0d314264568d181670`.
  - `./gradlew clean build jacocoAggregateReport runtimeJars --no-daemon --no-build-cache --no-configuration-cache --console=plain` → `BUILD SUCCESSFUL`.
  - Integration, Paper, persistence, protocol, staff-bot, Velocity, and runtime-JAR verification tasks passed.
  - Provider API source types checked: 27; runtime leaks: 0.
  - Paper runtime SHA-256 `79112d1ff1b2d8868a6e8e2fa374528e9cf5f10061c9200a44da33699582b81e`.
  - Velocity runtime SHA-256 `6cc6255ab47be253b23fe4435473adb1dd4af299a8c7f868819df2af9aa2f421`.
  - JaCoCo: 51.70% lines / 41.74% branches / 54.02% instructions.
  - Validation artifact `9809522236`, digest `sha256:00f970e5dc8a3d2823d4dae4d226be1cff8d71abcf601658f2405eb383aef1d6`.
  - Codacy coverage upload/finalization succeeded.
- Moderation Web Validation run `33529631190`: `SUCCESS`.
- Staff Bot Configuration Cache run `33529631174`: `SUCCESS`.
- Staff Bot PR Artifact run `33529631246`: `SUCCESS`.
- Sentinel Restart Artifact run `33529631254`: `SUCCESS`.
- Codacy Static Code Analysis check `99929501999`: `SUCCESS`, zero annotations.
- Codacy Diff Coverage check `99932381673`: `SUCCESS`, 33.57%, no configured gate.
- CodeRabbit review `5080380849`: exact-head, no new findings; zero unresolved review threads.

The CodeRabbit docstring-coverage display warning is not a repository-configured package gate and was not promoted into a new blocker. No missing or failed diagnostic was called passing.

## Required Cloudflare staging — FAIL / BLOCKER
Dispatch helper run `33530146334` verified PR #187 and the product branch both remained exact `a009f4f5f857cf86a859be0d314264568d181670` and dispatched the existing `moderation-web-staging-deploy.yml` workflow without changing product state.

Actual staging run `33530157844`, job `99930994457`, ran exact `a009f4f5f857cf86a859be0d314264568d181670`. Sanitized passing phases before the blocker:

- required protected configuration presence checks;
- Cloudflare account/zone resolution;
- Node 22 setup, `npm ci`, production build, 14 web tests, Wrangler dry-run;
- Worker deployment to `staff-staging.enthusia.info`, deployment version `932b8255-f49a-4ab9-abc8-4152d675f845`;
- fixed staging origin health and unauthenticated/private fence;
- first-use signed launch → moderation session;
- authenticated panel access;
- replay of the consumed launch rejected with HTTP 401;
- no bot token uploaded to Cloudflare.

The first account-level named-tunnel API GET returned HTTP 403. Because that read failed, no tunnel configuration or DNS mutation succeeded. The final mandatory `Require fixed private tunnel provisioning` step failed exactly as designed.

This is not eligible for `OWNER-APPROVED INFRASTRUCTURE EXCEPTION — STAGING DEFERRED`: a runner was allocated, product deployment/tests executed, and D16 explicitly requires staged live acceptance to be sanitized. The staging result remains `FAIL` / blocked, never `PASS`.

## Exact unblock
Grant or replace the protected staging Cloudflare API token with the owning account's required Cloudflare Tunnel account read/edit permissions. Then rerun the exact-head staging workflow. After the private tunnel provisions successfully, complete Bloom/live read acceptance and verify the staging Discord application's Message Content privileged-intent state before claiming message-content acceptance.

If the executable head changes while resuming, invalidate and rerun every applicable exact-head executable/static/review gate. If the head remains `a009f4f5f857cf86a859be0d314264568d181670`, reconcile live policy and apply the repository's exact-head/frozen-product rules without inventing new gates.

## Branch, merge, and production boundary
PR #187 remains deliberately open/draft and unmerged. Its implementation branch contains unique unmerged product work and must not be deleted. No force-push, rebase, squash, auto-merge, or default-branch product push was used.

Temporary safety/helper branches created during recovery contain no unique D16 product work beyond commits already contained by PR #187 or orchestration-only helper workflows. The connected mutation surface does not expose branch deletion; they are safe to remove when an authorized deletion path exists and are not a package blocker.

No production Discord configuration, production moderation/player/private-message data, bot-token value, LiteBans authority, migration/cutover, issue #43 acceptance, or destructive moderation action was performed.

## Routing after publication
D16 is parked. While the Cloudflare permission remains unchanged, dedicated Discord workers skip D16 and may select dependency-complete D07 before D13 according to priority. If the permission blocker changes, D16 becomes an `ACTIONABLE_CONTINUATION` and should be resumed/finalized before newly ready Discord work. A worker resuming D16 must use this handoff, the package record, live PR #187, current `main`, and live validation evidence rather than relying on stale summaries.
