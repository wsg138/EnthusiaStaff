# ES-D13 — Discord role-sync replacement

Status: `BLOCKED`. Classification: `PARKED_BLOCKED`. Priority: 142. Depends on `ES-D04`, `ES-D05`. Internal package.

## Objective
Replace DiscordSRV role sync with reliable one-way Enthusia/Minecraft→Discord reconciliation while keeping moderation authorization separate.

## Scope
Evaluate role eligibility across all current linked Minecraft accounts; deterministic desired-role projection; protected/unmanaged role allowlist; durable reconciliation worker; add/remove idempotency; guild/native hierarchy handling; rate-limit/restart recovery; audit/diagnostics; migration parity comparison against DiscordSRV; only remove DiscordSRV role-sync dependency after parity validation. DiscordSRV may remain for console functionality.

## Exclusions
No Discord→Minecraft authority, no moderation permission derived from roles, no production switch until validated/authorized.

## Validation
Multi-account role union/conflict tests, unlink/main-account behavior, hierarchy/rate-limit/outage/restart tests, staging parity with legacy role sync and full CI/review.

## Current implementation

Implementation PR #178 remains open on `package/es-d13-role-sync-replacement`. Exact frozen candidate head: `92b207d67a1098acc2dcfbddd35ac56e01711f95`. Observed base/current `main` at blocker publication: `e7b338979c3824687147a0b3253638324571a3a7`.

The product implementation is complete enough for acceptance testing. Six concrete CodeRabbit correctness/stability findings were repaired with regression coverage and all visible review threads are resolved. Hosted exact-head build/test/artifact checks and Codacy are green. Production ENFORCE remains rejected by configuration/runtime safeguards.

### Exact-head hosted/static evidence

- Coverage/full validation `34893756317` / job `104142632033`: **PASS** on Temurin Java `21.0.12+1`; `clean build jacocoAggregateReport runtimeJars` succeeded with all unit/integration modules passing.
- JaCoCo: 52.52% lines / 42.70% branches / 54.75% instructions.
- Provider API source inspection: 27 types / 0 runtime leaks.
- Validation artifact `10368305800`, digest `sha256:9ff9346cec686734744c41d5a7a9829166cbef02e3c8ae3fe9b57d5b91ba9f61`.
- Paper SHA-256 `e717cc463f6768fa8046cec5b0f13f02b55355d1788b2d05394e9f79387c968f`; Velocity SHA-256 `05071ab07fa9e67da6968095e3a8d2107248eb315e140c10f0cb9720d5d0f89d`.
- Staff Bot PR Artifact `34893756524`: **PASS**.
- Staff Bot Configuration Cache `34893756377`: **PASS**.
- Sentinel Restart Artifact `34893756520` / job `104142890224`: **PASS**; Paper artifact `10367358096`, digest `sha256:6af49e6343b61c42a73987c80cc851ca0d05f78013f62e302489ae1becb221ab`; authority bridge artifact `10367413075`, digest `sha256:4da2d4b57aa32b46551c3e1295939db8df34ed4222b21c21113f513d37352d8a`.
- Codacy Static Code Analysis `104143227910`: **PASS**, zero annotations / zero new valid findings.
- Codacy Diff Coverage `104145475928`: **PASS**, 55.93%; no repository diff-coverage gate is defined.
- CodeRabbit: six concrete findings repaired/resolved; all visible review threads resolved. A fresh automatic full-review pass is not claimed where repository policy skips it.

### Canonical Pi — NOT PASS

Exact-head public run `34893930914` successfully built the candidate, but bridge job `104146199543` failed before private dispatch in `Revalidate exact candidate and supersede stale staging` because the request to `wsg138/EnthusiaStaff-Staging` workflow history returned HTTP `401 Bad credentials`.

No private staging workflow was dispatched, no Pi runner/Paper/database runtime step executed, and transient public transfer cleanup succeeded. This is neither a Pi runtime pass nor a product runtime failure. Do not rerun the identical path until there is evidence the cross-repository staging credential/authorization condition changed.

### Sentinel durable state — NOT PASS / unavailable

The exact-head `@enthusia-sentinel test restart` and later `@enthusia-sentinel status` requests were submitted. The exact artifact-build gate is green, but live reconciliation at blocker publication exposes no durable `PAPER_RESTART_OK` for `92b207d...`. Do not represent Sentinel as passing and do not repeatedly enqueue identical requests without evidence the condition changed.

## Original blocking gate — DiscordSRV staging parity NOT RUN

The original ES-D13 contract requires staging parity with legacy DiscordSRV role sync before replacement/cutover. Current authorized non-production repository/staging state exposes neither:

1. an authoritative legacy Minecraft-group→Discord-role mapping/effective managed-role state; nor
2. a staging StaffBot D13 role-sync runtime configuration/parity harness.

The trusted staging `main` remains on its pre-D13 controls; its StaffBot Discord smoke is the D05 smoke and does not configure or exercise D13 role sync. No authoritative D13 parity input/runtime is available. Unit tests, hosted CI, Sentinel artifact creation, or an ordinary Pi boot do not satisfy this original package gate.

### Exact unblock
Provide an authorized non-production legacy DiscordSRV mapping/effective managed-role state and staging StaffBot D13 role-sync runtime configuration. Keep legacy role sync enabled, run D13 in `SHADOW` across current linked identities, require zero unexplained managed-role drift, and retain only sanitized acceptance evidence. Then reconcile the live implementation head and rerun every applicable acceptance gate before considering merge. Production cutover remains separately gated.

## Parked disposition

ES-D13 is `BLOCKED` / `PARKED_BLOCKED`. Preserve PR #178 and `package/es-d13-role-sync-replacement`; do not merge, rebase, squash, force-push, auto-merge, or replace them while this gate is unsatisfied.

No production DiscordSRV disablement, production Discord configuration/data access, production role-sync enforcement, LiteBans authority change, issue #43 acceptance, or cutover was authorized or performed.

Canonical blocker handoff: `ai-agents/reports/package-handoffs/2026-09-14-es-d13-role-sync-blocked.md`.
