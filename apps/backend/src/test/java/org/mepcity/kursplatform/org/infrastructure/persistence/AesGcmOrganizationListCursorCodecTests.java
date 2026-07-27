package org.mepcity.kursplatform.org.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mepcity.kursplatform.org.application.InvalidCursorException;
import org.mepcity.kursplatform.org.application.OrganizationListCursor;
import org.mepcity.kursplatform.org.application.OrganizationListTransaction;
import org.mepcity.kursplatform.org.domain.OrganizationListQuery;

class AesGcmOrganizationListCursorCodecTests {
    private static final Instant NOW = Instant.parse("2026-07-27T12:00:00Z");
    private static final UUID ACTOR = UUID.fromString("00000000-0000-0000-0000-000000000001");

    @Test
    void encryptsAndBindsEveryListContext() {
        var codec = codec(NOW);
        var context = context(ACTOR, null, "kurs", OrganizationListQuery.Sort.NAME, OrganizationListQuery.Order.ASC, 20);
        var expected = new OrganizationListCursor(context, new OrganizationListCursor.Name("Kurs A"), UUID.randomUUID(), NOW.plusSeconds(60));

        String encoded = codec.encode(expected);

        assertThat(encoded).doesNotContain("Kurs A").doesNotContain(ACTOR.toString());
        assertThat(codec.decode(encoded, context)).isEqualTo(expected);
        assertThatThrownBy(() -> codec.decode(encoded, context(UUID.randomUUID(), null, "kurs", OrganizationListQuery.Sort.NAME, OrganizationListQuery.Order.ASC, 20)))
                .isInstanceOf(InvalidCursorException.class);
        assertThatThrownBy(() -> codec.decode(encoded, context(ACTOR, null, "other", OrganizationListQuery.Sort.NAME, OrganizationListQuery.Order.ASC, 20)))
                .isInstanceOf(InvalidCursorException.class);
        assertThatThrownBy(() -> codec.decode(encoded, context(ACTOR, null, "kurs", OrganizationListQuery.Sort.CREATED_AT, OrganizationListQuery.Order.DESC, 20)))
                .isInstanceOf(InvalidCursorException.class);
        assertThatThrownBy(() -> codec.decode(encoded, context(ACTOR, null, "kurs", OrganizationListQuery.Sort.NAME, OrganizationListQuery.Order.ASC, 21)))
                .isInstanceOf(InvalidCursorException.class);
    }

    @Test
    void rejectsTamperingAndExpiry() {
        var context = context(ACTOR, null, null, OrganizationListQuery.Sort.CREATED_AT, OrganizationListQuery.Order.DESC, 10);
        var cursor = new OrganizationListCursor(context, new OrganizationListCursor.CreatedAt(NOW), UUID.randomUUID(), NOW.plusSeconds(1));
        String encoded = codec(NOW).encode(cursor);
        byte[] bytes = java.util.Base64.getUrlDecoder().decode(encoded);
        bytes[bytes.length - 1] ^= 1;
        String tampered = java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);

        assertThatThrownBy(() -> codec(NOW).decode(tampered, context)).isInstanceOf(InvalidCursorException.class);
        assertThatThrownBy(() -> codec(NOW.plusSeconds(2)).decode(encoded, context)).isInstanceOf(InvalidCursorException.class);
    }

    private static AesGcmOrganizationListCursorCodec codec(Instant now) {
        return new AesGcmOrganizationListCursorCodec("cursor-test-secret", Clock.fixed(now, ZoneOffset.UTC), new SecureRandom());
    }

    private static OrganizationListCursor.Context context(UUID actor, org.mepcity.kursplatform.org.domain.OrganizationStatus status,
            String search, OrganizationListQuery.Sort sort, OrganizationListQuery.Order order, int limit) {
        return new OrganizationListCursor.Context(actor, OrganizationListTransaction.Scope.GLOBAL, status, search, sort, order, limit);
    }
}
