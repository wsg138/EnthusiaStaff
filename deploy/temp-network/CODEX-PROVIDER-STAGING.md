# Codex execution — provider staging on Temp SMP

Core Staff/LiteBans shadow validation must be healthy first. Keep LiteBans authoritative and EnthusiaStaff in `SHADOW_MIGRATION` throughout provider testing.

## Reputation / EnthusiaCommend

Use exact source `wsg138/EnthusiaCommend:main` at `7078510031a4eebbd913b91adc8a2f793faade74`.

Evidence:
- main push workflow `35564594778` completed successfully on that exact SHA;
- retained artifact `EnthusiaCommend`, artifact id `10623242863`, archive digest `sha256:d8437f155964ce15825d55fc41d1e8e2bbf03a62a28aaeaf9b62acd1d59d7c9b`.

Preferred path: download that exact workflow artifact, extract the plugin JAR, record its JAR SHA-256 locally, back up the existing Temp SMP JAR if present, upload the exact JAR, and restart only Temp SMP.

If the artifact cannot be downloaded, rebuild exact source on Java 21 with:

```text
mvn --batch-mode --no-transfer-progress clean verify
```

Do not use open PR #23 as a runtime candidate; it is test/documentation-only.

Acceptance after restart:
- plugin enables without exception;
- ordinary rep give/view/remove/history flows work on approved test accounts;
- Staff remains `SHADOW_MIGRATION`;
- `/estaff verify full` does not gain a new provider/runtime failure;
- restart preserves reputation state;
- no unrelated plugin is replaced.

## Currency / EnthusiaCurrency

Use exact source `wsg138/EnthusiaCurrency:main` at `f010380239c171bb883c2925b712cc07a63a8f48`.

Evidence:
- main CI run `35564584157` completed successfully on that exact SHA;
- that workflow retained no JAR artifact, so build from exact source rather than using an unknown binary.

Build on Java 21:

```text
mvn -B -ntp verify
```

Record the built JAR SHA-256, back up any existing Temp SMP Currency JAR, upload the exact built JAR, and restart only Temp SMP.

Do not include open PR #18 in the runtime candidate; it is test/documentation-only. Do not include stale draft PR #2 unless separately reconciled and revalidated.

Acceptance after restart:
- plugin enables cleanly;
- deposit/withdraw/pay/balance flows work on approved test accounts;
- moderation/provider read paths do not mutate balances unexpectedly;
- restart preserves balances/state;
- Staff remains `SHADOW_MIGRATION` and `/estaff verify full` gains no new provider/runtime failure.

## Market

Do not deploy an arbitrary Market branch yet.

Current boundaries:
- `wsg138/EnthusiaMarket#8` contains the Muse Staff-integration compatibility repair and remains unmerged;
- `BadgersMC/EnthusiaMarket#194` is the existing upstream provider PR;
- `wsg138/EnthusiaStaff#139` is the Staff aggregate Market provider and remains separately gated.

Before Market is added to Temp SMP, reconcile one exact standalone Market candidate that contains current main plus the required Staff compatibility fixes, run full tests/analyzers, record its JAR SHA, then deploy Market last.

## Order

1. Core Staff/LiteBans shadow healthy.
2. Commend only; validate.
3. Currency added; validate.
4. Market last after its candidate is explicitly frozen/validated.
5. Run `/estaff status` and `/estaff verify full` after every provider addition.

Do not activate Staff cutover, disable LiteBans, or mutate production provider data as part of this staging sequence.
