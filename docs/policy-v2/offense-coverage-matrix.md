# Policy v2 W0 — Offense Coverage Audit and Owner Policy Matrix

Tracking: #362  
Parent program: #355  
Audit basis: live main at 514fd68ba2fe864b2c83554bbeeb8f8899fc107c on 2026-10-06. The policy/rule sources audited before the intervening #354 merge were rechecked and were unchanged by that merge.

## Purpose and status

This document is the Policy v2 content-design input. It is intentionally not an implementation or production-cutover document and it does not finalize punishment durations.

The audit reconciles:

- all 85 active reasons in paper/src/main/resources/reason-policies.yml, policy version 2026-09-26.1;
- the current public server rules in components/enthusia-site/public/rules.html;
- the current GUI category grouping;
- all remedy/restriction types currently expressible by Policy v1;
- alias/removed-reason compatibility support and historical policy metadata;
- likely missing moderation situations and duplicate/overlapping reasons.

### Hard product decisions carried forward

- Griefing is allowed in the wilderness and is not an offense category.
- AI classification/sentencing is out of scope.
- Unseen exploit implementations belong in broad conduct classes such as prohibited duplication, bug abuse, exploit abuse, and server disruption.
- Truly novel/unclassifiable conduct routes to Policy Gap / Unclassified Incident. Ordinary staff must not invent sanctions for it.
- Ordinary political or sensitive-topic discussion is separate from hate/extremist endorsement, glorification, or advocacy.
- Non-English public chat itself must never directly produce a network ban. Mute evasion is a separate offense.
- VPN/access compliance is separate from deliberate evasion/refusal.
- Username, skin, and other profile violations are separate findings and should support restricted-until-corrected remedies where technically appropriate.
- Detection source is not an offense. Likewise, an enforcement mechanism is not an offense.

## Source audit

### Current Policy v1 catalog

Current main contains 85 active reasons. The root supports aliases and removed-reasons, but the shipped policy has neither section populated. The historical merge that introduced alias/removed-reason support also had no live compatibility entries. Test-only examples such as chat.old-harassment, chat.retired-abuse, chat.legacy-abuse, and chat.spam are fixtures, not configured production aliases or removed reasons.

Therefore W0 found no configured legacy alias that must remain selectable. Policy v2 should still preserve the compatibility mechanism because future migration from the 85 current IDs will require aliases or removed-reason metadata.

### Existing remedy / restriction vocabulary

Policy v1 currently emits these effect types:

- WARNING
- MUTE
- KICK
- NETWORK_BAN
- NETWORK_IDENTITY_BAN
- CONTENT_REMOVAL
- INVENTORY_CONFISCATION
- MARKET_BLACKLIST
- REPUTATION_BLACKLIST
- REPORT_RESTRICTION
- STALL_OWNERSHIP_REMOVAL

Policy v2 should model CONTENT_REMOVAL, INVENTORY_CONFISCATION, blacklist/restriction effects, stall removal, profile correction restrictions, and similar cleanup as remedies/compliance conditions rather than pretending every response is a timed punitive sanction.

## Proposed decay classes

These names describe policy intent only. W1 owns the deterministic formula/config contract.

| Class | Intent |
| --- | --- |
| D0_COMPLIANCE | State persists until corrected/approved. The underlying compliance state does not “decay” with time. Separate refusal/evasion can create behavioral history. |
| D1_SHORT | Low-level conduct where old history should lose influence relatively quickly. |
| D2_STANDARD | Ordinary behavioral/integrity history. |
| D3_EXTENDED | Serious repeated abuse, cheating, exploit, evasion, or integrity behavior that should remain relevant longer. |
| D4_NONDECAY | Severe-safety/security conduct where the factual history normally remains relevant. |
| D5_REVIEW_ONLY | Policy gap. No automatic sanction history weight until owner review classifies the incident. |

## Related-history behavior groups

Implementation should not infer related history from GUI category equality. Proposed explicit groups:

CHAT_DISRUPTION, HARASSMENT, HATE, SEXUAL_CONTENT, SAFETY, PRIVACY, ADVERTISING, MALICIOUS_CONTENT, CHEATING, EXPLOIT_ABUSE, SERVER_DISRUPTION, ACCESS_COMPLIANCE, ACCOUNT_ABUSE, SANCTION_EVASION, PROFILE_COMPLIANCE, STAFF_COOPERATION, REPORT_ABUSE, EVIDENCE_INTEGRITY, MARKET_INTEGRITY, REPUTATION_INTEGRITY, COMPLICITY.

## Policy matrix

Cell shorthand:

- Class is one of compliance, behavioral, integrity, severe-safety.
- “Remedies” are possible mandatory cleanup/containment actions, not proposed punishment lengths.
- “Questions” are incident attributes W1/W3A should support.
- “Owner” notes identify unresolved policy choices.

### Chat & Spam

| Stable ID | Public name | Definition / inclusions | Explicit exclusions / use instead | Questions | Possible mandatory remedies | History groups | Decay | Class | Policy v1 mapping | Owner / gaps |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| chat.spam.low-level | Chat spam / caps | Repeated characters, excessive caps, low-volume repetitive chat that disrupts conversation. | Sustained rapid flooding -> chat.spam.flooding. PM-only spam -> chat.spam.private-message. | public/Discord, repetition count, duration, stopped after warning? | none | CHAT_DISRUPTION | D1_SHORT | behavioral | spam.low-level | Define threshold between low-level spam and flooding. |
| chat.spam.flooding | Message flooding | Sustained rapid message output intended or likely to overwhelm channels. | Bot/network attack -> disruption.other-technical-abuse. | message rate, duration, channels, automation evidence, impact | channel cleanup if supported | CHAT_DISRUPTION | D2_STANDARD | behavioral | spam.message-flooding | Decide whether automated flooding is an aggravator or technical-abuse finding. |
| chat.spam.private-message | Private-message spam | Repeated unwanted direct messages through server-controlled messaging. | Threats/harassment -> abuse.targeted-harassment or safety.credible-threat. | recipient count, blocks/requests to stop, duration | none | CHAT_DISRUPTION, HARASSMENT | D2_STANDARD | behavioral | spam.private-message | Keep only if server can evidence PM context reliably. |
| chat.disruption.noise | Voice / sound spam | Mic spam, repeated sound effects, note-block/portal or similar intentional noise disruption. | Lag machine -> disruption.laggy-build. | source, repetition, area/channel, intent, instruction given? | disable/remove disruptive source when appropriate | CHAT_DISRUPTION, SERVER_DISRUPTION | D2_STANDARD | behavioral | spam.noise-pollution | Distinguish harmless ambient build from deliberate disruption. |
| chat.language.non-english-public | Non-English public chat | Continued non-English communication in public moderated channels after correction notice. | The language itself is not bannable. Mute evasion -> evasion.mute. Private/ticket communication is not this offense unless separately configured. | public channel?, warning given?, understandable exception/emergency?, repeated after notice? | correction notice; chat-only restriction/mute if policy allows | CHAT_DISRUPTION | D1_SHORT | compliance | language.non-english-public-chat | Current v1 ladder contains network bans; remove that path. Owner should define exceptions and maximum chat-only consequence. |
| chat.begging | Repeated begging | Repeated requests for ranks, items, creative, permissions, or special treatment after being told to stop. | Ordinary one-off request is not an offense. Extortion -> safety.blackmail-extortion. | repeated?, recipient, staff warning, disruption | none | CHAT_DISRUPTION | D1_SHORT | behavioral | spam.begging | Clarify whether player-to-player item begging is included equally with staff-permission begging. |
| chat.sensitive-topic-public | Politics / sensitive IRL discussion | Political, controversial real-world, or heavy personal disputes continued in public channels contrary to topic rules. | Discriminatory expression -> hate.discriminatory-expression. Extremist/hate glorification or advocacy -> hate.extremist-advocacy. Threats -> safety.credible-threat. | topic, public channel, instruction to move/stop, disruption, endorsement vs discussion | move/remove content if supported | CHAT_DISRUPTION | D1_SHORT or D2_STANDARD by persistence | behavioral | politics.casual, politics.moderate, politics.extreme | Retire “extreme politics” as a substitute for hate/extremism. Owner should define when persistence raises history class. |
| chat.public-case-argument | Public punishment/case argument | Persistently arguing an active moderation case in public instead of using the ticket/appeal path. | Good-faith question or appeal in proper channel is allowed. Staff harassment -> abuse.targeted-harassment. | active case?, directed to ticket?, repeated?, disruptive? | move to ticket; remove sensitive case details if necessary | CHAT_DISRUPTION, STAFF_COOPERATION | D1_SHORT | behavioral | staff.public-punishment-argument | Avoid punishing simple disagreement; require persistence/disruption after direction. |

