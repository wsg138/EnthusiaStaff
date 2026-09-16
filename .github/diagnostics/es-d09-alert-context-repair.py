from pathlib import Path
import sys

root = Path(sys.argv[1])


def path(name):
    return root / name


def read(name):
    return path(name).read_text(encoding="utf-8")


def write(name, content):
    path(name).parent.mkdir(parents=True, exist_ok=True)
    path(name).write_text(content, encoding="utf-8")


def replace_once(name, old, new):
    content = read(name)
    count = content.count(old)
    if count != 1:
        raise RuntimeError(f"{name}: expected one replacement, found {count}: {old[:120]!r}")
    write(name, content.replace(old, new, 1))


write("domain/src/main/java/net/enthusia/staff/domain/investigation/EvasionAlert.java", r'''package net.enthusia.staff.domain.investigation;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import net.enthusia.staff.domain.auth.DiscordConsequenceType;
import net.enthusia.staff.domain.discord.DiscordPunishmentState;
import net.enthusia.staff.domain.moderation.DiscordUserId;
import net.enthusia.staff.domain.moderation.ModerationSubjectId;

/** Durable suspicion alert only; it never represents or authorizes an automatic punishment. */
public record EvasionAlert(
        UUID alertId,
        String operationKey,
        Context context,
        State state,
        DeliveryState discordDelivery,
        DeliveryState minecraftDelivery,
        int discordAttempts,
        int minecraftAttempts,
        Optional<String> discordErrorCode,
        Optional<String> minecraftErrorCode,
        Optional<Instant> discordNextAttemptAt,
        Optional<Instant> minecraftNextAttemptAt,
        Instant createdAt,
        Instant updatedAt,
        long revision,
        boolean replayed
) {
    private static final int MAX_SUMMARY = 512;

    public enum State {
        OPEN,
        RESOLVED
    }

    public enum DeliveryState {
        PENDING,
        DELIVERED,
        RETRY
    }

    public enum TriggerType {
        LINKED_MINECRAFT_ONLINE
    }

    public record Context(
            ModerationSubjectId subjectId,
            UUID punishmentId,
            DiscordUserId targetDiscordUserId,
            DiscordConsequenceType punishmentType,
            String punishmentSummary,
            DiscordPunishmentState punishmentState,
            Optional<Instant> punishmentExpiresAt,
            UUID triggeringMinecraftPlayerId,
            Optional<String> triggeringMinecraftUsername,
            String currentServer,
            long playerRevision,
            TriggerType triggerType,
            Instant triggeredAt
    ) {
        public Context {
            if (subjectId == null || punishmentId == null || targetDiscordUserId == null || punishmentType == null
                    || blank(punishmentSummary) || punishmentSummary.length() > MAX_SUMMARY || punishmentState == null
                    || punishmentState.terminal() || punishmentExpiresAt == null || triggeringMinecraftPlayerId == null
                    || triggeringMinecraftUsername == null || blank(currentServer) || currentServer.length() > 64
                    || playerRevision < 0 || triggerType == null || triggeredAt == null) {
                throw new IllegalArgumentException("evasion alert context must describe an active punishment and trigger");
            }
            triggeringMinecraftUsername.ifPresent(username -> {
                if (blank(username) || username.length() > 32) {
                    throw new IllegalArgumentException("triggering Minecraft username is invalid");
                }
            });
        }
    }

    public EvasionAlert {
        if (alertId == null || blank(operationKey) || operationKey.length() > 160 || context == null || state == null
                || discordDelivery == null || minecraftDelivery == null || discordAttempts < 0
                || minecraftAttempts < 0 || discordErrorCode == null || minecraftErrorCode == null
                || discordNextAttemptAt == null || minecraftNextAttemptAt == null || createdAt == null
                || updatedAt == null || updatedAt.isBefore(createdAt) || revision < 0) {
            throw new IllegalArgumentException("evasion alert fields must be present and valid");
        }
        validateDelivery(discordDelivery, discordErrorCode, discordNextAttemptAt);
        validateDelivery(minecraftDelivery, minecraftErrorCode, minecraftNextAttemptAt);
    }

    public ModerationSubjectId subjectId() {
        return context.subjectId();
    }

    public UUID punishmentId() {
        return context.punishmentId();
    }

    public DiscordUserId targetDiscordUserId() {
        return context.targetDiscordUserId();
    }

    public DiscordConsequenceType punishmentType() {
        return context.punishmentType();
    }

    public String punishmentSummary() {
        return context.punishmentSummary();
    }

    public DiscordPunishmentState punishmentState() {
        return context.punishmentState();
    }

    public Optional<Instant> punishmentExpiresAt() {
        return context.punishmentExpiresAt();
    }

    public UUID triggeringMinecraftPlayerId() {
        return context.triggeringMinecraftPlayerId();
    }

    public Optional<String> triggeringMinecraftUsername() {
        return context.triggeringMinecraftUsername();
    }

    public String currentServer() {
        return context.currentServer();
    }

    public long playerRevision() {
        return context.playerRevision();
    }

    public TriggerType triggerType() {
        return context.triggerType();
    }

    public Instant triggeredAt() {
        return context.triggeredAt();
    }

    private static void validateDelivery(
            DeliveryState delivery,
            Optional<String> errorCode,
            Optional<Instant> nextAttemptAt
    ) {
        if (delivery == DeliveryState.DELIVERED && (errorCode.isPresent() || nextAttemptAt.isPresent())) {
            throw new IllegalArgumentException("delivered alert channel cannot retain retry state");
        }
        if (delivery == DeliveryState.PENDING && (errorCode.isPresent() || nextAttemptAt.isEmpty())) {
            throw new IllegalArgumentException("pending alert channel requires a due time and no error");
        }
        if (delivery == DeliveryState.RETRY && (errorCode.isEmpty() || nextAttemptAt.isEmpty())) {
            throw new IllegalArgumentException("retry alert channel requires error and due time");
        }
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }
}
''')

