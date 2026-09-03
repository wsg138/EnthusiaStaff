# ES-D06 read-only staff moderation UX — BLOCKED

Date: 2026-08-27
Status: `BLOCKED` / `PARKED_BLOCKED`
Package: `ES-D06 — Read-only staff moderation UX`
Implementation branch: `package/es-d06-read-only-moderation-ux`
Implementation PR: #177
Last fully green reviewed head: `10b4255102489b5c423e1bb22c8daaa009fba6f9`
Blocking state-only head observed: `ad89be16537a7d21b3fa18dc9a54da6fd209017f`

## Completed product work

D06's read-only Discord moderation UX is implemented. The authority-host security finding and Discord 25-option ambiguity finding are repaired with regression coverage. Exact head `10b4255102489b5c423e1bb22c8daaa009fba6f9` passed Coverage `33116219168` / job `98671451083`, Staff Bot Configuration Cache `33116219157`, Sentinel Restart Artifact `33116219081`, Codacy Static Code Analysis check `98671703302` with zero issues, Codacy Diff Coverage `98673935155`, and the requested CodeRabbit rerun with no actionable comments and all three inline findings confirmed addressed/resolved.

Coverage run `33116219168` executed Temurin Java 21.0.12+1 `clean build jacocoAggregateReport runtimeJars`, all unit/integration/MariaDB-Testcontainers tests, runtime-JAR integrity and provider-leak inspection. Aggregate JaCoCo was 51.33% lines / 41.48% branches / 53.69% instructions. Artifact `9664863428` has digest `sha256:865c1fdb071046213a45ee4c851bead4f8298d32733eac3e84cd1bea6f68eee0`.

## Blocking evidence

After the fully green `10b4255` head, only package-state Markdown was changed. On state-only head `ad89be16537a7d21b3fa18dc9a54da6fd209017f`, hosted Codacy Static Code Analysis check `98676412677` completed `action_required` and reports **22 new issues**, with **22 annotations**. This is contradictory to the earlier exact-head green Codacy result and therefore cannot be relabeled as passing or ignored. The check summary exposes the count and complexity deltas but not the 22 individual annotation messages through the currently available GitHub connector surface. The connector rejects the check-run annotations subresource, and the available Codacy notification evidence likewise exposes only the aggregate issue count. Without per-finding rule/message/file/line evidence, the worker cannot safely classify each finding as valid, duplicate, false positive, or stale/misattributed, and broad suppression/exclusion is prohibited.

No merge was attempted. PR #177 remains open. No production deployment/configuration/data access, Discord mutation, secret access, LiteBans authority change, or issue #43 acceptance occurred.

## Exact unblock

Make the 22 individual findings from Codacy check `98676412677` available to a worker through an authorized evidence surface (Codacy PR Issues/CLI/API, GitHub check-run annotations, or equivalent durable per-finding output). Then reconcile live `main` and PR #177, classify every finding individually, fix every valid D06-introduced finding, document any proven false positive individually rather than using broad exclusions, rerun the full applicable exact-head hosted/static/review gates, and merge normally only after Codacy reports zero new valid findings.

If a future exact-head Codacy rerun independently returns green, first reconcile it against this contradictory 22-annotation result and preserve both records; do not silently discard the failed evidence.

## Next action after unblock

Resume the existing PR #177 and branch. Do not create a replacement D06 PR. Complete Codacy triage/repair and exact-head validation, merge normally, prove containment and safe branch cleanup, publish D06 `COMPLETE`, and stop without beginning D07 or any second Discord package.
