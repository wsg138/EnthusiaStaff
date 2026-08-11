package net.enthusia.staff.paper.command;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.function.Supplier;
import java.util.logging.Level;
import net.enthusia.staff.common.IdempotencyKey;
import net.enthusia.staff.domain.OperationalMode;
import net.enthusia.staff.domain.application.SanctionChangeService;
import net.enthusia.staff.domain.auth.Actor;
import net.enthusia.staff.domain.sanction.ExactSanctionChangeRequest;
import net.enthusia.staff.domain.sanction.ExactSanctionChangeResult;
import net.enthusia.staff.domain.sanction.SanctionChangeAction;
import net.enthusia.staff.paper.auth.PaperActorResolver;
import net.enthusia.staff.paper.config.ModerationFeatureSettings;
import net.kyori.adventure.text.Component;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;

public final class SanctionLifecycleCommand {
    public static final String REDUCE_PERMISSION = "enthusiastaff.sanction.reduce";
    public static final String END_PERMISSION = "enthusiastaff.sanction.end";
    public static final String REVOKE_PERMISSION = "enthusiastaff.sanction.revoke";
    public static final String OVERTURN_PERMISSION = "enthusiastaff.sanction.overturn";
    public static final String APPEAL_PERMISSION = "enthusiastaff.sanction.overturn.appeal";
    public static final String BYPASS_HIERARCHY_PERMISSION = "enthusiastaff.sanction.bypass-hierarchy";

    private final JavaPlugin plugin;
    private final String originRuntime;
    private final Supplier<OperationalMode> mode;
    private final Supplier<SanctionChangeService> changes;
    private final Supplier<ModerationFeatureSettings> settings;
    private final ExecutorService workers;
    private final SanctionExpirationParser expirations;
    private final CommandResponseDispatcher responses;

    public SanctionLifecycleCommand(
            JavaPlugin plugin,
            Clock clock,
            String originRuntime,
            Supplier<OperationalMode> mode,
            Supplier<SanctionChangeService> changes,
            Supplier<ModerationFeatureSettings> settings,
            ExecutorService workers
    ) {
        if (plugin == null || clock == null || originRuntime == null || originRuntime.isBlank()
                || mode == null || changes == null || settings == null || workers == null) {
            throw new IllegalArgumentException("sanction lifecycle dependencies must be present");
        }
        this.plugin = plugin;
        this.originRuntime = originRuntime;
        this.mode = mode;
        this.changes = changes;
        this.settings = settings;
        this.workers = workers;
        this.expirations = new SanctionExpirationParser(clock);
        this.responses = new CommandResponseDispatcher(plugin);
    }

    public boolean execute(CommandSender sender, String label, String[] args) {
        if (args.length < 2 || !args[0].equalsIgnoreCase("sanction")) {
            return false;
        }
        Optional<Operation> operation = Operation.parse(args[1]);
        if (operation.isEmpty()) {
            sender.sendMessage(Component.text(usage(label)));
            return true;
        }
        Operation selected = operation.orElseThrow();
        if (!EstaffCommand.requirePermission(sender, selected.permission, selected.denial)) {
            return true;
        }
        Parsed parsed = parse(selected, args);
        if (parsed.error != null) {
            sender.sendMessage(Component.text(parsed.error + " Usage: " + selected.usage(label)));
            return true;
        }
        if (parsed.appealId.isPresent()
                && !EstaffCommand.requirePermission(
                        sender,
                        APPEAL_PERMISSION,
                        "You do not have permission to link an appeal to an overturn."
                )) {
            return true;
        }
        Optional<Actor> actor = PaperActorResolver.resolve(sender);
        if (actor.isEmpty()) {
            sender.sendMessage(Component.text("Your staff identity could not be resolved."));
            return true;
        }
        PendingChange pending = new PendingChange(
                actor.orElseThrow(),
                selected,
                parsed,
                sender.hasPermission(BYPASS_HIERARCHY_PERMISSION)
        );
        submit(sender, () -> apply(sender, pending));
        return true;
    }

    private void apply(CommandSender sender, PendingChange pending) {
        SanctionChangeService service = changes.get();
        if (service == null) {
            responses.send(sender, Component.text("Sanction changes are unavailable while storage is offline."));
            return;
        }
        if (!service.supportsExactChanges()) {
            responses.send(sender, Component.text(
                    "Sanction change rejected: Exact sanction changes are unavailable [UNSUPPORTED]"
            ));
            return;
        }
        ModerationFeatureSettings active = settings.get();
        if (active == null) {
            responses.send(sender, Component.text(
                    "Sanction changes are unavailable because validated settings are not active."
            ));
            return;
        }
        ExactSanctionChangeRequest request = null;
        ExactSanctionChangeResult result;
        try {
            java.util.OptionalLong revision = service.exactRevision(pending.parsed().sanctionId());
            if (revision.isEmpty()) {
                responses.send(sender, Component.text("Sanction change rejected: The sanction does not exist [SANCTION_NOT_FOUND]"));
                return;
            }
            Parsed parsed = pending.parsed();
            request = new ExactSanctionChangeRequest(
                    idempotency(pending.actor(), pending.operation(), parsed),
                    parsed.sanctionId(),
                    revision.orElseThrow(),
                    pending.actor(),
                    pending.operation().action,
                    parsed.expiration(),
                    parsed.reason(),
                    parsed.appealId(),
                    parsed.punishmentRequestId(),
                    originRuntime,
                    pending.bypassHierarchy()
            );
            result = service.applyExact(request, mode.get(), active.sanctionActionLimits());
        } catch (RuntimeException exception) {
            plugin.getLogger().log(
                    Level.WARNING,
                    "Sanitized exact sanction change failed for " + pending.parsed().sanctionId(),
                    exception
            );
            responses.send(sender, Component.text(
                    "The sanction change failed; no raw database error was shown. See the server log."
            ));
            return;
        }
        responses.send(sender, render(result, request, active));
    }

