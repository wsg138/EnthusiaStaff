# Policy v2 publication and operational shadow mode

Policy v2 remains non-authoritative. Policy v1 continues to create cases and issue/enforce sanctions. This runtime only publishes immutable Policy v2 snapshots and, when explicitly configured as `shadow`, exposes the W3A manual review flow that writes W2 shadow evaluations.

## Configuration contract

Paper owns `plugins/EnthusiaStaff/policy-v2.yml`. The bundled file is deliberately `mode: disabled` and contains only an **EXAMPLE / NOT OWNER APPROVED** review-only snapshot. It does not supply real punishment durations, thresholds, relationship weights, remedies, or bounded-discretion choices.

The root contract is:

- `schema-version: 1`
- `mode: disabled|shadow`
- `active-version: <version>`
- `versions: [...]`

Every entry in `versions` is converted completely into the W1 `PolicySnapshot` domain model before publication. Unknown fields, invalid enum values, invalid attribute shapes, unknown history relationships, overlapping rules, invalid sanction shapes, and all other W1 validation failures reject the candidate. No partially parsed snapshot is published.

Each snapshot contains `version` and `offenses`. An offense contains `offense-id`, `display-name`, `navigation-group-id`, `attributes`, `history`, and `resolution-rules`. Attribute definitions use `attribute-id`; rules use `rule-id`; remedies use `remedy-id`. Attribute kinds are `boolean`, `integer`, `enum`, and `text`. History decay is `non-decaying` or `exponential`. Exponential decay requires `half-life`, `repeat-half-life-increase-per-prior`, and `maximum-half-life-multiplier`, and may specify a separate `pattern-half-life`. When `pattern-half-life` is omitted for a legacy exponential policy it defaults to `half-life`; new owner policy should set it explicitly.

Rule conditions contain explicit `attributes` accepted-value arrays and a `history` window with `minimum-inclusive` and optional `maximum-exclusive`. Actions support `exact`, `exact-with-approval`, `bounded`, `remedy-only`, and `requires-review`. Exact and bounded sanctions use the existing sanction types and `instant`, `permanent`, or bounded duration strings accepted by the shared duration parser. Bounded actions require `allowed-options` and `minimum-rank`. `exact-with-approval` carries fixed sanctions plus `minimum-rank`; the result is deterministic, but only that rank or higher may direct-confirm it. `remedy-only` actions carry no sanctions and use the rule's remedies as the deterministic response. Review-only actions require `reason-code`. Remedies use W1 remedy IDs, types, and descriptions.

Policy versions are append-only identifiers. A reload may omit an old version from the candidate file without deleting the in-process retained copy, but production policy files should retain every published version so restart replay remains deterministic. Reusing a published version with different content is a hard collision and is rejected. Reusing the same version with byte-semantically equivalent parsed content is idempotent.

## Atomic publication and reload

The loader constructs and validates the whole candidate first. The publisher then merges it with retained historical versions and performs one compare-and-set publication. Concurrent reloads cannot interleave individual offenses or versions. If a load, validation, or collision check fails, the prior publication remains active.

`/estaff reload` reloads Policy v2 only after the normal Policy v1/configuration reload succeeds. A Policy v2 rejection does not roll back or change Policy v1; it leaves the last-known-good v2 publication active and reports the rejection through the server log and runtime health issue `policy-v2`. Successful publication clears that issue and logs mode, active version, and retained-version count.

## Modes

`disabled` is the default. The Policy v2 manual workflow is not tab-advertised or dispatchable from `/estaff`, and the GUI controller fences already-open screens if a reload disables shadow mode.

`shadow` exposes `/estaff policyv2 <player>` to staff who already possess the normal punishment permission and current active-duty authority. The workflow can resolve the W1 recommendation and persist a W2 `recordShadowEvaluation` record. Its storage adapter has no path to create a Policy v2 case, issue a sanction, mutate a sanction, or enforce a remedy. The GUI labels the result as shadow-only and revalidates the current snapshot, history, and authority before recording.

There is intentionally no authoritative/cutover Policy v2 mode in W5B. Enabling `shadow` in production is an owner/deployment action outside this PR.
