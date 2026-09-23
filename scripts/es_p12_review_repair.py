from pathlib import Path


def replace(path: str, old: str, new: str) -> None:
    file = Path(path)
    text = file.read_text()
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"expected exactly one match in {path}, found {count}")
    file.write_text(text.replace(old, new))


def write(path: str, content: str) -> None:
    file = Path(path)
    file.parent.mkdir(parents=True, exist_ok=True)
    file.write_text(content)


# /staffwho: preserve entity ownership while respecting the viewer's vanish matrix.
replace(
    "paper/src/main/java/net/enthusia/staff/paper/command/StaffWhoCommand.java",
    "import java.util.function.Consumer;\nimport java.util.function.Supplier;",
    "import java.util.function.BiPredicate;\nimport java.util.function.Consumer;\nimport java.util.function.Supplier;"
)
replace(
    "paper/src/main/java/net/enthusia/staff/paper/command/StaffWhoCommand.java",
    '''    private void collectOnlineStaff(CommandSender sender) {\n        plugin.getServer().getGlobalRegionScheduler().execute(plugin, () -> {\n            List<Player> online = new ArrayList<>(plugin.getServer().getOnlinePlayers());\n            if (online.isEmpty()) {\n                submit(sender, List.of());\n                return;\n            }\n            collectOwnedSnapshots(sender, online);\n        });\n    }\n\n    private void collectOwnedSnapshots(CommandSender sender, List<Player> online) {\n        ConcurrentLinkedQueue<Entry> entries = new ConcurrentLinkedQueue<>();\n        AtomicInteger remaining = new AtomicInteger(online.size());\n        for (Player player : online) {\n            scheduleSnapshot(\n                    plugin,\n                    player,\n                    () -> safeSnapshot(player),\n                    entries::add,\n                    () -> completeSnapshot(sender, entries, remaining)\n            );\n        }\n    }''',
    '''    private void collectOnlineStaff(CommandSender sender) {\n        UUID viewerId = sender instanceof Player player ? player.getUniqueId() : null;\n        plugin.getServer().getGlobalRegionScheduler().execute(plugin, () -> {\n            List<Player> online = new ArrayList<>(plugin.getServer().getOnlinePlayers());\n            if (online.isEmpty()) {\n                submit(sender, List.of());\n                return;\n            }\n            collectOwnedSnapshots(sender, viewerId, online);\n        });\n    }\n\n    private void collectOwnedSnapshots(CommandSender sender, UUID viewerId, List<Player> online) {\n        ConcurrentLinkedQueue<Entry> entries = new ConcurrentLinkedQueue<>();\n        AtomicInteger remaining = new AtomicInteger(online.size());\n        for (Player player : online) {\n            scheduleSnapshot(\n                    plugin,\n                    player,\n                    () -> safeSnapshot(player, viewerId),\n                    entries::add,\n                    () -> completeSnapshot(sender, entries, remaining)\n            );\n        }\n    }'''
)
replace(
    "paper/src/main/java/net/enthusia/staff/paper/command/StaffWhoCommand.java",
    '''    private Entry safeSnapshot(Player player) {\n        try {\n            return snapshot(player);\n        } catch (RuntimeException exception) {\n            plugin.getLogger().log(Level.WARNING, "Staff presence snapshot failed", exception);\n            return null;\n        }\n    }\n\n    private Entry snapshot(Player player) {\n        StaffRank rank = PaperStaffRankResolver.resolve(player::hasPermission).orElse(null);\n        if (rank == null) {\n            return null;\n        }\n        UUID playerId = player.getUniqueId();\n        return new Entry(player.getName(), rank, staffMode.active(playerId), vanish.isVanished(playerId));\n    }''',
    '''    private Entry safeSnapshot(Player player, UUID viewerId) {\n        try {\n            return snapshot(player, viewerId);\n        } catch (RuntimeException exception) {\n            plugin.getLogger().log(Level.WARNING, "Staff presence snapshot failed", exception);\n            return null;\n        }\n    }\n\n    private Entry snapshot(Player player, UUID viewerId) {\n        StaffRank rank = PaperStaffRankResolver.resolve(player::hasPermission).orElse(null);\n        if (rank == null) {\n            return null;\n        }\n        UUID playerId = player.getUniqueId();\n        if (!visibleTo(viewerId, playerId, vanish::canSee)) {\n            return null;\n        }\n        return new Entry(player.getName(), rank, staffMode.active(playerId), vanish.isVanished(playerId));\n    }\n\n    static boolean visibleTo(UUID viewerId, UUID targetId, BiPredicate<UUID, UUID> visibility) {\n        return viewerId == null || visibility.test(viewerId, targetId);\n    }'''
)
replace(
    "paper/src/main/java/net/enthusia/staff/paper/visibility/VanishManager.java",
    '''    public boolean isVanished(UUID playerId) {\n        return visibility.isVanished(playerId);\n    }\n\n    int vanishedOnlineCount() {''',
    '''    public boolean isVanished(UUID playerId) {\n        return visibility.isVanished(playerId);\n    }\n\n    public boolean canSee(UUID viewerId, UUID targetId) {\n        return visibility.canSee(viewerId, targetId);\n    }\n\n    int vanishedOnlineCount() {'''
)
replace(
    "paper/src/test/java/net/enthusia/staff/paper/command/StaffWhoCommandTest.java",
    "import java.util.List;\nimport java.util.concurrent.atomic.AtomicBoolean;",
    "import java.util.List;\nimport java.util.UUID;\nimport java.util.concurrent.atomic.AtomicBoolean;"
)
replace(
    "paper/src/test/java/net/enthusia/staff/paper/command/StaffWhoCommandTest.java",
    '''    @Test\n    void pendingCountDoesNotPretendFiveHundredIsExhaustive() {\n        assertEquals("499", StaffWhoCommand.pendingLabel(499));\n        assertEquals("500+", StaffWhoCommand.pendingLabel(500));\n        assertEquals("500+", StaffWhoCommand.pendingLabel(501));\n    }\n''',
    '''    @Test\n    void pendingCountDoesNotPretendFiveHundredIsExhaustive() {\n        assertEquals("499", StaffWhoCommand.pendingLabel(499));\n        assertEquals("500+", StaffWhoCommand.pendingLabel(500));\n        assertEquals("500+", StaffWhoCommand.pendingLabel(501));\n    }\n\n    @Test\n    void visibilityPolicyFiltersPlayerViewersButNotConsole() {\n        UUID viewer = UUID.fromString("20000000-0000-0000-0000-000000000001");\n        UUID target = UUID.fromString("20000000-0000-0000-0000-000000000002");\n\n        assertFalse(StaffWhoCommand.visibleTo(viewer, target, (ignoredViewer, ignoredTarget) -> false));\n        assertTrue(StaffWhoCommand.visibleTo(viewer, target, (ignoredViewer, ignoredTarget) -> true));\n        assertTrue(StaffWhoCommand.visibleTo(null, target, (ignoredViewer, ignoredTarget) -> false));\n    }\n'''
)