### Harassment & Abuse

| Stable ID | Public name | Definition / inclusions | Explicit exclusions / use instead | Questions | Remedies | History groups | Decay | Class | Policy v1 mapping | Owner / gaps |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| abuse.general-toxicity | General toxicity / insulting | Non-protected-class insults or disruptive abusive behavior that is not sustained targeted harassment. | Protected-class abuse -> hate.discriminatory-expression. Ordinary profanity without attack is allowed. | target?, frequency, context, mutual banter?, request to stop? | none | HARASSMENT | D1_SHORT | behavioral | hate.general-toxicity | Rename/move out of “hate”; current family is misleading. |
| abuse.targeted-harassment | Targeted harassment / bullying | Repeated or severe targeted abuse, bullying, unwanted contact, baiting, or harassment of players or staff. | Sexual conduct -> abuse.sexual-harassment. Hate -> hate.*. Threat -> safety.credible-threat. | target, repeated?, requested stop?, power imbalance, channels, staff victim? | contact/channel restriction if separately authorized | HARASSMENT | D2_STANDARD or D3_EXTENDED for sustained campaigns | behavioral | hate.targeted-harassment, reports.harassing-staff | reports.harassing-staff is a surface duplicate and should retire as a selectable reason. |
| abuse.sexual-harassment | Sexual harassment | Unwanted sexual comments, advances, sexualized targeting, or repeated sexual conduct toward a person. | General explicit content without targeted victim -> content.explicit-sexual. Grooming -> safety.grooming. | target age if known, repeated?, unwanted?, coercion?, private/public? | content removal; contact restriction where supported | HARASSMENT, SEXUAL_CONTENT, SAFETY | D3_EXTENDED | behavioral | harassment.sexual | Owner should define escalation boundary to grooming/coercion. |

### Hate & Extremism

| Stable ID | Public name | Definition / inclusions | Exclusions / use instead | Questions | Remedies | History groups | Decay | Class | Policy v1 mapping | Owner / gaps |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| hate.discriminatory-expression | Discriminatory expression | Racist, sexist, bigoted, or protected-class demeaning statements without a slur. | Ordinary politics/sensitive discussion -> chat.sensitive-topic-public. Slur -> hate.slur. | protected class, targeted?, endorsement/attack?, quote/condemnation context? | content removal where appropriate | HATE, HARASSMENT | D2_STANDARD | behavioral | hate.discriminatory-statement | Define protected classes consistently across Minecraft/Discord. |
| hate.slur | Slur use | Full, abbreviated, masked, misspelled, quoted-as-attack, name/build, or joke use of prohibited slurs. Targetedness and form are incident attributes, not separate stable offenses. | Neutral identity terms or bona fide condemnation/administrative evidence handling are context-sensitive and not blanket offenses. | term, form, targeted?, quote/context, medium, obfuscation, name/build? | content/profile/build removal as applicable | HATE, HARASSMENT | D3_EXTENDED | behavioral | hate.abbreviated-slur-untargeted, hate.abbreviated-slur-targeted, hate.full-slur-untargeted, hate.full-slur-targeted | Owner must decide whether any quoted/educational exception exists in public chat. |
| hate.extremist-symbol | Hate / extremist symbol | Deliberate display of hate symbols or extremist imagery in profile, build, message, media, or other server surface. | Historical/condemnatory context should not be silently equated with advocacy. Politics alone -> chat.sensitive-topic-public. | symbol, context, placement, intent, targeted intimidation?, duration | content/profile/build removal; correction restriction | HATE | D3_EXTENDED | integrity | hate.extremist-symbol | Current reason does not encode context; W3A must ask it. |
| hate.extremist-advocacy | Hate/extremist advocacy or glorification | Endorsing, glorifying, recruiting for, or advocating genocide, hate-based violence, or extremist ideology against protected groups. | Discussion/reporting/condemnation -> chat.sensitive-topic-public if otherwise disruptive. Threat to a specific person -> safety.credible-threat. | advocacy vs quotation, target group, recruitment, violence endorsement, persistence | content removal; safety containment if needed | HATE, SAFETY | D4_NONDECAY or D3_EXTENDED per owner | severe-safety | hate.advocating-hate-violence | Owner must choose whether history is non-decaying and define nonviolent extremist advocacy boundary. |

### Sexual / Inappropriate Content

| Stable ID | Public name | Definition / inclusions | Exclusions | Questions | Remedies | History groups | Decay | Class | Policy v1 mapping | Owner / gaps |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| content.inappropriate | Inappropriate content | Clearly inappropriate/disruptive non-illegal content in messages, signs, builds, voice, shared media, or similar surfaces. Severity is an incident attribute. | Sexual explicit content -> content.explicit-sexual. Hate symbol -> hate.extremist-symbol. Illegal exploitative content -> safety.illegal-exploitative-content. | medium, audience, severity, deliberate filter bypass?, repeated?, removed voluntarily? | content/build removal | SEXUAL_CONTENT or CHAT_DISRUPTION as applicable | D2_STANDARD | behavioral | content.moderate-inappropriate, content.extreme-inappropriate | Current moderate/extreme split is subjective; define structured severity attributes instead. |
| content.explicit-sexual | Explicit sexual content | Explicit sexual material, nudity, or explicit sexual descriptions not better classified as targeted harassment/grooming/illegal content. | Targeted sexual harassment -> abuse.sexual-harassment. Grooming -> safety.grooming. Illegal exploitative content -> safety.illegal-exploitative-content. | medium, explicitness, audience, target, age context if relevant | immediate content removal | SEXUAL_CONTENT | D3_EXTENDED | behavioral | content.explicit-sexual | Owner should define whether consensual adult references are always prohibited or only explicit content. |

### Safety, Threats & Privacy

