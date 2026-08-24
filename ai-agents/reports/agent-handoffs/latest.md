# Latest package-worker handoff

Current package: `ES-D04 — Account linking and DiscordSRV migration`.

Status: `ACTIVE`.

Canonical handoff: `ai-agents/reports/package-handoffs/2026-08-23-es-d04-account-linking.md`.

Starting `main`: `783925e2b49ab4567bd3c3869e43fc03ff6d285f`.
Branch: `package/es-d04-account-linking`.
Implementation PR: not yet opened; open after the first coherent implementation checkpoint.

Fresh reconciliation found no existing Discord continuation, no competition branch, and no unique website work on `package/codacy-website-appeal-transitions` (0 ahead / 167 behind `main`). PR #139 remains independently parked ES-X03 work and must not be modified.

Current provider contracts are sufficient for D04 without private storage access: PlayTimePlugin exposes lifetime `activeMinutes` through `PlaytimeService#getLifetime(UUID)`, and DiscordSRV exposes public link read/link/unlink operations through `AccountLinkManager`.

D04 must implement one-use five-minute two-direction linking, replacement invalidation, online-account verification, confirmed unlink and audited staff recovery, historical-link access, 25% active-playtime main-account hysteresis with staff override, idempotent DiscordSRV import, and temporary main-link mirroring. Production import/deployment/configuration/cutover is excluded.

Issue #43 remains open and LiteBans remains authoritative. `ES-D05` is separately `READY` but is not started by this worker.
