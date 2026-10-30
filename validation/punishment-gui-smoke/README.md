# Disposable Paper punishment GUI protocol smoke — scope and evidence

**Draft-only test.** GitHub Actions creates an ephemeral, localhost-only Paper 26.2 build 129 server, with a SHA-256-pinned runtime and the exact candidate JAR built from its PR head. A tiny one-purpose helper plugin connects a disposable offline player and opens only the `/punish` target picker. A Mineflayer Java *protocol* client observes its resulting window packet.

The CI probe also checks the **authorization transition**. It first issues `/punish` with the disposable identity's rank permissions but **without active Staff Mode**; the picker must not open (`OFF_DUTY_PICKER_BLOCKED=true`). It then enters Staff Mode using the actual `/staff` command and verifies a new `/punish` attempt opens the picker. A transient off-duty picker opening fails the run, even if it closes before the next inspection. This checks one narrow off-duty denial path, **not** every rank/approval boundary.

The test explicitly checks:
- Candidate and helper JAR source/build provenance (run and SHA-256 receipts).
- The plugin is enabled only with an isolated-test JVM flag, `server-ip=127.0.0.1`, `online-mode=false` and at most two players.
- The server's `PunishmentTargetPickerHolder` opens with exactly **54 slots**.
- The client receives a matching top-inventory window with 54 slots.
- **Only two non-sanctioning player-click controls are permitted:** slot 49 must contain a CLOCK and refresh the picker; slot 51 must contain a BARRIER and close it. The client refuses to click when a slot's item changes, never selects player heads/reasons, and never invokes an approval.
- Server-side `InventoryClickEvent` and `InventoryCloseEvent` must independently show the Refresh click, refreshed picker opening, Close click, and final window closure. The client must also observe the second window and close packet.
- No category, reason, sanction, or confirmation action is sent; player-head navigation is restricted to the unique disposable target, and the server has no configured production integrations.
- Server and client evidence logs are attached to the CI run, including failures.

The next isolated test phase adds exactly one additional offline Java protocol player named `GuiTarget` on the same loopback server. After Refresh and Close, the helper reopens the target picker through the real `/punish` command only once the disposable target is connected and **exactly two players** are online. The client clicks **only slot 9**, and only after verifying the slot contains `PLAYER_HEAD`; the Paper helper independently requires the single allowed disposable target to be online and observes the actual head click. Successful navigation must open `PunishmentGuiHolder`, a 54-slot category overview titled `Punish · GuiTarget`, with the `Choose a Category` guide sign at slot 13. **The client then disconnects without clicking any category, reason, review, or confirmation.** Actual sanctions remain untested.

**Not proven:** This does not validate a human Java player's mouse interactions, any Bedrock client, Geyser/Floodgate UI translation, 20-row A01–A20 GUI acceptance, permission matrices, approval authorization, any automatic sanctions, test on a Folia server, or safe migration to production. The Java protocol-client view is a synthetic automated client and **cannot mark A01 Java PASS**. Keep all 39 real-client acceptance observations BLOCKED until witnessed in disposable staging.

If the test fails because `/punish` requires additional active Staff Mode state, do not loosen production permissions. Document the exact runtime rejection and use test-only entitlement arrangements that preserve the authentic authority rules; never spoof an approval or punitive action.

An automated CI success proves only the narrow runtime holder/window transmission plus safe Refresh/Close and disposable target-to-category navigation against one pinned Paper revision. The separate release gates, CodeRabbit review, Folia compatibility and all production policies remain unchanged.
