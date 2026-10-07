# Policy v2 sanction anchor proposal

Tracking: #423  
Companion: `docs/policy-v2-owner-policy-proposal.md`  
Status: **simulation draft — NOT owner-approved**

These anchors are intended to seed scenario simulation. They are not fixed ladders. The final resolver should select among configured outcomes from current incident facts plus related decayed history.

## Reading this document

- **Baseline** means a representative confirmed incident with no material related history.
- **Aggravated** means stronger current-incident facts and/or meaningful related history.
- **Ceiling** is the strongest consequence this offense should be able to produce by itself. Separate evasion/safety findings can exceed it.
- Remedies are listed separately from sanctions.
- Permanent outcomes require explicit terminal policy; they are never inferred merely because a score got large.

Standard timed options should stay relatively small and understandable:

- warning
- 6h
- 1d
- 3d
- 7d
- 14d
- 30d
- 60d
- 90d
- permanent only where explicitly authorized

## Chat & spam

| Offense | Baseline | Aggravated / related history | Ceiling under this finding | Remedies / notes |
| --- | --- | --- | --- | --- |
| chat.spam.low-level | warning | 6h -> 1d mute | 7d mute | Stops quickly after correction should remain near warning. |
| chat.spam.flooding | 6h mute | 1d -> 3d -> 7d | 30d mute | Automated network/bot flooding may reclassify to technical disruption. |
| chat.spam.private-message | warning or 6h mute | 1d -> 3d | 7d mute | Targeted unwanted contact may reclassify to harassment. |
| chat.disruption.noise | warning + stop/remove source | 6h -> 1d -> 3d mute/restriction | 7d | Severe machine/server impact uses disruption policy. |
| chat.language.non-english-public | correction/warning | 6h -> 1d -> 3d | **7d mute, never network ban** | Emergency/translation/ticket exceptions. |
| chat.begging | warning | 6h -> 1d | 3d mute | One-off requests are not offenses. |
| chat.sensitive-topic-public | warning or 6h mute after direction | 1d -> 3d | 7d mute | Hate/extremism is reclassified, not treated as “more politics.” |
| chat.public-case-argument | warning | 6h -> 1d | 3d mute | Mere disagreement/appeal is allowed. |

## Harassment & abuse

| Offense | Baseline | Aggravated / related history | Ceiling | Notes |
| --- | --- | --- | --- | --- |
| abuse.general-toxicity | warning / 6h mute | 1d -> 3d | 7d mute | Ordinary profanity is allowed unless used abusively. |
| abuse.targeted-harassment | 1d mute | 3d -> 7d -> 14d | 30d mute | Sustained campaigns across accounts can create separate evasion findings. |
| abuse.sexual-harassment | 7d mute | 14d mute + 3d ban -> 30d ban | 90d ban + long/permanent mute | Grooming/coercive exploitation reclassifies to safety.grooming. |

## Hate & extremism

| Offense | Baseline | Aggravated / related history | Ceiling | Notes |
| --- | --- | --- | --- | --- |
| hate.discriminatory-expression | 3d mute | 7d -> 14d -> 30d | 60d/permanent mute | Protected-class attack without slur. |
| hate.slur, untargeted/context lower | 14d mute | 30d -> 60d | permanent mute | Targetedness/obfuscation/repetition are structured attributes. |
| hate.slur, targeted | 30d mute | 60d -> 90d | permanent mute + up to 30d ban for chronic severe targeting | Do not create separate stable slur IDs. |
| hate.extremist-symbol | 14d ban + removal | 30d -> 60d -> 90d | permanent ban only for explicit terminal/intimidation policy | Historical/condemnatory context is not advocacy. |
| hate.extremist-advocacy, nonviolent glorification/recruitment | 30d mute + 14d ban | 30d -> 60d ban | 90d ban + permanent mute | Non-decaying history recommended. |
| hate.extremist-advocacy, genocide/hate-violence advocacy | 90d ban + permanent mute | permanent ban when repeated/organized or terminal facts apply | permanent ban | Specific credible threat uses safety.credible-threat. |

## Inappropriate / sexual content

