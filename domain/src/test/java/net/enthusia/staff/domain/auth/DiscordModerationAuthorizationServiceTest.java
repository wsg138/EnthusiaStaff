package net.enthusia.staff.domain.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.enthusia.staff.domain.moderation.ModerationPlatform;
import net.enthusia.staff.domain.sanction.SanctionLength;
import org.junit.jupiter.api.Test;

class DiscordModerationAuthorizationServiceTest {
    private static final Duration HELPER_MUTE = Duration.ofHours(2);
    private static final Duration MOD_MUTE = Duration.ofDays(7);
    private static final Duration MOD_BAN = Duration.ofDays(30);
    private static final Duration MOD_RESTRICTION = Duration.ofDays(7);

    private final DiscordModerationAuthorizationService service = new DiscordModerationAuthorizationService(
            new DiscordAuthorizationLimits(HELPER_MUTE, MOD_MUTE, MOD_BAN, MOD_RESTRICTION)
    );

    @Test
    void operationMatrixIsExplicitForEveryRank() {
        Map<StaffRank, Set<DiscordModerationOperation>> expected = new EnumMap<>(StaffRank.class);
        Set<DiscordModerationOperation> reads = EnumSet.of(
                DiscordModerationOperation.VIEW_LINKED_ACCOUNTS,
                DiscordModerationOperation.VIEW_HISTORY,
                DiscordModerationOperation.VIEW_NOTES,
                DiscordModerationOperation.VIEW_EVIDENCE
        );
        expected.put(StaffRank.HELPER, plus(reads, DiscordModerationOperation.ISSUE_SANCTION));
        expected.put(StaffRank.MOD, plus(reads,
                DiscordModerationOperation.ISSUE_SANCTION,
                DiscordModerationOperation.END_SANCTION,
                DiscordModerationOperation.REVOKE_SANCTION,
                DiscordModerationOperation.APPROVE_SANCTION_REQUEST,
                DiscordModerationOperation.REQUEST_OVERTURN));
        expected.put(StaffRank.DEVELOPER, expected.get(StaffRank.MOD));
        expected.put(StaffRank.ADMIN, EnumSet.allOf(DiscordModerationOperation.class));
        expected.put(StaffRank.FOUNDER, EnumSet.allOf(DiscordModerationOperation.class));
        expected.put(StaffRank.SYSTEM, EnumSet.noneOf(DiscordModerationOperation.class));

        for (StaffRank rank : StaffRank.values()) {
            Actor actor = actor(rank);
            for (DiscordModerationOperation operation : DiscordModerationOperation.values()) {
                DiscordAuthorizationRequest request = operation == DiscordModerationOperation.ISSUE_SANCTION
                        ? issue(discord(DiscordConsequenceType.WARNING, SanctionLength.instant(), false, false))
                        : operation(operation, ModerationPlatform.DISCORD);
                assertEquals(
                        expected.get(rank).contains(operation),
                        service.authorize(actor, Optional.empty(), request).permitted(),
                        rank + " / " + operation
                );
            }
        }
    }

    @Test
    void helperIsLimitedToConfiguredWarningsAndShortTemporaryMutes() {
        Actor helper = actor(StaffRank.HELPER);
        assertAllowed(helper, issue(discord(DiscordConsequenceType.WARNING, SanctionLength.instant(), false, false)));
        assertAllowed(helper, issue(discord(DiscordConsequenceType.MUTE, SanctionLength.temporary(HELPER_MUTE), false, false)));

        assertDenied(helper,
                issue(discord(DiscordConsequenceType.MUTE,
                        SanctionLength.temporary(HELPER_MUTE.plusSeconds(1)), false, false)),
                DiscordAuthorizationDenial.DURATION_EXCEEDS_LIMIT);
        assertDenied(helper,
                issue(discord(DiscordConsequenceType.MUTE, SanctionLength.permanent(), false, false)),
                DiscordAuthorizationDenial.PERMANENT_ACTION_REQUIRES_ADMIN);
        assertDenied(helper,
                issue(discord(DiscordConsequenceType.KICK, SanctionLength.instant(), false, false)),
                DiscordAuthorizationDenial.UNAUTHORIZED_CONSEQUENCE);
        assertDenied(helper,
                issue(discord(DiscordConsequenceType.BAN, SanctionLength.temporary(Duration.ofHours(1)), false, false)),
                DiscordAuthorizationDenial.UNAUTHORIZED_CONSEQUENCE);
        assertDenied(helper,
                issue(discord(DiscordConsequenceType.MUTE, SanctionLength.temporary(Duration.ofHours(1)), true, false)),
                DiscordAuthorizationDenial.CUSTOM_DURATION_NOT_PERMITTED);
        assertDenied(helper,
                issue(minecraft(DiscordConsequenceType.WARNING, SanctionLength.instant(), false, false)),
                DiscordAuthorizationDenial.HELPER_CROSS_PLATFORM_FORBIDDEN);
    }

