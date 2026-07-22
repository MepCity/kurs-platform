package org.mepcity.kursplatform.org.infrastructure;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Externalized ORG_CREATE quota; defaults deliberately favour normal admin setup workflows. */
@ConfigurationProperties(prefix = "org.create.rate-limit")
public class OrganizationRateLimitProperties {
    private int limit = 20;
    private Duration window = Duration.ofMinutes(1);
    public int getLimit() { return limit; }
    public void setLimit(int limit) { this.limit = limit; }
    public Duration getWindow() { return window; }
    public void setWindow(Duration window) { this.window = window; }
    public void validate() {
        if (limit < 1 || window == null || window.isZero() || window.isNegative()
                || window.toSeconds() < 1 || !window.minusSeconds(window.toSeconds()).isZero()) {
            throw new IllegalStateException("org.create.rate-limit limit and window must be positive");
        }
    }
}
