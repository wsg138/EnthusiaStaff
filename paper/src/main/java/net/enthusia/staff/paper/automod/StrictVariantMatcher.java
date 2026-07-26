package net.enthusia.staff.paper.automod;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

public final class StrictVariantMatcher {
    private static final int MAX_VARIANTS = 256;
    private static final int MAX_VARIANT_LENGTH = 128;
    private static final Pattern LEGACY_FORMATTING = Pattern.compile("(?i)\\u00a7[0-9A-FK-ORX]");
    private static final Pattern MINIMESSAGE_FORMATTING = Pattern.compile("<[/!?]?[A-Za-z][^>\\r\\n]{0,63}>");

    private final List<String> variants;

    public StrictVariantMatcher(List<String> configuredVariants) {
        if (configuredVariants == null || configuredVariants.size() > MAX_VARIANTS) {
            throw new IllegalArgumentException("automod exact variants must be a bounded list");
        }
        Set<String> normalized = new LinkedHashSet<>();
        for (String variant : configuredVariants) {
            if (variant == null || variant.isBlank() || variant.length() > MAX_VARIANT_LENGTH) {
                throw new IllegalArgumentException("automod exact variants must be nonblank and at most 128 characters");
            }
            String prepared = normalize(variant);
            if (prepared.isBlank()) {
                throw new IllegalArgumentException("automod exact variants cannot contain only formatting");
            }
            normalized.add(prepared);
        }
        this.variants = List.copyOf(new ArrayList<>(normalized));
    }

    public boolean enabled() {
        return !variants.isEmpty();
    }

    public boolean matches(String input) {
        String normalized = normalize(input);
        for (String variant : variants) {
            int from = 0;
            while (from <= normalized.length() - variant.length()) {
                int index = normalized.indexOf(variant, from);
                if (index < 0) {
                    break;
                }
                int end = index + variant.length();
                if (hasBoundary(normalized, index, end, variant)) {
                    return true;
                }
                from = index + 1;
            }
        }
        return false;
    }

    public static String normalize(String input) {
        if (input == null) {
            return "";
        }
        String value = Normalizer.normalize(input, Normalizer.Form.NFKC);
        value = LEGACY_FORMATTING.matcher(value).replaceAll("");
        value = MINIMESSAGE_FORMATTING.matcher(value).replaceAll("");
        StringBuilder cleaned = new StringBuilder(value.length());
        value.codePoints().forEach(codePoint -> {
            int type = Character.getType(codePoint);
            if (type != Character.FORMAT && type != Character.CONTROL) {
                cleaned.appendCodePoint(codePoint);
            }
        });
        return cleaned.toString().toLowerCase(Locale.ROOT).trim();
    }

    private static boolean hasBoundary(String input, int start, int end, String variant) {
        int first = variant.codePointAt(0);
        int last = variant.codePointBefore(variant.length());
        if (Character.isLetterOrDigit(first) && start > 0) {
            int preceding = input.codePointBefore(start);
            if (Character.isLetterOrDigit(preceding)) {
                return false;
            }
        }
        if (Character.isLetterOrDigit(last) && end < input.length()) {
            int following = input.codePointAt(end);
            if (Character.isLetterOrDigit(following)) {
                return false;
            }
        }
        return true;
    }
}
