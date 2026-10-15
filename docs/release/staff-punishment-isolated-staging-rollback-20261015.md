# Enthusia Staff — isolated staging, acceptance and rollback runbook
**Worker D / 2026-10-15. Status: NOT AUTHORIZED FOR PRODUCTION.** Companion: [independent release-gate audit](../qa/staff-punishment-independent-release-gate-20261015.md).

## Safety boundaries before any step

This runbook is a **procedure**, not authorization to invoke Pi staging, restart the live SMP, push a JAR, update Pterodactyl/SFTP, write production MariaDB, edit live YAML or send real Discord staff messages. The isolated staging server **must not** share production host, plugin data directory, database/schema, DB credential, Discord guild/channel/bot token, SFTP profile, Paper events or outbox queues. Never mirror real player messages, case data, receipts or personal information into tests. Use synthetic identities and sanitized logs.

The baseline repo source in the independent audit was `main=a565616c58816815d7f4ee72bb9160d71f331b54`. The unmerged guided candidate is **#597 + stacked #612 at `05c2e094464f40eb0e98291e0e09e07b838a1b0c`**. No individual stacked PR should be merged as if it were independent. Re-read current SHA, PR graph, CI, findings and policy before testing or merging.

**Embedded release controls:** exact SHA + reproducible JAR hash and size, source/policy version, feature flags, runtime/dependency versions, sanitized tests, owner acceptance, backup IDs, rollback IDs and named approver. A green CI badge alone is insufficient.

## A. Isolated build and evidence (Windows or Linux)

1. In a fresh, dedicated checkout, pin the exact intended commit; do not use a mutable default branch or a staging-only JAR from a previous commit:
   ```bash
   git clone https://github.com/wsg138/EnthusiaStaff.git enthusiastaff-qa
   cd enthusiastaff-qa
   git fetch origin 05c2e094464f40eb0e98291e0e09e07b838a1b0c
   git checkout --detach 05c2e094464f40eb0e98291e0e09e07b838a1b0c
   test "$(git rev-parse HEAD)" = 05c2e094464f40eb0e98291e0e09e07b838a1b0c
   ```
   If branch is updated, explicitly replace that pinned SHA with the reviewed candidate and **invalidate older checksums/results**. Run from a clean checkout. Use a JDK **25** toolchain (Paper module is compiled with `options.release=25`; plugin.yml advertises `api-version: '1.21'`). Match actual test server software versions.
2. On isolated Linux:
   ```bash
   chmod +x gradlew
   ./gradlew --no-daemon --no-build-cache --no-configuration-cache --rerun-tasks \
     :domain:test :paper:test :staff-bot:test :velocity:test :paper:shadowJar \
     --console=plain
   git diff --check
   test -f paper/build/libs/EnthusiaStaff-Paper-0.1.0-SNAPSHOT.jar
   wc -c paper/build/libs/EnthusiaStaff-Paper-0.1.0-SNAPSHOT.jar
   sha256sum paper/build/libs/EnthusiaStaff-Paper-0.1.0-SNAPSHOT.jar
   unzip -p paper/build/libs/EnthusiaStaff-Paper-0.1.0-SNAPSHOT.jar config.yml | sed -n '1,14p'
   ```
   Require embedded `guided-punishment.enabled: false`. The independent Windows rerun of #612 succeeded: 2,353 JUnit cases, 15 skipped, no failures/errors, 29 Gradle tasks executed. It produced **11,518,861 bytes / SHA-256 `74524bb2fca1e16670c2ba61d8ac947dd3a365e242fb0d2ccd03e5c8729faf41`**; archive embedded config also independently verified OFF. This checksum is **Windows-compiled local evidence**, not a promised byte-identical Linux or signed release checksum.
