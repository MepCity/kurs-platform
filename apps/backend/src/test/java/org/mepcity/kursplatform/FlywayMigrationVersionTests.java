package org.mepcity.kursplatform;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class FlywayMigrationVersionTests {
    private static final Path MIGRATION_ROOT = Path.of("src/main/resources/db/migration");
    private static final Pattern VERSIONED_MIGRATION =
            Pattern.compile("^V(?<version>\\d+)__.+\\.sql$");

    @Test
    void versionsAreUniqueContiguousAndIamFollowsMainV25() throws IOException {
        List<Migration> migrations;
        try (var files = Files.list(MIGRATION_ROOT)) {
            migrations = files
                    .map(path -> path.getFileName().toString())
                    .map(FlywayMigrationVersionTests::parse)
                    .sorted(java.util.Comparator.comparingInt(Migration::version))
                    .toList();
        }

        assertThat(migrations)
                .extracting(Migration::version)
                .doesNotHaveDuplicates()
                .containsExactlyElementsOf(
                        IntStream.rangeClosed(1, migrations.size()).boxed().toList());
        assertThat(migrations).hasSizeGreaterThanOrEqualTo(31);
        assertThat(migrations.subList(24, 31))
                .extracting(Migration::fileName)
                .containsExactly(
                        "V25__org_list_rate_limit.sql",
                        "V26__iam_cognito_security_events.sql",
                        "V27__iam_cognito_event_revoke_rls.sql",
                        "V28__iam_cognito_event_leases.sql",
                        "V29__iam_cognito_pool_and_revoke_select_rls.sql",
                        "V30__iam_cognito_reconciliation.sql",
                        "V31__iam_cognito_durable_retry_and_lag.sql");
    }

    private static Migration parse(String fileName) {
        var matcher = VERSIONED_MIGRATION.matcher(fileName);
        assertThat(matcher.matches())
                .as("Flyway migration file name: %s", fileName)
                .isTrue();
        return new Migration(Integer.parseInt(matcher.group("version")), fileName);
    }

    private record Migration(int version, String fileName) {
    }
}
