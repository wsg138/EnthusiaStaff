# ES-R01 deadlock-recovery final routing handoff

Recorded: 2026-08-08.

This is the stable terminal routing declaration for the owner-directed deadlock-recovery worker. It changes no product code, workflow, configuration, migration, provider authority, production route, or validation rule.

## Verified recovery result

- Starting legitimate `main`: `41659389ba105e099c77966015714067ea6f1ae7`.
- ES-P02 remains `GENUINELY_EXTERNAL_BLOCKER` / `PARKED_BLOCKED` on PR #70 because the current private `ubuntu-latest` staging build still fails before runner allocation under GitHub Billing & plans; the Pi job is consequently skipped. No ES-P02 exception exists and the unchanged gate was not rerun merely to reproduce failure.
- ES-P05 remains `GENUINELY_EXTERNAL_BLOCKER` / `PARKED_BLOCKED` on PR #81 for the same mandatory staging-route condition. Its older CodeRabbit-quota note was stale as a secondary condition: live exact-head CodeRabbit status is successful and zero review threads remain.
- No existing product dependency was relaxed. ES-P07 still technically needs ES-P02 lifecycle/reload foundations; ES-P06 still overlaps unmerged lifecycle/report integration; later destructive/provider/validation packages retain real prerequisites.
- The private staging environment is not wholly unavailable: self-hosted job `93064778261` succeeded on runner `Lincoln-PI-4`. The blocked element is the private GitHub-hosted `ubuntu-latest` build.
- Finite repository-side work can preserve both mandatory validation classes without relying on private hosted minutes: build and validate the exact authorized source on public `wsg138/EnthusiaStaff` hosted infrastructure, preserve immutable artifact/source provenance, then hand the exact verified artifact to the existing private self-hosted Pi safe boot/restart job.

## Canonical package-system change

`ES-R01 — Billing-independent staging bridge recovery` is the sole legitimate `READY` package, priority 15, with no package dependency. It is workflow/tooling/test/documentation infrastructure work only across `wsg138/EnthusiaStaff` and `wsg138/EnthusiaStaff-Staging`; it is not a validation exception and it does not complete ES-P02 or ES-P05.

Definition PR #90 froze at `5c68df5b774625ae78edce3b71f86dbc9c47951c`, passed exact-head public build/tests/coverage, Codacy, and CodeRabbit after five valid documentation/process findings were fixed, and merged normally as `25fee003bd94b605f18f71b54c014fb7b0547b94`. Exact containment and definition-branch deletion were verified.

Terminal evidence PR #91 froze at `c9359d7fcbae45fdaa4a4ecacd4cf97776666c18`, changed only three Markdown/package-state records, passed its exact-head public build/tests/coverage, Codacy, and CodeRabbit with zero review threads, and merged normally as `4cefb926fdefa4aad63c01b4acddf6afb43beacd`. Exact containment and terminal-branch deletion were verified after merge.

For both process-only PRs, the automatically triggered Pi wrapper followed the still-broken private-hosted route and failed before the private hosted build could allocate. Those runs are **NOT PASSES**. Runtime/Pi validation was genuinely non-applicable to the documentation-only diffs under `VALIDATION-POLICY.md`; no package-specific infrastructure exception was created or generalized.

V18 remains immutable/current. Issue #43 remains open/deferred. LiteBans remains authoritative. No production cutover or provider authority changed.

## Next normal sequential worker

When this handoff is present on `main`, recovery publication is complete. The next normal sequential worker must select **only ES-R01**, reconcile both required repositories, create the two implementation branches/PRs required by the ES-R01 contract, implement and prove the exact-source public-hosted-build → verified-artifact → private self-hosted-Pi bridge, merge normally, publish ES-R01 terminal state, and stop.

After ES-R01 completes, a policy-valid repository-side staging route exists even if GitHub billing remains blocked. Resume ES-P02 before ES-P05 and rerun each package's own synchronized exact-head hosted/static/review/staging gates. ES-R01's bridge proof is not a substitute for either package's validation evidence.

No owner action is required to begin ES-R01 with the live prerequisites observed by this recovery worker. Fixing the private GitHub Actions Billing & plans restriction remains an alternate direct way to restore the old route. If ES-R01 implementation later proves that secure artifact transfer needs a new owner-only credential or the trusted self-hosted runner becomes unavailable, record that exact new external prerequisite and stop rather than weakening validation.