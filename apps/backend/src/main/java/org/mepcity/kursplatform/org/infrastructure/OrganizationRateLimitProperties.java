package org.mepcity.kursplatform.org.infrastructure;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Externalized ORG_CREATE quota; defaults deliberately favour normal admin setup workflows. */
@ConfigurationProperties(prefix = "org.create.rate-limit")
public class OrganizationRateLimitProperties {
    private int limit = 20;
    private Duration window = Duration.ofMinutes(1);
    private int brandLimit = 60;
    private Duration brandWindow = Duration.ofMinutes(1);
    public int getLimit() { return limit; }
    public void setLimit(int limit) { this.limit = limit; }
    public Duration getWindow() { return window; }
    public void setWindow(Duration window) { this.window = window; }
    public int getBrandLimit() { return brandLimit; }
    public void setBrandLimit(int brandLimit) { this.brandLimit = brandLimit; }
    public Duration getBrandWindow() { return brandWindow; }
    public void setBrandWindow(Duration brandWindow) { this.brandWindow = brandWindow; }
    public void validate() {
        if (limit < 1 || window == null || window.isZero() || window.isNegative()
                || window.toSeconds() < 1 || !window.minusSeconds(window.toSeconds()).isZero()) {
            throw new IllegalStateException("org.create.rate-limit limit and window must be positive");
        }
        if (brandLimit < 1 || brandWindow == null || brandWindow.isZero() || brandWindow.isNegative()
                || brandWindow.toSeconds() < 1 || !brandWindow.minusSeconds(brandWindow.toSeconds()).isZero()) {
            throw new IllegalStateException("org.create.rate-limit brand limit and window must be positive");
        }
    }
}