| Stable ID | Public name | Definition / inclusions | Exclusions | Questions | Remedies | History groups | Decay | Class | Policy v1 mapping | Owner / gaps |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| safety.encouraging-self-harm | Encouraging self-harm | Encouraging, pressuring, or instructing another person to self-harm or die. | Good-faith support/discussion is not an offense. Credible threat by actor -> safety.credible-threat. | target, directness, repetition, vulnerability context, joking vs encouragement | content removal; safety escalation outside punishment system when appropriate | SAFETY, HARASSMENT | D3_EXTENDED | severe-safety | safety.encouraging-self-harm | Decide whether severe cases are D4_NONDECAY. |
| privacy.personal-information-exposure | Exposing personal information | Posting or exposing identifying/private information without consent, including careless disclosure without harmful intent. | Intentional targeting/weaponization -> privacy.doxxing. | info type, consent, public reach, intent, removal speed, victim risk | immediate content removal/redaction | PRIVACY | D2_STANDARD or D3_EXTENDED by sensitivity | integrity | privacy.personal-information-careless | Current v1 can become a ban; owner should decide when repeated careless exposure crosses into severe misuse. |
| privacy.personal-information-pressure | Pressuring for personal information | Coercing or pressuring another person to reveal identifying/private information. | Blackmail -> safety.blackmail-extortion. Grooming -> safety.grooming. | requested info, persistence, coercion, age/power context | stop-contact/content removal if needed | PRIVACY, HARASSMENT | D2_STANDARD | behavioral | none — rule gap | Public rules prohibit this but Policy v1 has no exact reason. |
| safety.credible-threat | Credible threat | A threat of real-world harm that is credible enough to create a safety concern. | Hyperbolic insult without credible threat -> abuse.*. Extortion -> safety.blackmail-extortion. | specificity, capability, target, immediacy, corroboration, location info | safety containment; content removal; preserve private evidence | SAFETY | D4_NONDECAY | severe-safety | safety.credible-threat | Public rules say zero tolerance/permanent ban while v1 allows a temporary first step; owner must resolve. |
| privacy.doxxing | Doxxing | Intentional collection, publication, or weaponization of identifying/private information to expose or endanger someone. | Careless disclosure -> privacy.personal-information-exposure. | intent, info sensitivity, sourcing, dissemination, target risk | immediate removal/redaction; preserve private evidence | PRIVACY, SAFETY | D4_NONDECAY | severe-safety | privacy.doxxing | Public rules describe zero tolerance/permanent ban while v1 also permits a temporary step; owner must resolve. |
| safety.blackmail-extortion | Blackmail / extortion | Threatening harm, exposure, punishment, or loss to coerce a person into providing money, items, access, content, or conduct. | Ordinary trade dispute -> owner decision under market policy. | demand, threat, leverage, target, evidence | stop-contact; content removal; protect victim evidence | SAFETY, PRIVACY | D4_NONDECAY | severe-safety | safety.blackmail-extortion | Current zero-tolerance rule is consistent structurally. |
| safety.grooming | Grooming | Sexual or exploitative relationship-building/manipulation for abuse, especially involving minors or vulnerable users. | Non-targeted sexual content -> content.explicit-sexual. Sexual harassment without grooming pattern -> abuse.sexual-harassment. | ages if known, relationship pattern, coercion, secrecy, sexualization, off-platform movement | immediate containment; preserve private evidence | SAFETY, SEXUAL_CONTENT | D4_NONDECAY | severe-safety | safety.grooming | Keep Admin/Founder review and privacy-safe public projection. |
| safety.illegal-exploitative-content | Illegal sexual / exploitative content | Sharing, soliciting, possessing through server surfaces, or distributing illegal sexual/exploitative content. | Legal adult explicit content -> content.explicit-sexual. | content type, solicitation/distribution, target, platform surface | immediate removal/containment; preserve only legally/operationally appropriate evidence | SAFETY, SEXUAL_CONTENT | D4_NONDECAY | severe-safety | safety.illegal-exploitative-content | Owner/legal handling procedure should be documented separately; public reason must stay sanitized. |

### Advertising, Scams & Malicious Content

| Stable ID | Public name | Definition / inclusions | Exclusions | Questions | Remedies | History groups | Decay | Class | Policy v1 mapping | Owner / gaps |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| advertising.minecraft-server | Other-server advertising | Promoting another Minecraft server through Enthusia surfaces. | Innocent mention without promotion may be allowed; unrelated self-promotion -> advertising.unrelated-self-promotion. | intent, invite/IP, repetition, DM/public, solicited? | content removal | ADVERTISING | D2_STANDARD | behavioral | advertising.minecraft-server | Define whether solicited private recommendations are exempt. |
| advertising.unrelated-self-promotion | Unrelated self-promotion | Unrelated media/social/channel promotion contrary to server rules. | Malicious/deceptive link -> security.malicious-link. | commercial?, frequency, solicited?, public/private | content removal | ADVERTISING | D1_SHORT or D2_STANDARD | behavioral | advertising.unrelated-self-promotion | Current v1 escalates to combined bans; owner should review proportionality. |
| security.unsafe-download | Unsafe/questionable download sharing | Sharing a download that is unsafe, unverified, deceptive, or presents a material security risk without proven malicious payload intent. | Proven malicious file/phishing -> security.malicious-file or security.malicious-link. | source, file type, warnings, intent, harm, known malware? | immediate link/file removal | MALICIOUS_CONTENT | D3_EXTENDED | integrity | account.unsafe-download | “Questionable” is vague. Owner should narrow or retire this reason in favor of objective risk criteria. |
| security.malicious-link | Malicious / phishing / scam link | Knowingly sharing phishing, credential theft, malware-delivery, or deliberately deceptive scam links. | Non-link trade dispute/scam has no approved Policy v2 finding yet; see owner gaps. | destination, intent, compromise reports, disguise, repetition | immediate removal; invalidate affected server tokens if operationally relevant outside policy | MALICIOUS_CONTENT, SAFETY | D4_NONDECAY or D3_EXTENDED per owner | severe-safety | advertising.malicious-link | Consider moving out of advertising family permanently. |
| security.malicious-file | Malicious file | Knowingly distributing malware or another malicious file through community surfaces. | Merely questionable/unverified download -> security.unsafe-download. | file evidence, intent, execution harm, targeting | immediate removal/containment | MALICIOUS_CONTENT, SAFETY | D4_NONDECAY | severe-safety | none — public-rule gap | Public rules call malicious files zero tolerance, but v1 has no exact reason. |

### Cheating & Unauthorized Modifications

| Stable ID | Public name | Definition / inclusions | Exclusions | Questions | Remedies | History groups | Decay | Class | Policy v1 mapping | Owner / gaps |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| cheating.hacked-client | Hacked client / confirmed cheat client | Manually confirmed use of a hacked client or comparable prohibited cheat package. Detection evidence may support the finding but is not itself the offense. | Unconfirmed automated alert is evidence only. Specific hidden-info or automation behavior may use the more precise findings below. | confirmation method, behaviors, duration, advantage, manual review | confiscation/rollback only when causally supported | CHEATING | D3_EXTENDED | integrity | cheating.manual-client; cheating.polar.template proposed retirement as selectable offense | Polar template must become evidence-source/config, not a durable finding ID. |
| cheating.hidden-information | Hidden-information advantage | X-ray, ESP, freecam used for advantage, entity/cave radar, prohibited PvP indicators, minimap hidden-entity/cave display, or equivalent concealed-information tools. | Allowed Fair Play minimap/world map -> allowed. Replay/camera use with no prohibited advantage requires owner/tool rules. | capability, actual use, context, gains, duration, item acquisition | confiscate illicit gains when attributable | CHEATING | D3_EXTENDED | integrity | cheating.xray-esp, cheating.freecam, cheating.pvp-indicator, cheating.minimap-display | Owner may keep subtypes for GUI questions, not separate history groups. |
| cheating.pathfinding-automation | Pathfinding / gameplay automation | Baritone or equivalent automation performing prohibited gameplay actions. | Pure route visualization with no automation -> other policy only if prohibited. | tool, actions automated, duration, gains | remove/confiscate gains when attributable | CHEATING | D3_EXTENDED | integrity | cheating.baritone | Define boundary for accessibility tools/macros. |
| cheating.build-automation | Easy Place / Printer automation | Prohibited automated placement/build functionality such as Easy Place or Printer Mode. | Litematica holograms/schematics alone are allowed. | mode, actual use, duration, warning history | correction instruction; remove gains only if necessary | CHEATING | D2_STANDARD | integrity | cheating.easy-place-printer | Keep first-warning rule from public rules if owner confirms. |
| cheating.autoclicker-unauthorized | Unauthorized autoclicker mod | Use of an autoclicker mod outside the specifically approved Enthusia/server-side/external desktop options, absent combat use. | Combat use -> cheating.autoclicker-combat. Bypassed approved client -> cheating.autoclicker-bypass. | tool, combat?, prior switch warning, CPS/use context | correction instruction | CHEATING | D1_SHORT or D2_STANDARD | compliance | cheating.unauthorized-autoclicker-mod | Public rules promise one warning to switch; preserve that as policy. |
| cheating.autoclicker-combat | Autoclicker in combat | Any autoclicker use in combat where server rules prohibit it, regardless of otherwise approved autoclicker source. | Non-combat unauthorized mod -> cheating.autoclicker-unauthorized. | combat evidence, tool, duration, impact | none; confiscation only if separately justified | CHEATING | D3_EXTENDED | integrity | cheating.combat-autoclicker | Confirm whether every external autoclicker is prohibited specifically in combat. |
| cheating.autoclicker-bypass | Autoclicker restriction bypass | Modifying the Enthusia autoclicker or deliberately bypassing its restrictions. | Merely using an unapproved mod without bypass -> cheating.autoclicker-unauthorized. | modification, bypass mechanism, intent, combat? | disable/correct client requirement | CHEATING | D3_EXTENDED | integrity | cheating.autoclicker-bypass | Distinguish security bypass from ordinary configuration error. |
| cheating.other-unfair-modification | Other unfair modification | Catch-all for unlisted client modifications that provide information or automation normal players do not have. | Unknown exploit in server mechanics -> exploit.*. Cosmetic/performance mods with no advantage are allowed. | capability, advantage, approval sought, actual use, gains | correction instruction; confiscate attributable gains | CHEATING | D2_STANDARD or D3_EXTENDED | integrity | cheating.other-modification | This broad class should prevent creating a new reason for every future client. |

