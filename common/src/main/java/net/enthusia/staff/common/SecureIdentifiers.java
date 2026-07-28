package net.enthusia.staff.common;

import java.security.SecureRandom;

public final class SecureIdentifiers {
    private static final char[] CROCKFORD = "0123456789ABCDEFGHJKMNPQRSTVWXYZ".toCharArray();

    private final SecureRandom random;

    public SecureIdentifiers(SecureRandom random) {
        this.random = random;
    }

    public CaseId newCaseId() {
        char[] value = new char[16];
        for (int index = 0; index < value.length; index++) {
            value[index] = CROCKFORD[random.nextInt(CROCKFORD.length)];
        }
        return new CaseId(new String(value));
    }
}