store_interface = "domain/src/main/java/net/enthusia/staff/domain/ports/DiscordInvestigationStore.java"
replace_once(store_interface, '''    record EvasionAlertDraft(
            UUID alertId,
            String operationKey,
            ModerationSubjectId subjectId,
            UUID punishmentId,
            UUID triggeringMinecraftPlayerId,
            String currentServer,
            long playerRevision,
            Instant now
    ) {
        public EvasionAlertDraft {
            if (alertId == null || blank(operationKey) || operationKey.length() > 160 || subjectId == null
                    || punishmentId == null || triggeringMinecraftPlayerId == null || blank(currentServer)
                    || currentServer.length() > 64 || playerRevision < 0 || now == null) {
                throw new IllegalArgumentException("evasion alert draft fields are invalid");
            }
        }
    }
''', '''    record EvasionAlertDraft(
            UUID alertId,
            String operationKey,
            EvasionAlert.Context context,
            Instant now
    ) {
        public EvasionAlertDraft {
            if (alertId == null || blank(operationKey) || operationKey.length() > 160 || context == null || now == null) {
                throw new IllegalArgumentException("evasion alert draft fields are invalid");
            }
        }
    }
''')
replace_once(store_interface, '''    record EvasionCandidate(
            ModerationSubjectId subjectId,
            UUID punishmentId,
            UUID minecraftPlayerId,
            String currentServer,
            long playerRevision
    ) {
        public EvasionCandidate {
            if (subjectId == null || punishmentId == null || minecraftPlayerId == null
                    || blank(currentServer) || playerRevision < 0) {
                throw new IllegalArgumentException("evasion candidate fields are invalid");
            }
        }
    }
''', '''    record EvasionCandidate(EvasionAlert.Context context) {
        public EvasionCandidate {
            if (context == null) {
                throw new IllegalArgumentException("evasion candidate context is required");
            }
        }

        public UUID punishmentId() {
            return context.punishmentId();
        }

        public UUID minecraftPlayerId() {
            return context.triggeringMinecraftPlayerId();
        }

        public long playerRevision() {
            return context.playerRevision();
        }
    }
''')

source = "persistence/src/main/java/net/enthusia/staff/persistence/JdbcDiscordInvestigationSource.java"
replace_once(source, "import java.util.List;\n", "import java.util.List;\nimport java.util.Optional;\n")
replace_once(source, "import net.enthusia.staff.domain.discord.DiscordPunishment;\n", "import net.enthusia.staff.domain.discord.DiscordPunishment;\nimport net.enthusia.staff.domain.investigation.EvasionAlert;\n")
replace_once(source,
             "                SELECT r.desired_state_json, membership.player_id, player.current_server, player.revision\n",
             "                SELECT r.desired_state_json, membership.player_id, player.current_username,\n                       player.current_server, player.last_seen_at, player.revision\n")
replace_once(source, '''        candidates.add(new EvasionCandidate(
                punishment.subjectId(),
                punishment.punishmentId(),
                UuidBytes.fromBytes(rows.getBytes("player_id")),
                rows.getString("current_server"),
                rows.getLong("revision")
        ));
''', '''        candidates.add(new EvasionCandidate(new EvasionAlert.Context(
                punishment.subjectId(),
                punishment.punishmentId(),
                punishment.targetUserId(),
                punishment.intent().type(),
                punishment.intent().publicReason(),
                punishment.state(),
                punishment.expiresAt(),
                UuidBytes.fromBytes(rows.getBytes("player_id")),
                Optional.ofNullable(rows.getString("current_username")),
                rows.getString("current_server"),
                rows.getLong("revision"),
                EvasionAlert.TriggerType.LINKED_MINECRAFT_ONLINE,
                rows.getTimestamp("last_seen_at").toInstant()
        )));
''')

worker = "staff-bot/src/main/java/net/enthusia/staff/discordbot/DiscordInvestigationWorker.java"
replace_once(worker, '''        store.createEvasionAlert(new EvasionAlertDraft(
                deterministicId(operationKey),
                operationKey,
                candidate.subjectId(),
                candidate.punishmentId(),
                candidate.minecraftPlayerId(),
                candidate.currentServer(),
                candidate.playerRevision(),
                now
        ));
''', '''        store.createEvasionAlert(new EvasionAlertDraft(
                deterministicId(operationKey), operationKey, candidate.context(), now
        ));
''')

