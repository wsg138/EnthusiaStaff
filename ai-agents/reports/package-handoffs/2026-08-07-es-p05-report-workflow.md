# ES-P05 package handoff — report evidence and staff workflow completion

Recorded: 2026-08-07 America/Chicago

## Package and routing

| Field | Value |
| --- | --- |
| Package | `ES-P05 — Report evidence and staff workflow completion` |
| Classification at terminal publication | `BLOCKED` / `PARKED_BLOCKED` |
| Repository | `wsg138/EnthusiaStaff` |
| Starting legitimate `main` | `bf9b305ba96d9536f3d111c79eef674bd2e11dc5` |
| Implementation branch | `package/es-p05-report-workflow` |
| Implementation PR | #81, open and unmerged |
| Frozen implementation / hosted-validation head | `4a38e191395913c6733726e222f0889a2d56d267` |
| Migration boundary | immutable V17; no ES-P05 migration |
| External parity | not applicable; internal package |

Live GitHub and current default-branch source override this handoff if they diverge. This file records only ES-P05 and must not be used to activate another package.

## Selection and startup reconciliation

- Read the universal package prompt and required package policies from current legitimate `main`.
- Open package PR inventory contained only PR #70, `ES-P02 — Runtime database recovery and Velocity reload`.
- ES-P02 remained `BLOCKED` / `PARKED_BLOCKED` on the unchanged private Actions Billing & plans restriction and therefore was not actionable.
- No ES-P05 branch, PR or handoff existed before this worker.
- ES-P03 and ES-P04 were complete. ES-P05 was the lowest-priority dependency-complete READY package ahead of ES-P09 and ES-P10.
- The separate `docs/wiki-maintenance-2026-08` branch was recognized as documentation-only work outside package-state authority and was not modified.
- Issue #43 remained open/deferred; LiteBans remained authoritative.

## Completed provider-independent implementation

The frozen head `4a38e191395913c6733726e222f0889a2d56d267` completes the intended provider-independent report workflow additions without changing V17:

- preserved durable report submission through the authoritative player directory, including offline target resolution and existing same-reporter locking, cooldown/open-limit, duplicate merge and idempotency behavior;
- preserved queue/detail GUI, private action-note flow, exact-revision transitions, stale-state rejection, async load fencing and text/console/Bedrock fallback;
- added a dedicated `enthusiastaff.reports.evidence` permission, separate from ordinary `enthusiastaff.reports.manage` triage;
- added `/reports evidence <report-id> <public|private|client> [snapshot] [page]`;
- default evidence selection uses the newest retained snapshot when an explicit snapshot is omitted;
- public/private chat is rendered as bounded messages/pages rather than raw retained JSON;
- client evidence is rendered from a strict operational allow-list, including a nested AutoClicker field allow-list; opaque Polar metadata is withheld;
- malformed retained evidence fails closed and does not echo raw storage;
- exact reporter/target coordinates are no longer displayed to broad helper-level GUI/text triage and require the sensitive evidence permission through the text detail path;
- direct arbitrary screenshot/file/URL/binary attachments are explicitly unsupported by ES-P05 rather than being exposed through an unaudited upload path;
- updated the report/evidence Wiki with commands, privacy, retention, restart, attachment and operational workflow guidance.

Explicit exclusions were preserved:

- RoseChat private-message provider capture remains ES-X01;
- Discord route rendering/delivery remains ES-P06;
- production evidence/routes, private production records, deployment, issue #43 acceptance, cutover and authority activation remain excluded.

## Automated proof

New direct proof:

- `ReportEvidenceFormatterTest` covers bounded pagination, private-message direction, strict client allow-listing, opaque metadata withholding, newest-snapshot selection, malformed/range boundaries and explicit unsupported attachment kind.
- `ReportWorkflowWiringTest` covers player/staff command binding to the durable store, offline-directory/evidence submission wiring, dedicated evidence permission, no raw snapshot dumps, GUI coordinate/snapshot privacy and rank permission separation.
- `ReportRestartIntegrationTest` starts MariaDB runtime, submits a report with all retained evidence types, closes/reopens the runtime twice, changes state after restart, and proves evidence/state/revision/assignment durability.

