# ES-D13 role-sync replacement — blocked handoff

Date: 2026-09-14

Package: `ES-D13 — Discord role-sync replacement`

Terminal classification for this worker: `BLOCKED` / `PARKED_BLOCKED`.

## Live implementation identity

- Implementation PR: #178 — `[ES-D13] Replace DiscordSRV role synchronization`.
- Branch: `package/es-d13-role-sync-replacement`.
- Exact frozen candidate head: `92b207d67a1098acc2dcfbddd35ac56e01711f95`.
- Observed base/current `main` during blocker publication: `e7b338979c3824687147a0b3253638324571a3a7`.
- PR #178 remains open/unmerged and its branch is preserved. No rebase, squash, force-push, auto-merge, or replacement occurred.

## Product implementation / hosted CI — GREEN

The D13 product implementation is complete enough for acceptance testing. Six concrete CodeRabbit findings were repaired with targeted regression coverage:

1. invalid LuckPerms inherited group names are filtered at the Paper authority boundary;
2. immediate reconciliation requests survive reconnect while a prior cycle is in flight;
3. coordinator shutdown waits for worker-cycle quiescence;
4. revision conflicts do not overwrite newer reconciliation state;
5. synchronous JDA mutation rejection preserves partial progress;
6. `StaffModerationConfiguration.toString()` formats diagnostic state correctly.

All six visible review threads are resolved. Repository policy may skip a fresh automatic full review; no fresh automatic full-review pass is claimed.

Exact-head hosted evidence for `92b207d67a1098acc2dcfbddd35ac56e01711f95`:

- Coverage/full validation run `34893756317`, job `104142632033`: **SUCCESS** on Temurin Java `21.0.12+1`; `clean build jacocoAggregateReport runtimeJars` passed and all unit/integration modules succeeded.
- JaCoCo: 52.52% lines / 42.70% branches / 54.75% instructions.
- Provider API source types inspected: 27; runtime leaks: 0.
- Paper SHA-256: `e717cc463f6768fa8046cec5b0f13f02b55355d1788b2d05394e9f79387c968f`.
- Velocity SHA-256: `05071ab07fa9e67da6968095e3a8d2107248eb315e140c10f0cb9720d5d0f89d`.
- Validation artifact `10368305800`, digest `sha256:9ff9346cec686734744c41d5a7a9829166cbef02e3c8ae3fe9b57d5b91ba9f61`.
- Staff Bot PR Artifact run `34893756524`: **SUCCESS**.
- Staff Bot Configuration Cache run `34893756377`: **SUCCESS**.
- Sentinel Restart Artifact run `34893756520`, job `104142890224`: **SUCCESS**.
- Sentinel Paper artifact `10367358096`, digest `sha256:6af49e6343b61c42a73987c80cc851ca0d05f78013f62e302489ae1becb221ab`.
- Authority bridge artifact `10367413075`, digest `sha256:4da2d4b57aa32b46551c3e1295939db8df34ed4222b21c21113f513d37352d8a`.
- Codacy Static Code Analysis check `104143227910`: **SUCCESS**, zero annotations / zero new valid findings.
- Codacy Diff Coverage check `104145475928`: **SUCCESS**, 55.93%; no repository diff-coverage gate is defined.

## Canonical Pi — FAILED BEFORE PRIVATE DISPATCH; NOT A RUNTIME RESULT

Exact-head public run: `34893930914`.

The trusted public exact-head build passed. Bridge job `104146199543` then failed in step `Revalidate exact candidate and supersede stale staging`. The request to `wsg138/EnthusiaStaff-Staging` workflow history returned HTTP `401 Bad credentials`.

Consequences:

- private staging dispatch: **NOT RUN**;
- Pi runner/Paper/database runtime: **NOT RUN**;
- correlated private verdict: **NOT RUN**;
- transient public transfer cleanup: **SUCCESS**.

This is not a Pi runtime pass and not a product runtime failure. The staging repository later received unrelated CombatLogX/Sentinel dependency branch activity, but its canonical `main` remained unchanged and there is no evidence the cross-repository staging credential/authorization condition was repaired. Therefore the identical canonical Pi path was not rerun.

## Sentinel — NO DURABLE PASS

The exact-head `@enthusia-sentinel test restart` request and a later `@enthusia-sentinel status` request were submitted on PR #178. The exact artifact build is green, but live reconciliation during this worker exposes no durable `PAPER_RESTART_OK` for the frozen D13 head.

Sentinel terminal state for this handoff: **NOT PASS / durable result unavailable**. No repeated identical request was enqueued.

## Original package gate — DiscordSRV parity NOT RUN / unavailable

The original ES-D13 contract requires staging parity with legacy DiscordSRV role sync before replacement/cutover. This is not a later worker-created optional gate.

Current authorized non-production repository/staging state exposes neither:

- an authoritative legacy Minecraft-group→Discord-role mapping/effective managed-role state; nor
- a staging StaffBot D13 role-sync runtime configuration/parity harness.

`wsg138/EnthusiaStaff-Staging` canonical `main` remains on pre-D13 controls. Its StaffBot Discord workflow is still the D05 smoke pinned to the D05 runtime and does not configure or exercise D13 role sync. The canonical staging tree contains no D13/DiscordSRV parity material.

Therefore required DiscordSRV parity is **NOT RUN**. Unit tests, hosted CI, the Sentinel artifact, or an ordinary Pi boot do not satisfy this original gate.

## Exact unblock

Provide an authorized **non-production** legacy DiscordSRV mapping/effective managed-role state and staging StaffBot D13 role-sync runtime configuration. Then:

1. keep legacy DiscordSRV role sync enabled;
2. run D13 in `SHADOW` across current linked identities;
3. compare effective managed-role state;
4. require zero unexplained managed-role drift;
5. retain only sanitized acceptance evidence;
6. reconcile the then-live implementation/main heads and rerun all applicable acceptance gates before merge.

Production cutover remains separately gated.

## Production/cutover boundary

No production DiscordSRV disablement, production Discord configuration/data access, production role mutation/enforcement, production deployment, LiteBans authority change, issue #43 acceptance, or cutover was authorized or performed.

## Parked disposition

`ES-D13` is `BLOCKED` / `PARKED_BLOCKED` on the unavailable original parity input/runtime. Preserve PR #178 and `package/es-d13-role-sync-replacement`. Do not merge the implementation PR while parity remains unsatisfied.

This documentation-only publication changes orchestration/status records only. It does not modify product code, tests, migrations, workflows, or runtime configuration.

The worker stops after this publication. Do not begin `ES-D07`, `ES-X03`, or another package in this worker.
