package net.enthusia.staff.protocol;

import java.util.List;
import java.util.UUID;

/** Explicit allowlisted wire contract for D08 Minecraft reason selection. */
public final class MinecraftPunishmentCatalogWire {
    public static final int VERSION = 1;
    public static final String PATH = "/v1/cross-platform/minecraft-reasons";

    private MinecraftPunishmentCatalogWire() {
    }

    public record Request(int version, UUID actorId, String actorName) {
        public Request {
            if (version != VERSION || actorId == null || blank(actorName)) {
                throw new IllegalArgumentException("Minecraft catalog request fields are invalid");
            }
        }
    }

    public record Response(
            int version,
            Outcome outcome,
            String code,
            String message,
            List<Reason> reasons
    ) {
        public Response {
            if (version != VERSION || outcome == null || code == null || message == null || reasons == null) {
                throw new IllegalArgumentException("Minecraft catalog response fields are invalid");
            }
            reasons = List.copyOf(reasons);
            if (outcome == Outcome.REJECTED && blank(code)) {
                throw new IllegalArgumentException("rejected catalog responses require a code");
            }
            if (outcome == Outcome.REJECTED && !reasons.isEmpty()) {
                throw new IllegalArgumentException("rejected catalog responses cannot include reasons");
            }
        }

        public static Response available(List<Reason> reasons) {
            return new Response(VERSION, Outcome.AVAILABLE, "", "", reasons);
        }

        public static Response rejected(String code, String message) {
            return new Response(VERSION, Outcome.REJECTED, code, message, List.of());
        }
    }

    public record Reason(String id, String family, String label) {
        public Reason {
            if (blank(id) || blank(family) || blank(label)) {
                throw new IllegalArgumentException("Minecraft catalog reason fields are invalid");
            }
        }
    }

    public enum Outcome {
        AVAILABLE,
        REJECTED
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }
}
