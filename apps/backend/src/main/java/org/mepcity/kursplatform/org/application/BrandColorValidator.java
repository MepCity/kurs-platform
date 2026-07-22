package org.mepcity.kursplatform.org.application;

import java.util.Locale;
import java.util.regex.Pattern;

/** Contractual #RRGGBB and WCAG AA validation for primary/secondary brand colors. */
public final class BrandColorValidator {
    private static final Pattern HEX = Pattern.compile("^#[0-9A-Fa-f]{6}$");
    private BrandColorValidator() {}

    public static String validateBrandColor(String field, String value) {
        if (value == null || !HEX.matcher(value).matches()) throw new IllegalArgumentException(field + ".INVALID_HEX");
        String normalized = value.toUpperCase(Locale.ROOT);
        if (normalized.equals("#000000") || normalized.equals("#FFFFFF")) throw new IllegalArgumentException(field + ".BLACK_OR_WHITE");
        double luminance = luminance(normalized);
        double onContrast = Math.max((1.0 + 0.05) / (luminance + 0.05), (luminance + 0.05) / 0.05);
        if (onContrast < 4.5d) throw new IllegalArgumentException(field + ".CONTRAST_NOT_PASSED");
        double neutralContrast = (1.0 + 0.05) / (luminance + 0.05);
        if (neutralContrast < 3.0d) throw new IllegalArgumentException(field + ".GRAPHICAL_CONTRAST_NOT_PASSED");
        return normalized;
    }

    public static String validatePaletteColor(String value) {
        if (value == null || !HEX.matcher(value).matches()) throw new IllegalArgumentException("colorHex.INVALID_HEX");
        return value.toUpperCase(Locale.ROOT);
    }

    private static double luminance(String hex) {
        double[] rgb = {Integer.parseInt(hex.substring(1, 3), 16) / 255d,
                Integer.parseInt(hex.substring(3, 5), 16) / 255d, Integer.parseInt(hex.substring(5, 7), 16) / 255d};
        for (int i = 0; i < 3; i++) rgb[i] = rgb[i] <= .04045 ? rgb[i] / 12.92 : Math.pow((rgb[i] + .055) / 1.055, 2.4);
        return .2126 * rgb[0] + .7152 * rgb[1] + .0722 * rgb[2];
    }
}
