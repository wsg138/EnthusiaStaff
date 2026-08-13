# Component registry

Last verified: 2026-08-05 through the connected `wsg138` GitHub account.

| Component ID | Component | Aggregate location | Standalone repository | Default branch | Verified setup head | Sync state | Purpose/notes |
| --- | --- | --- | --- | --- | --- | --- | --- |
| `COMP-STAFF` | EnthusiaStaff | `repository root product modules and product documentation` | `wsg138/EnthusiaStaff` | `main` | `af9aa3d0d54afc84de7c90cb3fdc5ce3cdf9118a` | `NOT_APPLICABLE` | Core Paper/Velocity moderation platform; no duplicate standalone copy exists outside this repository. |
| `COMP-SITE` | enthusia-site | `components/enthusia-site/` | `wsg138/enthusia-site` | `main` | `9408166c75def0b55caa8d38fb546c6e77ea1f7d` | `NOT_IMPORTED` | Website UX, authentication, punishment pages, appeals, and staff web interface. |
| `COMP-ROSECHAT` | Enthusia-RoseChat | `components/enthusia-rosechat/` | `UNRESOLVED` | `UNRESOLVED` | `UNRESOLVED` | `BLOCKED_UNRESOLVED_REPOSITORY` | Supported communication provider, staff chat, private-message evidence, and presence integration. |
| `COMP-CURRENCY` | EnthusiaCurrency | `components/enthusia-currency/` | `wsg138/EnthusiaCurrency` | `main` | `9696501a01cc11f6e5220c5297a6f34b64204e61` | `NOT_IMPORTED` | Transactional currency removal and restoration provider. |
| `COMP-MARKET` | EnthusiaMarket | `components/enthusia-market/` | `wsg138/EnthusiaMarket` | `main` | `bc24f1010642d6042307bc13a32fb33cc94e8883` | `SYNC_PENDING` | ES-X03 candidate is imported and exactly matches standalone `daed4d08d96f69f4513431c8bff8b90ada8faa70` at product hash `761b6e1e...`; paired PR merge and post-merge parity remain. |
| `COMP-COMMEND` | EnthusiaCommend | `components/enthusia-commend/` | `wsg138/EnthusiaCommend` | `main` | `2083061b8aeaa7fb3adaf89746f91a45e3a03e59` | `NOT_IMPORTED` | Exact reputation mutation and restoration provider. |

## Rules

1. Do not invent a repository name or URL. `COMP-ROSECHAT` remains unresolved until verified.
2. No component has or needs a permanent branch inside `wsg138/EnthusiaStaff`.
3. The root `COMP-STAFF` implementation uses the ordinary `EnthusiaStaff:main` history and one temporary package branch per internal package.
4. External component source is tracked as an aggregate copy under its designated `components/` path and in its standalone repository.
5. An external package normally creates two same-ID PRs, cross-references them, merges both normally, and verifies deterministic content parity after both merges.
6. Component metadata is orchestration-only and is excluded from product-content parity. Nested `.git` directories and submodules are not authorized.
7. Source import is not part of setup. It must occur only in an assigned package after repository, license/history, source SHA, exclusions, and reviewability are verified.
