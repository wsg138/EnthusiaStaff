# Codex — finish Staff Bot on the existing Bloom split

Use this with the existing authorized Bloom/Pterodactyl + SFTP access. Never print or return secret values.

## Product candidate

Staff Bot file-backed runtime PR: `wsg138/EnthusiaStaff#256`

Exact candidate head:

`c1f735818b24479afb4bd8d83535e8c8535d85e9`

PR artifact workflow run: `36578965033`
Artifact ID: `11039440567`
Artifact name: `staff-bot-pr-256-c1f735818b24479afb4bd8d83535e8c8535d85e9`

Candidate JAR:

`EnthusiaStaff-StaffBot.jar`

Candidate JAR SHA-256:

`183ab2f0b10de57ae181b90f244a731ac054538185ed0148649e2fb5ac7329e4`

Do not substitute the older September 14 JAR currently present on the split.

Before deployment, require PR #256 exact-head Coverage/full build to be terminal green and require no new valid hosted static-analysis finding. Staff Bot PR Artifact, Staff Bot Configuration Cache, Sentinel Restart Artifact and CodeRabbit are already green on this exact head at handoff creation.

## Existing Bloom split

The existing split is the old D16 preview runtime. It currently contains:

- `EnthusiaStaff-StaffBot.jar`
- `t` — private staging Discord token file
- `m` — private moderation runtime properties
- old `cloudflared` / `cloudflared-token.txt` preview files

Do not delete the old tunnel files during this task. They become unused rollback debris after APP FLAGS are changed.

Java 21 is correct.

Keep:

```text
JAR FILE=EnthusiaStaff-StaffBot.jar
FLAGS=-Dterminal.jline=false -Dterminal.ansi=true
```

Replace APP FLAGS completely. Do not append to the old preview flags.

Normal staging APP FLAGS:

```text
--environment=staging --token-file=t --moderation-config-file=m
```

Smoke-test APP FLAGS:

```text
--environment=staging --token-file=t --moderation-config-file=m --smoke-test
```

The old `--staging-ui-preview`, tunnel, preview-public-url and preview-web flags must not remain in the normal startup.

## `t` token file

Keep the token out of chat/logs. Validate only that the file is non-empty and that runtime identity validation accepts it.

The token must belong to fixed staging Discord application:

- application `1541279616881397772`
- guild `1410303324745371709`
- required staging channel `1541286004298752091`

Do not switch the runtime to production.

## `m` moderation runtime file

The existing parser accepts only an allowlisted property set. For this deployment require these keys:

```properties
db.jdbc-url=<Staff MariaDB JDBC URL>
db.username=<Staff DB runtime user>
db.password=<Staff DB runtime password>
authority.url=http://<private HUB address>:8771/v1/staff-rank
authority.secret=<shared Paper authority secret, at least 32 chars>
authority.transport=bloom-private-split
component.secret=<different random secret, at least 32 chars>
db.pool-size=4
db.timeout-millis=3000
discord-enforcement.enabled=false
```

Never print values for `db.password`, `authority.secret`, `component.secret`, JDBC credentials, or the token.

Do not enable Discord punishment enforcement in this task.

## HUB Paper authority

Preferred authority backend: HUB.

On HUB create/verify:

`plugins/EnthusiaStaff/discord-staff-authority.properties`

```properties
authority.secret=<same value used by m authority.secret>
authority.bind=bloom-private-split
authority.port=8771
```

Port 8771 must stay private/internal. HUB must have LuckPerms and healthy EnthusiaStaff storage.

If the authority file was newly created or changed, HUB requires one restart before the endpoint exists. Do not restart Velocity for Staff Bot setup.

## SFTP helper

Use:

`deploy/temp-network/staff-bot-sftp-upload.py`

It requires `paramiko` and reads SFTP credentials only from process environment:

```text
STAFFBOT_SFTP_HOST
STAFFBOT_SFTP_PORT
STAFFBOT_SFTP_USER
STAFFBOT_SFTP_PASSWORD
STAFFBOT_SFTP_HOST_KEY_SHA256
```

Do not place the SFTP password on a command line or in Git.

Example invocation after downloading the exact PR artifact JAR:

```text
python deploy/temp-network/staff-bot-sftp-upload.py \
  --jar /safe/local/path/EnthusiaStaff-StaffBot.jar \
  --expected-sha256 183ab2f0b10de57ae181b90f244a731ac054538185ed0148649e2fb5ac7329e4
```

The helper:

- validates local SHA-256;
- verifies trusted SFTP host-key fingerprint;
- validates `t` and the allowlisted required keys in `m` without printing secret values;
- rejects `discord-enforcement.enabled=true`;
- requires `authority.transport=bloom-private-split`;
- uploads to a temporary remote name;
- downloads the temporary remote JAR and SHA-verifies it;
- backs up the previous remote JAR;
- atomically installs the candidate.

If a trusted host-key fingerprint is not already available from the existing authorized SFTP setup, stop and obtain/verify it. Do not add an insecure accept-all-host-key path.

## Start/validate without shell access

Because this Pterodactyl split may expose only process console rather than a shell, use the built-in smoke mode instead of relying on `curl` inside the container.

1. Make sure HUB authority is already running.
2. Set smoke-test APP FLAGS shown above.
3. Start Staff Bot.
4. Require clean identity/database/authority initialization and `staff_bot_smoke_ready environment=staging`.
5. Smoke process should exit zero normally after readiness; it sends no Discord test message/moderation action.
6. If smoke fails, leave the normal bot stopped and report the sanitized failure category.
7. Remove only `--smoke-test` from APP FLAGS.
8. Start Staff Bot normally.
9. Require it to remain running and show no terminal failure/reconnect loop.
10. Leave destructive enforcement disabled.

## Stop conditions

Stop rather than weakening security if:

- PR #256 exact-head validation is not green;
- candidate JAR SHA differs;
- staging application/guild/channel identity fails;
- `m` points at the wrong DB or has missing required properties;
- HUB private authority is unavailable;
- authority signature validation fails;
- MariaDB is unavailable;
- Discord enforcement is enabled;
- setup requires exposing 8765/8766/8771 publicly;
- SFTP host key cannot be verified.

## Report

Return only sanitized state:

```text
candidate_head=
artifact_sha_ok=
hub_authority=
smoke_test=
bot_process=
discord_identity=
staff_db=
authority_signature=
destructive_enforcement=false
rollback_backup=
blocker=
```
