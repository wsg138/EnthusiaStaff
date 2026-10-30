# Owner-approved ban evasion policy: guarded migration and rollback

**Decision:** On 2026-10-30, the server owner approved **permanent network ban for the first confirmed ban-evasion offense**, without 30/60/90-day escalation. See [severity decision #616](https://github.com/wsg138/EnthusiaStaff/issues/616). This decision applies to confirmed \`evasion.ban\` only; it **does not** approve automatic accusation/classification, guided-mode enablement, or other policy changes.

**Production state observed read-only on 2026-10-30:** \`plugins/EnthusiaStaff/reason-policies.yml\` has version \`2026-09-26.1\` and the legacy four-step ban-evasion ladder. The bundled candidate version \`2026-10-10.1\` has one permanent \`NETWORK_BAN\` step. Both contain exactly 85 reason IDs. A strict whole-file source comparison showed that **replacing exactly the version line and \`evasion.ban\` block yields the byte-identical bundled candidate** (48,772 bytes before, 48,523 bytes after).

## Migration contract

\`scripts/migrate_ban_evasion_policy.py\` is a standalone **Python 3.10+ standard-library** tool; it has no deployment credential, server integration, external dependencies or built-in restart. It is intentionally narrow, not a general YAML policy editor.

- Default mode is **read-only dry-run**. Prints the current/expected SHA-256 and refuses unexpected policy changes.
- Strictly requires exact legacy version and exact four-step \`evasion.ban\` block.
- Constructs the proposed result by changing **only** the legacy version line and \`evasion.ban\` block. Proceeds only if resulting bytes equal the exact trusted repository candidate YAML. No other installed customization may be overwritten.
- **Explicit apply** requires both \`--apply\` and \`--expected-sha256\` matching the installed SHA reported by dry-run. Rechecks source bytes before writing.
- Creates an **exclusive, fsynced recovery backup beside the installed file** before a temporary-file-plus-\`os.replace\` atomic update. Refuses to replace an existing recovery backup and verifies post-update exact bytes.
- Refuses symlinks, wrong path equality, unexpected version/ladder, missing reason IDs and divergent policies. Already-migrated identical content is a no-op.
- The script is **not concurrency-safe against arbitrary other writers during the last byte check and replace**. Stop or isolate the server/plugin and coordinate all operators before applying. Do not run it while policy files may be edited by an independent process.

## Staging-only example

Run these commands **only in an explicitly isolated disposable staging folder** after obtaining the candidate YAML from an exact reviewed commit:

\`\`\`bash
python3 scripts/migrate_ban_evasion_policy.py \
  --installed /isolated/staging/plugins/EnthusiaStaff/reason-policies.yml \
  --candidate paper/src/main/resources/reason-policies.yml
# Check the dry-run diff/provenance, then supply the printed source digest:
python3 scripts/migrate_ban_evasion_policy.py \
  --installed /isolated/staging/plugins/EnthusiaStaff/reason-policies.yml \
  --candidate paper/src/main/resources/reason-policies.yml \
  --apply --expected-sha256 <64-character-dry-run-digest>
\`\`\`

No production command has been run as part of this PR. The command sample is illustrative, not permission to write to production. Staging tests must confirm enforcement, approval permissions, protected staff targets, audits, current policy version in the actual running plugin, and rollback. The guided feature flag should remain OFF.

## Recovery / rollback contract

Keep the generated \`reason-policies.yml.before-ban-evasion-<digest>.bak\` outside the active loaded-policy path, with original permissions. In an operator-authorized maintenance window, verify the recovery backup's SHA against the logged pre-migration SHA, stop or isolate all writers, restore the backup **atomically** to the original installed filename, and verify the SHA again. Then use the server's supported restart procedure and verify the loaded policy has reverted. The migration script does not automate rollback or silently change running server state.

Before any production migration: verify fresh installed/candidate checksums and code branch, ensure no changes beyond the approved two substitutions, take a separate server backup, validate the isolated Java/Bedrock acceptance matrix, and obtain explicit deploy/restart authorization. **The unrelated guided calibration issue #616 remains open; approval of permanent ban evasion is not approval of the whole guided system.**
