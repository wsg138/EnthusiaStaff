# ES-X04 — EnthusiaCommend provider — historical Pi artifact-quota blocker

Date: 2026-08-24

Status at this checkpoint: `BLOCKED` / `PARKED_BLOCKED`.

This handoff preserves the exact non-passing X04 state before the private Pi evidence-storage mechanism was changed. Live GitHub and newer handoffs override this historical checkpoint.

## Frozen implementation heads

- `wsg138/EnthusiaCommend` PR #12: `325c304512187f274463c31f1649efe0ae56ab7d`.
- `wsg138/EnthusiaStaff` PR #152: `7d525649e293e7af894587089e4e8a7e73597c9c`.

Both implementation PRs remained open and unmerged.

## Passing exact-head evidence before the blocker

- Commend exact-head Java 21 run `32797266212`, job `97651014296`: exact checkout, 110 tests, PMD, artifact `9545261529`; PASS.
- Staff Coverage/full run `32797272290`, job `97651031716`: exact Staff head, Java 21 full build/tests including MariaDB/Testcontainers, provider-leak inspection, JaCoCo and Codacy coverage; PASS.
- Staff Sentinel artifact run `32797272316`, job `97651031742`; PASS.
- Sentinel restart request comment `5403790637`, durable job `246`: terminal `PAPER_RESTART_OK`; PASS for the Sentinel restart profile.
- Review threads were resolved. Standalone Commend Codacy reported zero new issues; the Staff aggregate first-import component findings were separately scoped without weakening analyzer policy.

## Canonical Pi non-pass

Canonical public Pi run `32797271342` correlated private run `32797866588`, job `97652750867`, on trusted runner `Lincoln-PI-4`.

The following private steps passed:

- trusted runner identity;
- exact public bridge artifact retrieval and provenance verification;
- guarded disposable MariaDB/Paper boot and restart runtime test.

The final private evidence step then failed because `actions/upload-artifact` could not create a new artifact:

```text
Artifact storage quota has been hit. Unable to upload any new artifacts.
Usage is recalculated every 6-12 hours.
```

The public transient runtime-transfer release/tag cleanup succeeded. The canonical public result correctly remained terminal `failure`. Runtime success was not relabeled as a canonical staging pass.

## Follow-up direction

The owner subsequently approved replacing private canonical Pi **evidence output** storage with private GitHub Release assets on every run, rather than waiting for or paying around the Actions artifact quota. That infrastructure change belongs to `wsg138/EnthusiaStaff-Staging` and must itself be validated/merged before a fresh exact-head X04 Pi run can clear this historical blocker.

No production data, deployment, migration, website/Discord implementation, LiteBans authority, or cutover changed at this checkpoint.
