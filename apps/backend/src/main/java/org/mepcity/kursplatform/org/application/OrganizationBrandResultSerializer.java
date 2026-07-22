package org.mepcity.kursplatform.org.application;

/** Safe ORG-005 response snapshot boundary for idempotency replay. */
public interface OrganizationBrandResultSerializer {
    String serialize(BrandSettings.Brand result);
    String serialize(BrandSettings.Palette result);
    String serialize(BrandSettings.Modules result);
    BrandSettings.Brand deserializeBrand(String payload);
    BrandSettings.Palette deserializePalette(String payload);
    BrandSettings.Modules deserializeModules(String payload);
}
