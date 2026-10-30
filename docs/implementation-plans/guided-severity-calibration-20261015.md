# Guided punishment severity: calibration review (NOT APPROVED)

**Prepared:** 2026-10-15. **Owner approval:** pending. **Release:** blocked.

This document compares the historical guided **prototype** against the *repository default*
`paper/src/main/resources/reason-policies.yml` version `2026-10-10.1`.
It does **not** use the owner's customized live policy file (historically `2026-09-26.1`)
and must not be treated as a live configuration, policy approval, or recommendation to punish.

## Controlled assumptions

- Target and staff rank allowed, applicable reason is selectable.
- Relevant history selects **first policy step (step 0)**, with no ongoing sanctions.
- Staff assessment is **Balanced** (0), policy version is the repository default.
- Prototype: base impact 10; sustained +15; large scale +30; ignored verified warning +10;
  targeted +15; persistent placed content +10.
- Step offset: impact 25–44 adds 1; 45+ adds 2; 0–24 adds 0. The step is capped by the policy.
- Temporary mute duration is interpolated to the *next same-type configured tier*
  at 50%; the next tier must not be treated as a generally authorized automatic upgrade.
- Unsafe, mixed, permanent, network-ban, confiscation, market or reputation remedies
  are **review-only**, not issued by the guided resolver.
- The row calculations use source logic and the repository's default ladders, not actual
  `PunishmentDraftWorkflow` or rank/history/storage/end-to-end execution.
- Unknown/unsupported channels, allegations or evidence may add further mandatory review.
- Voice noise retains its configured reason ID for expert review, but ordinary chat mutes are not assumed to mute voice.

## Current prototype examples — not approved

| Scenario (first offense) | Internal reason | Impact | Prototype disposition |
|---|---|---:|---|
| Isolated public chat spam, no prior warning | `spam.low-level` | 10 | Warning |
| Isolated public chat spam, ignored verified warning | `spam.low-level` | 20 | Warning |
| Sustained flooding, no prior warning | `spam.message-flooding` | 25 | 15-hour mute |
| Sustained flooding, ignored verified warning | `spam.message-flooding` | 35 | 15-hour mute |
| Large-scale flooding, no prior warning | `spam.message-flooding` | 40 | 15-hour mute |
| Large-scale flooding, ignored verified warning | `spam.message-flooding` | 50 | 2-day mute |
| Isolated private-message spam, no prior warning | `spam.private-message` | 10 | Warning |
| Sustained private-message spam, no prior warning | `spam.private-message` | 25 | 15-hour mute |
| Sustained voice noise, no prior warning | `spam.noise-pollution` | 25 | Manual review required: voice mute enforcement is not verified |
| Isolated targeted verbal harassment, no prior warning | `hate.targeted-harassment` | 25 | 2-day mute |
| Repeated targeted verbal harassment, no prior warning | `hate.targeted-harassment` | 40 | 2-day mute |
| Repeated targeted verbal harassment, ignored verified warning | `hate.targeted-harassment` | 50 | 5-day mute |
| X-ray / ESP | `cheating.xray-esp` | 10 | Manual review: configured network ban plus confiscation |
| Major exploit abuse | `exploit.major-abuse` | 10 | Manual review: network ban and remediation |
| Ban evasion | `evasion.ban` | 10 | Manual review: permanent network ban policy |
| Market compliance failure | `market.compliance-failure` | 10 | Manual review: stall removal / market blacklist |
| Alt-account reputation manipulation | `reputation.alt-manipulation` | 10 | Manual review: reputation blacklist |
| Advertising another server | `advertising.minecraft-server` | 10 | Manual review: mixed mute / ban policy |

**Potential policy mismatches to resolve:** Ignoring a warning can leave an outcome identical
to no warning; minor ignored-warning spam still produces a warning; sustained first-offense
flooding automatically proposes 15 hours; isolated targeted harassment proposes 2 days.
These are findings about *current behavior*, not judgments about appropriate sanction lengths.
The owner must make the policy decision.

## This change set: versioned profile model

`GuidedSeverityProfile` moves numeric weights, thresholds and duration fractions into one
strictly validated immutable **domain** value object. The preserved prototype is explicitly
versioned `prototype-20261013-unapproved`. A `GuidedIncidentWizard.Finding` captures
the **exact** immutable profile object used for scoring; the resolver rejects attempts to
calculate using any other object, including a different policy with the same version name.
The recorded explanation contains the profile version.