migration = "persistence/src/main/resources/db/migration/V22__discord_investigation_state.sql"
replace_once(migration, '''    subject_id BINARY(16) NOT NULL,
    punishment_id BINARY(16) NOT NULL,
    triggering_minecraft_player_id BINARY(16) NOT NULL,
    current_server VARCHAR(64) NOT NULL,
    player_revision BIGINT UNSIGNED NOT NULL,
''', '''    subject_id BINARY(16) NOT NULL,
    punishment_id BINARY(16) NOT NULL,
    target_discord_user_id DECIMAL(20, 0) UNSIGNED NOT NULL,
    punishment_type VARCHAR(32) NOT NULL,
    punishment_summary VARCHAR(512) NOT NULL,
    punishment_state VARCHAR(32) NOT NULL,
    punishment_expires_at TIMESTAMP(6) NULL,
    triggering_minecraft_player_id BINARY(16) NOT NULL,
    triggering_minecraft_username VARCHAR(32) NULL,
    current_server VARCHAR(64) NOT NULL,
    player_revision BIGINT UNSIGNED NOT NULL,
    trigger_type VARCHAR(48) NOT NULL,
    triggered_at TIMESTAMP(6) NOT NULL,
''')
replace_once(migration, '''    CONSTRAINT fk_discord_evasion_alert_player
        FOREIGN KEY (triggering_minecraft_player_id) REFERENCES players(player_id),
    CONSTRAINT ck_discord_evasion_alert_resolution CHECK (
''', '''    CONSTRAINT fk_discord_evasion_alert_player
        FOREIGN KEY (triggering_minecraft_player_id) REFERENCES players(player_id),
    CONSTRAINT ck_discord_evasion_alert_discord_snowflake CHECK (
        target_discord_user_id BETWEEN 1 AND 18446744073709551615
    ),
    CONSTRAINT ck_discord_evasion_alert_trigger_time CHECK (triggered_at <= created_at),
    CONSTRAINT ck_discord_evasion_alert_resolution CHECK (
''')

alert_store = "persistence/src/main/java/net/enthusia/staff/persistence/JdbcDiscordEvasionAlertStore.java"
replace_once(alert_store, "import java.sql.Connection;\n", "import java.math.BigDecimal;\nimport java.sql.Connection;\n")
replace_once(alert_store, "import net.enthusia.staff.domain.investigation.EvasionAlert;\n", "import net.enthusia.staff.domain.auth.DiscordConsequenceType;\nimport net.enthusia.staff.domain.discord.DiscordPunishmentState;\nimport net.enthusia.staff.domain.investigation.EvasionAlert;\nimport net.enthusia.staff.domain.moderation.DiscordUserId;\n")
replace_once(alert_store, '''                INSERT INTO discord_evasion_alerts(
                    alert_id, operation_key, subject_id, punishment_id, triggering_minecraft_player_id,
                    current_server, player_revision, state, discord_delivery, minecraft_delivery,
                    discord_attempts, minecraft_attempts, discord_error_code, minecraft_error_code,
                    discord_next_attempt_at, minecraft_next_attempt_at,
                    created_at, updated_at, resolved_at, revision
                ) VALUES (?, ?, ?, ?, ?, ?, ?, 'OPEN', 'PENDING', 'PENDING', 0, 0, NULL, NULL,
                    ?, ?, ?, ?, NULL, 0)
                """)) {
            statement.setBytes(1, UuidBytes.toBytes(draft.alertId()));
            statement.setString(2, draft.operationKey());
            statement.setBytes(3, UuidBytes.toBytes(draft.subjectId().value()));
            statement.setBytes(4, UuidBytes.toBytes(draft.punishmentId()));
            statement.setBytes(5, UuidBytes.toBytes(draft.triggeringMinecraftPlayerId()));
            statement.setString(6, draft.currentServer());
            statement.setLong(7, draft.playerRevision());
            statement.setTimestamp(8, Timestamp.from(draft.now()));
            statement.setTimestamp(9, Timestamp.from(draft.now()));
            statement.setTimestamp(10, Timestamp.from(draft.now()));
            statement.setTimestamp(11, Timestamp.from(draft.now()));
''', '''                INSERT INTO discord_evasion_alerts(
                    alert_id, operation_key, subject_id, punishment_id, target_discord_user_id,
                    punishment_type, punishment_summary, punishment_state, punishment_expires_at,
                    triggering_minecraft_player_id, triggering_minecraft_username, current_server,
                    player_revision, trigger_type, triggered_at, state, discord_delivery, minecraft_delivery,
                    discord_attempts, minecraft_attempts, discord_error_code, minecraft_error_code,
                    discord_next_attempt_at, minecraft_next_attempt_at, created_at, updated_at, resolved_at, revision
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?,
                    'OPEN', 'PENDING', 'PENDING', 0, 0, NULL, NULL, ?, ?, ?, ?, NULL, 0)
                """)) {
            EvasionAlert.Context context = draft.context();
            statement.setBytes(1, UuidBytes.toBytes(draft.alertId()));
            statement.setString(2, draft.operationKey());
            bindContext(statement, context);
            statement.setTimestamp(16, Timestamp.from(draft.now()));
            statement.setTimestamp(17, Timestamp.from(draft.now()));
            statement.setTimestamp(18, Timestamp.from(draft.now()));
            statement.setTimestamp(19, Timestamp.from(draft.now()));
''')
# Add context binder before updateDelivery.
replace_once(alert_store, '''    private static void updateDelivery(
            Connection connection,
''', '''    private static void bindContext(PreparedStatement statement, EvasionAlert.Context context) throws SQLException {
        statement.setBytes(3, UuidBytes.toBytes(context.subjectId().value()));
        statement.setBytes(4, UuidBytes.toBytes(context.punishmentId()));
        statement.setBigDecimal(5, new BigDecimal(context.targetDiscordUserId().value()));
        statement.setString(6, context.punishmentType().name());
        statement.setString(7, context.punishmentSummary());
        statement.setString(8, context.punishmentState().name());
        setInstant(statement, 9, context.punishmentExpiresAt());
        statement.setBytes(10, UuidBytes.toBytes(context.triggeringMinecraftPlayerId()));
        if (context.triggeringMinecraftUsername().isPresent()) {
            statement.setString(11, context.triggeringMinecraftUsername().orElseThrow());
        } else {
            statement.setNull(11, Types.VARCHAR);
        }
        statement.setString(12, context.currentServer());
        statement.setLong(13, context.playerRevision());
        statement.setString(14, context.triggerType().name());
        statement.setTimestamp(15, Timestamp.from(context.triggeredAt()));
    }

    private static void updateDelivery(
            Connection connection,
''')
replace_once(alert_store, '''        return new Current(
                UuidBytes.fromBytes(rows.getBytes("alert_id")),
                rows.getString("operation_key"),
                new ModerationSubjectId(UuidBytes.fromBytes(rows.getBytes("subject_id"))),
                UuidBytes.fromBytes(rows.getBytes("punishment_id")),
                UuidBytes.fromBytes(rows.getBytes("triggering_minecraft_player_id")),
                rows.getString("current_server"),
                rows.getLong("player_revision"),
''', '''        return new Current(
                UuidBytes.fromBytes(rows.getBytes("alert_id")),
                rows.getString("operation_key"),
                readContext(rows),
''')
replace_once(alert_store, '''    private static Optional<Instant> optionalInstant(Timestamp timestamp) {
''', '''    private static EvasionAlert.Context readContext(ResultSet rows) throws SQLException {
        return new EvasionAlert.Context(
                new ModerationSubjectId(UuidBytes.fromBytes(rows.getBytes("subject_id"))),
                UuidBytes.fromBytes(rows.getBytes("punishment_id")),
                new DiscordUserId(rows.getString("target_discord_user_id")),
                DiscordConsequenceType.valueOf(rows.getString("punishment_type")),
                rows.getString("punishment_summary"),
                DiscordPunishmentState.valueOf(rows.getString("punishment_state")),
                optionalInstant(rows.getTimestamp("punishment_expires_at")),
                UuidBytes.fromBytes(rows.getBytes("triggering_minecraft_player_id")),
                Optional.ofNullable(rows.getString("triggering_minecraft_username")),
                rows.getString("current_server"),
                rows.getLong("player_revision"),
                EvasionAlert.TriggerType.valueOf(rows.getString("trigger_type")),
                rows.getTimestamp("triggered_at").toInstant()
        );
    }

    private static Optional<Instant> optionalInstant(Timestamp timestamp) {
''')
replace_once(alert_store, '''        if (!current.alertId().equals(draft.alertId()) || !current.subjectId().equals(draft.subjectId())
                || !current.punishmentId().equals(draft.punishmentId())
                || !current.triggeringPlayer().equals(draft.triggeringMinecraftPlayerId())) {
''', '''        if (!current.alertId().equals(draft.alertId()) || !current.context().equals(draft.context())) {
''')
replace_once(alert_store, '''    private record Current(
            UUID alertId,
            String operationKey,
            ModerationSubjectId subjectId,
            UUID punishmentId,
            UUID triggeringPlayer,
            String currentServer,
            long playerRevision,
            EvasionAlert.State state,
''', '''    private record Current(
            UUID alertId,
            String operationKey,
            EvasionAlert.Context context,
            EvasionAlert.State state,
''')
replace_once(alert_store, '''            return new EvasionAlert(
                    alertId, operationKey, subjectId, punishmentId, triggeringPlayer, currentServer,
                    playerRevision, state, discordDelivery, minecraftDelivery, discordAttempts,
''', '''            return new EvasionAlert(
                    alertId, operationKey, context, state, discordDelivery, minecraftDelivery, discordAttempts,
''')
# ModerationSubjectId import is no longer used directly in this file.
replace_once(alert_store, "import net.enthusia.staff.domain.moderation.ModerationSubjectId;\n", "")

