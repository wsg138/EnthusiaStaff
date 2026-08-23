# ES-D10 — AutoMod shadow engine

Status: `PLANNED`. Priority: 139. Depends on `ES-D05`, `ES-D09`. Internal package.

## Objective
Build the custom Discord AutoMod detector in shadow-only mode so policy quality is proven before automatic enforcement.

## Scope
Normalization/confusables/obfuscation; boundaries and substitutions; spam/flood/duplicates/cross-channel behavior; mass mentions; invite handling; allowed/blocked/known-malicious domains; targeted severe abuse/slur/suicide-encouragement contextual rules while ordinary profanity remains allowed; edits through same pipeline; staff behavior logging without auto-punishment; nickname/profile flag signals; reaction-phrase reconstruction; configured full ticket/support exemptions; new-account risk signal; OpenAI Moderation API as optional/free contextual signal with bounded timeouts/retries/cost and no AI-only severe punishment; durable shadow observations and false-positive/negative review data.

## Exclusions
No automatic delete/warn/mute/ban/security lock. No silent policy learning from staff overturns.

## Validation
Large deterministic adversarial corpus including innocent-word false-positive cases, obfuscation and split-message evasions, exempt channels, provider outage/timeouts, replay/edits and performance bounds. Shadow outputs must be reviewable and sufficient for D11 acceptance.
