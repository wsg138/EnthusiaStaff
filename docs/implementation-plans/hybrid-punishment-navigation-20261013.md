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

## 9. GUI visual system (owner priority: readability and quality)

Apply the same restrained design to the legacy manual punish screens, saved
draft review, staff punishment-request queue/review/denial and shadow Policy v2
screens. All navigation is in 9x6 or 9x3 vanilla inventories, without NMS,
client resource packs, shaders, external UI plugins or purchases.

### Visual tokens and spacing
- Base fill: BLACK_STAINED_GLASS_PANE; header and footer: GRAY_STAINED_GLASS_PANE.
- Cyan accent only for key navigation/status; keep color use deliberate.
  Green is submission/safe, gold signals review/recency, red signals danger,
  denial or exit. Decor is noninteractive; no fake buttons.
- Minecraft's default italic for custom item text is explicitly disabled on
  every item name and lore line. Item names are bold; supporting lines are not.
- Header: concise workflow guidance at slot 0, player summary at 4, history
  at 2, active sanctions at 6, reports at 8. Prominent player context remains
  visible at each stage. Two cyan accent dividers at header slots 1 and 7.
- Category browse: six prominent icons in a centered 3x2 layout (20,22,24,
  29,31,33), an explanatory guide above at slot 13. Unmapped Other appears
  beneath the main grid only when a family is unknown (40).
- Reason browse/search: severity communicated by book/paper type and name color,
  not a full grid of orange/red/yellow concrete. One clear reason name, severity
  and rank, first configured step labelled **without history**, optional
  example, exact ID and click guidance. Actual result remains on review.
- Footer: History 46, Back 47, a noninteractive page indicator 48, primary
  action 49 (where applicable), Search 50, Close 51, Previous 45/Next 53.
  Never move action IDs without changing click-controller tests.
- Review: reason 10, prior offenses 12, recommended outcome 14, draft/policy
  16. Ladder steps 18–35; selected step is clearly highlighted, older steps
  muted to light gray, later steps gray (permanent escalation red).
  Case visibility 37, private note 39, relevant history 41, authority 43.
  Submission at 49, Save & Close at 51. Existing confirmation semantics stay.
- Request review: 27-slot, dark body, gray top/bottom frames, prominent
  green Approve and red Deny, unchanged clicks and audited request leases.
- Policy v2: same frame and non-italic text, but its SHADOW marker and
  safe review-only Policy Gap must remain conspicuous.

### Copy and interaction
- Avoid long multi-line prose and avoid visual clutter in hover cards.
- Do not convey severity by color alone: text labels and numeric severity
  are always provided. Red is never used merely as a decorative accent.
- Every clickable action has a verb; every passive footer item clearly
  identifies itself as information only. Player UUIDs, reason IDs, and policy
  versions remain available for auditing.
- Different roles must see the same visual framework with rank-filtered options.
- Every GUI must remain usable at 54/27 inventory slots, including Java,
  Bedrock/Geyser clients and narrow screens. Manual acceptance must confirm
  this; unit tests cannot prove actual client rendering.

### Explicit acceptance gate
- In-game capture of target, six categories, densely populated reasons,
  search, no-match search, selected review with multi-step ladder, history,
  approval queue/review/denial, and Policy v2 shadow menu.
- Validate semantic contrast under default resource pack (no custom pack),
  screen reader/hover text usability, Bedrock compatibility and no lore clipping.
- No visual change may introduce a new server restart requirement beyond
  installing the updated Paper JAR at the next normal SMP restart.

## 10. Follow-up: distinct category icons and useful lore (2026-10-13)

Every displayed category has a distinct material:
- Chat & Communication: WRITABLE_BOOK
- Cheating & Gameplay: DIAMOND_SWORD
- Safety & Privacy: SHIELD
- Accounts & Evasion: ENDER_EYE
- Community & Conduct: LECTERN
- Economy & Reputation: GOLD_INGOT
- Other (only if needed): MAP

Category hover details now show the category description, a bold category-color
count of available exact reasons, at most two representative exact examples, an
optional count of recent related cases, and a clear yellow browse action.
There are no decorative generic instructions or fixed example counts. Every
hover line disables Minecraft's default italic styling; bold highlights only
key items instead of applying a single weight/color to all information.
The existing catalog regression asserts unique category materials.

## 11. Follow-up: staff tool handling and moving-player follow

**Container opening**: Holding a staff tool no longer intercepts right-click
on a real container. Vanilla open remains subject to the rank rules: Helpers
can view ordinary containers but cannot edit container contents; Mod and Dev
use their existing audited container permissions. Ender chest rank restrictions
remain unchanged.

**Staff tool rearrangement**: Helpers, Mods and Developers can pick up, swap,
and place their own **current-session** tagged tools in personal inventory slots,
including reordering the hotbar. Only live owner/session/material/rank-matched
items qualify. Any normal tool execution still validates those fields.
No container, crafting/armor/offhand storage, throwing, shift-transfer or
creative cloning is authorized. Unknown/stale tokens still fail closed. Staff
tools in inventory storage can be moved back to the hotbar; they execute only
when held in a hotbar slot. Default tool-slot settings remain starting layout,
not hardcoded usage permissions. Leaving staff mode restores the original
snapshotted inventory via the existing durable recovery pipeline.

**Moving-player follow**: Staff already in Spectator mode retain the native
setSpectatorTarget path. Staff in Creative/Survival use a 5-tick follow refresh
on the player/entity schedulers and asynchronous teleport only when the target
has moved more than 1.5 blocks. The follow keeps the current game mode,
requires permission/duty for each refresh, and ends safely if the target
disconnects, changes identity, becomes vanished/exempt, or teleport fails.
Right-click air while holding the Follow tool stops a non-spectator follow.
Player-target coordinates are read on that target's owning scheduler and
teleports are invoked on the staff actor's owning scheduler.

Unit regressions cover allowed personal movement, forbidden container/clone/
offhand movements, default-hotbar reassignment, HELPER view-only container
behavior, continued following through multiple player moves, vanish,
disconnection and authorization loss. Live Paper gameplay and creative/Bedrock
inventory screenshots still required before deployment.
