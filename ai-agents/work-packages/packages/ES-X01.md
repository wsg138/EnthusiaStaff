# `ES-X01` — RoseChat provider and communication integration

## 1. Package identity
`ES-X01`; External/multi-repository; primary `COMP-STAFF`; other `COMP-ROSECHAT`; priority 100; conditional parallel safety.

## 2. Status
`BLOCKED` / `PARKED_BLOCKED` after a 2026-08-26 `ACTIONABLE_CONTINUATION`. The previously unresolved repository condition changed and was reconciled, but source import is now blocked by the verified provider license/aggregate-publication boundary.

## 3. Objective
Use the verified supported RoseChat repository to implement staff chat, private-message evidence, and presence integration safely.

## 4. Why the package exists
Communication routing/audiences and PM evidence require the actual provider implementation; inventing APIs would create privacy and duplication defects.

## 5. Included audit IDs
`AUD-COMMS-001`, `AUD-COMMS-003`, RoseChat portion of `AUD-REPORT-003`, provider portion of `AUD-VANISH-003`.

## 6. Included behavior
Use the actual repository/default head; supported callbacks and delivery timing; sender/recipient/cancellation/filter/ignore/spy semantics; staff audiences/formatting/cross-server duplicate prevention/disconnect; privacy-safe PM evidence/retention; presence integration; matching aggregate copy and parity.

## 7. Explicit exclusions
Invented repository/API; reflection against unknown implementation; log scraping as callback; unrelated voice provider; production PM data/routes; publishing or redistributing provider source without a verified right to do so.

## 8. Dependencies
`ES-P03`, `ES-P04`, and `ES-P05` are `COMPLETE`.

## 9. Component and repository boundaries
`wsg138/EnthusiaStaff` root integration plus `components/enthusia-rosechat/`, and verified standalone `wsg138/Enthusia-RoseChat` only. No permanent component branch or isolated PR.

## 10. Required branches
When unblocked, temporary `package/es-x01-rosechat-provider` in EnthusiaStaff and the standalone repository (or stricter compatible standalone convention); delete after verified merges. No implementation branch was created during the 2026-08-26 blocker publication.

## 11. Required PRs
When unblocked, two cross-referenced same-ID PRs: one standalone and one to `EnthusiaStaff:main`. No third/isolated PR. The 2026-08-26 state-publication PR is documentation/orchestration only and is not an implementation PR.

## 12. Implementation checklist
Verify repository/license/history/AGENTS and source head; import/update aggregate copy safely; define/version contract; implement both sides; test privacy/audience/duplicates/restart; update metadata/state/handoff; review/freeze/validate both heads; merge both; run deterministic parity; cleanup.

Repository/default-head/history verification is complete. Safe aggregate import is blocked by the verified license boundary, so implementation did not begin.

## 13. Acceptance criteria
Repository and contract are verified; staff/PM events obey provider semantics and privacy; no duplicate or fail-open routing; missing/incompatible provider is explicit; both PRs merge and aggregate content equals standalone default head.

These criteria cannot currently be completed because deterministic aggregate parity requires publishing the standalone source tree in public `wsg138/EnthusiaStaff`.

## 14. Test requirements
Each repository's full suites plus callback timing, cancellation/filter/ignore/spy, cross-server audiences/duplicates, disconnect/reconnect/restart, missing/version mismatch, evidence retention/redaction, and Bedrock readability tests.

No product implementation head exists in this continuation, so product build/runtime/Pi results are not claimed. The documentation-only durable-state publication must still satisfy its actually applicable exact-head repository gates.

## 15. Static-analysis requirements
All configured checks in both repositories, CodeRabbit/Codacy where available, zero valid unresolved findings.

## 16. Documentation requirements
Provider versions/contracts, staff chat/PM evidence/privacy/retention/presence, configuration/troubleshooting, component metadata, package state/handoff, PR cross-links.

## 17. Security and privacy requirements
Least-privilege audiences; no private messages in logs/artifacts; capture only authorized bounded fields; fail closed on provider uncertainty.

## 18. Migration impact
Any required schema change is a new migration in the owning repository after live boundary verification; never alter deployed history. No migration was created by this blocked continuation.

## 19. Bedrock considerations
Readable text/command controls and identity-correct evidence; representative client acceptance remains `ES-V02`.

