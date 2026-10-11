# EnthusiaStaff Paper 26.2 API compatibility check

Candidate-only build-test branch. Pin both Paper compile and test APIs to the published stable **26.2.build.122-stable** artifact. Keep plugin metadata `api-version: '1.21'` until a migration review confirms a necessary change. Existing root build uses JDK25 and `--release 21`.

## Required checks
- Run `./gradlew :paper:test :paper:shadowJar --no-daemon` and inspect exact commit green test reports.
- Run API compatibility and reflective startup checks against Paper 26.2 runtime; inspect ProtocolLib and RoseChat integration presence, absent dependencies, event handlers, command registration, and all report GUI paths.
- Test enchantment/item serialization across 1.21.11 and 26.2 worlds on backups; never test inventory mutations with live players.
- Verify stealth, vanish spectator and tab packet presentation, container animation/sounds, staffchat authorized reception, rank-mod survival flight, freeze reconnect, tool inventory lock, and tester snapshot recovery.
- Do not deploy, merge or declare 26.2 compatibility without the complete runtime test matrix.

This branch changes **compile/test API only**; it does not claim to fix runtime behaviors.
