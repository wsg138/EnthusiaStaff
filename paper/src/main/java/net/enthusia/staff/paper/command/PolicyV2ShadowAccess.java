package net.enthusia.staff.paper.command;

import org.bukkit.command.CommandSender;

public interface PolicyV2ShadowAccess {
    boolean enabled();

    void open(CommandSender sender, String targetQuery);

    void reload();

    static PolicyV2ShadowAccess disabled() {
        return Disabled.INSTANCE;
    }

    enum Disabled implements PolicyV2ShadowAccess {
        INSTANCE;

        @Override
        public boolean enabled() {
            return false;
        }

        @Override
        public void open(CommandSender sender, String targetQuery) {
            // Deliberately unavailable while Policy v2 is disabled.
        }

        @Override
        public void reload() {
            // No Policy v2 publication exists.
        }
    }
}
