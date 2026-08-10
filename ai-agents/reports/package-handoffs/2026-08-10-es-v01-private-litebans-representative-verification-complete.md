# ES-V01 private LiteBans representative-data verification — COMPLETE

Date: 2026-08-10

## Terminal status

`ES-V01 — Private LiteBans representative-data verification` is `COMPLETE`.

Worker-start `main`: `b78a62de3876bfde7fa5f57860fedc1415ef3c53`.

Final frozen PR #110 head: `de39e30232df9bd44d4b4df54a8922e815bada76`.

PR #110 merged with a normal merge commit as `9a6c7240a4f6fffd216af0239709867b79080ddc`. The merge has the frozen feature head as its second parent, no unique feature-tree delta remains, and GitHub auto-deleted `package/es-v01-litebans-private-verification`.

## Private representative evidence disposition

The private LiteBans database was never requested, uploaded, or re-read by the hosted worker. The completed local/Codex evidence was preserved as sanitized aggregate evidence only:

- MariaDB 10.11.6 LiteBans source;
- `litebans_` prefix;
- 102 bans;
- 53 mutes;
- 1,747 history rows;
- 153 supported sanctions imported successfully;
- replayed all 153 without duplicate cases/events;
- zero mapped issue/expiry mismatches;
- abandoned-run recovery passed;
- 2 `INVALID_SOURCE_ROW`;
- 5 `INVALID_HISTORY_ROW`;
- 49 warnings and 44 kicks intentionally audit-only/unsupported;
- 322 invalid historical usernames ignored while usable UUID/network information remained.

The seven rejected rows remain unchanged later data-policy input. They were not silently repaired, discarded, skipped, or rewritten. Any later rehearsal must explicitly decide whether to correct source data or retain each durable rejection through the documented operator process.

## UUID-only compatibility repair

The locally discovered repair `22934e33` was reproduced on the canonical ES-V01 branch as `ea07f55a`. It makes sanction-table usernames optional when LiteBans supplies UUID identity and allows the reader to select a null username when no name column exists.

Substantive review additionally identified that the integration fixture exercised a UUID-backed mute and IP-only ban but not a UUID-backed ban. Final head `de39e30232df9bd44d4b4df54a8922e815bada76` added a distinct UUID-only non-IP ban while retaining the UUID-backed mute and IP-only ban path. Malformed-source rejection behavior was not weakened. No Flyway migration changed.

## Hosted Java 21 and Coverage

Final exact-head Coverage run `31353964138`, job `93349968412`, succeeded on `de39e30232df9bd44d4b4df54a8922e815bada76` with Java 21, the full Gradle test path, and MariaDB/Testcontainers.

An earlier exact-head Coverage run `31352944816` also succeeded. The fresh reopen-triggered run `31353964138` is the terminal exact-head evidence used for the final pre-merge reconciliation.

## Codacy

Final exact-head Codacy evidence:

- Static Code Analysis `93347267178`: success, zero annotations;
- Diff Coverage `93350870761`: success, 100.0%;
- Coverage Variation `93350870850`: success, +0.01% against the configured -1.0% target.

## Substantive review

The substantive CodeRabbit pass on pre-review head `2485c8b7a4a80ae306216eb9f66f1e9415d9eac0` found three valid documentation consistency findings and the UUID-backed-ban integration coverage gap:

1. bind ES-V01 routing to existing PR #110 instead of instructing a worker to open another draft PR;
2. reconcile the package boundary with the legitimate product-repair PR;
3. remove stale ES-P05 terminal routing that could stop ES-V01 continuation;
4. exercise a UUID-backed ban in integration coverage while retaining the mute and IP-only paths.

All were fixed in `de39e30232df9bd44d4b4df54a8922e815bada76`. All three substantive review threads are resolved/outdated and CodeRabbit marked them addressed by `de39e30`; valid unresolved thread count is zero.

The automatic incremental new-finding review after the fix was rate-limited. It is not represented as a second full CodeRabbit review. The exact-head CodeRabbit commit status was success, the fix diff was manually reconciled, and the final hosted gates exercised the changed integration fixture.

## Canonical Pi staging

### Historical non-passing attempts

The final source head did not change across these attempts.

Public run `31352943731` dispatched private run `31353309582` / job `93348082170`. Exact bridge provenance and trusted runner identity passed, but guarded Paper startup failed before any completed Paper/storage-ready cycle (`server_starts_completed=0`, `storage_ready_cycles_completed=0`) while the shared Pi had a competing Java process and constrained resources. This is not counted as a pass.

A GitHub rerun of the same public run dispatched private run `31353848239` / job `93349648289`. The private provenance guard rejected it before Paper because the attempt-bound manifest was stamped for public attempt 1 while the rerun bridge requested attempt 2. This is not counted as a pass and the guard was not weakened.

