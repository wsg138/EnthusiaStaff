package net.enthusia.staff.paper.automod;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class StrictVariantMatcherTest {
    private final StrictVariantMatcher matcher = new StrictVariantMatcher(List.of("blocked phrase", "bad-form"));

    @Test
    void matchesOnlyConfiguredNormalizedForms() {
        assertTrue(matcher.matches("That BLOCKED PHRASE is configured."));
        assertTrue(matcher.matches("That b\u200blocked phrase is formatting-obfuscated."));
        assertTrue(matcher.matches("A <red>bad-form</red> appears."));
    }

    @Test
    void punctuationAndSpacingVariantsMustBeExplicit() {
        assertFalse(matcher.matches("blocked-phrase"));
        assertFalse(matcher.matches("blocked  phrase"));
        assertFalse(matcher.matches("bad form"));
    }

    @Test
    void doesNotMatchInsideLargerAlphanumericToken() {
        StrictVariantMatcher token = new StrictVariantMatcher(List.of("test"));
        assertFalse(token.matches("contestant"));
        assertTrue(token.matches("a test, here"));
    }
}
