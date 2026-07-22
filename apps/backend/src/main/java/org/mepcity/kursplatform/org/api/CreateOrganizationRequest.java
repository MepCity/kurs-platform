package org.mepcity.kursplatform.org.api;

/** Contract body for {@code POST /api/v1/organizations}. */
public record CreateOrganizationRequest(String name, String shortName, String defaultTimezone) {
}
