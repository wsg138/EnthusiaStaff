package net.enthusia.staff.paper.freeze;

import net.enthusia.staff.domain.freeze.FreezeRecord;

@FunctionalInterface
public interface FreezeNoticeSink {
    void show(FreezeRecord record, String actorName);

    static FreezeNoticeSink noOp() {
        return (record, actorName) -> {
        };
    }
}
