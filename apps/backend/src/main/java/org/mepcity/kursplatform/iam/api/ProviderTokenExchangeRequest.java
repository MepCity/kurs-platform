package org.mepcity.kursplatform.iam.api;

import java.util.UUID;

public record ProviderTokenExchangeRequest(
        UUID deviceIdentifier,
        String platform,
        String deviceName
) {
}