### Exploits, Bug Abuse, Duplication & Server Disruption

| Stable ID | Public name | Definition / inclusions | Exclusions | Questions | Remedies | History groups | Decay | Class | Policy v1 mapping | Owner / gaps |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| exploit.prohibited-duplication | Prohibited duplication | Intentionally using any duplication method not expressly allowed or pre-approved. | TNT, rail, and carpet duping are expressly allowed unless a temporary written restriction says otherwise. Mere possession -> exploit.duplicated-item-possession. | method, allowed list at incident time, written approval/restriction, volume, impact | mandatory removal/confiscation of illicit duplicate value; rollback when justified | EXPLOIT_ABUSE, MARKET_INTEGRITY | D3_EXTENDED | integrity | exploit.illegal-duplication | Policy must be version-aware because allowed duplication methods can change. |
| exploit.duplicated-item-possession | Possession of duplicated items | Possessing, storing, trading, or using prohibited duplicated items. Knowledge level is an incident attribute. | Active laundering conspiracy -> complicity.laundering-duplicated-items. Unknown legitimate provenance can be remedy-only. | knowledge confidence, quantity, source, transaction history, cooperation | confiscate illicit items/value | EXPLOIT_ABUSE, MARKET_INTEGRITY | D2_STANDARD or D3_EXTENDED by knowledge | integrity | exploit.duplicated-possession-unclear, exploit.duplicated-possession-knowing | Current “unclear knowledge” can punish uncertainty. Owner should allow remedy without behavioral finding where knowledge is not established. |
| exploit.bug-withholding | Withholding a discovered bug/exploit | Knowingly concealing a suspected exploitable bug after recognizing it and having a reasonable opportunity to report it. | Accidental encounter without recognition/opportunity is not an offense. Active use -> exploit.bug-abuse. | discovery time, certainty, opportunity to report, concealment, benefit | preserve/remediate affected state | EXPLOIT_ABUSE, STAFF_COOPERATION | D2_STANDARD or D3_EXTENDED | integrity | exploit.withholding | Define a reasonable reporting window; current “immediately” rule is too literal for edge cases. |
| exploit.bug-testing-sharing | Unauthorized exploit testing / sharing | Reproducing, testing, teaching, publishing, or sharing an unapproved bug/exploit without written staff authorization. | Good-faith minimal evidence gathering requested by staff is allowed. Actual gain/damage -> exploit.bug-abuse. | tested/reproduced/shared?, audience, staff permission, scope, damage | remove exploit instructions/content; remediate state | EXPLOIT_ABUSE | D3_EXTENDED | integrity | none — public-rule gap | Rules explicitly prohibit testing/reproducing/sharing, but v1 lacks an exact reason. |
| exploit.bug-abuse | Bug / exploit abuse | Deliberate use of an unapproved bug/exploit. Impact, gain, repetition, economic damage, and server damage are attributes rather than “minor/major” stable IDs. | Prohibited duplication -> exploit.prohibited-duplication. Crash attempt -> disruption.server-crash-attempt. | intent, repetitions, gain, affected players/economy, server impact, disclosure/cooperation | remove gains/builds/items; rollback/remediate affected state | EXPLOIT_ABUSE, MARKET_INTEGRITY, SERVER_DISRUPTION | D3_EXTENDED | integrity | exploit.minor-abuse, exploit.major-abuse | Replace subjective minor/major IDs with structured impact attributes. |
| disruption.chunk-loading | Prohibited offline chunk loading | Chunk loaders or mechanisms keeping chunks active while nobody is present contrary to rules. | Ordinary loaded chunks while players are present are not this offense. | mechanism, unattended duration, warning, performance impact | disable/remove mechanism | SERVER_DISRUPTION | D0_COMPLIANCE then D2 for refusal | compliance | mechanics.chunk-loader | Initial response should usually be remedy/compliance; repeated deliberate rebuilding can become behavior. |
| disruption.laggy-build | Unreasonable lag / instability build | Farm, machine, entity setup, or build causing unreasonable lag/instability, especially after staff instruction. | Deliberate crash attempt -> disruption.server-crash-attempt. | measured impact, staff instruction, correction opportunity, recurrence | disable/remove/limit offending build/entities | SERVER_DISRUPTION | D0_COMPLIANCE then D2 for refusal | compliance | mechanics.laggy-farm | Current ID already says “after staff instruction”; preserve that distinction. |
| disruption.end-portal-obstruction | End portal obstruction | Blocking, trapping, or preventing normal End Portal entry using TNT duping, falling blocks, or another method. | Camping the End Portal frame is expressly allowed. | method, duration, accessibility, warning, removal | mandatory removal/restoration | SERVER_DISRUPTION | D2_STANDARD | behavioral | mechanics.end-portal-obstruction | Keep narrow so allowed camping is not swept in. |
| disruption.protected-area-violation | Protected-area violation | Unauthorized PvP, theft, modification, raiding, or damage inside explicitly protected Spawn/Market areas. | Wilderness PvP, theft, raiding, and griefing remain allowed. This is not a general “griefing” offense. | location/protection state, action, warning, restoration cost | restore/remove unauthorized changes/items as appropriate | SERVER_DISRUPTION, MARKET_INTEGRITY | D2_STANDARD | integrity | none — rule gap | Public rules create protected areas but v1 has no exact reason. Owner should confirm whether all listed actions are punishable or automatically blocked only. |
| disruption.server-crash-attempt | Server crash / serious damage attempt | Intentionally attempting to crash, corrupt, or seriously damage server availability/state. | Accidental laggy build -> disruption.laggy-build. Unknown technical abuse without crash intent -> disruption.other-technical-abuse. | intent, technique, attempts, impact, persistence | emergency containment; remediate state | SERVER_DISRUPTION, EXPLOIT_ABUSE | D4_NONDECAY | severe-safety | exploit.server-crash-attempt | Keep Admin/Founder review. |
| disruption.other-technical-abuse | Other technical abuse / server disruption | Broad class for deliberate technical abuse that harms availability, integrity, or normal operation but is not a more specific exploit/crash finding. | Normal gameplay, allowed duping, and harmless edge cases are excluded. | technique, intent, impact, repeatability, approval, affected systems | disable/remediate mechanism; emergency containment where necessary | SERVER_DISRUPTION, EXPLOIT_ABUSE | D3_EXTENDED | integrity | none — coverage gap | Intended future-safe class; owner should approve wording to avoid overbreadth. |

