package org.mepcity.kursplatform.iam.domain;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class IdempotencyKeyTests {

    @Test
    void isCompletedReturnsTrueOnlyForCompletedStatus() {
        IdempotencyKey completed = createKey(IdempotencyStatus.COMPLETED);
        IdempotencyKey pending = createKey(IdempotencyStatus.PENDING);
        IdempotencyKey failed = createKey(IdempotencyStatus.FAILED);
        assertThat(completed.isCompleted()).isTrue();
        assertThat(pending.isCompleted()).isFalse();
        assertThat(failed.isCompleted()).isFalse();
    }

    @Test
    void isFailedReturnsTrueOnlyForFailedStatus() {
        IdempotencyKey failed = createKey(IdempotencyStatus.FAILED);
        IdempotencyKey completed = createKey(IdempotencyStatus.COMPLETED);
        assertThat(failed.isFailed()).isTrue();
        assertThat(completed.isFailed()).isFalse();
    }

    @Test
    void hasResultReturnsTrueWhenResultReferenceOrPayloadPresent() {
        IdempotencyKey withRef = createKey(IdempotencyStatus.COMPLETED, "ref-1", null);
        IdempotencyKey withoutResult = createKey(IdempotencyStatus.PENDING, null, null);
        assertThat(withRef.hasResult()).isTrue();
        assertThat(withoutResult.hasResult()).isFalse();
    }

    @Test
    void isResultExpiredReturnsTrueWhenNowIsAfterResultExpiresAt() {
        Instant past = Instant.now().minusSeconds(60);
        IdempotencyKey key = new IdempotencyKey(
                UUID.randomUUID(), IdempotencyScope.IAM_AUTH, null, UUID.randomUUID(),
                "mutation-1", "PROVIDER_TOKEN_EXCHANGE", "fp",
                IdempotencyStatus.COMPLETED, UUID.randomUUID(), (short) 200, null,
                null, "ref-1", null, null, null,
                Instant.now().minusSeconds(120), Instant.now().minusSeconds(120), past,
                Instant.now().plusSeconds(300));
        assertThat(key.isResultExpired(Instant.now())).isTrue();
    }

    private IdempotencyKey createKey(IdempotencyStatus status) {
        return createKey(status, null, null);
    }

    private IdempotencyKey createKey(IdempotencyStatus status, String resultReference, String resultPayload) {
        return new IdempotencyKey(
                UUID.randomUUID(), IdempotencyScope.IAM_AUTH, null, UUID.randomUUID(),
                "mutation-1", "PROVIDER_TOKEN_EXCHANGE", "fp",
                status, UUID.randomUUID(), (short) 200, null,
                resultPayload, resultReference, null, null, null,
                Instant.now(), Instant.now(), Instant.now().plusSeconds(300),
                Instant.now().plusSeconds(300));
    }
}
