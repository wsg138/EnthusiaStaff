package net.enthusia.staff.paper.modcase;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import net.enthusia.staff.domain.modcase.AuthorityBackedCaseActions;
import net.enthusia.staff.domain.modcase.CaseNotice;
import org.junit.jupiter.api.Test;

class PaperCaseActorProofIssuerTest {
    private static final UUID ID = UUID.fromString("3b0d5bf6-355a-48b5-a701-9c0236d7fd99");
    private static final String ACTOR = "minecraft:" + ID;
    private static final String SCOPE = "smp";
    private static final String CLIENT_ID = "staffbot";
    private static final long ISSUED_AT = 1791993600L;
    private static final Clock CLOCK = Clock.fixed(Instant.ofEpochSecond(ISSUED_AT), ZoneOffset.UTC);
    // Independent Python canonical_claims() and HMAC-SHA256 test vector.
    private static final String PYTHON_SIGNATURE =
            "632eae91f8baa4da6f86e73426d2bac15dd559f361162a48476bcc5a09e482ee";

    private static byte[] secret() {
        byte[] result = new byte[32];
        for (int i = 0; i < result.length; i++) {
            result[i] = (byte) i;
        }
        return result;
    }

    private static AuthorityBackedCaseActions.Command command(
            String scope, String actor, CaseNotice.Action action) {
        return new AuthorityBackedCaseActions.Command(
                CaseNotice.Origin.MINECRAFT, scope, ID, 0, actor,
                action, "command:synthetic:0001");
    }

    @Test
    void goldenSignatureMatchesIndependentPythonVerifierByteForByte() {
        AtomicInteger roleChecks = new AtomicInteger();
        byte[] key = secret();
        PaperCaseActorProofIssuer issuer = new PaperCaseActorProofIssuer(
                SCOPE, CLIENT_ID, key, CLOCK, (id, scope, cmd) -> {
                    roleChecks.incrementAndGet();
                    return ID.equals(id) && SCOPE.equals(scope)
                            && cmd.action() == CaseNotice.Action.CLAIM;
                });
        key[0] = 99;
        var proof = issuer.issue(command(SCOPE, ACTOR, CaseNotice.Action.CLAIM));
        assertEquals(ISSUED_AT, proof.issuedAt());
        assertEquals(ISSUED_AT + 30L, proof.expiresAt());
        assertEquals(PYTHON_SIGNATURE, proof.signature());
        assertEquals(1, roleChecks.get());
    }

    @Test
    void deniedActorOrActionProducesNoProof() {
        PaperCaseActorProofIssuer issuer = new PaperCaseActorProofIssuer(
                SCOPE, CLIENT_ID, secret(), CLOCK,
                (id, scope, cmd) -> cmd.action() == CaseNotice.Action.CLAIM
                        && ID.equals(id));
        assertThrows(SecurityException.class,
                () -> issuer.issue(command(SCOPE, ACTOR, CaseNotice.Action.RESOLVE)));
        assertThrows(SecurityException.class,
                () -> issuer.issue(command(SCOPE, "staff:alice", CaseNotice.Action.CLAIM)));
        assertThrows(SecurityException.class,
                () -> issuer.issue(command("other-server", ACTOR, CaseNotice.Action.CLAIM)));
    }

    @Test
    void weakSecretAndMissingActualRoleGateFailClosed() {
        assertThrows(IllegalArgumentException.class,
                () -> new PaperCaseActorProofIssuer(SCOPE, CLIENT_ID, new byte[1],
                        CLOCK, (id, scope, cmd) -> true));
        assertThrows(IllegalArgumentException.class,
                () -> new PaperCaseActorProofIssuer(SCOPE, CLIENT_ID, secret(), CLOCK, null));
        PaperCaseActorProofIssuer denyAll = new PaperCaseActorProofIssuer(
                SCOPE, CLIENT_ID, secret(), CLOCK, (id, scope, cmd) -> false);
        assertThrows(SecurityException.class,
                () -> denyAll.issue(command(SCOPE, ACTOR, CaseNotice.Action.CLAIM)));
    }
}
