package org.mepcity.kursplatform.org.application;

/** Opaque, authenticated cursor boundary. Implementations must never expose plaintext fields. */
public interface OrganizationListCursorCodec {
    String encode(OrganizationListCursor cursor);
    OrganizationListCursor decode(String token, OrganizationListCursor.Context expected);
}
