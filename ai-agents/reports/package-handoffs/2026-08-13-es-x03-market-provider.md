# ES-X03 EnthusiaMarket provider handoff

## Current status

`ACTIVE` on 2026-08-13.

## Starting authority

- EnthusiaStaff `main`: `49e5aa999b43193181aafabbb75811c820fa03c7`.
- EnthusiaMarket `main`: `bc24f1010642d6042307bc13a32fb33cc94e8883`.
- Temporary branch in both repositories: `package/es-x03-market-provider`.
- Staff migration ceiling: V18, immutable.
- Market migration ceiling: V024, immutable.
- No prior ES-X03 branch, PR, implementation, or package handoff existed.

## Live reconciliation

- EnthusiaStaff has no open PR.
- EnthusiaMarket PR #1 is unrelated and targets `feat/website-market-sync`, not the default branch.
- The supported standalone repository and default branch are available.
- The standalone repository has no repository-local `AGENTS.md`; the parent Enthusia rules apply.
- The standalone fork default head remains the package authority. Newer detached upstream history is not imported or rewritten by this package.
- Issue #43 remains outside this package; LiteBans remains authoritative.

## Scope in progress

1. Define a supported versioned moderation API and immutable operation models.
2. Add durable stall reservation, compliance restriction, acquisition blacklist, confiscation hold, rollback, and exact restoration.
3. Fence acquisition and listing races without bypassing existing market transactions or rent behavior.
4. Add Staff-side case authorization, durable coordination, recovery, commands, audit, and provider absence/version handling.
5. Import the exact standalone product tree into `components/enthusia-market/` and maintain deterministic parity.
6. Prove both repositories, resolve valid findings, merge both PRs normally, verify post-merge parity, and clean temporary branches.

## Evidence and findings

- Local and hosted validation have not yet run for this package.
- No ES-X03 review threads or analyzer findings exist yet.
- Existing Market transaction and ownership services are authoritative and must be extended, not bypassed.

## Boundaries

- Do not use production listings or player records.
- Do not access credentials, production databases, or deployment routes.
- Do not run cutover or alter punishment authority.
- Representative destructive, latency, and load acceptance remains assigned to ES-V03.
- Do not disturb unrelated Staff worktrees or unrelated Market branches/PRs.

## Exact next action

Add the provider requirements, API contract, operation state machine, persistence migration, and race/recovery tests on the standalone branch; then mirror the exact contract into Staff integration and publish cross-linked draft PRs after the first coherent implementation checkpoint.
