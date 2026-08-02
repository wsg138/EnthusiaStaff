# Report Configuration

EnthusiaStaff keeps report behavior and report inventory presentation in two
separate versioned files under the Paper plugin data directory:

```text
plugins/EnthusiaStaff/reports.yml
plugins/EnthusiaStaff/gui/reports.yml
```

The bundled files are copied only when absent. Existing operator edits are not
overwritten during startup.

## Policy settings

`reports.yml` controls:

- the ordinary reporter cooldown;
- the same-target cooldown;
- the duplicate merge window;
- the maximum number of open reports per reporter;
- the maximum number of results returned to a report queue;
- the recently-closed queue window;
- retained report-evidence duration;
- the bounded evidence-purge batch size.

Durations use the same finite duration syntax as moderation policy files, such as
`30s`, `2m`, `2h` or `7d`. Permanent values, zero values, negative values and
unsupported units are rejected. Query results are bounded from 1 to 100, and
purge batches are bounded from 1 to 1,000.

The shipped defaults preserve the prior behavior: two minutes between ordinary
reports, thirty minutes before reporting the same target again, a two-hour
same-reason duplicate window, five open reports, 100 queue results, seven days
for recently closed reports and retained evidence, and 500 cleanup records per
maintenance batch.

## GUI settings

`gui/reports.yml` controls:

- inventory size;
- queue content slots;
- action slots;
- queue, navigation, detail and review slots;
- item materials;
- queue, detail and confirmation titles;
- report inventory labels and explanatory messages.

Every required key must be present and unknown keys are rejected. Inventory size
must be a multiple of nine from 9 to 54. Slots must be inside the inventory,
content/action lists cannot contain duplicates, materials must be usable item
materials, and each screen is checked for overlapping interactive slots.

An already-open report inventory retains the immutable GUI snapshot used to
render it. A reload therefore cannot reinterpret a click in an old screen with a
new slot layout. Newly opened or refreshed screens use the latest validated
configuration.

## Reload behavior

Run:

```text
/estaff reload
```

The report policy and GUI files are parsed and fully validated before the
existing configuration reload coordinator is allowed to apply another runtime
change. When either report file is invalid:

- the reload is rejected;
- the previous report policy and GUI remain active;
- the ordinary configuration/reason-policy reload is not started;
- the command returns a path-aware validation detail;
- the sanitized rejection is written to the server log.

After every candidate succeeds, the report snapshot is replaced atomically.
Active persistence stores read the current policy through a supplier and capture
one immutable policy per transaction or query, so one operation cannot mix old
and new cooldown, limit or retention values. No database reconnect or Flyway
migration is required.

## Operational cautions

- Keep the `version` fields meaningful when changing either file.
- Do not raise limits merely to hide a queue or cleanup backlog; investigate the
  cause first.
- Reducing evidence retention affects future snapshot expiry and active client
  evidence reads/cleanup. Preserve evidence needed for an active investigation
  before changing retention under an approved privacy procedure.
- Reload does not deploy the plugin, change moderation authority, disable
  LiteBans or authorize production cutover.
