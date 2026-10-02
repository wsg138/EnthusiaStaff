package net.enthusia.staff.paper.commandbridge;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import net.enthusia.staff.domain.ports.CommandBridgeExecutor;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.ComponentLike;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.plugin.java.JavaPlugin;

/** Schedules exactly one console dispatch on Paper's global region scheduler and captures bounded sender output. */
public final class BukkitConsoleCommandBridgeExecutor implements CommandBridgeExecutor {
    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(5);
    private static final int MAX_CAPTURE_CHARACTERS = 8_192;

    private final Scheduler scheduler;
    private final Dispatcher dispatcher;
    private final Duration timeout;

    public BukkitConsoleCommandBridgeExecutor(JavaPlugin plugin) {
        this(
                operation -> plugin.getServer().getGlobalRegionScheduler().execute(plugin, operation),
                command -> dispatch(plugin, command),
                DEFAULT_TIMEOUT
        );
    }

    BukkitConsoleCommandBridgeExecutor(Scheduler scheduler, Dispatcher dispatcher, Duration timeout) {
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.dispatcher = Objects.requireNonNull(dispatcher, "dispatcher");
        if (timeout == null || timeout.isNegative() || timeout.isZero()) {
            throw new IllegalArgumentException("command bridge execution timeout must be positive");
        }
        this.timeout = timeout;
    }

    @Override
    public Execution execute(String normalizedCommand) {
        if (normalizedCommand == null || normalizedCommand.isBlank()) {
            throw new IllegalArgumentException("normalized command is required");
        }
        CompletableFuture<Execution> result = new CompletableFuture<>();
        scheduler.schedule(() -> dispatchOnce(normalizedCommand, result));
        try {
            return result.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException failure) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("command execution wait interrupted", failure);
        } catch (ExecutionException | TimeoutException failure) {
            throw new IllegalStateException("command execution outcome is uncertain", failure);
        }
    }

    private void dispatchOnce(String command, CompletableFuture<Execution> result) {
        try {
            result.complete(dispatcher.dispatch(command));
        } catch (RuntimeException failure) {
            result.completeExceptionally(failure);
        }
    }

    private static Execution dispatch(JavaPlugin plugin, String command) {
        ConsoleCommandSender delegate = plugin.getServer().getConsoleSender();
        OutputCapture capture = new OutputCapture(delegate);
        boolean accepted = plugin.getServer().dispatchCommand(capture.sender(), command);
        return new Execution(accepted, capture.text());
    }

    @FunctionalInterface
    interface Scheduler {
        void schedule(Runnable operation);
    }

    @FunctionalInterface
    interface Dispatcher {
        Execution dispatch(String command);
    }

    private static final class OutputCapture {
        private final ConsoleCommandSender delegate;
        private final StringBuilder output = new StringBuilder();
        private final PlainTextComponentSerializer plainText = PlainTextComponentSerializer.plainText();

        private OutputCapture(ConsoleCommandSender delegate) {
            this.delegate = Objects.requireNonNull(delegate, "delegate");
        }

        private ConsoleCommandSender sender() {
            return (ConsoleCommandSender) Proxy.newProxyInstance(
                    ConsoleCommandSender.class.getClassLoader(),
                    new Class<?>[] {ConsoleCommandSender.class},
                    this::invoke
            );
        }

        private Object invoke(Object proxy, Method method, Object[] arguments) throws Throwable {
            if (captures(method)) {
                capture(arguments);
                return null;
            }
            try {
                return method.invoke(delegate, arguments);
            } catch (InvocationTargetException failure) {
                throw failure.getCause();
            }
        }

        private static boolean captures(Method method) {
            String name = method.getName();
            return method.getReturnType() == Void.TYPE
                    && ("sendMessage".equals(name) || "sendPlainMessage".equals(name) || "sendRichMessage".equals(name));
        }

        private void capture(Object[] arguments) {
            if (arguments == null) {
                return;
            }
            for (Object argument : arguments) {
                if (argument instanceof String text) {
                    append(text);
                } else if (argument instanceof String[] lines) {
                    for (String line : lines) {
                        append(line);
                    }
                } else if (argument instanceof Component component) {
                    append(plainText.serialize(component));
                } else if (argument instanceof ComponentLike componentLike) {
                    append(plainText.serialize(componentLike.asComponent()));
                }
            }
        }

        private void append(String value) {
            if (value == null || output.length() >= MAX_CAPTURE_CHARACTERS) {
                return;
            }
            if (!output.isEmpty()) {
                output.append('\n');
            }
            int remaining = MAX_CAPTURE_CHARACTERS - output.length();
            output.append(value, 0, Math.min(value.length(), remaining));
        }

        private String text() {
            return output.toString();
        }
    }
}