    private static List<Component> render(
            ExactSanctionChangeResult result,
            ExactSanctionChangeRequest request,
            ModerationFeatureSettings settings
    ) {
        List<Component> lines = new ArrayList<>();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm:ss z")
                .withZone(settings.historyTimezone());
        if (result instanceof ExactSanctionChangeResult.Applied applied) {
            lines.add(Component.text(
                    (applied.replayed() ? "No duplicate change was created; replayed" : "Applied")
                            + " " + human(applied.action().name())
                            + " for case " + applied.caseId().value()
                            + ", sanction " + applied.sanctionId()
            ));
            lines.add(Component.text(
                    "State: " + human(applied.previousStatus().name())
                            + " -> " + human(applied.resultingStatus().name())
            ));
            lines.add(Component.text(
                    "Expiration: " + expiration(applied.previousExpiration(), formatter)
                            + " -> " + expiration(applied.resultingExpiration(), formatter)
            ));
            lines.add(Component.text("Actor: " + request.actor().displayName() + " | Reason: " + request.reason()));
            applied.linkedAppealId().ifPresent(value -> lines.add(Component.text("Linked appeal: " + value)));
            applied.linkedPunishmentRequestId().ifPresent(
                    value -> lines.add(Component.text("Linked punishment request: " + value))
            );
            return List.copyOf(lines);
        }
        if (result instanceof ExactSanctionChangeResult.NoChange noChange) {
            lines.add(Component.text(
                    "No change: " + noChange.message() + " [" + noChange.code() + "]"
            ));
            lines.add(Component.text(
                    "Case " + noChange.caseId().value() + " | sanction " + noChange.sanctionId()
                            + " | current state " + human(noChange.currentStatus().name())
                            + " | expiration " + expiration(noChange.currentExpiration(), formatter)
            ));
            return List.copyOf(lines);
        }
        ExactSanctionChangeResult.Rejected rejected = (ExactSanctionChangeResult.Rejected) result;
        return List.of(Component.text(
                "Sanction change rejected: " + rejected.message() + " [" + rejected.code() + "]"
        ));
    }

    private Parsed parse(Operation operation, String[] args) {
        int minimum = operation == Operation.REDUCE ? 5 : 4;
        if (args.length < minimum) {
            return Parsed.error("Not enough arguments.");
        }
        UUID sanctionId;
        try {
            sanctionId = UUID.fromString(args[2]);
        } catch (IllegalArgumentException exception) {
            return Parsed.error("Sanction ID must be a UUID.");
        }
        int index = 3;
        Optional<Instant> expiration = Optional.empty();
        if (operation == Operation.REDUCE) {
            String expirationToken = args[index++];
            expiration = expirations.parse(expirationToken);
            if (expiration.isEmpty()) {
                return Parsed.error(
                        "Expiration must be an ISO-8601 timestamp or compact duration such as 30m, 2h, 7d, or 4w."
                );
            }
        }

        Optional<UUID> appealId = Optional.empty();
        Optional<UUID> requestId = Optional.empty();
        while (index < args.length && args[index].startsWith("--")) {
            String flag = args[index++].toLowerCase(Locale.ROOT);
            if (index >= args.length) {
                return Parsed.error("The " + flag + " flag requires a UUID.");
            }
            UUID value;
            try {
                value = UUID.fromString(args[index++]);
            } catch (IllegalArgumentException exception) {
                return Parsed.error("The " + flag + " value must be a UUID.");
            }
            switch (flag) {
                case "--appeal" -> {
                    if (operation != Operation.OVERTURN || appealId.isPresent()) {
                        return Parsed.error("--appeal is only allowed once on overturn.");
                    }
                    appealId = Optional.of(value);
                }
                case "--request" -> {
                    if (requestId.isPresent()) {
                        return Parsed.error("--request may only be provided once.");
                    }
                    requestId = Optional.of(value);
                }
                default -> {
                    return Parsed.error("Unknown sanction option " + flag + ".");
                }
            }
        }
        if (index >= args.length) {
            return Parsed.error("A staff reason is required.");
        }
        String reason = String.join(" ", java.util.Arrays.copyOfRange(args, index, args.length)).trim();
        if (reason.isBlank()) {
            return Parsed.error("A staff reason is required.");
        }
        String canonical = operation.name() + "|" + sanctionId + "|"
                + expiration.map(Instant::toString).orElse("") + "|"
                + appealId.map(UUID::toString).orElse("") + "|"
                + requestId.map(UUID::toString).orElse("") + "|" + reason;
        return new Parsed(
                sanctionId,
                expiration,
                reason,
                appealId,
                requestId,
                canonical,
                null
        );
    }

