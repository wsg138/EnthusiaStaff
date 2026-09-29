# Codex execution — Staff Bot staging

Repository: `wsg138/EnthusiaStaff`
Deployment branch: `deploy/temp-network-sftp-staging`
Product PR: `#256`

For the existing Bloom split shown by the owner, the authoritative execution handoff is now:

`deploy/temp-network/CODEX-STAFF-BOT-SFTP-NOW.md`

Read that file first and follow it exactly. It pins the exact PR head, artifact, JAR SHA-256, SFTP helper, staging Discord identity, HUB authority setup, APP FLAGS, smoke-test sequence, rollback rules, and stop conditions.

## Current intended panel shape

Java 21 remains correct.

Keep:

```text
JAR FILE=EnthusiaStaff-StaffBot.jar
FLAGS=-Dterminal.jline=false -Dterminal.ansi=true
```

After the exact-head candidate is uploaded and all required PR #256 gates are green, replace the old D16 preview APP FLAGS with:

```text
--token-file=t --moderation-config-file=m
```

For the one-shot smoke test use:

```text
--token-file=t --moderation-config-file=m --smoke-test
```

Do not keep `--staging-ui-preview`, Cloudflare tunnel flags, preview URL flags, or any old D16 preview startup options.

## Non-negotiable safety

- Staging Discord application: `1541279616881397772`
- Allowed guild: `1410303324745371709`
- Required staging channel: `1541286004298752091`
- `discord-enforcement.enabled=false`
- Staff MariaDB must be healthy.
- HUB private staff-rank authority must be reachable on the private route.
- Never print, commit, screenshot, or return the Discord token, DB password, authority secret, component secret, or SFTP password.
- Do not expose ports 8765, 8766, or 8771 publicly.
- Do not use the old September 14 JAR after the exact-head PR #256 artifact is approved.

Return only the sanitized report format defined in `CODEX-STAFF-BOT-SFTP-NOW.md`.
