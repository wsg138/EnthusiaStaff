package net.enthusia.staff.paper.visibility;

import static org.junit.jupiter.api.Assertions.*;
import java.util.OptionalInt;
import org.junit.jupiter.api.Test;

class Paper26VanishRuntimePolicyTest {
    @Test void acceptsOnlyExactPaperBuild129() {
        String supported = Paper26VanishClientGameModeAdapter.SUPPORTED_MINECRAFT_VERSION;
        assertTrue(Paper26VanishClientGameModeAdapter.supportsRuntime(true, supported, OptionalInt.of(129)));
        assertFalse(Paper26VanishClientGameModeAdapter.supportsRuntime(true, supported, OptionalInt.of(128)));
        assertFalse(Paper26VanishClientGameModeAdapter.supportsRuntime(true, "26.3", OptionalInt.of(129)));
        assertFalse(Paper26VanishClientGameModeAdapter.supportsRuntime(false, supported, OptionalInt.of(129)));
        assertFalse(Paper26VanishClientGameModeAdapter.supportsRuntime(true, supported, OptionalInt.empty()));
    }
}
