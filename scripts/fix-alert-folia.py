from pathlib import Path


def replace_once(text: str, old: str, new: str, label: str) -> str:
    count = text.count(old)
    if count != 1:
        raise RuntimeError(f"{label}: expected one match, found {count}")
    return text.replace(old, new, 1)


# Runtime contract: retain the old global executor as a test-compatible default,
# but allow production to schedule final authorization/presentation on the
# recipient's owning entity scheduler.
path = Path("paper/src/main/java/net/enthusia/staff/paper/alert/PunishmentRequestAlertRuntime.java")
text = path.read_text(encoding="utf-8")
text = replace_once(
    text,
    "    List<PunishmentRequestAlertRecipient> onlineRecipients(int limit);\n\n"
    "    Optional<PunishmentRequestAlertRecipient> currentRecipient(UUID playerId);\n",
    "    List<PunishmentRequestAlertRecipient> onlineRecipients(int limit);\n\n"
    "    default Optional<PunishmentRequestAlertRecipient> snapshotRecipient(UUID playerId) {\n"
    "        return currentRecipient(playerId);\n"
    "    }\n\n"
    "    Optional<PunishmentRequestAlertRecipient> currentRecipient(UUID playerId);\n",
    "runtime cached snapshot contract",
)
text = replace_once(
    text,
    "    void executeSynchronously(Runnable action);\n\n"
    "    Logger logger();\n",
    "    default boolean executeForRecipient(\n"
    "            UUID playerId,\n"
    "            Runnable action,\n"
    "            Runnable retired\n"
    "    ) {\n"
    "        executeSynchronously(action);\n"
    "        return true;\n"
    "    }\n\n"
    "    void executeSynchronously(Runnable action);\n\n"
    "    Logger logger();\n",
    "runtime entity executor contract",
)
path.write_text(text, encoding="utf-8")


