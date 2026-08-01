# Build and Testing

## Complete local validation

Windows:

```powershell
.\gradlew.bat clean test check runtimeJars
```

Linux/macOS:

```bash
./gradlew clean test check runtimeJars
```

This must build the two runtime jars, run unit and integration tests, and
perform configured verification tasks.

## Runtime artifacts

Expected deployables:

```text
paper/build/libs/EnthusiaStaff-Paper-<version>.jar
velocity/build/libs/EnthusiaStaff-Velocity-<version>.jar
```

Inspect jar contents for:

- Exactly the intended entrypoint and resources
- No provider-owned API duplication
- No private jars
- No secrets or local configuration
- No test fixtures or development servers
- Correct service metadata

## Focused checks

Use focused module tests while developing, then rerun the full build before a
checkpoint.

Examples:

```bash
./gradlew :domain:test
./gradlew :persistence:test
./gradlew :protocol:test
./gradlew :paper:test
./gradlew :velocity:test
./gradlew :integration-tests:test
```

Do not claim a test passed unless the exact command ran successfully at the
exact reviewed commit.

## Test categories

Required coverage includes:

- Punishment ladder, decay, combined sanctions, removal, overturn
- Every rank boundary, especially Developer denial
- Alt inheritance and exceptions
- Inventory concurrency, revisions, offline patches, restoration
- Economy rollback and uncertain provider outcomes
- Staff mode crash/reconnect and item leakage
- Vanish hierarchy and provider visibility
- Freeze restrictions and reconnect
- Discord/network outbox lease, retry, circuit breaking
- LiteBans dry run, rerun, shadow mismatch, cutover
- Website auth, codes, appeals, uploads, privacy
- Database/network/partial failure injection

## Coverage targets

Authoritative goals specify:

- Critical code: 80% line / 70% branch
- Overall Java: 70% line / 60% branch

Getter-only or assertion-free tests do not satisfy the intent.

## Static analysis

The target is Codacy grade A with zero unresolved first-party findings. Do not
achieve that by disabling tools, excluding source, blanket suppressing, or
lowering thresholds. Narrow suppressions require documented false-positive
evidence.

## Staging

Automated tests cannot prove:

- Real Paper/Leaf event ordering
- Real Velocity backend transitions
- Provider classloader compatibility
- Geyser/Bedrock GUI behavior
- Voice/chat recipient behavior
- Live certificate rotation
- Multi-staff inventory viewers
- Production-like load and crash windows
- 168-hour shadow parity

Record staging evidence with commit, jar hashes, configuration version,
environment versions, steps, results, and logs.

### Exact-SHA Pi gate

The repository's `Pi Staging` workflow is a merge-candidate gate, not a general
production-readiness claim. For an eligible pull request it dispatches the
trusted staging harness with the exact PR head SHA and waits for that exact
request's verdict.

The current harness independently builds with Java 21, inspects the Paper
runtime jar and provider-API packaging, loads the plugin on Paper, exercises
two boot/storage/command/shutdown cycles, and scans the sanitized evidence for
critical failures. A passing result applies only to the recorded SHA; push a
new head and the gate must run again.

This gate does not replace Velocity, multi-backend, provider-plugin, Bedrock,
live Discord, production-data, load, or crash-window acceptance testing.

## Wiki validation

```bash
python scripts/wiki/validate_wiki.py
```

Wiki checks run separately from Java tests and must pass before publishing.