## 20. Distributed-runtime considerations
Multiple Paper/Velocity processes, delivery ordering, duplicates, reconnect, server switching, provider restart, and stale presence.

## 21. External-provider considerations
Verified standalone repository: `wsg138/Enthusia-RoseChat`; default branch `master`; verified head at reconciliation `8fcca5420b0f54207d6efa332327b9fd18edb8d8`.

GitHub identifies it as a public fork of `BadgersMC/Enthusia-RoseChat`, whose source repository is `Rosewood-Development/RoseChat`. No repository-specific `AGENTS.md` is present in the verified source tree. The checked-in Rosewood Development `LICENSE` permits use/copy/modify/merge while expressly excluding publication and redistribution rights. `wsg138/EnthusiaStaff` is public, and `BRANCH-AND-MIRROR-POLICY.md` requires the aggregate component directory to reproduce the standalone repository with parity excluding only `.git` and aggregate-only `COMPONENT-METADATA.md`. No checked-in license exception or redistribution authorization was found in the verified repository/PR state.

Do not infer that GitHub fork availability grants permission to republish the source outside the fork network.

## 22. Completion definition
Both exact-head implementation PRs merge normally; all behavior/checks/docs pass; zero valid threads; deterministic aggregate-versus-standalone parity passes; temp branches cleaned.

## 23. Resume state
No implementation branch or implementation PR exists in either repository. The 2026-08-26 worker correctly resumed X01 because the old repository-resolution blocker changed, verified the provider, then stopped implementation at the license/import boundary. Durable blocker publication is through a Staff state-only PR.

## 24. Last completed checkpoint
Repository/default branch/source/history/license verification:
- `wsg138/Enthusia-RoseChat`, public, default `master`;
- verified head `8fcca5420b0f54207d6efa332327b9fd18edb8d8`;
- public fork chain `wsg138/Enthusia-RoseChat` → `BadgersMC/Enthusia-RoseChat` → `Rosewood-Development/RoseChat`;
- no provider `AGENTS.md` in the verified tree;
- existing Staff integration already compiles against the `dev.rosewood.rosechat.api.staff` contract, while the verified provider source does not yet provide that staff API;
- aggregate import was not attempted because the provider license does not grant publication/redistribution rights required by the public parity model.

## 25. Remaining checklist
Obtain durable, verifiable authorization/license terms that permit publishing the required RoseChat source tree in public `wsg138/EnthusiaStaff`, or make an explicitly authorized canonical architecture/policy change that removes that republication requirement without weakening source traceability. Then reconcile live heads again, create the two same-ID implementation branches/PRs, implement provider and Staff integration, run full exact-head tests/static/review/Sentinel/Pi as applicable, merge normally, prove post-merge parity under the then-authorized model, clean branches, and publish `COMPLETE`.

## 26. Known blockers
`LICENSE_REDISTRIBUTION`: the supported repository is now resolved, but its checked-in license expressly excludes publishing/(re)distributing copies. The required aggregate copy would publish the standalone source in a second public repository. No durable repository evidence currently authorizes that action.

Exact unblock: a verified license change or written authorization represented in durable repository/project authority that permits the required public aggregate copy, or an explicitly authorized canonical package/mirror-policy redesign that does not require republication and still defines a deterministic supported-source verification model. Do not treat an informal assumption or the existence of a GitHub fork as sufficient authorization.

## 27. Final evidence
Current blocker evidence:
- Staff selection/base: `37a2073b535cf32f89b2fc075699dca4e3420408`;
- standalone verified head: `8fcca5420b0f54207d6efa332327b9fd18edb8d8` on `master`;
- provider repo metadata: public fork, writable by connected account;
- upstream license: custom Rosewood Development license with publication/(re)distribution rights excluded;
- Staff repo: public;
- aggregate policy: exact standalone-tree comparison excluding only `.git` and `COMPONENT-METADATA.md`;
- implementation branches/PRs: none;
- product/runtime/Pi validation: not run/not claimed because no product implementation head was created;
- durable-state publication validation: recorded in the current X01 handoff after its exact state-only head reaches terminal checks.

## 28. Merge and synchronization record
No implementation merge or synchronization occurred. Aggregate source remains unimported by design. If the license/import blocker is resolved later, follow the normal two-PR merge/parity process; if one implementation PR merges first, status becomes `SYNC_PENDING`; `COMPLETE` only after both merge, parity passes, metadata updates, and both temporary branches are handled.