# Bukkit/Folia adapter: global polling only reads immutable cached snapshots.
# All live Player reads and message delivery are executed by EntityScheduler.
path = Path("paper/src/main/java/net/enthusia/staff/paper/alert/BukkitPunishmentRequestAlertRuntime.java")
text = path.read_text(encoding="utf-8")
text = replace_once(
    text,
    "import java.time.Duration;\n"
    "import java.util.List;\n"
    "import java.util.Optional;\n"
    "import java.util.UUID;\n"
    "import java.util.concurrent.TimeUnit;\n",
    "import java.time.Duration;\n"
    "import java.util.ArrayList;\n"
    "import java.util.List;\n"
    "import java.util.Map;\n"
    "import java.util.Optional;\n"
    "import java.util.UUID;\n"
    "import java.util.concurrent.ConcurrentHashMap;\n"
    "import java.util.concurrent.TimeUnit;\n",
    "bukkit runtime imports",
)
text = replace_once(
    text,
    "import org.bukkit.event.player.PlayerJoinEvent;\n",
    "import org.bukkit.event.player.PlayerJoinEvent;\n"
    "import org.bukkit.event.player.PlayerQuitEvent;\n",
    "bukkit quit import",
)
text = replace_once(
    text,
    "final class BukkitPunishmentRequestAlertRuntime implements PunishmentRequestAlertRuntime {\n"
    "    private final JavaPlugin plugin;\n",
    "final class BukkitPunishmentRequestAlertRuntime implements PunishmentRequestAlertRuntime {\n"
    "    private final JavaPlugin plugin;\n"
    "    private final Map<UUID, PunishmentRequestAlertRecipient> recipientSnapshots =\n"
    "            new ConcurrentHashMap<>();\n",
    "bukkit recipient cache",
)
old_online = '''    @Override
    public List<PunishmentRequestAlertRecipient> onlineRecipients(int limit) {
        if (limit < 1) {
            throw new IllegalArgumentException("recipient limit must be positive");
        }
        return plugin.getServer().getOnlinePlayers().stream()
                .limit(limit)
                .map(this::snapshot)
                .toList();
    }
'''
new_online = '''    @Override
    public List<PunishmentRequestAlertRecipient> onlineRecipients(int limit) {
        if (limit < 1) {
            throw new IllegalArgumentException("recipient limit must be positive");
        }
        List<Player> online = plugin.getServer().getOnlinePlayers().stream()
                .limit(limit)
                .toList();
        List<PunishmentRequestAlertRecipient> cached = new ArrayList<>(online.size());
        for (Player player : online) {
            UUID playerId = player.getUniqueId();
            refreshSnapshot(player, playerId);
            PunishmentRequestAlertRecipient snapshot = recipientSnapshots.get(playerId);
            if (snapshot != null) {
                cached.add(snapshot);
            }
        }
        return List.copyOf(cached);
    }

    @Override
    public Optional<PunishmentRequestAlertRecipient> snapshotRecipient(UUID playerId) {
        return Optional.ofNullable(recipientSnapshots.get(playerId));
    }
'''
text = replace_once(text, old_online, new_online, "cached online recipients")
old_register = '''    @Override
    public AutoCloseable registerJoinListener(Consumer<UUID> listener) {
        JoinListener registered = new JoinListener(listener);
        plugin.getServer().getPluginManager().registerEvents(registered, plugin);
        return () -> HandlerList.unregisterAll(registered);
    }
'''
new_register = '''    @Override
    public AutoCloseable registerJoinListener(Consumer<UUID> listener) {
        JoinListener registered = new JoinListener(
                player -> {
                    UUID playerId = player.getUniqueId();
                    recipientSnapshots.put(playerId, snapshot(player));
                    listener.accept(playerId);
                },
                recipientSnapshots::remove
        );
        plugin.getServer().getPluginManager().registerEvents(registered, plugin);
        return () -> HandlerList.unregisterAll(registered);
    }
'''
text = replace_once(text, old_register, new_register, "join and quit snapshot listener")
text = replace_once(
    text,
    "    @Override\n"
    "    public void executeSynchronously(Runnable action) {\n",
    "    @Override\n"
    "    public boolean executeForRecipient(\n"
    "            UUID playerId,\n"
    "            Runnable action,\n"
    "            Runnable retired\n"
    "    ) {\n"
    "        Player player = plugin.getServer().getPlayer(playerId);\n"
    "        if (player == null || !player.isOnline()) {\n"
    "            recipientSnapshots.remove(playerId);\n"
    "            return false;\n"
    "        }\n"
    "        return player.getScheduler().execute(plugin, action, retired, 1L);\n"
    "    }\n\n"
    "    @Override\n"
    "    public void executeSynchronously(Runnable action) {\n",
    "entity-owned execution",
)
text = replace_once(
    text,
    "    private PunishmentRequestAlertRecipient snapshot(Player player) {\n",
    "    private void refreshSnapshot(Player player, UUID playerId) {\n"
    "        boolean scheduled = player.getScheduler().execute(\n"
    "                plugin,\n"
    "                () -> {\n"
    "                    if (player.isOnline()) {\n"
    "                        recipientSnapshots.put(playerId, snapshot(player));\n"
    "                    } else {\n"
    "                        recipientSnapshots.remove(playerId);\n"
    "                    }\n"
    "                },\n"
    "                () -> recipientSnapshots.remove(playerId),\n"
    "                1L\n"
    "        );\n"
    "        if (!scheduled) {\n"
    "            recipientSnapshots.remove(playerId);\n"
    "        }\n"
    "    }\n\n"
    "    private PunishmentRequestAlertRecipient snapshot(Player player) {\n",
    "entity snapshot refresh",
)
old_listener = '''    private static final class JoinListener implements Listener {
        private final Consumer<UUID> listener;

        private JoinListener(Consumer<UUID> listener) {
            if (listener == null) {
                throw new IllegalArgumentException("join listener must be present");
            }
            this.listener = listener;
        }

        @EventHandler
        public void onJoin(PlayerJoinEvent event) {
            listener.accept(event.getPlayer().getUniqueId());
        }
    }
'''
new_listener = '''    private static final class JoinListener implements Listener {
        private final Consumer<Player> joined;
        private final Consumer<UUID> quit;

        private JoinListener(Consumer<Player> joined, Consumer<UUID> quit) {
            if (joined == null || quit == null) {
                throw new IllegalArgumentException("join and quit listeners must be present");
            }
            this.joined = joined;
            this.quit = quit;
        }

        @EventHandler
        public void onJoin(PlayerJoinEvent event) {
            joined.accept(event.getPlayer());
        }

        @EventHandler
        public void onQuit(PlayerQuitEvent event) {
            quit.accept(event.getPlayer().getUniqueId());
        }
    }
'''
text = replace_once(text, old_listener, new_listener, "join listener ownership cache")
path.write_text(text, encoding="utf-8")


