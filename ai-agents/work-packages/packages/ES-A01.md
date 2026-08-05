# `ES-A01` — LiteBans cutover acceptance

## 1. Package identity
`ES-A01`; Production acceptance; primary `COMP-STAFF`; all release components; priority 300; owner-led and not parallel-safe.

## 2. Status
Initial `DEFERRED`; registry is authoritative.

## 3. Objective
Run issue #43's owner-authorized production-like LiteBans acceptance campaign against one pinned release candidate.

## 4. Why the package exists
Migration/cutover machinery is dormant and synthetic-tested, but the 168-hour shadow, distributed rehearsal, activation ambiguity, freeze, rollback, and final authorization have not occurred.

## 5. Included audit IDs
`AUD-MIG-005`, `AUD-MIG-006`, `AUD-MIG-007`, and issue #43.

## 6. Included behavior
Pin exact repo/JAR/config/provider/environment/operator revisions; representative isolated restore; schema/dry-run/rejected/mapping/checksum evidence; interrupted migration/restart; uninterrupted 168-hour shadow with seven daily summaries; maintenance/final incremental rehearsal; Velocity/HUB/SMP restarts; ambiguous activation retry; emergency freeze persistence; rollback/reconciliation; Java/Bedrock/provider/outage/latency/load evidence; formal owner review/approval.

## 7. Explicit exclusions
Ordinary coding; automatic deployment/authority; unapproved production access; hiding failed gates; changing LiteBans before approval.

## 8. Dependencies
`ES-V01`, `ES-V02`, and `ES-V03` must be `COMPLETE`, all implementation packages applicable to the release candidate must be complete, and the owner must explicitly authorize the campaign.

## 9. Component and repository boundaries
Owner-controlled isolated/production-like environment plus sanitized acceptance records. No implementation changes, permanent component branches, or isolated PRs.

## 10. Required branches
Do not begin with a branch. If a sanitized final acceptance record is committed, use temporary `package/es-a01-litebans-cutover-acceptance`; delete after merge.

## 11. Required PRs
One EnthusiaStaff evidence/documentation PR only when the complete owner-authorized sanitized record is ready. No product/external component PR implied.

## 12. Implementation checklist
Verify all gates/dependencies; pin exact candidate and artifacts; owner approves plan/operators/window/rollback; run every issue #43 scenario without changing authority prematurely; retain sanitized immutable evidence; investigate every mismatch; complete formal approval; update issue/registry/handoff; review/merge optional evidence PR; cleanup temporary branch.

## 13. Acceptance criteria
Every issue #43 checkbox has revision-specific evidence; 168-hour window uninterrupted with no unexplained mismatch; activation/freeze/rollback/restart/idempotency/topology/provider/client gates pass; owner signs off before any live authority/cutover action.

## 14. Test requirements
All prior exact-head suites/evidence plus the complete issue #43 production-like scenario matrix; failed/interrupted runs are recorded, never erased or called passing.

## 15. Static-analysis requirements
Pinned candidate must retain green CI/static analysis/review; acceptance-doc changes pass configured validation.

## 16. Documentation requirements
Controlling `docs/cutover-acceptance.md`, issue #43, exact candidate/environment/operators, daily summaries, decisions, rollback plan/results, final approval, registry/handoff.

## 17. Security and privacy requirements
Owner-controlled credentials/data/routes; sanitized non-reconstructable evidence only; strict access/retention; no secrets/private rows in GitHub/ChatGPT.

## 18. Migration impact
No migration bytes/history changes. Acceptance may execute dormant migrations on isolated copies only under the approved runbook; no Flyway repair.

## 19. Bedrock considerations
Explicit Java/Bedrock login, mute, network-ban, expiration, switching, identity, and fallback acceptance is mandatory.

## 20. Distributed-runtime considerations
Velocity/HUB/SMP switching/restarts, ownership/fences, duplicate/ambiguous responses, outages/reconnect, saturation and rollback are mandatory.

## 21. External-provider considerations
Pin and accept every release provider; outages/incompatibility and restoration behavior must be evidenced.

## 22. Completion definition
All issue #43 gates and owner approval complete; any sanitized evidence PR merged; exact release record immutable; only then may separate live deployment/cutover authorization be considered.

## 23. Resume state
Deferred/unassigned; no branch/PR/handoff. Do not start without owner authorization and completed prerequisites.

## 24. Last completed checkpoint
Definition only; issue #43 remains open and no acceptance window began.

## 25. Remaining checklist
All prerequisite completion, owner authorization, candidate pinning, 168-hour campaign, rehearsals, approval, and evidence remain.

## 26. Known blockers
Owner authorization, pinned release candidate, isolated representative environment/data, and completion of `ES-V01/V02/V03`.

## 27. Final evidence
Unset: candidate/JAR/config/provider hashes, operators/environment, daily summaries, all scenario results, approvals, issue/PR links.

## 28. Merge and synchronization record
Optional evidence PR only; record merge/containment/temp branch cleanup. This package does not authorize product code changes or permanent branches.
