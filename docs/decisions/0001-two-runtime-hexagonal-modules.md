# ADR 0001: Two runtime jars with hexagonal shared modules

**Status:** Accepted

## Decision

Build shared `common`, `domain`, `persistence`, and `protocol` libraries into exactly two shaded runtime jars, `paper` and `velocity`. Platform and integration code implements domain ports; shared code has no platform imports.

## Consequences

The same policy and persistence behavior is used by commands, GUIs, automation, and proxy enforcement. Platform tests need adapters, and both jars include the shared runtime dependencies, but no third deployable coordination plugin is introduced.
