# Incident Playbooks

These playbooks give staff a consistent first response. They do not override
current production tooling, rank limits, or recovery procedures.

## Suspected cheating

1. Claim the report or open an investigation record.
2. Spectate in vanish.
3. Capture point-in-time client evidence with `/client <player>`.
4. Preserve relevant anticheat metadata and direct observations.
5. Use freeze only when necessary to prevent evidence loss or evasion.
6. Do not treat spoofable client brand or one unapproved signal as proof.
7. Punish through the exact configured reason and ladder when evidence is
   sufficient.
8. Close the report with evidence and outcome.

## Duplication exploit

1. Stop active exploitation without destroying evidence.
2. Freeze involved accounts if necessary.
3. Record item types, quantities, locations, inventories, Ender contents,
   containers, balances, market state, server, and timestamps.
4. Link related accounts and reports.
5. Use case-linked confiscation; do not manually delete items.
6. Preserve the exploit method privately.
7. Escalate a live economy threat to Admin/Founder.
8. Consider maintenance or whitelist only through approved incident authority.
9. Do not promise whole-server rollback; it is outside EnthusiaStaff scope.

## Suspected compromised account

1. Distinguish compromise from voluntary account sharing or ban evasion.
2. Preserve login/session, name, client, and network-identity evidence.
3. Restrict immediate harm with the least destructive control.
4. Do not expose network information to the claimant.
5. Escalate identity decisions and account restoration policy.
6. Link sanctions and appeals to the correct UUID.
7. Avoid punishing unrelated household accounts without evidence.

## Ban or mute evasion

1. Review the original active sanction and exact remaining duration.
2. Review alt confidence and exceptions.
3. Confirm restart/maintenance suppression.
4. Allow automatic inheritance only at the configured confidence/state.
5. Apply a separate evasion reason only when intent is established.
6. Link every inherited sanction to the original case.

## Harassment or slur report

1. Preserve the complete relevant chat context.
2. Verify whether the statement was targeted, general, quoted, abbreviated, or
   part of an allowed context under policy.
3. Use the exact reason ID rather than a generic label.
4. Do not use broad fuzzy matching as proof.
5. Keep private-message evidence out of Discord and public case details.
6. Apply the configured ladder and record why the exact variant matches.

## Market or economy abuse

1. Preserve stall, owner, transaction, item, balance, and time evidence.
2. Block further destructive market/economy action only through provider APIs.
3. Use case-linked confiscation for items and the Currency moderation API for
   funds.
4. Do not edit provider databases directly.
5. If a provider result is uncertain, quarantine instead of retrying manually.
6. Observe the configured human review period for market compliance actions.

## Inventory operation stuck

1. Stop all edits for the target and scope.
2. Record operation, patch, profile, case, fence, lease, backend, and checksums.
3. Do not reopen the inventory editor.
4. Do not clear rows or locks manually.
5. Follow [[Recovery and Troubleshooting]].

## Database or network outage

1. Check `/estaff status`.
2. Stop new destructive work.
3. Keep exactly one punishment authority.
4. Preserve safe reads and evidence collection where supported.
5. Record the outage start and affected operation IDs.
6. Do not interpret delivery delay as application failure or reapply sanctions.

## Incorrect punishment

1. Identify the exact case and sanction.
2. Preserve the original evidence and actor record.
3. Use end, revoke, reduce, or overturn according to what is actually wrong.
4. State the correction reason clearly.
5. Do not erase history or create a second conflicting punishment.
6. Follow the approval path required by rank.

## Staff-state restoration failure

1. Keep the staff member out of normal gameplay.
2. Preserve the original durable snapshot and current staff state.
3. Do not create a replacement snapshot.
4. Record session ID, backend, checksums, and reconnect/restart history.
5. Escalate to owner recovery.
