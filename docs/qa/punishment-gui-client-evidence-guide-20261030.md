# Punishment GUI — staged Java / Bedrock evidence acceptance

**State:** NO-GO; no client sessions have been run or claimed as passing by this document. This supplements, but does not replace, the [existing A01–A20 client checklist](../implementation-plans/staff-punishment-gui-ux-acceptance-20261015.md).

The release candidate must be installed only in an explicitly authorized **disposable Paper staging environment**. This evidence tool does not connect to any Minecraft server, download software, apply sanctions or change the live SMP.

## Available test server preflight (read-only, 2026-10-30)

A read-only inspection of the existing `bloom-test1` host found a **shared TEST environment**, not a dedicated punishment-test sandbox. The plugin folder has hundreds of entries, other projects' unmerged TEST JARs, RoseChat, Floodgate, LiteBans and older EnthusiaStaff test artifacts. The server root contains Leaf build archives, rather than proof of a clean exact-version Paper staging runtime. The existing `README-TEST.md` describes a separate Market acceptance procedure and explicitly requires separate authorization for downtime and installation.

**Conclusion: DO NOT automatically install the punishment candidate on `bloom-test1` or claim that its files satisfy the isolated-staging gate.** Neither SFTP directory listings nor a named TEST host prove that a server is isolated from production permissions, shared network endpoints, database writers, Discord channels, punishment backends or actual players. An authorized isolated, disposable server must be identified before any staged JAR activation.

### Minimum staging isolation checks (operator review)

- Establish a new dedicated Paper instance or explicitly reserve an existing disposable instance for this test; record the exact host, server build, candidate Git commit and JAR digest **without exposing credentials publicly**.
- Confirm test accounts/permissions: ordinary staff, directly sanction-authorized staff, rank requiring approval, authorized approver, unauthorized account, expendable punishment target. No real player accounts should receive a test sanction.
- Verify separately isolated permissions, database/queue, audits and sanction targets. Disable shared production proxy/StaffBot integrations and any outbound webhooks or notifications that could affect live moderation.
- Verify Geyser/Floodgate routing with real Java and Bedrock clients; an installed Floodgate JAR alone does not prove a functioning Bedrock connection.
- Capture a clean backup and rollback plan before enabling guided mode **only on staging**; repeat the relevant OFF baseline checks. Do not change the live SMP configuration or installed custom reason policy.
- Run the actual A01–A20 checks and record FAIL/BLOCKED honestly. Confirm staging can be destroyed without affecting any other developer's shared test data.

## Collection requirements

Record a completed result for every acceptance row and applicable client combination: **20 Java/Bedrock scenarios, 39 required client observations** (A19 is Bedrock-only). Give each row a distinct PASS, FAIL, or BLOCKED assessment.

A PASS must have: the actual isolated staging candidate SHA and built JAR hash, exact Paper version, Java and Bedrock versions, exact Geyser/Floodgate versions, a timestamp with timezone, an identified reviewer, a useful observation, an explicit real-client attestation, and a matching checksum for locally stored screenshot/video evidence. For a video of multiple cases, reuse the same media file with clear per-case timestamps in the notes. Do not claim PASS from mocked Java Inventory objects or fabricated Bedrock screenshots. A Java automated client is not a substitute for the mandated real Bedrock touch interaction.

For missing clients, unconfigured permissions or a test not yet performed, enter **BLOCKED** with a reason. Actual unexpected behavior should be **FAIL**, not BLOCKED. Only an isolated, disposable test target may receive sanctions; repeat permissions, reviewer, target protection, approval, audit, and the guided OFF/ON controls.

## Generate the complete BLOCKED baseline

To avoid manually assembling 39 scenario records, generate a manifest that is intentionally **all BLOCKED**:

```bash
python3 scripts/qa/create_punishment_gui_client_manifest.py /isolated/staging/evidence/manifest.json
```

The directory must already exist. This is a create-only operation: an existing manifest is never overwritten. The generator leaves the exact candidate SHA, artifact information, server/client versions and isolation attestation blank or unapproved. Populate those only from the real isolated test session; then update each case to PASS or FAIL with the appropriate real-client evidence. A newly generated manifest necessarily fails admission, as intended.

## JSON evidence format

Save the manifest next to the candidate JAR and screenshot/video captures in an isolated staging evidence folder. Populate **all 39 applicable records**, not just this one example:

\`\`\`json
{
  "candidate_commit": "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
  "artifact": {
    "path": "candidate.jar",
    "sha256": "replace-with-64-character-lowercase-sha256"
  },
  "server": {
    "isolated_staging": true,
    "production": false,
    "paper_version": "exact-staging-paper-version"
  },
  "client_versions": {
    "java": "exact-Java-client-version",
    "bedrock": "exact-Bedrock-client-version",
    "geyser": "exact-Geyser-version",
    "floodgate": "exact-Floodgate-version"
  },
  "cases": [
    {
      "id": "A01",
      "client": "java",
      "status": "BLOCKED",
      "notes": "Java client staging check has not been performed"
    }
  ]
}
\`\`\`

That example is **intentionally incomplete**. It will not pass the validator. A real PASS row additionally requires \`actual_client_attested: true\`, \`reviewer\`, \`tested_at\` with timezone, \`media\` (relative local path), \`media_sha256\`, and detailed \`notes\`.

Run from the repository root:

\`\`\`bash
python3 scripts/qa/punishment_gui_client_evidence_gate.py /isolated/staging/evidence/manifest.json
\`\`\`

Exit codes: **0** means all 39 records are complete with integrity-checked media and attestations (but human gameplay review is still required); **1** means recorded FAIL/BLOCKED rows remain; **2** means manifest data is missing, unsafe or inconsistent. The validator never changes the manifest, server or plugin.

## Strict boundaries

- **No automatic authenticity claim:** Even checksum-matched video files and reviewer declarations can be inaccurate. A separate human must examine the client footage and server logs for each claim.
- **No production substitution:** The string \`isolated_staging: true\` is an attestation, not a technical isolation guarantee. Operators must independently verify IP, plugin set, permissions, copied database, audit destinations, and that no live service can be impacted.
- **No release from evidence alone:** The permanent confirmed-ban-evasion rule is owner-approved, but the installed production policy remains a separate migration gate. Other guided sanction calibration under #616, Folia admission (if targeted), rollback proof, and independent integration review remain required.
- **Media safety:** The validator rejects absolute/traversing paths, symlinks, missing media and SHA mismatches. Store captures outside the repository if they contain usernames, chat content, server addresses, or other private information.

All tests for this evidence gate are **synthetic fixtures** and must never be marked as A01–A20 runtime acceptance.
