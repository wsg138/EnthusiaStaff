# W5 — External components, forks, and mirror status

**Tracking:** [repository cleanup #426](https://github.com/wsg138/EnthusiaStaff/issues/426).  
**Snapshot:** Aggregate at [`EnthusiaStaff/main@8d6ca759`](https://github.com/wsg138/EnthusiaStaff/commit/8d6ca75909a3568664ac543852a5ac0d94dda4b6), external default heads read on 2026-11-11. **No source import/removal/deploy occurred.** The [module relocation plan](MODULE-RELOCATION-PLAN.md) documents the separate Gradle and Cloudflare consumers.

## What is and is not in the aggregate

The [`components/` tree](../../components) has **2,268 tracked files** (plus directories) distributed as:
- `enthusia-site/`: 2,079 blob files including `COMPONENT-METADATA.md`; Node website, Cloudflare source/config, docs, assets and extensive history.
- `enthusia-commend/`: 106 blob files including metadata; Java Paper plugin with Maven `pom.xml` and source/tests.
- `enthusia-currency/`: 80 blob files including metadata; Java Paper plugin with Maven `pom.xml` and source/tests.
- `enthusia-market/`: **one metadata blob only**.
- `enthusia-rosechat/`: **one metadata blob only**.
- `components/README.md`: one catalog policy file.

These are direct Git objects, **not Git submodules**. The three populated copies are real source, not merely links. Neither `components/*` nor `moderation-web` appears in the root [Gradle settings](../../settings.gradle.kts): the external providers are **not compiled by the root `runtimeJars` task**. In particular, Maven project roots `components/enthusia-currency/pom.xml` and `components/enthusia-commend/pom.xml` are independent build definitions, and the copied site [`package.json`](../../components/enthusia-site/package.json) has its own Node scripts. The nested site `.github/workflows` entries are inert as root workflow definitions; that does **not** prove separate standalone CI, Cloudflare deployments or other consumer workflows are unused.

The current [component registry](../../ai-agents/work-packages/COMPONENT-REGISTRY.md), [mirror policy](../../ai-agents/work-packages/BRANCH-AND-MIRROR-POLICY.md), and [sync tool](../../tools/component-sync/README.md) demand reviewed same-package PRs in **both** repositories and exact content parity, excluding only aggregate-only `COMPONENT-METADATA.md`. A change to link-only entries must first replace that policy with owner approval, version pinning, CI verification, and compatibility gates.

## Verified components and repository provenance

| Aggregate directory | Independently verified repository | Repository type / license status | Content/build role | W5 disposition |
| --- | --- | --- | --- | --- |
| [Site](../../components/enthusia-site) | [`wsg138/enthusia-site`](https://github.com/wsg138/enthusia-site), default `main` | Not a GitHub fork; reuse/redistribution license **not verified** here | Node/website with Cloudflare Pages-style `wrangler.toml`; site assets, functions and additional Cloudflare packages | Populated mirror, **diverged**; keep source until owner decides reconciliation or canonical-link model |
| [Currency](../../components/enthusia-currency) | [`wsg138/EnthusiaCurrency`](https://github.com/wsg138/EnthusiaCurrency), `main` | Not a GitHub fork; license rights **not separately verified** | Java 21, Maven shaded plugin; runtime `plugin.yml`; versioned artifactId `enthusia-currency`, `pom.xml` version at aggregate `1.4.4` | Populated mirror, **diverged**; standalone latest is not automatically safe to overwrite aggregate |
| [Commend](../../components/enthusia-commend) | [`wsg138/EnthusiaCommend`](https://github.com/wsg138/EnthusiaCommend), `main` | Not a GitHub fork; license rights **not separately verified** | Java 21, Maven shaded plugin; `pom.xml` defines `EnthusiaCommend-<version>` output, at aggregate version `2.13.1` | Populated mirror, **diverged**; reconcile distinct project progress before any source deletion |
| [Market](../../components/enthusia-market) | [`wsg138/EnthusiaMarket`](https://github.com/wsg138/EnthusiaMarket), `main` | GitHub-verified fork of [`BadgersMC/EnthusiaMarket`](https://github.com/BadgersMC/EnthusiaMarket); GitHub reports AGPL-3.0 (review actual obligations before rehosting) | Independent provider, referenced as Paper soft dependency; no aggregate source/build artifact | Metadata-only, `NOT_IMPORTED`; **link-only candidate** after policy revision; preserve provenance |
| [RoseChat](../../components/enthusia-rosechat) | [`wsg138/Enthusia-RoseChat`](https://github.com/wsg138/Enthusia-RoseChat), default `master` | Verified fork chain [`wsg138`](https://github.com/wsg138/Enthusia-RoseChat) → [`BadgersMC`](https://github.com/BadgersMC/Enthusia-RoseChat) → [upstream Rosewood Development](https://github.com/Rosewood-Development/RoseChat). [Checked-in LICENSE](https://github.com/wsg138/Enthusia-RoseChat/blob/master/LICENSE) excludes publish/(re)distribution rights. | Independent communication provider; aggregate remains metadata-only | **BLOCKED_LICENSE_REDISTRIBUTION** for public source mirror; link/reference only pending legal/policy review |

**Other verified external integration repositories, not inside the five `components/` copies:**
- [`wsg138/EnthusiaAutoClicker`](https://github.com/wsg138/EnthusiaAutoClicker) exists as a standalone non-fork repository; [Paper `plugin.yml`](../../paper/src/main/resources/plugin.yml) soft-depends on `EnthusiaServerAutoClicker`. Repository name and plugin identifier differ; do not assume exact deployed version or component sync.
- [`wsg138/LumaGuilds`](https://github.com/wsg138/LumaGuilds) is a GitHub-confirmed fork of [`BadgersMC/LumaGuilds`](https://github.com/BadgersMC/LumaGuilds) (GitHub reports AGPL-3.0). It appears in the historical [upgrade manifest](../../UPGRADE-MANIFEST.md) as an external integration provider, **not** a tracked aggregate component.
- Dependencies such as LuckPerms, ProtocolLib, Polar, PlaceholderAPI, DiscordSRV, Geyser and Vault are third-party APIs or optional runtime integrations from current build/descriptor evidence, **not proof of owner-managed forks**. No arbitrary GitHub fork URLs are invented here.

## Fresh exact-head mirror comparison

For each aggregate component at [`8d6ca759`](https://github.com/wsg138/EnthusiaStaff/commit/8d6ca75909a3568664ac543852a5ac0d94dda4b6), W5 fetched recursive GitHub Git trees and compared **relative file paths and Git blob object SHAs** to the verified independent default-branch head. `COMPONENT-METADATA.md` was excluded from aggregate product entries, as prescribed in the sync policy. Tree queries were non-truncated. These are **byte-identity differences**, not a functional regression test or deployment check, and do **not** establish the direction or reason for divergence.

| Component | Standalone default head | Aggregate source blobs vs standalone blobs | Missing in aggregate | Extra in aggregate | Shared paths changed | Current exact parity |
| --- | --- | ---: | ---: | ---: | ---: | --- |
| Site | [`8e16f1d6`](https://github.com/wsg138/enthusia-site/commit/8e16f1d6de5f1f9a61397865c5836fc3fc373360) | 2,078 / 3,151 | 1,074 | 1 | 71 | **NO** |
| Currency | [`3f4a1e65`](https://github.com/wsg138/EnthusiaCurrency/commit/3f4a1e65b86cb4598b7727b6b265c7a4da6a265c) | 79 / 93 | 14 | 0 | 7 | **NO** |
| Commend | [`bec72320`](https://github.com/wsg138/EnthusiaCommend/commit/bec72320402700c8d729f14261e92825a95e2118) | 105 / 167 | 62 | 0 | 38 | **NO** |
| Market | [`4fd250af`](https://github.com/wsg138/EnthusiaMarket/commit/4fd250af1103931973330b8615366d9d3b044b88) | 0 / 516 | 516 | 0 | 0 | Metadata-only; not expected to match |
| RoseChat | [`1bd6e80f`](https://github.com/wsg138/Enthusia-RoseChat/commit/1bd6e80fe1e993ec6f770e8e83134f58d1ee0c98) | 0 / 395 | 395 | 0 | 0 | Metadata-only; **license block** |

Examples of measured drift:
- Site has standalone-only `cloudflare/community-sync/` and `cloudflare/competition-jobs/` paths; one aggregate-only `functions/lib/reviewer-auth.js`; shared `cloudflare/market-api/src/index.ts` differs. It cannot safely become a bare pointer without an ownership/version plan.
- Currency has standalone-only `src/main/java/com/enthusia/enthusiacurrency/VaultBootstrap.java` and new tests; shared `pom.xml` and main plugin differ.
- Commend has standalone-only `docs/policy-v2-reputation-correction-*` and tests; shared `pom.xml`, main plugin and commands differ.

**Do not quote historical `IN_SYNC` or `PARITY_VERIFIED` metadata as current status.** Those fields may truthfully describe the **recorded older package checkpoint**. For instance:
- [Site metadata](../../components/enthusia-site/COMPONENT-METADATA.md) records past `ES-X05` parity and a deferred staging exception;
- [Currency metadata](../../components/enthusia-currency/COMPONENT-METADATA.md) records prior `ES-X02` parity;
- [Commend metadata](../../components/enthusia-commend/COMPONENT-METADATA.md) records prior identical shared object IDs.
These records should remain as historical evidence until a dedicated reviewed change updates the canonical status language. No automatic sync, overwrite, or self-merge is justified by these counts.

## Recommended future policy by component

**Keep as independent repositories and reference immutable commit SHAs in a small `external/` catalog,** rather than continuously duplicating thousands of source files in the Staff repository, **if** the owner explicitly approves superseding the current mirror policy. This reduces unrelated history/noise and keeps independent build/test/release ownership clear, but creates new obligations: documented version pins, cross-repository compatibility tests, release provenance, dependency availability, and downstream PR coordination. A pointer to `main` without exact SHA is not a reproducible dependency.

- **Site:** favor the standalone site as release authority only after diagnosing its large diverged tree, any aggregate-only changes, Cloudflare dependency/deployment paths, and existing site-specific PRs. This is a *recommendation*, not an assertion that current standalone main already contains everything needed.
- **Currency/Commend:** favor authoritative standalone Maven repositories with explicit API/version compatibility tests for Staff; keep aggregate copies until reconcile or prove redundant. Preserve a history of intentional integration changes and checksums; tests/source may differ even where metadata once indicated parity.
- **Market:** a metadata/link-only catalog is simpler than importing 516 files, but retain fork/AGPL provenance and a tested contract. No aggregate source to remove now.
- **RoseChat:** **do not mirror publicly** without verifiable license rights; link/pin compatible fork and retain a provider contract, subject to owner/legal review. Avoid treating public visibility of a GitHub repository as redistribution permission.

If the owner retains the two-PR mirror policy instead, each provider must have an assigned reconcile package, reviewable delta, exact default heads, successful deterministic `tools/component-sync/component_sync.py compare`, and independent CI acceptance before asserting `IN_SYNC`. **No mass sync, reset, force push, subtree removal or history rewrite.**

## Verification and acceptance before any policy change

1. For every external repo verify exact clone URL, default branch, target ref/SHA, upstream fork relationship, license and redistribution permissions, archived/availability status, and existing PRs. GitHub metadata alone does not settle legal obligations.
2. Enumerate which paths are compiled, published or deployed from standalone versus aggregate, including Cloudflare site packages, Maven provider outputs, CI artifacts, staging scripts and any private/off-repo consumers. The root Gradle settings do *not* establish absence of external consumers.
3. Reconcile shared files and aggregate-only changes separately; determine canonical branch and release ownership per provider. Identify SQL/data, public API and plugin descriptor compatibility with the current Staff Paper runtime.
4. If changing policy, review a new documented immutable-link/parity validator, ownership table, and reproducible CI; ensure existing work-package tooling/tests and historical links remain navigable.
5. Require clean provider builds/tests, root Staff integration suites, static analysis/Codacy zero new valid findings, exact-head parity or explicit justified divergence, provenance record and independent code review. Roll back by reverting the scoped catalog/policy PR, not by overwriting external repositories.

**Unverified:** exact live provider JARs, live Cloudflare site origins, full redistribution rights beyond RoseChat's express restriction, origin of every divergent file, and cross-repository PR/workflow consumers. Those require further owner/coordinator audit before any deletion or link-only conversion.