    private static IdempotencyKey idempotency(Actor actor, Operation operation, Parsed parsed) {
        String raw = actor.id() + "|" + operation.name() + "|" + parsed.canonical;
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(raw.getBytes(StandardCharsets.UTF_8));
            return new IdempotencyKey("sanction-command:" + HexFormat.of().formatHex(digest));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private void submit(CommandSender sender, Runnable task) {
        try {
            workers.execute(task);
        } catch (RejectedExecutionException exception) {
            responses.send(sender, Component.text("Sanction processing is busy; try again shortly."));
        }
    }

    public List<String> complete(CommandSender sender, String[] args) {
        if (args.length == 2 && args[0].equalsIgnoreCase("sanction")) {
            String prefix = args[1].toLowerCase(Locale.ROOT);
            return java.util.Arrays.stream(Operation.values())
                    .filter(operation -> sender.hasPermission(operation.permission))
                    .map(operation -> operation.command)
                    .filter(value -> value.startsWith(prefix))
                    .toList();
        }
        if (args.length >= 4 && args[0].equalsIgnoreCase("sanction")) {
            Optional<Operation> operation = Operation.parse(args[1]);
            if (operation.isPresent()) {
                List<String> flags = new ArrayList<>();
                if (operation.orElseThrow() == Operation.OVERTURN
                        && sender.hasPermission(APPEAL_PERMISSION)
                        && "--appeal".startsWith(args[args.length - 1].toLowerCase(Locale.ROOT))) {
                    flags.add("--appeal");
                }
                if ("--request".startsWith(args[args.length - 1].toLowerCase(Locale.ROOT))) {
                    flags.add("--request");
                }
                return List.copyOf(flags);
            }
        }
        return List.of();
    }

    private static String expiration(Optional<Instant> value, DateTimeFormatter formatter) {
        return value.map(formatter::format).orElse("permanent/no expiration");
    }

    private static String human(String value) {
        String normalized = value.toLowerCase(Locale.ROOT).replace('_', ' ');
        return Character.toUpperCase(normalized.charAt(0)) + normalized.substring(1);
    }

    private static String usage(String label) {
        return "/" + label + " sanction <reduce|end|revoke|overturn> ...";
    }

    private enum Operation {
        REDUCE(
                "reduce",
                SanctionChangeAction.REDUCE_DURATION,
                REDUCE_PERMISSION,
                "You do not have permission to reduce sanctions."
        ),
        END(
                "end",
                SanctionChangeAction.END_EARLY,
                END_PERMISSION,
                "You do not have permission to end sanctions early."
        ),
        REVOKE(
                "revoke",
                SanctionChangeAction.REVOKE,
                REVOKE_PERMISSION,
                "You do not have permission to revoke sanctions."
        ),
        OVERTURN(
                "overturn",
                SanctionChangeAction.FULL_OVERTURN,
                OVERTURN_PERMISSION,
                "You do not have permission to overturn sanctions."
        );

        private final String command;
        private final SanctionChangeAction action;
        private final String permission;
        private final String denial;

        Operation(String command, SanctionChangeAction action, String permission, String denial) {
            this.command = command;
            this.action = action;
            this.permission = permission;
            this.denial = denial;
        }

        private static Optional<Operation> parse(String value) {
            return java.util.Arrays.stream(values())
                    .filter(operation -> operation.command.equalsIgnoreCase(value))
                    .findFirst();
        }

        private String usage(String label) {
            String base = "/" + label + " sanction " + command + " <sanction-id> ";
            return switch (this) {
                case REDUCE -> base + "<new-expiration-or-duration> [--request <request-id>] <reason>";
                case END, REVOKE -> base + "[--request <request-id>] <reason>";
                case OVERTURN -> base + "[--appeal <appeal-id>] [--request <request-id>] <reason>";
            };
        }
    }

    private record PendingChange(
            Actor actor,
            Operation operation,
            Parsed parsed,
            boolean bypassHierarchy
    ) {
    }

    private record Parsed(
            UUID sanctionId,
            Optional<Instant> expiration,
            String reason,
            Optional<UUID> appealId,
            Optional<UUID> punishmentRequestId,
            String canonical,
            String error
    ) {
        private static Parsed error(String message) {
            return new Parsed(
                    new UUID(0L, 0L),
                    Optional.empty(),
                    "invalid",
                    Optional.empty(),
                    Optional.empty(),
                    "invalid",
                    message
            );
        }
    }
}
