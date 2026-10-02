# Production Staff Bot file startup

This path is for a separately validated production JAR. The currently deployed staging JAR supports file-backed staging only. Keep its current startup flags until the production candidate and token are ready.

Place the production application's token in a private file on the Staff Bot split. Do not put it in a panel flag, repository, log, or chat. The runtime checks that the connected Discord application is the fixed production application and that its guild set is the Enthusia guild.

Use these APP FLAGS for a non-destructive production smoke run:

```text
--environment=production --token-file=tp --moderation-config-file=m --smoke-test
```

The smoke process must report `staff_bot_smoke_ready environment=production` and exit normally. For regular startup, remove only `--smoke-test`:

```text
--environment=production --token-file=tp --moderation-config-file=m
```

Keep `discord-enforcement.enabled=false` in `m`. Verify the private HUB authority and MariaDB readiness before any cutover. The production application must be installed in the Enthusia Discord and its moderation commands granted only to the staff role through Discord Integrations. The runtime still checks linked Minecraft staff rank on every staff command.