| Offense | Baseline | Aggravated | Ceiling | Remedies |
| --- | --- | --- | --- | --- |
| content.inappropriate, lower | warning + removal | 1d mute -> 3d | 7d mute | remove content/build |
| content.inappropriate, severe | 7d mute + removal | 14d -> 30d mute | 60d mute + 14d ban | structured explicitness/audience/intent |
| content.explicit-sexual | 7d mute + removal | 14d mute + 3d ban -> 30d ban | 90d ban | targeted conduct may reclassify to sexual harassment/grooming |

## Safety, threats & privacy

| Offense | Baseline | Aggravated | Ceiling | Decay |
| --- | --- | --- | --- | --- |
| safety.encouraging-self-harm | 14d mute + 7d ban | 30d -> 60d ban | permanent only under explicit severe terminal facts | D3, consider D4 for severe |
| privacy.personal-information-exposure, careless | removal + warning | 1d -> 7d ban | 30d | D2/D3 |
| privacy.personal-information-pressure | 1d mute | 3d -> 7d -> 14d | 30d mute/ban for coercive repeated behavior | D2 |
| safety.credible-threat | **permanent ban after Admin+ confirmation** | n/a | permanent | D4 |
| privacy.doxxing | **permanent ban after Admin+ confirmation** | n/a | permanent | D4 |
| safety.blackmail-extortion | **permanent ban** | n/a | permanent | D4 |
| safety.grooming | **permanent ban** | n/a | permanent | D4 |
| safety.illegal-exploitative-content | **permanent ban** | n/a | permanent | D4 |
| security.malicious-link/file, intentional malware/phishing | **permanent ban after Admin+ confirmation** | n/a | permanent | D4 |

## Advertising / impersonation

| Offense | Baseline | Aggravated | Ceiling | Notes |
| --- | --- | --- | --- | --- |
| advertising.minecraft-server | 30d mute + 7d ban | 30d ban -> 90d | permanent ban only chronic/organized | deliberate server advertising |
| advertising.unrelated-self-promotion | warning / 1d mute | 7d -> 14d mute | 30d mute + 7d ban | distinguish normal conversation/link sharing |
| identity.staff-impersonation | 7d mute + 1d ban | 7d -> 30d ban | 90d/permanent for serious fraud/evasion | official-service fraud may be more severe |

## Accounts & access

| Offense / condition | Baseline | Aggravated | Ceiling | Notes |
| --- | --- | --- | --- | --- |
| access.vpn-compliance | restrict until VPN disabled/approved | n/a | no timed sanction | D0 condition |
| access.vpn-evasion | warning / short restriction | 1d -> 7d -> 30d | **90d ban** | deliberate repeated bypass only |
| account.sharing | 7d ban | 30d -> 90d | permanent | account owner remains responsible |
| account.headless-client | 14d ban | 30d -> 60d -> 90d | permanent | ordinary non-headless alts are not automatically prohibited |
| account.theft | permanent ban after strong evidence | n/a | permanent | Admin+ |
| profile.inappropriate-username | restrict until corrected | repeat deliberate violation becomes behavioral | condition itself has no timed ceiling | D0 |
| profile.inappropriate-skin | restrict until corrected where reliable | repeat deliberate violation becomes behavioral | condition itself has no timed ceiling | D0 |
| profile.inappropriate-other | hide/restrict until corrected | repeat deliberate violation becomes behavioral | condition itself has no timed ceiling | D0 |

## Staff cooperation / evidence integrity

| Offense | Baseline | Aggravated | Ceiling | Notes |
| --- | --- | --- | --- | --- |
| integrity.evidence-falsification | 30d ban | 90d | permanent | intentional fabrication only |
| integrity.investigation-dishonesty | 3d ban | 7d -> 30d -> 60d | 90d | material intentional lie, not memory error |
| integrity.evidence-destruction | 7d ban | 30d -> 60d | 90d/permanent if tied to severe offense | may aggravate underlying finding |
| staff.investigation-interference | warning / 1d ban | 3d -> 7d -> 30d | 60d | direct interference |
| staff.instruction-refusal | warning | 1d -> 7d | 30d | instruction must satisfy “reasonable instruction” test |

## Punishment / restriction evasion

| Offense | Baseline | Aggravated | Ceiling | Notes |
| --- | --- | --- | --- | --- |
| evasion.mute | 7d network ban | 30d -> 60d -> 90d | permanent | original mute remains in effect as applicable |
| evasion.ban | add/reset substantial ban, normally at least 30d | 60d -> 90d | permanent | exact interaction with original ban should be explicit in config |
| evasion.restriction | 7d ban or extended matching restriction | 30d -> 90d | permanent | Market/reputation/report/access |
| evasion.assisting-other | 7d ban | 30d -> 90d | permanent | requires knowledge/material assistance |

