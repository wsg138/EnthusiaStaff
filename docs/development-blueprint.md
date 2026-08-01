# EnthusiaStaff remaining development blueprint

This document groups unfinished work into four development sections. It does not
repeat feature percentages, code paths, validation commands or operator cutover
steps.

- Feature percentages: `docs/wiki/pages/Implementation-Status.md`
- Exact evidence and blockers: `reports/REQUIREMENTS-MATRIX.md`
- Code ownership: `docs/wiki/pages/Developer-Code-Guide.md`
- Validation: `docs/wiki/pages/Build-and-Testing.md`
- Migration operations: LiteBans migration and shadow/cutover Wiki pages

## Four development groups

1. **Core platform and infrastructure**
2. **Moderation, punishments and reports**
3. **Staff tools, investigations and player-state safety**
4. **Integrations, migration and release readiness**

<details>
<summary><strong>1. Core platform and infrastructure</strong></summary>

- Finish remaining coordinator and JDBC responsibility splits.
- Complete safe degraded startup, partial-startup cleanup and bounded shutdown.
- Add production-volume, process-kill and multi-server MariaDB testing.
- Stage real Paper–Velocity certificates, rotation, multiple backends,
  no-player transport, backpressure and long outages.
- Complete all operational modes and dependency-specific feature gates.
- Build the modular configuration tree and complete atomic reload.
- Finish previous-name, Bedrock alias, fuzzy lookup and in-memory completion.
- Complete operator-readable health, recovery and degraded-feature diagnostics.
- Add meaningful coverage enforcement, mutation tests, load tests and broader
  runtime acceptance.

**Done when:** every platform responsibility has one owner, every runtime mode has
explicit behavior and interruption cannot corrupt durable state.

</details>

<details>
<summary><strong>2. Moderation, punishments and reports</strong></summary>

- Reconcile PR #27 without losing or duplicating request lifecycle behavior.
- Add `/history` and complete case/sanction/audit timelines.
- Prove combined sanctions and exact reduction, ending, revocation and removal.
- Add durable overturn requests and appeal-linked decisions.
- Complete request lifecycle notifications for online and offline staff.
- Finish escalation families, decay, recency, aliases and policy compatibility.
- Complete punishment GUI/configuration for Java and Bedrock.
- Build the reports queue/detail GUI and complete cooldown/merge behavior.
- Implement supported RoseChat public/private-message evidence capture.
- Complete evidence privacy, retention, strict pre-broadcast automod and client
  evidence providers.

**Done when:** every moderation change is authority-safe, audited, idempotent,
restart-safe and cannot alter an unrelated sanction or report.

</details>

<details>
<summary><strong>3. Staff tools, investigations and player-state safety</strong></summary>

- Complete staff-mode snapshot, exact restore, rank enforcement and crash resume.
- Complete freeze restrictions, staff-only communication, reconnect and expiry.
- Finish vanish tab, entity, command, chat, voice, effect, container and public-API
  hiding with real Java/Bedrock/Folia tests.
- Complete online/offline inventory editing, concurrent viewers, nested containers,
  queued patches, recovery and quarantine.
- Complete item and economy confiscation/restoration without duplicate or loss.
- Finish alt confidence aging, exceptions, inheritance, encryption, GUI and alerts.
- Finish the player inspector, staff hotbar and tools menu.
- Implement safe journaled cheat testers, fake entity and virtual fake base.
- Add `/fakebase`.

**Done when:** crashes, disconnects, stale viewers and server switches cannot leak
staff state, overwrite newer player data, lose assets or expose private evidence.

</details>

<details>
<summary><strong>4. Integrations, migration and release readiness</strong></summary>

- Complete Discord routing, sanitization, circuit status and manual recovery.
- Build the private punishment/appeal website with authentication, CSRF, rate
  limits, role controls and safe media.
- Reconstruct Currency, Commend, AutoClicker, RoseChat and Market providers.
- Stage provider classloaders and independent degraded behavior.
- Complete PR #37 cutover coordination, writer fencing and emergency freeze.
- Prove migration interruption, resume, reconciliation, rollback and real-data
  rehearsal.
- Run full Velocity/HUB/SMP, Java, Bedrock/Geyser and Folia acceptance.
- Run load, saturation and process-kill tests.
- Produce one cross-repository release manifest with exact revisions and hashes.
- Complete seven daily comparisons spanning at least 168 hours.
- Rehearse rollback before any production authority change.

**Done when:** one reproducible release manifest passes every applicable acceptance
group and the complete shadow record contains no unexplained mismatch.

</details>

## Current development order

1. Finish PR #37 and reconcile PR #27.
2. Complete core mode, configuration and recovery gaps needed by other features.
3. Complete moderation history, decisions, reports and notifications.
4. Complete staff-state, inventory, asset and investigation safety.
5. Reconstruct providers, Discord and the private website.
6. Run migration, topology, platform, load and failure acceptance.
7. Complete the 168-hour shadow evidence and rollback rehearsal.

Correctness, security and data-integrity defects may interrupt this order.

## Documentation ownership

| Information | Primary source |
| --- | --- |
| Finished behavior | `ENTHUSIASTAFF-GOALS.md` |
| Exact evidence and blockers | `reports/REQUIREMENTS-MATRIX.md` |
| Feature percentages | `docs/wiki/pages/Implementation-Status.md` |
| Remaining development groups | This document |
| Code paths and ownership | `docs/wiki/pages/Developer-Code-Guide.md` |
| Validation procedure | `docs/wiki/pages/Build-and-Testing.md` |
| Migration operations | LiteBans migration and shadow/cutover Wiki pages |
