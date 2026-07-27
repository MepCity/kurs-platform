package org.mepcity.kursplatform.org.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.mepcity.kursplatform.org.application.OrganizationListValidationException;
import org.mepcity.kursplatform.org.domain.OrganizationListQuery;

class OrganizationListQueryParserTests {

    @Test
    void appliesDocumentedDefaultsAndTracksWhetherAnyParameterWasSupplied() {
        var query = OrganizationListQueryParser.parse(null, null, null, null, null, null);

        assertThat(query.status()).isNull();
        assertThat(query.search()).isNull();
        assertThat(query.sort()).isEqualTo(OrganizationListQuery.Sort.NAME);
        assertThat(query.order()).isEqualTo(OrganizationListQuery.Order.ASC);
        assertThat(query.limit()).isEqualTo(50);
        assertThat(query.cursor()).isNull();
        assertThat(query.supplied()).isFalse();
    }

    @Test
    void normalizesTurkishIAndUnicodeBeforePostgresSearch() {
        assertThat(OrganizationListQueryParser.normalizeSearch(" İstanbul "))
                .isEqualTo("istanbul");
        assertThat(OrganizationListQueryParser.normalizeSearch(" IĞDIR "))
                .isEqualTo("ığdır");
        assertThat(OrganizationListQueryParser.normalizeSearch("  ")).isNull();
    }

    @Test
    void acceptsTwoHundredCharactersAndRejectsTwoHundredOne() {
        assertThat(OrganizationListQueryParser.normalizeSearch("a".repeat(200))).hasSize(200);
        assertThatThrownBy(
                        () ->
                                OrganizationListQueryParser.parse(
                                        null, "a".repeat(201), null, null, null, null))
                .isInstanceOf(OrganizationListValidationException.class);
    }

    @Test
    void rejectsUnknownEnumsAndOutOfRangeLimits() {
        assertThatThrownBy(
                        () ->
                                OrganizationListQueryParser.parse(
                                        null, null, "unknown", null, null, null))
                .isInstanceOf(OrganizationListValidationException.class);
        assertThatThrownBy(
                        () ->
                                OrganizationListQueryParser.parse(
                                        null, null, null, "DOWN", null, null))
                .isInstanceOf(OrganizationListValidationException.class);
        assertThatThrownBy(
                        () ->
                                OrganizationListQueryParser.parse(
                                        null, null, null, null, "0", null))
                .isInstanceOf(OrganizationListValidationException.class);
    }
}