### Final passing public run

Canonical public run `31353964382` used the same exact source head `de39e30232df9bd44d4b4df54a8922e815bada76` and a fresh run ID/attempt:

- `Build trusted EnthusiaStaff Paper runtime` job `93349969346`: PASS;
- exact source selection and Java 21 build: PASS;
- runtime package and checksum-bound manifest: PASS;
- bounded transient release transfer: PASS;
- `Bridge verified runtime to private Pi staging` job `93350945971`: PASS;
- private run correlation: PASS;
- transient public transfer removal: PASS;
- `Explain fork staging boundary` job `93349969918`: SKIPPED, not passed; it was not the applicable authorized-PR path.

### Final passing private run

Correlated private run `31354311211`, job `93350973876`, succeeded on trusted runner `Lincoln-PI-4` against staging-controls head `991316917be5116546a3ceab101d0ad9e6b1dca3`.

Public/private provenance was pinned to:

- source SHA `de39e30232df9bd44d4b4df54a8922e815bada76`;
- source selection `authorized_pr`;
- PR `110`;
- branch `package/es-v01-litebans-private-verification`;
- public run `31353964382`, attempt `1`;
- public workflow SHA `b78a62de3876bfde7fa5f57860fedc1415ef3c53`;
- transient transfer tag `es-r01-staging-31353964382-1`;
- transfer asset SHA-256 `4da8aa0b4fc5f2f14f41916da6b66fad32b82be13caedc5873e3d511eed54a0b`.

Private artifact validation passed for `EnthusiaStaff-Paper-0.1.0-SNAPSHOT.jar`, size 9,145,475 bytes, SHA-256 `65779e5decfa751e3140b972185cc60d1d6a14a4b185c7cc00533a2e1d11b024`. Allowlist, manifest, checksum, ZIP integrity, plugin main class, and provider-API checks all passed with zero provider API leaks.

Guarded database/runtime phases:

- required database variables present without recording their values: PASS;
- disposable DB identity before reset: PASS, zero objects removed;
- first Paper cycle: PASS, storage-ready in 15 seconds, mode `SHADOW_MIGRATION`;
- first Flyway cycle: empty schema → V1 through V18, 18 migrations applied, PASS;
- first clean shutdown: exit code 0, stopping marker and all-dimensions-saved marker PASS;
- first critical failure scan: PASS;
- second Paper/restart cycle: PASS, storage-ready in 8 seconds, mode `SHADOW_MIGRATION`;
- second Flyway cycle: schema 18 current, no migration necessary, PASS;
- second clean shutdown: exit code 0, stopping marker and all-dimensions-saved marker PASS;
- second critical failure scan: PASS;
- post-run disposable DB reset: PASS, 69 objects removed;
- unrelated `enthusia-helper-bot.service` and `astrotimelapse.service`: active before and after;
- final failure count: zero.

Sanitized private evidence artifact: `9050381344`, digest `sha256:34f77c0fe32fee5c79872daf9487371b17404f3308c4212b736b6f011a194bd0`.

## Merge and containment

Immediately before merge, PR #110 was open, ready for review, mergeable, and frozen at `de39e30232df9bd44d4b4df54a8922e815bada76`; `main` was still the worker-start SHA `b78a62de3876bfde7fa5f57860fedc1415ef3c53`. Zero valid unresolved review threads remained.

PR #110 was merged using a normal merge commit only:

`9a6c7240a4f6fffd216af0239709867b79080ddc`

The merge commit's second parent is the exact frozen feature head. Compare/containment showed the merge one commit ahead of the feature head with no unique feature-tree delta, so no ES-V01 package work remained outside `main`. GitHub auto-deleted the package branch.

## Boundaries preserved

ES-V01 did not:

- request or expose the private LiteBans dump;
- begin a production shadow window;
- migrate production data;
- alter LiteBans authority;
- activate issue #43;
- perform production cutover;
- modify ES-P07, ES-P06, ES-X01, or another package;
- rewrite a Flyway migration.

V18 remains current and immutable. LiteBans remains authoritative. Issue #43 remains open/deferred.

## Routing after completion

ES-V01 is terminal `COMPLETE`.

No package becomes newly `READY` solely because ES-V01 completed. `ES-P07 — Inventory and Ender editing runtime completion` was already `READY` and is now the highest-priority package available to the next sequential worker. `ES-P06` remains `READY` behind it. `ES-X01` remains `BLOCKED` / `PARKED_BLOCKED`. `ES-A01` remains deferred/blocked because ES-V02, ES-V03, owner authorization, and issue #43 are still required.

Exact next action: a **new** sequential worker should reconcile live GitHub and select ES-P07 if live state still agrees. This ES-V01 worker must stop and must not start it.