write("staff-bot/src/main/java/net/enthusia/staff/discordbot/DiscordInvestigationAlertControls.java", r'''package net.enthusia.staff.discordbot;

import java.util.Optional;
import java.util.UUID;
import net.enthusia.staff.domain.investigation.EvasionAlert;

/** Compact private component identifiers; every action is reauthorized when clicked. */
final class DiscordInvestigationAlertControls {
    private static final String PREFIX = "d09alert:";
    private static final int MAX_COMPONENT_ID = 100;

    enum Type {
        LINKED,
        HISTORY,
        MODERATE,
        RESOLVE
    }

    record Action(Type type, long targetDiscordId, Optional<UUID> alertId) {
        Action {
            if (type == null || targetDiscordId == 0L || alertId == null
                    || (type == Type.RESOLVE) != alertId.isPresent()) {
                throw new IllegalArgumentException("linked-alt alert action is invalid");
            }
        }
    }

    private DiscordInvestigationAlertControls() {
    }

    static boolean handles(String customId) {
        return customId != null && customId.startsWith(PREFIX);
    }

    static String linked(EvasionAlert alert) {
        return id(Type.LINKED, alert, false);
    }

    static String history(EvasionAlert alert) {
        return id(Type.HISTORY, alert, false);
    }

    static String moderate(EvasionAlert alert) {
        return id(Type.MODERATE, alert, false);
    }

    static String resolve(EvasionAlert alert) {
        return id(Type.RESOLVE, alert, true);
    }

    static Action parse(String customId) {
        if (!handles(customId)) {
            throw new IllegalArgumentException("not a linked-alt alert action");
        }
        String[] parts = customId.substring(PREFIX.length()).split(":", -1);
        Type type = type(parts);
        int expected = type == Type.RESOLVE ? 3 : 2;
        if (parts.length != expected) {
            throw new IllegalArgumentException("linked-alt alert action is malformed");
        }
        long targetId = parseUnsigned(parts[1]);
        Optional<UUID> alertId = type == Type.RESOLVE ? Optional.of(uuid(parts[2])) : Optional.empty();
        return new Action(type, targetId, alertId);
    }

    private static Type type(String[] parts) {
        if (parts.length < 2) {
            throw new IllegalArgumentException("linked-alt alert action is malformed");
        }
        return switch (parts[0]) {
            case "linked" -> Type.LINKED;
            case "history" -> Type.HISTORY;
            case "moderate" -> Type.MODERATE;
            case "resolve" -> Type.RESOLVE;
            default -> throw new IllegalArgumentException("unknown linked-alt alert action");
        };
    }

    private static String id(Type type, EvasionAlert alert, boolean includeAlert) {
        if (alert == null) {
            throw new IllegalArgumentException("alert must be present");
        }
        String target = alert.targetDiscordUserId().value();
        String value = PREFIX + type.name().toLowerCase(java.util.Locale.ROOT) + ':' + target
                + (includeAlert ? ":" + alert.alertId() : "");
        if (value.length() > MAX_COMPONENT_ID) {
            throw new IllegalArgumentException("linked-alt alert action exceeds Discord component limit");
        }
        return value;
    }

    private static long parseUnsigned(String value) {
        try {
            long parsed = Long.parseUnsignedLong(value);
            if (parsed == 0L) {
                throw new IllegalArgumentException("Discord target id must be positive");
            }
            return parsed;
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("Discord target id is invalid", exception);
        }
    }

    private static UUID uuid(String value) {
        try {
            return UUID.fromString(value);
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("linked-alt alert id is invalid", exception);
        }
    }
}
''')