## Cheating & unauthorized modifications

Cheating should use current-tool facts instead of one generic severity.

| Conduct | Baseline | Aggravated / related history | Ceiling | Remedies |
| --- | --- | --- | --- | --- |
| cheating.hacked-client, broad confirmed client | 30d ban | 60d -> 90d | permanent | confiscate illicit gains if causally linked |
| cheating.hidden-information: X-ray/ESP with material gain | 30d ban | 60d -> 90d | permanent | confiscate illicit gain |
| cheating.hidden-information: Freecam used for advantage | 14d ban | 30d -> 60d -> 90d | permanent | confiscate only if causal gain |
| cheating.hidden-information: prohibited indicator/radar | 3d ban | 7d -> 21d -> 30d/60d | 90d | lower baseline than X-ray |
| cheating.pathfinding-automation | 14d ban | 30d -> 60d -> 90d | permanent | |
| cheating.build-automation | warning / require disable | 7d -> 30d -> 60d | 90d | accidental first use can remain warning |
| cheating.autoclicker-unauthorized outside combat | switch warning | second warning -> 1d -> 7d | 30d | approved tools outside combat remain allowed |
| cheating.autoclicker-combat | 7d ban | 30d -> 60d -> 90d | permanent | applies regardless of autoclicker source |
| cheating.autoclicker-bypass | 7d ban | 30d -> 60d -> 90d | permanent | intentional bypass |
| cheating.other-unfair-modification | warning or 7d depending capability | 30d -> 60d | 90d | unknown serious cheat may require review rather than guessing |

Detection source such as Polar must never be a separate behavioral history family.

## Exploits, duplication & technical abuse

| Offense | Baseline | Aggravated | Ceiling | Remedies |
| --- | --- | --- | --- | --- |
| exploit.prohibited-duplication, low scale | 14d ban | 30d -> 60d | 90d | confiscate duplicated value |
| exploit.prohibited-duplication, major economic scale | 30d ban | 60d -> 90d | permanent only explicit catastrophic/chronic terminal rule | confiscate/repair |
| exploit.duplicated-item-possession, knowledge uncertain | **no adverse finding / no ban** | n/a | n/a | confiscation/remedy only |
| exploit.duplicated-item-possession, knowing | 14d ban | 30d -> 90d | permanent | confiscation |
| exploit.bug-withholding | 7d ban | 30d -> 60d | 90d/permanent for major concealed security/server risk | remediation |
| exploit.bug-testing-sharing | 7d ban | 30d -> 60d | 90d/permanent for widespread harmful release | remove instructions/remediate |
| exploit.bug-abuse, low impact | 7d ban | 30d -> 60d | 90d | remediation |
| exploit.bug-abuse, significant impact | 30d ban | 60d -> 90d | permanent only explicit catastrophic terminal rule | remediation/confiscation |
| disruption.chunk-loading | warning + removal | 1d -> 7d | 30d | initial state is mostly remediation |
| disruption.laggy-build | warning + removal | 1d -> 7d | 30d | requires unreasonable impact or refusal |
| disruption.end-portal-obstruction | 7d ban + removal | 30d -> 90d | permanent | |
| disruption.protected-area-violation | restoration + warning | 1d -> 7d -> 30d | 60d | only deliberate bypass/repeated interference |
| disruption.other-technical-abuse | 7d–30d depending impact | 60d -> 90d | permanent explicit severe case | broad future-safe class |
| disruption.server-crash-attempt | **permanent ban** | n/a | permanent | D4 |

Allowed TNT, rail, and carpet duping are excluded from prohibited duplication unless an explicit temporary restriction was validly announced before the conduct.

## Complicity

| Offense | Baseline | Aggravated | Ceiling | Remedies |
| --- | --- | --- | --- | --- |
| complicity.encouraging-rule-breaking | warning / 1d depending underlying rule | 3d -> 14d | 30d | material encouragement required |
| complicity.encouraging-cheating | 3d ban | 14d -> 30d -> 60d | 90d | remove instructional content |
| complicity.assisting-cheating | 7d ban | 30d -> 60d -> 90d | permanent | confiscate causally linked benefit |
| complicity.laundering-duplicated-items | 14d ban | 30d -> 90d | permanent | confiscate/reverse duplicated value |

