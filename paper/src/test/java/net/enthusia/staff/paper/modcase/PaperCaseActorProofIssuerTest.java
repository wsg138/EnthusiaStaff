package net.enthusia.staff.paper.modcase;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.lang.reflect.Proxy;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import net.enthusia.staff.domain.auth.StaffRank;
import net.enthusia.staff.domain.modcase.AuthorityBackedCaseActions;
import net.enthusia.staff.domain.modcase.CaseNotice;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

class PaperCaseActorProofIssuerTest {
    private static final UUID ID = UUID.fromString("3b0d5bf6-355a-48b5-a701-9c0236d7fd99");
    private static final UUID OTHER = UUID.fromString("1b0d5bf6-355a-48b5-a701-9c0236d7fd99");
    private static final String SCOPE = "smp";
    private static final String DISCORD = String.valueOf(100000000000000000L + 345678L);
    private static final String GUILD = String.valueOf(800000000000000000L + 7654321L);
    private static final String CLIENT_ID = "staffbot";
    private static final String COMMAND_ID = "command:synthetic:0001";
    private static final long ISSUED_AT = 1791993600L;
    private static final Clock CLOCK = Clock.fixed(Instant.ofEpochSecond(ISSUED_AT), ZoneOffset.UTC);
    // Independently generated with the Python canonical_claims() implementation, synthetic key.
    private static final String PYTHON_SIGNATURE =
            "632eae91f8baa4da6f86e73426d2bac15dd559f361162a48476bcc5a09e482ee";

    private static byte[] secret() {
        byte[] result = new byte[32];
        for (int i = 0; i < result.length; i++) {
            result[i] = (byte) i;
        }
        return result;
    }

    private static AuthorityBackedCaseActions.Command command(String scope, UUID actor,
            CaseNotice.Action action, long revision) {
        return new AuthorityBackedCaseActions.Command(
                CaseNotice.Origin.MINECRAFT, scope, ID, revision, "minecraft:" + actor,
                action, COMMAND_ID);
    }

    private static AuthorityBackedCaseActions.Command claim() {
        return command(SCOPE, ID, CaseNotice.Action.CLAIM, 0);
    }

    private static PaperCaseStaffAuthority.VerifiedDiscordInteraction interaction(
            AuthorityBackedCaseActions.Command command) {
        return new PaperCaseStaffAuthority.VerifiedDiscordInteraction(
                DISCORD, GUILD, ISSUED_AT, command.origin(), command.scope(), command.caseId(),
                command.expectedRevision(), command.action(), command.commandId());
    }

    private static Player fakePlayer() {
        return (Player) Proxy.newProxyInstance(
                Thread.currentThread().getContextClassLoader(), new Class<?>[]{Player.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getUniqueId" -> ID;
                    case "isOnline" -> true;
                    default -> null;
                });
    }

    private static final class Fixture implements PaperCaseStaffAuthority.CurrentStaffPolicy {
        boolean link = true;
        boolean unique = true;
        boolean duty = true;
        boolean offline;
        boolean caseCurrent = true;
        boolean permission = true;
        boolean throwOnLookup;
        StaffRank rank = StaffRank.MOD;
        PaperCaseStaffAuthority.VerifiedDiscordInteraction verified = interaction(claim());
        final Player player = fakePlayer();
        final PaperCaseStaffAuthority authority;
        final byte[] key = secret();

        Fixture() {
            PaperCaseActorProofIssuer issuer =
                    new PaperCaseActorProofIssuer(SCOPE, CLIENT_ID, key, CLOCK);
            authority = new PaperCaseStaffAuthority(issuer,
                    evidence -> {
                        if (evidence.length != 1 || evidence[0] != 42) {
                            return Optional.empty();
                        }
                        return Optional.ofNullable(verified);
                    },
                    source -> source == player ? Optional.of(ID) : Optional.empty(),
                    this, CLOCK, SCOPE, GUILD);
        }

        PaperCaseActorProofIssuer.Proof discord(AuthorityBackedCaseActions.Command command) {
            return authority.issueFromDiscord(new byte[]{42}, command);
        }

        PaperCaseActorProofIssuer.Proof minecraft(AuthorityBackedCaseActions.Command command) {
            return authority.issueFromMinecraft(player, command);
        }

        @Override public Optional<UUID> uniquelyLinkedMinecraft(String discordId) {
            return unique && DISCORD.equals(discordId) ? Optional.of(ID) : Optional.empty();
        }
        @Override public boolean currentVerifiedLink(UUID id, String discordId) {
            return link && ID.equals(id) && DISCORD.equals(discordId);
        }
        @Override public Optional<StaffRank> currentRank(UUID id) {
            if (throwOnLookup) {
                throw new IllegalStateException("LuckPerms timed out");
            }
            return Optional.ofNullable(rank);
        }
        @Override public boolean currentActionPermission(UUID id, CaseNotice.Action action) {
            return permission;
        }
        @Override public boolean locallyAppliedStaffMode(UUID id) {
            return duty;
        }
        @Override public boolean explicitlyPermittedOfflineDiscord(UUID id) {
            return offline;
        }
        @Override public boolean currentCaseAllows(AuthorityBackedCaseActions.Command command) {
            return caseCurrent && command.expectedRevision() == 0;
        }
    }

