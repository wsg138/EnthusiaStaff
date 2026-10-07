# Policy v2 owner policy proposal

Tracking: #423  
Parent: #355  
Status: **Owner-approved direction by Lincoln on 2026-10-07 — still non-production until encoded, simulated, shadow-tested, and cut over separately**

This document converts the unresolved W0/W6 questions into a concrete starting position for owner review. It deliberately does not activate Policy v2, change the bundled disabled example policy, or alter Policy v1 authority.

## 1. Core policy principles

These principles should govern the final Policy v2 configuration.

1. **Classify conduct, not implementation trivia.** New exploit variants should normally fit an existing conduct type such as duplication, exploit abuse, malicious automation, or server disruption instead of requiring a new reason for every technique.
2. **Unknown conduct fails safely.** If the configured taxonomy genuinely cannot resolve an incident, staff selects Policy Gap / Unclassified Incident and the case routes to Admin/Founder review. Ordinary staff do not invent sanctions.
3. **Current facts and historical pattern are separate.** The current incident determines what happened and how serious this occurrence is. Related history changes the response, but unrelated history must not inflate it.
4. **Remedies are not punishment.** Removing duplicated items, deleting malicious content, closing a prohibited stall, or requiring a profile correction should be modeled separately from bans/mutes.
5. **Compliance conditions should usually end when compliance is restored.** A bad username, prohibited profile state, or active unapproved VPN can justify a restriction until corrected without pretending a fixed-duration ban is the natural response.
6. **Evasion is separate misconduct.** Circumventing a mute, access restriction, VPN condition, Market restriction, or other sanction is its own finding and may justify a stronger response.
7. **Evidence confidence is not severity.** If evidence is insufficient, the result should be no finding / further review, not a smaller punishment for a severe offense that staff only partly believes occurred.
8. **Permanent bans must come from explicit terminal policy.** They should never emerge accidentally from an accumulated scalar score.
9. **Public records are sanitized summaries.** Private evidence, staff notes, alt/IP signals, detection internals, and exploit details stay private.
10. **Policy changes are versioned.** A later policy version does not rewrite why an older case was decided under an earlier version.

## 2. Recommended answers to the unresolved owner decisions

### 2.1 Player-to-player scams / trade fraud

**Recommendation: ordinary in-game trade deception is allowed unless another explicit rule is violated.**

Enthusia already allows wilderness theft, raiding, and griefing. Treating every dishonest in-game trade as staff-enforced fraud would create an inconsistent “theft is allowed unless it happens through a trade window” rule.

Not protected by this allowance:

- impersonating staff or official server services;
- malicious/phishing links;
- real-money/external-value fraud;
- exploiting a technical bug in the trade/Market system;
- blackmail/extortion;
- reputation manipulation;
- lying to staff during an investigation.

Result: do not add a generic player-to-player scam offense.

### 2.2 Real-money trading / external transactions

**Recommendation: prohibit unauthorized real-money/external-value trading involving Enthusia assets or services.**

Prohibit exchanging in-game currency/items/services/accounts for:

- cash;
- cryptocurrency;
- gift cards;
- paid external goods/services;
- other things of real-world monetary value,

unless the transaction is through an explicitly owner-approved official server service.

Use a specific integrity offense, not the generic advertising rule. Staff should not arbitrate unrelated private transactions that do not involve Enthusia.

### 2.3 Ordinary alt accounts

**Recommendation: ordinary non-headless alternate accounts are allowed unless used to gain a prohibited advantage or bypass a restriction.**

Separate findings already cover the harmful uses:

- punishment evasion;
- extra Market stalls;
- reputation manipulation;
- headless/automation accounts;
- account sharing;
- identity-ban evasion;
- other explicit per-account limits.

This avoids turning “having an alt” into misconduct by itself.

### 2.4 Protected-area enforcement

**Recommendation: technical prevention/restoration first; punishment only for deliberate bypass or repeated interference.**

Spawn/Market protections should block or restore prohibited actions where possible. A single blocked attempt should not automatically become a punishment case.

