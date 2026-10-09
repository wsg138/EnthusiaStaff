package net.enthusia.staff.paper.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.rosewood.rosechat.api.staff.BridgeRegistration;
import dev.rosewood.rosechat.api.staff.PresenceContext;
import dev.rosewood.rosechat.api.staff.PresenceType;
import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import javax.tools.ToolProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RoseChatPresenceBinaryCompatibilityTest {
    private static final String REGISTRATION = "dev.rosewood.rosechat.api.staff.BridgeRegistration";
    private static final String RENDERER = "net.enthusia.staff.paper.integration.RoseChatPresenceRenderer";

    @Test
    void oldProviderInterfaceWithoutOptionalMethodKeepsItsBridgeActive(@TempDir Path directory) throws Exception {
        Path source = directory.resolve("BridgeRegistration.java");
        Files.writeString(source, """
                package dev.rosewood.rosechat.api.staff;
                public interface BridgeRegistration extends AutoCloseable {
                    String owner();
                    boolean isActive();
                    void close();
                }
                """);
        Path implementation = directory.resolve("OldRegistration.java");
        Files.writeString(implementation, """
                package dev.rosewood.rosechat.api.staff;
                public final class OldRegistration implements BridgeRegistration {
                    public String owner() { return "EnthusiaStaff"; }
                    public boolean isActive() { return true; }
                    public void close() { }
                }
                """);
        assertEquals(0, ToolProvider.getSystemJavaCompiler().run(null, null, null,
                "-d", directory.toString(), source.toString(), implementation.toString()));
        try (URLClassLoader loader = new URLClassLoader(new URL[]{directory.toUri().toURL()}, getClass().getClassLoader()) {
            @Override
            protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
                if (name.equals(REGISTRATION) || name.endsWith(".OldRegistration")) {
                    Class<?> type = findLoadedClass(name);
                    return type != null ? type : findClass(name);
                }
                if (name.equals(RENDERER)) {
                    Class<?> type = findLoadedClass(name);
                    if (type == null) {
                        try (var bytes = getParent().getResourceAsStream(name.replace('.', '/') + ".class")) {
                            if (bytes == null) {
                                throw new ClassNotFoundException(name);
                            }
                            byte[] content = bytes.readAllBytes();
                            type = defineClass(name, content, 0, content.length);
                        } catch (IOException exception) {
                            throw new ClassNotFoundException(name, exception);
                        }
                    }
                    return type;
                }
                return super.loadClass(name, resolve);
            }
        }) {
            Class<?> registrationType = loader.loadClass(REGISTRATION);
            Object oldRegistration = loader.loadClass("dev.rosewood.rosechat.api.staff.OldRegistration")
                    .getConstructor().newInstance();
            Class<?> rendererType = loader.loadClass(RENDERER);
            var constructor = rendererType.getDeclaredConstructor(registrationType);
            constructor.setAccessible(true);
            Object renderer = constructor.newInstance(oldRegistration);
            var render = rendererType.getDeclaredMethod("render", PresenceContext.class);
            render.setAccessible(true);
            assertEquals(false, render.invoke(renderer, context()));
            assertEquals(false, render.invoke(renderer, context()));
            assertEquals(true, registrationType.getMethod("isActive").invoke(oldRegistration));
        }
    }

    @Test
    void supportedProviderReceivesPresenceAndInactiveBridgeDoesNot() {
        BridgeRegistration active = new BridgeRegistration() {
            public String owner() { return "EnthusiaStaff"; }
            public boolean isActive() { return true; }
            public void close() { }
            public boolean renderPresence(PresenceContext context) { return true; }
        };
        assertTrue(new RoseChatPresenceRenderer(active).render(context()));
        BridgeRegistration inactive = new BridgeRegistration() {
            public String owner() { return "EnthusiaStaff"; }
            public boolean isActive() { return false; }
            public void close() { }
            public boolean renderPresence(PresenceContext context) {
                throw new AssertionError("inactive registration must not render");
            }
        };
        assertFalse(new RoseChatPresenceRenderer(inactive).render(context()));
    }

    private static PresenceContext context() {
        return new PresenceContext(UUID.randomUUID(), UUID.randomUUID(), PresenceType.QUIT);
    }
}
