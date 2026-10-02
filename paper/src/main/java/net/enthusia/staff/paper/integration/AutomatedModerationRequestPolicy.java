package net.enthusia.staff.paper.integration;

import dev.rosewood.rosechat.api.staff.AutomatedPublicMuteRequest;
import java.time.Duration;

final class AutomatedModerationRequestPolicy {
    static final int REQUIRED_STRIKES = 2;
    private static final Duration REQUIRED_MUTE = Duration.ofDays(30);

    private AutomatedModerationRequestPolicy() {
    }

    static String rejectionReason(AutomatedPublicMuteRequest request) {
        if (request.strikeCount() != REQUIRED_STRIKES) {
            return "Exactly two enforcement strikes are required";
        }
        if (request.evidence().size() != REQUIRED_STRIKES) {
            return "Exactly two reviewable evidence records are required";
        }
        if (!REQUIRED_MUTE.equals(request.muteDuration())) {
            return "Only the fixed 30-day public mute is supported";
        }
        return null;
    }
}
