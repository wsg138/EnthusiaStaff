# Sanction shortcut type isolation — offline acceptance

Status: DRAFT / NO_GO. No deployment, configuration change, owner policy approval,
or live server/database access is authorized by this patch.

## Correctness failure

Before this patch, /unban, /unmute, /unwarn, and /removewarning
resolved a case by sanction family but submitted an unscoped
SanctionChangeRequest. The SQL mutation selected **every** eligible
root sanction within that case, regardless of type. A case containing both
BAN and MUTE sanctions could therefore have both ended by /unban.
Passing a case ID directly bypassed even the initial family lookup.
The alias GUI confirmation had the same case-wide mutation behavior.

## Resolution

The existing /removepunishment command remains explicitly case-wide.
Shortcut commands now carry an immutable, non-empty sanction-type scope
through the request and into the SQL row-lock **and** update predicates,
both within the existing transaction. The GUI action catalog also hides
shortcut actions when only unrelated sanction families are eligible.
Unmatched scopes reject without a mutation. Inherited sanctions are still
reconciled only for changed source sanction IDs.

This is not an authorization expansion: the existing rank, duty, action
permission, fresh worker reauthorization, idempotency and case revision
expectation checks remain intact. No schema migration is required.

## Evidence / limits

- Red test: new domain scope contract failed compilation before the request
  gained type-scope support.
- Green: focused domain and GUI unit tests.
- Real disposable MariaDB 11.8.3 Testcontainers:
  CaseSanctionHierarchyIntegrationTest — 7 tests, 0 failed/skipped,
  BUILD SUCCESSFUL, including a mixed BAN/MUTE/APPLIED WARNING case where
  /unban, /unmute and /unwarn each mutate only their own type.
- Final Windows and Linux exact-head verification must be recorded before
  considering this draft for review.
- Still **NO_GO** for production: this does not prove Paper/Folia runtime
  behavior, Java/Bedrock GUI acceptance, rollback, custom production policies
  or owner severity calibration issue #616.
