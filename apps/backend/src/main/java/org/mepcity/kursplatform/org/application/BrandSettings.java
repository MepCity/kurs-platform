package org.mepcity.kursplatform.org.application;

import java.util.List;

/** API-facing immutable views for the six file-free ORG-005 endpoints. */
public final class BrandSettings {
    private BrandSettings() {}

    /** Logo remains null in file-free ORG-005. Palette has its own endpoint. */
    public record Brand(String primaryColor, String secondaryColor, int rowVersion, Object logo) {}
    public record Color(String colorHex, int sortOrder) {}
    public record Palette(int rowVersion, List<Color> items) {}
    public record Module(String moduleCode, boolean isEnabled, int sortOrder) {}
    /** PATCH DTO preserves absent values; response modules always contain both fields. */
    public record ModulePatch(String moduleCode, Boolean isEnabled, Integer sortOrder) {}
    public record Modules(int rowVersion, List<Module> items) {}
}
