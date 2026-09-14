package net.enthusia.staff.discordbot;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

/** Reads the staging Discord bot token without exposing secret content in diagnostics. */
final class StaffBotTokenFile {
    private StaffBotTokenFile() {
    }

    static String read(Path path) {
        Objects.requireNonNull(path, "path");
        final String raw;
        try {
            raw = Files.readString(path, StandardCharsets.UTF_8);
        } catch (IOException | SecurityException exception) {
            throw invalidTokenFile();
        }

        String token = raw.strip();
        if (token.isEmpty() || containsWhitespace(token)) {
            throw invalidTokenFile();
        }
        return token;
    }

    private static boolean containsWhitespace(String value) {
        return value.codePoints().anyMatch(Character::isWhitespace);
    }

    private static IllegalArgumentException invalidTokenFile() {
        return new IllegalArgumentException("staff bot token file is missing, unreadable, empty, or invalid");
    }
}
