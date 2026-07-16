package org.mepcity.kursplatform.configuration.environment;

public enum RuntimeEnvironment {
	DEVELOPMENT("development"),
	STAGING("staging"),
	PRODUCTION("production");

	private final String externalName;

	RuntimeEnvironment(String externalName) {
		this.externalName = externalName;
	}

	public String externalName() {
		return externalName;
	}

	public static RuntimeEnvironment from(String value) {
		for (RuntimeEnvironment environment : values()) {
			if (environment.externalName.equals(value)) {
				return environment;
			}
		}
		throw new IllegalArgumentException(
				"KURS_PLATFORM_ENVIRONMENT must be one of development, staging or production");
	}
}