Create a behavioral finding when evidence shows:

- intentional bypass of region protections;
- repeated interference after correction;
- exploiting a protection bug;
- deliberate damage/theft that succeeded despite protection.

This keeps wilderness griefing explicitly allowed while still protecting designated areas.

### 2.5 Malicious links and files

**Recommendation: intentional malware/phishing/credential theft is severe-safety, non-decaying, and normally permanent-ban territory after Admin review.**

Use the same severe policy class for a malicious file and a link intentionally directing users to malware, credential theft, token theft, or similar compromise.

Do not classify:

- an unrelated harmless link as malicious content;
- ordinary server advertising as malware;
- an accidentally broken/unsafe link as intentional malicious distribution without evidence.

Confirmed deliberate malicious distribution should align with the public zero-tolerance safety rule.

### 2.6 Credible threats and doxxing

**Recommendation: align Policy v2 with the current public zero-tolerance rule: confirmed credible threats and intentional doxxing result in permanent network bans.**

Requirements:

- Admin or higher final approval;
- strong evidence that the threat is actually credible or the disclosure is intentional doxxing;
- privacy-safe public reason;
- non-decaying history.

Careless personal-information exposure remains a separate lower offense and should not be silently upgraded into doxxing.

### 2.7 Hate/extremism decay

**Recommendation: ordinary slur/discriminatory history decays slowly; confirmed extremist advocacy/glorification and advocacy of hate violence is non-decaying.**

Suggested classes:

- general discriminatory expression: D2_STANDARD;
- slurs / targeted severe hate: D3_EXTENDED;
- extremist symbol: D3_EXTENDED unless used as explicit advocacy/intimidation;
- extremist advocacy/glorification or advocacy of genocide/hate violence: D4_NONDECAY.

Important distinction:

> Mentioning Hitler, discussing Hitler historically, or condemning Nazism is not the same conduct as praising Hitler, glorifying Nazism, endorsing genocide, recruiting for extremist ideology, or advocating hate-based violence.

The UI should ask structured context questions so “politics” cannot absorb actual extremist advocacy.

### 2.8 Non-English public chat

**Recommendation: pure non-English-public-chat violations are chat-only and capped at a 7-day mute. They never directly create a network ban.**

Suggested progression under the final resolver:

- correction notice / warning;
- short mute;
- 1-day mute;
- 3-day mute;
- maximum 7-day mute for the language rule itself.

Exceptions should include:

- emergency/safety communication;
- brief necessary translation;
- tickets/private staff communication where staff can support it;
- quoted evidence being supplied to staff.

If a player bypasses an active mute or uses accounts/VPNs to continue speaking, classify the bypass separately as punishment evasion. That offense may create a ban.

### 2.9 VPN escalation

**Recommendation: treat an active unapproved VPN as an access/compliance condition first, with a separate deliberate-evasion offense for repeated circumvention.**

Baseline:

1. detected unapproved VPN -> deny/restrict relevant access until VPN is disabled or approved;
2. no behavioral punishment merely for the first condition;
3. explicit notice is recorded;
4. deliberate repeated attempts to bypass the condition become `access.vpn-evasion`.

Recommended maximum evasion scale:

- warning / short restriction for first deliberate refusal;
- 1 day;
- 7 days;
- 30 days;
- maximum 90-day network ban for chronic deliberate VPN/access evasion.

Do not jump directly from “VPN detected” to a 90-day ban.

### 2.10 Profile enforcement

**Recommendation: split username, skin, and other profile surfaces and use “until corrected” access conditions as the primary response.**

For reliably detectable surfaces:

- inappropriate username -> cannot join until username changes;
- inappropriate skin -> access restricted until corrected where technically reliable;
- other server-controlled profile fields -> hide/restrict until corrected.

The compliance restriction ends when the offending state no longer exists.

A separate behavioral offense applies when someone deliberately re-applies prohibited content, repeatedly cycles profiles after correction, or bypasses the restriction.

### 2.11 Emergency reporting while report-restricted

