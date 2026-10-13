package net.enthusia.staff.velocity;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.LinkedHashMap;
import java.util.Map;

/** Human-readable private Discord punishment notice, assembled only from renderer-allowlisted fields. */
final class PunishmentCreatedDiscordEmbed {
    private static final String TYPE = "PUNISHMENT_CREATED";
    private static final String DESTINATION = "punishments";

    private PunishmentCreatedDiscordEmbed() {
    }

    static boolean apply(ObjectNode embed, String destination, String[] lines) {
        if (!DESTINATION.equals(destination) || lines.length == 0 || !TYPE.equals(lines[0])) {
            return false;
        }
        Map<String, String> data = allowedLines(lines);
        String kind = data.getOrDefault("sanctionTypes", "");
        embed.put("title", kind.equals("WARNING") ? "Warning issued" : "Punishment issued");
        String target = data.getOrDefault("targetUsername",
                data.getOrDefault("targetId", "Unknown player"));
        StringBuilder description = new StringBuilder("**Player:** ").append(target);
        String discordId = data.get("targetDiscordUserId");
        if (validDiscordId(discordId)) {
            // Webhook allowed_mentions.parse=[] makes this a link, never a ping.
            description.append("\n**Discord:** <@").append(discordId).append('>');
        }
        String reason = data.get("publicReason");
        if (reason != null && !reason.isBlank()) {
            description.append("\n\n**Reason:** ").append(reason);
        }
        embed.put("description", description.toString());
        ArrayNode fields = embed.putArray("fields");
        addField(fields, "Punishment", data.getOrDefault("sanctionDetails", kind), true);
        String issuer = data.getOrDefault("actorName", "System");
        addField(fields, "Issued by", issuer, true);
        addField(fields, "Case", data.get("caseId"), false);
        return true;
    }

    private static Map<String, String> allowedLines(String[] lines) {
        Map<String, String> entries = new LinkedHashMap<>();
        for (int i = 1; i < lines.length; i++) {
            int sep = lines[i].indexOf('=');
            if (sep > 0) {
                String name = lines[i].substring(0, sep);
                if (name.matches("[a-zA-Z][a-zA-Z0-9_-]{0,63}")) {
                    entries.putIfAbsent(name, lines[i].substring(sep + 1));
                }
            }
        }
        return entries;
    }

    private static boolean validDiscordId(String id) {
        if (id == null || !id.matches("[1-9][0-9]{15,19}")) {
            return false;
        }
        try {
            Long.parseUnsignedLong(id);
            return true;
        } catch (NumberFormatException ignored) {
            return false;
        }
    }

    private static void addField(ArrayNode fields, String label, String value, boolean inline) {
        if (value == null || value.isBlank()) {
            return;
        }
        ObjectNode field = fields.addObject();
        field.put("name", label);
        field.put("value", value.length() > 950 ? value.substring(0, 949) + "…" : value);
        field.put("inline", inline);
    }
}