write("staff-bot/src/main/java/net/enthusia/staff/discordbot/JdaDiscordInvestigationAlertSink.java", r'''package net.enthusia.staff.discordbot;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.enthusia.staff.domain.investigation.EvasionAlert;

/** Sends the private D09 investigation snapshot and reauthorized quick actions to staff. */
final class JdaDiscordInvestigationAlertSink implements DiscordInvestigationAlertSink {
    private static final int MAX_INLINE_TEXT = 180;

    private final long guildId;
    private final String channelId;
    private final String staffRoleId;
    private final AtomicReference<JDA> jda = new AtomicReference<>();

    JdaDiscordInvestigationAlertSink(long guildId, String channelId, String staffRoleId) {
        if (guildId <= 0 || channelId == null || channelId.isBlank() || staffRoleId == null || staffRoleId.isBlank()) {
            throw new IllegalArgumentException("Discord investigation alert destination is invalid");
        }
        this.guildId = guildId;
        this.channelId = channelId;
        this.staffRoleId = staffRoleId;
    }

    void bind(JDA api) {
        if (api == null) {
            throw new IllegalArgumentException("JDA must be present");
        }
        jda.set(api);
    }

    void unbind() {
        jda.set(null);
    }

    @Override
    public Delivery deliver(EvasionAlert alert) {
        if (alert == null) {
            throw new IllegalArgumentException("alert must be present");
        }
        JDA api = jda.get();
        if (api == null) {
            return Delivery.retry("DISCORD_GATEWAY_UNAVAILABLE");
        }
        TextChannel channel = api.getTextChannelById(channelId);
        if (channel == null || channel.getGuild().getIdLong() != guildId) {
            return Delivery.retry("DISCORD_ALERT_CHANNEL_UNAVAILABLE");
        }
        try {
            channel.sendMessage("<@&" + staffRoleId + "> " + content(alert))
                    .setAllowedMentions(List.of(Message.MentionType.ROLE))
                    .mentionRoles(List.of(staffRoleId))
                    .addComponents(ActionRow.of(buttons(alert)))
                    .complete();
            return Delivery.success();
        } catch (RuntimeException exception) {
            return Delivery.retry("DISCORD_ALERT_SEND_FAILED");
        }
    }

    static String content(EvasionAlert alert) {
        String player = alert.triggeringMinecraftUsername().map(JdaDiscordInvestigationAlertSink::safe)
                .map(name -> name + " (`" + alert.triggeringMinecraftPlayerId() + "`)")
                .orElse("`" + alert.triggeringMinecraftPlayerId() + "`");
        String expiry = alert.punishmentExpiresAt().map(JdaDiscordInvestigationAlertSink::discordTime)
                .orElse("permanent");
        return "**Linked-alt investigation alert** `" + alert.alertId() + "`\n"
                + "Punished Discord: `" + alert.targetDiscordUserId().value() + "`\n"
                + "Linked Minecraft: " + player + "\n"
                + "Active punishment: **" + alert.punishmentType() + "** / " + alert.punishmentState()
                + " / " + expiry + " — " + safe(alert.punishmentSummary()) + "\n"
                + "Trigger: linked Minecraft account observed online on `" + safe(alert.currentServer()) + "` at "
                + discordTime(alert.triggeredAt()) + ".\n"
                + "No automatic punishment was applied; staff must make the evasion decision.";
    }

    private static List<Button> buttons(EvasionAlert alert) {
        return List.of(
                Button.secondary(DiscordInvestigationAlertControls.linked(alert), "Linked"),
                Button.secondary(DiscordInvestigationAlertControls.history(alert), "History"),
                Button.primary(DiscordInvestigationAlertControls.moderate(alert), "Moderate"),
                Button.success(DiscordInvestigationAlertControls.resolve(alert), "Resolve")
        );
    }

    private static String discordTime(Instant value) {
        return "<t:" + value.getEpochSecond() + ":R>";
    }

    private static String safe(String raw) {
        String value = raw.replace('\r', ' ').replace('\n', ' ').replace('`', '\'').replace("@", "＠").trim();
        return value.length() <= MAX_INLINE_TEXT ? value : value.substring(0, MAX_INLINE_TEXT - 1) + "…";
    }
}
''')