# Ban login enforcement must remain fail-closed in persisted read-only failure mode.
replace(
    "paper/src/main/java/net/enthusia/staff/paper/enforcement/PaperBanEnforcementListener.java",
    '''    @EventHandler(priority = EventPriority.LOWEST)\n    public void onPreLogin(AsyncPlayerPreLoginEvent event) {\n        if (mode.get() != OperationalMode.ACTIVE) {\n            return;\n        }\n        LoginDecision decision = decision(event.getUniqueId(), clock.instant());''',
    '''    @EventHandler(priority = EventPriority.LOWEST)\n    public void onPreLogin(AsyncPlayerPreLoginEvent event) {\n        if (!enforcesLogin(mode.get())) {\n            return;\n        }\n        LoginDecision decision = decision(event.getUniqueId(), clock.instant());'''
)
replace(
    "paper/src/main/java/net/enthusia/staff/paper/enforcement/PaperBanEnforcementListener.java",
    '''    private LoginDecision decision(UUID playerId, Instant now) {''',
    '''    static boolean enforcesLogin(OperationalMode current) {\n        return current == OperationalMode.ACTIVE || current == OperationalMode.READ_ONLY_FAILURE;\n    }\n\n    private LoginDecision decision(UUID playerId, Instant now) {'''
)
replace(
    "paper/src/test/java/net/enthusia/staff/paper/enforcement/PaperBanEnforcementListenerTest.java",
    "import static org.junit.jupiter.api.Assertions.assertEquals;\nimport static org.junit.jupiter.api.Assertions.assertTrue;",
    "import static org.junit.jupiter.api.Assertions.assertEquals;\nimport static org.junit.jupiter.api.Assertions.assertFalse;\nimport static org.junit.jupiter.api.Assertions.assertTrue;"
)
replace(
    "paper/src/test/java/net/enthusia/staff/paper/enforcement/PaperBanEnforcementListenerTest.java",
    "import net.enthusia.staff.common.CaseId;",
    "import net.enthusia.staff.common.CaseId;\nimport net.enthusia.staff.domain.OperationalMode;"
)
replace(
    "paper/src/test/java/net/enthusia/staff/paper/enforcement/PaperBanEnforcementListenerTest.java",
    '''    @Test\n    void unavailableAuthorityUsesGenericFailClosedMessage() {''',
    '''    @Test\n    void loginEnforcementContinuesDuringReadOnlyFailure() {\n        assertTrue(PaperBanEnforcementListener.enforcesLogin(OperationalMode.ACTIVE));\n        assertTrue(PaperBanEnforcementListener.enforcesLogin(OperationalMode.READ_ONLY_FAILURE));\n        assertFalse(PaperBanEnforcementListener.enforcesLogin(OperationalMode.BOOTSTRAP));\n        assertFalse(PaperBanEnforcementListener.enforcesLogin(OperationalMode.DEGRADED));\n        assertFalse(PaperBanEnforcementListener.enforcesLogin(OperationalMode.SHADOW_MIGRATION));\n        assertFalse(PaperBanEnforcementListener.enforcesLogin(OperationalMode.MAINTENANCE));\n    }\n\n    @Test\n    void unavailableAuthorityUsesGenericFailClosedMessage() {'''
)

# Freeze movement blocks translation but keeps the player's requested camera orientation.
replace(
    "paper/src/main/java/net/enthusia/staff/paper/freeze/FreezeManager.java",
    "import java.time.Instant;\nimport java.util.UUID;",
    "import java.time.Instant;\nimport java.util.ArrayList;\nimport java.util.List;\nimport java.util.UUID;"
)
replace(
    "paper/src/main/java/net/enthusia/staff/paper/freeze/FreezeManager.java",
    "import org.bukkit.plugin.java.JavaPlugin;",
    "import org.bukkit.plugin.Plugin;\nimport org.bukkit.plugin.java.JavaPlugin;"
)
replace(
    "paper/src/main/java/net/enthusia/staff/paper/freeze/FreezeManager.java",
    '''        if (to == null || (from.getX() == to.getX() && from.getY() == to.getY() && from.getZ() == to.getZ())) {\n            return;\n        }\n        event.setCancelled(true);''',
    '''        if (to == null || (from.getX() == to.getX() && from.getY() == to.getY() && from.getZ() == to.getZ())) {\n            return;\n        }\n        Location stationary = from.clone();\n        stationary.setYaw(to.getYaw());\n        stationary.setPitch(to.getPitch());\n        event.setTo(stationary);'''
)
replace(
    "paper/src/test/java/net/enthusia/staff/paper/freeze/FreezeMovementAndCommandTest.java",
    "import static org.junit.jupiter.api.Assertions.assertFalse;\nimport static org.junit.jupiter.api.Assertions.assertTrue;",
    "import static org.junit.jupiter.api.Assertions.assertEquals;\nimport static org.junit.jupiter.api.Assertions.assertFalse;\nimport static org.junit.jupiter.api.Assertions.assertTrue;"
)
replace(
    "paper/src/test/java/net/enthusia/staff/paper/freeze/FreezeMovementAndCommandTest.java",
    '''    @Test\n    void positionalMovementIsCancelledInsteadOfRewritten() {\n        FreezeManager manager = restrictedManager();\n        Player player = player(new ArrayList<>());\n        PlayerMoveEvent event = new PlayerMoveEvent(\n                player,\n                new Location(null, 10.0, 64.0, 10.0, 0.0f, 0.0f),\n                new Location(null, 10.5, 64.0, 10.0, 45.0f, 0.0f)\n        );\n\n        manager.onMove(event);\n\n        assertTrue(event.isCancelled());\n    }''',
    '''    @Test\n    void positionalMovementKeepsRequestedOrientationAtOriginalPosition() {\n        FreezeManager manager = restrictedManager();\n        Player player = player(new ArrayList<>());\n        PlayerMoveEvent event = new PlayerMoveEvent(\n                player,\n                new Location(null, 10.0, 64.0, 10.0, 0.0f, 0.0f),\n                new Location(null, 10.5, 64.0, 10.0, 45.0f, 15.0f)\n        );\n\n        manager.onMove(event);\n\n        assertFalse(event.isCancelled());\n        assertEquals(10.0, event.getTo().getX());\n        assertEquals(64.0, event.getTo().getY());\n        assertEquals(10.0, event.getTo().getZ());\n        assertEquals(45.0f, event.getTo().getYaw());\n        assertEquals(15.0f, event.getTo().getPitch());\n    }'''
)