## Reports

| Offense | Baseline | Aggravated | Ceiling | Notes |
| --- | --- | --- | --- | --- |
| reports.spam | warning | 1d -> 7d report restriction | 30d report restriction | emergency path remains available |
| reports.false | warning or 7d report restriction | 30d -> long/permanent ordinary report restriction | ordinary report channel may be permanent-restricted | emergency path remains available |
| substantive abusive content in reports | classify actual hate/harassment/content offense | n/a | n/a | report surface is an attribute, not duplicate offense |

## Market

The generic `market.compliance-failure` selector should retire.

| Conduct | Baseline | Aggravated | Ceiling | Remedies |
| --- | --- | --- | --- | --- |
| market.extra-stall-alt | remove extra stall + warning | 7d -> 30d -> 90d Market restriction | permanent Market restriction | remove extra stall |
| market.protected-storage-abuse | correct/remove storage | 7d -> 30d Market restriction for deliberate repeat | 90d/permanent Market restriction | remove prohibited storage |
| market.stall-obstruction | correction/removal | 7d Market restriction for deliberate repeat | 30d | cleanup |
| market.listing-storage-abuse | correction/removal | 7d -> 30d Market restriction | 90d | cleanup |
| Market blacklist evasion | use evasion.restriction | 30d/90d as related history grows | permanent | |

## Reputation

| Offense | Baseline | Aggravated | Ceiling | Remedies |
| --- | --- | --- | --- | --- |
| reputation.false | remove reputation + warning | 7d -> 30d restriction | 90d/permanent reputation restriction | remove invalid reputation |
| reputation.coordinated | 30d reputation restriction | 90d | permanent | remove campaign results |
| reputation.alt-manipulation | 30d reputation restriction | 90d | permanent | remove invalid reputation |
| reputation restriction evasion | evasion.restriction | 30d -> 90d ban/restriction | permanent | |

## External-value / RMT integrity

If owners approve the companion proposal:

| Offense | Baseline | Aggravated | Ceiling | Notes |
| --- | --- | --- | --- | --- |
| integrity.real-money-trading | 30d ban | 60d -> 90d | permanent for organized/chronic conduct | official owner-approved server transactions excluded |
| real-world payment fraud / extortion | classify safety.blackmail-extortion or appropriate fraud/security policy | severe review | permanent where severe-safety terminal policy applies | do not treat as ordinary in-game scam |

Ordinary in-game trade deception remains allowed unless another explicit rule is violated.

## History relationship starting points for simulation

These are **simulation seeds**, not approved weights.

- exact same canonical offense: 1.00
- same highly related behavior group: 0.75
- clearly related neighboring conduct: 0.35
- unrelated: 0.00

Examples:

- X-ray <-> Freecam/hidden-information: 0.75
- X-ray <-> combat autoclicker: 0.35
- manual hacked client <-> any confirmed cheating: 0.75
- assisting cheating <-> direct cheating: 0.50
- targeted harassment <-> discriminatory targeted harassment: 0.50–0.75
- sexual harassment <-> targeted harassment: 0.50
- doxxing <-> personal-information exposure: 0.50
- doxxing <-> credible threat: 0.50
- Market manipulation <-> reputation manipulation: 0.35
- any sanction evasion <-> other sanction evasion: 0.75
- spam <-> cheating: 0.00

Every non-zero relationship should eventually be explicitly encoded; category equality must never create a relationship automatically.

## Simulator acceptance scenarios

Before owners approve numeric values, run at least these cases against every representative offense:

1. first offense, no history;
2. same offense 7 days later;
3. same offense 30 days later;
4. same offense one direct half-life later;
5. same offense after a long clean period;
6. three rapid repeated incidents;
7. repeated cycles of offense -> partial decay -> offense;
8. strongly related different offense;
9. moderately related offense;
10. unrelated offense;
11. prior more-serious related offense;
12. appeal leniency only;
13. factual reclassification;
14. full overturn;
15. policy version change;
16. old pre-v2 mapped history;
17. unclassified novel incident;
18. compliance condition corrected immediately;
19. compliance condition deliberately bypassed;
20. severe terminal-policy case.

The goal is not to force every row into a smooth numeric progression. The goal is to catch surprising outcomes before the policy becomes authoritative.