    @Test
    void moderatorAndDeveloperShareDiscordAuthorityButNotMinecraftAuthority() {
        for (StaffRank rank : List.of(StaffRank.MOD, StaffRank.DEVELOPER)) {
            Actor actor = actor(rank);
            assertAllowed(actor, issue(discord(DiscordConsequenceType.WARNING, SanctionLength.instant(), false, false)));
            assertAllowed(actor, issue(discord(DiscordConsequenceType.KICK, SanctionLength.instant(), false, false)));
            assertAllowed(actor, issue(discord(DiscordConsequenceType.MUTE, SanctionLength.temporary(MOD_MUTE), true, false)));
            assertAllowed(actor, issue(discord(DiscordConsequenceType.BAN, SanctionLength.temporary(MOD_BAN), true, false)));
            assertAllowed(actor, issue(discord(DiscordConsequenceType.CHANNEL_RESTRICTION,
                    SanctionLength.temporary(MOD_RESTRICTION), true, false)));

            assertDenied(actor,
                    issue(discord(DiscordConsequenceType.BAN, SanctionLength.permanent(), false, false)),
                    DiscordAuthorizationDenial.PERMANENT_ACTION_REQUIRES_ADMIN);
            assertDenied(actor,
                    issue(discord(DiscordConsequenceType.MUTE,
                            SanctionLength.temporary(MOD_MUTE.plusSeconds(1)), false, false)),
                    DiscordAuthorizationDenial.DURATION_EXCEEDS_LIMIT);
            assertDenied(actor,
                    issue(discord(DiscordConsequenceType.MUTE, SanctionLength.temporary(Duration.ofHours(1)), false, true)),
                    DiscordAuthorizationDenial.CUSTOM_CONSEQUENCE_REQUIRES_ADMIN);
        }

        assertAllowed(actor(StaffRank.MOD), issue(minecraft(
                DiscordConsequenceType.WARNING, SanctionLength.instant(), false, false)));
        assertDenied(actor(StaffRank.DEVELOPER), issue(minecraft(
                        DiscordConsequenceType.WARNING, SanctionLength.instant(), false, false)),
                DiscordAuthorizationDenial.MINECRAFT_AUTHORIZATION_DENIED);
    }

    @Test
    void crossPlatformPlansAuthorizeEachPlatformIndependently() {
        DiscordAuthorizationRequest separateConsequences = issue(
                discord(DiscordConsequenceType.MUTE, SanctionLength.temporary(Duration.ofHours(4)), false, false),
                minecraft(DiscordConsequenceType.WARNING, SanctionLength.instant(), false, false)
        );
        assertAllowed(actor(StaffRank.MOD), separateConsequences);
        assertDenied(actor(StaffRank.DEVELOPER), separateConsequences,
                DiscordAuthorizationDenial.MINECRAFT_AUTHORIZATION_DENIED);
        assertDenied(actor(StaffRank.HELPER), separateConsequences,
                DiscordAuthorizationDenial.HELPER_CROSS_PLATFORM_FORBIDDEN);
    }

    @Test
    void adminAndFounderReceivePermanentDiscordAuthorityWhileMinecraftKeepsItsOwnCustomPolicy() {
        for (StaffRank rank : List.of(StaffRank.ADMIN, StaffRank.FOUNDER)) {
            Actor actor = actor(rank);
            assertAllowed(actor, issue(discord(DiscordConsequenceType.MUTE, SanctionLength.permanent(), false, false)));
            assertAllowed(actor, issue(discord(DiscordConsequenceType.BAN, SanctionLength.permanent(), false, true)));
            assertAllowed(actor, issue(discord(DiscordConsequenceType.CHANNEL_RESTRICTION,
                    SanctionLength.permanent(), false, true)));
        }

        assertAllowed(actor(StaffRank.ADMIN), issue(minecraft(
                DiscordConsequenceType.MUTE, SanctionLength.temporary(Duration.ofDays(45)), true, false)));
        assertDenied(actor(StaffRank.ADMIN), issue(minecraft(
                        DiscordConsequenceType.MUTE, SanctionLength.temporary(Duration.ofDays(45)), false, true)),
                DiscordAuthorizationDenial.MINECRAFT_AUTHORIZATION_DENIED);
        assertAllowed(actor(StaffRank.FOUNDER), issue(minecraft(
                DiscordConsequenceType.MUTE, SanctionLength.temporary(Duration.ofDays(45)), false, true)));
    }

