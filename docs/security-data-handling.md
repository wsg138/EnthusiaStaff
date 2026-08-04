# Sensitive database inputs

Production or representative database snapshots must remain outside this repository, all repository worktrees, and all public GitHub Actions artifacts.

Use a private local input directory with current-user-only permissions when a migration or investigation requires a snapshot. Never commit, attach, cache, log, or upload that input through a public repository workflow. Repository-owned Flyway migrations under `persistence/src/main/resources/db/migration/` are the only SQL files intentionally tracked here.
