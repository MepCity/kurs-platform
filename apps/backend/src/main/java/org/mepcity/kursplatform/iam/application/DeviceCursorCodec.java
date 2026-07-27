package org.mepcity.kursplatform.iam.application;

import java.time.Instant;
import java.util.UUID;

/** Application port for opaque, actor- and query-bound device-list cursors. */
public interface DeviceCursorCodec {
    String encode(UUID actor, int limit, Instant trustedAt, UUID id);

    Cursor decode(UUID actor, int limit, String cursor);

    record Cursor(Instant trustedAt, UUID id) { }
}
