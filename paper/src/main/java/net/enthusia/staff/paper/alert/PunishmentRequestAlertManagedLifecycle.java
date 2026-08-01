package net.enthusia.staff.paper.alert;

public interface PunishmentRequestAlertManagedLifecycle extends AutoCloseable {
    boolean start();

    boolean active();

    @Override
    void close();
}
