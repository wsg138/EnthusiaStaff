package net.enthusia.staff.paper.tester;

import java.util.UUID;
import org.bukkit.Location;
import org.bukkit.entity.Player;

interface FakeEntityAdapter extends AutoCloseable {
    record Handle(int entityId, UUID entityUuid) {
        public Handle {
            if (entityUuid == null) {
                throw new IllegalArgumentException("entityUuid must be present");
            }
        }
    }

    boolean available();

    Handle create();

    void show(Player viewer, Handle handle, Location location);

    void destroy(Player viewer, Handle handle);

    @Override
    void close();

    static FakeEntityAdapter unavailable() {
        return new FakeEntityAdapter() {
            @Override
            public boolean available() {
                return false;
            }

            @Override
            public Handle create() {
                throw new IllegalStateException("fake-entity packet support is unavailable");
            }

            @Override
            public void show(Player viewer, Handle handle, Location location) {
                throw new IllegalStateException("fake-entity packet support is unavailable");
            }

            @Override
            public void destroy(Player viewer, Handle handle) {
                // Nothing was emitted.
            }

            @Override
            public void close() {
                // No resources.
            }
        };
    }
}
