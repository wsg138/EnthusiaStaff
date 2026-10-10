# DiscordSRV JAR dependency inventory

This standalone Java 21 tool is a **read-only, conservative early check** before the
physical uninstall of DiscordSRV. It scans the immediate contents of a Paper
`plugins` directory, opening `plugin.yml`, `paper-plugin.yml` and bounded
compiled `.class` entries inside JAR files. It does **not** inspect plugin
data/config folders, connect to a server, read private keys, write to a server,
change any role or channel, or remove any files.

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
- `HARD_DEPENDENCY`: a manifest declares DiscordSRV in Bukkit `depend`
  or Paper `dependencies.server.DiscordSRV.required: true`. The dependent
  plugin must be removed or migrated before DiscordSRV is uninstalled.
- `SOFT_DEPENDENCY`: Bukkit `softdepend` / `loadbefore`, or a Paper dependency
  explicitly marked `required: false`. This is **not** a safe-to-remove
  finding: existing features may still depend on the installed DiscordSRV.
- `REFERENCE`: another manifest mentions DiscordSRV outside a recognized
  dependency declaration, or the Paper `required` flag cannot be determined.
  Review as unresolved rather than inferring optionality.
- `BYTECODE_REFERENCE`: a plugin has `DiscordSRV` (case-insensitive) in
  a compiled class, potentially as a direct JVM package symbol, reflection
  string, or incidental constant. This is reported **even if the same plugin
  already declares a manifest dependency**. Review the referenced component:
  the finding alone does not prove an active runtime dependency.
- `HARD_DEPENDENCIES`, `SOFT_DEPENDENCIES`, `OTHER_MANIFEST_REFERENCES`
  and `BYTECODE_ONLY_REFERENCES` are **disjoint** counts by plugin.
  `BYTECODE_REFERENCES` is an **overlapping** count of all non-legacy plugins
  with matching compiled class bytes; it may include the manifest categories.
  `DEPENDENCY_REFERENCES` counts distinct non-legacy plugins matching either
  source exactly once. All categories remain fail-closed. The analyzer is
  deliberately conservative and not a full YAML parser.
- `UNVERIFIED`: unreadable JAR, missing plugin manifest, excessive manifest
  or expanded class size/count, or a non-regular/symlinked JAR. Resolve rather
  than assuming safety. Class scans are capped at 1 MiB per entry and 256 MiB
  expanded bytes per JAR; a cap violation is never treated as clean.
- `RESULT=BLOCKED`: at least one manifest/class reference or unverifiable JAR.
  Exit code 2.
- `RESULT=NO_DETECTED_REFERENCES`: no manifest/class UTF-8 references detected
  among the scanned JARs. Exit code 0 **does not authorize uninstall**.
- Empty or invalid input produces exit code 3. An inventory that did not run
  is never a PASS.

This scanner recognizes case-insensitive `DiscordSRV` occurrences in ordinary
class-file bytes; it can find hidden symbols but also reports harmless incidental
strings. It cannot prove the absence of encrypted/obfuscated reflection,
configuration-only hooks, plugin startup ordering, database ownership, or
feature-level behavior. Clean bytecode is **not** a substitute for runtime testing. The final retirement requires the
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