Existing report integration coverage retained and rerun includes duplicate merge, ordinary/same-target cooldown behavior, idempotent replay/conflict, concurrent staff changes, stale revision rejection, lifecycle transitions, bounded evidence purge and transactional rollback.

## Harsh review record

Before freeze, the complete product diff was reviewed adversarially. Three confirmed issues were repaired:

1. exact reporter/target coordinates were still exposed through broad GUI triage despite the new sensitive permission;
2. the initial client renderer serialized the whole nested AutoClicker handshake object instead of strictly allow-listing its fields;
3. omitted evidence snapshot selection initially defaulted to the oldest snapshot rather than the newest retained context.

After those fixes, the product head froze at `4a38e191395913c6733726e222f0889a2d56d267`. Zero inline review threads exist. Codacy reported zero issues. CodeRabbit was unable to start its final review because the review quota was temporarily exhausted; it returned no product finding and must be rerun before merge when ES-P05 resumes.

## Successful exact-head hosted validation

All ordinary executable hosted gates succeeded on exact head `4a38e191395913c6733726e222f0889a2d56d267`:

- Wiki run `31183192145`, job `92881243088`: success.
- Coverage run `31183192068`, job `92881313210`: success on GitHub-hosted Java 21, including full build/tests, MariaDB/Testcontainers, migration integrity, aggregate JaCoCo, runtime-JAR creation/inspection, artifact upload and Codacy coverage upload.
- Validation artifact `8995826742`, `java-21-validation`, digest `sha256:ed87314d5eda8286928ce64f11027240898a0823333c6ffa5aa6d98f1697dbe4`.
- Codacy Static Code Analysis `92882185524`: success, zero issues/annotations.
- Codacy Coverage Variation `92882989470`: success, `+0.05%` variation.
- Codacy Diff Coverage `92882989439`: success, `49.05%` diff coverage; repository gate not defined.

## Terminal blocker — private staging unavailable

The required trusted private build and Pi boot/restart route did not execute product code.

Latest exact-head attempt:

- public wrapper run `31183283525`, job `92881545286`;
- downstream private run `31183290816`;
- trusted `ubuntu-latest` build job `92881577147`: `failure`, runner ID `0`, empty runner name, `steps: []`;
- GitHub annotation: the job was not started because recent account payments failed or the Actions spending limit needs to be increased under **Billing & plans**;
- Pi job `92881591391`: skipped because the trusted build never ran.

An earlier automatic exact-head dispatch (`31183190537` -> `31183198718`) produced the same zero-runner Billing & plans failure. After confirming the blocker was unchanged, no manual identical retry was issued.

This evidence is infrastructure-unavailable, not a product defect, but it is not a pass. The repository validation policy does not permit this worker to merge ES-P05 around a missing ordinary trusted build/Pi gate. ES-P04's owner-approved exception is explicitly package-specific and cannot be reused for ES-P05.

## Exact unblock condition

The repository owner must resolve the GitHub Actions payment/spending-limit restriction affecting private repository `wsg138/EnthusiaStaff-Staging`.

Then a resumed ES-P05 worker must:

1. reconcile live GitHub and any newer legitimate `main`;
2. preserve the frozen product work unless a newly confirmed defect requires changes;
3. integrate newer `main` through the normal merge rules when necessary—never rebase/force-push;
4. freeze the resulting exact merge candidate;
5. rerun CodeRabbit and resolve every valid finding/thread;
6. rerun all hosted/static checks on the exact candidate;
7. obtain a successful trusted private build and Pi safe boot/restart for the same exact source head;
8. merge PR #81 normally;
9. verify merge containment, publish final COMPLETE state and clean the temporary branch;
10. stop without starting another package.

Do not treat the existing failed/skipped staging evidence as passed and do not repeat the same zero-runner attempt while the external condition is unchanged.

## Terminal routing boundary

ES-P05 is parked; it is not complete and PR #81 remains open. ES-P02 is also parked on the same unchanged external condition. ES-P06 and ES-X01 remain dependency-blocked by ES-P05. ES-P09 and ES-P10 remain READY and unassigned. No second package was selected or started by this worker.