# Worker handoff: final lookup, authorization, and presentation are dispatched to
# the recipient owner. Scheduler rejection/retirement resolves the lease once.
path = Path("paper/src/main/java/net/enthusia/staff/paper/alert/PunishmentRequestAlertWorker.java")
text = path.read_text(encoding="utf-8")
text = replace_once(
    text,
    "    private final Executor asynchronous;\n"
    "    private final Consumer<Runnable> synchronous;\n",
    "    private final Executor asynchronous;\n"
    "    private final RecipientExecutor synchronous;\n",
    "worker recipient executor field",
)
old_constructor = '''    public PunishmentRequestAlertWorker(
            Clock clock,
            String owner,
            PunishmentRequestAlertWorkerSettings settings,
            PunishmentRequestAlertStore alerts,
            PunishmentRequestStore requests,
            PlayerDirectory players,
            PunishmentRequestAlertRenderer renderer,
            PunishmentRequestAlertPresenter presenter,
            Executor asynchronous,
            Consumer<Runnable> synchronous,
            BooleanSupplier stopping,
            Logger logger
    ) {
        this.clock = Objects.requireNonNull(clock, "clock");
        if (owner == null || owner.isBlank() || owner.length() > 128) {
            throw new IllegalArgumentException("alert lease owner must be present and at most 128 characters");
        }
        this.owner = owner;
        this.settings = Objects.requireNonNull(settings, "settings");
        this.alerts = Objects.requireNonNull(alerts, "alerts");
        this.requests = Objects.requireNonNull(requests, "requests");
        this.players = Objects.requireNonNull(players, "players");
        this.renderer = Objects.requireNonNull(renderer, "renderer");
        this.presenter = Objects.requireNonNull(presenter, "presenter");
        this.recipientPolicy = new PunishmentRequestRecipientPolicy();
        this.asynchronous = Objects.requireNonNull(asynchronous, "asynchronous");
        this.synchronous = Objects.requireNonNull(synchronous, "synchronous");
        this.stopping = Objects.requireNonNull(stopping, "stopping");
        this.logger = Objects.requireNonNull(logger, "logger");
    }
'''
new_constructor = '''    public PunishmentRequestAlertWorker(
            Clock clock,
            String owner,
            PunishmentRequestAlertWorkerSettings settings,
            PunishmentRequestAlertStore alerts,
            PunishmentRequestStore requests,
            PlayerDirectory players,
            PunishmentRequestAlertRenderer renderer,
            PunishmentRequestAlertPresenter presenter,
            Executor asynchronous,
            Consumer<Runnable> synchronous,
            BooleanSupplier stopping,
            Logger logger
    ) {
        this(
                clock,
                owner,
                settings,
                alerts,
                requests,
                players,
                renderer,
                presenter,
                asynchronous,
                globalExecutor(synchronous),
                stopping,
                logger
        );
    }

    PunishmentRequestAlertWorker(
            Clock clock,
            String owner,
            PunishmentRequestAlertWorkerSettings settings,
            PunishmentRequestAlertStore alerts,
            PunishmentRequestStore requests,
            PlayerDirectory players,
            PunishmentRequestAlertRenderer renderer,
            PunishmentRequestAlertPresenter presenter,
            Executor asynchronous,
            RecipientExecutor synchronous,
            BooleanSupplier stopping,
            Logger logger
    ) {
        this.clock = Objects.requireNonNull(clock, "clock");
        if (owner == null || owner.isBlank() || owner.length() > 128) {
            throw new IllegalArgumentException("alert lease owner must be present and at most 128 characters");
        }
        this.owner = owner;
        this.settings = Objects.requireNonNull(settings, "settings");
        this.alerts = Objects.requireNonNull(alerts, "alerts");
        this.requests = Objects.requireNonNull(requests, "requests");
        this.players = Objects.requireNonNull(players, "players");
        this.renderer = Objects.requireNonNull(renderer, "renderer");
        this.presenter = Objects.requireNonNull(presenter, "presenter");
        this.recipientPolicy = new PunishmentRequestRecipientPolicy();
        this.asynchronous = Objects.requireNonNull(asynchronous, "asynchronous");
        this.synchronous = Objects.requireNonNull(synchronous, "synchronous");
        this.stopping = Objects.requireNonNull(stopping, "stopping");
        this.logger = Objects.requireNonNull(logger, "logger");
    }

    private static RecipientExecutor globalExecutor(Consumer<Runnable> synchronous) {
        Objects.requireNonNull(synchronous, "synchronous");
        return (ignored, action, retired) -> {
            synchronous.accept(action);
            return true;
        };
    }
'''
text = replace_once(text, old_constructor, new_constructor, "worker constructor overload")
old_handoff = '''        try {
            synchronous.accept(() -> present(snapshot.playerId(), presentations, completion));
        } catch (RuntimeException exception) {
            logger.log(Level.WARNING, "Punishment request alert synchronous handoff failed", exception);
            completion.run();
        }
    }
'''
new_handoff = '''        handoff(snapshot.playerId(), presentations, completion);
    }

    private void handoff(
            UUID recipientId,
            List<PunishmentRequestAlertPresentation> presentations,
            Completion completion
    ) {
        AtomicBoolean resolved = new AtomicBoolean();
        Runnable retired = () -> retryHandoff(
                resolved,
                presentations,
                PLAYER_OFFLINE,
                completion
        );
        try {
            boolean scheduled = synchronous.execute(
                    recipientId,
                    () -> {
                        if (resolved.compareAndSet(false, true)) {
                            present(recipientId, presentations, completion);
                        }
                    },
                    retired
            );
            if (!scheduled) {
                retired.run();
            }
        } catch (RuntimeException exception) {
            logger.log(Level.WARNING, "Punishment request alert recipient handoff failed", exception);
            retryHandoff(resolved, presentations, PRESENTATION_UNAVAILABLE, completion);
        }
    }

    private void retryHandoff(
            AtomicBoolean resolved,
            List<PunishmentRequestAlertPresentation> presentations,
            String code,
            Completion completion
    ) {
        if (!resolved.compareAndSet(false, true)) {
            return;
        }
        queueOutcomes(
                presentations.stream()
                        .map(value -> Outcome.retry(value.claim(), code))
                        .toList(),
                completion
        );
    }
'''
text = replace_once(text, old_handoff, new_handoff, "worker entity handoff")
text = replace_once(
    text,
    "    public static final class ClaimBudget {\n",
    "    @FunctionalInterface\n"
    "    interface RecipientExecutor {\n"
    "        boolean execute(UUID playerId, Runnable action, Runnable retired);\n"
    "    }\n\n"
    "    public static final class ClaimBudget {\n",
    "worker recipient executor interface",
)
path.write_text(text, encoding="utf-8")