### Accounts, VPNs & Access

| Stable ID | Public name | Definition / inclusions | Exclusions | Questions | Remedies | History groups | Decay | Class | Policy v1 mapping | Owner / gaps |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| access.vpn-compliance | Unapproved VPN / proxy access | Connecting through a VPN/proxy without the required prior approval/vouch. Primary response is access compliance, not a punishment ladder. | Deliberate repeated bypass/refusal -> access.vpn-evasion. Punishment evasion using VPN -> evasion.ban/mute/restriction as applicable. | VPN confidence, approval record, vouch, false positive, first occurrence | deny/kick/restrict access until approved or VPN disabled | ACCESS_COMPLIANCE | D0_COMPLIANCE | compliance | account.unapproved-vpn | Current v1 mixes warning/kick with escalating bans. Split compliance from misconduct. |
| access.vpn-evasion | VPN/access compliance evasion | Deliberately bypassing VPN/proxy restrictions, repeatedly reconnecting after clear instruction, falsifying compliance, or changing endpoints to evade access controls. | First unapproved connection -> access.vpn-compliance. Ban/mute evasion -> sanction-evasion finding. | prior notice, bypass steps, intent, repeated endpoints, false statements | access restriction; network-identity controls if separately authorized | ACCESS_COMPLIANCE, SANCTION_EVASION | D3_EXTENDED | behavioral | none — required split | Owner should define when repeated noncompliance becomes serious misconduct and where long-ban options become available. |
| account.sharing | Account sharing | Allowing another person to use the account or using another player’s account contrary to rules. | Mere linked alts owned by the same player are not automatically this offense unless another rule forbids them. | owner/user, consent, duration, reason, evasion involvement | account/access restriction until ownership/security clarified if necessary | ACCOUNT_ABUSE, SANCTION_EVASION | D3_EXTENDED | integrity | account.sharing | Clarify ordinary alt-account allowance separately. |
| account.headless-client | Headless client / headless alt | Operating a headless Minecraft client/account where the server rules prohibit headless alternate accounts. | Approved infrastructure/service accounts are excluded if owner authorizes them. | client type, account purpose, automation, approval, gains | disconnect/restrict offending account | ACCOUNT_ABUSE, CHEATING | D2_STANDARD | integrity | account.headless-client | Define approved exceptions and whether headless main account is also prohibited. |
| account.theft | Account theft / compromise abuse | Taking, hijacking, or using another person’s account without authorization. | Consensual sharing -> account.sharing. | ownership evidence, authorization, compromise method, damage/evasion | lock/restrict affected access; restore state where supported | ACCOUNT_ABUSE, SAFETY | D4_NONDECAY | severe-safety | account.theft | Keep high-authority review and sanitized public output. |

### Punishment Evasion & Alt Abuse

| Stable ID | Public name | Definition / inclusions | Exclusions | Questions | Remedies | History groups | Decay | Class | Policy v1 mapping | Owner / gaps |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| evasion.mute | Mute evasion | Circumventing an active mute through alts, other accounts, channels, VPN/access tricks, or another person. | Merely speaking non-English while not muted -> chat.language.non-english-public. | active mute, method, identity confidence, assistance | reapply/extend underlying communication restriction per owner policy; account linkage containment | SANCTION_EVASION | D3_EXTENDED | integrity | evasion.mute | This is the only route by which repeated non-English-chat enforcement could lead into ban-level misconduct. |
| evasion.ban | Ban evasion | Accessing/attempting to access while an active ban applies, using alts, VPNs, shared accounts, or other identities. | Unapproved VPN with no active sanction -> access.vpn-compliance. | active ban, identity confidence, method, account ownership, assistance | enforce active ban; network-identity restriction if separately authorized | SANCTION_EVASION, ACCOUNT_ABUSE | D3_EXTENDED | integrity | evasion.ban; evasion.network-identity-ban proposed retirement as finding | NETWORK_IDENTITY_BAN is an enforcement remedy, not an offense ID. |
| evasion.restriction | Restriction / blacklist evasion | Circumventing an active market, reputation, report, jail, or other configured restriction through another account/player/method. | Ban/mute evasion use specific reasons. | restriction type, active state, method, identity, helper | restore/enforce restriction; remove illicit benefit | SANCTION_EVASION, MARKET_INTEGRITY, REPUTATION_INTEGRITY, REPORT_ABUSE | D3_EXTENDED | integrity | market.blacklist-evasion, reputation.blacklist-evasion | Future restrictions should reuse this offense rather than creating one evasion ID per subsystem. |
| evasion.assisting-other | Assisting another player’s evasion | Knowingly hiding, gearing, housing, transporting, lending accounts, providing access, or otherwise helping another person evade a sanction. | General cheating assistance -> complicity.assisting-cheating. | knowledge, assistance type, benefit, persistence, relationship | remove illicit benefits/access; secure shared account if applicable | SANCTION_EVASION, COMPLICITY | D3_EXTENDED | integrity | evasion.helping-another, evasion.harboring-ban-evader | Merge overlapping “helping” and “harboring” v1 reasons. |

### Profiles & Identity

| Stable ID | Public name | Definition / inclusions | Exclusions | Questions | Remedies | History groups | Decay | Class | Policy v1 mapping | Owner / gaps |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| profile.inappropriate-username | Inappropriate username | Username violates content/hate/sexual/impersonation rules. | Substantive staff impersonation may also use identity.staff-impersonation. | content type, platform rename availability, corrected?, repeat deliberate rename? | restricted until username corrected; kick/rejoin gate where technically possible | PROFILE_COMPLIANCE plus HATE/SEXUAL_CONTENT when relevant | D0_COMPLIANCE; D2 for deliberate repeat | compliance | identity.inappropriate-profile | Split from skin/profile. Current v1 escalates from kick directly into long bans; prefer condition-until-corrected plus separate refusal/repeat behavior. |
| profile.inappropriate-skin | Inappropriate skin | Skin contains prohibited sexual, hateful, extremist, or otherwise clearly inappropriate imagery. | Ordinary cosmetic disagreement is not an offense. | content, visibility, corrected?, repeated deliberate changes | restricted until skin corrected | PROFILE_COMPLIANCE plus HATE/SEXUAL_CONTENT when relevant | D0_COMPLIANCE; D2 for deliberate repeat | compliance | identity.inappropriate-profile | Needs technical enforcement design from W3B. |
| profile.inappropriate-other | Inappropriate profile component | Other profile/cosmetic components exposed to the community violate content rules. | Username and skin use their specific findings. | component, content, correction path, repeat | restricted until corrected; content removal if controllable | PROFILE_COMPLIANCE | D0_COMPLIANCE | compliance | identity.inappropriate-profile | Define supported profile surfaces before cutover. |
| identity.staff-impersonation | Staff impersonation | Falsely presenting oneself as staff or falsely claiming staff authority. | Satire/jokes with no reasonable deception may be excluded; abusive conduct can also classify separately. | representation, likelihood of deception, action taken under false authority, target | remove misleading profile/content | ACCOUNT_ABUSE, STAFF_COOPERATION | D2_STANDARD or D3_EXTENDED | integrity | identity.staff-impersonation | Define obvious parody exception. |

