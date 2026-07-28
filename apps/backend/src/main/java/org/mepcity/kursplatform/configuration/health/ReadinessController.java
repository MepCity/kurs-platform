package org.mepcity.kursplatform.configuration.health;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** Public, detail-free readiness probe for the closed-alpha runtime. */
@RestController
public class ReadinessController {

    private static final String RUNTIME_ROLE_QUERY = """
            SELECT NOT rolsuper AND NOT rolbypassrls
            FROM pg_roles
            WHERE rolname = current_user AND rolname = 'iam_runtime'
            """;

    private final JdbcTemplate jdbcTemplate;

    public ReadinessController(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> readiness() {
        try {
            Boolean leastPrivilegeRuntime = jdbcTemplate.queryForObject(RUNTIME_ROLE_QUERY, Boolean.class);
            if (Boolean.TRUE.equals(leastPrivilegeRuntime)) {
                return ResponseEntity.ok(Map.of("status", "UP"));
            }
        } catch (RuntimeException ignored) {
            // Public health responses deliberately disclose no dependency or exception detail.
        }
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(Map.of("status", "DOWN"));
    }
}
