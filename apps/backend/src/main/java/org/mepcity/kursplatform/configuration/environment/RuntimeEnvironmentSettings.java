package org.mepcity.kursplatform.configuration.environment;

import java.net.URI;
import java.util.Locale;
import java.util.Objects;

public record RuntimeEnvironmentSettings(
		RuntimeEnvironment environment,
		URI publicApiBaseUrl,
		URI cognitoIssuerUri,
		String cognitoClientId,
		SecretReference databaseUrlSecretRef,
		SecretReference iamTokenPepperSecretRef,
		SecretReference iamSecretDeliveryKeyRef,
		SecretReference cognitoAdminRoleRef) {

	public RuntimeEnvironmentSettings {
		Objects.requireNonNull(environment, "environment is required");
		Objects.requireNonNull(publicApiBaseUrl, "publicApiBaseUrl is required");
		Objects.requireNonNull(cognitoIssuerUri, "cognitoIssuerUri is required");
		cognitoClientId = requireText("KURS_PLATFORM_COGNITO_CLIENT_ID", cognitoClientId);
		Objects.requireNonNull(databaseUrlSecretRef, "databaseUrlSecretRef is required");
		Objects.requireNonNull(iamTokenPepperSecretRef, "iamTokenPepperSecretRef is required");
		Objects.requireNonNull(iamSecretDeliveryKeyRef, "iamSecretDeliveryKeyRef is required");
		Objects.requireNonNull(cognitoAdminRoleRef, "cognitoAdminRoleRef is required");

		validatePublicUri("KURS_PLATFORM_PUBLIC_API_BASE_URL", publicApiBaseUrl, environment);
		validatePublicUri("KURS_PLATFORM_COGNITO_ISSUER_URI", cognitoIssuerUri, environment);
	}

	public static RuntimeEnvironmentSettings from(Values values) {
		RuntimeEnvironment environment = RuntimeEnvironment.from(requireText(
				"KURS_PLATFORM_ENVIRONMENT",
				values.get("KURS_PLATFORM_ENVIRONMENT")));
		return new RuntimeEnvironmentSettings(
				environment,
				parseUri("KURS_PLATFORM_PUBLIC_API_BASE_URL", values.get("KURS_PLATFORM_PUBLIC_API_BASE_URL")),
				parseUri("KURS_PLATFORM_COGNITO_ISSUER_URI", values.get("KURS_PLATFORM_COGNITO_ISSUER_URI")),
				values.get("KURS_PLATFORM_COGNITO_CLIENT_ID"),
				SecretReference.from(environment, "KURS_PLATFORM_DATABASE_URL_SECRET_REF",
						values.get("KURS_PLATFORM_DATABASE_URL_SECRET_REF")),
				SecretReference.from(environment, "KURS_PLATFORM_IAM_TOKEN_PEPPER_SECRET_REF",
						values.get("KURS_PLATFORM_IAM_TOKEN_PEPPER_SECRET_REF")),
				SecretReference.from(environment, "KURS_PLATFORM_IAM_SECRET_DELIVERY_KEY_REF",
						values.get("KURS_PLATFORM_IAM_SECRET_DELIVERY_KEY_REF")),
				SecretReference.from(environment, "KURS_PLATFORM_COGNITO_ADMIN_ROLE_REF",
						values.get("KURS_PLATFORM_COGNITO_ADMIN_ROLE_REF")));
	}

	private static String requireText(String key, String value) {
		if (value == null || value.isBlank()) {
			throw new IllegalArgumentException(key + " is required");
		}
		return value.trim();
	}

	private static URI parseUri(String key, String value) {
		String required = requireText(key, value);
		try {
			return URI.create(required);
		} catch (IllegalArgumentException exception) {
			throw new IllegalArgumentException(key + " must be a valid URI", exception);
		}
	}

	private static void validatePublicUri(String key, URI uri, RuntimeEnvironment environment) {
		String scheme = uri.getScheme();
		String host = uri.getHost();
		if (scheme == null || host == null) {
			throw new IllegalArgumentException(key + " must include scheme and host");
		}
		if ("https".equals(scheme)) {
			return;
		}
		boolean localDevelopmentHttp = environment == RuntimeEnvironment.DEVELOPMENT
				&& "http".equals(scheme)
				&& ("localhost".equals(host) || "127.0.0.1".equals(host) || "::1".equals(host));
		if (!localDevelopmentHttp) {
			throw new IllegalArgumentException(key + " must use HTTPS outside local development");
		}
	}

	public interface Values {
		String get(String key);
	}

	public record SecretReference(String value) {

		public SecretReference {
			value = requireText("secret reference", value);
		}

		private static SecretReference from(RuntimeEnvironment environment, String key, String value) {
			String reference = requireText(key, value);
			String lower = reference.toLowerCase(Locale.ROOT);
			if (!lower.startsWith(environment.externalName() + "/")) {
				throw new IllegalArgumentException(key + " must start with " + environment.externalName() + "/");
			}
			if (reference.contains("://") || reference.contains("=") || reference.chars().anyMatch(Character::isWhitespace)) {
				throw new IllegalArgumentException(key + " must be a secret reference, not a raw value");
			}
			return new SecretReference(reference);
		}
	}
}
