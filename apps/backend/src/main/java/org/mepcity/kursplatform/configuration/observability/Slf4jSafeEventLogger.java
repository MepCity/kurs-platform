package org.mepcity.kursplatform.configuration.observability;

import org.mepcity.kursplatform.core.observability.SafeEventLogger;
import org.mepcity.kursplatform.core.observability.SafeLogEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.spi.LoggingEventBuilder;
import org.springframework.stereotype.Component;

/** Emits only fields that have passed {@link SafeLogEvent}'s allow-list. */
@Component
public final class Slf4jSafeEventLogger implements SafeEventLogger {

	private final Logger logger;

	public Slf4jSafeEventLogger() {
		this(LoggerFactory.getLogger("kurs-platform.observability"));
	}

	Slf4jSafeEventLogger(Logger logger) {
		this.logger = logger;
	}

	@Override
	public void log(SafeLogEvent event) {
		LoggingEventBuilder builder = switch (event.severity()) {
			case INFO -> logger.atInfo();
			case WARNING -> logger.atWarn();
			case ERROR, FATAL -> logger.atError();
		};
		builder.addKeyValue("event", event.name());
		builder.addKeyValue("severity", event.severity().name());
		builder.addKeyValue("occurredAt", event.occurredAt());
		event.fields().forEach(builder::addKeyValue);
		builder.log(event.name());
	}
}
