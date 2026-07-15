package org.mepcity.kursplatform.configuration.observability;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.mepcity.kursplatform.core.observability.SafeLogEvent;
import org.mepcity.kursplatform.core.observability.SafeLogEvent.SafeLogOperation;
import org.slf4j.Logger;
import org.slf4j.spi.LoggingEventBuilder;

class Slf4jSafeEventLoggerTests {

	@Test
	void mapsEverySeverityToExplicitSlf4jLevel() {
		Logger logger = mock(Logger.class);
		LoggingEventBuilder info = mock(LoggingEventBuilder.class);
		LoggingEventBuilder warning = mock(LoggingEventBuilder.class);
		LoggingEventBuilder error = mock(LoggingEventBuilder.class);
		when(logger.atInfo()).thenReturn(info);
		when(logger.atWarn()).thenReturn(warning);
		when(logger.atError()).thenReturn(error);
		Slf4jSafeEventLogger adapter = new Slf4jSafeEventLogger(logger);
		Instant now = Instant.parse("2026-07-15T10:00:00Z");

		adapter.log(SafeLogEvent.httpRequestCompleted(now, "req-1", "GET", "/health", 200, 1));
		adapter.log(SafeLogEvent.httpRequestCompleted(now, "req-2", "GET", "/health", 400, 1));
		adapter.log(SafeLogEvent.httpRequestCompleted(now, "req-3", "GET", "/health", 500, 1));
		adapter.log(SafeLogEvent.fatalApplicationError(
				now, "req-4", SafeLogOperation.HTTP_REQUEST, new IllegalStateException()));

		verify(logger).atInfo();
		verify(logger).atWarn();
		verify(logger, times(2)).atError();
		verify(info).addKeyValue("severity", "INFO");
		verify(warning).addKeyValue("severity", "WARNING");
		verify(error).addKeyValue("severity", "ERROR");
		verify(error).addKeyValue("severity", "FATAL");
		verify(info).log("http.request.completed");
		verify(warning).log("http.request.completed");
		verify(error).log("http.request.completed");
		verify(error).log("application.error.unexpected");
	}
}
