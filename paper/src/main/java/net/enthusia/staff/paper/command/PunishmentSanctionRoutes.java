package net.enthusia.staff.paper.command;

import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/** Maps convenience commands onto the audited exact-sanction lifecycle, never a case-wide change. */
final class PunishmentSanctionRoutes {
    private static final Set<String> DIRECT = Set.of(
            "remove", "revoke", "end", "reduce", "change", "overturn"
    );

    private PunishmentSanctionRoutes() {
    }

    static boolean handles(String command, String[] args) {
        return "unpunish".equals(command)
                || "punish".equals(command) && args.length > 0
                && DIRECT.contains(args[0].toLowerCase(Locale.ROOT));
    }

    static boolean isPickerRequest(String command, String[] args) {
        return handles(command, args)
                && ("unpunish".equals(command) && args.length == 1
                    || "punish".equals(command) && args.length == 2);
    }

    static String pickerTarget(String command, String[] args) {
        return "unpunish".equals(command) ? args[0] : args[1];
    }

    static String pickerAction(String command, String[] args) {
        if ("unpunish".equals(command)) {
            return "remove";
        }
        return switch (args[0].toLowerCase(Locale.ROOT)) {
            case "remove", "revoke" -> "remove";
            case "end" -> "end";
            case "reduce" -> "reduce";
            default -> "change";
        };
    }

    /**
     * Returns the canonical /estaff sanction argument vector; null means invalid input.
     * Every mutation requires an exact sanction UUID. A player or case name never selects
     * an implicit latest punishment, because multiple warnings can coexist.
     */
    static String[] rewrite(String command, String[] args) {
        if (!handles(command, args)) {
            return null;
        }
        boolean alias = "unpunish".equals(command);
        int idIndex = alias ? 0 : 1;
        if (args.length <= idIndex || !canonicalUuid(args[idIndex])) {
            return null;
        }
        String operation = alias ? "revoke" : canonicalAction(args[0]);
        int tailStart = idIndex + 1;
        if ("change".equals(operation)) {
            if (args.length <= tailStart) {
                return null;
            }
            String requested = canonicalAction(args[tailStart]);
            // /punish change <id> <action> ... supports explicit actions. A duration
            // instead means REDUCE ONLY; the exact sanction service rejects extensions.
            if (!"change".equals(requested)) {
                operation = requested;
                tailStart++;
            } else {
                operation = "reduce";
            }
        }
        if (args.length <= tailStart) {
            return null;
        }
        String[] result = new String[3 + args.length - tailStart];
        result[0] = "sanction";
        result[1] = operation;
        result[2] = args[idIndex];
        System.arraycopy(args, tailStart, result, 3, args.length - tailStart);
        return result;
    }

    private static String canonicalAction(String action) {
        return switch (action.toLowerCase(Locale.ROOT)) {
            case "remove", "revoke" -> "revoke";
            case "end" -> "end";
            case "reduce", "shorten" -> "reduce";
            case "overturn" -> "overturn";
            default -> "change";
        };
    }

    private static boolean canonicalUuid(String value) {
        try {
            return UUID.fromString(value).toString().equalsIgnoreCase(value);
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    static String usage() {
        return "Use /unpunish <player> or /punish remove|end|reduce|change <player> "
                + "to select a punishment in a GUI. Console may use exact IDs with reasons.";
    }
}