# Freeze fanout operations: global scheduler snapshots only; Player operations stay entity-owned.
replace(
    "paper/src/main/java/net/enthusia/staff/paper/freeze/FreezeManager.java",
    '''    private void relayFrozenChat(Player player, Component body) {\n        UUID playerId = player.getUniqueId();\n        String playerName = player.getName();\n        Component rendered = Component.text("<" + playerName + "> ").append(body);\n        scheduleGlobal(() -> {\n            Player current = plugin.getServer().getPlayer(playerId);\n            if (current != null) {\n                current.sendMessage(rendered);\n            }\n            plugin.getServer().getOnlinePlayers().stream()\n                    .filter(staff -> !staff.getUniqueId().equals(playerId))\n                    .filter(staff -> staff.hasPermission("enthusiastaff.freeze.chat"))\n                    .forEach(staff -> staff.sendMessage(Component.text("[Frozen Chat] ").append(rendered)));\n        });\n    }''',
    '''    private void relayFrozenChat(Player player, Component body) {\n        UUID playerId = player.getUniqueId();\n        String playerName = player.getName();\n        Component rendered = Component.text("<" + playerName + "> ").append(body);\n        Component staffMessage = Component.text("[Frozen Chat] ").append(rendered);\n        scheduleGlobal(() -> {\n            List<Player> online = new ArrayList<>(plugin.getServer().getOnlinePlayers());\n            online.forEach(recipient -> scheduleRecipient(plugin, recipient, () -> {\n                if (recipient.getUniqueId().equals(playerId)) {\n                    recipient.sendMessage(rendered);\n                } else if (recipient.hasPermission("enthusiastaff.freeze.chat")) {\n                    recipient.sendMessage(staffMessage);\n                }\n            }));\n        });\n    }'''
)
replace(
    "paper/src/main/java/net/enthusia/staff/paper/freeze/FreezeManager.java",
    '''    private void sendAlert(String message) {\n        if (staffAlertSink != null) {\n            staffAlertSink.accept(message);\n            return;\n        }\n        plugin.getServer().getOnlinePlayers().stream()\n                .filter(player -> player.hasPermission("enthusiastaff.freeze"))\n                .forEach(player -> player.sendMessage(Component.text(message)));\n    }''',
    '''    private void sendAlert(String message) {\n        if (staffAlertSink != null) {\n            staffAlertSink.accept(message);\n            return;\n        }\n        Component alert = Component.text(message);\n        List<Player> online = new ArrayList<>(plugin.getServer().getOnlinePlayers());\n        online.forEach(player -> scheduleRecipient(plugin, player, () -> {\n            if (player.hasPermission("enthusiastaff.freeze")) {\n                player.sendMessage(alert);\n            }\n        }));\n    }\n\n    static boolean scheduleRecipient(Plugin plugin, Player player, Runnable operation) {\n        try {\n            return player.getScheduler().execute(plugin, operation, null, 1L);\n        } catch (RuntimeException exception) {\n            return false;\n        }\n    }'''
)

# Paper disconnect presence must use a bounded asynchronous retry instead of dropping the only observation.
replace(
    "paper/src/main/java/net/enthusia/staff/paper/PaperPresenceListener.java",
    "import java.util.concurrent.RejectedExecutionException;",
    "import java.util.concurrent.RejectedExecutionException;\nimport java.util.concurrent.TimeUnit;"
)
replace(
    "paper/src/main/java/net/enthusia/staff/paper/PaperPresenceListener.java",
    '''final class PaperPresenceListener implements Listener {\n    private final Clock clock;''',
    '''final class PaperPresenceListener implements Listener {\n    private static final int MAX_SUBMISSION_ATTEMPTS = 3;\n    private static final long RETRY_DELAY_MILLIS = 100L;\n\n    private final Clock clock;'''
)
replace(
    "paper/src/main/java/net/enthusia/staff/paper/PaperPresenceListener.java",
    '''    private final Consumer<Runnable> submitter;\n    private final Logger logger;''',
    '''    private final Consumer<Runnable> submitter;\n    private final Consumer<Runnable> retryScheduler;\n    private final Logger logger;'''
)
replace(
    "paper/src/main/java/net/enthusia/staff/paper/PaperPresenceListener.java",
    '''    ) {\n        this(clock, serverId, players, workers::execute, plugin.getLogger());\n    }\n\n    PaperPresenceListener(\n            Clock clock,\n            String serverId,\n            Supplier<PlayerDirectory> players,\n            Consumer<Runnable> submitter,\n            Logger logger\n    ) {''',
    '''    ) {\n        this(\n                clock, serverId, players, workers::execute,\n                retry -> plugin.getServer().getAsyncScheduler().runDelayed(\n                        plugin, ignored -> retry.run(), RETRY_DELAY_MILLIS, TimeUnit.MILLISECONDS\n                ),\n                plugin.getLogger()\n        );\n    }\n\n    PaperPresenceListener(\n            Clock clock,\n            String serverId,\n            Supplier<PlayerDirectory> players,\n            Consumer<Runnable> submitter,\n            Consumer<Runnable> retryScheduler,\n            Logger logger\n    ) {'''
)
replace(
    "paper/src/main/java/net/enthusia/staff/paper/PaperPresenceListener.java",
    '''        this.players = java.util.Objects.requireNonNull(players, "players");\n        this.submitter = java.util.Objects.requireNonNull(submitter, "submitter");\n        this.logger = java.util.Objects.requireNonNull(logger, "logger");''',
    '''        this.players = java.util.Objects.requireNonNull(players, "players");\n        this.submitter = java.util.Objects.requireNonNull(submitter, "submitter");\n        this.retryScheduler = java.util.Objects.requireNonNull(retryScheduler, "retryScheduler");\n        this.logger = java.util.Objects.requireNonNull(logger, "logger");'''
)
replace(
    "paper/src/main/java/net/enthusia/staff/paper/PaperPresenceListener.java",
    '''    void recordDisconnected(UUID playerId) {\n        Instant disconnectedAt = clock.instant();\n        try {\n            submitter.accept(() -> persistDisconnect(playerId, disconnectedAt));\n        } catch (RejectedExecutionException exception) {\n            logger.warning("Paper presence disconnect skipped because the bounded queue is full");\n        }\n    }''',
    '''    void recordDisconnected(UUID playerId) {\n        submitDisconnect(playerId, clock.instant(), 1);\n    }\n\n    private void submitDisconnect(UUID playerId, Instant disconnectedAt, int attempt) {\n        try {\n            submitter.accept(() -> persistDisconnect(playerId, disconnectedAt));\n        } catch (RejectedExecutionException exception) {\n            scheduleRetry(playerId, disconnectedAt, attempt);\n        }\n    }\n\n    private void scheduleRetry(UUID playerId, Instant disconnectedAt, int attempt) {\n        if (attempt >= MAX_SUBMISSION_ATTEMPTS) {\n            logger.severe("Paper presence disconnect could not be queued after bounded retries");\n            return;\n        }\n        try {\n            retryScheduler.accept(() -> submitDisconnect(playerId, disconnectedAt, attempt + 1));\n        } catch (RuntimeException exception) {\n            logger.log(Level.SEVERE, "Paper presence disconnect retry scheduling failed", exception);\n        }\n    }'''
)