service = "staff-bot/src/main/java/net/enthusia/staff/discordbot/DiscordInvestigationService.java"
old_resolve = '''    AlertResult resolveAlert(
            long actorDiscordId,
            String actorName,
            long targetDiscordId,
            UUID alertId,
            long expectedRevision
    ) {
        if (alertId == null) {
            throw new IllegalArgumentException("alertId must be present");
        }
        TargetContext context = authorize(actorDiscordId, actorName, targetDiscordId,
                DiscordModerationOperation.RESOLVE_EVASION_ALERT);
        EvasionAlert alert = store.findEvasionAlert(alertId)
                .orElseThrow(() -> new IllegalStateException("linked-alt alert does not exist"));
        requireSubject(alert.subjectId(), context.subjectId(), "linked-alt alert");
        EvasionAlert resolved = store.resolveEvasionAlert(alertId, expectedRevision, clock.instant());
        return new AlertResult(resolved.alertId(), resolved.revision(), resolved.replayed());
    }
'''
new_resolve = '''    AlertResult resolveAlert(
            long actorDiscordId,
            String actorName,
            long targetDiscordId,
            UUID alertId,
            long expectedRevision
    ) {
        TargetContext context = authorizeAlert(actorDiscordId, actorName, targetDiscordId, alertId);
        return resolve(context, alertId, expectedRevision);
    }

    AlertResult resolveAlert(
            long actorDiscordId,
            String actorName,
            long targetDiscordId,
            UUID alertId
    ) {
        TargetContext context = authorizeAlert(actorDiscordId, actorName, targetDiscordId, alertId);
        EvasionAlert current = requireAlert(context, alertId);
        return resolve(context, alertId, current.revision());
    }

    private TargetContext authorizeAlert(
            long actorDiscordId,
            String actorName,
            long targetDiscordId,
            UUID alertId
    ) {
        if (alertId == null) {
            throw new IllegalArgumentException("alertId must be present");
        }
        return authorize(actorDiscordId, actorName, targetDiscordId,
                DiscordModerationOperation.RESOLVE_EVASION_ALERT);
    }

    private AlertResult resolve(TargetContext context, UUID alertId, long expectedRevision) {
        EvasionAlert alert = requireAlert(context, alertId);
        EvasionAlert resolved = store.resolveEvasionAlert(alertId, expectedRevision, clock.instant());
        return new AlertResult(resolved.alertId(), resolved.revision(), resolved.replayed());
    }

    private EvasionAlert requireAlert(TargetContext context, UUID alertId) {
        EvasionAlert alert = store.findEvasionAlert(alertId)
                .orElseThrow(() -> new IllegalStateException("linked-alt alert does not exist"));
        requireSubject(alert.subjectId(), context.subjectId(), "linked-alt alert");
        return alert;
    }
'''
replace_once(service, old_resolve, new_resolve)

controller = "staff-bot/src/main/java/net/enthusia/staff/discordbot/DiscordInvestigationCommandController.java"
replace_once(controller, '''    private Mutation resolveAlert(
            long actorId,
            String actorName,
            long targetId,
            SlashCommandInteractionEvent event
    ) {
        DiscordInvestigationService.AlertResult result = service.resolveAlert(
                actorId, actorName, targetId, uuid(string(event, ALERT_ID)), integer(event, REVISION)
        );
        return new Mutation("Linked-alt investigation alert `" + result.alertId() + "` resolved.");
    }
''', '''    private Mutation resolveAlert(
            long actorId,
            String actorName,
            long targetId,
            SlashCommandInteractionEvent event
    ) {
        DiscordInvestigationService.AlertResult result = service.resolveAlert(
                actorId, actorName, targetId, uuid(string(event, ALERT_ID)), integer(event, REVISION)
        );
        return resolved(result);
    }

    Mutation resolveAlert(long actorId, String actorName, long targetId, UUID alertId) {
        return resolved(service.resolveAlert(actorId, actorName, targetId, alertId));
    }

    private static Mutation resolved(DiscordInvestigationService.AlertResult result) {
        return new Mutation("Linked-alt investigation alert `" + result.alertId() + "` resolved.");
    }
''')

listener = "staff-bot/src/main/java/net/enthusia/staff/discordbot/JdaStaffModerationListener.java"
replace_once(listener, '''    private boolean handleInvestigationButton(ButtonInteractionEvent event) {
        DiscordInvestigationCommandController investigation = investigations.orElse(null);
        if (investigation == null || !DiscordInvestigationCommandController.isCaptureMore(event.getComponentId())) {
            return false;
        }
        DiscordInvestigationCommandController.ContextTarget target =
                DiscordInvestigationCommandController.contextTarget(event.getComponentId());
        dispatchInvestigation(event, () -> captureMore(event, investigation, target));
        return true;
    }
''', '''    private boolean handleInvestigationButton(ButtonInteractionEvent event) {
        DiscordInvestigationCommandController investigation = investigations.orElse(null);
        if (investigation == null) {
            return false;
        }
        String customId = event.getComponentId();
        if (DiscordInvestigationCommandController.isCaptureMore(customId)) {
            DiscordInvestigationCommandController.ContextTarget target =
                    DiscordInvestigationCommandController.contextTarget(customId);
            dispatchInvestigation(event, () -> captureMore(event, investigation, target));
            return true;
        }
        if (!DiscordInvestigationAlertControls.handles(customId)) {
            return false;
        }
        dispatchAlertAction(event, investigation, DiscordInvestigationAlertControls.parse(customId));
        return true;
    }

    private void dispatchAlertAction(
            ButtonInteractionEvent event,
            DiscordInvestigationCommandController investigation,
            DiscordInvestigationAlertControls.Action action
    ) {
        long actorId = event.getUser().getIdLong();
        String actorName = event.getUser().getName();
        long targetId = action.targetDiscordId();
        switch (action.type()) {
            case LINKED -> dispatch(event, () -> controller.linkedDiscord(actorId, actorName, targetId));
            case HISTORY -> dispatch(event, () -> controller.historyDiscord(actorId, actorName, targetId));
            case MODERATE -> dispatch(event, () -> withPunish(
                    controller.moderateDiscord(actorId, actorName, targetId), targetId));
            case RESOLVE -> dispatchInvestigation(event, () -> investigation.resolveAlert(
                    actorId, actorName, targetId, action.alertId().orElseThrow()));
        }
    }
''')