**Recommendation: a report restriction must never eliminate a safety/emergency reporting path.**

Even a permanently report-restricted player should retain a narrowly scoped route for:

- credible threats;
- doxxing;
- grooming;
- illegal exploitative content;
- account compromise;
- urgent server-security incidents.

The emergency route may be rate-limited and reviewed for abuse. Abuse of the emergency route can create a separate report-abuse finding, but the route itself should remain available.

### 2.12 Reasonable staff instructions

**Recommendation: refusal is punishable only when the instruction is clearly connected to an active moderation/safety/server-operational need.**

A valid instruction should be:

- issued by staff with authority over the active matter;
- specific and understandable;
- relevant to a current incident/investigation;
- technically feasible;
- not a demand for unrelated personal information or off-topic conduct.

Players may decline optional investigative questions. That alone is not obstruction.

Punishable obstruction includes:

- destroying requested evidence;
- intentionally interfering with an active investigation;
- refusing a necessary in-game corrective action;
- deliberately lying about material facts;
- directing others to obstruct the investigation.

### 2.13 Bug-reporting window

**Recommendation: define “immediately” behaviorally, not as an arbitrary clock timer.**

On discovering a suspected exploit, the player must:

1. stop using it;
2. not intentionally reproduce it again except with explicit staff authorization;
3. not share it with other players;
4. not profit from it;
5. open a ticket as soon as reasonably possible in the same play session or before further use.

The key boundary is **intentional continued testing/use before reporting**, not whether the ticket arrived within exactly five or ten minutes.

### 2.14 Uncertain duplicated-item possession

**Recommendation: if staff cannot establish knowledge, confiscate/neutralize the duplicated assets as a remedy but do not create a behavioral punishment finding.**

This should be a remedy-only incident/state:

- remove clearly duplicated/invalid items;
- document provenance privately;
- no ban/mute;
- no behavioral-history contribution.

If later evidence establishes knowing possession, laundering, or participation in duplication, reclassify into the appropriate exploit/complicity finding.

### 2.15 Autoclickers

**Recommendation: explicitly separate approved non-combat automation from combat automation.**

Allowed outside combat:

- Enthusia Autoclicker;
- server-side autoclicker;
- approved external desktop autoclicker,
subject to any configured rate/safety restrictions.

Prohibited:

- any autoclicker used to gain combat advantage;
- modified/bypassed Enthusia Autoclicker restrictions;
- unauthorized autoclicker mods after the required switch warning.

The public rules should eventually be edited to explicitly state that the listed allowed autoclickers are not permission for combat automation.

### 2.16 Politics vs extremism

**Recommendation: retire “extreme politics” as a severity bucket.**

Use:

- `chat.sensitive-topic-public` for politics/controversial IRL discussion that is inappropriate for public chat;
- `hate.discriminatory-expression` for protected-class attacks;
- `hate.extremist-symbol` for symbols/imagery;
- `hate.extremist-advocacy` for endorsement, praise, glorification, recruitment, genocide advocacy, or hate-based violence.

Historical/factual discussion and condemnation should not become hate findings merely because Hitler/Nazism/extremism is mentioned.

### 2.17 Market compliance

**Recommendation: retire the generic selectable `market.compliance-failure` offense.**

Replace it with explicit facts/reasons such as:

- extra stall through alt;
- protected-storage abuse;
- stall obstruction / prohibited floor storage;
- deliberate overpriced-listing storage abuse;
- blacklist evasion;
- other specific Market rule that owners explicitly add later.

A generic compliance state may exist internally for remediation, but staff should not choose an undefined “failed compliance” punishment reason.

### 2.18 Legacy v1 mapping

**Recommendation: preserve every current v1 reason ID as a historical alias/migration mapping, but retire duplicated/structurally wrong IDs from new manual selection.**

Rules:

