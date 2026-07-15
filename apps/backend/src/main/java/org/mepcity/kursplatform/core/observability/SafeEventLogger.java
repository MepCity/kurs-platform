package org.mepcity.kursplatform.core.observability;

/** Provider adapter boundary for safe diagnostic events. */
@FunctionalInterface
public interface SafeEventLogger {

	void log(SafeLogEvent event);
}