worker_test = "staff-bot/src/test/java/net/enthusia/staff/discordbot/DiscordInvestigationWorkerTest.java"
replace_once(worker_test, "import net.enthusia.staff.common.CaseId;\n", '''import net.enthusia.staff.common.CaseId;
import net.enthusia.staff.domain.auth.DiscordConsequenceType;
import net.enthusia.staff.domain.discord.DiscordPunishmentState;
''')
replace_once(worker_test, "import net.enthusia.staff.domain.moderation.ModerationSubjectId;\n", '''import net.enthusia.staff.domain.moderation.DiscordUserId;
import net.enthusia.staff.domain.moderation.ModerationSubjectId;
''')
replace_once(worker_test, '''        assertEquals(EvasionAlert.DeliveryState.RETRY, first.discordDelivery());
        assertEquals(EvasionAlert.DeliveryState.DELIVERED, first.minecraftDelivery());
''', '''        assertEquals(EvasionAlert.DeliveryState.RETRY, first.discordDelivery());
        assertEquals(EvasionAlert.DeliveryState.DELIVERED, first.minecraftDelivery());
        assertEquals(context(7), first.context());
''')
replace_once(worker_test, '''    private static DiscordInvestigationStore.EvasionCandidate candidate(long revision) {
        return new DiscordInvestigationStore.EvasionCandidate(SUBJECT, PUNISHMENT, PLAYER, "survival", revision);
    }
''', '''    private static DiscordInvestigationStore.EvasionCandidate candidate(long revision) {
        return new DiscordInvestigationStore.EvasionCandidate(context(revision));
    }

    private static EvasionAlert.Context context(long revision) {
        return new EvasionAlert.Context(
                SUBJECT, PUNISHMENT, new DiscordUserId("223456789012345680"), DiscordConsequenceType.BAN,
                "Active ban", DiscordPunishmentState.APPLIED, Optional.empty(), PLAYER, Optional.of("LinkedAlt"),
                "survival", revision, EvasionAlert.TriggerType.LINKED_MINECRAFT_ONLINE, NOW.minusSeconds(5)
        );
    }
''')
replace_once(worker_test, '''            EvasionAlert created = new EvasionAlert(
                    draft.alertId(), draft.operationKey(), draft.subjectId(), draft.punishmentId(),
                    draft.triggeringMinecraftPlayerId(), draft.currentServer(), draft.playerRevision(),
                    EvasionAlert.State.OPEN, EvasionAlert.DeliveryState.PENDING, EvasionAlert.DeliveryState.PENDING,
''', '''            EvasionAlert created = new EvasionAlert(
                    draft.alertId(), draft.operationKey(), draft.context(),
                    EvasionAlert.State.OPEN, EvasionAlert.DeliveryState.PENDING, EvasionAlert.DeliveryState.PENDING,
''')
replace_once(worker_test, '''            return new EvasionAlert(
                    current.alertId(), current.operationKey(), current.subjectId(), current.punishmentId(),
                    current.triggeringMinecraftPlayerId(), current.currentServer(), current.playerRevision(), current.state(),
                    deliveryState(update), current.minecraftDelivery(), current.discordAttempts() + 1,
''', '''            return new EvasionAlert(
                    current.alertId(), current.operationKey(), current.context(), current.state(),
                    deliveryState(update), current.minecraftDelivery(), current.discordAttempts() + 1,
''')
replace_once(worker_test, '''            return new EvasionAlert(
                    current.alertId(), current.operationKey(), current.subjectId(), current.punishmentId(),
                    current.triggeringMinecraftPlayerId(), current.currentServer(), current.playerRevision(), current.state(),
                    current.discordDelivery(), deliveryState(update), current.discordAttempts(),
''', '''            return new EvasionAlert(
                    current.alertId(), current.operationKey(), current.context(), current.state(),
                    current.discordDelivery(), deliveryState(update), current.discordAttempts(),
''')
replace_once(worker_test, '''            return new EvasionAlert(
                    source.alertId(), source.operationKey(), source.subjectId(), source.punishmentId(),
                    source.triggeringMinecraftPlayerId(), source.currentServer(), source.playerRevision(), source.state(),
                    source.discordDelivery(), source.minecraftDelivery(), source.discordAttempts(),
''', '''            return new EvasionAlert(
                    source.alertId(), source.operationKey(), source.context(), source.state(),
                    source.discordDelivery(), source.minecraftDelivery(), source.discordAttempts(),
''')

