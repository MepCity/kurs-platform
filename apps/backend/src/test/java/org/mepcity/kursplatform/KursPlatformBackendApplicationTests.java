package org.mepcity.kursplatform;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.ActiveProfiles;

@ActiveProfiles("test")
@SpringBootTest(classes = KursPlatformBackendApplicationTests.DbFreeApplication.class, properties = {
		"KURS_PLATFORM_ENVIRONMENT=development",
		"KURS_PLATFORM_PUBLIC_API_BASE_URL=https://api-development.example.invalid",
		"KURS_PLATFORM_COGNITO_ISSUER_URI=https://cognito-idp.eu-central-1.amazonaws.com/eu-central-1_EXAMPLE",
		"KURS_PLATFORM_COGNITO_CLIENT_ID=examplepublicclientid",
		"KURS_PLATFORM_DATABASE_URL_SECRET_REF=development/platform/database-url",
		"KURS_PLATFORM_IAM_TOKEN_PEPPER_SECRET_REF=development/platform/iam-token-pepper",
		"KURS_PLATFORM_IAM_SECRET_DELIVERY_KEY_REF=development/platform/iam-secret-delivery-key",
		"KURS_PLATFORM_COGNITO_ADMIN_ROLE_REF=development/platform/cognito-admin-role",
		"spring.autoconfigure.exclude=org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration"
})
class KursPlatformBackendApplicationTests {

    @Configuration
    @EnableAutoConfiguration(exclude = org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration.class)
    static class DbFreeApplication { }

	@Test
	void contextLoads() {
	}

}
