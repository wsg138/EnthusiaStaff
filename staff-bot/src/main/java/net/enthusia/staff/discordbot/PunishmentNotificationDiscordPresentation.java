package net.enthusia.staff.discordbot;

import java.util.ArrayList;
import java.util.List;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.enthusia.staff.domain.auth.DiscordConsequenceType;
import net.enthusia.staff.domain.policyv2.persistence.PolicyV2PublicProjection;
import net.enthusia.staff.domain.policyv2.publicview.PolicyV2PublicDiscordAdapter;
import net.enthusia.staff.domain.sanction.SanctionType;

/** Branded Discord presentation for player-facing punishment notifications. */
final class PunishmentNotificationDiscordPresentation {
    static final String ENTHUSIA_GUILD_ID = "1410303324745371709";
    static final String FOOTER = "Enthusia Staff • Automated moderation notice";
    static final int WARNING_COLOR = 0xF2C94C;
    static final int MUTE_COLOR = 0xF2994A;
    static final int KICK_COLOR = 0xE67E22;
    static final int BAN_COLOR = 0xD64545;
    static final int RESTRICTION_COLOR = 0x9B51E0;
    static final int UPDATE_COLOR = 0x27AE60;

    private static final String ALERT_HEADING = "# Punishment Alert";
    private static final String UPDATE_HEADING = "# Punishment Update";
    private static final int MARKDOWN_LABEL_PREFIX_LENGTH = 2;
    private static final int MARKDOWN_LABEL_SEPARATOR_LENGTH = 3;

    private PunishmentNotificationDiscordPresentation() {
    }

    static MessageEmbed embed(String message, int color, String iconUrl) {
        ParsedMessage parsed = parse(message);
        EmbedBuilder builder = new EmbedBuilder()
                .setTitle(parsed.title())
                .setDescription(parsed.description())
                .setColor(color)
                .setFooter(FOOTER);
        parsed.fields().forEach(field -> builder.addField(field.name(), field.value(), false));
        if (iconUrl != null && !iconUrl.isBlank()) {
            builder.setThumbnail(iconUrl);
        }
        return builder.build();
    }

    static MessageEmbed embed(PolicyV2PublicProjection projection, int color, String iconUrl) {
        PolicyV2PublicDiscordAdapter.Message message = PolicyV2PublicDiscordAdapter.toMessage(projection);
        EmbedBuilder builder = new EmbedBuilder()
                .setTitle(message.title())
                .setDescription(message.description())
                .setColor(color)
                .setFooter(FOOTER);
        message.fields().forEach(field -> builder.addField(field.name(), field.value(), false));
        if (iconUrl != null && !iconUrl.isBlank()) {
            builder.setThumbnail(iconUrl);
        }
        return builder.build();
    }

    static ActionRow appealRow() {
        return ActionRow.of(
                Button.link(JdaPunishmentNotifier.APPEAL_SITE, "Appeal on Website"),
                Button.link(JdaPunishmentNotifier.APPEAL_CHANNEL, "Appeal in Discord")
        );
    }

    static String guildIconUrl(JDA jda) {
        if (jda == null) {
            return null;
        }
        Guild guild = jda.getGuildById(ENTHUSIA_GUILD_ID);
        return guild == null ? null : guild.getIconUrl();
    }

    static int color(DiscordConsequenceType type) {
        return switch (type) {
            case WARNING -> WARNING_COLOR;
            case MUTE -> MUTE_COLOR;
            case KICK -> KICK_COLOR;
            case BAN -> BAN_COLOR;
            case CHANNEL_RESTRICTION -> RESTRICTION_COLOR;
            default -> MUTE_COLOR;
        };
    }

    static int color(SanctionType type) {
        return switch (type) {
            case WARNING -> WARNING_COLOR;
            case MUTE -> MUTE_COLOR;
            case BAN, NETWORK_BAN, NETWORK_IDENTITY_BAN -> BAN_COLOR;
            default -> MUTE_COLOR;
        };
    }

    private static ParsedMessage parse(String message) {
        if (message == null || message.isBlank()) {
            throw new IllegalArgumentException("punishment notification message is required");
        }
        String title = message.startsWith(UPDATE_HEADING) ? "Punishment Updated" : "Punishment Alert";
        String body = stripHeading(message);
        body = stripAppealText(body);
        String[] blocks = body.split("\\n\\n");
        String description = blocks.length == 0 ? "" : blocks[0].strip();
        List<Field> fields = new ArrayList<>();
        for (int index = 1; index < blocks.length; index++) {
            Field field = field(blocks[index]);
            if (field != null) {
                fields.add(field);
            }
        }
        return new ParsedMessage(title, description, List.copyOf(fields));
    }

    private static String stripHeading(String message) {
        if (message.startsWith(ALERT_HEADING)) {
            return message.substring(ALERT_HEADING.length()).strip();
        }
        if (message.startsWith(UPDATE_HEADING)) {
            return message.substring(UPDATE_HEADING.length()).strip();
        }
        return message.strip();
    }

    private static String stripAppealText(String body) {
        String marker = "If you believe this punishment is incorrect, you can appeal in ";
        int appeal = body.indexOf(marker);
        return appeal < 0 ? body.strip() : body.substring(0, appeal).strip();
    }

    private static Field field(String block) {
        String value = block.strip();
        if (!value.startsWith("**")) {
            return null;
        }
        int labelEnd = value.indexOf(":**");
        if (labelEnd < MARKDOWN_LABEL_PREFIX_LENGTH) {
            return null;
        }
        String name = value.substring(MARKDOWN_LABEL_PREFIX_LENGTH, labelEnd).strip();
        String fieldValue = value.substring(labelEnd + MARKDOWN_LABEL_SEPARATOR_LENGTH).strip();
        return name.isEmpty() || fieldValue.isEmpty() ? null : new Field(name, fieldValue);
    }

    private record ParsedMessage(String title, String description, List<Field> fields) {
    }

    private record Field(String name, String value) {
    }
}
