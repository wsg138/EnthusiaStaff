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


# Carry the exact freeze generation through queued notice delivery. A boolean
# "currently frozen" check cannot distinguish an old freeze from a later re-freeze.
write(
    "paper/src/main/java/net/enthusia/staff/paper/freeze/FreezeNoticeSink.java",
    '''package net.enthusia.staff.paper.freeze;\n\nimport net.enthusia.staff.domain.freeze.FreezeRecord;\n\n@FunctionalInterface\npublic interface FreezeNoticeSink {\n    void show(FreezeRecord record, String actorName, long generation);\n\n    static FreezeNoticeSink noOp() {\n        return (record, actorName, generation) -> {\n        };\n    }\n}\n'''
)

replace(
    "paper/src/main/java/net/enthusia/staff/paper/freeze/FreezeManager.java",
    "import net.enthusia.staff.domain.ports.FreezeStore;",
    "import net.enthusia.staff.domain.freeze.FreezeRecord;\nimport net.enthusia.staff.domain.ports.FreezeStore;"
)
replace(
    "paper/src/main/java/net/enthusia/staff/paper/freeze/FreezeManager.java",
    '''    private final Consumer<String> staffAlertSink;\n    private final FreezeRuntimeState runtimeState = new FreezeRuntimeState();''',
    '''    private final Consumer<String> staffAlertSink;\n    private final FreezeRuntimeState runtimeState = new FreezeRuntimeState();\n    private volatile FreezeNoticeSink noticeSink = FreezeNoticeSink.noOp();'''
)
replace(
    "paper/src/main/java/net/enthusia/staff/paper/freeze/FreezeManager.java",
    '''    public boolean isRestricted(UUID playerId) {\n        return runtimeState.isRestricted(playerId);\n    }\n\n    public void applyOnline(UUID playerId) {\n        long generation = runtimeState.apply(playerId);\n        onEntity(playerId, player -> {\n            if (!runtimeState.isCurrentFrozen(playerId, generation)) {\n                return;\n            }\n            securePlayer(player);\n        }, () -> runtimeState.retireIfCurrent(playerId, generation));\n    }''',
    '''    public boolean isRestricted(UUID playerId) {\n        return runtimeState.isRestricted(playerId);\n    }\n\n    public boolean isCurrentFrozen(UUID playerId, long generation) {\n        return runtimeState.isCurrentFrozen(playerId, generation);\n    }\n\n    public void setNoticeSink(FreezeNoticeSink noticeSink) {\n        this.noticeSink = java.util.Objects.requireNonNull(noticeSink, "noticeSink");\n    }\n\n    public long applyOnline(UUID playerId) {\n        long generation = runtimeState.apply(playerId);\n        onEntity(playerId, player -> {\n            if (!runtimeState.isCurrentFrozen(playerId, generation)) {\n                return;\n            }\n            securePlayer(player);\n        }, () -> runtimeState.retireIfCurrent(playerId, generation));\n        return generation;\n    }'''
)
replace(
    "paper/src/main/java/net/enthusia/staff/paper/freeze/FreezeManager.java",
    '''            boolean active = loaded.active(playerId, clock.instant()).isPresent();\n            if (!runtimeState.resolveVerification(playerId, verificationToken, active) || !active) {\n                return;\n            }\n            onEntity(playerId, player -> {''',
    '''            FreezeRecord record = loaded.active(playerId, clock.instant()).orElse(null);\n            boolean active = record != null;\n            if (!runtimeState.resolveVerification(playerId, verificationToken, active) || !active) {\n                return;\n            }\n            noticeSink.show(record, null, verificationToken);\n            onEntity(playerId, player -> {'''
)

