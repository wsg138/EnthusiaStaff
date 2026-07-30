package net.enthusia.staff.domain.application;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import net.enthusia.staff.domain.sanction.SanctionSpec;

public record PunishmentMatchKey(String value) {
    public PunishmentMatchKey {
        if (value == null || !value.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("punishment match key must be a lowercase SHA-256 value");
        }
    }

    public static PunishmentMatchKey of(UUID targetId, String reasonId, List<SanctionSpec> sanctions) {
        if (targetId == null || reasonId == null || reasonId.isBlank() || sanctions == null || sanctions.isEmpty()) {
            throw new IllegalArgumentException("punishment match fields must be present");
        }
        List<String> canonicalSanctions = sanctions.stream()
                .map(PunishmentMatchKey::canonical)
                .sorted(Comparator.naturalOrder())
                .toList();
        String canonical = targetId + "\n" + reasonId.trim() + "\n" + String.join("\n", canonicalSanctions);
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return new PunishmentMatchKey(HexFormat.of().formatHex(
                    digest.digest(canonical.getBytes(StandardCharsets.UTF_8))
            ));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private static String canonical(SanctionSpec specification) {
        if (specification == null) {
            throw new IllegalArgumentException("sanction specification must be present");
        }
        String duration = specification.length().temporary()
                .map(value -> value.getSeconds() + ":" + value.getNano())
                .orElse("-");
        return specification.type().name() + ':' + specification.length().kind().name() + ':' + duration;
    }
}
