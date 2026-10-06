package net.enthusia.staff.protocol;

import java.util.List;

/** Explicit allowlisted wire contract for D08 Minecraft punishment commit. */
public final class MinecraftPunishmentCommitWire {
    public static final int VERSION = 1;
    public static final String PATH = "/v1/cross-platform/minecraft-commit";

    private MinecraftPunishmentCommitWire() {
    }

    public record Request(
            int version,
            MinecraftPunishmentPreparationWire.Request punishment,
            Expectation expectation
    ) {
        public Request {
            if (version != VERSION || punishment == null || expectation == null) {
                throw new IllegalArgumentException("Minecraft commit request fields are invalid");
            }
        }
    }

    public record Expectation(
            String configurationVersion,
            int stepOrdinal,
            String stepLabel,
            List<MinecraftPunishmentPreparationWire.Sanction> sanctions
    ) {
        public Expectation {
            if (blank(configurationVersion) || stepOrdinal < 0 || blank(stepLabel)
                    || sanctions == null || sanctions.isEmpty()) {
                throw new IllegalArgumentException("Minecraft commit expectation fields are invalid");
            }
            sanctions = List.copyOf(sanctions);
        }
    }

    public record Response(
            int version,
            Outcome outcome,
            String code,
            String message,
            Accepted accepted
    ) {
        public Response {
            if (version != VERSION || outcome == null || code == null || message == null) {
                throw new IllegalArgumentException("Minecraft commit response fields are invalid");
            }
            if ((outcome == Outcome.ACCEPTED) != (accepted != null)) {
                throw new IllegalArgumentException("accepted responses must carry exactly one accepted result");
            }
            if (outcome == Outcome.REJECTED && blank(code)) {
                throw new IllegalArgumentException("rejected responses require a code");
            }
        }

        public static Response accepted(String caseId, boolean replayed) {
            return new Response(VERSION, Outcome.ACCEPTED, "", "", new Accepted(caseId, replayed));
        }

        public static Response rejected(String code, String message) {
            return new Response(VERSION, Outcome.REJECTED, code, message, null);
        }
    }

    public record Accepted(String caseId, boolean replayed) {
        public Accepted {
            if (blank(caseId)) {
                throw new IllegalArgumentException("accepted case identifier is required");
            }
        }
    }

    public enum Outcome {
        ACCEPTED,
        REJECTED
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }
}
