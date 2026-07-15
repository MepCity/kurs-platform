package org.mepcity.kursplatform.core.observability;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.lang.reflect.Modifier;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mepcity.kursplatform.core.observability.SafeLogEvent.RuntimeEnvironment;
import org.mepcity.kursplatform.core.observability.SafeLogEvent.SafeLogOperation;

class SafeLogEventTests {

	private static final Instant NOW = Instant.parse("2026-07-15T10:00:00Z");

	@Test
	void requestEventContainsOnlyTypedDiagnosticMetadata() {
		SafeLogEvent event = SafeLogEvent.httpRequestCompleted(
				NOW, "req-1", "POST", "/api/v1/students/{studentId}", 201, 42,
				RuntimeEnvironment.STAGING);

		assertThat(event.name()).isEqualTo("http.request.completed");
		assertThat(event.severity()).isEqualTo(SafeLogSeverity.INFO);
		assertThat(event.fields()).containsOnly(
				Map.entry("requestId", "req-1"),
				Map.entry("method", "POST"),
				Map.entry("path", "/api/v1/students/{studentId}"),
				Map.entry("status", 201),
				Map.entry("durationMs", 42L),
				Map.entry("environment", "staging"));
	}

	@Test
	void noExternalGeneralMapConstructorExists() {
		assertThat(SafeLogEvent.class.getDeclaredConstructors())
				.allSatisfy(constructor -> {
					assertThat(Modifier.isPrivate(constructor.getModifiers())).isTrue();
					assertThat(constructor.getParameterTypes()).contains(Map.class);
				});
	}

	@Test
	void freeTextOperationAndEnvironmentAreRejected() {
		assertThatThrownBy(() -> SafeLogOperation.fromValue("telefon=05551234567 token=secret parola=abc"))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessage("Unsupported operation");
		assertThatThrownBy(() -> RuntimeEnvironment.fromValue("production\nforged=true"))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessage("Unsupported environment");
	}

	@Test
	void requestFieldsRejectForgingAndInvalidRanges() {
		assertThatThrownBy(() -> request("req\nforged", "GET", "/health", 200, 1))
				.hasMessage("Invalid requestId");
		for (String invalidRoute : new String[] {
				"/health?token=secret",
				"/students?phone=05551234567",
				"/health#fragment",
				"/health\r\nforged=true"
		}) {
			assertThatThrownBy(() -> request("req-1", "GET", invalidRoute, 200, 1))
					.as(invalidRoute)
					.hasMessage("Invalid route pattern");
		}
		assertThatThrownBy(() -> request("req-1", "BREW\nforged", "/health", 200, 1))
				.hasMessage("Invalid HTTP method");
		assertThatThrownBy(() -> request("req-1", "GET", "/health", 99, 1))
				.hasMessage("Invalid HTTP status");
		assertThatThrownBy(() -> request("req-1", "GET", "/health", 600, 1))
				.hasMessage("Invalid HTTP status");
		assertThatThrownBy(() -> request("req-1", "GET", "/health", 200, -1))
				.hasMessage("durationMs cannot be negative");
	}

	@Test
	void validSpringRoutePatternsAndSafeMethodsAreAccepted() {
		for (String route : new String[] {
				"/health", "/api/v1/students/{studentId}", "/api/v1/classes/*"
		}) {
			assertThat(request("req-1", "GET", route, 200, 1).fields())
					.containsEntry("path", route);
		}
		assertThat(request("req-1", "TRACE", "/health", 200, 1).fields())
				.containsEntry("method", "TRACE");
		assertThat(request("req-1", "CONNECT", "/health", 200, 1).fields())
				.containsEntry("method", "CONNECT");
	}

	@Test
	void unexpectedErrorRecordsTypeWithoutMessageOrStack() {
		SafeLogEvent event = SafeLogEvent.unexpectedApplicationError(
				NOW, "req-2", SafeLogOperation.HTTP_REQUEST,
				new IllegalStateException("telefon=05551234567 token=secret parola=abc"));

		assertThat(event.severity()).isEqualTo(SafeLogSeverity.ERROR);
		assertThat(event.fields())
				.containsOnly(
						Map.entry("requestId", "req-2"),
						Map.entry("operation", "http.request"),
						Map.entry("errorType", "IllegalStateException"))
				.doesNotContainValue("telefon=05551234567 token=secret parola=abc");
	}

	@Test
	void emptyErrorTypeIsRejected() {
		RuntimeException anonymousError = new RuntimeException("sensitive") {};

		assertThatThrownBy(() -> SafeLogEvent.unexpectedApplicationError(
				NOW, "req-2", SafeLogOperation.HTTP_REQUEST, anonymousError))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessage("Invalid errorType");
	}

	@Test
	void severityIsDerivedByTypedEventFactory() {
		assertThat(request("req-1", "GET", "/health", 200, 0).severity())
				.isEqualTo(SafeLogSeverity.INFO);
		assertThat(request("req-1", "GET", "/health", 400, 0).severity())
				.isEqualTo(SafeLogSeverity.WARNING);
		assertThat(request("req-1", "GET", "/health", 500, 0).severity())
				.isEqualTo(SafeLogSeverity.ERROR);
		assertThat(SafeLogEvent.fatalApplicationError(
				NOW, "req-1", SafeLogOperation.HTTP_REQUEST, new IllegalStateException()).severity())
				.isEqualTo(SafeLogSeverity.FATAL);
	}

	private SafeLogEvent request(
			String requestId, String method, String path, int status, long durationMs) {
		return SafeLogEvent.httpRequestCompleted(NOW, requestId, method, path, status, durationMs);
	}
}
