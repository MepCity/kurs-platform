package org.mepcity.kursplatform.org.api;

/** Typed pagination envelope for the organization collection. */
public record OrganizationListPageResponse(String nextCursor, boolean hasNextPage) { }