# Lifecycle uses cached reconnect snapshots for claims and owning-entity handoff
# for the final fresh authorization/presentation pass.
path = Path("paper/src/main/java/net/enthusia/staff/paper/alert/PunishmentRequestAlertLifecycle.java")
text = path.read_text(encoding="utf-8")
text = replace_once(
    text,
    "                runtime::executeSynchronously,\n",
    "                runtime::executeForRecipient,\n",
    "lifecycle entity handoff",
)
text = replace_once(
    text,
    "            recipient = runtime.currentRecipient(playerId);\n",
    "            recipient = runtime.snapshotRecipient(playerId);\n",
    "lifecycle cached reconnect snapshot",
)
path.write_text(text, encoding="utf-8")


# Focused worker tests cover both EntityScheduler rejection and retirement.
path = Path("paper/src/test/java/net/enthusia/staff/paper/alert/PunishmentRequestAlertWorkerTest.java")
text = path.read_text(encoding="utf-8")
anchor = '''    @Test
    void authorizationLossCancelsBeforePresentation() {
'''
new_tests = '''    @Test
    void rejectedRecipientSchedulerRetriesAsOfflineExactlyOnce() {
        Harness harness = Harness.direct((playerId, action, retired) -> false);

        harness.runComplete();

        assertEquals(0, harness.presenter.presented);
        assertEquals(1, harness.alerts.failed);
        assertEquals(PunishmentRequestAlertWorker.PLAYER_OFFLINE, harness.alerts.lastCode);
        assertTrue(harness.completed.get());
    }

    @Test
    void retiredRecipientSchedulerRetriesAsOfflineExactlyOnce() {
        Harness harness = Harness.direct((playerId, action, retired) -> {
            retired.run();
            return true;
        });

        harness.runComplete();

        assertEquals(0, harness.presenter.presented);
        assertEquals(1, harness.alerts.failed);
        assertEquals(PunishmentRequestAlertWorker.PLAYER_OFFLINE, harness.alerts.lastCode);
        assertTrue(harness.completed.get());
    }

'''
text = replace_once(text, anchor, new_tests + anchor, "worker scheduler tests")
text = replace_once(
    text,
    "        private Harness(PunishmentRequestAlertRecipient snapshot) {\n"
    "            this.snapshot = snapshot;\n"
    "            presenter.current = Optional.of(snapshot);\n"
    "            worker = new PunishmentRequestAlertWorker(\n",
    "        private Harness(PunishmentRequestAlertRecipient snapshot) {\n"
    "            this(snapshot, null);\n"
    "        }\n\n"
    "        private Harness(\n"
    "                PunishmentRequestAlertRecipient snapshot,\n"
    "                PunishmentRequestAlertWorker.RecipientExecutor recipientExecutor\n"
    "        ) {\n"
    "            this.snapshot = snapshot;\n"
    "            presenter.current = Optional.of(snapshot);\n"
    "            PunishmentRequestAlertWorker.RecipientExecutor selected = recipientExecutor == null\n"
    "                    ? (playerId, action, retired) -> {\n"
    "                        synchronous.execute(action);\n"
    "                        return true;\n"
    "                    }\n"
    "                    : recipientExecutor;\n"
    "            worker = new PunishmentRequestAlertWorker(\n",
    "worker harness executor constructor",
)
text = replace_once(
    text,
    "                    asynchronous,\n"
    "                    synchronous::execute,\n"
    "                    stopping::get,\n",
    "                    asynchronous,\n"
    "                    selected,\n"
    "                    stopping::get,\n",
    "worker harness selected executor",
)
old_direct = '''        static Harness direct() {
            Harness harness = new Harness(new PunishmentRequestAlertRecipient(
                    PunishmentRequestAlertTestFixtures.REQUESTER_ID,
                    "RequestingHelper",
                    StaffRank.HELPER
            ));
            harness.alerts.directClaims = List.of(PunishmentRequestAlertTestFixtures.claim(
                    PunishmentRequestLifecycleEventType.REQUEST_SUBMITTED,
                    PunishmentRequestAlertAudience.DIRECT_RECIPIENT,
                    PunishmentRequestAlertTestFixtures.REQUESTER_ID,
                    null
            ));
            return harness;
        }
'''
new_direct = '''        static Harness direct() {
            return direct(null);
        }

        static Harness direct(PunishmentRequestAlertWorker.RecipientExecutor recipientExecutor) {
            Harness harness = new Harness(new PunishmentRequestAlertRecipient(
                    PunishmentRequestAlertTestFixtures.REQUESTER_ID,
                    "RequestingHelper",
                    StaffRank.HELPER
            ), recipientExecutor);
            harness.alerts.directClaims = List.of(PunishmentRequestAlertTestFixtures.claim(
                    PunishmentRequestLifecycleEventType.REQUEST_SUBMITTED,
                    PunishmentRequestAlertAudience.DIRECT_RECIPIENT,
                    PunishmentRequestAlertTestFixtures.REQUESTER_ID,
                    null
            ));
            return harness;
        }
'''
text = replace_once(text, old_direct, new_direct, "worker direct harness overload")
path.write_text(text, encoding="utf-8")
