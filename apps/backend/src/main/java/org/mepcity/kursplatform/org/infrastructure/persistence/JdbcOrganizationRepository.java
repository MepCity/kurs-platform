package org.mepcity.kursplatform.org.infrastructure.persistence;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;
import java.util.UUID;
import org.mepcity.kursplatform.org.domain.Organization;
import org.mepcity.kursplatform.org.domain.OrganizationRepository;
import org.mepcity.kursplatform.org.domain.OrganizationStatus;
import javax.sql.DataSource;
import org.springframework.jdbc.datasource.DataSourceUtils;
import org.springframework.stereotype.Repository;

/** JDBC adapter that uses the caller's transaction and its PostgreSQL RLS context. */
@Repository
public class JdbcOrganizationRepository implements OrganizationRepository {

    private static final String ORGANIZATION_COLUMNS = "id, name, short_name, primary_color, secondary_color, status, "
            + "default_timezone, created_at, updated_at, row_version, created_by_user_id, updated_by_user_id";

    private final DataSource dataSource;

    public JdbcOrganizationRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public Organization create(Organization organization) {
        Connection connection = DataSourceUtils.getConnection(dataSource);
        String sql = "INSERT INTO organizations (id, name, short_name, primary_color, secondary_color, status, "
                + "default_timezone, created_by_user_id, updated_by_user_id) "
                + "VALUES (?, ?, ?, ?, ?, ?::organization_status_enum, ?, ?, ?)";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setObject(1, organization.id());
            statement.setString(2, organization.name());
            statement.setString(3, organization.shortName());
            statement.setString(4, organization.primaryColor());
            statement.setString(5, organization.secondaryColor());
            statement.setString(6, organization.status().name());
            statement.setString(7, organization.defaultTimezone());
            statement.setObject(8, organization.createdByUserId());
            statement.setObject(9, organization.updatedByUserId());
            if (statement.executeUpdate() != 1) {
                throw new IllegalStateException("Kurum oluşturma sonuç döndürmedi");
            }
            // GLOBAL CREATE RLS intentionally does not expose organizations as a read surface.
            // The database defaults are deterministic for this command; the service may read later via ORG_DETAIL.
            var createdAt = transactionTimestamp(connection);
            return new Organization(organization.id(), organization.name(), organization.shortName(), organization.primaryColor(),
                    organization.secondaryColor(), OrganizationStatus.ACTIVE, organization.defaultTimezone(), createdAt, createdAt, 1,
                    organization.createdByUserId(), organization.updatedByUserId());
        } catch (SQLException exception) {
            throw new OrganizationPersistenceException("Kurum oluşturulamadı", exception);
        }
    }

    @Override
    public Optional<Organization> findById(UUID organizationId) {
        Connection connection = DataSourceUtils.getConnection(dataSource);
        return queryOne(connection, "SELECT " + ORGANIZATION_COLUMNS + " FROM organizations WHERE id = ?", organizationId);
    }

    @Override
    public Optional<Organization> findByIdForUpdate(UUID organizationId) {
        Connection connection = DataSourceUtils.getConnection(dataSource);
        return queryOne(connection, "SELECT " + ORGANIZATION_COLUMNS + " FROM organizations WHERE id = ? FOR UPDATE", organizationId);
    }

    @Override
    public Optional<Organization> updateIdentity(Organization organization) {
        Connection connection = DataSourceUtils.getConnection(dataSource);
        String sql = "UPDATE organizations SET name = ?, short_name = ?, primary_color = ?, secondary_color = ?, "
                + "default_timezone = ?, updated_at = transaction_timestamp(), row_version = row_version + 1, "
                + "updated_by_user_id = ? WHERE id = ? AND row_version = ? RETURNING " + ORGANIZATION_COLUMNS;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, organization.name());
            statement.setString(2, organization.shortName());
            statement.setString(3, organization.primaryColor());
            statement.setString(4, organization.secondaryColor());
            statement.setString(5, organization.defaultTimezone());
            statement.setObject(6, organization.updatedByUserId());
            statement.setObject(7, organization.id());
            statement.setInt(8, organization.rowVersion());
            try (ResultSet result = statement.executeQuery()) {
                return result.next() ? Optional.of(map(result)) : Optional.empty();
            }
        } catch (SQLException exception) {
            throw new OrganizationPersistenceException("Kurum güncellenemedi", exception);
        }
    }

    @Override
    public Optional<Organization> transitionStatus(
            UUID organizationId, OrganizationStatus expectedStatus,
            OrganizationStatus nextStatus, int expectedRowVersion, UUID updatedByUserId) {
        Connection connection = DataSourceUtils.getConnection(dataSource);
        String sql = "UPDATE organizations SET status = ?::organization_status_enum, updated_at = transaction_timestamp(), "
                + "row_version = row_version + 1, updated_by_user_id = ? "
                + "WHERE id = ? AND status = ?::organization_status_enum AND row_version = ? RETURNING " + ORGANIZATION_COLUMNS;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, nextStatus.name());
            statement.setObject(2, updatedByUserId);
            statement.setObject(3, organizationId);
            statement.setString(4, expectedStatus.name());
            statement.setInt(5, expectedRowVersion);
            try (ResultSet result = statement.executeQuery()) {
                return result.next() ? Optional.of(map(result)) : Optional.empty();
            }
        } catch (SQLException exception) {
            throw new OrganizationPersistenceException("Kurum durumu değiştirilemedi", exception);
        }
    }

    @Override
    public Optional<Organization> updateBrand(UUID organizationId, String primaryColor, String secondaryColor,
            int expectedRowVersion, UUID updatedByUserId) {
        Connection connection = DataSourceUtils.getConnection(dataSource);
        String sql = "UPDATE organizations SET primary_color = ?, secondary_color = ?, "
                + "updated_at = transaction_timestamp(), row_version = row_version + 1, updated_by_user_id = ? "
                + "WHERE id = ? AND row_version = ? RETURNING " + ORGANIZATION_COLUMNS;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, primaryColor);
            statement.setString(2, secondaryColor);
            statement.setObject(3, updatedByUserId);
            statement.setObject(4, organizationId);
            statement.setInt(5, expectedRowVersion);
            try (ResultSet result = statement.executeQuery()) {
                return result.next() ? Optional.of(map(result)) : Optional.empty();
            }
        } catch (SQLException exception) {
            throw new OrganizationPersistenceException("Kurum markası güncellenemedi", exception);
        }
    }

    private Optional<Organization> queryOne(Connection connection, String sql, UUID organizationId) {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setObject(1, organizationId);
            try (ResultSet result = statement.executeQuery()) {
                return result.next() ? Optional.of(map(result)) : Optional.empty();
            }
        } catch (SQLException exception) {
            throw new OrganizationPersistenceException("Kurum okunamadı", exception);
        }
    }

    private static java.time.Instant transactionTimestamp(Connection connection) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("SELECT transaction_timestamp()");
                ResultSet result = statement.executeQuery()) {
            result.next();
            return result.getTimestamp(1).toInstant();
        }
    }

    private static Organization map(ResultSet result) throws SQLException {
        return new Organization(
                result.getObject("id", UUID.class),
                result.getString("name"),
                result.getString("short_name"),
                result.getString("primary_color"),
                result.getString("secondary_color"),
                OrganizationStatus.valueOf(result.getString("status")),
                result.getString("default_timezone"),
                result.getTimestamp("created_at").toInstant(),
                result.getTimestamp("updated_at").toInstant(),
                result.getInt("row_version"),
                result.getObject("created_by_user_id", UUID.class),
                result.getObject("updated_by_user_id", UUID.class));
    }
}
