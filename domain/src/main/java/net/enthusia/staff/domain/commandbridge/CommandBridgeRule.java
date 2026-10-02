package net.enthusia.staff.domain.commandbridge;

import java.util.Locale;
import java.util.regex.Pattern;
import net.enthusia.staff.domain.auth.StaffRank;

/** One explicit allowlisted console command and the Minecraft authority required to execute it. */
public record CommandBridgeRule(
        String commandName,
        StaffRank minimumRank,
        String requiredPermission,
        int maximumArguments
) {
    private static final Pattern COMMAND_NAME = Pattern.compile("[a-z0-9][a-z0-9:_-]{0,63}");
    private static final Pattern PERMISSION = Pattern.compile("[a-z0-9][a-z0-9._:-]{0,127}");
    private static final int MAX_ARGUMENTS = 32;

    public CommandBridgeRule {
        commandName = normalize(commandName);
        requiredPermission = normalize(requiredPermission);
        if (!COMMAND_NAME.matcher(commandName).matches()
                || minimumRank == null
                || !PERMISSION.matcher(requiredPermission).matches()
                || maximumArguments < 0
                || maximumArguments > MAX_ARGUMENTS) {
            throw new IllegalArgumentException("command bridge rule is invalid");
        }
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }
}