    @Test
    void selfAndEqualOrHigherStaffTargetsAreProtectedForMutations() {
        DiscordAuthorizationRequest request = issue(discord(
                DiscordConsequenceType.WARNING, SanctionLength.instant(), false, false));
        Actor mod = actor(StaffRank.MOD);
        assertEquals(
                DiscordAuthorizationDenial.SELF_TARGET,
                service.authorize(mod, Optional.of(mod), request).denial()
        );

        Map<StaffRank, Integer> levels = Map.of(
                StaffRank.HELPER, 10,
                StaffRank.MOD, 20,
                StaffRank.DEVELOPER, 20,
                StaffRank.ADMIN, 30,
                StaffRank.FOUNDER, 40,
                StaffRank.SYSTEM, 50
        );
        for (StaffRank actorRank : List.of(
                StaffRank.HELPER, StaffRank.MOD, StaffRank.DEVELOPER, StaffRank.ADMIN, StaffRank.FOUNDER)) {
            for (StaffRank targetRank : StaffRank.values()) {
                Actor actor = actor(actorRank);
                Actor target = actor(targetRank);
                boolean shouldPass = levels.get(actorRank) > levels.get(targetRank);
                assertEquals(
                        shouldPass,
                        service.authorize(actor, Optional.of(target), request).permitted(),
                        actorRank + " -> " + targetRank
                );
            }
        }

        assertTrue(service.authorize(
                actor(StaffRank.HELPER),
                Optional.of(actor(StaffRank.FOUNDER)),
                operation(DiscordModerationOperation.VIEW_HISTORY, ModerationPlatform.DISCORD)
        ).permitted(), "read-only investigation does not mutate protected staff");
    }

    @Test
    void discordRoleHierarchyIsOnlyAnExternalPreconditionAndNeverAnAuthoritySource() {
        DiscordAuthorizationDecision warning = service.authorize(
                actor(StaffRank.MOD), Optional.empty(),
                issue(discord(DiscordConsequenceType.WARNING, SanctionLength.instant(), false, false))
        );
        assertTrue(warning.permitted());
        assertTrue(warning.requiredPreconditions().isEmpty());

        DiscordAuthorizationDecision mute = service.authorize(
                actor(StaffRank.MOD), Optional.empty(),
                issue(discord(DiscordConsequenceType.MUTE, SanctionLength.temporary(Duration.ofHours(1)), false, false))
        );
        assertTrue(mute.permitted());
        assertEquals(Set.of(DiscordEnforcementPrecondition.DISCORD_ROLE_HIERARCHY), mute.requiredPreconditions());

        DiscordAuthorizationDecision developerMinecraft = service.authorize(
                actor(StaffRank.DEVELOPER), Optional.empty(),
                issue(minecraft(DiscordConsequenceType.WARNING, SanctionLength.instant(), false, false))
        );
        assertFalse(developerMinecraft.permitted());
        assertTrue(developerMinecraft.requiredPreconditions().isEmpty());
    }

    @Test
    void finalCommitReauthorizationFailsClosedOnStaleActorOrTargetAuthority() {
        Actor mod = actor(StaffRank.MOD);
        Actor helperTarget = actor(StaffRank.HELPER);
        DiscordAuthorizationRequest request = issue(discord(
                DiscordConsequenceType.WARNING, SanctionLength.instant(), false, false));
        DiscordAuthorizationSnapshot snapshot = service.captureForConfirmation(
                mod, Optional.of(helperTarget), request).orElseThrow();

        assertTrue(service.reauthorize(snapshot, mod, Optional.of(helperTarget)).permitted());

        Actor promotedActor = new Actor(mod.id(), mod.displayName(), StaffRank.ADMIN);
        assertEquals(
                DiscordAuthorizationDenial.STALE_AUTHORIZATION,
                service.reauthorize(snapshot, promotedActor, Optional.of(helperTarget)).denial()
        );
        Actor promotedTarget = new Actor(helperTarget.id(), helperTarget.displayName(), StaffRank.MOD);
        assertEquals(
                DiscordAuthorizationDenial.STALE_AUTHORIZATION,
                service.reauthorize(snapshot, mod, Optional.of(promotedTarget)).denial()
        );
        assertEquals(
                DiscordAuthorizationDenial.STALE_AUTHORIZATION,
                service.reauthorize(snapshot, mod, Optional.of(actor(StaffRank.HELPER))).denial()
        );

        assertTrue(service.captureForConfirmation(
                actor(StaffRank.HELPER), Optional.empty(),
                issue(discord(DiscordConsequenceType.BAN, SanctionLength.temporary(Duration.ofHours(1)), false, false))
        ).isEmpty());
    }

