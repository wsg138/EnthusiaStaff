# ES-D16 moderation real-data read bridge — Bloom live-acceptance blocked handoff

Status: `BLOCKED` / `PARKED_BLOCKED`.

Date: 2026-09-02.
Repository: `wsg138/EnthusiaStaff`.
Starting canonical `main`: `44f284606813d133b6b2813cdc6cbe8924c5d7af`.
Implementation PR: #187 (open/draft/unmerged; mergeable at publication).
Implementation branch: `package/es-d16-moderation-read-bridge`.
Frozen reviewed executable head: `066b97f4344ab83d3e226b3f4ff3ab614dee6430`.

## Terminal outcome
All safe repository work and protected Cloudflare staging are complete. D16 cannot become `COMPLETE` because final live read acceptance requires owner-operated Bloom DuckPanel staging splits and this worker has no authenticated Bloom mutation surface. PR #187 remains unmerged; hosted tests are not relabeled as live acceptance.

## Exact-head evidence
- Coverage/full Java 21 `33683792916` / `100426714267`: PASS; full integration build; 27 provider API types / zero leaks; 52.08% line / 42.18% branch / 54.38% instruction coverage; artifact `9867619687`, digest `sha256:c52610d6913e85d80f8397fc898344f0b530e979adb42f7439346168687e34fb`.
- Moderation Web Validation `33683792884`: PASS.
- Staff Bot Configuration Cache `33683792893`: PASS.
- Staff Bot artifact `33683792982` / `100426291034`: PASS; artifact `9867301625`; JAR SHA-256 `f546bbb418e4d38b3f1a1eea3f4621739bd6d1e75351c9cd73f0ce39e1056b60`.
- Sentinel Paper artifact `33683792967` / `100426290606`: PASS; artifact `9867310817`; JAR SHA-256 `0bc62c09742fe0eae96a1725e52a64756a761bf134023da5ce71438de6627944`.
- Codacy static: PASS, zero annotations/no new valid findings.
- CodeRabbit exact-head: no actionable findings; zero unresolved valid review threads.

## Protected Cloudflare staging — PASS
Guarded dispatcher `33688117871` pinned canonical `main` and `066b97f4344ab83d3e226b3f4ff3ab614dee6430`. Permanent run `33688133318` / job `100440387112` passed fixed tunnel/DNS provisioning, 14 web tests/Wrangler validation, Worker deployment `5fb4931b-65a7-4df7-9444-ad354323e228`, origin/private fence, first-use launch, authenticated session, replay rejection, and simulation-only fencing. Raw Discord bot credentials were not uploaded to Cloudflare. The failure-only tunnel-provisioning guard was correctly skipped because provisioning succeeded.

Historical `33530157844` remains explicit non-passing HTTP-403 history; that condition is repaired. Staging Discord Message Content entitlement is verified enabled, while the runtime still does not subscribe to that Gateway intent.

## External blocker and exact owner action
The owner must deploy the exact validated artifacts and runtime-only configuration described by `docs/staff-bot-staging-ui-preview.md` to authorized non-production Bloom staging. Use one authoritative EnthusiaStaff MariaDB, matching private-split authority secret, distinct component secret, staging Discord token file, current Linux `cloudflared`, and only the tunnel-specific connector token for `enthusia-moderation-read-staging`. Never place the broad Cloudflare account API token on Bloom and never publicly allocate ports 8766/8771.

Start Paper first, then Staff Bot. Return only sanitized status/console evidence. Acceptance must prove private authority connectivity; actor/guild/target authorization; real identity/link/sanction/history reads; bounded Discord message/channel reads; truthful unavailable behavior; and no destructive moderation action.

## Boundaries and resume
PR #187 and its product branch contain unique unmerged work and must remain. No squash/rebase/force/auto-merge. PR #178 and PR #139 were not modified. Production Discord/Minecraft data/configuration, LiteBans authority, issue #43, destructive moderation, and cutover remain untouched.

Once Bloom staging is deployed, D16 becomes `ACTIONABLE_CONTINUATION`: reconcile live GitHub, verify connector health, run sanitized live acceptance, repair/revalidate any real finding, merge PR #187 normally only after acceptance passes, prove containment/cleanup, publish `COMPLETE`, update dependency routing without starting another package, and stop.
