# Configuration

The target configuration is modular, versioned, reloadable, and validated as
one immutable model. Current builds still use a more limited configuration
shape; do not assume every file below exists yet.

## Target layout

```text
plugins/EnthusiaStaff/
├── config.yml
├── storage.yml
├── messages.yml
├── discord.yml
├── website.yml
├── vanish.yml
├── staff-mode.yml
├── staff-tools.yml
├── inventory.yml
├── alts.yml
├── reports.yml
├── automod.yml
├── anticheat.yml
├── market.yml
├── reputation.yml
├── escalation.yml
├── migration.yml
├── integrations.yml
├── servers.yml
├── gui/
│   ├── punish-categories.yml
│   ├── punish-reasons.yml
│   ├── punish-review.yml
│   ├── punishment-history.yml
│   ├── remove-punishment.yml
│   ├── reports.yml
│   ├── report-details.yml
│   ├── player-inspector.yml
│   ├── staff-tools.yml
│   ├── cheat-testers.yml
│   └── alts.yml
└── punishments/
    ├── hate-harassment-safety.yml
    ├── spam-noise-language.yml
    ├── inappropriate-content.yml
    ├── politics-irl.yml
    ├── account-security.yml
    ├── complicity-evasion.yml
    ├── exploits.yml
    ├── market.yml
    ├── reputation.yml
    ├── mods-clients.yml
    ├── reports-tickets.yml
    └── other-extreme.yml
```

## Current limitation

The requirements matrix currently describes Paper configuration as monolithic
and incomplete, with `config.yml` and `reason-policies.yml` covering only part
of the target model. Required GUI and feature-specific files are not all
present.

Document the deployed files, not only the target tree.

## Secrets

Keep these outside Git and ordinary YAML where supported:

- MariaDB passwords
- TLS key/trust store passwords
- Network identity encryption/HMAC keys
- Discord webhook URLs
- Website bridge secrets
- Cloudflare, email, Turnstile, D1, R2, and Hyperdrive credentials
- Private provider API secrets

Use environment variables or an approved secret manager. Never place real
values in Wiki examples.

## Durations and IDs

Configurations should use:

```text
6h
21d
90d
permanent
```

Reason, family, ladder, GUI, server, and integration identifiers must be stable.
A display-name change must not silently create a new policy identity. Removed
IDs remain readable for history but unselectable. Renames require explicit
aliases.

## Reload

```text
/estaff reload
```

Target reload behavior:

1. Read into a temporary configuration tree.
2. Validate every file, version, path, reference, alias, ladder, GUI slot,
   duration, server, permission, and integration.
3. Reject the entire reload on any error.
4. Keep the current valid runtime model unchanged.
5. Atomically swap only a fully valid immutable model.
6. Preserve active sanctions, drafts, sessions, reports, locks, journals, and
   recovery work.

A reload must not partially apply some files.

## Restart-required settings

TLS material, server identity, database connection pools, provider
classloading, and other bootstrap settings may require restart. Verification
must say **RESTART REQUIRED** rather than pretending reload applied them.

## Operational modes

- `BOOTSTRAP`
- `DEGRADED`
- `SHADOW_MIGRATION`
- `ACTIVE`
- `MAINTENANCE`
- `READ_ONLY_FAILURE`

Mode changes are safety controls. Do not change them merely to make a command
available.

## Validation checklist

Before approving a config change:

- IDs are unique and stable
- Aliases resolve once
- Every reason has family, severity, ladder, decay, visibility, reportability,
  rank, automation eligibility, confiscation options, and alt inheritance
- Every GUI reference exists
- Slots do not conflict
- Durations parse
- Permission nodes are known
- Server scopes are unambiguous
- Secrets are absent from the diff
- Reload test rejects a deliberately invalid copy
- Current active sanctions remain interpretable