# The notice service no longer performs a second join-time freeze lookup. FreezeManager
# owns authoritative verification and hands this service the record plus exact generation.
write(
    "paper/src/main/java/net/enthusia/staff/paper/freeze/FreezeNoticeService.java",
    '''package net.enthusia.staff.paper.freeze;\n\nimport java.util.UUID;\nimport java.util.concurrent.ExecutorService;\nimport java.util.concurrent.RejectedExecutionException;\nimport java.util.function.Supplier;\nimport java.util.logging.Level;\nimport net.enthusia.staff.domain.freeze.FreezeRecord;\nimport net.enthusia.staff.domain.player.PlayerIdentity;\nimport net.enthusia.staff.domain.ports.PlayerDirectory;\nimport org.bukkit.entity.Player;\nimport org.bukkit.plugin.java.JavaPlugin;\n\npublic final class FreezeNoticeService implements FreezeNoticeSink {\n    private final JavaPlugin plugin;\n    private final Supplier<PlayerDirectory> players;\n    private final ExecutorService workers;\n    private final FreezeManager manager;\n\n    public FreezeNoticeService(\n            JavaPlugin plugin,\n            Supplier<PlayerDirectory> players,\n            ExecutorService workers,\n            FreezeManager manager\n    ) {\n        this.plugin = java.util.Objects.requireNonNull(plugin, "plugin");\n        this.players = java.util.Objects.requireNonNull(players, "players");\n        this.workers = java.util.Objects.requireNonNull(workers, "workers");\n        this.manager = java.util.Objects.requireNonNull(manager, "manager");\n    }\n\n    @Override\n    public void show(FreezeRecord record, String actorName, long generation) {\n        if (record == null) {\n            return;\n        }\n        String supplied = actorName == null ? "" : actorName.trim();\n        if (!supplied.isEmpty()) {\n            schedule(record, supplied, generation);\n            return;\n        }\n        submit(() -> schedule(record, actorName(record.frozenBy()), generation));\n    }\n\n    private void schedule(FreezeRecord record, String actorName, long generation) {\n        onEntity(record.playerId(), player -> deliverIfCurrent(manager, record, actorName, generation, player));\n    }\n\n    static void deliverIfCurrent(\n            FreezeManager manager,\n            FreezeRecord record,\n            String actorName,\n            long generation,\n            Player player\n    ) {\n        if (!manager.isCurrentFrozen(record.playerId(), generation)) {\n            return;\n        }\n        FreezeNoticePresentation.render(record, actorName).forEach(player::sendMessage);\n    }\n\n    private String actorName(UUID actorId) {\n        PlayerDirectory directory = players.get();\n        if (directory == null) {\n            return actorId.toString();\n        }\n        try {\n            PlayerIdentity actor = directory.find(actorId.toString()).orElse(null);\n            return actor == null ? actorId.toString() : actor.currentUsername().orElse(actorId.toString());\n        } catch (RuntimeException exception) {\n            plugin.getLogger().log(Level.FINE, "Freeze actor name lookup failed", exception);\n            return actorId.toString();\n        }\n    }\n\n    private void submit(Runnable operation) {\n        try {\n            workers.execute(operation);\n        } catch (RejectedExecutionException exception) {\n            plugin.getLogger().warning("Freeze notice lookup skipped because the bounded queue is full");\n        }\n    }\n\n    private void onEntity(UUID playerId, java.util.function.Consumer<Player> operation) {\n        plugin.getServer().getGlobalRegionScheduler().execute(plugin, () -> {\n            Player player = plugin.getServer().getPlayer(playerId);\n            if (player != null) {\n                player.getScheduler().execute(plugin, () -> operation.accept(player), null, 1L);\n            }\n        });\n    }\n}\n'''
)

replace(
    "paper/src/main/java/net/enthusia/staff/paper/PaperRuntimeComponents.java",
    '''        FreezeNoticeService notices = new FreezeNoticeService(\n                plugin, dependencies.environment().clock(), dependencies.stores().freezeStore(),\n                dependencies.stores().playerDirectory(), dependencies.environment().workers(), freeze\n        );\n        registerListener(plugin, notices);\n        return notices;''',
    '''        FreezeNoticeService notices = new FreezeNoticeService(\n                plugin, dependencies.stores().playerDirectory(), dependencies.environment().workers(), freeze\n        );\n        freeze.setNoticeSink(notices);\n        return notices;'''
)

