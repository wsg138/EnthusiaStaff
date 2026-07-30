# Build and Testing

## Complete validation

Run the clean validation from the repository root.

Windows:

```powershell
.\gradlew.bat --no-daemon --no-build-cache --no-configuration-cache --rerun-tasks clean test check runtimeJars
```

Linux and macOS:

```shell
./gradlew --no-daemon --no-build-cache --no-configuration-cache --rerun-tasks clean test check runtimeJars
```

Docker must be reachable for this run. MariaDB Testcontainers tests are
mandatory; a run that skips them is not a complete validation.

## Deployable artifacts

The `runtimeJars` task creates exactly two deployable plugin artifacts:

- `paper/build/libs/EnthusiaStaff-Paper-<version>.jar`
- `velocity/build/libs/EnthusiaStaff-Velocity-<version>.jar`

The `integration-tests` module and ordinary module jars are not deployment
artifacts. Build directories, reports, downloaded dependencies, analyzer
caches, and container data must not be committed.

## Focused development checks

Use the narrowest relevant module test while implementing a change, then run
the complete clean validation before merging a checkpoint. Examples:

```shell
./gradlew :common:test
./gradlew :domain:test
./gradlew :persistence:test
./gradlew :integration-tests:test
```

Do not treat a successful compile as evidence that transaction, recovery, or
container-backed behavior passed.

## Static analysis

Codacy uses the repository's checked-in `ruleset.xml` for PMD. Findings are
handled in this order:

1. correctness, security, concurrency, transaction, recovery, resource
   ownership, and data integrity;
2. excessive complexity and oversized responsibilities;
3. meaningful duplication;
4. maintainability and style;
5. demonstrated analyzer mistakes.

Rules and first-party source are not disabled to improve a grade. A finding is
ignored only after its exact report is shown to be incorrect or inapplicable
and the reason is recorded.

## Review evidence

A merge checkpoint should record:

- exact commit tested;
- Java version;
- clean build result;
- test, suite, failure, error, and skip counts;
- MariaDB Testcontainers tests actually executed;
- runtime artifact hashes;
- local and hosted analyzer results;
- unresolved review findings and staging limitations.
