package org.mepcity.kursplatform.org.api;

import java.util.List;

/** Typed collection response; avoids exposing mutable map representations at the HTTP boundary. */
public record OrganizationListResponse(List<OrganizationResponse> items, OrganizationListPageResponse page) { }