### Reports, Evidence & Staff Cooperation

| Stable ID | Public name | Definition / inclusions | Exclusions | Questions | Remedies | History groups | Decay | Class | Policy v1 mapping | Owner / gaps |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| reports.spam | Report / ticket spam | Repeated frivolous or duplicate reports/tickets that abuse the reporting system. | Good-faith mistaken report -> no offense. Knowingly fabricated accusation -> reports.false. | volume, duplication, warning, intent, emergencies | report restriction when necessary while preserving emergency/safety path | REPORT_ABUSE | D2_STANDARD | behavioral | reports.spam | Any permanent report restriction must still preserve a safe route for urgent safety reports. |
| reports.false | Knowingly false report | Deliberately making a materially false accusation/report. | Mistake, incomplete evidence, or unproven allegation without knowing falsity is not enough. Forged evidence -> integrity.evidence-falsification. | knowledge, material falsehood, motive, correction/retraction | report restriction if necessary | REPORT_ABUSE, EVIDENCE_INTEGRITY | D3_EXTENDED | integrity | reports.false | Require knowledge/intent; do not punish failed reports. |
| integrity.evidence-falsification | Falsifying evidence | Forging, editing, fabricating, staging, or materially misrepresenting screenshots, recordings, logs, or other evidence. | Innocent cropping/format conversion that preserves meaning is not falsification. Evidence destruction -> integrity.evidence-destruction. | alteration, materiality, intent, case impact, distribution | disregard/quarantine false evidence; preserve audit trail | EVIDENCE_INTEGRITY | D3_EXTENDED | integrity | dishonesty.falsifying-evidence | Current high severity is structurally justified; owner decides sanction range. |
| integrity.investigation-dishonesty | Investigation dishonesty | Knowingly making material false statements during a staff investigation. | Right to decline optional questions should not be automatically equated with lying. Refusal of a reasonable required instruction -> staff.instruction-refusal. | material fact, knowledge, correction, question authority, case impact | none beyond correcting record | EVIDENCE_INTEGRITY, STAFF_COOPERATION | D2_STANDARD or D3_EXTENDED | integrity | dishonesty.lying-investigation | Define which questions players are required to answer. |
| integrity.evidence-destruction | Evidence destruction | Knowingly deleting, altering, hiding, or destroying relevant evidence after an investigation/incident makes preservation reasonably expected. | Ordinary deletion before any incident/investigation awareness is not this offense. | notice/awareness, evidence relevance, deletion act, recoverability, intent | preserve remaining evidence; recovery/containment where authorized | EVIDENCE_INTEGRITY, STAFF_COOPERATION | D3_EXTENDED | integrity | none — public-rule gap | Rules expressly mention evidence destruction but v1 lacks an exact reason. |
| staff.instruction-refusal | Refusing a reasonable staff instruction | Deliberately refusing a lawful/reasonable instruction during an active incident/investigation after the instruction is clear and within staff authority. | Disagreement/appeal is allowed. Invalid/unsafe/out-of-scope instruction is not enforceable through this finding. | instruction text, authority, clarity, feasibility, notice, refusal | compliance action directly tied to instruction if authorized | STAFF_COOPERATION | D2_STANDARD | behavioral | staff.refusing-instruction | Current ladder reaches bans from a broad reason; owner should require reasonableness/authority attributes. |
| staff.investigation-interference | Investigation interference | Obstructing an active investigation without necessarily lying: witness intimidation, evidence access interference, repeated disruption, or materially preventing staff work. | Simple disagreement -> no offense; public case argument -> chat.public-case-argument. | act, material effect, warning, intent, safety risk | containment needed to let investigation proceed | STAFF_COOPERATION, EVIDENCE_INTEGRITY | D2_STANDARD or D3_EXTENDED | integrity | none — public-rule gap | Rules explicitly prohibit interference. |
| reports.abusive-content-retired | Retire: abusive content in report | Policy v1 surface-based reason should not remain a selectable v2 finding. Classify the substantive conduct instead: hate, harassment, sexual/inappropriate content, threat, or spam. | N/A | channel=report/ticket remains an incident attribute | as required by substantive offense | underlying group | underlying class | behavioral/integrity | reports.abusive-content | Keep historical removed-reason presentation, not a new selectable offense. |

### Economy, Market & Reputation

| Stable ID | Public name | Definition / inclusions | Exclusions | Questions | Remedies | History groups | Decay | Class | Policy v1 mapping | Owner / gaps |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| market.stall-compliance | Market stall compliance | Misusing a stall as protected storage, violating the one-container restock rule, leaving obstructive shulkers/items, or otherwise failing explicit stall-layout/compliance requirements. | Extra stall through alt -> market.extra-stall-alt. Trade fraud is unresolved below. | subtype, notice, correction opportunity, repeat, protected-storage intent | remove items/obstruction; stall cleanup/ownership removal; market restriction if policy permits | MARKET_INTEGRITY | D0_COMPLIANCE then D2 for deliberate repeat | compliance | market.compliance-failure proposed retirement as vague selector | Replace generic “failed review” with explicit subtype attributes. |
| market.extra-stall-alt | Extra stall through alt | Using an alternate account to obtain additional Market stall ownership contrary to one-stall-per-player rule. | Ordinary alt ownership outside Market is not automatically prohibited. | linked-account confidence, beneficial owner, number of stalls, intent | remove extra stall; market restriction if needed | MARKET_INTEGRITY, ACCOUNT_ABUSE | D2_STANDARD | integrity | market.extra-stall-alt | Define household/shared-IP false-positive protections. |
| reputation.false | False / unjustified reputation | Giving reputation not based on a real trade or interaction, including self-serving fabricated review behavior. | Coordinated campaigns -> reputation.coordinated. Alt self-manipulation -> reputation.alt-manipulation. | underlying interaction, giver/receiver link, intent, value, recurrence | remove invalid reputation; reputation restriction | REPUTATION_INTEGRITY | D2_STANDARD | integrity | reputation.false | Define what qualifies as a “real interaction.” |
| reputation.coordinated | Coordinated reputation manipulation | Organized mass reputation, review bombing, coordinated boosting/downvoting, or campaign manipulation by multiple participants. | Single false reputation -> reputation.false. | participants, coordination evidence, targets, scale | remove invalid reputation; reputation restriction | REPUTATION_INTEGRITY, COMPLICITY | D3_EXTENDED | integrity | reputation.coordinated | Decide whether organizers and participants share one base policy or role-based attributes. |
| reputation.alt-manipulation | Alt reputation manipulation | Using linked/controlled alternate accounts to manipulate reputation. | Extra Market stall -> market.extra-stall-alt. | account-control confidence, direction of reputation, scale, recurrence | remove invalid reputation; reputation restriction | REPUTATION_INTEGRITY, ACCOUNT_ABUSE | D3_EXTENDED | integrity | reputation.alt-manipulation | Keep account-link evidence private from public projection. |

### Complicity

