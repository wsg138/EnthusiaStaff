package net.enthusia.staff.paper.visibility;

/**
 * Suppresses container {@code BLOCK_ACTION} animations for vanished staff.
 * Mirrors the {@link SpectatorTabPacketAdapter} lifecycle: a no-op implementation
 * is used when ProtocolLib is unavailable so vanish keeps working otherwise.
 */
interface SilentContainerPacketAdapter extends AutoCloseable {
    boolean available();

    @Override
    void close();

    static SilentContainerPacketAdapter unavailable() {
        return UnavailableSilentContainerPacketAdapter.INSTANCE;
    }

    enum UnavailableSilentContainerPacketAdapter implements SilentContainerPacketAdapter {
        INSTANCE;

        @Override
        public boolean available() {
            return false;
        }

        @Override
        public void close() {
            // No packet listener was installed.
        }
    }
}
