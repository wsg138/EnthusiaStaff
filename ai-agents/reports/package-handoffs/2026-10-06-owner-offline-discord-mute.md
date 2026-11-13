# Offline Discord mute verification handoff

Published October 14 review heads: Staff `0ea1f43a9894243d32757da7c9e3cc9977dd2f94`
and RoseChat `4948e4782f4882887bb26f50ddd371e7cdba92bd`. Original three review threads
are resolved with source/test evidence. Staff hosted Coverage `38638009528` is running;
RoseChat Build `38638002355` is ACTION_REQUIRED for maintainer approval of the fork run.
The exact same 460 generic orchestration errors reproduce on current unchanged main.
This documentation-only publication does not merge or supersede either implementation PR.

Owner-directed `OWNER-OFFLINE-DISCORD-MUTE`: PARTIAL / ACTIONABLE_CONTINUATION.
Base `ceb12e0f`; provider base `cd0290b`. See the package contract for scope, requirements and local evidence.
The source repair is implemented in isolated `package/owner-offline-discord-mute` branches; both Staff and RoseChat must be delivered together to restore offline linked Discord ingress.
Preserve existing packages and the ES-X01 public aggregate-copy licensing boundary. No provider source is imported into Staff.
Existing publication: Staff [PR #339](https://github.com/wsg138/EnthusiaStaff/pull/339) and required RoseChat [PR #22](https://github.com/wsg138/Enthusia-RoseChat/pull/22), both OPEN/DRAFT. Do not publish duplicates.
October 14 owner-directed review reconciles Staff `af2b4437` and RoseChat `818e3eb`, preserves coordinator work, repairs server-thread dispatch/feedback and atomic registration removal, and adds a demonstrated red/green race regression.
Local current-base evidence: 1,290 Paper + 3 contract tests pass on Java 25; Staff runtime JAR and provider-leak check pass. RoseChat Java 21 clean test/shadowJar passes 155 tests. Wiki passes 41 pages. Shared interface sources match. No migrations change (current Staff V31).
Next: inspect new exact-head hosted checks/static/review, obtain any required fork-run approval, then authorized isolated offline linked delivery, full/public mutes, vanish and lifecycle acceptance. Full MariaDB/Testcontainers requires Docker/hosted infrastructure; old exact-head evidence does not apply to the new reconciliation. Never deploy unmerged local JARs. Production remains untouched.
