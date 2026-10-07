# Policy v2 numerical simulator — first tuning pass

Tracking: #435  
Base: owner-approved policy direction in #423 / PR #424  
Status: **simulation candidate, not production authority**

This pass uses the real Policy v2 `HistoryEvaluator` / `PolicyResolver` semantics. It is intended to turn the owner-approved decay direction into concrete, testable numerical behavior before the first real Policy v2 configuration is published.

## Candidate numerical constants

### Decay classes

| Class | Direct half-life | Pattern half-life | Max effective half-life |
| --- | ---: | ---: | ---: |
| D1_SHORT | 30d | 120d | 2.0x |
| D2_STANDARD | 90d | 270d | 2.5x |
| D3_EXTENDED | 180d | 540d | 3.0x |
| D4_NONDECAY | non-decaying | non-decaying | n/a |

Candidate pattern-strength scale:

`repeat-half-life-increase-per-prior = 0.50`

The value is applied to the **remaining fading pattern persistence**, not to a permanent raw repeat count.

### Relationship weights

Starting simulation seeds:

- exact same canonical offense: `1.00`
- strongly related conduct: `0.75`
- moderately related conduct: `0.35`
- unrelated conduct: `0.00`

These are not inferred from category membership. Every non-zero relationship must be explicit.

### History bands

Recommended general starting bands:

| Band | Total related-history contribution | Meaning |
| --- | ---: | --- |
| H0 baseline | `0.00 <= h < 0.50` | history is too old/weak to change the normal outcome |
| H1 related repeat | `0.50 <= h < 1.50` | meaningful related recurrence |
| H2 established pattern | `1.50 <= h < 2.25` | multiple/reinforced related incidents |
| H3 chronic pattern | `2.25 <= h < 3.25` | strong chronic/recent pattern |
| H4 heavy chronic | `h >= 3.25` | do not silently continue escalating; route to configured Admin review or another explicit terminal rule |

These are **per-offense history windows**, not a global player badness score. Individual offenses may intentionally cap or collapse bands.

## Clean-period behavior

Rounded total contributions using the candidate `0.50` pattern scale:

| Scenario | D1_SHORT | D2_STANDARD | D3_EXTENDED |
| --- | ---: | ---: | ---: |
| one exact prior, 7d old | 0.851 | 0.948 | 0.973 |
| one exact prior, 30d old | 0.500 | 0.794 | 0.891 |
| one exact prior, 90d old | 0.125 | 0.500 | 0.707 |
| one exact prior, 180d old | 0.016 | 0.250 | 0.500 |
| one exact prior, 365d old | ~0.000 | 0.060 | 0.245 |
| three rapid priors at 21d / 14d / 7d | 2.339 | 2.754 | 2.873 |
| repeated cycle at 180d / 90d / 7d | 1.114 | 1.828 | 2.272 |

Interpretation:

- D1 nuisance history clears quickly.
- D2 normal behavioral history remains relevant for months but a single old event stops changing the outcome after enough clean time.
- D3 integrity/severe-pattern history lingers longer.
- repeated D3 behavior can remain meaningfully elevated even when no single old event would justify the same escalation by itself.

## Fading chronic-pattern behavior

Three rapid related incidents were moved forward through clean time:

| Scenario | D1_SHORT | D2_STANDARD | D3_EXTENDED |
| --- | ---: | ---: | ---: |
| ~180d clean after rapid pattern | 0.166 | 1.062 | 1.764 |
| ~365d clean after rapid pattern | 0.015 | 0.427 | 1.089 |

This is the intended distinction between:

- **one old mistake**, which fades normally; and
- **a demonstrated repeated pattern**, which fades more slowly but still eventually clears.

There is no permanent invisible recurrence flag.

## Relationship sanity check

For a D3 offense with one related incident 30 days ago:

- exact same offense: about `0.891`;
- strongly related at `0.75`: about `0.668`;
- moderately related at `0.35`: about `0.312`;
- unrelated: `0.000`.

Under the candidate bands:

- exact/strong related conduct can produce H1;
- one moderate neighboring incident does not automatically escalate;
- unrelated history has no effect.

This is the behavior we wanted from the relationship graph.

## X-ray and Freecam

Owner-approved baseline for both:

**21-day network ban**

Recommended D3_EXTENDED candidate progression:

| Related-history band | Candidate result |
| --- | --- |
| H0 baseline | 21d network ban |
| H1 related repeat | 30d network ban |
| H2 established pattern | 60d network ban |
| H3 chronic pattern | 90d network ban |
| H4 heavy chronic | Admin Review |

Important: **history alone does not automatically create a permanent ban**.

Example outputs from the real resolver model:

- no related history -> **21d**;
- one exact X-ray case 180 days earlier -> contribution `0.500` -> **30d**;
- X-ray 60d earlier + Freecam 15d earlier -> contribution about `1.512` -> **60d**;
- three recent exact cheating cases at 21d / 14d / 7d -> contribution about `2.873` -> **90d**;
- an extreme rapid chronic sequence above `3.25` -> **Admin Review**, not an accidental permanent ban.

X-ray <-> Freecam starts as a strong `0.75` relationship.

## Owner-policy invariants retained

The numerical bands do not override substantive policy:

- pure non-English-public-chat violations remain chat-only and capped at a 7d mute;
- a first unapproved VPN detection remains a compliance condition, so it contributes no behavioral-history score by itself;
- knowledge-unproven duplicated-item possession remains remedy-only;
- profile corrections remain compliance conditions until deliberate repeat/bypass becomes a separate behavioral finding;
- permanent bans remain explicit terminal policy, not a high scalar score;
- doxxing, confirmed credible threats, grooming, blackmail/extortion, illegal exploitative content, deliberate server-crash attempts, and intentional malware/phishing remain explicit severe/terminal rules where approved;
- fully overturned findings contribute zero;
- factual reclassification uses the corrected finding;
- leniency-only sanction changes do not rewrite behavioral history;
- mapped pre-v2 findings use the same history math as equivalent native v2 findings.

## First tuning conclusion

The candidate `0.50` pattern scale and `0.50 / 1.50 / 2.25 / 3.25` history boundaries are a good first fit.

They satisfy the main qualitative goals:

1. one recent same offense matters;
2. one event fades smoothly back to baseline;
3. three rapid repeats reach a chronic band;
4. repeated D2/D3 behavior persists longer than a single event;
5. a year of clean time materially clears D1/D2 history;
6. a demonstrated D3 pattern can still matter after a year without being permanent;
7. one moderate neighboring offense does not over-escalate;
8. unrelated history remains zero;
9. heavy chronic history fails safely into review instead of accidentally generating permanent punishment.

No production configuration or authority is changed by this report.