# Staff-mode: failed durable closure retains the recovery fence and does not invoke verified-exit cleanup.
replace(
    "paper/src/main/java/net/enthusia/staff/paper/staff/StaffModeManager.java",
    '''                if (!submit(() -> completeRestoration(playerId, session, loaded, restored))) {\n                    completeRuntimeExit(playerId);\n                    player.sendMessage(Component.text(\n                            "State was restored, but durable verification is still pending; contact an administrator."\n                    ));\n                }''',
    '''                if (!submit(() -> completeRestoration(playerId, session, loaded, restored))) {\n                    retainRecoveryAfterRuntimeExit(playerId);\n                    player.sendMessage(Component.text(\n                            "State was restored, but durable verification is still pending; contact an administrator."\n                    ));\n                }'''
)
replace(
    "paper/src/main/java/net/enthusia/staff/paper/staff/StaffModeManager.java",
    '''        try {\n            closed = loaded.completeExit(session.sessionId(), restored.checksum(), clock.instant());\n        } catch (RuntimeException exception) {\n            completeRuntimeExit(playerId);\n            plugin.getLogger().log(Level.SEVERE, "Staff session closure verification failed", exception);\n            safeMessage(playerId, "State was restored, but durable closure verification failed; contact an administrator.");\n            return;\n        }\n        completeRuntimeExit(playerId);\n        if (!closed) {\n            safeMessage(playerId, "State was restored, but checksum verification requires administrator review.");\n            return;\n        }\n        safeMessage(playerId, "Staff mode exited; your exact saved state was restored and verified.");\n    }\n\n    private void completeRuntimeExit(UUID playerId) {\n        active.remove(playerId);\n        ranks.remove(playerId);\n        toolSessions.remove(playerId);\n        recoveryGate.clear(playerId);\n        try {\n            exitListener.accept(playerId);\n        } catch (RuntimeException exception) {\n            plugin.getLogger().log(Level.WARNING, "Post-exit staff-mode cleanup callback failed", exception);\n        }\n    }''',
    '''        try {\n            closed = loaded.completeExit(session.sessionId(), restored.checksum(), clock.instant());\n        } catch (RuntimeException exception) {\n            retainRecoveryAfterRuntimeExit(playerId);\n            plugin.getLogger().log(Level.SEVERE, "Staff session closure verification failed", exception);\n            safeMessage(playerId, "State was restored, but durable closure verification failed; contact an administrator.");\n            return;\n        }\n        if (!closed) {\n            retainRecoveryAfterRuntimeExit(playerId);\n            safeMessage(playerId, "State was restored, but checksum verification requires administrator review.");\n            return;\n        }\n        completeRuntimeExit(playerId);\n        safeMessage(playerId, "Staff mode exited; your exact saved state was restored and verified.");\n    }\n\n    private void retainRecoveryAfterRuntimeExit(UUID playerId) {\n        recoveryGate.retry(playerId);\n        removeRuntimeState(playerId);\n    }\n\n    private void completeRuntimeExit(UUID playerId) {\n        removeRuntimeState(playerId);\n        recoveryGate.clear(playerId);\n        try {\n            exitListener.accept(playerId);\n        } catch (RuntimeException exception) {\n            plugin.getLogger().log(Level.WARNING, "Post-exit staff-mode cleanup callback failed", exception);\n        }\n    }\n\n    private void removeRuntimeState(UUID playerId) {\n        active.remove(playerId);\n        ranks.remove(playerId);\n        toolSessions.remove(playerId);\n    }'''
)

