# Codex execution — provider staging on Temp SMP

Core Staff/LiteBans shadow validation must be healthy first. Keep LiteBans authoritative and EnthusiaStaff in `SHADOW_MIGRATION` throughout provider testing.

The Staff-side Currency and Reputation provider contracts are already complete on current Staff `main`:

- Currency `ES-X02` — merged/synchronized; terminal state published through Staff PR #138 after correction PR #137.
- Reputation/Commend `ES-X04` — merged/synchronized; terminal state published through Staff PR #162 after implementation PR #152 and standalone Commend PR #12.

Do not rebuild those Staff packages merely for Temp testing. Test the exact current standalone provider runtimes against the already-merged Staff APIs.

## Reputation / EnthusiaCommend

Use exact source `wsg138/EnthusiaCommend:main` at:

`7078510031a4eebbd913b91adc8a2f793faade74`

Evidence:
- exact main push workflow `35564594778` completed successfully;
- retained artifact `EnthusiaCommend`, artifact id `10623242863`;
- artifact archive digest `sha256:d8437f155964ce15825d55fc41d1e8e2bbf03a62a28aaeaf9b62acd1d59d7c9b`.

Preferred path:
1. download workflow artifact `10623242863`;
2. extract the plugin JAR;
3. record the actual JAR SHA-256 locally;
4. back up an existing Temp SMP Commend JAR if present;
5. upload that exact JAR;
6. restart only Temp SMP.

If the artifact cannot be downloaded, rebuild exact source on Java 21 with:

```text
mvn --batch-mode --no-transfer-progress clean verify
```

Do not substitute an open feature/test PR for this baseline unless that PR is explicitly selected and revalidated.

Acceptance after restart:
- plugin enables without exception;
- ordinary rep give/view/remove/history flows work on approved test accounts;
- Staff discovers the already-merged ES-X04 reputation provider without API-version/degraded errors;
- Staff remains `SHADOW_MIGRATION`;
- `/estaff verify full` does not gain a new provider/runtime failure;
- restart preserves reputation state;
- no unrelated plugin is replaced.

## Currency / EnthusiaCurrency

Use exact source `wsg138/EnthusiaCurrency:main` at:

`f010380239c171bb883c2925b712cc07a63a8f48`

Evidence:
- exact main CI run `35564584157` completed successfully on that SHA;
- current normal CI does not retain the runtime JAR, so build from exact source rather than using an unknown binary.

Build on Java 21:

```text
mvn -B -ntp clean verify
```

Then:
1. locate the non-`original-*` shaded `enthusia-currency-*.jar` under `target/`;
2. record its SHA-256;
3. back up any existing Temp SMP Currency JAR;
4. upload the exact built JAR;
5. restart only Temp SMP.

Acceptance after restart:
- plugin enables cleanly with Vault present;
- deposit/withdraw/pay/balance flows work on approved test accounts;
- Staff discovers the already-merged ES-X02 provider without API-version/degraded errors;
- moderation/provider read paths do not mutate balances unexpectedly;
- representative rejection/authorization paths do not alter balances;
- restart preserves balances/state;
- Staff remains `SHADOW_MIGRATION` and `/estaff verify full` gains no new provider/runtime failure.

Do not deploy the separate Badgers-upstream candidate `upstream/fix-vault-startup-lifecycle` as the network runtime. That branch is only for contributing the generic missing-Vault lifecycle fix back upstream; our network baseline remains current `main` above.

## Market

Do not deploy an arbitrary Market branch yet.

Live boundaries at this handoff update:
- Badgers upstream Market PR #194 has already merged; do not create another upstream Market PR;
- Staff aggregate Market provider PR `wsg138/EnthusiaStaff#139` remains open and mergeable;
- #139 exact current head `5ef56b8c6a1f07ff5201005e99a93c7ed2900ef4` has successful Coverage/full build and Sentinel artifact runs, but hosted Codacy still reports 1,124 new issues because the complete mirrored Market source tree is new relative to Staff `main`;
- do not bypass that with a broad path exclusion. The validation boundary must be resolved before X03 is merged or used as a final candidate.

Market therefore remains last in the Temp provider sequence.

## Order

1. Core Staff/LiteBans shadow healthy.
2. Commend exact current-main candidate alone; validate.
3. Currency exact current-main candidate; validate.
4. Market only after X03 has a legitimate zero-new-valid-finding validation result or another explicitly reviewed validation design.
5. Run `/estaff status` and `/estaff verify full` after every provider addition.

Do not activate Staff cutover, disable LiteBans, enable destructive Discord enforcement, or mutate production provider data as part of this staging sequence.
