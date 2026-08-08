# ES-R01 PR-target provenance correction

## Status
`IN_PROGRESS / VERIFYING`

## Triggering evidence
Public checkpoint PR #94 exercised the new bridge through `pull_request_target` on exact PR head `82fb405cbead14406dd0a2fcd2ad87abea405b13`. Public Pi Staging run `31249683256` successfully completed its ordinary GitHub-hosted build for that exact source. The correlated private staging run was `31249951781`; job `93084607327` allocated the trusted `Lincoln-PI-4` runner (runner ID `2`) and failed closed in the bridge provenance verifier before database reset or Paper boot.

The failure was `Public workflow control SHA mismatch`. GitHub's `pull_request_target` run metadata correctly reported the PR source as run/head SHA `82fb405cbead14406dd0a2fcd2ad87abea405b13`, while the trusted workflow/control revision was the PR base/main SHA `094838fa221476e0832cf821f7b4908b9402d0d9`. The verifier had incorrectly required those distinct identities to be equal. Its failure cleanup also exposed an unrelated `set -u` scope defect (`tmp: unbound variable`) after the verifier had already denied boot.

## Correction
Staging PR #60, `ES-R01: fix PR-target bridge provenance binding`, corrected the event-specific rule without weakening trust:

- `pull_request_target` source/run/job head must equal the exact authorized same-repository PR head;
- the matching run PR base must be `main` and its base SHA must equal the trusted workflow/control SHA;
- live PR metadata is still fetched and must still be open, unmerged, same-repository, targeting `main`, and at the exact requested head;
- push/workflow-dispatch control-SHA behavior remains unchanged;
- verifier temporary cleanup now uses a lifetime-safe global handle so every error path cleans up without a local-scope `set -u` failure.

Exact-head Staging Controls CI run `31250097746`, job `93084990928`, succeeded on `Lincoln-PI-4` at PR #60 head `cb393f50118278739ca44e6f99839a981b55195b`. The suite included a valid `pull_request_target` fixture, wrong base/control rejection, clean failure-path checks, source selection, artifact/digest/manifest tests, disposable-database retry tests, storage readiness, successful cycle, issue #43 prerequisite controls, and Sentinel unit tests. PR #60 merged normally as `4036d6e915c2d751bef18849107722dfd1e586a6`.

## Remaining acceptance
ES-R01 is not complete yet. The next exact PR #94 head must pass the live `pull_request_target` bridge through provenance, guarded database reset, two-cycle Paper boot/restart, sanitized evidence upload, correlated public success, and transient transfer cleanup. After PR #94 merges, the resulting current-`main` push must also complete the same bridge successfully before terminal package state is published.
