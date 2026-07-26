package net.enthusia.staff.common;

public record IdempotencyKey(String value) {
    public IdempotencyKey {
        value = Checks.nonBlank(value, "idempotencyKey", 128);
        if (!value.chars().allMatch(character -> character >= 0x21 && character <= 0x7e)) {
            throw new IllegalArgumentException("idempotencyKey contains unsupported characters");
        }
    }

    @Override
    public String toString() {
        return value;
    }
}
