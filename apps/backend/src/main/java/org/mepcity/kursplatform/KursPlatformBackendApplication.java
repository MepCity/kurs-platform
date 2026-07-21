package org.mepcity.kursplatform;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * {@link EnableScheduling} is required so the IAM provider-command scheduler's {@code @Scheduled}
 * poll is actually invoked by Spring's TaskScheduler. It is harmless when the worker bean is absent
 * (the default — disabled via {@code iam.provider-command.worker.enabled} unless a deployment
 * explicitly opts in): no {@code @Scheduled} method is registered, so the scheduler thread pool
 * stays idle.
 */
@SpringBootApplication
@EnableScheduling
public class KursPlatformBackendApplication {

	public static void main(String[] args) {
		SpringApplication.run(KursPlatformBackendApplication.class, args);
	}

}