| Stable ID | Public name | Definition / inclusions | Exclusions | Questions | Remedies | History groups | Decay | Class | Policy v1 mapping | Owner / gaps |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| complicity.encouraging-rule-breaking | Encouraging rule breaking | Deliberately encouraging another person to violate a server rule where the encouragement is material, repeated, or tied to an actual incident. | Mere discussion of rules or hypothetical behavior is not enough. Cheating/exploit encouragement -> complicity.encouraging-cheating. | underlying offense, specificity, audience, actual effect, repetition | remove instructional content if harmful | COMPLICITY | D1_SHORT or D2_STANDARD | behavioral | complicity.encouraging-rule-breaking | Current reason is extremely broad; owner should require material encouragement and relate severity to underlying conduct. |
| complicity.encouraging-cheating | Encouraging cheating / exploit abuse | Encouraging, instructing, or recruiting others to cheat or abuse exploits. | Sharing an exploit implementation -> exploit.bug-testing-sharing may also/usually apply. | underlying conduct, instructions, recruitment, success | remove cheat/exploit instructions | COMPLICITY, CHEATING, EXPLOIT_ABUSE | D2_STANDARD or D3_EXTENDED | integrity | complicity.encouraging-cheating | Avoid double-counting with exploit-sharing; choose primary finding + attributes. |
| complicity.assisting-cheating | Assisting / knowingly benefiting from cheating | Knowingly gearing, transporting, housing, profiting from, or otherwise materially assisting a cheater or benefiting from cheating. | Duplicate-item laundering -> complicity.laundering-duplicated-items. Evasion assistance -> evasion.assisting-other. | knowledge, assistance, benefit, duration, underlying cheat | remove/confiscate illicit benefits when causally linked | COMPLICITY, CHEATING | D3_EXTENDED | integrity | complicity.assisting-cheating | Define threshold for “knowingly benefiting” so innocent trades are protected. |
| complicity.laundering-duplicated-items | Laundering duplicated items | Knowingly storing, moving, selling, distributing, or concealing prohibited duplicated items for another person or to hide provenance. | Mere possession with uncertain knowledge -> exploit.duplicated-item-possession. | knowledge, transactions, concealment, quantity, beneficiary | confiscate duplicated value; reverse/repair economy effects where supported | COMPLICITY, EXPLOIT_ABUSE, MARKET_INTEGRITY | D3_EXTENDED | integrity | complicity.laundering-duplicated-items | Keep distinct because intent/concealment is stronger than possession. |

### Policy Gap / Unclassified Incident

| Stable ID | Public name | Definition / inclusions | Exclusions | Questions | Remedies | History groups | Decay | Class | Policy v1 mapping | Owner / gaps |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| policy-gap.unclassified-incident | Policy Gap / Unclassified Incident | Harmful, unsafe, disruptive, or unfair conduct that cannot honestly be classified under an owner-approved configured finding. Routes to Admin/Founder review and records facts without inventing policy. | Do not use merely because staff cannot remember the right reason. Search configured taxonomy first. Unknown exploit implementations normally fit exploit.bug-abuse, prohibited duplication, cheating.other-unfair-modification, or disruption.other-technical-abuse. | what happened, why no existing class fits, immediate risk, evidence, temporary containment needed? | only separately authorized emergency containment; no invented punishment | none until reclassified | D5_REVIEW_ONLY | review-only special path | none | Required safe fallback. Owner review should either classify under existing policy or approve a versioned new/changed offense for future cases. |

## Policy v1 coverage / migration map

Every active current reason maps below or is explicitly proposed for retirement as a selectable v2 finding.

| Policy v1 reason | Policy v2 destination |
| --- | --- |
| hate.general-toxicity | abuse.general-toxicity |
| hate.targeted-harassment | abuse.targeted-harassment |
| hate.discriminatory-statement | hate.discriminatory-expression |
| hate.abbreviated-slur-untargeted | hate.slur with form=abbreviated, targeted=false |
| hate.abbreviated-slur-targeted | hate.slur with form=abbreviated, targeted=true |
| hate.full-slur-untargeted | hate.slur with form=full, targeted=false |
| hate.full-slur-targeted | hate.slur with form=full, targeted=true |
| hate.extremist-symbol | hate.extremist-symbol |
| hate.advocating-hate-violence | hate.extremist-advocacy |
| harassment.sexual | abuse.sexual-harassment |
| safety.encouraging-self-harm | safety.encouraging-self-harm |
| privacy.personal-information-careless | privacy.personal-information-exposure |
| safety.credible-threat | safety.credible-threat |
| privacy.doxxing | privacy.doxxing |
| safety.blackmail-extortion | safety.blackmail-extortion |
| safety.grooming | safety.grooming |
| safety.illegal-exploitative-content | safety.illegal-exploitative-content |
| spam.low-level | chat.spam.low-level |
| spam.message-flooding | chat.spam.flooding |
| spam.private-message | chat.spam.private-message |
| spam.noise-pollution | chat.disruption.noise |
| language.non-english-public-chat | chat.language.non-english-public |
| spam.begging | chat.begging |
| content.moderate-inappropriate | content.inappropriate with lower severity attributes |
| content.extreme-inappropriate | content.inappropriate with higher severity attributes |
| content.explicit-sexual | content.explicit-sexual |
| identity.inappropriate-profile | split by actual surface to profile.inappropriate-username, profile.inappropriate-skin, or profile.inappropriate-other |
| advertising.minecraft-server | advertising.minecraft-server |
| advertising.unrelated-self-promotion | advertising.unrelated-self-promotion |
| identity.staff-impersonation | identity.staff-impersonation |
| advertising.malicious-link | security.malicious-link |
| politics.casual | chat.sensitive-topic-public |
| politics.moderate | chat.sensitive-topic-public |
| politics.extreme | chat.sensitive-topic-public; never use it as a substitute for hate.extremist-advocacy |
| account.unapproved-vpn | access.vpn-compliance; deliberate repeat/bypass may instead be access.vpn-evasion |
| account.sharing | account.sharing |
| account.headless-client | account.headless-client |
| account.unsafe-download | security.unsafe-download |
| account.theft | account.theft |
| complicity.encouraging-rule-breaking | complicity.encouraging-rule-breaking |
| complicity.encouraging-cheating | complicity.encouraging-cheating |
| complicity.assisting-cheating | complicity.assisting-cheating |
| complicity.laundering-duplicated-items | complicity.laundering-duplicated-items |
| evasion.harboring-ban-evader | evasion.assisting-other |
| dishonesty.falsifying-evidence | integrity.evidence-falsification |
| dishonesty.lying-investigation | integrity.investigation-dishonesty |
| staff.refusing-instruction | staff.instruction-refusal |
| evasion.mute | evasion.mute |
| evasion.ban | evasion.ban |
| evasion.network-identity-ban | retire as selectable finding; use evasion.ban and model NETWORK_IDENTITY_BAN as an authorized remedy |
| evasion.helping-another | evasion.assisting-other |
| exploit.illegal-duplication | exploit.prohibited-duplication |
| exploit.duplicated-possession-unclear | exploit.duplicated-item-possession with knowledge=uncertain; remedy-only path should be possible |
| exploit.duplicated-possession-knowing | exploit.duplicated-item-possession with knowledge=knowing |
| exploit.withholding | exploit.bug-withholding |
| exploit.minor-abuse | exploit.bug-abuse with structured impact attributes |
| exploit.major-abuse | exploit.bug-abuse with structured impact attributes |
| mechanics.chunk-loader | disruption.chunk-loading |
| mechanics.laggy-farm | disruption.laggy-build |
| mechanics.end-portal-obstruction | disruption.end-portal-obstruction |
| exploit.server-crash-attempt | disruption.server-crash-attempt |
| market.compliance-failure | market.stall-compliance; retire vague “failed review” selection after explicit subtype/questions exist |
| market.extra-stall-alt | market.extra-stall-alt |
| market.blacklist-evasion | evasion.restriction with restriction=market |
| reputation.false | reputation.false |
| reputation.coordinated | reputation.coordinated |
| reputation.alt-manipulation | reputation.alt-manipulation |
| reputation.blacklist-evasion | evasion.restriction with restriction=reputation |
| cheating.polar.template | retire as selectable finding; detection source only, classify confirmed conduct under cheating.hacked-client or another exact cheating finding |
| cheating.manual-client | cheating.hacked-client |
| cheating.xray-esp | cheating.hidden-information |
| cheating.freecam | cheating.hidden-information |
| cheating.baritone | cheating.pathfinding-automation |
| cheating.easy-place-printer | cheating.build-automation |
| cheating.pvp-indicator | cheating.hidden-information |
| cheating.minimap-display | cheating.hidden-information |
| cheating.unauthorized-autoclicker-mod | cheating.autoclicker-unauthorized |
| cheating.combat-autoclicker | cheating.autoclicker-combat |
| cheating.autoclicker-bypass | cheating.autoclicker-bypass |
| cheating.other-modification | cheating.other-unfair-modification |
| staff.public-punishment-argument | chat.public-case-argument |
| reports.spam | reports.spam |
| reports.false | reports.false |
| reports.abusive-content | retire as a surface-based duplicate; classify substantive hate/harassment/content/spam conduct and retain channel=report as an attribute |
| reports.harassing-staff | abuse.targeted-harassment with targetRole=staff |

