# Offline Discord mute verification handoff

Owner-directed `OWNER-OFFLINE-DISCORD-MUTE`: PARTIAL / ACTIONABLE_CONTINUATION.
Base `ceb12e0f`; provider base `cd0290b`. See the package contract for scope, requirements and local evidence.
The source repair is implemented in isolated `package/owner-offline-discord-mute` branches; both Staff and RoseChat must be delivered together to restore offline linked Discord ingress.
Preserve existing packages and the ES-X01 public aggregate-copy licensing boundary. No provider source is imported into Staff.
Next: paired draft PRs, exact-head hosted checks/static/review, then authorized isolated test of an offline linked sender, active full/public mutes, vanish and shutdown/reload. Never deploy the unmerged local JARs.
Local focused tests pass; full validation is not green due unavailable Docker and five baseline Windows CRLF-sensitive source tests. Production remains untouched.
