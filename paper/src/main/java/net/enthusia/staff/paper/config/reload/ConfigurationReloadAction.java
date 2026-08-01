package net.enthusia.staff.paper.config.reload;

@FunctionalInterface
public interface ConfigurationReloadAction {
    ConfigurationReloadResult reload();
}