- original v1 reason ID remains in immutable audit;
- an unambiguous v1 reason maps to one canonical v2 offense for history purposes;
- many old variants may map to one v2 offense plus attributes (for example the four slur variants -> `hate.slur`);
- surface duplicates such as `reports.harassing-staff` map to the canonical harassment offense;
- `politics.extreme` does not automatically map to extremist advocacy without enough historical facts; ambiguous historical records should map conservatively to sensitive-topic history or remain history-only;
- Polar detection is a detection source, not a unique misconduct family.

### 2.19 Pre-v2 behavioral history carry-forward

**Recommendation: carry forward only unambiguous factual v1 findings; do not reset everyone to a blank history at cutover.**

Use a compatibility adapter rather than fabricating new Policy v2 cases.

Carry forward when:

- the v1 reason maps unambiguously to a canonical v2 offense;
- the original case was not overturned;
- incident date is known.

Do not invent missing v2 incident attributes.

Ambiguous old reasons should either:

- contribute only to a conservative broad history group; or
- contribute nothing automatically and remain visible to reviewers,

depending on what the final mapping can prove.

This recommendation requires a small cutover implementation follow-up because W6 intentionally did not make this owner decision.

### 2.20 Ancient fully-decayed recurrence effect

**Recommendation: change the current behavior. Ancient history should not slow future decay forever.**

The present engine counts earlier related findings toward recurrence even when their direct contribution has effectively decayed away. That means one very old minor incident can permanently lengthen the half-life of every later incident.

That is too sticky and does not match the intended model.

Recommended replacement: **two-layer fading memory**.

For each behavior domain:

- direct incident contribution decays using the normal offense half-life;
- a slower “pattern persistence” contribution also decays over time;
- repeated related incidents reinforce pattern persistence;
- pattern persistence increases effective half-life up to a configured cap;
- if the player stays clean long enough, pattern persistence also approaches zero.

Conceptually:

`direct = relationship * 2^(-age / directHalfLife)`

`pattern = relationship * 2^(-age / patternHalfLife)`

`effectiveHalfLifeMultiplier = 1 + min(maxIncrease, patternScale * sum(pattern))`

This preserves the desired behavior:

- repeated reoffending makes history decay slower;
- someone repeatedly offending after partial decay builds a longer-term pattern;
- unrelated offenses do not interact;
- a genuinely long clean period eventually clears the pattern instead of leaving a permanent invisible penalty.

Owner-approved starting defaults for simulation; numerical tuning may still be adjusted if simulator results expose bad behavior:

| Class | Direct half-life | Pattern half-life | Maximum effective half-life |
| --- | ---: | ---: | ---: |
| D1_SHORT | 30 days | 120 days | 2.0x |
| D2_STANDARD | 90 days | 270 days | 2.5x |
| D3_EXTENDED | 180 days | 540 days | 3.0x |
| D4_NONDECAY | non-decaying | non-decaying | n/a |

These values should be tuned in the simulator before owner approval.

## 3. Recommended response classes

Policy v2 should use four response classes instead of forcing every offense through the same ladder shape.

### A. Compliance condition

Examples:

- invalid username/skin;
- active unapproved VPN;
- Market stall currently out of compliance.

Default response:

- correct/remove/restrict until compliant;
- no behavioral-history finding merely because the condition existed;
- deliberate refusal/bypass is a separate behavioral offense.

### B. Behavioral communication/community offense

Examples:

- spam;
- public politics;
- harassment;
- slurs;
- abusive report behavior.

Default response:

- warning/mute/contact/report restriction as appropriate;
- network bans reserved for severe safety-linked behavior or evasion, not as the automatic final step of every chat rule.

### C. Integrity/fair-play offense

Examples:

- cheating;
- exploit abuse;
- prohibited duplication;
- evidence falsification;
- Market/reputation manipulation.

Default response:

- temporary network restriction/ban where appropriate;
- mandatory remediation/confiscation separately;
- longer decay than ordinary chat behavior.

### D. Severe safety/security offense

Examples:

- grooming;
- doxxing;
- credible real-world threat;
- blackmail/extortion;
- illegal exploitative content;
- intentional malware/phishing distribution;
- deliberate server-crash attempt;
- severe extremist/hate-violence advocacy where owner policy classifies it here.