# Detailed freeze notices: wire the service, deliver on immediate freeze/join, and skip stale queued notices after release.
replace(
    "paper/src/main/java/net/enthusia/staff/paper/freeze/FreezeNoticeService.java",
    '''    private final Supplier<PlayerDirectory> players;\n    private final ExecutorService workers;''',
    '''    private final Supplier<PlayerDirectory> players;\n    private final ExecutorService workers;\n    private final FreezeManager manager;'''
)
replace(
    "paper/src/main/java/net/enthusia/staff/paper/freeze/FreezeNoticeService.java",
    '''            Supplier<FreezeStore> freezes,\n            Supplier<PlayerDirectory> players,\n            ExecutorService workers\n    ) {''',
    '''            Supplier<FreezeStore> freezes,\n            Supplier<PlayerDirectory> players,\n            ExecutorService workers,\n            FreezeManager manager\n    ) {'''
)
replace(
    "paper/src/main/java/net/enthusia/staff/paper/freeze/FreezeNoticeService.java",
    '''        this.players = java.util.Objects.requireNonNull(players, "players");\n        this.workers = java.util.Objects.requireNonNull(workers, "workers");''',
    '''        this.players = java.util.Objects.requireNonNull(players, "players");\n        this.workers = java.util.Objects.requireNonNull(workers, "workers");\n        this.manager = java.util.Objects.requireNonNull(manager, "manager");'''
)
replace(
    "paper/src/main/java/net/enthusia/staff/paper/freeze/FreezeNoticeService.java",
    '''        onEntity(record.playerId(), player -> FreezeNoticePresentation.render(record, actorName)\n                .forEach(player::sendMessage));''',
    '''        onEntity(record.playerId(), player -> deliverIfRestricted(manager, record, actorName, player));'''
)
replace(
    "paper/src/main/java/net/enthusia/staff/paper/freeze/FreezeNoticeService.java",
    '''    @EventHandler(priority = EventPriority.MONITOR)\n    public void onJoin(PlayerJoinEvent event) {''',
    '''    static void deliverIfRestricted(\n            FreezeManager manager,\n            FreezeRecord record,\n            String actorName,\n            Player player\n    ) {\n        if (!manager.isRestricted(record.playerId())) {\n            return;\n        }\n        FreezeNoticePresentation.render(record, actorName).forEach(player::sendMessage);\n    }\n\n    @EventHandler(priority = EventPriority.MONITOR)\n    public void onJoin(PlayerJoinEvent event) {'''
)
replace(
    "paper/src/main/java/net/enthusia/staff/paper/PaperRuntimeComponents.java",
    "import net.enthusia.staff.paper.freeze.FreezeNetworkReconciler;",
    "import net.enthusia.staff.paper.freeze.FreezeNetworkReconciler;\nimport net.enthusia.staff.paper.freeze.FreezeNoticeService;"
)
replace(
    "paper/src/main/java/net/enthusia/staff/paper/PaperRuntimeComponents.java",
    '''        ReportEvidenceMaintenance reportEvidenceMaintenance,\n        FreezeManager freeze,\n        FreezeNetworkReconciler freezeNetworkReconciler,''',
    '''        ReportEvidenceMaintenance reportEvidenceMaintenance,\n        FreezeManager freeze,\n        FreezeNoticeService freezeNotices,\n        FreezeNetworkReconciler freezeNetworkReconciler,'''
)
replace(
    "paper/src/main/java/net/enthusia/staff/paper/PaperRuntimeComponents.java",
    '''        FreezeManager freeze = createFreezeManager(dependencies);\n        FreezeNetworkReconciler freezeNetworkReconciler = new FreezeNetworkReconciler(''',
    '''        FreezeManager freeze = createFreezeManager(dependencies);\n        FreezeNoticeService freezeNotices = createFreezeNoticeService(dependencies, freeze);\n        FreezeNetworkReconciler freezeNetworkReconciler = new FreezeNetworkReconciler('''
)
replace(
    "paper/src/main/java/net/enthusia/staff/paper/PaperRuntimeComponents.java",
    '''                evidence,\n                freeze,\n                freezeNetworkReconciler,''',
    '''                evidence,\n                freeze,\n                freezeNotices,\n                freezeNetworkReconciler,'''
)
replace(
    "paper/src/main/java/net/enthusia/staff/paper/PaperRuntimeComponents.java",
    '''    private static StaffModeManager createStaffModeManager(Dependencies dependencies) {''',
    '''    private static FreezeNoticeService createFreezeNoticeService(\n            Dependencies dependencies,\n            FreezeManager freeze\n    ) {\n        JavaPlugin plugin = dependencies.environment().plugin();\n        FreezeNoticeService notices = new FreezeNoticeService(\n                plugin, dependencies.environment().clock(), dependencies.stores().freezeStore(),\n                dependencies.stores().playerDirectory(), dependencies.environment().workers(), freeze\n        );\n        registerListener(plugin, notices);\n        return notices;\n    }\n\n    private static StaffModeManager createStaffModeManager(Dependencies dependencies) {'''
)
replace(
    "paper/src/main/java/net/enthusia/staff/paper/PaperCommandRegistrar.java",
    "import net.enthusia.staff.paper.freeze.FreezeManager;",
    "import net.enthusia.staff.paper.freeze.FreezeManager;\nimport net.enthusia.staff.paper.freeze.FreezeNoticeSink;"
)
replace(
    "paper/src/main/java/net/enthusia/staff/paper/PaperCommandRegistrar.java",
    '''                plugin(), clock(), writeMode(), storage(PaperStorageBindings::playerDirectory),\n                storage(PaperStorageBindings::freezeStore), dependencies.players().freeze(), workers()\n        );''',
    '''                plugin(), clock(), writeMode(), storage(PaperStorageBindings::playerDirectory),\n                storage(PaperStorageBindings::freezeStore), dependencies.players().freeze(), workers(),\n                dependencies.players().freezeNotices()\n        );'''
)
replace(
    "paper/src/main/java/net/enthusia/staff/paper/PaperCommandRegistrar.java",
    '''    record PlayerComponents(\n            FreezeManager freeze,\n            StaffModeManager staffMode,''',
    '''    record PlayerComponents(\n            FreezeManager freeze,\n            FreezeNoticeSink freezeNotices,\n            StaffModeManager staffMode,'''
)
replace(
    "paper/src/main/java/net/enthusia/staff/paper/EnthusiaStaffPaperPlugin.java",
    '''                new PaperCommandRegistrar.PlayerComponents(\n                        runtimeComponents.freeze(),\n                        runtimeComponents.staffMode(),''',
    '''                new PaperCommandRegistrar.PlayerComponents(\n                        runtimeComponents.freeze(),\n                        runtimeComponents.freezeNotices(),\n                        runtimeComponents.staffMode(),'''
)
replace(
    "paper/src/main/java/net/enthusia/staff/paper/command/FreezeCommand.java",
    "import net.enthusia.staff.domain.player.PlayerIdentity;",
    "import net.enthusia.staff.domain.freeze.FreezeRecord;\nimport net.enthusia.staff.domain.player.PlayerIdentity;"
)
replace(
    "paper/src/main/java/net/enthusia/staff/paper/command/FreezeCommand.java",
    "import net.enthusia.staff.paper.freeze.FreezeManager;",
    "import net.enthusia.staff.paper.freeze.FreezeManager;\nimport net.enthusia.staff.paper.freeze.FreezeNoticeSink;"
)
replace(
    "paper/src/main/java/net/enthusia/staff/paper/command/FreezeCommand.java",
    '''    private final StaffTargetGuard targetGuard;\n    private final FreezeAlertSink alerts;''',
    '''    private final StaffTargetGuard targetGuard;\n    private final FreezeAlertSink alerts;\n    private final FreezeNoticeSink targetNotices;'''
)
replace(
    "paper/src/main/java/net/enthusia/staff/paper/command/FreezeCommand.java",
    '''    public FreezeCommand(\n            JavaPlugin plugin,\n            Clock clock,\n            Supplier<OperationalMode> mode,\n            Supplier<PlayerDirectory> players,\n            Supplier<FreezeStore> freezes,\n            FreezeManager manager,\n            ExecutorService workers\n    ) {\n        this(\n                plugin, clock, mode, players, freezes, manager, workers,\n                new RuntimeHooks(\n                        LuckPermsStaffTargetGuard.discover(plugin),\n                        new FreezeStaffNotifier(plugin),\n                        commandResponses(plugin)\n                )\n        );\n    }''',
    '''    public FreezeCommand(\n            JavaPlugin plugin,\n            Clock clock,\n            Supplier<OperationalMode> mode,\n            Supplier<PlayerDirectory> players,\n            Supplier<FreezeStore> freezes,\n            FreezeManager manager,\n            ExecutorService workers\n    ) {\n        this(plugin, clock, mode, players, freezes, manager, workers, FreezeNoticeSink.noOp());\n    }\n\n    public FreezeCommand(\n            JavaPlugin plugin,\n            Clock clock,\n            Supplier<OperationalMode> mode,\n            Supplier<PlayerDirectory> players,\n            Supplier<FreezeStore> freezes,\n            FreezeManager manager,\n            ExecutorService workers,\n            FreezeNoticeSink targetNotices\n    ) {\n        this(\n                plugin, clock, mode, players, freezes, manager, workers, targetNotices,\n                new RuntimeHooks(\n                        LuckPermsStaffTargetGuard.discover(plugin),\n                        new FreezeStaffNotifier(plugin),\n                        commandResponses(plugin)\n                )\n        );\n    }'''
)
replace(
    "paper/src/main/java/net/enthusia/staff/paper/command/FreezeCommand.java",
    '''        this(\n                plugin, clock, mode, players, freezes, manager, workers,\n                new RuntimeHooks(\n                        (actor, targetId, systemActor) -> StaffTargetGuard.Result.allow(),\n                        FreezeAlertSink.noOp(),\n                        responses\n                )\n        );''',
    '''        this(\n                plugin, clock, mode, players, freezes, manager, workers, FreezeNoticeSink.noOp(),\n                new RuntimeHooks(\n                        (actor, targetId, systemActor) -> StaffTargetGuard.Result.allow(),\n                        FreezeAlertSink.noOp(),\n                        responses\n                )\n        );'''
)
replace(
    "paper/src/main/java/net/enthusia/staff/paper/command/FreezeCommand.java",
    '''    FreezeCommand(\n            JavaPlugin plugin,\n            Clock clock,\n            Supplier<OperationalMode> mode,\n            Supplier<PlayerDirectory> players,\n            Supplier<FreezeStore> freezes,\n            FreezeManager manager,\n            ExecutorService workers,\n            RuntimeHooks hooks\n    ) {\n        this.plugin = plugin;''',
    '''    FreezeCommand(\n            JavaPlugin plugin,\n            Clock clock,\n            Supplier<OperationalMode> mode,\n            Supplier<PlayerDirectory> players,\n            Supplier<FreezeStore> freezes,\n            FreezeManager manager,\n            ExecutorService workers,\n            RuntimeHooks hooks\n    ) {\n        this(plugin, clock, mode, players, freezes, manager, workers, FreezeNoticeSink.noOp(), hooks);\n    }\n\n    private FreezeCommand(\n            JavaPlugin plugin,\n            Clock clock,\n            Supplier<OperationalMode> mode,\n            Supplier<PlayerDirectory> players,\n            Supplier<FreezeStore> freezes,\n            FreezeManager manager,\n            ExecutorService workers,\n            FreezeNoticeSink targetNotices,\n            RuntimeHooks hooks\n    ) {\n        this.plugin = plugin;'''
)
replace(
    "paper/src/main/java/net/enthusia/staff/paper/command/FreezeCommand.java",
    '''        this.targetGuard = hooks.targetGuard();\n        this.alerts = hooks.alerts();\n        this.responses = hooks.responses();''',
    '''        this.targetGuard = hooks.targetGuard();\n        this.alerts = hooks.alerts();\n        this.targetNotices = java.util.Objects.requireNonNull(targetNotices, "targetNotices");\n        this.responses = hooks.responses();'''
)
replace(
    "paper/src/main/java/net/enthusia/staff/paper/command/FreezeCommand.java",
    '''    private void freeze(CommandSender sender, FreezeStore store, PlayerIdentity target, Actor actor, String reason) {\n        store.apply(target.playerId(), actor.id(), reason, clock.instant());\n        manager.applyOnline(target.playerId());\n        alerts.frozen(target, actor, reason);''',
    '''    private void freeze(CommandSender sender, FreezeStore store, PlayerIdentity target, Actor actor, String reason) {\n        FreezeRecord record = store.apply(target.playerId(), actor.id(), reason, clock.instant());\n        manager.applyOnline(target.playerId());\n        targetNotices.show(record, actor.displayName());\n        alerts.frozen(target, actor, reason);'''
)

