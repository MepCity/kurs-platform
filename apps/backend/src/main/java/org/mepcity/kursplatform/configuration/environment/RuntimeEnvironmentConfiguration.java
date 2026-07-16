package org.mepcity.kursplatform.configuration.environment;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

@Configuration
public class RuntimeEnvironmentConfiguration {

	@Bean
	RuntimeEnvironmentSettings runtimeEnvironmentSettings(Environment environment) {
		return RuntimeEnvironmentSettings.from(new SpringEnvironmentValues(environment));
	}

	private record SpringEnvironmentValues(Environment environment) implements RuntimeEnvironmentSettings.Values {

		@Override
		public String get(String key) {
			return environment.getProperty(key);
		}
	}
}
