package net.enthusia.staff.paper.aireview;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class AiReviewSafetyContractTest {
    private static final Path SOURCE = Path.of(
            "src/main/java/net/enthusia/staff/paper/aireview"
    );

    @Test
    void aiReviewPackageHasNoPunishmentDispatchPath() throws IOException {
        String source = Files.list(SOURCE)
                .filter(path -> path.toString().endsWith(".java"))
                .sorted()
                .map(AiReviewSafetyContractTest::read)
                .collect(Collectors.joining("\n"));

        assertFalse(source.contains("net.enthusia.staff.paper.punishment"));
        assertFalse(source.contains("PunishmentService"));
        assertFalse(source.contains("PunishmentRequestService"));
        assertFalse(source.contains("dispatchCommand("));
        assertFalse(source.contains("performCommand("));
        assertFalse(source.contains("staffapi punish"));
    }

    @Test
    void guiRechecksPermissionsAndCentralStateBeforeWrites() throws IOException {
        String source = Files.readString(SOURCE.resolve("AiReviewGuiController.java"));
        int click = source.indexOf("public void onClick");
        int queueGate = source.indexOf("AiReviewPermissions.queue(viewer)", click);
        int confirm = source.indexOf("private void confirmClick");
        int correctionGate = source.indexOf("AiReviewPermissions.correct(viewer)", confirm);
        int refresh = source.indexOf("private void prewriteRefresh");
        int load = source.indexOf("subsystem.loadEvent(", refresh);
        int authorityRecheck = source.indexOf("AiReviewPermissions.authority(", load);
        int write = source.indexOf("write(viewer, state, fresh, currentAuthority)", authorityRecheck);

        assertTrue(click >= 0 && queueGate > click);
        assertTrue(confirm >= 0 && correctionGate > confirm);
        assertTrue(refresh >= 0 && load > refresh);
        assertTrue(authorityRecheck > load && write > authorityRecheck);
        assertTrue(source.contains("AiReviewPermissions.detail(viewer)"));
        assertTrue(source.contains("AiReviewPermissions.correct(viewer)"));
        assertTrue(source.contains("activeGeneration.getOrDefault"));
        assertTrue(source.contains("AiReviewWriteFence.valid"));
    }

    @Test
    void confirmScreenIsRetiredBeforeAnyCentralWriteDispatch() throws IOException {
        String source = Files.readString(SOURCE.resolve("AiReviewGuiController.java"));
        int write = source.indexOf("private void write(");
        int retire = source.indexOf(
                "activeGeneration.remove(viewer.getUniqueId(), state.generation())",
                write
        );
        int correct = source.indexOf("subsystem.correct(", write);
        int reject = source.indexOf("subsystem.reject(", write);
        assertTrue(write >= 0 && retire > write);
        assertTrue(correct > retire);
        assertTrue(reject > retire);
    }

    @Test
    void remoteBearerTransportRequiresHttps() throws IOException {
        String source = Files.readString(SOURCE.resolve("AiReviewConfiguration.java"));
        assertTrue(source.contains("\"http\".equalsIgnoreCase(scheme) && !isLoopbackHost(uri.getHost())"));
        assertTrue(source.contains("base-url must use https unless the host is loopback"));
        assertTrue(source.contains("\"127\".equals(octets[0])"));
    }

    @Test
    void adminOverrideRequiresResolvedAdminRankAsWellAsPermission() throws IOException {
        String source = Files.readString(SOURCE.resolve("AiReviewPermissions.java"));
        assertTrue(source.contains("PaperStaffRankResolver.resolve(sender::hasPermission)"));
        assertTrue(source.contains("rank.atLeast(StaffRank.ADMIN)"));
        assertTrue(source.contains("sender.hasPermission(ADMIN)"));
    }

    @Test
    void newerScreensRetirePendingLoadsAndFenceScheduledOldScreens() throws IOException {
        String source = Files.readString(SOURCE.resolve("AiReviewGuiController.java"));
        int generation = source.indexOf("private long nextGeneration");
        int retire = source.indexOf("loadFence.retire(viewerId)", generation);
        int open = source.indexOf("private void open(Player viewer, AiReviewGuiState state)");
        int scheduledGate = source.indexOf("activeGeneration.getOrDefault", open);
        int close = source.indexOf("public void onClose(InventoryCloseEvent event)");
        int closeFence = source.indexOf("activeGeneration.remove(viewerId, state.generation())", close);
        assertTrue(generation >= 0 && retire > generation);
        assertTrue(open >= 0 && scheduledGate > open);
        assertTrue(close >= 0 && closeFence > close);
    }

    @Test
    void sensitiveEventDetailIsNotDumpedToServerConsole() throws IOException {
        String source = Files.readString(SOURCE.resolve("AiReviewCommand.java"));
        int view = source.indexOf("private void view");
        int playerGate = source.indexOf("if (!(sender instanceof Player player))", view);
        int guiOpen = source.indexOf("gui.openEvent(player, arguments[1], 0)", playerGate);
        assertTrue(view >= 0 && playerGate > view && guiOpen > playerGate);
        assertFalse(source.substring(view, guiOpen).contains("AiReviewPresentation.detailLines"));
    }

    @Test
    void textFallbackRequiresExplicitConfirmationAndStablePlayerIdentity() throws IOException {
        String source = Files.readString(SOURCE.resolve("AiReviewCommand.java"));
        assertTrue(source.contains("Append the exact word CONFIRM"));
        assertTrue(source.contains("AiReviewPresentation.correctionDecisionLines"));
        assertTrue(source.contains("player.getUniqueId().toString()"));
        assertTrue(source.contains("\"allow\""));
        assertTrue(source.contains("\"block\""));
        assertTrue(source.contains("\"label\""));
        assertTrue(source.contains("\"approve\""));
        assertTrue(source.contains("\"reject\""));
    }

    @Test
    void conflictsRefreshAuthoritativeStateAndDoNotMasqueradeAsOutages() throws IOException {
        String subsystem = Files.readString(SOURCE.resolve("AiReviewSubsystem.java"));
        String gui = Files.readString(SOURCE.resolve("AiReviewGuiController.java"));
        assertTrue(subsystem.contains("exception.category() == AiReviewClientException.Category.CONFLICT"));
        assertTrue(subsystem.contains("failure.accept(\"central review conflict\")"));
        assertTrue(gui.contains("\"central review conflict\".equals(issue)"));
        assertTrue(gui.contains("openEvent(viewer, fresh.eventId(), returnPage)"));
    }

    @Test
    void messageAndContextLoreAreBoundedAndWrapped() throws IOException {
        String source = Files.readString(SOURCE.resolve("AiReviewGuiRenderer.java"));
        assertTrue(source.contains("appendWrapped("));
        assertTrue(source.contains("configuration.maximumMessageCharacters()"));
        assertTrue(source.contains("configuration.maximumContextItems()"));
        assertTrue(source.contains("lines.size() < 24"));
    }

    @Test
    void detailGuiKeepsCorrectionHistoryOnADedicatedBoundedSurface() throws IOException {
        String source = Files.readString(SOURCE.resolve("AiReviewGuiRenderer.java"));
        assertTrue(source.contains("\"Correction history\""));
        assertTrue(source.contains("correctionLore(details)"));
        assertTrue(source.contains("details.acceptedCorrection()"));
        assertTrue(source.contains(".limit(10)"));
    }

    @Test
    void permissionTreeKeepsReviewerAndAdminAuthoritySeparate() throws IOException {
        String manifest = Files.readString(Path.of("src/main/resources/plugin.yml"));
        int mod = manifest.indexOf("enthusiastaff.rank.mod:");
        int admin = manifest.indexOf("enthusiastaff.rank.admin:");
        int founder = manifest.indexOf("enthusiastaff.rank.founder:");
        String modSection = manifest.substring(mod, admin);
        String adminSection = manifest.substring(admin, founder);

        assertTrue(modSection.contains("enthusiastaff.ai-review.queue: true"));
        assertTrue(modSection.contains("enthusiastaff.ai-review.detail: true"));
        assertTrue(modSection.contains("enthusiastaff.ai-review.correct: true"));
        assertFalse(modSection.contains("enthusiastaff.ai-review.admin: true"));
        assertTrue(adminSection.contains("enthusiastaff.ai-review.admin: true"));
    }

    @Test
    void optionalConfigurationIsDisabledByDefaultAndProviderNeutral() throws IOException {
        String config = Files.readString(Path.of("src/main/resources/config.yml"));
        int section = config.indexOf("ai-review:");
        String ai = config.substring(section);
        assertTrue(ai.contains("enabled: false"));
        assertTrue(ai.contains("base-url: \"\""));
        assertTrue(ai.contains("token-environment: ES_AI_REVIEW_TOKEN"));
        assertTrue(ai.contains("notification-permission: enthusiastaff.ai-review.queue"));
    }

    private static String read(Path path) {
        try {
            return Files.readString(path);
        } catch (IOException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
