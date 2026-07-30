package net.enthusia.staff.paper.visibility;

interface SpectatorTabPacketAdapter extends AutoCloseable {
    boolean available();

    @Override
    void close();

    static SpectatorTabPacketAdapter unavailable() {
        return UnavailableSpectatorTabPacketAdapter.INSTANCE;
    }

    enum UnavailableSpectatorTabPacketAdapter implements SpectatorTabPacketAdapter {
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
