package net.enthusia.staff.discordbot;

import java.util.concurrent.atomic.AtomicBoolean;
import net.dv8tion.jda.api.events.message.MessageUpdateEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;

/** Passive D09 edit-history listener; interaction acknowledgements remain owned by the primary listener. */
final class JdaDiscordInvestigationEvidenceListener extends ListenerAdapter {
    private static final System.Logger LOGGER =
            System.getLogger(JdaDiscordInvestigationEvidenceListener.class.getName());

    private final long guildId;
    private final StaffBotWorkerPool workers;
    private final DiscordInvestigationCommandController controller;
    private final AtomicBoolean enabled = new AtomicBoolean();

    JdaDiscordInvestigationEvidenceListener(
            long guildId,
            StaffBotWorkerPool workers,
            DiscordInvestigationService service
    ) {
        if (guildId <= 0 || workers == null || service == null) {
            throw new IllegalArgumentException("investigation evidence listener dependencies are invalid");
        }
        this.guildId = guildId;
        this.workers = workers;
        this.controller = new DiscordInvestigationCommandController(service);
    }

    void enable() {
        enabled.set(true);
    }

    void disable() {
        enabled.set(false);
    }

    @Override
    public void onMessageUpdate(MessageUpdateEvent event) {
        if (!enabled.get() || event.getGuild().getIdLong() != guildId) {
            return;
        }
        boolean scheduled = workers.tryExecute(() -> recordEdit(event));
        if (!scheduled) {
            log("discord_evidence_edit_queue_saturated", null);
        }
    }

    private void recordEdit(MessageUpdateEvent event) {
        try {
            controller.recordEdit(event.getMessage());
        } catch (RuntimeException exception) {
            log("discord_evidence_edit_record_failed", exception);
        }
    }

    private static void log(String code, Throwable failure) {
        if (LOGGER.isLoggable(System.Logger.Level.WARNING)) {
            String type = failure == null ? "unknown" : failure.getClass().getSimpleName();
            LOGGER.log(System.Logger.Level.WARNING, "{0} type={1}", code, type);
        }
    }
}
