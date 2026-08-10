# Workspace state

Last updated: 2026-08-10

Live GitHub overrides stale records. Detailed package evidence is in the registry and canonical handoffs.

## Current routing

| Field | Value |
| --- | --- |
| Completed packages | `ES-P01`, `ES-P02`, `ES-P03`, `ES-P04`, `ES-P05`, `ES-P09`, `ES-P10`, `ES-P11`, `ES-X05`, `ES-R01`, `ES-R02`, `ES-V01` |
| Parked selected package | `ES-P07 — Inventory and Ender editing runtime completion`; `BLOCKED` / `PARKED_BLOCKED`; implementation PR #112; branch `package/es-p07-inventory-runtime`; frozen reviewed head `b34aade6ae79c7aaada0ada3c87970f937b6db6a`. |
| ES-P07 completed proof | Java 21 full tests/MariaDB Testcontainers, Wiki, runtime-JAR/provider-leak checks, aggregate coverage, Codacy and CodeRabbit/review closure all passed on the frozen head. |
| ES-P07 missing proof | Canonical private Pi run `31426646043` / job `93579820065` is queued without runner assignment; exact-head Sentinel job 75 is queued at 120 MB available memory (<700 MB) and 82.3 C (>=80 C). No private Paper/MariaDB/Flyway cycle and no `PAPER_RESTART_OK` exist. |
| ES-P07 unblock | Resume before new package work only when runner availability materially changes and Sentinel memory/temperature materially clears the resource gate. Final merge still requires both exact-head runtime gates to pass. |
| Other ready package | `ES-P06 — Discord notification delivery completion` remains `READY` at priority 60. If ES-P07 stays parked after reconciliation, a new worker may select ES-P06. |
| Dependency-blocked | `ES-P08` remains blocked by ES-P07; destructive providers and later validation/cutover packages remain parked on their documented dependencies. |
| RoseChat | `ES-X01` remains `BLOCKED` / `PARKED_BLOCKED` because the supported integration repository/source contract is unresolved. |
| Migration boundary | V18 remains current and immutable; ES-P07 adds no migration. |
| Production boundary | Issue #43 remains deferred and LiteBans authoritative; no production deployment/data/shadow/cutover/source rewrite occurred. |
| Exact next action | Reconcile live runner/Sentinel conditions. Resume ES-P07 only if its unblock condition materially changed; otherwise keep it parked and route by normal package priority. |

## Stop boundary

This ES-P07 worker stops `BLOCKED` with PR #112 and its implementation branch preserved. It must not activate ES-P06, ES-P08, ES-X01, or another package.