Default response:

- explicit owner-configured terminal/minimum policy;
- Admin/Founder review;
- non-decaying history;
- no accidental scalar escalation into or out of permanent-ban status.

## 4. First representative outcome anchors

These are proposed scenario anchors for owner review and simulator testing. They are not yet production values.

| Scenario | Recommended baseline |
| --- | --- |
| Low-volume spam, first case, stops when told | warning |
| Sustained flooding after warning | 6h–1d mute |
| Non-English public chat, first correction | warning/correction |
| Repeated pure non-English violation | mute, maximum 7d under this offense |
| Public political discussion after staff direction to stop | 6h–1d mute depending persistence |
| Repeated inflammatory political disruption | 3d–7d mute |
| Historical discussion of Hitler/Nazism without endorsement | not extremist advocacy; only sensitive-topic policy if public-chat rule is violated |
| Praising/glorifying Hitler/Nazism | hate/extremist advocacy; substantially stronger than politics |
| Advocating genocide/hate-based violence | severe hate/safety review; long restriction/ban with explicit terminal policy |
| Inappropriate username | access restricted until username changes |
| Inappropriate skin | access/profile restriction until corrected where technically reliable |
| First unapproved VPN detection | access condition; no timed behavioral punishment |
| Repeated deliberate VPN bypass | escalating evasion; may reach 90d |
| X-ray with confirmed material gain | temporary network ban + confiscation |
| X-ray with recent related cheating | longer ban based on related decayed history |
| Unknown possession of duplicated items, knowledge unproven | confiscation/remedy only |
| Knowing duplicated-item laundering | exploit/complicity ban + confiscation |
| Deliberate major exploit abuse | substantial temporary ban + remediation; permanent only under explicit terminal conditions |
| Deliberate server-crash attempt | permanent ban, Admin review, non-decay |
| Confirmed doxxing | permanent ban, non-decay |
| Confirmed credible real-world threat | permanent ban, non-decay |
| Grooming | permanent ban, non-decay |
| Intentional malware/phishing distribution | permanent ban, non-decay |
| Appeal reduces 5d to 3d as leniency only | behavioral history unchanged |
| Appeal proves offense was misclassified | finding/history reclassified |
| Appeal fully overturns case | zero future history contribution + sanction/remedy cleanup |

## 5. Policy values still needing simulation before approval

Even if the recommendations above are accepted in principle, these numerical values should be tested rather than guessed directly into production:

- exact sanction options per offense;
- exact history relationship weights;
- exact D1/D2/D3 direct half-lives;
- exact pattern-persistence half-lives;
- pattern multiplier scale and caps;
- history thresholds that move a case between configured outcomes;
- bounded-discretion cases and minimum rank;
- exact first/repeat cheating anchors;
- hate/slur mute durations;
- extremist-advocacy sanction package;
- report/Market/reputation restriction durations;
- VPN-evasion progression;
- profile-repeat violation consequences.

## 6. Required implementation follow-ups if this proposal is approved

Most decisions fit the merged infrastructure. Two recommendations intentionally require additional implementation:

1. **Fading recurrence/pattern persistence.** Replace the current forever-counted recurrence multiplier with a separately decaying pattern signal.
2. **Legacy v1 history carry-forward adapter.** At cutover, map unambiguous pre-v2 findings into resolver history without fabricating Policy v2 cases or missing incident attributes.

Neither should be implemented as production authority until owners approve the corresponding policy decision.

## 7. Approval state and next steps

Lincoln approved the substantive recommendations in this document on 2026-10-07. The policy remains non-production because implementation/simulation/shadow validation still precede authority cutover.

Next steps:

1. implement fading pattern memory as approved in section 2.20;
2. implement the legacy-v1 carry-forward adapter described in section 2.19;
3. run the simulator matrix over D1/D2/D3 values and recurrence behavior;
4. tune numerical values only when the simulator exposes surprising behavior;
5. encode the first real versioned Policy v2 configuration;
6. run production shadow mode before any authority cutover.