    @Test
    void signedDiscordAndPaperMinecraftPathsPreserveIndependentPythonGoldenSignature() {
        Fixture fixture = new Fixture();
        fixture.key[0] = 99; // issuer takes a defensive copy
        var discordProof = fixture.discord(claim());
        var minecraftProof = fixture.minecraft(claim());
        assertEquals(ISSUED_AT, discordProof.issuedAt());
        assertEquals(ISSUED_AT + 30, discordProof.expiresAt());
        assertEquals(PYTHON_SIGNATURE, discordProof.signature());
        assertEquals(discordProof, minecraftProof);
    }

    @Test
    void unauthenticatedDiscordAndFalseActorIdentityFailClosed() {
        Fixture f = new Fixture();
        assertThrows(SecurityException.class, () ->
                f.authority.issueFromDiscord(null, claim()));
        assertThrows(SecurityException.class, () ->
                f.authority.issueFromDiscord(new byte[]{1}, claim()));
        assertThrows(SecurityException.class, () ->
                f.authority.issueFromMinecraft(null, claim()));
        assertThrows(SecurityException.class, () ->
                f.discord(command(SCOPE, OTHER, CaseNotice.Action.CLAIM, 0)));
        f.unique = false;
        assertThrows(SecurityException.class, () -> f.discord(claim()));
        f.unique = true;
        f.link = false;
        assertThrows(SecurityException.class, () -> f.discord(claim()));
    }

    @Test
    void revokedRankDutyPermissionAndCaseRevisionNeverSign() {
        Fixture f = new Fixture();
        f.rank = StaffRank.HELPER;
        assertThrows(SecurityException.class, () -> f.discord(claim()));
        f.rank = StaffRank.MOD;
        f.duty = false;
        assertThrows(SecurityException.class, () -> f.discord(claim()));
        f.offline = true;
        f.discord(claim()); // Explicit offline-review exception only for verified Discord.
        assertThrows(SecurityException.class, () -> f.minecraft(claim()));
        f.duty = true;
        f.permission = false;
        assertThrows(SecurityException.class, () -> f.discord(claim()));
        f.permission = true;
        f.caseCurrent = false;
        assertThrows(SecurityException.class, () -> f.discord(claim()));
        f.caseCurrent = true;
        f.throwOnLookup = true;
        assertThrows(SecurityException.class, () -> f.discord(claim()));
    }

    @Test
    void scopeGuildActionExpiredInteractionAndStaleRevisionCannotBeSubstituted() {
        Fixture f = new Fixture();
        assertThrows(SecurityException.class, () ->
                f.discord(command("other", ID, CaseNotice.Action.CLAIM, 0)));
        assertThrows(SecurityException.class, () ->
                f.discord(command(SCOPE, ID, CaseNotice.Action.RESOLVE, 0)));
        assertThrows(SecurityException.class, () ->
                f.discord(command(SCOPE, ID, CaseNotice.Action.CLAIM, 1)));
        f.verified = new PaperCaseStaffAuthority.VerifiedDiscordInteraction(
                DISCORD, "99999", ISSUED_AT, CaseNotice.Origin.MINECRAFT,
                SCOPE, ID, 0, CaseNotice.Action.CLAIM, COMMAND_ID);
        assertThrows(SecurityException.class, () -> f.discord(claim()));
        f.verified = new PaperCaseStaffAuthority.VerifiedDiscordInteraction(
                DISCORD, GUILD, ISSUED_AT - 31, CaseNotice.Origin.MINECRAFT,
                SCOPE, ID, 0, CaseNotice.Action.CLAIM, COMMAND_ID);
        assertThrows(SecurityException.class, () -> f.discord(claim()));
        f.verified = new PaperCaseStaffAuthority.VerifiedDiscordInteraction(
                DISCORD, GUILD, ISSUED_AT + 6, CaseNotice.Origin.MINECRAFT,
                SCOPE, ID, 0, CaseNotice.Action.CLAIM, COMMAND_ID);
        assertThrows(SecurityException.class, () -> f.discord(claim()));
    }

    @Test
    void malformedSettingsAndMissingDependenciesFailClosed() {
        assertThrows(IllegalArgumentException.class,
                () -> new PaperCaseActorProofIssuer(SCOPE, CLIENT_ID, new byte[1], CLOCK));
        assertThrows(NullPointerException.class,
                () -> new PaperCaseStaffAuthority(
                        new PaperCaseActorProofIssuer(SCOPE, CLIENT_ID, secret(), CLOCK),
                        evidence -> Optional.empty(), source -> Optional.empty(), null,
                        CLOCK, SCOPE, GUILD));
        Fixture f = new Fixture();
        assertThrows(SecurityException.class, () -> f.authority.issueFromDiscord(
                new byte[65537], claim()));
    }
}