# Tests for retry, recovery fencing, notice staleness, and entity-scheduler fanout.
write(
    "paper/src/test/java/net/enthusia/staff/paper/PaperPresenceListenerTest.java",
    '''package net.enthusia.staff.paper;\n\nimport static org.junit.jupiter.api.Assertions.assertEquals;\nimport static org.junit.jupiter.api.Assertions.assertNotNull;\nimport static org.junit.jupiter.api.Assertions.assertNull;\n\nimport java.time.Clock;\nimport java.time.Instant;\nimport java.time.ZoneOffset;\nimport java.util.ArrayList;\nimport java.util.List;\nimport java.util.Optional;\nimport java.util.UUID;\nimport java.util.concurrent.RejectedExecutionException;\nimport java.util.concurrent.atomic.AtomicInteger;\nimport java.util.concurrent.atomic.AtomicReference;\nimport java.util.logging.Logger;\nimport net.enthusia.staff.domain.player.PlayerIdentity;\nimport net.enthusia.staff.domain.player.PlayerPlatform;\nimport net.enthusia.staff.domain.player.PlayerPresence;\nimport net.enthusia.staff.domain.ports.PlayerDirectory;\nimport org.junit.jupiter.api.Test;\n\nclass PaperPresenceListenerTest {\n    private static final Instant NOW = Instant.parse("2026-09-23T15:00:00Z");\n    private static final UUID PLAYER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");\n    private static final Logger LOGGER = Logger.getLogger(PaperPresenceListenerTest.class.getName());\n\n    @Test\n    void disconnectIsPersistedWithPaperBackendAndCapturedTime() {\n        AtomicReference<Disconnect> recorded = new AtomicReference<>();\n        PaperPresenceListener listener = listener(directory(recorded), Runnable::run, ignored -> {\n            throw new AssertionError("retry not expected");\n        });\n\n        listener.recordDisconnected(PLAYER_ID);\n\n        assertEquals(new Disconnect(PLAYER_ID, "smp-1", NOW), recorded.get());\n    }\n\n    @Test\n    void rejectedWorkerSubmissionRetriesCapturedDisconnect() {\n        AtomicReference<Disconnect> recorded = new AtomicReference<>();\n        AtomicReference<Runnable> retry = new AtomicReference<>();\n        AtomicInteger attempts = new AtomicInteger();\n        PaperPresenceListener listener = listener(directory(recorded), operation -> {\n            if (attempts.incrementAndGet() == 1) {\n                throw new RejectedExecutionException("full");\n            }\n            operation.run();\n        }, retry::set);\n\n        listener.recordDisconnected(PLAYER_ID);\n\n        assertNull(recorded.get());\n        assertNotNull(retry.get());\n        retry.get().run();\n        assertEquals(2, attempts.get());\n        assertEquals(new Disconnect(PLAYER_ID, "smp-1", NOW), recorded.get());\n    }\n\n    @Test\n    void repeatedQueueRejectionStopsAfterBoundedAttempts() {\n        List<Runnable> retries = new ArrayList<>();\n        AtomicInteger attempts = new AtomicInteger();\n        PaperPresenceListener listener = listener(directory(new AtomicReference<>()), ignored -> {\n            attempts.incrementAndGet();\n            throw new RejectedExecutionException("full");\n        }, retries::add);\n\n        listener.recordDisconnected(PLAYER_ID);\n        while (!retries.isEmpty()) {\n            retries.removeFirst().run();\n        }\n\n        assertEquals(3, attempts.get());\n        assertEquals(0, retries.size());\n    }\n\n    private static PaperPresenceListener listener(\n            PlayerDirectory directory,\n            java.util.function.Consumer<Runnable> submitter,\n            java.util.function.Consumer<Runnable> retryScheduler\n    ) {\n        return new PaperPresenceListener(\n                Clock.fixed(NOW, ZoneOffset.UTC), "smp-1", () -> directory,\n                submitter, retryScheduler, LOGGER\n        );\n    }\n\n    private static PlayerDirectory directory(AtomicReference<Disconnect> recorded) {\n        return new PlayerDirectory() {\n            @Override\n            public Optional<PlayerIdentity> find(String uuidOrUsername) {\n                return Optional.empty();\n            }\n\n            @Override\n            public List<PlayerIdentity> search(String prefix, int limit) {\n                return List.of();\n            }\n\n            @Override\n            public Optional<PlayerPresence> presence(UUID playerId) {\n                return Optional.empty();\n            }\n\n            @Override\n            public void recordSeen(\n                    UUID playerId, String username, PlayerPlatform platform, String serverId, Instant seenAt\n            ) {\n            }\n\n            @Override\n            public void recordDisconnected(UUID playerId, String serverId, Instant disconnectedAt) {\n                recorded.set(new Disconnect(playerId, serverId, disconnectedAt));\n            }\n        };\n    }\n\n    private record Disconnect(UUID playerId, String serverId, Instant disconnectedAt) {\n    }\n}\n'''
)
write(
    "paper/src/test/java/net/enthusia/staff/paper/staff/StaffModeRestorationWiringTest.java",
    '''package net.enthusia.staff.paper.staff;\n\nimport static org.junit.jupiter.api.Assertions.assertTrue;\n\nimport java.io.IOException;\nimport java.nio.file.Files;\nimport java.nio.file.Path;\nimport org.junit.jupiter.api.Test;\n\nclass StaffModeRestorationWiringTest {\n    private static final Path SOURCE = Path.of(\n            "src/main/java/net/enthusia/staff/paper/staff/StaffModeManager.java"\n    );\n\n    @Test\n    void savedStateRestoreIsAuthorizedAndClearsSpectatorTarget() throws IOException {\n        String method = method("private boolean restoreSavedState", "private void completeRestoration");\n\n        assertTrue(method.indexOf("profileApplications.add(playerId)") < method.indexOf("codec.restore"));\n        assertTrue(method.indexOf("setSpectatorTarget(null)") < method.indexOf("codec.restore"));\n        assertTrue(method.indexOf("codec.restore") < method.indexOf("profileApplications.remove(playerId)"));\n    }\n\n    @Test\n    void rejectedVerificationSubmissionKeepsRecoveryFence() throws IOException {\n        String method = method("private void restoreAndVerify", "private boolean restoreSavedState");\n        int rejected = method.indexOf("if (!submit(() -> completeRestoration");\n        int retained = method.indexOf("retainRecoveryAfterRuntimeExit(playerId)", rejected);\n\n        assertTrue(rejected >= 0);\n        assertTrue(retained > rejected);\n    }\n\n    @Test\n    void verificationFailureKeepsRecoveryFenceUntilDurableClosure() throws IOException {\n        String method = method("private void completeRestoration", "private void retainRecoveryAfterRuntimeExit");\n        int verification = method.indexOf("loaded.completeExit");\n        int catchRetention = method.indexOf("retainRecoveryAfterRuntimeExit(playerId)", verification);\n        int mismatch = method.indexOf("if (!closed)", catchRetention);\n        int mismatchRetention = method.indexOf("retainRecoveryAfterRuntimeExit(playerId)", mismatch);\n        int successCleanup = method.indexOf("completeRuntimeExit(playerId)", mismatchRetention);\n\n        assertTrue(verification >= 0);\n        assertTrue(catchRetention > verification);\n        assertTrue(mismatch > catchRetention);\n        assertTrue(mismatchRetention > mismatch);\n        assertTrue(successCleanup > mismatchRetention);\n    }\n\n    @Test\n    void failedClosureDoesNotInvokeVerifiedExitListener() throws IOException {\n        String retained = method("private void retainRecoveryAfterRuntimeExit", "private void completeRuntimeExit");\n        String completed = method("private void completeRuntimeExit", "private void removeRuntimeState");\n\n        assertTrue(retained.contains("recoveryGate.retry(playerId)"));\n        assertTrue(!retained.contains("exitListener.accept"));\n        assertTrue(completed.contains("recoveryGate.clear(playerId)"));\n        assertTrue(completed.contains("exitListener.accept(playerId)"));\n    }\n\n    @Test\n    void cleanExitSuccessMessageIsOnlyEmittedAfterVerificationPasses() throws IOException {\n        String method = method("private void completeRestoration", "private void retainRecoveryAfterRuntimeExit");\n        int mismatch = method.indexOf("if (!closed)");\n        int mismatchReturn = method.indexOf("return;", mismatch);\n        int cleanup = method.indexOf("completeRuntimeExit(playerId)", mismatchReturn);\n        int success = method.indexOf("Staff mode exited; your exact saved state was restored and verified.");\n\n        assertTrue(mismatch >= 0 && mismatchReturn > mismatch);\n        assertTrue(cleanup > mismatchReturn);\n        assertTrue(success > cleanup);\n    }\n\n    private static String method(String startMarker, String endMarker) throws IOException {\n        String source = Files.readString(SOURCE);\n        int start = source.indexOf(startMarker);\n        int end = source.indexOf(endMarker, start + startMarker.length());\n        if (start < 0 || end <= start) {\n            throw new IllegalStateException("Could not locate staff-mode restoration method boundaries");\n        }\n        return source.substring(start, end);\n    }\n}\n'''
)
write(
    "paper/src/test/java/net/enthusia/staff/paper/freeze/FreezeNoticeServiceTest.java",
    '''package net.enthusia.staff.paper.freeze;\n\nimport static org.junit.jupiter.api.Assertions.assertEquals;\nimport static org.junit.jupiter.api.Assertions.assertTrue;\n\nimport java.lang.reflect.Array;\nimport java.lang.reflect.Proxy;\nimport java.time.Clock;\nimport java.time.Instant;\nimport java.util.ArrayList;\nimport java.util.List;\nimport java.util.Optional;\nimport java.util.UUID;\nimport java.util.logging.Logger;\nimport net.enthusia.staff.domain.freeze.FreezeRecord;\nimport net.kyori.adventure.text.Component;\nimport org.bukkit.entity.Player;\nimport org.junit.jupiter.api.Test;\n\nclass FreezeNoticeServiceTest {\n    private static final UUID PLAYER_ID = UUID.fromString("91000000-0000-0000-0000-000000000001");\n\n    @Test\n    void queuedNoticeIsSkippedAfterReleaseGenerationWins() {\n        FreezeManager manager = manager();\n        List<Component> messages = new ArrayList<>();\n        Player player = player(messages);\n        FreezeRecord record = record();\n\n        manager.applyOnline(PLAYER_ID);\n        FreezeNoticeService.deliverIfRestricted(manager, record, "Moderator", player);\n        assertTrue(messages.size() > 1);\n        int beforeRelease = messages.size();\n\n        manager.releaseOnline(PLAYER_ID);\n        FreezeNoticeService.deliverIfRestricted(manager, record, "Moderator", player);\n\n        assertEquals(beforeRelease, messages.size());\n    }\n\n    private static FreezeManager manager() {\n        return new FreezeManager(\n                null, Clock.systemUTC(), () -> null, null,\n                (playerId, operation, unavailable) -> {\n                },\n                Runnable::run, Logger.getLogger(FreezeNoticeServiceTest.class.getName()), null\n        );\n    }\n\n    private static FreezeRecord record() {\n        return new FreezeRecord(\n                PLAYER_ID,\n                UUID.fromString("91000000-0000-0000-0000-000000000002"),\n                "verification",\n                Instant.parse("2026-09-23T12:00:00Z"),\n                Optional.empty(),\n                false,\n                1L\n        );\n    }\n\n    private static Player player(List<Component> messages) {\n        return proxy(Player.class, (method, arguments) -> {\n            if (method.getName().equals("sendMessage")\n                    && arguments != null && arguments.length > 0 && arguments[0] instanceof Component component) {\n                messages.add(component);\n            }\n            return defaultValue(method.getReturnType());\n        });\n    }\n\n    @SuppressWarnings("unchecked")\n    private static <T> T proxy(Class<T> type, Invocation invocation) {\n        return (T) Proxy.newProxyInstance(\n                Thread.currentThread().getContextClassLoader(),\n                new Class<?>[]{type},\n                (instance, method, arguments) -> invocation.invoke(method, arguments)\n        );\n    }\n\n    private static Object defaultValue(Class<?> type) {\n        if (!type.isPrimitive() || type == void.class) {\n            return null;\n        }\n        return Array.get(Array.newInstance(type, 1), 0);\n    }\n\n    @FunctionalInterface\n    private interface Invocation {\n        Object invoke(java.lang.reflect.Method method, Object[] arguments);\n    }\n}\n'''
)
write(
    "paper/src/test/java/net/enthusia/staff/paper/freeze/FreezeSchedulerBoundaryTest.java",
    '''package net.enthusia.staff.paper.freeze;\n\nimport static org.junit.jupiter.api.Assertions.assertFalse;\nimport static org.junit.jupiter.api.Assertions.assertTrue;\n\nimport io.papermc.paper.threadedregions.scheduler.EntityScheduler;\nimport java.lang.reflect.Method;\nimport java.lang.reflect.Proxy;\nimport java.util.concurrent.atomic.AtomicBoolean;\nimport org.bukkit.entity.Player;\nimport org.bukkit.plugin.Plugin;\nimport org.junit.jupiter.api.Test;\n\nclass FreezeSchedulerBoundaryTest {\n    @Test\n    void recipientOperationRunsOnlyInsideEntityOwnedCallback() {\n        AtomicBoolean executed = new AtomicBoolean();\n        Runnable[] owned = new Runnable[1];\n        EntityScheduler scheduler = scheduler((plugin, action, retired, delay) -> {\n            owned[0] = action;\n            return true;\n        });\n\n        boolean scheduled = FreezeManager.scheduleRecipient(plugin(), player(scheduler), () -> executed.set(true));\n\n        assertTrue(scheduled);\n        assertFalse(executed.get());\n        owned[0].run();\n        assertTrue(executed.get());\n    }\n\n    @Test\n    void retiredRecipientRejectsWithoutRunningPlayerOperation() {\n        AtomicBoolean executed = new AtomicBoolean();\n        EntityScheduler scheduler = scheduler((plugin, action, retired, delay) -> false);\n\n        boolean scheduled = FreezeManager.scheduleRecipient(plugin(), player(scheduler), () -> executed.set(true));\n\n        assertFalse(scheduled);\n        assertFalse(executed.get());\n    }\n\n    private static Plugin plugin() {\n        return proxy(Plugin.class, (method, arguments) -> unexpected(method));\n    }\n\n    private static Player player(EntityScheduler scheduler) {\n        return proxy(Player.class, (method, arguments) -> {\n            if (method.getName().equals("getScheduler")) {\n                return scheduler;\n            }\n            return unexpected(method);\n        });\n    }\n\n    private static EntityScheduler scheduler(SchedulerExecution execution) {\n        return proxy(EntityScheduler.class, (method, arguments) -> {\n            if (method.getName().equals("execute")) {\n                return execution.execute(\n                        (Plugin) arguments[0], (Runnable) arguments[1],\n                        (Runnable) arguments[2], (Long) arguments[3]\n                );\n            }\n            return unexpected(method);\n        });\n    }\n\n    private static <T> T proxy(Class<T> type, Invocation invocation) {\n        return type.cast(Proxy.newProxyInstance(\n                Thread.currentThread().getContextClassLoader(), new Class<?>[]{type},\n                (instance, method, arguments) -> invocation.invoke(method, arguments == null ? new Object[0] : arguments)\n        ));\n    }\n\n    private static Object unexpected(Method method) {\n        throw new AssertionError("Unexpected call: " + method.getName());\n    }\n\n    @FunctionalInterface\n    private interface Invocation {\n        Object invoke(Method method, Object[] arguments) throws Throwable;\n    }\n\n    @FunctionalInterface\n    private interface SchedulerExecution {\n        boolean execute(Plugin plugin, Runnable action, Runnable retired, long delay);\n    }\n}\n'''
)
