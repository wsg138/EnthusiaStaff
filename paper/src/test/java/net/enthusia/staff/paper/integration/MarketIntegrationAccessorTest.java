package net.enthusia.staff.paper.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;

class MarketIntegrationAccessorTest {

    @Test
    void prefersLegacyBeanAccessorWhenAvailable() throws Exception {
        Method accessor = MarketIntegration.modelAccessor(BeanShape.class, "getId", "id");
        assertEquals("getId", accessor.getName());
        assertEquals("legacy", accessor.invoke(new BeanShape()));
    }

    @Test
    void acceptsCurrentRecordStyleAccessor() throws Exception {
        Method accessor = MarketIntegration.modelAccessor(RecordShape.class, "getId", "id");
        assertEquals("id", accessor.getName());
        assertEquals("current", accessor.invoke(new RecordShape("current")));
    }

    @Test
    void rejectsModelsWithNeitherSupportedAccessor() {
        assertThrows(NoSuchMethodException.class,
                () -> MarketIntegration.modelAccessor(UnsupportedShape.class, "getId", "id"));
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
