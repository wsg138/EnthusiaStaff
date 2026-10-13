# Enthusia punishment escalation simulator

Read-only browser tool that uses the actual ReasonPolicyConfigurationLoader
and EscalationEngine from EnthusiaStaff. It has no Minecraft, Discord, MariaDB,
or other remote service client and cannot apply punishments.

## Build
Run: gradlew.bat :punishment-simulator:test :punishment-simulator:installDist
Run the installed bin/punishment-simulator.bat with the absolute path to a
reason-policies.yml policy snapshot as its sole argument. Requires Java 25+.

A terminal prints a random localhost URL and normally opens your browser.
The tool binds ONLY to 127.0.0.1. Close its terminal to stop it.

## Actual rules used
- Contributing, non-overturned prior offenses in the same family count.
- Each prior offense adds +1, or +2 if more severe than the current reason.
- A related punishment that ended within 30 days contributes a +1 bonus.
- One step of eligible prior contribution decays per full 90-day clean period
  since the latest related punishment ended. Individual contributions floor at 0.
- The effective ordinal is capped to the final configured ladder entry.

## Important limitations
The historical decay eligibility in the scenario is inferred from the
selected current snapshot. Actual historical cases may have an eligibility
stored from an older policy, which this simulator cannot reconstruct.
The simulated prior date is when that sanction ENDED, not when it was issued.

This tool does not simulate Policy v2, staff rank/approval gates, alt accounts,
real historical player data, case creation or actual sanction enforcement.
Policy snapshots are not auto-refreshed. No production writes occur.

The user-facing packaged Windows folder (with live and proposed policy
snapshots) is under Downloads/Enthusia-Punishment-Simulator.
