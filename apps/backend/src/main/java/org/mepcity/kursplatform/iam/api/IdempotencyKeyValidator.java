package org.mepcity.kursplatform.iam.api;

import org.mepcity.kursplatform.iam.domain.IamException;

import java.util.regex.Pattern;

/**
 * {@code Idempotency-Key} format gate shared by every IAM write endpoint, per
 * {@code API_GENEL_KURALLARI.md} §3.2: at most 128 ASCII printable (non-whitespace) characters.
 * Distinct from the {@code X-Request-Id} contract on purpose — the two headers serve different
 * concerns and must never be substitutable for one another.
 */
final class IdempotencyKeyValidator {

    private static final Pattern ALLOWED = Pattern.compile("[\\x21-\\x7E]{1,128}");

    private IdempotencyKeyValidator() {
    }

    static void requireValid(String idempotencyKey) {
        if (idempotencyKey == null || !ALLOWED.matcher(idempotencyKey).matches()) {
            throw new IamException("INVALID_REQUEST",
                    "Idempotency-Key en fazla 128 ASCII görünür karakter olmalıdır.");
        }
    }
}
