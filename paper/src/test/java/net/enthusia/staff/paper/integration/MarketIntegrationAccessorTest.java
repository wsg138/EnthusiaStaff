package net.enthusia.staff.paper.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;

class MarketIntegrationAccessorTest {
    private static final String LEGACY_ACCESSOR = "getId";

    @Test
    void prefersLegacyBeanAccessorWhenAvailable() throws Exception {
        Method accessor = MarketIntegration.modelAccessor(BeanShape.class, LEGACY_ACCESSOR, "id");
        assertEquals(LEGACY_ACCESSOR, accessor.getName());
        assertEquals("legacy", accessor.invoke(new BeanShape()));
    }

    @Test
    void acceptsCurrentRecordStyleAccessor() throws Exception {
        Method accessor = MarketIntegration.modelAccessor(RecordShape.class, LEGACY_ACCESSOR, "id");
        assertEquals("id", accessor.getName());
        assertEquals("current", accessor.invoke(new RecordShape("current")));
    }

    @Test
    void rejectsModelsWithNeitherSupportedAccessor() {
        assertThrows(NoSuchMethodException.class,
                () -> MarketIntegration.modelAccessor(UnsupportedShape.class, LEGACY_ACCESSOR, "id"));
    }

    static final class BeanShape {
        public String getId() {
            return "legacy";
        }

        public String id() {
            return "record";
        }
    }

    record RecordShape(String id) {
    }

    static final class UnsupportedShape {
    }
}