The new trial profile exists **only inside tests**. It is not loaded from production YAML,
not selectable by staff, not owner-approved and not registered in Paper startup. Existing
default-off feature behavior is unchanged. No live `reason-policies.yml` is touched.

## Current offline regression snapshot (2026-10-23; NOT OWNER APPROVAL)

The new GuidedPolicyCalibrationMatrixTest runs **14 representative fact/history scenarios**
against the actual repository default reason-policies.yml version 2026-10-10.1.
It checks warning/no-warning/ignored cases, spam intensity, existing history,
targeted harassment, voice-chat and cheating manual-review boundaries. These are
historical prototype parity tests; they deliberately fail if the bundled policy
version changes until the scenario expectations have been reviewed.

GuidedPolicyResolutionTest also rejects automatic transitions across a decreasing
temporary-mute ladder or from an unsupported baseline. This is a fail-closed
guard, not a new severity calibration.

These tests do NOT read the customized live server policy, establish voice enforcement,
prove actual rank/approval/runtime behavior, or approve any punishment length.
Production guided mode must remain disabled pending signoff.

## Remaining gates (issue #616)

1. Owner reviews each scenario and records acceptable sanction **type and exact range**
   for first offense, repeated history and warning status. Include no-warning, warning
   obeyed, ignored and unknown as distinct observed facts.
2. Compare additional Geyser/Bedrock and server-specific channels with actual sanction
   enforcement (e.g., voice mute propagation). Incompatible types require manual review.
3. Define the **approved** profile/version/config format, explicit change-control and
   validation against authoritative `reason-policies.yml`; prefer separate opt-in config
   over silently modifying live custom policy files.
4. Decide whether impact changes **within-tier duration**, **allowed tier selection**,
   or **manual-review routing**. Prevent heuristic jumps across protected policy classes,
   even at high impact. Never invent permanent, ban, mixed or confiscation sanctions.
5. Record immutable profile version and values/fingerprint in durable drafts/audits; require
   current version at confirmation and protect against in-place version-name reuse.
6. Test exact policy/history/rank/approval behavior in isolated Java/Bedrock staging, rerun
   Linux/real API matrices and obtain explicit owner acceptance before production enablement.

**Do not merge a calibrated enforcement cutover or enable guided punishment on live** based
on this test-only profile infrastructure alone.

## Experimental within-tier duration calibration (2026-10-16, NOT APPROVED)

Issue #616 identified a real collision: sustained public-chat flooding with no
prior verified warning scores 25, and the same incident after a verified ignored
warning scores 35. Both fall inside the moderate tier [25,45), so the original
duration interpolation based only on the staff preference produces the same
sanction length despite distinct reported facts.

The new test-only \`impactDurationWeight\` field (0.0 to 0.5) allows a
**non-production** trial profile to blend the existing staff preference ratio
with normalized factual impact **within the same threshold tier**. The shipped
prototype keeps the value at **0.0**, so its exact historical behavior remains
unchanged and no production opt-in mechanism is exposed.

The experimental test fixture uses moderate/severe boundaries 25/45, Balanced
staff preference 0.50, and impact weight 0.30. On a hypothetical
15-minute-to-60-minute same-type mute ladder, the two same-tier examples
interpolate to approximately:

| Incident | Score | Tier | Test-only bounded duration |
|---|---:|---|---|
| Sustained flooding, no verified warning | 25 | Moderate | 31 minutes |
| Sustained flooding, ignored verified warning | 35 | Moderate | 38 minutes |

This is **not** an owner recommendation for 31 or 38 minute mutes and says
nothing about the actual customized live \`reason-policies.yml\`. The resolver
still requires a matching immutable profile, a valid configured sanction
ladder, safe intermediate steps, ordinary warning/temporary-mute remedies,
and exact lower/upper duration bounds. There is no general power to invent
bans, permanent penalties, new policy tiers or unsanctioned remedies.

### Remaining owner decisions

- Which factual aggravators should change the **sanction tier** rather than
  only the length within an already-approved temporary-mute tier?
- Should any ignored-warning increase be automatic? Must the warning be
  verified, relevant, clearly communicated and still applicable?
- What are the approved real-world durations for sustained flooding, repeat
  harassment, private-message/sign/voice incidents, and repeat history?
- What absolute tolerance for staff preference and impact adjustment is
  acceptable on each offense family?
- Which reason/channel combinations must always require experienced
  staff review, regardless of impact?
- How do we sign, version, stage and audit policy changes without overwriting
  the production-customized file?

Do **not** enable guided punishments on the live server before written
approval and separate Java/Bedrock staging acceptance.
