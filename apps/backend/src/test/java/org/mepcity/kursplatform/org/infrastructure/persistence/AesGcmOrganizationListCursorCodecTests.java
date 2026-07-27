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
import org.mepcity.kursplatform.org.domain.OrganizationStatus;
import org.mepcity.kursplatform.org.infrastructure.OrganizationListCursorProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

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
        assertThatThrownBy(
                        () ->
                                codec.decode(
                                        encoded,
                                        new OrganizationListCursor.Context(
                                                ACTOR,
                                                OrganizationListTransaction.Scope.ORGANIZATION,
                                                null,
                                                "kurs",
                                                OrganizationListQuery.Sort.NAME,
                                                OrganizationListQuery.Order.ASC,
                                                20)))
                .isInstanceOf(InvalidCursorException.class);
        assertThatThrownBy(
                        () ->
                                codec.decode(
                                        encoded,
                                        context(
                                                ACTOR,
                                                OrganizationStatus.ACTIVE,
                                                "kurs",
                                                OrganizationListQuery.Sort.NAME,
                                                OrganizationListQuery.Order.ASC,
                                                20)))
                .isInstanceOf(InvalidCursorException.class);
        assertThatThrownBy(
                        () ->
                                codec.decode(
                                        encoded,
                                        context(
                                                ACTOR,
                                                null,
                                                "kurs",
                                                OrganizationListQuery.Sort.NAME,
                                                OrganizationListQuery.Order.DESC,
                                                20)))
                .isInstanceOf(InvalidCursorException.class);
    }

    @Test
    void rejectsTamperingAndExpiry() {
        var context = context(ACTOR, null, null, OrganizationListQuery.Sort.CREATED_AT, OrganizationListQuery.Order.DESC, 10);
        var cursor = new OrganizationListCursor(context, new OrganizationListCursor.CreatedAt(NOW), UUID.randomUUID(), NOW.plusSeconds(1));
        String encoded = codec(NOW).encode(cursor);
        String[] parts = encoded.split("\\.", -1);
        byte[] bytes = java.util.Base64.getUrlDecoder().decode(parts[1]);
        bytes[bytes.length - 1] ^= 1;
        String tampered = parts[0] + "." + java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);

        assertThatThrownBy(() -> codec(NOW).decode(tampered, context)).isInstanceOf(InvalidCursorException.class);
        assertThatThrownBy(() -> codec(NOW.plusSeconds(1)).decode(encoded, context)).isInstanceOf(InvalidCursorException.class);
        assertThatThrownBy(() -> codec(NOW).decode("v1.a", context)).isInstanceOf(InvalidCursorException.class);
        assertThatThrownBy(() -> codec(NOW).decode("not-base64.%%%", context))
                .isInstanceOf(InvalidCursorException.class);
        assertThatThrownBy(() -> codec(NOW).decode("unknown." + parts[1], context))
                .isInstanceOf(InvalidCursorException.class);
        assertThatThrownBy(() -> codec(NOW).decode(encoded.substring(0, encoded.length() - 2), context))
                .isInstanceOf(InvalidCursorException.class);
        assertThatThrownBy(() -> codec(NOW).decode("x".repeat(4097), context)).isInstanceOf(InvalidCursorException.class);

        String expired =
                codec(NOW)
                        .encode(
                                new OrganizationListCursor(
                                        context,
                                        new OrganizationListCursor.CreatedAt(NOW),
                                        UUID.randomUUID(),
                                        NOW.minusMillis(1)));
        assertThatThrownBy(() -> codec(NOW).decode(expired, context))
                .isInstanceOf(InvalidCursorException.class);
    }

    @Test
    void supportsRestartAndConfiguredPreviousKeyOnly() {
        var context = context(ACTOR, null, "I|İ_%\\", OrganizationListQuery.Sort.NAME, OrganizationListQuery.Order.ASC, 10);
        var current = new AesGcmOrganizationListCursorCodec("v2", "current-cursor-secret-with-at-least-32", "v1",
                "previous-cursor-secret-with-at-least32", Clock.fixed(NOW, ZoneOffset.UTC), new SecureRandom());
        var previous = new AesGcmOrganizationListCursorCodec("v1", "previous-cursor-secret-with-at-least32", null, null,
                Clock.fixed(NOW, ZoneOffset.UTC), new SecureRandom());
        var value = new OrganizationListCursor(context, new OrganizationListCursor.Name("İ|_%\\"), UUID.randomUUID(), NOW.plusSeconds(60));
        String oldCursor = previous.encode(value);

        assertThat(current.decode(oldCursor, context)).isEqualTo(value);
        assertThat(new AesGcmOrganizationListCursorCodec("v2", "other-cursor-secret-with-at-least-32", null, null,
                Clock.fixed(NOW, ZoneOffset.UTC), new SecureRandom()).encode(value)).doesNotContain("İ|_%\\");
        assertThatThrownBy(() -> new AesGcmOrganizationListCursorCodec("v2", "other-cursor-secret-with-at-least-32", null, null,
                Clock.fixed(NOW, ZoneOffset.UTC), new SecureRandom()).decode(oldCursor, context)).isInstanceOf(InvalidCursorException.class);
    }

    @Test
    void independentInstancesExchangeCurrentKeyTokensInBothDirections() {
        var context =
                context(
                        ACTOR,
                        OrganizationStatus.ACTIVE,
                        "kurs",
                        OrganizationListQuery.Sort.NAME,
                        OrganizationListQuery.Order.ASC,
                        10);
        var first = codec(NOW);
        var second = codec(NOW);
        var value =
                new OrganizationListCursor(
                        context,
                        new OrganizationListCursor.Name("Kurs"),
                        UUID.randomUUID(),
                        NOW.plusSeconds(60));

        assertThat(second.decode(first.encode(value), context)).isEqualTo(value);
        assertThat(first.decode(second.encode(value), context)).isEqualTo(value);
        assertThat(first.encode(value)).startsWith("test-v1.");
    }

    @Test
    void springContextFailsFastForMissingShortOrPartialKeyConfiguration() {
        assertContextFails("org.list.cursor.key-id=v2");
        assertContextFails(
                "org.list.cursor.key-id=v2",
                "org.list.cursor.secret=short");
        assertContextFails(
                "org.list.cursor.key-id=v2",
                "org.list.cursor.secret=current-cursor-secret-with-at-least-32",
                "org.list.cursor.previous-key-id=v1");
        assertContextFails(
                "org.list.cursor.key-id=v2",
                "org.list.cursor.secret=current-cursor-secret-with-at-least-32",
                "org.list.cursor.previous-secret=previous-cursor-secret-with-at-least32");
        assertContextFails(
                "org.list.cursor.key-id=v2",
                "org.list.cursor.secret=current-cursor-secret-with-at-least-32",
                "org.list.cursor.previous-key-id=v1",
                "org.list.cursor.previous-secret=short");
    }

    private static AesGcmOrganizationListCursorCodec codec(Instant now) {
        return new AesGcmOrganizationListCursorCodec("test-v1", "cursor-test-secret-with-at-least-32-bytes", null, null,
                Clock.fixed(now, ZoneOffset.UTC), new SecureRandom());
    }

    private static OrganizationListCursor.Context context(
            UUID actor,
            OrganizationStatus status,
            String search,
            OrganizationListQuery.Sort sort,
            OrganizationListQuery.Order order,
            int limit) {
        return new OrganizationListCursor.Context(
                actor,
                OrganizationListTransaction.Scope.GLOBAL,
                status,
                search,
                sort,
                order,
                limit);
    }

    private static void assertContextFails(String... properties) {
        new ApplicationContextRunner()
                .withUserConfiguration(CursorValidationConfiguration.class)
                .withPropertyValues(properties)
                .run(context -> assertThat(context.getStartupFailure()).isNotNull());
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(OrganizationListCursorProperties.class)
    static class CursorValidationConfiguration {

        @Bean
        Object validatedCursorProperties(OrganizationListCursorProperties properties) {
            properties.validate();
            return new Object();
        }
    }
}
