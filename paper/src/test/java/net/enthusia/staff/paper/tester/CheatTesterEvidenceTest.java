package net.enthusia.staff.paper.tester;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Proxy;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import net.enthusia.staff.domain.tester.CheatTesterType;
import org.bukkit.Location;
import org.bukkit.World;
import org.junit.jupiter.api.Test;

class CheatTesterEvidenceTest {
    private static final Instant NOW = Instant.parse("2026-08-07T20:00:00Z");
    private static final UUID WORLD_ID = UUID.fromString("10000000-0000-0000-0000-000000000001");
    private final CheatTesterEvidence evidence = new CheatTesterEvidence(
            Clock.fixed(NOW, ZoneOffset.UTC),
            CheatTesterSettings.defaults()
    );

    @Test
    void boundedReasonDefaultsAndTruncates() {
        assertEquals("unspecified tester termination", CheatTesterEvidence.boundedReason(null));
        assertEquals("unspecified tester termination", CheatTesterEvidence.boundedReason("   "));
        assertEquals("done", CheatTesterEvidence.boundedReason("done"));
        assertEquals(255, CheatTesterEvidence.boundedReason("x".repeat(300)).length());
    }

    @Test
    void displacementRequiresMatchingWorldAndUsesThreeDimensions() {
        CheatTesterSession.StartPoint start = new CheatTesterSession.StartPoint(WORLD_ID, 1.0D, 2.0D, 3.0D);
        Location current = new Location(world(WORLD_ID), 4.0D, 6.0D, 15.0D);
        assertEquals(13.0D, CheatTesterEvidence.displacement(current, start));
        assertEquals(-1.0D, CheatTesterEvidence.displacement(new Location(null, 4.0D, 6.0D, 15.0D), start));
    }

    @Test
    void summaryFormatsEveryTesterType() {
        assertTrue(evidence.summary(session(CheatTesterType.TOTEM_REFILL),
                "{\"offhandTotemObserved\":true}").contains("true"));
        assertTrue(evidence.summary(session(CheatTesterType.AUTO_ARMOR),
                "{\"armorReequippedObserved\":false}").contains("false"));
        assertTrue(evidence.summary(session(CheatTesterType.VELOCITY),
                "{\"displacement\":2.5}").contains("2.50"));
        assertTrue(evidence.summary(session(CheatTesterType.NO_FALL),
                "{\"airborneFallResets\":2,\"maximumFallDistance\":4.25}").contains("4.25"));
        assertTrue(evidence.summary(session(CheatTesterType.FAKE_ENTITY),
                "{\"interactions\":3,\"attacks\":1,\"minimumAimAngleDegrees\":6.5}").contains("6.5°"));
    }

    @Test
    void configurationIncludesSyntheticHandleOnlyWhenPresent() {
        CheatTesterSession ordinary = session(CheatTesterType.NO_FALL);
        String ordinaryConfig = evidence.configuration(ordinary);
        assertTrue(ordinaryConfig.contains("\"tester\":\"no-fall\""));
        assertTrue(!ordinaryConfig.contains("fakeEntityId"));

        CheatTesterSession fake = session(CheatTesterType.FAKE_ENTITY);
        fake.fakeHandle = new FakeEntityAdapter.Handle(2_000_000_000, UUID.fromString(
                "10000000-0000-0000-0000-000000000002"
        ));
        String fakeConfig = evidence.configuration(fake);
        assertTrue(fakeConfig.contains("\"fakeEntityId\":2000000000"));
        assertTrue(fakeConfig.contains("fakeEntityUuid"));
    }

    private static CheatTesterSession session(CheatTesterType type) {
        return new CheatTesterSession(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                type,
                false,
                NOW.minusSeconds(1)
        );
    }

    private static World world(UUID id) {
        return (World) Proxy.newProxyInstance(
                Thread.currentThread().getContextClassLoader(),
                new Class<?>[]{World.class},
                (instance, method, arguments) -> {
                    if (method.getName().equals("getUID")) {
                        return id;
                    }
                    Class<?> type = method.getReturnType();
                    if (!type.isPrimitive()) {
                        return null;
                    }
                    if (type == boolean.class) {
                        return false;
                    }
                    if (type == long.class) {
                        return 0L;
                    }
                    if (type == double.class) {
                        return 0D;
                    }
                    if (type == float.class) {
                        return 0F;
                    }
                    return 0;
                }
        );
    }
}