integration_test = "integration-tests/src/test/java/net/enthusia/staff/integration/DiscordInvestigationPersistenceIntegrationTest.java"
replace_once(integration_test, "import net.enthusia.staff.domain.discord.DiscordPunishmentIntent;\n", '''import net.enthusia.staff.domain.discord.DiscordPunishmentIntent;
import net.enthusia.staff.domain.discord.DiscordPunishmentState;
''')
replace_once(integration_test, '''            EvasionAlert created = store.createEvasionAlert(new DiscordInvestigationStore.EvasionAlertDraft(
                    alertId, "d09:test:alert:1", subjectId, UUID.randomUUID(), trigger, "survival", 7, NOW
            ));
            assertEquals(1, store.pendingEvasionAlerts(NOW, 10).size());
''', '''            EvasionAlert.Context context = new EvasionAlert.Context(
                    subjectId, UUID.randomUUID(), new DiscordUserId("223456789012345680"),
                    DiscordConsequenceType.BAN, "Active ban", DiscordPunishmentState.APPLIED, Optional.empty(),
                    trigger, Optional.of("LinkedAlt"), "survival", 7,
                    EvasionAlert.TriggerType.LINKED_MINECRAFT_ONLINE, NOW.minusSeconds(30)
            );
            EvasionAlert created = store.createEvasionAlert(new DiscordInvestigationStore.EvasionAlertDraft(
                    alertId, "d09:test:alert:1", context, NOW
            ));
            assertEquals(context, created.context());
            assertEquals(context, store.findEvasionAlert(alertId).orElseThrow().context());
            assertEquals(1, store.pendingEvasionAlerts(NOW, 10).size());
''')

write("staff-bot/src/test/java/net/enthusia/staff/discordbot/DiscordInvestigationAlertControlsTest.java", r'''package net.enthusia.staff.discordbot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import net.enthusia.staff.domain.auth.DiscordConsequenceType;
import net.enthusia.staff.domain.discord.DiscordPunishmentState;
import net.enthusia.staff.domain.investigation.EvasionAlert;
import net.enthusia.staff.domain.moderation.DiscordUserId;
import net.enthusia.staff.domain.moderation.ModerationSubjectId;
import org.junit.jupiter.api.Test;

class DiscordInvestigationAlertControlsTest {
    private static final Instant NOW = Instant.parse("2026-09-15T12:00:00Z");

    @Test
    void quickActionsRoundTripWithoutEmbeddingAuthority() {
        EvasionAlert alert = alert();

        assertEquals(DiscordInvestigationAlertControls.Type.LINKED,
                DiscordInvestigationAlertControls.parse(DiscordInvestigationAlertControls.linked(alert)).type());
        assertEquals(DiscordInvestigationAlertControls.Type.HISTORY,
                DiscordInvestigationAlertControls.parse(DiscordInvestigationAlertControls.history(alert)).type());
        assertEquals(DiscordInvestigationAlertControls.Type.MODERATE,
                DiscordInvestigationAlertControls.parse(DiscordInvestigationAlertControls.moderate(alert)).type());
        var resolve = DiscordInvestigationAlertControls.parse(DiscordInvestigationAlertControls.resolve(alert));
        assertEquals(DiscordInvestigationAlertControls.Type.RESOLVE, resolve.type());
        assertEquals(alert.alertId(), resolve.alertId().orElseThrow());
        assertEquals(Long.parseUnsignedLong(alert.targetDiscordUserId().value()), resolve.targetDiscordId());
        assertTrue(DiscordInvestigationAlertControls.resolve(alert).length() <= 100);
    }

    @Test
    void malformedOrZeroTargetActionsAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> DiscordInvestigationAlertControls.parse("d09alert:linked:0"));
        assertThrows(IllegalArgumentException.class, () -> DiscordInvestigationAlertControls.parse("d09alert:resolve:1:not-a-uuid"));
        assertThrows(IllegalArgumentException.class, () -> DiscordInvestigationAlertControls.parse("d09alert:unknown:1"));
    }

    static EvasionAlert alert() {
        EvasionAlert.Context context = new EvasionAlert.Context(
                new ModerationSubjectId(UUID.fromString("10000000-0000-0000-0000-000000000001")),
                UUID.fromString("20000000-0000-0000-0000-000000000001"),
                new DiscordUserId("18446744073709551614"),
                DiscordConsequenceType.BAN,
                "Ban reason",
                DiscordPunishmentState.APPLIED,
                Optional.empty(),
                UUID.fromString("30000000-0000-0000-0000-000000000001"),
                Optional.of("LinkedAlt"),
                "survival",
                7,
                EvasionAlert.TriggerType.LINKED_MINECRAFT_ONLINE,
                NOW.minusSeconds(5)
        );
        return new EvasionAlert(
                UUID.fromString("40000000-0000-0000-0000-000000000001"), "d09:test:alert", context,
                EvasionAlert.State.OPEN, EvasionAlert.DeliveryState.PENDING, EvasionAlert.DeliveryState.PENDING,
                0, 0, Optional.empty(), Optional.empty(), Optional.of(NOW), Optional.of(NOW), NOW, NOW, 0, false
        );
    }
}
''')

write("staff-bot/src/test/java/net/enthusia/staff/discordbot/JdaDiscordInvestigationAlertSinkTest.java", r'''package net.enthusia.staff.discordbot;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class JdaDiscordInvestigationAlertSinkTest {
    @Test
    void privateAlertPresentationCarriesInvestigationContextWithoutCreatingTargetMentions() {
        String content = JdaDiscordInvestigationAlertSink.content(DiscordInvestigationAlertControlsTest.alert());

        assertTrue(content.contains("18446744073709551614"));
        assertTrue(content.contains("LinkedAlt"));
        assertTrue(content.contains("BAN"));
        assertTrue(content.contains("Ban reason"));
        assertTrue(content.contains("survival"));
        assertTrue(content.contains("No automatic punishment"));
        assertFalse(content.contains("<@18446744073709551614>"));
    }
}
''')

print("D09 evasion alert context repair applied")
