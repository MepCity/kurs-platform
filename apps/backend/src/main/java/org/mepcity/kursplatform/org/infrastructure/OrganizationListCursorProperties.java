package org.mepcity.kursplatform.org.infrastructure;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Separate cursor key material; it must never reuse IAM token hashing secrets. */
@ConfigurationProperties(prefix = "org.list.cursor")
public class OrganizationListCursorProperties {
    private String keyId = "v1";
    private String secret;
    private String previousKeyId;
    private String previousSecret;

    public String getKeyId() {
        return keyId;
    }

    public void setKeyId(String keyId) {
        this.keyId = keyId;
    }

    public String getSecret() {
        return secret;
    }

    public void setSecret(String secret) {
        this.secret = secret;
    }

    public String getPreviousKeyId() {
        return previousKeyId;
    }

    public void setPreviousKeyId(String previousKeyId) {
        this.previousKeyId = previousKeyId;
    }

    public String getPreviousSecret() {
        return previousSecret;
    }

    public void setPreviousSecret(String previousSecret) {
        this.previousSecret = previousSecret;
    }

    public void validate() {
        if (keyId == null || keyId.isBlank() || secret == null || secret.length() < 32) {
            throw new IllegalStateException("org.list.cursor keyId ve en az 32 karakter secret zorunludur");
        }
        boolean hasPreviousId = previousKeyId != null && !previousKeyId.isBlank();
        boolean hasPreviousSecret = previousSecret != null && !previousSecret.isBlank();
        if (hasPreviousId != hasPreviousSecret || (hasPreviousSecret && previousSecret.length() < 32)) {
            throw new IllegalStateException("Önceki ORG_LIST cursor anahtarı eksiksiz olmalıdır");
        }
    }
}