    @Test
    void requestShapeRejectsImplicitOrInvalidScopeAndDurationCombinations() {
        assertThrows(IllegalArgumentException.class, () -> new DiscordAuthorizationRequest(
                DiscordModerationOperation.VIEW_HISTORY, Set.of(), List.of()));
        assertThrows(IllegalArgumentException.class, () -> new DiscordAuthorizationRequest(
                DiscordModerationOperation.ISSUE_SANCTION,
                Set.of(ModerationPlatform.DISCORD),
                List.of(minecraft(DiscordConsequenceType.WARNING, SanctionLength.instant(), false, false))
        ));
        assertThrows(IllegalArgumentException.class, () -> new DiscordAuthorizationRequest(
                DiscordModerationOperation.ISSUE_SANCTION,
                Set.of(ModerationPlatform.DISCORD),
                List.of(
                        discord(DiscordConsequenceType.WARNING, SanctionLength.instant(), false, false),
                        discord(DiscordConsequenceType.KICK, SanctionLength.instant(), false, false)
                )
        ));
        assertThrows(IllegalArgumentException.class, () -> minecraft(
                DiscordConsequenceType.CHANNEL_RESTRICTION,
                SanctionLength.temporary(Duration.ofHours(1)), false, false));
        assertThrows(IllegalArgumentException.class, () -> discord(
                DiscordConsequenceType.WARNING,
                SanctionLength.temporary(Duration.ofHours(1)), false, false));
        assertThrows(IllegalArgumentException.class, () -> discord(
                DiscordConsequenceType.BAN,
                SanctionLength.permanent(), true, false));
        assertThrows(IllegalArgumentException.class, () -> new DiscordAuthorizationLimits(
                Duration.ofHours(3), Duration.ofHours(2), MOD_BAN, MOD_RESTRICTION));
    }

    private void assertAllowed(Actor actor, DiscordAuthorizationRequest request) {
        DiscordAuthorizationDecision decision = service.authorize(actor, Optional.empty(), request);
        assertTrue(decision.permitted(), () -> actor.rank() + " denied: " + decision.denial());
    }

    private void assertDenied(
            Actor actor,
            DiscordAuthorizationRequest request,
            DiscordAuthorizationDenial denial
    ) {
        DiscordAuthorizationDecision decision = service.authorize(actor, Optional.empty(), request);
        assertFalse(decision.permitted());
        assertEquals(denial, decision.denial());
    }

    private static DiscordAuthorizationRequest issue(DiscordConsequenceIntent... consequences) {
        EnumSet<ModerationPlatform> platforms = EnumSet.noneOf(ModerationPlatform.class);
        for (DiscordConsequenceIntent consequence : consequences) {
            platforms.add(consequence.platform());
        }
        return new DiscordAuthorizationRequest(
                DiscordModerationOperation.ISSUE_SANCTION,
                platforms,
                List.of(consequences)
        );
    }

    private static DiscordAuthorizationRequest operation(
            DiscordModerationOperation operation,
            ModerationPlatform... platforms
    ) {
        return new DiscordAuthorizationRequest(operation, Set.of(platforms), List.of());
    }

    private static DiscordConsequenceIntent discord(
            DiscordConsequenceType type,
            SanctionLength length,
            boolean customDuration,
            boolean customConsequence
    ) {
        return new DiscordConsequenceIntent(
                ModerationPlatform.DISCORD, type, length, customDuration, customConsequence);
    }

    private static DiscordConsequenceIntent minecraft(
            DiscordConsequenceType type,
            SanctionLength length,
            boolean customDuration,
            boolean customConsequence
    ) {
        return new DiscordConsequenceIntent(
                ModerationPlatform.MINECRAFT, type, length, customDuration, customConsequence);
    }

    private static Actor actor(StaffRank rank) {
        return new Actor(UUID.randomUUID(), rank.name(), rank);
    }

    private static Set<DiscordModerationOperation> plus(
            Set<DiscordModerationOperation> base,
            DiscordModerationOperation... additions
    ) {
        EnumSet<DiscordModerationOperation> result = EnumSet.copyOf(base);
        result.addAll(List.of(additions));
        return result;
    }
}
