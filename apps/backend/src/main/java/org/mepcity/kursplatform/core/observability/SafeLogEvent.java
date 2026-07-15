package org.mepcity.kursplatform.core.observability;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;

/** Provider-independent diagnostic event created only through typed factories. */
public final class SafeLogEvent {

	private static final Pattern REQUEST_ID = Pattern.compile("[A-Za-z0-9._:-]{1,128}");
	private static final Pattern ERROR_TYPE = Pattern.compile("[A-Za-z_$][A-Za-z0-9_.$]{0,127}");
	private static final Pattern ROUTE_PATTERN = Pattern.compile("/[A-Za-z0-9._~!$&'()*+,;=:@/{}`*\\[\\]-]{0,255}");

	private final String name;
	private final SafeLogSeverity severity;
	private final Instant occurredAt;
	private final Map<String, Object> fields;

	private SafeLogEvent(
			String name,
			SafeLogSeverity severity,
			Instant occurredAt,
			Map<String, Object> fields) {
		this.name = name;
		this.severity = Objects.requireNonNull(severity, "severity is required");
		this.occurredAt = Objects.requireNonNull(occurredAt, "occurredAt is required");
		this.fields = Map.copyOf(fields);
	}

	public static SafeLogEvent httpRequestCompleted(
			Instant occurredAt,
			String requestId,
			String method,
			String routePattern,
			int status,
			long durationMs) {
		return httpRequestCompleted(
				occurredAt, requestId, method, routePattern, status, durationMs, null);
	}

	public static SafeLogEvent httpRequestCompleted(
			Instant occurredAt,
			String requestId,
			String method,
			String routePattern,
			int status,
			long durationMs,
			RuntimeEnvironment environment) {
		validateRequestId(requestId);
		String validatedMethod = HttpMethod.fromValue(method).value();
		String validatedRoute = validateRoutePattern(routePattern);
		if (status < 100 || status > 599) {
			throw new IllegalArgumentException("Invalid HTTP status");
		}
		if (durationMs < 0) {
			throw new IllegalArgumentException("durationMs cannot be negative");
		}

		Map<String, Object> fields = new LinkedHashMap<>();
		fields.put("requestId", requestId);
		fields.put("method", validatedMethod);
		fields.put("path", validatedRoute);
		fields.put("status", status);
		fields.put("durationMs", durationMs);
		if (environment != null) {
			fields.put("environment", environment.value());
		}
		return new SafeLogEvent(
				"http.request.completed", severityForHttpStatus(status), occurredAt, fields);
	}

	public static SafeLogEvent unexpectedApplicationError(
			Instant occurredAt, String requestId, SafeLogOperation operation, Throwable error) {
		return applicationError(
				occurredAt, requestId, operation, error, SafeLogSeverity.ERROR);
	}

	public static SafeLogEvent fatalApplicationError(
			Instant occurredAt, String requestId, SafeLogOperation operation, Throwable error) {
		return applicationError(
				occurredAt, requestId, operation, error, SafeLogSeverity.FATAL);
	}

	private static SafeLogEvent applicationError(
			Instant occurredAt,
			String requestId,
			SafeLogOperation operation,
			Throwable error,
			SafeLogSeverity severity) {
		validateRequestId(requestId);
		Objects.requireNonNull(operation, "operation is required");
		Objects.requireNonNull(error, "error is required");
		String errorType = error.getClass().getSimpleName();
		if (!ERROR_TYPE.matcher(errorType).matches()) {
			throw new IllegalArgumentException("Invalid errorType");
		}
		return new SafeLogEvent("application.error.unexpected", severity, occurredAt, Map.of(
				"requestId", requestId,
				"operation", operation.value(),
				"errorType", errorType));
	}

	private static SafeLogSeverity severityForHttpStatus(int status) {
		if (status >= 500) {
			return SafeLogSeverity.ERROR;
		}
		if (status >= 400) {
			return SafeLogSeverity.WARNING;
		}
		return SafeLogSeverity.INFO;
	}

	private static void validateRequestId(String requestId) {
		if (requestId == null || !REQUEST_ID.matcher(requestId).matches()) {
			throw new IllegalArgumentException("Invalid requestId");
		}
	}

	private static String validateRoutePattern(String routePattern) {
		if ("unmatched".equals(routePattern)) {
			return routePattern;
		}
		if (routePattern == null || !ROUTE_PATTERN.matcher(routePattern).matches()) {
			throw new IllegalArgumentException("Invalid route pattern");
		}
		return routePattern;
	}

	public String name() {
		return name;
	}

	public SafeLogSeverity severity() {
		return severity;
	}

	public Instant occurredAt() {
		return occurredAt;
	}

	public Map<String, Object> fields() {
		return fields;
	}

	public enum SafeLogOperation {
		HTTP_REQUEST("http.request");

		private final String value;

		SafeLogOperation(String value) {
			this.value = value;
		}

		public String value() {
			return value;
		}

		public static SafeLogOperation fromValue(String value) {
			for (SafeLogOperation operation : values()) {
				if (operation.value.equals(value)) {
					return operation;
				}
			}
			throw new IllegalArgumentException("Unsupported operation");
		}
	}

	public enum RuntimeEnvironment {
		DEVELOPMENT("development"),
		STAGING("staging"),
		PRODUCTION("production");

		private final String value;

		RuntimeEnvironment(String value) {
			this.value = value;
		}

		public String value() {
			return value;
		}

		public static RuntimeEnvironment fromValue(String value) {
			for (RuntimeEnvironment environment : values()) {
				if (environment.value.equals(value)) {
					return environment;
				}
			}
			throw new IllegalArgumentException("Unsupported environment");
		}
	}

	private enum HttpMethod {
		GET,
		HEAD,
		POST,
		PUT,
		PATCH,
		DELETE,
		OPTIONS,
		TRACE,
		CONNECT;

		private String value() {
			return name();
		}

		private static HttpMethod fromValue(String value) {
			try {
				return valueOf(value);
			} catch (NullPointerException | IllegalArgumentException exception) {
				throw new IllegalArgumentException("Invalid HTTP method", exception);
			}
		}
	}
}
