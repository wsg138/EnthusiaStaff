# EnthusiaStaff — Hybrid Punishment Navigation
**Firm design baseline HPN-1 · 2026-10-13**

## 0. Decision

**Keep precise reason policies; simplify discovery and choice.**
This is a GUI/presentation redesign, not a replacement escalation engine.
One policy evaluation remains authoritative for in-game commands, GUI, private
web actions and Discord approvals. Keep stable reason IDs, family IDs, severity,
duration ladders, historical snapshots, and all permission/case-writing services.

Why: broad-only ladders conflate materially different violations. Thousands of
choices are not needed either. Six sections and search retain precision and
reduce selection time.

## 1. Baseline and scope

Based on main commit 38715182: Paper GUI already exposes 11 grouped categories,
85 configured reasons, and a system-only Polar template excluded from menus.
Production SMP holds reason-policies.yml version 2026-09-26.1; repository default
is newer 2026-10-10.1. Do NOT replace production policy when updating GUI.

**In scope**: 6 navigation groups, global search, clear reason distinctions,
stable pagination, consistent command filters, no changes to enforcement.
**Out of scope**: new offense IDs, merging or renaming reason IDs, changes to
sanction ladders or escalation, permissions, case schema, automatic classifier,
Policy v2 activation, live deployment or Polar/LiteBans provider changes.

## 2. Six primary categories

| Key | Display | Existing families |
| --- | --- | --- |
| chat | Chat & Communication | chat, spam, language, politics, hate, harassment |
| gameplay | Cheating & Gameplay | cheating, exploit, mechanics, complicity |
| safety | Safety & Privacy | safety, privacy |
| accounts | Accounts & Evasion | account, evasion, identity |
| community | Community & Conduct | advertising, reports, staff, dishonesty, content |
| economy | Economy & Reputation | market, reputation |

A seventh Other tile appears only when a new, unrecognized family exists.
This must be deterministic: any manually available reason belongs to exactly
one group. System-only templates remain filtered, not exposed under Other.
Grouping is display-only; NEVER change ReasonPolicy.family in storage or engine.

## 3. Exact flow and screen design

**Entry**: /punish player -> player overview -> six category icons +
global search -> paginated exact reason list -> existing review -> confirm.
Filtered /ban player, /mute player, /warn player and /kick player follow the
same flow but show only allowed reason/sanction types. Resume existing draft
continues directly to review, with the original authoritative rechecks.

**Categories — 54-slot inventory**
- Header retains player identity, currently active sanctions, relevant history,
  reports and page context.
- Six centered high-contrast icons. Each shows 2–3 examples and available count.
- Search all reasons at slot 50 (spare navigation control).
- Footer retains History at 46 and Close at 51.
- No empty clickable group. Category group is not a punishment reason.

**Reasons — 54-slot inventory**
- Up to 36 reasons/page (slots 9–44), sorted display name then stable ID.
- Every entry displays precise reason, severity, required rank, first-step
  example (not guaranteed current), and representative configured examples.
- Header reminds player of current category, number of results.
- Footer Back, History, Search (slot 50), Previous and Next, Close.
- On selecting a reason, call existing prepare workflow; no GUI enforcement.

**Search**
- Slot 50 from categories or reason list starts a private chat-input capture.
- Search all currently permitted manual reasons, regardless of categories.
- Match normalized name, ID, family and configured examples.
- Exact name/ID first, prefix second, substring last; deterministic tie-break.
- Input 2–64 characters after whitespace trimming. 'cancel' returns to the
  origin view; 60-second expiration/quit clears it without broadcasting.
- 36 results per page, with category/family shown in the result tooltip.
- Search never touches database, logs private input or changes policy.
- Selection must recheck current permissions and target hierarchy.

**Review**
- Preserve existing review slots and authoritative re-evaluation.
- Show selected exact ID, source policy version, related history contribution,
  severity, decay, selected ordinal and full sanctions/ladder.
- Keep saved 24h drafts, case visibility, private staff note and confirmation.
- Never treat an initial ladder step as today's computed penalty.
- No enforcement until existing service transaction succeeds.

## 4. Safety and policy accuracy

1. No changes to EscalationEngine, Policy v2 or policy YAML.
2. Every active/manual reason in the exact YAML has one and only one route.
3. Search uses PunishmentGuiCatalog's authoritative filters: rank, request role,
   sanction type and system-only exclusions. No privilege visibility leak.
4. Every selected reason is revalidated at action time by existing service.
5. GUI never writes cases directly; confirmed result is idempotent.
6. Public chat must not disclose search query or staff internal notes.
7. Never allow auto-classification/heuristics to select or issue punishment.
8. Preserve /punish resume and current history/privacy controls.
9. Ensure Bedrock/Geyser compatibility; avoid anvil-only GUI or NMS.
10. Unknown future families map to Other; removed or alias-only reasons
    remain in history and not in manual picker.
11. Policy v2 workflow remains separately gated: a navigation change cannot
    silently switch policy engines or change sanction semantics.

