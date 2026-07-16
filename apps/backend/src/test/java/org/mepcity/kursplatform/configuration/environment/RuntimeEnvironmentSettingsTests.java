package org.mepcity.kursplatform.configuration.environment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class RuntimeEnvironmentSettingsTests {

	@Test
	void acceptsSyntheticDevelopmentConfiguration() {
		RuntimeEnvironmentSettings settings = RuntimeEnvironmentSettings.from(values(Map.of(
				"KURS_PLATFORM_ENVIRONMENT", "development",
				"KURS_PLATFORM_PUBLIC_API_BASE_URL", "https://api-development.example.invalid",
				"KURS_PLATFORM_COGNITO_ISSUER_URI",
						"https://cognito-idp.eu-central-1.amazonaws.com/eu-central-1_EXAMPLE",
				"KURS_PLATFORM_COGNITO_CLIENT_ID", "examplepublicclientid",
				"KURS_PLATFORM_DATABASE_URL_SECRET_REF", "development/platform/database-url",
				"KURS_PLATFORM_IAM_TOKEN_PEPPER_SECRET_REF", "development/platform/iam-pepper-ref",
				"KURS_PLATFORM_IAM_SECRET_DELIVERY_KEY_REF", "development/platform/iam-delivery-key-ref",
				"KURS_PLATFORM_COGNITO_ADMIN_ROLE_REF", "development/platform/cognito-admin-role-ref")));

		assertThat(settings.environment()).isEqualTo(RuntimeEnvironment.DEVELOPMENT);
		assertThat(settings.publicApiBaseUrl().getHost()).isEqualTo("api-development.example.invalid");
	}

	@Test
	void rejectsShortEnvironmentAlias() {
		assertThatThrownBy(() -> RuntimeEnvironmentSettings.from(values(Map.of(
				"KURS_PLATFORM_ENVIRONMENT", "prod"))))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("development, staging or production");
	}

	@Test
	void rejectsProductionHttpUrl() {
		Map<String, String> values = validProductionValues();
		values.put("KURS_PLATFORM_PUBLIC_API_BASE_URL", "http://api.example.invalid");

		assertThatThrownBy(() -> RuntimeEnvironmentSettings.from(values(values)))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("HTTPS");
	}

	@Test
	void allowsOnlyLocalHttpInDevelopment() {
		Map<String, String> values = validDevelopmentValues();
		values.put("KURS_PLATFORM_PUBLIC_API_BASE_URL", "http://localhost:8080");

		RuntimeEnvironmentSettings settings = RuntimeEnvironmentSettings.from(values(values));

		assertThat(settings.publicApiBaseUrl().getHost()).isEqualTo("localhost");
	}

	@Test
	void rejectsSecretReferenceFromAnotherEnvironment() {
		Map<String, String> values = validProductionValues();
		values.put("KURS_PLATFORM_DATABASE_URL_SECRET_REF", "development/platform/database-url");

		assertThatThrownBy(() -> RuntimeEnvironmentSettings.from(values(values)))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("production/");
	}

	@Test
	void rejectsRawSecretLookingReference() {
		Map<String, String> values = validDevelopmentValues();
		values.put("KURS_PLATFORM_IAM_TOKEN_PEPPER_SECRET_REF", "development/platform/password=cleartext");

		assertThatThrownBy(() -> RuntimeEnvironmentSettings.from(values(values)))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("secret reference");
	}

	private static RuntimeEnvironmentSettings.Values values(Map<String, String> values) {
		return values::get;
	}

	private static Map<String, String> validDevelopmentValues() {
		return new HashMap<>(Map.of(
				"KURS_PLATFORM_ENVIRONMENT", "development",
				"KURS_PLATFORM_PUBLIC_API_BASE_URL", "https://api-development.example.invalid",
				"KURS_PLATFORM_COGNITO_ISSUER_URI",
						"https://cognito-idp.eu-central-1.amazonaws.com/eu-central-1_EXAMPLE",
				"KURS_PLATFORM_COGNITO_CLIENT_ID", "examplepublicclientid",
				"KURS_PLATFORM_DATABASE_URL_SECRET_REF", "development/platform/database-url",
				"KURS_PLATFORM_IAM_TOKEN_PEPPER_SECRET_REF", "development/platform/iam-pepper-ref",
				"KURS_PLATFORM_IAM_SECRET_DELIVERY_KEY_REF", "development/platform/iam-delivery-key-ref",
				"KURS_PLATFORM_COGNITO_ADMIN_ROLE_REF", "development/platform/cognito-admin-role-ref"));
	}

	private static Map<String, String> validProductionValues() {
		return new HashMap<>(Map.of(
				"KURS_PLATFORM_ENVIRONMENT", "production",
				"KURS_PLATFORM_PUBLIC_API_BASE_URL", "https://api.example.invalid",
				"KURS_PLATFORM_COGNITO_ISSUER_URI",
						"https://cognito-idp.eu-central-1.amazonaws.com/eu-central-1_EXAMPLE",
				"KURS_PLATFORM_COGNITO_CLIENT_ID", "examplepublicclientid",
				"KURS_PLATFORM_DATABASE_URL_SECRET_REF", "production/platform/database-url",
				"KURS_PLATFORM_IAM_TOKEN_PEPPER_SECRET_REF", "production/platform/iam-pepper-ref",
				"KURS_PLATFORM_IAM_SECRET_DELIVERY_KEY_REF", "production/platform/iam-delivery-key-ref",
				"KURS_PLATFORM_COGNITO_ADMIN_ROLE_REF", "production/platform/cognito-admin-role-ref"));
	}
}