## Newly identified coverage gaps

These are either directly stated in current public rules but absent from Policy v1, or common situations that require an owner decision.

### Direct rule gaps that should be represented

1. privacy.personal-information-pressure — public rules prohibit pressuring others to disclose identifying information.
2. security.malicious-file — public rules list malicious files as zero tolerance.
3. exploit.bug-testing-sharing — public rules prohibit testing, reproducing, or sharing unapproved bugs/exploits.
4. disruption.protected-area-violation — Spawn/Market are protected while wilderness griefing/raiding/theft/PvP remain allowed.
5. integrity.evidence-destruction — public rules explicitly prohibit evidence destruction.
6. staff.investigation-interference — public rules explicitly prohibit interference.
7. access.vpn-evasion — product decision requires deliberate evasion/refusal to be separate from access compliance.
8. disruption.other-technical-abuse — broad future-safe class for technical harm that is not a named exploit/crash method.
9. policy-gap.unclassified-incident — explicit review-only safety valve.

### Owner decisions before Policy v2 content can be called final

1. Player-to-player scams / trade fraud: current rules prohibit malicious scam links but do not clearly say whether deceptive in-game trades are punishable or part of permissive gameplay. Do not invent a market-fraud offense until owners decide.
2. Real-money trading / external transactions: not clearly governed by current rules. Decide allowed/prohibited/scope before adding a reason.
3. Ordinary alt accounts: current rules prohibit headless alts, extra Market stalls through alts, and sanction evasion, but do not clearly prohibit normal personally controlled alts. Confirm baseline.
4. Protected-area enforcement: confirm which Spawn/Market actions are punishable findings versus simply technically blocked/restored.
5. Malicious links/files: public rules say zero tolerance for malicious files, while Policy v1 malicious-link ladder is not strictly zero-tolerance. Decide whether malicious links share the same severe-safety policy.
6. Credible threats and doxxing: public rules say permanent-ban zero tolerance; current v1 allows temporary first outcomes. Resolve inconsistency.
7. Hate/extremism non-decay: decide whether extremist advocacy/glorification belongs in D4_NONDECAY or D3_EXTENDED.
8. Non-English public chat: define exceptions and maximum chat-only consequence. The offense itself must never yield a network ban.
9. VPN progression: define the exact boundary from D0 compliance into access.vpn-evasion and what owner-approved sanction bounds apply to deliberate repeat evasion.
10. Profile enforcement: define exactly which surfaces can be restricted-until-corrected and whether repeated deliberate re-violations create a separate behavioral escalation or remain the same finding with history.
11. Report restrictions: define a guaranteed emergency/safety reporting path even for a player under report restriction.
12. Staff instructions: define “reasonable instruction,” authority scope, and whether a player can decline optional investigative questions without a refusal finding.
13. Bug reporting window: define “immediately” operationally so accidental discovery does not become withholding before a reasonable reporting opportunity.
14. Uncertain duplicated-item possession: approve a remedy-only/confiscation path without an adverse behavioral finding when knowledge cannot be established.
15. Autoclickers: confirm the public rule interpretation that approved/external autoclickers are allowed outside combat but any autoclicker use in combat is prohibited.
16. Politics/sensitive topics: decide whether one stable offense with persistence/severity attributes is preferred over separate tiers. W0 recommends one offense so “extreme politics” cannot absorb hate advocacy.
17. Market compliance: approve retirement of generic market.compliance-failure in favor of explicit stall-compliance subtype facts.
18. Legacy migration: main currently has no populated aliases/removed-reasons. At v2 migration time, owners/W4 must decide which v1 IDs become aliases to canonical v2 IDs and which become removed-history-only IDs where one-to-one semantic aliasing would be misleading.

## Questionable / structurally wrong current Policy v1 ladders

W0 is not selecting replacement durations. These items need owner review:

- language.non-english-public-chat currently progresses from warnings/mute into network bans. That directly conflicts with the Policy v2 decision that non-English chat itself never becomes a ban.
- identity.inappropriate-profile currently combines username, skin, and profile and escalates from a correction kick into long/permanent bans. The primary model should be restricted until corrected; deliberate refusal/repeat conduct can be handled separately.
- account.unapproved-vpn mixes access compliance and punishment escalation. Split initial access denial/correction from deliberate evasion.
- politics.extreme is vulnerable to swallowing extremist/hate advocacy. Those must be distinct findings.
- hate.extremist-symbol does not encode intent/context. A symbol finding needs context questions before a severe sanction can be resolved.
- privacy.doxxing and safety.credible-threat conflict with the public “zero tolerance = permanent ban” wording because v1 contains temporary outcomes.
- account.unsafe-download is too vague because “questionable” is not a durable conduct standard, while genuinely malicious files deserve a specific severe-safety reason.
- complicity.encouraging-rule-breaking is broad enough to cover trivial and severe underlying behavior with the same reason. Consequence policy should depend on materiality and underlying conduct.
- exploit.duplicated-possession-unclear can punish a player precisely when knowledge is unclear. Policy v2 should permit confiscation/remediation without asserting intentional misconduct.
- exploit.minor-abuse and exploit.major-abuse encode a subjective label instead of structured impact facts.
- mechanics.chunk-loader and mechanics.laggy-farm mix removable technical conditions with punishment. Initial remediation should be modeled separately from deliberate refusal/repeat conduct.
- market.compliance-failure is too vague for a stable offense and should not remain selectable once specific stall facts exist.
- evasion.network-identity-ban is an enforcement outcome/remedy disguised as an offense.
- cheating.polar.template is a detector-specific evidence source disguised as an offense.
- reports.abusive-content and reports.harassing-staff duplicate substantive content/harassment policy based only on the surface where conduct occurred.
- market.blacklist-evasion and reputation.blacklist-evasion are instances of one broader restriction-evasion behavior and should share history logic.

## Recommended migration semantics

- Preserve all v1 historical reason IDs exactly in existing records.
- Do not rewrite old cases.
- Use direct aliases only where the old ID has one unambiguous semantic successor.
- Use removed-reason metadata when an old reason is split, merged with materially different semantics, or should no longer be selectable. Examples likely include identity.inappropriate-profile, cheating.polar.template, evasion.network-identity-ban, reports.abusive-content, and market.compliance-failure.
- New v2 findings should store the structured incident attributes needed to explain which branch of a broad conduct class applied.
- Public projection should expose a compact safe reason name and sanction/remedy status without evidence, account-link signals, IP/VPN detection internals, or staff-only decision data.

## W0 recommendation

Use this matrix as the owner-review baseline, not as automatically approved sanctions. W1 can build the taxonomy/config contract around stable IDs, incident questions, behavior groups, decay classes, remedies, and explicit REQUIRES_REVIEW behavior while leaving sanction lengths/bounds as owner-owned configuration.

Before production cutover, owners should resolve the 18 decisions above and then convert the approved subset into versioned policy data.