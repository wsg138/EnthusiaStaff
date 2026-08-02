# EnthusiaStaff remaining development blueprint

The canonical remaining-development map is maintained in:

- [`docs/wiki/pages/Development-Blueprint.md`](wiki/pages/Development-Blueprint.md)

That repository-managed Wiki source contains the four expandable development
groups, detailed unfinished-work tables, completion conditions and current order.
It is published to the live Wiki after review.

Related sources:

- Feature completion percentages:
  [`docs/wiki/pages/Implementation-Status.md`](wiki/pages/Implementation-Status.md)
- Exact evidence and blockers: `reports/REQUIREMENTS-MATRIX.md`
- Code ownership: `docs/wiki/pages/Developer-Code-Guide.md`
- Validation procedures: `docs/wiki/pages/Build-and-Testing.md`
- Migration operations: `docs/wiki/pages/LiteBans-Migration.md` and
  `docs/wiki/pages/Shadow-Mode-and-Cutover.md`

Do not duplicate the roadmap in this file. Update the canonical Wiki source so
the repository and published documentation remain synchronized.

## Completed feature slice: punishment history and sanction lifecycle

The punishment-history slice now provides bounded player timelines, one canonical case-detail view, exact sanction reduction/early-end/revocation/overturn, request and appeal linkage, transaction-bound hierarchy/authority rechecks, append-only audit, reloadable presentation/validation settings, and Java/Bedrock-readable output. It deliberately leaves production authority, LiteBans cutover and issue #43 acceptance unchanged.

The next logical moderation slice is the staff report queue/detail GUI and action workflow; it must be started in a separate branch after this feature is merged.
