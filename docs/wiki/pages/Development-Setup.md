# Development Setup

Use this page to prepare a clean development environment. Before changing behavior, use [[Developer Guide Index]] to find the owning feature and [[Code Review Guide]] to understand the invariants your change must preserve.

## Prerequisites

- Git
- Java 21 JDK
- Docker daemon compatible with the repository's MariaDB Testcontainers usage
- Python 3 for Wiki validation
- An IDE with Gradle support
- Access to any required private compile-only provider artifacts for the exact task
- No production secrets or private player/evidence data in the checkout

## Checkout

```bash
git clone https://github.com/wsg138/EnthusiaStaff.git
cd EnthusiaStaff
git fetch --all --tags --prune
```

Create work from the latest legitimate `main`. Preserve existing branch commits and keep unrelated/concurrent work separate. Do not recreate another worker's branch or silently discard work to obtain a cleaner history.

If you are explicitly assigned an `ai-agents/` work package, follow that package contract and live GitHub routing. Ordinary development should not invent, finalize, or rewrite package state merely because a code or Wiki discrepancy was found.

## Authoritative reading order

For a feature change:

1. [`ENTHUSIASTAFF-GOALS.md`](https://github.com/wsg138/EnthusiaStaff/blob/main/ENTHUSIASTAFF-GOALS.md) — intended finished behavior.
2. Current merged code/config/migrations/tests — implemented behavior.
3. [[Implementation Status]] and matching feature hub — readable current limitations and entry points.
4. [Requirements matrix](https://github.com/wsg138/EnthusiaStaff/blob/main/reports/REQUIREMENTS-MATRIX.md) plus current legitimate review/runtime evidence — exact proof and blockers.
5. [[Developer Code Guide]] — detailed source trace.
6. [[Code Review Guide]] — boundary/failure review.

Historical implementation plans and package records explain how older work was executed; they are not automatically the current product state.

## Java and Gradle

```bash
java -version
./gradlew --version
```

Both should resolve Java 21 for normal repository builds. The project treats compiler warnings seriously; do not weaken lint/quality gates merely to make a change pass.

## Docker and MariaDB

Confirm Testcontainers can reach Docker before calling a run a complete validation checkpoint. MariaDB tests are part of persistence/migration correctness, not optional decoration.

Use disposable schemas/containers for development. Do not point tests or local experiments at production MariaDB or LiteBans data.

## Local secrets and provider artifacts

- Copy example configuration only when needed; keep real values out of tracked files.
- Generate disposable local TLS material.
- Use non-production webhook/site endpoints or disable them.
- Keep private provider jars under ignored local paths.
- Never commit `.env`, credentials, keys, runtime folders, databases, logs, private evidence, build output or generated reports.
- Provider contracts are compile-time boundaries. Do not copy provider-owned internals into EnthusiaStaff because the real API is inconvenient or unavailable.

## Repository shape

The core modules are:

```text
common/
domain/
integration-contracts/
persistence/
protocol/
paper/
velocity/
integration-tests/
components/             # aggregate copies for external components when present
docs/
```

Exactly two Minecraft runtime JARs should be produced: Paper and Velocity. Shared/internal modules and tests are not deployable plugins.

See [[Architecture]] for dependency direction and [[Developer Code Guide]] for composition roots.

## Development loop

1. Identify the owning domain service/policy and durable store before editing an adapter.
2. Read nearby tests and the relevant migration/configuration boundary.
3. Run focused tests while developing.
4. Add hostile-input, authority, stale-state, duplicate, restart, failure and concurrency coverage where the risk exists.
5. Review Paper/Folia or Velocity thread ownership for every new callback/asynchronous hop.
6. Review Java/Bedrock/provider fallback assumptions.
7. Run the complete clean validation from [[Build and Testing]].
8. Inspect the two runtime JARs and provider-leak checks.
9. Review static-analysis/coverage results without suppressing legitimate findings.
10. Run the required exact-candidate runtime/staging gates for the claim you intend to make.
11. Update the focused Wiki page that owns changed human-facing behavior.

A green local branch does not waive Velocity, provider, multi-server, Bedrock, Folia, load, migration, rollback or production acceptance when those are relevant to the change.

## Documentation changes

Repository-managed Wiki source lives in `docs/wiki/pages/`. Validate it with:

```bash
python scripts/wiki/validate_wiki.py
```

Use progressive disclosure:

- concise answer/procedure first;
- feature hub for readable status and entry points;
- [[Developer Code Guide]] for detailed source mapping;
- focused deep-dive pages for complex internals;
- [[Code Review Guide]] for cross-cutting review invariants;
- authoritative goals/evidence for exact requirements and proof.

Do not make the live GitHub Wiki the only copy of a change. See [[Wiki Maintenance]].

## Related pages

- [[Developer Guide Index]]
- [[Code Review Guide]]
- [[Architecture]]
- [[Developer Code Guide]]
- [[Build and Testing]]
- [[Wiki Maintenance]]