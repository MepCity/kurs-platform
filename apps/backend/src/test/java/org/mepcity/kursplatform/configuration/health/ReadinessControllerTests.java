package org.mepcity.kursplatform.configuration.health;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ReadinessControllerTests {

    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final ReadinessController controller = new ReadinessController(jdbc);

    @Test
    void returnsUpOnlyForTheLeastPrivilegedIamRuntimeRole() {
        when(jdbc.queryForObject(anyString(), org.mockito.ArgumentMatchers.eq(Boolean.class))).thenReturn(true);

        var response = controller.readiness();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).containsEntry("status", "UP");
    }

    @Test
    void returnsDetailFreeDownWhenRoleCheckFails() {
        when(jdbc.queryForObject(anyString(), org.mockito.ArgumentMatchers.eq(Boolean.class)))
                .thenThrow(new IllegalStateException("sensitive database detail"));

        var response = controller.readiness();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(response.getBody()).containsExactlyEntriesOf(java.util.Map.of("status", "DOWN"));
        assertThat(response.toString()).doesNotContain("sensitive database detail");
    }
}
