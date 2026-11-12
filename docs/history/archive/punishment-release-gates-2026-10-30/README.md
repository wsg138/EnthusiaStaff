# Guided punishment release gates — October 30, 2026 (historical)

**Archived evidence, not a live release checklist or current deployment authority.** The accompanying [original ledger](ORIGINAL-RELEASE-GATES.md) was copied byte-for-byte from the historical [PR #761 head](https://github.com/wsg138/EnthusiaStaff/commit/bd37f9db0aef0351749189f91eeddaf1916fb4f2), preserving exact original Git blob `2cbf28f34594888cd79ed7a378b2ade6a1b97c78`.

## What this record documents

The October 30 ledger records a contemporaneous guided-punishment **NO-GO**, operator policy decisions, differences between installed and bundled `evasion.ban` ladders, source/runtime QA boundaries, Folia lifecycle risks, stacked PR ancestry, and staging/rollback prerequisites. Its quoted on-disk file versions, check runs, branch counts, and observations are explicitly **October 2026 snapshots**. Never interpret them as present server state or as commands to apply an installed policy.

## Changes since that snapshot

- The exact target UUID and owner-thread punishment effect safety changes reached current source through [merged #800](https://github.com/wsg138/EnthusiaStaff/pull/800). The old #753 and #763 PR conversations were closed with their dependent branches preserved.
- The standalone guarded **dry-run-by-default** migration tool reached current source through [merged #805](https://github.com/wsg138/EnthusiaStaff/pull/805), with 18 passing offline fixture tests, improved post-swap uncertainty handling, verified backups and no new Codacy issues; old #764 closed as superseded.
- [#806](https://github.com/wsg138/EnthusiaStaff/pull/806) and [#807](https://github.com/wsg138/EnthusiaStaff/pull/807) address distinct Folia-related source threading paths but do **not** establish Folia admission or runtime compatibility; check each PR's actual state when reading this guide.
- The broad [#765](https://github.com/wsg138/EnthusiaStaff/pull/765) guided-punishment integration remains a separate draft requiring per-change reconciliation. The full guided policy/classification system and real Java/Bedrock GUI remain unapproved/unverified.
- Source merges and synthetic tests did **not** update the installed policy, execute a production migration, build and deploy a new release, restart the server, or complete the real-client staging acceptance matrix.

For latest status, consult [coordinator STATUS](../../../repository-cleanup/STATUS.md), [issue #426](https://github.com/wsg138/EnthusiaStaff/issues/426), and current merged source/tests—not the original October ledger.

## Retention

The original [PR #761](https://github.com/wsg138/EnthusiaStaff/pull/761), head commit, source branch, discussion and evidence history are intentionally preserved. Archiving this file is not permission to delete branches or close dependent PRs without checking live references. No policy migration, API access, or live server action is authorized by this historical archive.
