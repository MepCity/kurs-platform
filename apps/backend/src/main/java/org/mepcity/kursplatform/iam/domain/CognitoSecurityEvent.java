package org.mepcity.kursplatform.iam.domain;

import java.time.Instant;

/** Minimal, allow-listed CloudTrail security event; the raw delivery body is never retained. */
public record CognitoSecurityEvent(String userPoolId, String eventId, String eventName,
                                   String subject, Instant eventTime) {
    public CognitoSecurityEvent {
        if (!userPoolId.matches("[A-Za-z0-9_-]{3,128}")
                || !eventId.matches("[A-Za-z0-9._:-]{1,256}")
                || !eventName.matches("AdminDisableUser|AdminUserGlobalSignOut|RevokeToken|AdminResetUserPassword|AdminSetUserPassword")
                || !subject.matches("[A-Za-z0-9_-]{1,128}") || eventTime == null) {
            throw new IllegalArgumentException("Geçersiz Cognito güvenlik olayı.");
        }
    }
}
