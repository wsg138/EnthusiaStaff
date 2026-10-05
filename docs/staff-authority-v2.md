# Staff Mode Authority v2

Tracking: #271

This document records the migration contract introduced by the Authority v2 foundation.

## Permanent identity

Permanent staff identity is represented by non-authoritative permission nodes:

- `enthusiastaff.identity.helper`
- `enthusiastaff.identity.mod`
- `enthusiastaff.identity.admin`
- `enthusiastaff.identity.owner`
- `enthusiastaff.identity.developer`

`owner` maps to the current internal `StaffRank.FOUNDER` value until the domain naming is migrated separately.

The existing `enthusiastaff.rank.*` bundles remain a compatibility fallback during migration. If an explicit `identity.*` node and a conflicting legacy `rank.*` node are both present, the permanent identity wins.

## Active-duty LuckPerms context

While an authoritative Staff Mode session is active, EnthusiaStaff supplies the same LuckPerms context on Paper and Velocity:

- context key: `enthusiastaff-duty`
- context value: `active`

Paper derives it from the local authoritative Staff Mode session. Velocity verifies the durable session is `ACTIVE` and owned by the player's current backend before publishing the context. This lets deployment configuration make groups such as `active-helper`, `active-mod`, `active-admin`, and `active-owner` conditional on Staff Mode without hard-coding those LuckPerms group names into Java.

The context is an inheritance/input mechanism only. It is not sufficient authorization for destructive operations. Authority v2 sensitive mutations must also revalidate the authoritative Staff Mode session immediately before commit so stale permission caches, stale GUIs, or accidental direct grants cannot authorize a mutation.

## Migration boundary

This foundation does not modify production LuckPerms data and does not yet remove any permission from the legacy `rank.*` bundles. Production/base-vs-active group migration must wait until the direct active-session authorization gates and rank profiles from #271 are implemented and validated.
