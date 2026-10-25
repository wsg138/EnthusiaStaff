# Staff punishment GUI/UX — Java and Bedrock acceptance checklist
2026-10-15 · Worker A · source branch `feat/staff-punishment-gui-ux-worker-a-20261015`

## Scope and honest execution status
- Local source review and contract tests: performed; see PR checks for latest results.
- In-game Java client checks: **NOT EXECUTED** in this worker.
- In-game Geyser/Bedrock checks: **NOT EXECUTED** in this worker.
- Live SMP: **NOT TOUCHED**. This checklist requires an isolated test server and test accounts.
- Guided enforcement is disabled by default; do not change any production configuration.
- PR #597 is an experimental draft, PR #612 carries separate policy safety changes, and owner-approved policy calibration remains a release gate.

## Setup
1. Start an isolated Paper staging server with a **staging-only** build from the target commit; do not install it on live SMP.
2. Use separate test accounts: ordinary staff rank, rank that may directly sanction, rank needing approval, authorized approver, unauthorized user. Use expendable player identities and staging-only data.
3. Enable `guided-punishment.enabled: true` **only in the isolated staging config**; capture the exact tested commit, server version, Geyser/Floodgate versions, permissions and console logs. Repeat baseline checks with the switch `false`.
4. Use real Java mouse input and a real Bedrock client through Geyser, not inferred inventories or invented screenshots. If a client cannot be connected, mark that row **BLOCKED**, not PASS.
5. For dangerous cases and denied permissions, do not execute real irreversible sanctions; use isolated mock targets/test fixtures. Assert that draft, recheck and approval paths retain their existing authority protections.

## Runtime acceptance matrix
Record **PASS / FAIL / BLOCKED**, client (Java or Bedrock), exact version, screenshot or recording identifier and observations for **each** item. Repeat all key rows with both clients.

| ID | Screen / path | Action and expected behavior | Java | Bedrock |
| --- | --- | --- | --- | --- |
| A01 | `/punish` with no target | Open player picker; player heads select the intended test player; Refresh/Close and pages work | NOT RUN | NOT RUN |
| A02 | Category picker, guided OFF | Distinct category materials, centered grid, bold nonitalic headings; clicking each available category browses exact reasons, not a questionnaire | NOT RUN | NOT RUN |
| A03 | Category picker, guided ON | Six primary category items are legible; selecting a category opens the matching guided interview; Search still finds exact reasons | NOT RUN | NOT RUN |
| A04 | Guided first question | Clear prompt and unique selectable response colors; Back returns to categories **only if no answers yet** | NOT RUN | NOT RUN |
| A05 | Guided after first answer | Back shows the very first question again, with no surprise return to categories; changing answer clears dependent responses | NOT RUN | NOT RUN |
| A06 | Guided remaining branches | 3–5 relevant steps in normal paths; warning choices: ignored, stopped, no warning given and unknown; no mandatory evidence question | NOT RUN | NOT RUN |
| A07 | Guided result, supported | Read-only incident details and human-readable factors; Conservative/Balanced/Stronger choice highlights only the selected option; Calculate opens policy review, never executes directly | NOT RUN | NOT RUN |
| A08 | Guided result, unsupported/sensitive | Review-only explanation appears; no green Calculate action; Expert Reasons may be used only when authorized | NOT RUN | NOT RUN |
| A09 | Expert fallback | Every visible authorized fallback opens filtered exact reasons; if none are authorized, fallback is visibly unavailable | NOT RUN | NOT RUN |
| A10 | Search and reason browser | Case-insensitive search, search cancellation, result pagination, reason selection, Back and Close work; no decorative pane executes an action | NOT RUN | NOT RUN |
| A11 | Final review, normal | Proposed sanction **type + exact duration** are clearly distinguishable from an applied sanction; history, required rank, visibility, private note and policy version are visible | NOT RUN | NOT RUN |
| A12 | Final review, adjusted | Conservative/Stronger adjustment without rationale shows a blocked gray submission item and a highlighted note requirement; saving a reason unlocks existing confirmation path | NOT RUN | NOT RUN |
| A13 | Review Back / resume | Back restores guided answers in the same active session; after restart/resume without that session, Back leads to exact reasons; no stale cached action executes | NOT RUN | NOT RUN |
| A14 | Confirmation / approval | Recheck occurs before action; test direct rank goes through existing confirmation; lower rank creates a pending approval request; unauthorized rank cannot act | NOT RUN | NOT RUN |
| A15 | Approval queue | Pagination and Refresh work; frozen proposal displays readable reason, type/duration, target, requester and approval rank | NOT RUN | NOT RUN |
| A16 | Approval review and denial | Approve shows target and exact proposed sanction, acts only with a valid claim; denial presets and custom command path produce audited results | NOT RUN | NOT RUN |
| A17 | History | Complete/empty/unavailable history modes, page controls and return path work; private events remain hidden from unauthorized viewers | NOT RUN | NOT RUN |
| A18 | Close/reopen | Closing draft review preserves resumable draft; quitting while in questionnaire leaves no stale click action or cross-target session | NOT RUN | NOT RUN |
| A19 | Bedrock-specific | Verify touch selection, no unintended double-taps, no missing lore, scroll/hover equivalent, colored materials, nonitalic bold names and 54-slot layout | N/A | NOT RUN |
| A20 | Policy v2 shadow path | Category, offense, configured questions, Policy Gap, result, review and Shadow Record controls remain visually distinct from live application | NOT RUN | NOT RUN |

## Fail-safe observations to capture
- Inventory titles and upper/lower-inventory click behavior, including shift-click, hotbar swap and rapid double-click.
- Server response to stale GUI after policy reload, permission changes, expiry, player disconnect and target logout.
- Whether a read-only object suggests it can be clicked, whether a visible button is inert, and whether any unexpected slot overlaps a control.
- Captures of six-category view, guided warning options, revision of first answer, calculated draft review, locked rationale, approval request and Bedrock equivalents. **Never invent screenshots.**
- Client-specific layout defects: report exact slots, materials, locale and steps to reproduce.
- Approval/reason/rank safety failures go to the lead worker immediately; do not modify underlying policy or bypass gates in this UX branch.

## Release handoff
Automated Java tests are useful but do not substitute for real Java and Geyser client proof. Keep PR stacked on #597, not main. Before production discussion, the lead must integrate #612, approve policy thresholds and complete this matrix on isolated staging. Neither this worktree nor a built JAR is authorization to deploy.
