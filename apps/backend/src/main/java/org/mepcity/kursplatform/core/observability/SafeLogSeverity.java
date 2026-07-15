package org.mepcity.kursplatform.core.observability;

/** Provider-independent severity. FATAL is transported through SLF4J at ERROR level. */
public enum SafeLogSeverity {
	INFO,
	WARNING,
	ERROR,
	FATAL
}