replace(
    "paper/src/main/java/net/enthusia/staff/paper/command/FreezeCommand.java",
    '''        FreezeRecord record = store.apply(target.playerId(), actor.id(), reason, clock.instant());\n        manager.applyOnline(target.playerId());\n        targetNotices.show(record, actor.displayName());''',
    '''        FreezeRecord record = store.apply(target.playerId(), actor.id(), reason, clock.instant());\n        long generation = manager.applyOnline(target.playerId());\n        targetNotices.show(record, actor.displayName(), generation);'''
)

# Prove the important race: an old queued notice must remain stale even if the player
# has already been frozen again by a newer generation.
write(
    "paper/src/test/java/net/enthusia/staff/paper/freeze/FreezeNoticeServiceTest.java",
    '''package net.enthusia.staff.paper.freeze;\n\nimport static org.junit.jupiter.api.Assertions.assertFalse;\nimport static org.junit.jupiter.api.Assertions.assertTrue;\n\nimport java.lang.reflect.Array;\nimport java.lang.reflect.Proxy;\nimport java.time.Clock;\nimport java.time.Instant;\nimport java.util.ArrayList;\nimport java.util.List;\nimport java.util.Optional;\nimport java.util.UUID;\nimport java.util.logging.Logger;\nimport net.enthusia.staff.domain.freeze.FreezeRecord;\nimport net.kyori.adventure.text.Component;\nimport org.bukkit.entity.Player;\nimport org.junit.jupiter.api.Test;\n\nclass FreezeNoticeServiceTest {\n    private static final UUID PLAYER_ID = UUID.fromString("91000000-0000-0000-0000-000000000001");\n\n    @Test\n    void releasedThenRefrozenPlayerRejectsNoticeFromOlderGeneration() {\n        FreezeManager manager = manager();\n        List<Component> messages = new ArrayList<>();\n        Player player = player(messages);\n        FreezeRecord record = record();\n\n        long staleGeneration = manager.applyOnline(PLAYER_ID);\n        manager.releaseOnline(PLAYER_ID);\n        long currentGeneration = manager.applyOnline(PLAYER_ID);\n\n        FreezeNoticeService.deliverIfCurrent(\n                manager, record, "OldModerator", staleGeneration, player\n        );\n        assertTrue(messages.isEmpty());\n\n        FreezeNoticeService.deliverIfCurrent(\n                manager, record, "CurrentModerator", currentGeneration, player\n        );\n        assertFalse(messages.isEmpty());\n    }\n\n    private static FreezeManager manager() {\n        return new FreezeManager(\n                null, Clock.systemUTC(), () -> null, null,\n                (playerId, operation, unavailable) -> {\n                },\n                Runnable::run, Logger.getLogger(FreezeNoticeServiceTest.class.getName()), null\n        );\n    }\n\n    private static FreezeRecord record() {\n        return new FreezeRecord(\n                PLAYER_ID,\n                UUID.fromString("91000000-0000-0000-0000-000000000002"),\n                "verification",\n                Instant.parse("2026-09-23T12:00:00Z"),\n                Optional.empty(),\n                false,\n                1L\n        );\n    }\n\n    private static Player player(List<Component> messages) {\n        return proxy(Player.class, (method, arguments) -> {\n            if (method.getName().equals("sendMessage")\n                    && arguments != null && arguments.length > 0 && arguments[0] instanceof Component component) {\n                messages.add(component);\n            }\n            return defaultValue(method.getReturnType());\n        });\n    }\n\n    @SuppressWarnings("unchecked")\n    private static <T> T proxy(Class<T> type, Invocation invocation) {\n        return (T) Proxy.newProxyInstance(\n                Thread.currentThread().getContextClassLoader(),\n                new Class<?>[]{type},\n                (instance, method, arguments) -> invocation.invoke(method, arguments)\n        );\n    }\n\n    private static Object defaultValue(Class<?> type) {\n        if (!type.isPrimitive() || type == void.class) {\n            return null;\n        }\n        return Array.get(Array.newInstance(type, 1), 0);\n    }\n\n    @FunctionalInterface\n    private interface Invocation {\n        Object invoke(java.lang.reflect.Method method, Object[] arguments);\n    }\n}\n'''
)