3. CI: use exact-head Linux [coverage run](https://github.com/wsg138/EnthusiaStaff/actions/runs/38682510585) as complementary evidence, not an acceptance substitute. It ran an explicitly selected eight-class MariaDB suite. All Windows POSIX and real-FastAPI skips remain open until an isolated Linux/external-API matrix is explicitly recorded. Exercise the relevant integration suite on a disposable MariaDB service **with its documented isolation/environment controls**; do not point integration tests at production.
4. Record: git SHA, command line, Java/Gradle version, OS, PASS/FAIL/SKIP counts with skipped names, run/job URLs, build timestamp, JAR bytes/SHA-256, contained `config.yml`, current Codacy/CodeRabbit findings, test database ID, and evidence folder checksum. If any differs from the approval record, stop.

## B. Take a pre-staging snapshot and isolate dependencies

1. For the **new staging environment** record OS/JDK, Paper/Leaf/Folia build and scheduler, server.properties, Geyser/Floodgate version, Java and Bedrock client versions, ViaVersion/ProtocolLib, LuckPerms, chat plugins, RoseChat/DiscordSRV/InteractiveChat, Velocity where relevant and StaffBot revision. The Paper plugin declares many softdepends; missing plugins may change acceptance semantics. Do not assume plugin presence equals compatibility.
2. Capture checksum-protected **staging** backups of: previous staging Paper JAR; `plugins/EnthusiaStaff/` YAML and plugin data; staging MariaDB schema + data dump; migration history; bot config/outbox state; dependency JAR manifest and test screenshots/logs. Include timestamps, clean restore location and a copy of permissions/ownership. Sanitize secrets and user data. Never silently reset an existing database.
3. Make a separate staging config copy. Preserve its current `reason-policies.yml` and `policy-v2.yml` as the actual policy authority and diff each against source. **Known mismatch to investigate:** historically live policy version `2026-09-26.1`, repository default `2026-10-10.1`. Never copy the repo default over live YAML. Record exact active policy version and an owner-approved migration/calibration decision before any attempted production rollout.
4. Provision isolated test permissions/LuckPerms data for helper, mod, admin, owner and regular player; distinct online/offline players and Bedrock accounts; independent MariaDB credentials; no production external API endpoint or Discord bot/guild. For StaffBot or proof experiments, substitute mock endpoints and fake credentials. Keep unregistered #601/#606/#611/#615 adapters inactive.
5. Confirm staging `operational-mode` and any activation preconditions using the documented local bootstrap/cutover procedure. Do not bypass BOOTSTRAP/SHADOW_MIGRATION or fabricate a cutover record to force ACTIVE. If an ACTIVE-mode sanction scenario cannot safely be reached using isolated, documented setup, mark it **BLOCKED**, not passed.

## C. Runtime acceptance order and pass/fail recordings

Run each check with exact player roles and timestamps. For every case record **Observed**, **Expected**, status **PASS/FAIL/BLOCKED**, server type/version, Java or Bedrock client, actor/target scope, policy SHA/version, screenshot/video path and relevant sanitized logs. Require zero unsanctioned live writes, staff-data leaks, cross-thread inventory mutations, or unauthorized actions.

| ID | Isolated test | Pass criterion |
| --- | --- | --- |
| S01 | Default OFF restart, open `/punish <player>`, click six primary categories plus Other fallback; `/ban`, `/mute`, `/warn`, `/kick`, `/ipban` | Existing reason browser/search and filtered expert flow remain, no guided cutover; correct permitted reasons only |
| S02 | Toggle ON **only in copied staging config**, restart, open `/punish <player>`; Java and Geyser/Bedrock screenshots for each screen | Category -> medium -> conduct -> follow-up -> result; 2–7 choices, sensible text wrapping, no lost inventory, accessible click slots |
| S03 | Low-volume chat spam: few/no warning, few/stopped, sustained/ignored, unknown warning; repeat with earlier recent/old unrelated/decayed cases | Reason and impact consistent with owner-approved expected outcomes, none silently aggravate unknown; **unknown no-review semantics need policy-owner signature** |
| S04 | Conduct mediums: public chat/DM vs sign/book/voice/profile; spam placement vs mute; harassment sensitive content; slurs/threats/privacy/evidence uncertainty | Correct reason or fail-closed manual review; no unsupported sign/book punishment or unverified allegation treated as proven |
| S05 | Six category taxonomy and **84 staff-selectable reasons** vs 85 configured including automated-only Polar template | Every allowed staff reason has a reachable classified route or explicitly documented review fallback; wrong family not silently selected |
| S06 | Target changes rank/online state while GUI open; helper vs mod/admin/owner; on-duty/off-duty; current permission revocation; peers/superiors; self target | Fresh rank/current-policy/staff-mode gates reject unauthorized case preparation and confirmation; no downgrade bypass |
| S07 | History weight/decay, sanction ladders, multiple history steps, attempted severity skip across ban/permanent/mixed rung | Only approved warnings/temporary mutes calculated automatically; restricted intermediate rung forces manual review |
| S08 | Shorter/default/stronger duration, 90–100 second artificial policy bound, rationale note, approval request, policy version change mid-draft | Bounds inclusive, no rounding under minimum; changed choice requires staff rationale; policy/rank rechecks apply |
| S09 | Cancel/back/close before preview, after preview, at review and after GUI reopen; restart/resume and policy update | No **case/sanction** committed before explicit authorized confirm. Saved draft writes, if any, are measured and documented; stale draft/decision cannot be submitted |
| S10 | `/report <player>`, full-description view, draft cancel, offline/rejoin, private evidence access and permissions (coordinate with draft #618) | No unintended report submission or disclosure; acceptance records distinguish merged vs unmerged behavior |
| S11 | Spectate/follow a **moving target continuously for several minutes**, multiple worlds/server scope; inspect inventory/staff menus; Staff Mode and Folia schedulers | No crashes, stale target pointers, unsafe cross-thread access or abandoned inventory; screenshots at start/mid/end |
| S12 | Paper/Leaf/Folia test matrix (separate isolated instances) and Java/Geyser/Bedrock sessions; search chat cancel/timeout with chat plugins | Same authorized workflow outcome, no accidental public/private search-text echo, no GUI duplication, no async violations |
| S13 | Fake StaffBot case event outbox with two destinations; JDA private guild/channel role gate; offline staff rejoin; replay/restart and stale version | Isolated authenticated projections, per-destination ACK, no role ping/raw data leak; never mark production when sink/listener is unregistered |
| S14 | Fake canonical claim/resolve, replay/idempotency, revocation, wrong actor/scope, short-lived Paper proof; include API stub and genuine role-gate stub denials | Authoritative revision remains single-source; no optimistic local close, no always-allow proof gate, no live key provision |
| S15 | Discord/Minecraft retraction using synthetic posts/receipts: exact-source deletion eligibility, missing/tampered receipt, wrong publication, duplicate/expired operation | Wrong/uncertain target yields **no deletion**; only isolated exact-evidence scenarios qualify and remain idempotent |
| S16 | Restart, crash recovery, failed network, simulated permission/timeouts and stale config; revert feature flag OFF | No lost/corrupt outbox or duplicate sanctions; old browser restored without destroying test data |

**Caution:** S13–S15 depend on cross-repository API/StaffBot development and may remain BLOCKED even if S01–S12 pass. A fake test gateway is not a real signed authority or staff-channel acceptance. Similarly, internal Unit PASS does not count as a Paper/Bedrock screenshot.

## D. Owner acceptance and staged release decision

The lead collects the above evidence and a reviewed list of *exact* included commits/PRs, rollback test logs, Cordacy/Codacy issue resolution, CodeRabbit/human reviews, scope-sensitive permission matrix, proposed policy thresholds and calibrated example punishments. The owner must explicitly approve the fixed scoring and unknown-warning behavior; no automatic policy deployment. Resolve #612's two Codacy medium findings; investigate #615 protocol matrix failure for any release containing that branch. No activation of case commands or retraction without separately authorized current-role proofs and transport verification.

If every required test passes, propose a **new, reviewed release candidate** from the intended source graph, pin its actual artifact digest and complete independent staging sign-off. Before any future production action, require a separate change window and named operator/approver; this document does not grant that approval.

Recommended incremental order **after** approval: baseline behavior unchanged (guided flag OFF) -> staged owner/small-staff acceptance ON in isolated instance -> narrow production canary only if a separate deployment is authorized and rollback rehearsed -> widen only after recorded clean metrics. Do not send live Discord staff alerts or enable proof/deletion adapters as a side effect of GUI release.

## E. Backup, rollback and stop conditions

Stop a test immediately on: unauthorized sanction or case write, wrong player/role, wrong-channel Discord send/delete, Bedrock/Java duplicate click causing double execution, missing audit event, policy version surprise, MariaDB write outside staging, receiver claiming an unverified proof, corrupted rollback artifact, or non-allowlisted plugin mutation.

**Staging rollback rehearsal (with a separate isolated environment, not live):**

1. Record the failure time, source SHA, policy version, JAR SHA-256, affected actors/cases, database schema migration head, plugin dependencies, and last accepted event/outbox revision. Quiesce the isolated Paper/Velocity/StaffBot services and any fake event consumers; stop writing before restoration.
2. Turn the copied staging `guided-punishment.enabled` OFF, **restart** to restore old GUI routing; verify this eliminates the guided entrypoint. This is a **feature rollback only** — it does *not* revert already persisted drafts/cases or schema mutations.
3. Verify backup checksums and restore the exact **previous staging** plugin binaries/YAML/data with ownership/permission metadata. Check active policy/config hashes before startup. Reconcile pending outbox/idempotency receipts rather than blindly resending events.
4. If a staging migration/schema/data change requires database rollback, restore the **matched pre-test staging DB snapshot to a new isolated database** and validate it before reconnecting; do not assume a destructive reverse migration is safe. Keep the failed DB snapshot for investigation. Never import this into production.
5. Restart isolated dependencies in dependency-safe order, confirm existing reason browser, expected staff permissions and read-only history; run no-op verification and one synthetic request. Confirm no surprise Discord post, Minecraft punishment, database mutation or retraction. Record the recovered JAR/config/DB digests, results and sign-off.
6. If rollback cannot restore all changed components and expected event revisions, remain OFF/BLOCKED and escalate. Never declare recovery solely from successful restart.

**Required per-release manifest template:**
```text
timestamp_utc:
owner_approver:
reviewed_source_sha:
included_prs_and_exact_heads:
jar_path / bytes / sha256:
paper_leaf_folia_versions:
java_version:
geyser_floodgate_viaversion_protocollib_versions:
staffbot_api_revisions:
config_yaml_sha256:
policy_yaml_sha256 / version:
feature_flags:
staging_db_id / schema_migration_head:
backup_ids_and_hashes:
ci_run_ids / junit_pass_skip_fail:
java_screenshots / bedrock_screenshots:
cross_platform_synthetic_receipt_proof:
open_findings_and_exceptions:
staging_owner_acceptance:
rollback_rehearsal_evidence:
production_change_approval_id: NOT_GRANTED
```

## F. Evidence verdicts

Use only these terms: **IMPLEMENTED**, **UNIT PASS**, **HOSTED SELECTED INTEGRATION PASS**, **STAGING RUNTIME PASS**, **IN-GAME OWNER ACCEPTED**, **PRODUCTION DEPLOYED**. State **BLOCKED**, **SKIPPED** and **NOT TESTED** separately. The independent 2026-10-15 audit reached source inspection, a 29-task Windows unit/build pass and selected exact-head hosted Linux/MariaDB PASS, **not** full isolated in-game sign-off and **not** production readiness.
