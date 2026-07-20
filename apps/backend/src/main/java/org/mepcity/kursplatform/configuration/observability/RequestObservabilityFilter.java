package org.mepcity.kursplatform.configuration.observability;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.regex.Pattern;
import org.mepcity.kursplatform.core.observability.SafeEventLogger;
import org.mepcity.kursplatform.core.observability.SafeLogEvent;
import org.mepcity.kursplatform.core.observability.SafeLogEvent.SafeLogOperation;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerMapping;
import org.springframework.web.filter.OncePerRequestFilter;

/** Establishes one safe correlation identifier and one completion event per HTTP request. */
@Component
public final class RequestObservabilityFilter extends OncePerRequestFilter {

	public static final String REQUEST_ID_HEADER = "X-Request-Id";
	private static final Pattern REQUEST_ID = Pattern.compile("[A-Za-z0-9._:-]{1,128}");
	private static final String INVALID_REQUEST_MESSAGE =
			"{\"error\":{\"code\":\"INVALID_REQUEST\",\"message\":\"Geçersiz istek kimliği.\",\"requestId\":\"%s\"}}";

	private final Clock clock;
	private final SafeEventLogger eventLogger;

	@Autowired
	public RequestObservabilityFilter(SafeEventLogger eventLogger) {
		this(Clock.systemUTC(), eventLogger);
	}

	RequestObservabilityFilter(Clock clock, SafeEventLogger eventLogger) {
		this.clock = clock;
		this.eventLogger = eventLogger;
	}

	@Override
	protected void doFilterInternal(
			HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
			throws ServletException, IOException {
		String suppliedRequestId = request.getHeader(REQUEST_ID_HEADER);
		String requestId = suppliedRequestId == null ? UUID.randomUUID().toString() : suppliedRequestId;
		if (!REQUEST_ID.matcher(requestId).matches()) {
			String generatedRequestId = UUID.randomUUID().toString();
			writeInvalidRequestId(response, generatedRequestId);
			logBestEffort(() -> SafeLogEvent.httpRequestCompleted(
					clock.instant(), generatedRequestId, request.getMethod(), "unmatched", 400, 0));
			return;
		}

		response.setHeader(REQUEST_ID_HEADER, requestId);
		Instant startedAt = clock.instant();
		try (MDC.MDCCloseable ignored = MDC.putCloseable("requestId", requestId)) {
			try {
				filterChain.doFilter(request, response);
			} catch (IOException | ServletException | RuntimeException error) {
				logBestEffort(() -> SafeLogEvent.unexpectedApplicationError(
						clock.instant(), requestId, SafeLogOperation.HTTP_REQUEST, error));
				if (!response.isCommitted()) {
					response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
				}
				throw error;
			} finally {
				long durationMs = Math.max(0, clock.millis() - startedAt.toEpochMilli());
				logBestEffort(() -> SafeLogEvent.httpRequestCompleted(
						clock.instant(), requestId, request.getMethod(), safePath(request),
						response.getStatus(), durationMs));
			}
		}
	}

	private void logBestEffort(Supplier<SafeLogEvent> eventSupplier) {
		try {
			eventLogger.log(eventSupplier.get());
		} catch (RuntimeException ignored) {
			// Telemetry cannot change HTTP behavior. JVM fatal Error types remain visible.
		}
	}

	private void writeInvalidRequestId(HttpServletResponse response, String requestId) throws IOException {
		response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
		response.setHeader(REQUEST_ID_HEADER, requestId);
		response.setContentType(MediaType.APPLICATION_JSON_VALUE);
		response.setCharacterEncoding(StandardCharsets.UTF_8.name());
		response.getWriter().write(INVALID_REQUEST_MESSAGE.formatted(requestId));
	}

	private String safePath(HttpServletRequest request) {
		Object matchedPattern = request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
		if (!(matchedPattern instanceof String path) || path.isBlank()) {
			return "unmatched";
		}
		return path.length() <= 256 ? path : path.substring(0, 256);
	}
}