## 5. Acceptance test plan (do not merge/ship without evidence)

**Navigation/policy integrity**
- Load both live 2026-09-26.1 and bundled 2026-10-10.1 policy snapshots
  without altering either. Enumerate every manual reason and assert exactly
  one mapped group and global search result for its ID.
- Unknown future family goes to Other. Alias and removed reason stay
  browse-disabled; Polar automated template stays hidden.
- Check helper, mod, admin, developer and founder route visibility in
  /punish, /ban, /mute and /warn. Developer remains request-only.
- Empty filters have clear fallback, no null/inventory errors; 0/1/36/37/85
  entries and multiple pages have correct navigation.
- Minecraft Staff Mode and target hierarchy remain service-authorized
  regardless of filter, stale clicks, reload, logout or rank changes.
- Existing case/history private data, visibility toggle and saved draft
  remain unchanged.

**Search**
- Exact ID/name, prefix, substring, typo/no-result, overlapping names,
  examples/families, mixed case, whitespace, bad control chars, length limits.
- Ensure intercept + cancellation: never broadcast the search text.
- Concurrent viewers, close/quit/timeout and reentry must not leak state;
  old search response must not open a later target.
- Search result selecting a denied/revoked/changed reason must fail closed.

**Escalation parity**
- Compare exact reason selected via old category ID, new category ID,
  global search and filtered command: all must generate the same assessment
  for identical policy, actor, target and history.
- Use first offense, repeat within 30 days, 90/180-day decay, higher-severity
  same-family prior, unrelated prior, overturned record and combined sanctions.
- No changes to actual stored sanction type, duration, case ID, or actor.
- Separate offline simulator for tabletop ladder experiments; never use
  production player data to try destructive flows.

**Production rollout**
1. Merge navigation-only PR after full Paper tests, Codacy zero new issues
   and code review; do not package into previously tested live candidate.
2. Build paired release artifacts only if separate StaffBot protocol changes
   are present; otherwise changed Paper JAR alone suffices for GUI.
3. Verify exact policy file on the live server is preserved, and confirm
   current runtime remains ACTIVE. No premature Policy v2 cutover.
4. Deploy Paper on a normal operator-scheduled restart, not via hot reload.
5. Compare offline policy recommendations before and after for each sample
   exact reason. Test /punish, filtered commands, first/last page and search
   on authorized disposable staff account without committing a punishment.
6. Release fallback is prior Paper JAR plus unchanged YAML. StaffBot, Velocity
   and database must not require migration for this navigation-only release.

## 6. Delivery slices and sign-off gates

| Slice | Work | Completion test |
| --- | --- | --- |
| HPN-1A | This specification and six category mappings | All catalog tests plus exhaustive one-category policy test |
| HPN-1B | Global private search prompt and sorted results | Unit, concurrency and live-safe staff UI acceptance |
| HPN-1C | Clear distinctions and consistent review preview | UX and sanction-equality regression tests |
| HPN-1D | Rollout checklist, screenshots, staging acceptance | Owner accepts with no changes to policy behavior |
| Later | Optional favorites/recent reasons, fine-grained policy redesign | Separate owner policy approval |

### Owner-facing confirmation choices

- **Fixed**: Six navigation groups with searchable exact reasons; per-reason
  ladders and cross-offense families stay as-is during GUI rollout.
- **Fixed**: No grouping of fundamentally different offenses into one sanction.
- **Open for later**, not a release blocker: whether individual reason
  duplication deserves collapsing; any merge requires separate policy
  version, aliases, history migration review, and independently signed
  punishment-policy acceptance. Do not combine with this GUI update.

## 7. Wireframe

    /punish Steve   [Target summary | History]
     +-------------------------------+
     | Chat & Communication          |
     | Cheating & Gameplay           |
     | Safety & Privacy              |
     | Accounts & Evasion            |
     | Community & Conduct           |
     | Economy & Reputation          |
     |                               |
     | [History] [Search] [Close]    |
     +-------------------------------+
                   ↓
     Chat & Communication — 19 reasons*
     [Spam...] [Toxicity...] [Slurs...]
     [Targeted harassment...] [...]
     [Prev] [Back] [Search] [Next]
                   ↓
     Exact reason: targeted harassment
     Severity: 25   Policy: 2026-09-26.1*
     Related history: ...   Escalation: ...
     Calculated step: ...
     Full ladder: ...
     [Public/Private] [Note] [Confirm]

*Counts and policy versions are illustrative and will be calculated at runtime
from the active, rank-filtered snapshot. The UI must never hardcode them.

## 8. Definition of done

Not done merely because GUI compiles. Done means a stable six-group taxonomy,
private search, all relevant permissions and history tests, accessible
Minecraft + Bedrock navigation, positive and negative policy-parity tests,
Codacy/CI green, in-game acceptance evidence, rollback proof and no unexpected
production config changes. Keep old GUI rollback independent of policy changes.
