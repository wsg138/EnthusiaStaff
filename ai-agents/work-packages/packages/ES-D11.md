# ES-D11 — AutoMod enforcement and security locks

Status: `PLANNED`. Priority: 140. Depends on accepted `ES-D10` shadow evidence. Internal package.

## Objective
Turn validated AutoMod signals into configurable, auditable enforcement without letting uncertain/AI-only signals create severe sanctions.

## Scope
Allow/log/flag/delete/warn/mute/case outcomes through existing services; escalation ladder; staff mini-panels (overturn/change/history/note/escalate/evidence); all-staff ping policy only for medium/high/uncertain events; Account Security Lock for likely compromised accounts; `/unlock`; repeated substantially similar link across three non-exempt channels in 60 seconds compromise rule; known-malicious-link handling; staff-message removal/logging without auto-punishment; nickname reset/flag behavior; reaction removal/logging. Ticket/support exemptions remain total for automatic scanning/action.

Security Lock is safety state, not punishment and never contributes to punishment ladder; support/ticket access must remain available.

## Validation
Replay/restart/rate-limit/partial-failure tests, false-positive guardrails, lock/unlock recovery, staged enforcement corpus, audit/overturn correctness and full CI/review. No ban from AI-only ambiguity.
