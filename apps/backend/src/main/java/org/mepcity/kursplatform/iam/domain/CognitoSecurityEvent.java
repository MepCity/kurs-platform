package org.mepcity.kursplatform.iam.domain;

import java.time.Instant;

/** Minimal, allow-listed CloudTrail security event; the raw delivery body is never retained. */
public record CognitoSecurityEvent(String userPoolId, String eventId, String eventName,
                                   String subject, Instant eventTime) {
    private static final int MAX_SUBJECT_CODE_POINTS = 512;

    public CognitoSecurityEvent {
        if (!userPoolId.matches("[A-Za-z0-9_-]{3,128}")
                || !eventId.matches("[A-Za-z0-9._:-]{1,256}")
                || !eventName.matches("AdminDisableUser|AdminUserGlobalSignOut|AdminResetUserPassword|AdminSetUserPassword")
                || !validSubject(subject)
                || eventTime == null) {
            throw new IllegalArgumentException("Geçersiz Cognito güvenlik olayı.");
        }
    }

    private static boolean validSubject(String value) {
        if (value == null || value.isBlank()
                || value.codePointCount(0, value.length()) > MAX_SUBJECT_CODE_POINTS) {
            return false;
        }
        for (int offset = 0; offset < value.length();) {
            int codePoint = value.codePointAt(offset);
            if (Character.isISOControl(codePoint)) {
                return false;
            }
            offset += Character.charCount(codePoint);
        }
        return true;
    }
}
