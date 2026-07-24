package org.mepcity.kursplatform.org.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import org.junit.jupiter.api.Test;

class BrandColorValidatorTests {

    @Test
    void acceptsContractualColorsAndNormalizesToUpperCase() {
        assertThat(BrandColorValidator.validateBrandColor("primaryColor", "#1565c0")).isEqualTo("#1565C0");
        assertThat(BrandColorValidator.validateBrandColor("secondaryColor", "#E65100")).isEqualTo("#E65100");
    }

    @Test
    void rejectsForbiddenAndLowContrastColorsWithFieldSpecificCodes() {
        assertThatIllegalArgumentException().isThrownBy(() -> BrandColorValidator.validateBrandColor("primaryColor", "#FFFFFF"))
                .withMessage("primaryColor.BLACK_OR_WHITE");
        assertThatIllegalArgumentException().isThrownBy(() -> BrandColorValidator.validateBrandColor("secondaryColor", "#959595"))
                .withMessage("secondaryColor.GRAPHICAL_CONTRAST_NOT_PASSED");
        assertThatIllegalArgumentException().isThrownBy(() -> BrandColorValidator.validateBrandColor("primaryColor", "#abcd"))
                .withMessage("primaryColor.INVALID_HEX");
    }

    @Test
    void paletteColorsOnlyRequireHexFormat() {
        assertThat(BrandColorValidator.validatePaletteColor("#ffffff")).isEqualTo("#FFFFFF");
        assertThatIllegalArgumentException().isThrownBy(() -> BrandColorValidator.validatePaletteColor("blue"))
                .withMessage("colorHex.INVALID_HEX");
    }
}
