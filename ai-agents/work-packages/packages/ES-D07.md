# ES-D07 — Discord punishment enforcement

Status: `PLANNED`. Priority: 136. Depends on `ES-D03`, `ES-D05`, `ES-D06`. Internal package.

## Objective
Implement authoritative Discord-only punishment actions with durable external enforcement and recovery.

## Scope
Warning; temporary/permanent managed-role mute preserving ticket/support access; kick; temporary/permanent native guild ban; temporary/permanent channel/category read-only/no-access restrictions; unmute/unban/unrestrict/end/revoke/overturn flows; duration parser/presets; confirmation and immediate reauthorization; reason/explanation/message-delete options; temp expiry; DMs and delivery outcomes; native-ban reconciliation; quick commands calling the same services; durable outbox/worker semantics and partial-failure truthfulness.

Permanent Discord ban/mute/restriction requires Admin+ under approved policy. Discord native Timeout is not the normal mute mechanism.

Approval flows must authorize the concrete requested sanction at decision time, not only the abstract `APPROVE_SANCTION_REQUEST` operation. Loading/claiming/approving a request must re-check the requester's/approver's current authority, required approval rank, target protection, actual Discord consequence type, permanent/custom flags, duration ceiling and current policy before commit. In particular, Mod/Developer approval capability must never allow approval of an Admin-only permanent/custom consequence merely because the surface operation itself is permitted. Mirror the existing fail-closed punishment-request rank/revalidation semantics rather than building a weaker Discord-only approval path.

## Exclusions
No automatic Minecraft punishment, AutoMod enforcement, cross-platform `Both` orchestration, production deployment/cutover.

## Validation
Isolated staging guild/bot enforcement tests where available plus deterministic adapter tests for Discord failures/rate limits/restarts/expiry/hierarchy; MariaDB recovery tests; explicit approval-escalation tests proving Mod/Developer cannot approve Admin-only permanent/custom sanctions; full CI/static/review. Never fake global success when Discord side effects are pending/failed.
