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
| Sustained voice noise, no prior warning | `spam.noise-pollution` | 25 | 15-hour mute (channel enforcement needs runtime verification) |
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
