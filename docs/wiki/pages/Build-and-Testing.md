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

## Wiki validation

```bash
python scripts/wiki/validate_wiki.py
```

Wiki checks run separately from Java tests and must pass before publishing.
