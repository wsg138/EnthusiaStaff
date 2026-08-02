# Feature Completion Status

This page is the main directory for understanding **what EnthusiaStaff does, how
complete each part is, where its code lives, and what still needs work**.

> **Overall verdict: NOT READY for production authority.** LiteBans and the
> existing production staff stack remain authoritative.

## How the percentages work

Percentages are rounded planning estimates based on implemented behavior,
automated tests, available staging evidence and unfinished requirements. They are
not generated automatically and are not a release score.

| Mark | Meaning |
| --- | --- |
| ✅ **100%** | The intended repository feature is implemented and tested. |
| 🟢 **80–99%** | Core behavior exists; focused integration or staging remains. |
| 🟡 **50–79%** | Useful implementation exists, but important behavior is unfinished. |
| 🟠 **20–49%** | Early or partial implementation; substantial work remains. |
| 🔴 **0–19%** | Not started, contract-only or externally blocked. |

A high feature percentage does not by itself authorize production use. The final
release also requires combined provider, topology, migration, security, load,
recovery and operational evidence.

## Open a feature group

Each group has its own detailed page. Open one to see:

- specific feature categories and percentages;
- a plain-language explanation of what each category does;
- commands or runtime ownership;
- direct links to important source files and directories;
- related staff/operator/developer documentation; and
- the remaining work for that category.

| Feature group | Estimated completion | What belongs there | Largest unfinished areas |
| --- | ---: | --- | --- |
| [[Core Platform and Infrastructure]] | **About 72%** | Runtime jars, architecture, Paper/Velocity lifecycle, MariaDB, protocol, safe-write controls, modes, configuration, identity and CI. | Complete operational modes, modular configuration/reload, Bedrock aliases, production-volume and multi-backend failure testing. |
| [[Moderation, Punishments, and Reports]] | **About 56%** | Cases, sanctions, punishment GUI/drafts, rank approval, escalation, history, appeals, reports, evidence and automod. | History/overturns, request notifications, complete escalation policy, report GUI, RoseChat PM evidence and automod integration. |
| [[Staff Tools, Investigations, and Player-State Safety]] | **About 44%** | Staff mode, freeze, vanish, inventory/Ender access, confiscation, economy actions, alts, inspector, testers and fake systems. | Crash/reconnect recovery, integration hiding, concurrent/offline inventory safety, provider-backed economy, alt lifecycle and fake systems. |
| [[Integrations, Migration, and Release Readiness]] | **About 36%** | Discord, website bridge/site, provider APIs, LiteBans migration, shadow/cutover, client/runtime acceptance and release evidence. | Provider reconstruction, private site, real-data migration rehearsal, 168-hour shadow, full topology/Bedrock/Folia/load/process-kill acceptance. |

## Find information by question

### I want to know what a feature does

Open the matching feature-group page above. Every group page explains the purpose
and user-facing behavior before listing implementation files.

### I want to find the source code

Start with the matching feature-group page for the most important files, then use
[[Developer Code Guide]] for the complete repository map and end-to-end feature
traces.

### I want exact proof or blockers

Use the
[requirements matrix](https://github.com/wsg138/EnthusiaStaff/blob/main/reports/REQUIREMENTS-MATRIX.md).
It records exact status, source files, configuration, tests, remaining work and
blockers. The Wiki percentages are the readable planning view of that evidence.

### I want staff instructions

- [[Staff Quick Start|Moderator-Quick-Start]]
- [[Punishment System]]
- [[Reports and Evidence]]
- [[Staff Mode, Vanish, and Freeze|Staff-Mode-Vanish-and-Freeze]]
- [[Inventory and Confiscation Safety]]
- [[Alt Investigations]]

### I want administrator or release instructions

- [[Commands and Permissions]]
- [[Roles and Permissions|Rank-Authority]]
- [[Configuration]]
- [[Integrations]]
- [[Installation]]
- [[Recovery and Troubleshooting]]
- [[LiteBans Migration]]
- [[Shadow Mode and Cutover]]

### I want to know what should be developed next

Use [[Remaining Development Map|Development-Blueprint]]. It groups the unfinished
work into the same four feature areas without repeating the full completion tables.

## Current development boundaries

Two large workstreams are active outside current `main`:

- [PR #27](https://github.com/wsg138/EnthusiaStaff/pull/27) — durable punishment-request notifications, recovery, modular alert configuration/reload and related staff/freeze lifecycle work;
- [PR #37](https://github.com/wsg138/EnthusiaStaff/pull/37) — LiteBans cutover coordination and activation safety.

Branch work is not counted as merged behavior merely because it has tests. Group
pages may identify an active branch where it materially explains the remaining
work.

## Current command gaps

These required top-level commands are not currently registered on `main`:

```text
/history
/fakebase
```

`/alts` and `/alt` are registered on Velocity, but the complete alt lifecycle,
GUI, confidence, exception, inheritance and staging work remains unfinished.

## External blockers

- The supported RoseChat provider repository/API required for the complete staff,
  private-message evidence and pre-broadcast moderation bridge is unavailable.
- Polar does not expose the supported violation-event contract required for
  automatic enforcement.
- Complete provider branches, the private site, production-like LiteBans data,
  real Bedrock/Folia clients and the full multi-server failure environment are
  unavailable or unfinished.
- The mandatory real-data 168-hour LiteBans shadow observation has not run.

## Updating completion information

When implementation changes:

1. Update the exact row in `reports/REQUIREMENTS-MATRIX.md` first.
2. Update the detailed feature-group page.
3. Adjust the group percentage here only when meaningful requirement groups move.
4. Update [[Remaining Development Map|Development-Blueprint]] when the unfinished
   development order changes.
5. Keep commands in [[Commands and Permissions]], source traces in
   [[Developer Code Guide]], and validation procedure in [[Build and Testing]].
6. Run `python scripts/wiki/validate_wiki.py` before publication.

## Punishment history lifecycle update

The history/sanction-change requirement group is implemented in PR #46 pending exact-head validation: database-bounded `/history`, complete `/case` detail, exact reduce/end/revoke/overturn, request/appeal linkage, append-only audit and concurrency tests. Production cutover and LiteBans authority are unchanged.
