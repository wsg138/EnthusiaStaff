# Codex execution — curate Badgers upstream branches

These branches already exist and intentionally start at exact Badgers `main`:

- `wsg138/EnthusiaCommend:upstream/badgers-main-cleanup` from `BadgersMC/EnthusiaCommend` SHA `f6bd0f56273425b28bb4135e144191b121e6aaca`.
- `wsg138/EnthusiaCurrency:upstream/badgers-main-cleanup` from `BadgersMC/EnthusiaCurrency` SHA `3d32bb50ae08024c100b75f099a77309ede370e9`.

Do not merge either Enthusia `main` wholesale. Their histories are heavily diverged and contain Enthusia-network-specific integrations.

## Commend selection rules

Port only changes that are generic to the reputation plugin and do not depend on EnthusiaStaff, EnthusiaTeleport, Enthusia-specific network topology/config, private analytics/export infrastructure, or other private provider contracts unless Badgers explicitly wants that dependency.

Start by evaluating small generic correctness/security fixes such as:
- private reputation text input not leaking into public chat;
- temporary anvil GUI item/dye cleanup;
- wrong-player effect targeting fixes;
- persistence/audit correctness fixes that apply to the upstream schema.

Do not blindly cherry-pick commits if their surrounding architecture differs. Reimplement the minimal fix on the Badgers base when necessary.

Required before opening the upstream PR:
- Java 21;
- `mvn --batch-mode --no-transfer-progress clean verify`;
- PMD/static analysis used by the repository;
- regression tests for every ported bug;
- no EnthusiaStaff/private-network classes or configuration added unintentionally;
- one focused PR with a clear changelog, not the entire fork history.

## Currency selection rules

Port only generic economy correctness/performance/security fixes that make sense on Badgers main. Exclude EnthusiaStaff provider code, private Plan/R2 export behavior, network-specific APIs, and deployment/Sentinel-only changes unless Badgers explicitly requests them.

The old draft `wsg138/EnthusiaCurrency#2` is stale/conflicted against current Enthusia main; do not use it as an upstream branch without independently revalidating its baltop-cache idea on the clean Badgers base.

Required before opening the upstream PR:
- Java 21;
- `mvn -B -ntp verify`;
- repository analyzers/static checks;
- tests for success/rejection/retry/conflict/rollback where the ported change can fail;
- no Staff/private-network API leakage;
- one focused upstream PR.

## Market

Do not create another Market upstream PR. `BadgersMC/EnthusiaMarket#194` already exists from the wsg138 fork and is mergeable. The current GitHub App cannot mutate that upstream PR's metadata (403), so leave it intact and do not duplicate it.

## Output

For each curated branch report:

```text
repo=<Commend|Currency>
base_badgers_sha=<sha>
ported_changes=<short list>
build=<pass/fail>
tests=<pass/fail>
static=<pass/fail>
head_sha=<sha>
upstream_pr=<number-or-not-opened>
blocker=<none-or-reason>
```

Do not open an upstream PR until the branch has real product changes, all required local/hosted checks available to the fork have passed, and the diff contains no Enthusia-only integration by accident.
