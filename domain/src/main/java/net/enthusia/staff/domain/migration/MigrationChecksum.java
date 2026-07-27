package net.enthusia.staff.domain.migration;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;

public final class MigrationChecksum {
    public String calculate(List<LegacySanction> sanctions) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            sanctions.stream()
                    .sorted(Comparator.comparing(LegacySanction::sourceTable)
                            .thenComparing(LegacySanction::externalId))
                    .map(MigrationChecksum::canonical)
                    .forEach(value -> digest.update(value.getBytes(StandardCharsets.UTF_8)));
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private static String canonical(LegacySanction sanction) {
        return String.join("\u001f",
                sanction.sourceTable(),
                sanction.externalId(),
                sanction.type().name(),
                sanction.playerId().map(Object::toString).orElse(""),
                sanction.username().orElse(""),
                sanction.originalReason(),
                sanction.originalStaffName(),
                sanction.issuedAt().toString(),
                sanction.expiresAt().map(Object::toString).orElse("PERMANENT"),
                sanction.endedAt().map(Object::toString).orElse("NOT_ENDED"),
                sanction.networkAddress().map(LegacyNetworkAddress::canonicalHex).orElse("NO_NETWORK_ADDRESS"),
                Boolean.toString(sanction.active())
        ) + '\n';
    }
}
