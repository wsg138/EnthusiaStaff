# DiscordSRV JAR dependency inventory

This standalone Java 21 tool is a **read-only, conservative early check** before the
physical uninstall of DiscordSRV. It scans the immediate contents of a Paper
`plugins` directory, opening only `plugin.yml` and `paper-plugin.yml` inside
JAR files. It does **not** inspect the plugin data/config folders, connect to a
server, read private keys, write to a server, change any role or channel, or remove
any files.

## Build and test

From this directory, with JDK 21 or newer:

```sh
javac --release 21 DiscordSrvDependencyInventory.java DiscordSrvDependencyInventoryTest.java
java DiscordSrvDependencyInventoryTest
```

To inventory a separate, **authorized staging copy** of the Paper plugin directory:

```sh
java DiscordSrvDependencyInventory --plugins-dir "/path/to/staging/plugins"
```

Use a complete staging plugin/JAR snapshot, not production credentials or a
stripped subset. Reading the live production folder requires the normal separate
access/maintenance authorization. Do not upload the server's private configuration
to the repository.

## Interpretation

- `LEGACY_PLUGIN`: the DiscordSRV JAR itself. Its presence is informational.
- `BLOCKER`: another plugin manifest references DiscordSRV. This includes
  hard dependencies, soft dependencies, and compatibility hooks. Inspect and
  migrate or positively establish that the optional path is disabled before
  considering removal.
- `UNVERIFIED`: unreadable JAR, missing plugin manifest, excessive manifest
  size, or a non-regular/symlinked JAR. Resolve rather than assuming safety.
- `RESULT=BLOCKED`: at least one manifest reference or unverifiable JAR.
  Exit code 2.
- `RESULT=NO_MANIFEST_REFERENCES`: no manifest-level references detected
  among the scanned JARs. Exit code 0 **does not authorize uninstall**.
- Empty or invalid input produces exit code 3. An inventory that did not run
  is never a PASS.

This scanner cannot prove the absence of reflective hooks, imports, direct
DiscordSRV API use, network/plugin startup ordering, database ownership, or
feature-level behavior. A plugin may refer to DiscordSRV only from bytecode
without recording a YAML dependency. The final retirement requires the
separate evidence and acceptance gates in
[`docs/discordsrv-full-retirement.md`](../../docs/discordsrv-full-retirement.md).

The scanner never prints plugin manifest contents or credential fields; only
sanitized JAR basenames, classifications, and totals. It neither treats
DiscordSRV's own JAR as a dependent consumer nor accepts a symlinked JAR as
verified.

## Automation boundary

**Never** make this command delete `plugins/DiscordSRV.jar`, its configuration
directory, or any other plugin automatically. A dependency-free manifest scan
is necessary but not sufficient for physical removal. A separate reviewed
cutover and rollback plan plus explicit owner approval are required.
