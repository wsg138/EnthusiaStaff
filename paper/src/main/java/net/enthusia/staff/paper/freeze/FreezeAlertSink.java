package net.enthusia.staff.paper.freeze;

import net.enthusia.staff.domain.auth.Actor;
import net.enthusia.staff.domain.player.PlayerIdentity;

public interface FreezeAlertSink {
    void frozen(PlayerIdentity target, Actor actor, String reason);

    void unfrozen(PlayerIdentity target, Actor actor, String reason);

    static FreezeAlertSink noOp() {
        return new FreezeAlertSink() {
            @Override
            public void frozen(PlayerIdentity target, Actor actor, String reason) {
            }

            @Override
            public void unfrozen(PlayerIdentity target, Actor actor, String reason) {
            }
        };
    }
}
