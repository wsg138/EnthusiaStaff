# Disposable Paper punishment GUI protocol smoke — scope and evidence

**Draft-only test.** GitHub Actions creates an ephemeral, localhost-only Paper 26.2 build 129 server, with a SHA-256-pinned runtime and the exact candidate JAR built from its PR head. A tiny one-purpose helper plugin connects a disposable offline player and opens only the `/punish` target picker. A Mineflayer Java *protocol* client observes its resulting window packet.

The test explicitly checks:
- Candidate and helper JAR source/build provenance (run and SHA-256 receipts).
- The plugin is enabled only with an isolated-test JVM flag, `server-ip=127.0.0.1`, `online-mode=false` and at most two players.
- The server's `PunishmentTargetPickerHolder` opens with exactly **54 slots**.
- The client receives a matching top-inventory window with 54 slots.
- No command-to-sanction or confirmation action is sent, and the server has no configured production integrations.
- Server and client evidence logs are attached to the CI run, including failures.

**Not proven:** This does not validate a human Java player's mouse interactions, any Bedrock client, Geyser/Floodgate UI translation, 20-row A01–A20 GUI acceptance, permission matrices, approval authorization, any automatic sanctions, test on a Folia server, or safe migration to production. The Java protocol-client view is a synthetic automated client and **cannot mark A01 Java PASS**. Keep all 39 real-client acceptance observations BLOCKED until witnessed in disposable staging.

If the test fails because `/punish` requires additional active Staff Mode state, do not loosen production permissions. Document the exact runtime rejection and use test-only entitlement arrangements that preserve the authentic authority rules; never spoof an approval or punitive action.

An automated CI success proves only the narrow runtime holder/window transmission against one pinned Paper revision. The separate release gates, CodeRabbit review, Folia compatibility and all production policies remain unchanged.
