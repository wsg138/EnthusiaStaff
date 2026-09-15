package net.enthusia.staff.discordbot;

import net.enthusia.staff.domain.investigation.EvasionAlert;

/** One private delivery channel for a durable linked-alt investigation alert. */
@FunctionalInterface
interface DiscordInvestigationAlertSink {
    Delivery deliver(EvasionAlert alert);

    record Delivery(boolean delivered, String errorCode) {
        Delivery {
            if (delivered && errorCode != null) {
                throw new IllegalArgumentException("successful alert delivery cannot include an error code");
            }
            if (!delivered && (errorCode == null || errorCode.isBlank())) {
                throw new IllegalArgumentException("failed alert delivery requires a stable error code");
            }
        }

        static Delivery success() {
            return new Delivery(true, null);
        }

        static Delivery retry(String errorCode) {
            return new Delivery(false, errorCode);
        }
    }
}
