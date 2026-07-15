package org.mepcity.kursplatform.configuration.observability;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mepcity.kursplatform.core.observability.SafeLogEvent;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.servlet.HandlerMapping;

class RequestObservabilityFilterTests {

	private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-07-15T10:00:00Z"), ZoneOffset.UTC);

	@Test
	void reusesValidRequestIdAndDoesNotLogQueryOrBody() throws Exception {
		List<SafeLogEvent> events = new ArrayList<>();
		RequestObservabilityFilter filter = new RequestObservabilityFilter(CLOCK, events::add);
		MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/students");
		request.addHeader("X-Request-Id", "mobile:req-1");
		request.setQueryString("phone=05551234567&note=secret");
		request.setContent("{\"token\":\"Bearer secret\"}".getBytes());
		MockHttpServletResponse response = new MockHttpServletResponse();

		filter.doFilter(request, response, (ignoredRequest, ignoredResponse) -> {
			request.setAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE, "/api/v1/students");
			response.setStatus(202);
		});

		assertThat(response.getHeader("X-Request-Id")).isEqualTo("mobile:req-1");
		assertThat(events).hasSize(1);
		assertThat(events.getFirst().fields())
				.containsEntry("path", "/api/v1/students")
				.doesNotContainValue("phone=05551234567&note=secret")
				.doesNotContainValue("{\"token\":\"Bearer secret\"}");
	}

	@Test
	void rejectsInvalidRequestIdWithoutCallingApplication() throws Exception {
		List<SafeLogEvent> events = new ArrayList<>();
		RequestObservabilityFilter filter = new RequestObservabilityFilter(CLOCK, events::add);
		MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/students");
		request.addHeader("X-Request-Id", "invalid request id");
		MockHttpServletResponse response = new MockHttpServletResponse();
		boolean[] called = {false};

		filter.doFilter(request, response, (ignoredRequest, ignoredResponse) -> called[0] = true);

		assertThat(called[0]).isFalse();
		assertThat(response.getStatus()).isEqualTo(400);
		assertThat(response.getHeader("X-Request-Id")).matches("[A-Za-z0-9._:-]{1,128}");
		assertThat(response.getContentAsString()).contains("INVALID_REQUEST").doesNotContain("invalid request id");
		assertThat(events).singleElement().satisfies(event -> assertThat(event.fields())
				.containsEntry("requestId", response.getHeader("X-Request-Id"))
				.containsEntry("path", "unmatched")
				.containsEntry("status", 400));
	}

	@Test
	void generatesRequestIdWhenHeaderIsMissing() throws Exception {
		List<SafeLogEvent> events = new ArrayList<>();
		RequestObservabilityFilter filter = new RequestObservabilityFilter(CLOCK, events::add);
		MockHttpServletRequest request = new MockHttpServletRequest("GET", "/health");
		MockHttpServletResponse response = new MockHttpServletResponse();

		filter.doFilter(request, response, (ignoredRequest, ignoredResponse) -> {
			request.setAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE, "/health");
			response.setStatus(200);
		});

		assertThat(response.getHeader("X-Request-Id")).matches("[A-Za-z0-9._:-]{1,128}");
		assertThat(events).singleElement().satisfies(event -> assertThat(event.fields())
				.containsEntry("requestId", response.getHeader("X-Request-Id"))
				.containsEntry("status", 200));
	}

	@Test
	void unmatchedPathDoesNotCopyAttackerControlledPathIntoLogs() throws Exception {
		List<SafeLogEvent> events = new ArrayList<>();
		RequestObservabilityFilter filter = new RequestObservabilityFilter(CLOCK, events::add);
		MockHttpServletRequest request = new MockHttpServletRequest("GET", "/token/Bearer-secret");
		MockHttpServletResponse response = new MockHttpServletResponse();

		filter.doFilter(request, response, (ignoredRequest, ignoredResponse) -> response.setStatus(404));

		assertThat(events).singleElement().satisfies(event -> assertThat(event.fields())
				.containsEntry("path", "unmatched")
				.doesNotContainValue("/token/Bearer-secret"));
	}

	@Test
	void loggerRuntimeFailureDoesNotChangeSuccessfulResponse() throws Exception {
		RequestObservabilityFilter filter = failingLoggerFilter();
		MockHttpServletRequest request = new MockHttpServletRequest("GET", "/health");
		MockHttpServletResponse response = new MockHttpServletResponse();

		filter.doFilter(request, response, (ignoredRequest, ignoredResponse) -> response.setStatus(204));

		assertThat(response.getStatus()).isEqualTo(204);
	}

	@Test
	void traceAndConnectProduceCompletionEvents() throws Exception {
		for (String method : new String[] {"TRACE", "CONNECT"}) {
			List<SafeLogEvent> events = new ArrayList<>();
			RequestObservabilityFilter filter = new RequestObservabilityFilter(CLOCK, events::add);
			MockHttpServletRequest request = new MockHttpServletRequest(method, "/health");
			MockHttpServletResponse response = new MockHttpServletResponse();

			filter.doFilter(request, response, (ignoredRequest, ignoredResponse) -> {
				request.setAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE, "/health");
				response.setStatus(204);
			});

			assertThat(response.getStatus()).as(method).isEqualTo(204);
			assertThat(events).as(method).singleElement().satisfies(event -> assertThat(event.fields())
					.containsEntry("method", method)
					.containsEntry("status", 204));
		}
	}

	@Test
	void invalidMethodTelemetryDoesNotChangeProductResponse() throws Exception {
		List<SafeLogEvent> events = new ArrayList<>();
		RequestObservabilityFilter filter = new RequestObservabilityFilter(CLOCK, events::add);
		MockHttpServletRequest request = new MockHttpServletRequest("BREW\nforged", "/health");
		MockHttpServletResponse response = new MockHttpServletResponse();

		filter.doFilter(request, response, (ignoredRequest, ignoredResponse) -> response.setStatus(207));

		assertThat(response.getStatus()).isEqualTo(207);
		assertThat(events).isEmpty();
	}

	@Test
	void loggerRuntimeFailureDoesNotChangeInvalidRequestIdResponse() throws Exception {
		RequestObservabilityFilter filter = failingLoggerFilter();
		MockHttpServletRequest request = new MockHttpServletRequest("GET", "/health");
		request.addHeader("X-Request-Id", "invalid request id");
		MockHttpServletResponse response = new MockHttpServletResponse();

		filter.doFilter(request, response, (ignoredRequest, ignoredResponse) -> {
			throw new AssertionError("application must not be called");
		});

		assertThat(response.getStatus()).isEqualTo(400);
		assertThat(response.getContentAsString()).contains("INVALID_REQUEST");
	}

	@Test
	void loggerRuntimeFailureDoesNotMaskApplicationFailure() {
		RequestObservabilityFilter filter = failingLoggerFilter();
		MockHttpServletRequest request = new MockHttpServletRequest("GET", "/health");
		MockHttpServletResponse response = new MockHttpServletResponse();
		IllegalStateException applicationFailure = new IllegalStateException("application failure");

		assertThatThrownBy(() -> filter.doFilter(
				request, response, (ignoredRequest, ignoredResponse) -> { throw applicationFailure; }))
				.isSameAs(applicationFailure);
	}

	@Test
	void fatalJvmErrorFromLoggerIsNotSwallowed() {
		AssertionError fatal = new AssertionError("fatal logger failure");
		RequestObservabilityFilter filter = new RequestObservabilityFilter(CLOCK, event -> { throw fatal; });
		MockHttpServletRequest request = new MockHttpServletRequest("GET", "/health");
		MockHttpServletResponse response = new MockHttpServletResponse();

		assertThatThrownBy(() -> filter.doFilter(
				request, response, (ignoredRequest, ignoredResponse) -> response.setStatus(200)))
				.isSameAs(fatal);
	}

	private RequestObservabilityFilter failingLoggerFilter() {
		return new RequestObservabilityFilter(CLOCK, event -> {
			throw new IllegalStateException("logger unavailable");
		});
	}
}
