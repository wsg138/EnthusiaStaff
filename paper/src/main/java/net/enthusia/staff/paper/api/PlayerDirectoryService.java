package net.enthusia.staff.paper.api;

import java.util.List;

public interface PlayerDirectoryService {
    List<Entry> search(String prefix, int limit);

    record Entry(String playerId, String currentName, boolean online, String serverId) {
    }
}
