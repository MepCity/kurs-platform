-- V1: IAM tabloları, roller, RLS ve migration
-- Görev: IAM-003

CREATE TYPE user_status_enum AS ENUM ('PROVISIONING', 'ACTIVE', 'SUSPENDED');
CREATE TYPE membership_role_enum AS ENUM ('ORG_ADMIN', 'TEACHER');
CREATE TYPE device_platform_enum AS ENUM ('IOS', 'ANDROID');
CREATE TYPE provider_command_type_enum AS ENUM ('TEACHER_ACCOUNT_CREATE', 'USER_DISABLE', 'USER_LOGOUT', 'PASSWORD_RESET');
CREATE TYPE provider_command_status_enum AS ENUM ('PENDING', 'CLAIMED', 'COMPLETED', 'FAILED');
CREATE TYPE secret_delivery_status_enum AS ENUM ('ESCROWED', 'READY', 'CONSUMED', 'EXPIRED');
CREATE TYPE event_source_enum AS ENUM ('ADMIN_EVENTS', 'USER_EVENTS');
CREATE TYPE idempotency_scope_enum AS ENUM ('GLOBAL', 'ORGANIZATION', 'IAM_AUTH');
CREATE TYPE idempotency_status_enum AS ENUM ('PENDING', 'COMPLETED', 'FAILED');
CREATE TYPE escrow_status_enum AS ENUM ('READY', 'EXPIRED', 'REVOKED');
CREATE TYPE event_scope_enum AS ENUM ('ORGANIZATION', 'GLOBAL');
CREATE TYPE event_kind_enum AS ENUM ('DATA_MUTATION', 'SECURITY', 'ACCESS', 'EXPORT');

CREATE TABLE organizations (
    id UUID PRIMARY KEY,
    name TEXT NOT NULL,
    short_name TEXT,
    status TEXT NOT NULL DEFAULT 'ACTIVE',
    default_timezone TEXT NOT NULL DEFAULT 'Europe/Istanbul',
    created_at TIMESTAMPTZ NOT NULL DEFAULT transaction_timestamp(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT transaction_timestamp(),
    row_version INTEGER NOT NULL DEFAULT 1,
    created_by_user_id UUID,
    updated_by_user_id UUID
);

CREATE TABLE users (
    id UUID PRIMARY KEY,
    status user_status_enum NOT NULL DEFAULT 'PROVISIONING',
    reauthentication_required_after TIMESTAMPTZ NOT NULL DEFAULT 'epoch'::timestamptz,
    created_at TIMESTAMPTZ NOT NULL DEFAULT transaction_timestamp(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT transaction_timestamp(),
    row_version INTEGER NOT NULL DEFAULT 1,
    created_by_user_id UUID REFERENCES users(id),
    updated_by_user_id UUID REFERENCES users(id)
);

CREATE TABLE user_identities (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id),
    issuer TEXT NOT NULL,
    subject TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT transaction_timestamp(),
    disabled_at TIMESTAMPTZ,
    UNIQUE (issuer, subject),
    UNIQUE (user_id, issuer)
);

CREATE TABLE people (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL REFERENCES organizations(id),
    first_name TEXT NOT NULL,
    last_name TEXT NOT NULL,
    phone TEXT NOT NULL,
    photo_asset_id UUID,
    birth_date DATE,
    address TEXT,
    school TEXT,
    note TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT transaction_timestamp(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT transaction_timestamp(),
    row_version INTEGER NOT NULL DEFAULT 1,
    created_by_user_id UUID REFERENCES users(id),
    updated_by_user_id UUID REFERENCES users(id),
    UNIQUE (id, organization_id)
);

CREATE INDEX people_org_name_idx ON people (organization_id, last_name, first_name);

CREATE TABLE platform_administrators (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL UNIQUE REFERENCES users(id),
    granted_by_user_id UUID REFERENCES users(id),
    granted_at TIMESTAMPTZ NOT NULL,
    revoked_at TIMESTAMPTZ
);

CREATE TABLE platform_administrator_profiles (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL UNIQUE REFERENCES users(id),
    first_name TEXT NOT NULL,
    last_name TEXT NOT NULL,
    phone TEXT,
    note TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT transaction_timestamp(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT transaction_timestamp(),
    row_version INTEGER NOT NULL DEFAULT 1,
    created_by_user_id UUID REFERENCES users(id),
    updated_by_user_id UUID REFERENCES users(id)
);

CREATE TABLE organization_memberships (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL REFERENCES organizations(id),
    user_id UUID NOT NULL REFERENCES users(id),
    person_id UUID NOT NULL,
    status user_status_enum NOT NULL DEFAULT 'PROVISIONING',
    session_generation INTEGER NOT NULL DEFAULT 1,
    reauthentication_required_after TIMESTAMPTZ NOT NULL DEFAULT 'epoch'::timestamptz,
    granted_by_user_id UUID REFERENCES users(id),
    granted_at TIMESTAMPTZ NOT NULL,
    UNIQUE (organization_id, user_id),
    UNIQUE (person_id),
    UNIQUE (id, organization_id),
    UNIQUE (id, user_id),
    FOREIGN KEY (person_id, organization_id) REFERENCES people (id, organization_id)
);

CREATE TABLE organization_membership_roles (
    id UUID PRIMARY KEY,
    organization_membership_id UUID NOT NULL,
    organization_id UUID NOT NULL REFERENCES organizations(id),
    role membership_role_enum NOT NULL,
    granted_by_user_id UUID REFERENCES users(id),
    granted_at TIMESTAMPTZ NOT NULL,
    revoked_at TIMESTAMPTZ,
    FOREIGN KEY (organization_membership_id, organization_id)
        REFERENCES organization_memberships (id, organization_id),
    UNIQUE (id, organization_id, role)
);

CREATE UNIQUE INDEX omr_active_role_uq ON organization_membership_roles
    (organization_membership_id, role) WHERE revoked_at IS NULL;

CREATE TABLE permission_categories (
    code TEXT PRIMARY KEY,
    label TEXT NOT NULL
);

INSERT INTO permission_categories (code, label) VALUES
    ('CLASS_STUDENT_GUARDIAN', 'Sınıf, öğrenci ve veli yönetimi'),
    ('PROGRAM', 'Program ve değerlendirme yönetimi'),
    ('ATTENDANCE_REPORTING', 'Yoklama ve raporlama'),
    ('ORG_SETTINGS', 'Kurum ayarları'),
    ('STAFF_MANAGEMENT', 'Personel yönetimi');

CREATE TABLE permission_catalog (
    code TEXT PRIMARY KEY,
    category_code TEXT NOT NULL REFERENCES permission_categories(code),
    label TEXT NOT NULL,
    description TEXT,
    introduced_in_version TEXT NOT NULL
);

INSERT INTO permission_catalog (code, category_code, label, description, introduced_in_version) VALUES
    ('CLASS_MANAGE', 'CLASS_STUDENT_GUARDIAN', 'Sınıf yönetimi', 'Sınıf oluşturma, düzenleme ve arşivleme', '1.0'),
    ('STUDENT_MANAGE', 'CLASS_STUDENT_GUARDIAN', 'Öğrenci yönetimi', 'Öğrenci ekleme, düzenleme ve arşivleme', '1.0'),
    ('GUARDIAN_MANAGE', 'CLASS_STUDENT_GUARDIAN', 'Veli yönetimi', 'Anne ve baba bilgilerini yönetme', '1.0'),
    ('GUARDIAN_CONTACT_VIEW', 'CLASS_STUDENT_GUARDIAN', 'Veli iletişim bilgisi görüntüleme', 'Veli telefon ve adres bilgilerini görme', '1.0'),
    ('RESTORE_ARCHIVED', 'CLASS_STUDENT_GUARDIAN', 'Arşivden geri yükleme', 'Arşivlenmiş öğrenci ve sınıfları geri yükleme', '1.0'),
    ('TERM_CALENDAR_MANAGE', 'CLASS_STUDENT_GUARDIAN', 'Dönem ve takvim yönetimi', 'Eğitim dönemi, çalışma günleri ve tatil ayarları', '1.0'),
    ('PROGRAM_MANAGE', 'PROGRAM', 'Program yönetimi', 'Program oluşturma, düzenleme ve şablon dağıtımı', '1.0'),
    ('EVALUATION_SCHEMA_MANAGE', 'PROGRAM', 'Değerlendirme şeması yönetimi', 'Puan, not ve tekrar gerekli alanlarını ayarlama', '1.0'),
    ('ATTENDANCE_BACKDATE_CORRECT', 'ATTENDANCE_REPORTING', 'Geçmiş yoklama düzeltme', 'Geçmiş tarihli yoklama kayıtlarını düzeltme', '1.0'),
    ('REPORT_EXPORT', 'ATTENDANCE_REPORTING', 'Rapor dışa aktarma', 'Excel raporu oluşturma ve indirme', '1.0'),
    ('AUDIT_LOG_VIEW', 'ATTENDANCE_REPORTING', 'Denetim kaydı görüntüleme', 'Kurum denetim geçmişini görme', '1.0'),
    ('AUDIT_UNDO', 'ATTENDANCE_REPORTING', 'Denetim geri alma', 'Desteklenen işlemleri geri alma', '1.0'),
    ('BRAND_MANAGE', 'ORG_SETTINGS', 'Marka ayarları', 'Kurum adı, logo ve renk yönetimi', '1.0'),
    ('MODULE_MANAGE', 'ORG_SETTINGS', 'Modül yönetimi', 'Etkin modülleri belirleme', '1.0'),
    ('CUSTOM_ATTENDANCE_STATUS_MANAGE', 'ORG_SETTINGS', 'Özel yoklama durumu yönetimi', 'Kuruma özel yoklama durumları tanımlama', '1.0'),
    ('TEACHER_ACCOUNT_MANAGE', 'STAFF_MANAGEMENT', 'Hoca hesabı yönetimi', 'Hoca hesabı oluşturma ve kapatma', '1.0'),
    ('TEACHER_CLASS_ASSIGN', 'STAFF_MANAGEMENT', 'Hoca sınıf ataması', 'Hocaları sınıflara atama', '1.0'),
    ('TEACHER_PERMISSION_VIEW', 'STAFF_MANAGEMENT', 'Hoca izin görüntüleme', 'Hoca izin durumunu görme', '1.0'),
    ('DEVICE_SESSION_REVOKE', 'STAFF_MANAGEMENT', 'Cihaz oturumu iptali', 'Kullanıcının cihaz oturumlarını iptal etme', '1.0');

CREATE TABLE organization_membership_permissions (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL REFERENCES organizations(id),
    target_membership_role_id UUID NOT NULL,
    target_role_code membership_role_enum NOT NULL DEFAULT 'TEACHER'
        CHECK (target_role_code = 'TEACHER'),
    permission_code TEXT NOT NULL REFERENCES permission_catalog(code),
    granted_by_membership_role_id UUID,
    granted_role_code membership_role_enum
        CHECK (granted_role_code IS NULL OR granted_role_code = 'ORG_ADMIN'),
    granted_by_platform_admin_user_id UUID,
    granted_at TIMESTAMPTZ NOT NULL,
    revoked_at TIMESTAMPTZ,
    CHECK (num_nonnulls(granted_by_membership_role_id, granted_by_platform_admin_user_id) = 1),
    CHECK ((granted_by_membership_role_id IS NULL AND granted_role_code IS NULL)
        OR (granted_by_membership_role_id IS NOT NULL AND granted_role_code = 'ORG_ADMIN')),
    FOREIGN KEY (target_membership_role_id, organization_id, target_role_code)
        REFERENCES organization_membership_roles (id, organization_id, role),
    FOREIGN KEY (granted_by_membership_role_id, organization_id, granted_role_code)
        REFERENCES organization_membership_roles (id, organization_id, role)
);

CREATE UNIQUE INDEX omp_active_permission_uq ON organization_membership_permissions
    (target_membership_role_id, permission_code) WHERE revoked_at IS NULL;

CREATE TABLE trusted_devices (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id),
    device_identifier UUID NOT NULL,
    device_name TEXT,
    platform device_platform_enum NOT NULL,
    trusted_at TIMESTAMPTZ NOT NULL DEFAULT transaction_timestamp(),
    last_seen_at TIMESTAMPTZ,
    revoked_at TIMESTAMPTZ,
    UNIQUE (id, user_id)
);

CREATE UNIQUE INDEX trusted_devices_active_uq ON trusted_devices
    (user_id, device_identifier) WHERE revoked_at IS NULL;

CREATE TABLE context_selection_tokens (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id),
    trusted_device_id UUID NOT NULL,
    token_hash TEXT NOT NULL UNIQUE,
    authenticated_at TIMESTAMPTZ NOT NULL,
    issued_at TIMESTAMPTZ NOT NULL DEFAULT transaction_timestamp(),
    expires_at TIMESTAMPTZ NOT NULL,
    consumed_at TIMESTAMPTZ,
    revoked_at TIMESTAMPTZ,
    CHECK (issued_at < expires_at),
    CHECK (expires_at = issued_at + INTERVAL '5 minutes'),
    FOREIGN KEY (trusted_device_id, user_id) REFERENCES trusted_devices (id, user_id)
);

CREATE TABLE refresh_token_families (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id),
    trusted_device_id UUID NOT NULL,
    organization_membership_id UUID,
    authenticated_at TIMESTAMPTZ NOT NULL,
    issued_at_session_generation INTEGER,
    revoked_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT transaction_timestamp(),
    FOREIGN KEY (trusted_device_id, user_id) REFERENCES trusted_devices (id, user_id),
    FOREIGN KEY (organization_membership_id, user_id)
        REFERENCES organization_memberships (id, user_id),
    CHECK (
        organization_membership_id IS NULL
        OR issued_at_session_generation IS NOT NULL
    )
);

CREATE TABLE refresh_tokens (
    id UUID PRIMARY KEY,
    family_id UUID NOT NULL REFERENCES refresh_token_families(id),
    previous_refresh_token_id UUID REFERENCES refresh_tokens(id),
    token_hash TEXT NOT NULL UNIQUE,
    access_token_hash TEXT NOT NULL UNIQUE,
    access_expires_at TIMESTAMPTZ NOT NULL,
    issued_at TIMESTAMPTZ NOT NULL DEFAULT transaction_timestamp(),
    used_at TIMESTAMPTZ,
    expires_at TIMESTAMPTZ NOT NULL,
    revoked_at TIMESTAMPTZ,
    CHECK (issued_at < expires_at),
    CHECK (used_at IS NULL OR issued_at <= used_at),
    CHECK (revoked_at IS NULL OR used_at IS NULL OR used_at <= revoked_at),
    CHECK (revoked_at IS NULL OR issued_at <= revoked_at),
    UNIQUE (id, family_id)
);

CREATE UNIQUE INDEX refresh_tokens_previous_uq ON refresh_tokens
    (previous_refresh_token_id) WHERE previous_refresh_token_id IS NOT NULL;

ALTER TABLE refresh_tokens
    ADD CONSTRAINT refresh_tokens_same_family_fk
    FOREIGN KEY (previous_refresh_token_id, family_id)
    REFERENCES refresh_tokens (id, family_id);

CREATE TABLE iam_provider_commands (
    id UUID PRIMARY KEY,
    idempotency_key TEXT NOT NULL UNIQUE,
    provider TEXT NOT NULL,
    command_type provider_command_type_enum NOT NULL,
    target_user_id UUID REFERENCES users(id),
    target_identity_id UUID REFERENCES user_identities(id),
    organization_id UUID REFERENCES organizations(id),
    username_lookup_hash TEXT,
    payload_fingerprint TEXT NOT NULL,
    encrypted_command_payload BYTEA,
    payload_key_id TEXT,
    status provider_command_status_enum NOT NULL DEFAULT 'PENDING',
    attempt_count INTEGER NOT NULL DEFAULT 0,
    next_attempt_at TIMESTAMPTZ NOT NULL DEFAULT transaction_timestamp(),
    lease_expires_at TIMESTAMPTZ,
    fencing_token BIGINT NOT NULL DEFAULT 0,
    lease_owner TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT transaction_timestamp(),
    completed_at TIMESTAMPTZ,
    last_safe_error_code TEXT,
    CHECK (
        (command_type = 'TEACHER_ACCOUNT_CREATE'
            AND target_user_id IS NOT NULL
            AND organization_id IS NOT NULL
            AND username_lookup_hash IS NOT NULL
            AND encrypted_command_payload IS NOT NULL
            AND payload_key_id IS NOT NULL
            AND target_identity_id IS NULL)
        OR
        (command_type IN ('USER_DISABLE', 'USER_LOGOUT', 'PASSWORD_RESET')
            AND target_identity_id IS NOT NULL
            AND target_user_id IS NULL)
    )
);

CREATE UNIQUE INDEX ipc_create_provisioning_uq ON iam_provider_commands
    (provider, command_type, organization_id, username_lookup_hash)
    WHERE command_type = 'TEACHER_ACCOUNT_CREATE';

CREATE TABLE iam_secret_deliveries (
    id UUID PRIMARY KEY,
    provider_command_id UUID NOT NULL UNIQUE REFERENCES iam_provider_commands(id),
    recipient_actor_user_id UUID NOT NULL REFERENCES users(id),
    encrypted_secret BYTEA,
    payload_key_id TEXT,
    status secret_delivery_status_enum NOT NULL DEFAULT 'ESCROWED',
    created_at TIMESTAMPTZ NOT NULL DEFAULT transaction_timestamp(),
    ready_at TIMESTAMPTZ,
    expires_at TIMESTAMPTZ NOT NULL,
    consumed_at TIMESTAMPTZ,
    CHECK (created_at < expires_at AND expires_at <= created_at + INTERVAL '10 minutes'),
    CHECK (
        (status = 'ESCROWED' AND ready_at IS NULL AND consumed_at IS NULL
            AND encrypted_secret IS NOT NULL AND payload_key_id IS NOT NULL)
        OR (status = 'READY' AND ready_at IS NOT NULL AND ready_at < expires_at AND consumed_at IS NULL
            AND encrypted_secret IS NOT NULL AND payload_key_id IS NOT NULL)
        OR (status = 'CONSUMED' AND ready_at IS NOT NULL AND ready_at <= consumed_at AND consumed_at <= expires_at
            AND encrypted_secret IS NULL AND payload_key_id IS NULL)
        OR (status = 'EXPIRED' AND consumed_at IS NULL
            AND encrypted_secret IS NULL AND payload_key_id IS NULL)
    )
);

CREATE TABLE iam_event_cursors (
    id UUID PRIMARY KEY,
    source event_source_enum NOT NULL UNIQUE,
    realm TEXT NOT NULL,
    last_event_time TIMESTAMPTZ,
    last_event_id TEXT,
    last_successful_poll_at TIMESTAMPTZ,
    status TEXT NOT NULL DEFAULT 'ACTIVE',
    updated_at TIMESTAMPTZ NOT NULL DEFAULT transaction_timestamp()
);

CREATE TABLE iam_event_deduplications (
    source event_source_enum NOT NULL,
    event_id TEXT NOT NULL,
    event_time TIMESTAMPTZ NOT NULL,
    processed_at TIMESTAMPTZ NOT NULL DEFAULT transaction_timestamp(),
    PRIMARY KEY (source, event_id)
);

CREATE TABLE idempotency_keys (
    id UUID PRIMARY KEY,
    scope_type idempotency_scope_enum NOT NULL,
    organization_id UUID REFERENCES organizations(id),
    user_id UUID NOT NULL REFERENCES users(id),
    client_mutation_id TEXT NOT NULL,
    operation_type TEXT NOT NULL,
    request_fingerprint TEXT NOT NULL,
    status idempotency_status_enum NOT NULL DEFAULT 'PENDING',
    result_entity_id UUID,
    terminal_http_status SMALLINT,
    terminal_error_code TEXT,
    result_payload JSONB,
    result_reference TEXT,
    lease_owner TEXT,
    lease_generation BIGINT,
    lease_expires_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT transaction_timestamp(),
    completed_at TIMESTAMPTZ,
    result_expires_at TIMESTAMPTZ,
    key_retention_expires_at TIMESTAMPTZ NOT NULL,
    CHECK (
        ((scope_type = 'GLOBAL' OR scope_type = 'IAM_AUTH') AND organization_id IS NULL)
        OR (scope_type = 'ORGANIZATION' AND organization_id IS NOT NULL)
    ),
    CHECK (result_payload IS NULL OR result_reference IS NULL),
    CHECK (
        (status = 'PENDING'
            AND completed_at IS NULL
            AND lease_owner IS NOT NULL
            AND lease_generation IS NOT NULL
            AND lease_expires_at IS NOT NULL
            AND terminal_http_status IS NULL
            AND terminal_error_code IS NULL
            AND result_entity_id IS NULL
            AND result_payload IS NULL
            AND result_reference IS NULL
            AND result_expires_at IS NULL
            AND key_retention_expires_at >= lease_expires_at)
        OR (status = 'COMPLETED'
            AND completed_at IS NOT NULL
            AND completed_at >= created_at
            AND lease_owner IS NULL
            AND lease_generation IS NULL
            AND lease_expires_at IS NULL
            AND terminal_http_status BETWEEN 200 AND 299
            AND terminal_error_code IS NULL
            AND key_retention_expires_at > completed_at)
        OR (status = 'FAILED'
            AND completed_at IS NOT NULL
            AND completed_at >= created_at
            AND lease_owner IS NULL
            AND lease_generation IS NULL
            AND lease_expires_at IS NULL
            AND terminal_http_status IN (400, 403, 404, 409, 422)
            AND terminal_error_code IS NOT NULL
            AND result_entity_id IS NULL
            AND result_payload IS NULL
            AND result_reference IS NULL
            AND result_expires_at IS NULL
            AND key_retention_expires_at > completed_at)
    ),
    CHECK (
        (result_payload IS NULL AND result_reference IS NULL AND result_expires_at IS NULL)
        OR (completed_at IS NOT NULL
            AND result_expires_at >= completed_at
            AND result_expires_at <= key_retention_expires_at)
    ),
    CHECK (key_retention_expires_at >= created_at)
);

CREATE UNIQUE INDEX idempotency_keys_global_scope_uq
    ON idempotency_keys (user_id, client_mutation_id) WHERE scope_type = 'GLOBAL';
CREATE UNIQUE INDEX idempotency_keys_organization_scope_uq
    ON idempotency_keys (organization_id, user_id, client_mutation_id) WHERE scope_type = 'ORGANIZATION';
CREATE UNIQUE INDEX idempotency_keys_iam_auth_scope_uq
    ON idempotency_keys (user_id, client_mutation_id) WHERE scope_type = 'IAM_AUTH';

CREATE TABLE iam_auth_response_escrows (
    id UUID PRIMARY KEY,
    idempotency_key_id UUID NOT NULL UNIQUE REFERENCES idempotency_keys(id),
    actor_user_id UUID NOT NULL REFERENCES users(id),
    operation_type TEXT NOT NULL,
    device_identifier UUID NOT NULL,
    token_fingerprint TEXT NOT NULL,
    result_context_token_id UUID REFERENCES context_selection_tokens(id),
    result_refresh_token_family_id UUID REFERENCES refresh_token_families(id),
    result_refresh_token_id UUID REFERENCES refresh_tokens(id),
    ciphertext BYTEA,
    aead_key_reference TEXT,
    aead_nonce BYTEA,
    aad_context TEXT,
    status escrow_status_enum NOT NULL DEFAULT 'READY',
    expires_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT transaction_timestamp(),
    deleted_at TIMESTAMPTZ,
    CHECK (created_at < expires_at),
    CHECK (
        (operation_type IN ('PROVIDER_TOKEN_EXCHANGE', 'PLATFORM_ADMIN_ACTIVATE', 'CONTEXT_ACTIVATE')
            AND expires_at = created_at + INTERVAL '5 minutes')
        OR (operation_type = 'SESSION_REFRESH'
            AND expires_at = created_at + INTERVAL '10 minutes')
    ),
    CHECK (
        (status = 'READY'
            AND deleted_at IS NULL
            AND ciphertext IS NOT NULL
            AND aead_key_reference IS NOT NULL
            AND aead_nonce IS NOT NULL
            AND aad_context IS NOT NULL)
        OR (status IN ('EXPIRED', 'REVOKED')
            AND deleted_at IS NOT NULL
            AND deleted_at >= created_at
            AND ciphertext IS NULL
            AND aead_key_reference IS NULL
            AND aead_nonce IS NULL
            AND aad_context IS NULL)
    ),
    CHECK (num_nonnulls(result_context_token_id, result_refresh_token_family_id, result_refresh_token_id) >= 1),
    CHECK (
        (operation_type = 'PROVIDER_TOKEN_EXCHANGE'
            AND result_context_token_id IS NOT NULL
            AND result_refresh_token_family_id IS NULL
            AND result_refresh_token_id IS NULL)
        OR (operation_type IN ('PLATFORM_ADMIN_ACTIVATE', 'CONTEXT_ACTIVATE')
            AND result_context_token_id IS NULL
            AND result_refresh_token_family_id IS NOT NULL
            AND result_refresh_token_id IS NOT NULL)
        OR (operation_type = 'SESSION_REFRESH'
            AND result_context_token_id IS NULL
            AND result_refresh_token_family_id IS NOT NULL
            AND result_refresh_token_id IS NOT NULL)
    )
);

-- RLS rolleri ve politikaları

CREATE ROLE iam_runtime WITH LOGIN NOCREATEDB NOCREATEROLE NOINHERIT;
CREATE ROLE app_runtime WITH LOGIN NOCREATEDB NOCREATEROLE NOINHERIT;

REVOKE ALL ON SCHEMA public FROM iam_runtime, app_runtime;
GRANT USAGE ON SCHEMA public TO iam_runtime, app_runtime;

REVOKE ALL ON ALL TABLES IN SCHEMA public FROM app_runtime;
REVOKE ALL ON ALL SEQUENCES IN SCHEMA public FROM app_runtime;

ALTER DEFAULT PRIVILEGES FOR ROLE current_user IN SCHEMA public
    REVOKE ALL ON TABLES FROM app_runtime;
ALTER DEFAULT PRIVILEGES FOR ROLE current_user IN SCHEMA public
    REVOKE ALL ON SEQUENCES FROM app_runtime;

GRANT SELECT, INSERT, UPDATE ON users TO iam_runtime;
GRANT SELECT, INSERT ON user_identities TO iam_runtime;
GRANT SELECT ON platform_administrators TO iam_runtime;
GRANT SELECT ON platform_administrator_profiles TO iam_runtime;
GRANT SELECT, INSERT, UPDATE ON trusted_devices TO iam_runtime;
GRANT SELECT, INSERT, UPDATE ON organization_memberships TO iam_runtime;
GRANT SELECT, INSERT, UPDATE ON organization_membership_roles TO iam_runtime;
GRANT SELECT ON organization_membership_permissions TO iam_runtime;
GRANT SELECT ON permission_catalog TO iam_runtime;
GRANT SELECT, INSERT, UPDATE ON context_selection_tokens TO iam_runtime;
GRANT SELECT, INSERT, UPDATE ON refresh_token_families TO iam_runtime;
GRANT SELECT, INSERT, UPDATE ON refresh_tokens TO iam_runtime;
GRANT SELECT, INSERT, UPDATE ON idempotency_keys TO iam_runtime;
GRANT SELECT, INSERT, UPDATE ON iam_auth_response_escrows TO iam_runtime;
GRANT SELECT, INSERT, UPDATE ON iam_provider_commands TO iam_runtime;
GRANT SELECT, INSERT, UPDATE ON iam_secret_deliveries TO iam_runtime;
GRANT SELECT, INSERT, UPDATE ON iam_event_cursors TO iam_runtime;
GRANT SELECT, INSERT ON iam_event_deduplications TO iam_runtime;
GRANT SELECT ON people TO iam_runtime;

ALTER TABLE users ENABLE ROW LEVEL SECURITY;
ALTER TABLE users FORCE ROW LEVEL SECURITY;
ALTER TABLE user_identities ENABLE ROW LEVEL SECURITY;
ALTER TABLE user_identities FORCE ROW LEVEL SECURITY;
ALTER TABLE platform_administrators ENABLE ROW LEVEL SECURITY;
ALTER TABLE platform_administrators FORCE ROW LEVEL SECURITY;
ALTER TABLE platform_administrator_profiles ENABLE ROW LEVEL SECURITY;
ALTER TABLE platform_administrator_profiles FORCE ROW LEVEL SECURITY;
ALTER TABLE trusted_devices ENABLE ROW LEVEL SECURITY;
ALTER TABLE trusted_devices FORCE ROW LEVEL SECURITY;
ALTER TABLE organization_memberships ENABLE ROW LEVEL SECURITY;
ALTER TABLE organization_memberships FORCE ROW LEVEL SECURITY;
ALTER TABLE organization_membership_roles ENABLE ROW LEVEL SECURITY;
ALTER TABLE organization_membership_roles FORCE ROW LEVEL SECURITY;
ALTER TABLE organization_membership_permissions ENABLE ROW LEVEL SECURITY;
ALTER TABLE organization_membership_permissions FORCE ROW LEVEL SECURITY;
ALTER TABLE context_selection_tokens ENABLE ROW LEVEL SECURITY;
ALTER TABLE context_selection_tokens FORCE ROW LEVEL SECURITY;
ALTER TABLE refresh_token_families ENABLE ROW LEVEL SECURITY;
ALTER TABLE refresh_token_families FORCE ROW LEVEL SECURITY;
ALTER TABLE refresh_tokens ENABLE ROW LEVEL SECURITY;
ALTER TABLE refresh_tokens FORCE ROW LEVEL SECURITY;
ALTER TABLE idempotency_keys ENABLE ROW LEVEL SECURITY;
ALTER TABLE idempotency_keys FORCE ROW LEVEL SECURITY;
ALTER TABLE iam_auth_response_escrows ENABLE ROW LEVEL SECURITY;
ALTER TABLE iam_auth_response_escrows FORCE ROW LEVEL SECURITY;
ALTER TABLE iam_provider_commands ENABLE ROW LEVEL SECURITY;
ALTER TABLE iam_provider_commands FORCE ROW LEVEL SECURITY;
ALTER TABLE iam_secret_deliveries ENABLE ROW LEVEL SECURITY;
ALTER TABLE iam_secret_deliveries FORCE ROW LEVEL SECURITY;
ALTER TABLE iam_event_cursors ENABLE ROW LEVEL SECURITY;
ALTER TABLE iam_event_cursors FORCE ROW LEVEL SECURITY;
ALTER TABLE iam_event_deduplications ENABLE ROW LEVEL SECURITY;
ALTER TABLE iam_event_deduplications FORCE ROW LEVEL SECURITY;
ALTER TABLE people ENABLE ROW LEVEL SECURITY;
ALTER TABLE people FORCE ROW LEVEL SECURITY;

CREATE POLICY users_select_global ON users FOR SELECT USING (
    current_user = 'iam_runtime'
    AND current_setting('app.iam_operation_scope', true) = 'GLOBAL'
    AND id = current_setting('app.iam_target_user_id', true)::uuid
);

CREATE POLICY users_select_iam_auth ON users FOR SELECT USING (
    current_user = 'iam_runtime'
    AND current_setting('app.iam_operation_scope', true) = 'IAM_AUTH'
    AND id = current_setting('app.iam_actor_user_id', true)::uuid
);

CREATE POLICY users_update_iam_auth ON users FOR UPDATE USING (
    current_user = 'iam_runtime'
    AND current_setting('app.iam_operation_scope', true) = 'IAM_AUTH'
    AND id = current_setting('app.iam_actor_user_id', true)::uuid
);

CREATE POLICY user_identities_select_auth ON user_identities FOR SELECT USING (
    current_user = 'iam_runtime'
    AND current_setting('app.iam_operation_scope', true) = 'AUTHENTICATION'
    AND issuer = current_setting('app.iam_provider_issuer', true)
    AND subject = current_setting('app.iam_provider_subject', true)
);

CREATE POLICY user_identities_select_global_provider_command ON user_identities FOR SELECT USING (
    current_user = 'iam_runtime'
    AND current_setting('app.iam_operation_scope', true) = 'GLOBAL'
    AND current_setting('app.iam_operation_code', true) IN ('USER_DISABLE', 'USER_LOGOUT', 'PASSWORD_RESET')
    AND id = current_setting('app.iam_target_identity_id', true)::uuid
    AND user_id = current_setting('app.iam_target_user_id', true)::uuid
);

CREATE POLICY platform_administrators_select_iam_auth ON platform_administrators FOR SELECT USING (
    current_user = 'iam_runtime'
    AND current_setting('app.iam_operation_scope', true) = 'IAM_AUTH'
    AND current_setting('app.iam_operation_code', true) = 'PLATFORM_ADMIN_ACTIVATE'
    AND user_id = current_setting('app.iam_actor_user_id', true)::uuid
    AND revoked_at IS NULL
);

CREATE POLICY platform_administrators_select_platform_device_revoke ON platform_administrators FOR SELECT USING (
    current_user = 'iam_runtime'
    AND current_setting('app.iam_operation_scope', true) = 'GLOBAL'
    AND current_setting('app.iam_operation_code', true) = 'PLATFORM_DEVICE_REVOKE'
    AND user_id = current_setting('app.iam_actor_user_id', true)::uuid
    AND revoked_at IS NULL
);

CREATE POLICY trusted_devices_select_self ON trusted_devices FOR SELECT USING (
    current_user = 'iam_runtime'
    AND current_setting('app.iam_operation_scope', true) = 'IAM_AUTH'
    AND current_setting('app.iam_operation_code', true) IN ('DEVICE_LIST', 'DEVICE_SELF_REVOKE')
    AND user_id = current_setting('app.iam_actor_user_id', true)::uuid
);

CREATE POLICY trusted_devices_select_provider_exchange ON trusted_devices FOR SELECT USING (
    current_user = 'iam_runtime'
    AND current_setting('app.iam_operation_scope', true) = 'IAM_AUTH'
    AND current_setting('app.iam_operation_code', true) = 'PROVIDER_TOKEN_EXCHANGE'
    AND user_id = current_setting('app.iam_actor_user_id', true)::uuid
    AND device_identifier = current_setting('app.iam_provider_device_identifier', true)::uuid
);

CREATE POLICY trusted_devices_select_platform_revoke ON trusted_devices FOR SELECT USING (
    current_user = 'iam_runtime'
    AND current_setting('app.iam_operation_scope', true) = 'GLOBAL'
    AND current_setting('app.iam_operation_code', true) = 'PLATFORM_DEVICE_REVOKE'
    AND user_id = current_setting('app.iam_target_user_id', true)::uuid
    AND id = current_setting('app.iam_target_device_id', true)::uuid
);

CREATE POLICY trusted_devices_insert_provider_exchange ON trusted_devices FOR INSERT WITH CHECK (
    current_user = 'iam_runtime'
    AND current_setting('app.iam_operation_scope', true) = 'IAM_AUTH'
    AND current_setting('app.iam_operation_code', true) = 'PROVIDER_TOKEN_EXCHANGE'
    AND user_id = current_setting('app.iam_actor_user_id', true)::uuid
    AND device_identifier = current_setting('app.iam_provider_device_identifier', true)::uuid
    AND NOT EXISTS (
        SELECT 1 FROM trusted_devices old
        WHERE old.user_id = current_setting('app.iam_actor_user_id', true)::uuid
          AND old.device_identifier = current_setting('app.iam_provider_device_identifier', true)::uuid
          AND old.revoked_at IS NOT NULL
          AND old.revoked_at >= current_setting('app.iam_verified_auth_time', true)::timestamptz
    )
);

CREATE POLICY trusted_devices_update_self_revoke ON trusted_devices FOR UPDATE USING (
    current_user = 'iam_runtime'
    AND current_setting('app.iam_operation_scope', true) = 'IAM_AUTH'
    AND current_setting('app.iam_operation_code', true) = 'DEVICE_SELF_REVOKE'
    AND user_id = current_setting('app.iam_actor_user_id', true)::uuid
    AND id = current_setting('app.iam_target_device_id', true)::uuid
    AND revoked_at IS NULL
) WITH CHECK (
    revoked_at = transaction_timestamp()
);

CREATE POLICY trusted_devices_update_platform_revoke ON trusted_devices FOR UPDATE USING (
    current_user = 'iam_runtime'
    AND current_setting('app.iam_operation_scope', true) = 'GLOBAL'
    AND current_setting('app.iam_operation_code', true) = 'PLATFORM_DEVICE_REVOKE'
    AND user_id = current_setting('app.iam_target_user_id', true)::uuid
    AND id = current_setting('app.iam_target_device_id', true)::uuid
    AND revoked_at IS NULL
) WITH CHECK (
    revoked_at = transaction_timestamp()
);

REVOKE UPDATE ON trusted_devices FROM iam_runtime;
GRANT UPDATE (revoked_at) ON trusted_devices TO iam_runtime;

CREATE POLICY organization_memberships_select_context_activate ON organization_memberships FOR SELECT USING (
    current_user = 'iam_runtime'
    AND current_setting('app.iam_operation_scope', true) = 'IAM_AUTH'
    AND current_setting('app.iam_operation_code', true) = 'CONTEXT_ACTIVATE'
    AND id = current_setting('app.iam_target_membership_id', true)::uuid
    AND user_id = current_setting('app.iam_actor_user_id', true)::uuid
    AND organization_id = current_setting('app.iam_target_organization_id', true)::uuid
);

CREATE POLICY organization_membership_roles_select_context_activate ON organization_membership_roles FOR SELECT USING (
    current_user = 'iam_runtime'
    AND current_setting('app.iam_operation_scope', true) = 'IAM_AUTH'
    AND current_setting('app.iam_operation_code', true) = 'CONTEXT_ACTIVATE'
    AND organization_membership_id = current_setting('app.iam_target_membership_id', true)::uuid
    AND revoked_at IS NULL
);

CREATE POLICY context_selection_tokens_select_iam_auth ON context_selection_tokens FOR SELECT USING (
    current_user = 'iam_runtime'
    AND current_setting('app.iam_operation_scope', true) = 'IAM_AUTH'
    AND user_id = current_setting('app.iam_actor_user_id', true)::uuid
    AND EXISTS (
        SELECT 1 FROM trusted_devices td
        WHERE td.id = trusted_device_id
          AND td.user_id = current_setting('app.iam_actor_user_id', true)::uuid
          AND td.revoked_at IS NULL
    )
);

CREATE POLICY context_selection_tokens_insert_iam_auth ON context_selection_tokens FOR INSERT WITH CHECK (
    current_user = 'iam_runtime'
    AND current_setting('app.iam_operation_scope', true) = 'IAM_AUTH'
    AND user_id = current_setting('app.iam_actor_user_id', true)::uuid
);

CREATE POLICY context_selection_tokens_update_iam_auth ON context_selection_tokens FOR UPDATE USING (
    current_user = 'iam_runtime'
    AND current_setting('app.iam_operation_scope', true) = 'IAM_AUTH'
    AND user_id = current_setting('app.iam_actor_user_id', true)::uuid
);

-- broad refresh_token_families_select_iam_auth replaced by operation-specific policies

CREATE POLICY refresh_token_families_insert_iam_auth ON refresh_token_families FOR INSERT WITH CHECK (
    current_user = 'iam_runtime'
    AND current_setting('app.iam_operation_scope', true) = 'IAM_AUTH'
    AND user_id = current_setting('app.iam_actor_user_id', true)::uuid
);

-- broad refresh_tokens_select_iam_auth replaced by operation-specific policies

CREATE POLICY refresh_tokens_insert_iam_auth ON refresh_tokens FOR INSERT WITH CHECK (
    current_user = 'iam_runtime'
    AND current_setting('app.iam_operation_scope', true) = 'IAM_AUTH'
    AND EXISTS (
        SELECT 1 FROM refresh_token_families rtf
        WHERE rtf.id = family_id
          AND rtf.user_id = current_setting('app.iam_actor_user_id', true)::uuid
    )
);

CREATE POLICY idempotency_keys_select_iam_auth ON idempotency_keys FOR SELECT USING (
    current_user = 'iam_runtime'
    AND current_setting('app.iam_operation_scope', true) = 'IAM_AUTH'
    AND scope_type = 'IAM_AUTH'
    AND organization_id IS NULL
    AND user_id = current_setting('app.iam_actor_user_id', true)::uuid
    AND operation_type = current_setting('app.iam_operation_code', true)
);

CREATE POLICY idempotency_keys_insert_iam_auth ON idempotency_keys FOR INSERT WITH CHECK (
    current_user = 'iam_runtime'
    AND current_setting('app.iam_operation_scope', true) = 'IAM_AUTH'
    AND scope_type = 'IAM_AUTH'
    AND organization_id IS NULL
    AND user_id = current_setting('app.iam_actor_user_id', true)::uuid
    AND operation_type = current_setting('app.iam_operation_code', true)
);

CREATE POLICY iam_auth_response_escrows_select_iam_auth ON iam_auth_response_escrows FOR SELECT USING (
    current_user = 'iam_runtime'
    AND current_setting('app.iam_operation_scope', true) = 'IAM_AUTH'
    AND actor_user_id = current_setting('app.iam_actor_user_id', true)::uuid
    AND operation_type = current_setting('app.iam_operation_code', true)
);

CREATE POLICY iam_auth_response_escrows_insert_iam_auth ON iam_auth_response_escrows FOR INSERT WITH CHECK (
    current_user = 'iam_runtime'
    AND current_setting('app.iam_operation_scope', true) = 'IAM_AUTH'
    AND actor_user_id = current_setting('app.iam_actor_user_id', true)::uuid
    AND operation_type = current_setting('app.iam_operation_code', true)
);

CREATE POLICY people_select_provisioning ON people FOR SELECT USING (
    current_user = 'iam_runtime'
    AND current_setting('app.iam_operation_scope', true) = 'IAM_PROVISIONING'
);

CREATE POLICY organization_memberships_select_org ON organization_memberships FOR SELECT USING (
    current_user = 'iam_runtime'
    AND current_setting('app.iam_operation_scope', true) = 'ORGANIZATION'
    AND organization_id = current_setting('app.organization_id', true)::uuid
);

CREATE POLICY organization_memberships_update_org ON organization_memberships FOR UPDATE USING (
    current_user = 'iam_runtime'
    AND current_setting('app.iam_operation_scope', true) = 'ORGANIZATION'
    AND organization_id = current_setting('app.organization_id', true)::uuid
) WITH CHECK (
    (current_setting('app.iam_operation_code', true) = 'DEVICE_SESSION_REVOKE')
);

CREATE POLICY organization_membership_roles_select_org ON organization_membership_roles FOR SELECT USING (
    current_user = 'iam_runtime'
    AND current_setting('app.iam_operation_scope', true) = 'ORGANIZATION'
    AND organization_id = current_setting('app.organization_id', true)::uuid
);

CREATE POLICY organization_membership_permissions_select_org ON organization_membership_permissions FOR SELECT USING (
    current_user = 'iam_runtime'
    AND current_setting('app.iam_operation_scope', true) = 'ORGANIZATION'
    AND organization_id = current_setting('app.organization_id', true)::uuid
);

CREATE POLICY refresh_token_families_select_org ON refresh_token_families FOR SELECT USING (
    current_user = 'iam_runtime'
    AND current_setting('app.iam_operation_scope', true) = 'ORGANIZATION'
    AND organization_membership_id = current_setting('app.iam_target_membership_id', true)::uuid
);

CREATE POLICY refresh_token_families_update_org ON refresh_token_families FOR UPDATE USING (
    current_user = 'iam_runtime'
    AND current_setting('app.iam_operation_scope', true) = 'ORGANIZATION'
    AND current_setting('app.iam_operation_code', true) = 'DEVICE_SESSION_REVOKE'
    AND organization_membership_id = current_setting('app.iam_target_membership_id', true)::uuid
) WITH CHECK (
    revoked_at IS NOT NULL
);

CREATE POLICY refresh_token_families_select_global ON refresh_token_families FOR SELECT USING (
    current_user = 'iam_runtime'
    AND current_setting('app.iam_operation_scope', true) = 'GLOBAL'
    AND user_id = current_setting('app.iam_target_user_id', true)::uuid
);

CREATE POLICY refresh_token_families_update_global ON refresh_token_families FOR UPDATE USING (
    current_user = 'iam_runtime'
    AND current_setting('app.iam_operation_scope', true) = 'GLOBAL'
    AND user_id = current_setting('app.iam_target_user_id', true)::uuid
) WITH CHECK (
    revoked_at IS NOT NULL
);

CREATE POLICY refresh_tokens_select_org ON refresh_tokens FOR SELECT USING (
    current_user = 'iam_runtime'
    AND current_setting('app.iam_operation_scope', true) = 'ORGANIZATION'
    AND EXISTS (
        SELECT 1 FROM refresh_token_families rtf
        WHERE rtf.id = family_id
          AND rtf.organization_membership_id = current_setting('app.iam_target_membership_id', true)::uuid
    )
);

CREATE POLICY refresh_tokens_update_org ON refresh_tokens FOR UPDATE USING (
    current_user = 'iam_runtime'
    AND current_setting('app.iam_operation_scope', true) = 'ORGANIZATION'
    AND current_setting('app.iam_operation_code', true) = 'DEVICE_SESSION_REVOKE'
    AND EXISTS (
        SELECT 1 FROM refresh_token_families rtf
        WHERE rtf.id = family_id
          AND rtf.organization_membership_id = current_setting('app.iam_target_membership_id', true)::uuid
    )
) WITH CHECK (
    revoked_at IS NOT NULL
);

CREATE POLICY refresh_tokens_select_global ON refresh_tokens FOR SELECT USING (
    current_user = 'iam_runtime'
    AND current_setting('app.iam_operation_scope', true) = 'GLOBAL'
    AND EXISTS (
        SELECT 1 FROM refresh_token_families rtf
        WHERE rtf.id = family_id
          AND rtf.user_id = current_setting('app.iam_target_user_id', true)::uuid
    )
);

CREATE POLICY refresh_tokens_update_global ON refresh_tokens FOR UPDATE USING (
    current_user = 'iam_runtime'
    AND current_setting('app.iam_operation_scope', true) = 'GLOBAL'
    AND EXISTS (
        SELECT 1 FROM refresh_token_families rtf
        WHERE rtf.id = family_id
          AND rtf.user_id = current_setting('app.iam_target_user_id', true)::uuid
    )
) WITH CHECK (
    revoked_at IS NOT NULL
);

CREATE POLICY idempotency_keys_select_org ON idempotency_keys FOR SELECT USING (
    current_user = 'iam_runtime'
    AND current_setting('app.iam_operation_scope', true) = 'ORGANIZATION'
    AND scope_type = 'ORGANIZATION'
    AND organization_id = current_setting('app.organization_id', true)::uuid
    AND user_id = current_setting('app.iam_actor_user_id', true)::uuid
    AND operation_type = current_setting('app.iam_operation_code', true)
);

CREATE POLICY idempotency_keys_insert_org ON idempotency_keys FOR INSERT WITH CHECK (
    current_user = 'iam_runtime'
    AND current_setting('app.iam_operation_scope', true) = 'ORGANIZATION'
    AND scope_type = 'ORGANIZATION'
    AND organization_id = current_setting('app.organization_id', true)::uuid
    AND user_id = current_setting('app.iam_actor_user_id', true)::uuid
    AND operation_type = current_setting('app.iam_operation_code', true)
);

CREATE POLICY idempotency_keys_select_global ON idempotency_keys FOR SELECT USING (
    current_user = 'iam_runtime'
    AND current_setting('app.iam_operation_scope', true) = 'GLOBAL'
    AND scope_type = 'GLOBAL'
    AND organization_id IS NULL
    AND user_id = current_setting('app.iam_actor_user_id', true)::uuid
    AND operation_type = current_setting('app.iam_operation_code', true)
);

CREATE POLICY idempotency_keys_insert_global ON idempotency_keys FOR INSERT WITH CHECK (
    current_user = 'iam_runtime'
    AND current_setting('app.iam_operation_scope', true) = 'GLOBAL'
    AND scope_type = 'GLOBAL'
    AND organization_id IS NULL
    AND user_id = current_setting('app.iam_actor_user_id', true)::uuid
    AND operation_type = current_setting('app.iam_operation_code', true)
);

CREATE POLICY iam_provider_commands_select_global ON iam_provider_commands FOR SELECT USING (
    current_user = 'iam_runtime'
    AND current_setting('app.iam_operation_scope', true) = 'GLOBAL'
    AND current_setting('app.iam_operation_code', true) IN ('USER_DISABLE','USER_LOGOUT','PASSWORD_RESET')
    AND (
        target_user_id = current_setting('app.iam_target_user_id', true)::uuid
        OR EXISTS (
            SELECT 1 FROM user_identities ui WHERE ui.id = target_identity_id
            AND ui.user_id = current_setting('app.iam_target_user_id', true)::uuid
        )
    )
);

CREATE POLICY iam_provider_commands_select_provisioning ON iam_provider_commands FOR SELECT USING (
    current_user = 'iam_runtime'
    AND current_setting('app.iam_operation_scope', true) = 'IAM_PROVISIONING'
    AND current_setting('app.iam_operation_code', true) IN ('TEACHER_ACCOUNT_CREATE', 'TEACHER_ACCOUNT_FINALIZE')
    AND target_user_id = current_setting('app.iam_target_user_id', true)::uuid
    AND organization_id = current_setting('app.organization_id', true)::uuid
);

CREATE POLICY iam_provider_commands_insert_provisioning ON iam_provider_commands FOR INSERT WITH CHECK (
    current_user = 'iam_runtime'
    AND current_setting('app.iam_operation_scope', true) = 'IAM_PROVISIONING'
    AND current_setting('app.iam_operation_code', true) = 'TEACHER_ACCOUNT_CREATE'
    AND target_user_id = current_setting('app.iam_target_user_id', true)::uuid
    AND organization_id = current_setting('app.organization_id', true)::uuid
);

CREATE POLICY iam_secret_deliveries_select_provisioning ON iam_secret_deliveries FOR SELECT USING (
    current_user = 'iam_runtime'
    AND current_setting('app.iam_operation_scope', true) = 'IAM_PROVISIONING'
    AND EXISTS (
        SELECT 1 FROM iam_provider_commands ipc WHERE ipc.id = provider_command_id
        AND ipc.organization_id = current_setting('app.organization_id', true)::uuid
    )
);

CREATE POLICY iam_secret_deliveries_insert_provisioning ON iam_secret_deliveries FOR INSERT WITH CHECK (
    current_user = 'iam_runtime'
    AND current_setting('app.iam_operation_scope', true) = 'IAM_PROVISIONING'
    AND EXISTS (
        SELECT 1 FROM iam_provider_commands ipc WHERE ipc.id = provider_command_id
        AND ipc.organization_id = current_setting('app.organization_id', true)::uuid
    )
);

CREATE POLICY iam_event_cursors_select ON iam_event_cursors FOR SELECT USING (
    current_user = 'iam_runtime'
);

CREATE POLICY iam_event_cursors_insert ON iam_event_cursors FOR INSERT WITH CHECK (
    current_user = 'iam_runtime'
);

CREATE POLICY iam_event_cursors_update ON iam_event_cursors FOR UPDATE USING (
    current_user = 'iam_runtime'
);

CREATE POLICY iam_event_deduplications_select ON iam_event_deduplications FOR SELECT USING (
    current_user = 'iam_runtime'
);

CREATE POLICY iam_event_deduplications_insert ON iam_event_deduplications FOR INSERT WITH CHECK (
    current_user = 'iam_runtime'
);

-- Column-level grants: replace table-wide UPDATE with column-specific

REVOKE UPDATE ON users FROM iam_runtime;
GRANT UPDATE (reauthentication_required_after) ON users TO iam_runtime;

REVOKE UPDATE ON refresh_tokens FROM iam_runtime;
GRANT UPDATE (used_at, revoked_at) ON refresh_tokens TO iam_runtime;

REVOKE UPDATE ON refresh_token_families FROM iam_runtime;
GRANT UPDATE (revoked_at) ON refresh_token_families TO iam_runtime;

REVOKE UPDATE ON idempotency_keys FROM iam_runtime;
GRANT UPDATE (status, result_entity_id, terminal_http_status, terminal_error_code, result_payload, result_reference, completed_at, result_expires_at, lease_owner, lease_generation, lease_expires_at) ON idempotency_keys TO iam_runtime;

REVOKE UPDATE ON iam_auth_response_escrows FROM iam_runtime;
GRANT UPDATE (status, ciphertext, aead_key_reference, aead_nonce, aad_context, deleted_at) ON iam_auth_response_escrows TO iam_runtime;

REVOKE UPDATE ON iam_provider_commands FROM iam_runtime;
GRANT UPDATE (status, attempt_count, next_attempt_at, lease_expires_at, fencing_token, lease_owner, completed_at, last_safe_error_code) ON iam_provider_commands TO iam_runtime;

-- Provider command state machine with fencing trigger

CREATE FUNCTION trg_ipc_state() RETURNS trigger AS $$
DECLARE
    _worker_id TEXT;
    _fencing BIGINT;
BEGIN
    IF TG_OP <> 'UPDATE' THEN RETURN NEW; END IF;
    IF OLD.status IN ('COMPLETED','FAILED') THEN
        RAISE EXCEPTION 'terminal command cannot be updated';
    END IF;
    IF OLD.status = 'PENDING' AND NEW.status = 'CLAIMED' THEN
        IF NEW.attempt_count <> OLD.attempt_count + 1 THEN
            RAISE EXCEPTION 'attempt_count must increment by 1';
        END IF;
        IF NEW.fencing_token IS NULL OR NEW.fencing_token <> COALESCE(OLD.fencing_token, 0) + 1 THEN
            RAISE EXCEPTION 'fencing_token must increment by 1';
        END IF;
        IF NEW.lease_owner IS NULL THEN
            RAISE EXCEPTION 'lease_owner required for CLAIMED';
        END IF;
        IF NEW.lease_expires_at IS NULL OR NEW.lease_expires_at <= transaction_timestamp() THEN
            RAISE EXCEPTION 'lease_expires_at must be future';
        END IF;
        IF NEW.completed_at IS NOT NULL OR NEW.last_safe_error_code IS NOT NULL THEN
            RAISE EXCEPTION 'terminal fields must be NULL';
        END IF;
        RETURN NEW;
    END IF;
    IF OLD.status = 'CLAIMED' AND NEW.status = 'CLAIMED' THEN
        IF OLD.lease_expires_at IS NULL OR OLD.lease_expires_at >= transaction_timestamp() THEN
            RAISE EXCEPTION 'renew only for expired lease';
        END IF;
        IF NEW.fencing_token IS NULL OR NEW.fencing_token <> OLD.fencing_token + 1 THEN
            RAISE EXCEPTION 'fencing_token must increment by 1';
        END IF;
        IF NEW.lease_owner IS NULL THEN
            RAISE EXCEPTION 'lease_owner required';
        END IF;
        IF NEW.lease_expires_at IS NULL OR NEW.lease_expires_at <= transaction_timestamp() THEN
            RAISE EXCEPTION 'lease_expires_at must be future';
        END IF;
        RETURN NEW;
    END IF;
    IF NEW.status IN ('COMPLETED','FAILED') THEN
        IF OLD.status <> 'CLAIMED' THEN
            RAISE EXCEPTION 'only CLAIMED can transition to terminal';
        END IF;
        BEGIN
            _worker_id := current_setting('app.iam_worker_id', true);
            _fencing := current_setting('app.iam_fencing_token', true)::bigint;
        EXCEPTION WHEN OTHERS THEN
            RAISE EXCEPTION 'worker context not set';
        END;
        IF _worker_id IS NULL OR _fencing IS NULL THEN
            RAISE EXCEPTION 'worker context not set';
        END IF;
        IF OLD.lease_owner IS DISTINCT FROM _worker_id THEN
            RAISE EXCEPTION 'lease_owner mismatch: expected %, got %', _worker_id, OLD.lease_owner;
        END IF;
        IF OLD.fencing_token IS DISTINCT FROM _fencing THEN
            RAISE EXCEPTION 'fencing_token mismatch: expected %, got %', _fencing, OLD.fencing_token;
        END IF;
        IF OLD.lease_expires_at IS NULL OR OLD.lease_expires_at < transaction_timestamp() THEN
            RAISE EXCEPTION 'lease already expired';
        END IF;
        IF NEW.completed_at IS NULL THEN
            RAISE EXCEPTION 'completed_at required';
        END IF;
        IF NEW.lease_owner IS NOT NULL OR NEW.lease_expires_at IS NOT NULL THEN
            RAISE EXCEPTION 'lease fields must be cleared';
        END IF;
        RETURN NEW;
    END IF;
    RAISE EXCEPTION 'invalid state: % to %', OLD.status, NEW.status;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_ipc_state
    BEFORE UPDATE ON iam_provider_commands
    FOR EACH ROW EXECUTE FUNCTION trg_ipc_state();

REVOKE UPDATE ON iam_secret_deliveries FROM iam_runtime;
GRANT UPDATE (status, ready_at, consumed_at) ON iam_secret_deliveries TO iam_runtime;

REVOKE UPDATE ON organization_memberships FROM iam_runtime;
GRANT UPDATE (session_generation, reauthentication_required_after) ON organization_memberships TO iam_runtime;

-- Event source-guarded policies (drop old permissive ones first)

DROP POLICY iam_event_cursors_select ON iam_event_cursors;
DROP POLICY iam_event_cursors_insert ON iam_event_cursors;
DROP POLICY iam_event_cursors_update ON iam_event_cursors;
DROP POLICY iam_event_deduplications_select ON iam_event_deduplications;
DROP POLICY iam_event_deduplications_insert ON iam_event_deduplications;

CREATE POLICY iam_event_cursors_select ON iam_event_cursors FOR SELECT USING (
    current_user = 'iam_runtime'
    AND source = current_setting('app.iam_event_source', true)::event_source_enum
);

CREATE POLICY iam_event_cursors_insert ON iam_event_cursors FOR INSERT WITH CHECK (
    current_user = 'iam_runtime'
    AND source = current_setting('app.iam_event_source', true)::event_source_enum
);

CREATE POLICY iam_event_cursors_update ON iam_event_cursors FOR UPDATE USING (
    current_user = 'iam_runtime'
    AND source = current_setting('app.iam_event_source', true)::event_source_enum
    AND current_setting('app.iam_operation_code', true) = 'EVENT_CURSOR_ADVANCE'
);

CREATE POLICY iam_event_deduplications_select ON iam_event_deduplications FOR SELECT USING (
    current_user = 'iam_runtime'
    AND source = current_setting('app.iam_event_source', true)::event_source_enum
);

CREATE POLICY iam_event_deduplications_insert ON iam_event_deduplications FOR INSERT WITH CHECK (
    current_user = 'iam_runtime'
    AND source = current_setting('app.iam_event_source', true)::event_source_enum
);

-- IAM_AUTH session token lifecycle

CREATE POLICY refresh_tokens_select_session_refresh ON refresh_tokens FOR SELECT USING (
    current_user = 'iam_runtime'
    AND current_setting('app.iam_operation_scope', true) = 'IAM_AUTH'
    AND current_setting('app.iam_operation_code', true) = 'SESSION_REFRESH'
    AND EXISTS (
        SELECT 1 FROM refresh_token_families rtf WHERE rtf.id = family_id
        AND rtf.user_id = current_setting('app.iam_actor_user_id', true)::uuid
        AND rtf.trusted_device_id = current_setting('app.iam_current_trusted_device_id', true)::uuid
    )
);

CREATE POLICY refresh_tokens_sr ON refresh_tokens FOR UPDATE USING (
    current_user = 'iam_runtime'
    AND current_setting('app.iam_operation_scope', true) = 'IAM_AUTH'
    AND current_setting('app.iam_operation_code', true) = 'SESSION_REFRESH'
    AND EXISTS (
        SELECT 1 FROM refresh_token_families rtf WHERE rtf.id = family_id
        AND rtf.user_id = current_setting('app.iam_actor_user_id', true)::uuid
        AND rtf.trusted_device_id = current_setting('app.iam_current_trusted_device_id', true)::uuid
    )
    AND used_at IS NULL AND revoked_at IS NULL
) WITH CHECK (used_at = transaction_timestamp());

CREATE POLICY refresh_tokens_sl ON refresh_tokens FOR UPDATE USING (
    current_user = 'iam_runtime'
    AND current_setting('app.iam_operation_scope', true) = 'IAM_AUTH'
    AND current_setting('app.iam_operation_code', true) = 'SESSION_LOGOUT'
    AND EXISTS (
        SELECT 1 FROM refresh_token_families rtf WHERE rtf.id = family_id
        AND rtf.user_id = current_setting('app.iam_actor_user_id', true)::uuid
        AND rtf.trusted_device_id = current_setting('app.iam_current_trusted_device_id', true)::uuid
    )
    AND revoked_at IS NULL
) WITH CHECK (revoked_at = transaction_timestamp());

CREATE POLICY refresh_tokens_select_session_logout ON refresh_tokens FOR SELECT USING (
    current_user = 'iam_runtime'
    AND current_setting('app.iam_operation_scope', true) = 'IAM_AUTH'
    AND current_setting('app.iam_operation_code', true) = 'SESSION_LOGOUT'
    AND EXISTS (
        SELECT 1 FROM refresh_token_families rtf WHERE rtf.id = family_id
        AND rtf.user_id = current_setting('app.iam_actor_user_id', true)::uuid
        AND rtf.trusted_device_id = current_setting('app.iam_current_trusted_device_id', true)::uuid
    )
);

CREATE POLICY refresh_token_families_sl ON refresh_token_families FOR UPDATE USING (
    current_user = 'iam_runtime'
    AND current_setting('app.iam_operation_scope', true) = 'IAM_AUTH'
    AND current_setting('app.iam_operation_code', true) = 'SESSION_LOGOUT'
    AND user_id = current_setting('app.iam_actor_user_id', true)::uuid
    AND revoked_at IS NULL
) WITH CHECK (revoked_at = transaction_timestamp());

-- Idempotency UPDATE all 3 scopes

CREATE POLICY idempotency_keys_upd_iam ON idempotency_keys FOR UPDATE USING (
    current_user = 'iam_runtime'
    AND current_setting('app.iam_operation_scope', true) = 'IAM_AUTH'
    AND scope_type = 'IAM_AUTH' AND organization_id IS NULL
    AND user_id = current_setting('app.iam_actor_user_id', true)::uuid
    AND operation_type = current_setting('app.iam_operation_code', true)
    AND status = 'PENDING'
) WITH CHECK (
    status IN ('COMPLETED','FAILED')
    AND lease_owner IS NULL AND lease_generation IS NULL AND lease_expires_at IS NULL
);

CREATE POLICY idempotency_keys_upd_org ON idempotency_keys FOR UPDATE USING (
    current_user = 'iam_runtime'
    AND current_setting('app.iam_operation_scope', true) = 'ORGANIZATION'
    AND scope_type = 'ORGANIZATION'
    AND organization_id = current_setting('app.organization_id', true)::uuid
    AND user_id = current_setting('app.iam_actor_user_id', true)::uuid
    AND operation_type = current_setting('app.iam_operation_code', true)
    AND status = 'PENDING'
) WITH CHECK (
    status IN ('COMPLETED','FAILED')
    AND lease_owner IS NULL AND lease_generation IS NULL AND lease_expires_at IS NULL
);

CREATE POLICY idempotency_keys_upd_gbl ON idempotency_keys FOR UPDATE USING (
    current_user = 'iam_runtime'
    AND current_setting('app.iam_operation_scope', true) = 'GLOBAL'
    AND scope_type = 'GLOBAL' AND organization_id IS NULL
    AND user_id = current_setting('app.iam_actor_user_id', true)::uuid
    AND operation_type = current_setting('app.iam_operation_code', true)
    AND status = 'PENDING'
) WITH CHECK (
    status IN ('COMPLETED','FAILED')
    AND lease_owner IS NULL AND lease_generation IS NULL AND lease_expires_at IS NULL
);

-- Escrow purge: READY -> EXPIRED/REVOKED

CREATE POLICY iam_auth_response_escrows_upd ON iam_auth_response_escrows FOR UPDATE USING (
    current_user = 'iam_runtime'
    AND current_setting('app.iam_operation_scope', true) = 'IAM_AUTH'
    AND actor_user_id = current_setting('app.iam_actor_user_id', true)::uuid
    AND operation_type = current_setting('app.iam_operation_code', true)
    AND status = 'READY'
    AND (
        expires_at <= transaction_timestamp()
        OR current_setting('app.iam_security_revoke_required', true) = 'true'
    )
) WITH CHECK (
    status IN ('EXPIRED','REVOKED')
    AND ciphertext IS NULL AND aead_key_reference IS NULL
    AND aead_nonce IS NULL AND aad_context IS NULL
    AND deleted_at IS NOT NULL
);

-- Provider command state machine

CREATE POLICY ipc_claim ON iam_provider_commands FOR UPDATE USING (
    current_user = 'iam_runtime'
    AND current_setting('app.iam_operation_scope', true) = 'GLOBAL'
    AND current_setting('app.iam_operation_code', true) = 'PROVIDER_COMMAND_CLAIM'
    AND status = 'PENDING'
) WITH CHECK (
    status = 'CLAIMED'
    AND lease_owner = current_setting('app.iam_worker_id', true)
    AND fencing_token = current_setting('app.iam_fencing_token', true)::bigint
);

CREATE POLICY ipc_claim_renew ON iam_provider_commands FOR UPDATE USING (
    current_user = 'iam_runtime'
    AND current_setting('app.iam_operation_scope', true) = 'GLOBAL'
    AND current_setting('app.iam_operation_code', true) = 'PROVIDER_COMMAND_CLAIM'
    AND status = 'CLAIMED'
    AND lease_expires_at < transaction_timestamp()
) WITH CHECK (
    status = 'CLAIMED'
    AND lease_owner = current_setting('app.iam_worker_id', true)
    AND fencing_token = current_setting('app.iam_fencing_token', true)::bigint
);

CREATE POLICY ipc_complete ON iam_provider_commands FOR UPDATE USING (
    current_user = 'iam_runtime'
    AND current_setting('app.iam_operation_scope', true) = 'IAM_PROVISIONING'
    AND current_setting('app.iam_operation_code', true) = 'TEACHER_ACCOUNT_CREATE'
    AND target_user_id = current_setting('app.iam_target_user_id', true)::uuid
    AND organization_id = current_setting('app.organization_id', true)::uuid
    AND status = 'CLAIMED'
    AND lease_owner = current_setting('app.iam_worker_id', true)
    AND fencing_token = current_setting('app.iam_fencing_token', true)::bigint
    AND lease_expires_at IS NOT NULL
    AND lease_expires_at >= transaction_timestamp()
) WITH CHECK (
    status IN ('COMPLETED','FAILED')
    AND lease_owner IS NULL
    AND lease_expires_at IS NULL
);

-- Secret delivery state transitions with trigger for time invariants

CREATE POLICY isd_escrowed ON iam_secret_deliveries FOR UPDATE USING (
    current_user = 'iam_runtime'
    AND current_setting('app.iam_operation_scope', true) = 'IAM_PROVISIONING'
    AND current_setting('app.iam_operation_code', true) = 'TEACHER_ACCOUNT_FINALIZE'
    AND recipient_actor_user_id = current_setting('app.iam_actor_user_id', true)::uuid
    AND status = 'ESCROWED'
) WITH CHECK (status IN ('READY','EXPIRED'));

CREATE POLICY isd_ready ON iam_secret_deliveries FOR UPDATE USING (
    current_user = 'iam_runtime'
    AND current_setting('app.iam_operation_scope', true) = 'IAM_PROVISIONING'
    AND current_setting('app.iam_operation_code', true) = 'TEACHER_ACCOUNT_FINALIZE'
    AND recipient_actor_user_id = current_setting('app.iam_actor_user_id', true)::uuid
    AND status = 'READY'
) WITH CHECK (status IN ('CONSUMED','EXPIRED'));

CREATE FUNCTION trg_isd_transition() RETURNS trigger AS $$
BEGIN
    IF TG_OP <> 'UPDATE' THEN RETURN NEW; END IF;
    IF OLD.status IN ('CONSUMED','EXPIRED') THEN
        RAISE EXCEPTION 'terminal secret delivery cannot be updated';
    END IF;
    IF OLD.status = 'ESCROWED' AND NEW.status = 'READY' THEN
        IF NEW.ready_at IS NULL OR NEW.ready_at <> transaction_timestamp() THEN
            RAISE EXCEPTION 'ready_at must be transaction_timestamp()';
        END IF;
        IF NEW.consumed_at IS NOT NULL THEN
            RAISE EXCEPTION 'consumed_at must be NULL during ESCROWED->READY';
        END IF;
        RETURN NEW;
    END IF;
    IF OLD.status = 'ESCROWED' AND NEW.status = 'EXPIRED' THEN
        NEW.encrypted_secret := NULL;
        NEW.payload_key_id := NULL;
        RETURN NEW;
    END IF;
    IF OLD.status = 'READY' AND NEW.status = 'CONSUMED' THEN
        IF NEW.consumed_at IS NULL OR NEW.consumed_at <> transaction_timestamp() THEN
            RAISE EXCEPTION 'consumed_at must be transaction_timestamp()';
        END IF;
        IF NEW.ready_at IS DISTINCT FROM OLD.ready_at THEN
            RAISE EXCEPTION 'ready_at cannot change during READY->CONSUMED';
        END IF;
        NEW.encrypted_secret := NULL;
        NEW.payload_key_id := NULL;
        RETURN NEW;
    END IF;
    IF OLD.status = 'READY' AND NEW.status = 'EXPIRED' THEN
        IF NEW.ready_at IS DISTINCT FROM OLD.ready_at THEN
            RAISE EXCEPTION 'ready_at cannot change during READY->EXPIRED';
        END IF;
        NEW.encrypted_secret := NULL;
        NEW.payload_key_id := NULL;
        RETURN NEW;
    END IF;
    RAISE EXCEPTION 'invalid status transition: % to %', OLD.status, NEW.status;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_isd_transition
    BEFORE UPDATE ON iam_secret_deliveries
    FOR EACH ROW EXECUTE FUNCTION trg_isd_transition();

-- Organization memberships: DEVICE_SESSION_REVOKE trigger with invariants

DROP POLICY organization_memberships_update_org ON organization_memberships;

CREATE POLICY organization_memberships_update_org ON organization_memberships FOR UPDATE USING (
    current_user = 'iam_runtime'
    AND current_setting('app.iam_operation_scope', true) = 'ORGANIZATION'
    AND current_setting('app.iam_operation_code', true) = 'DEVICE_SESSION_REVOKE'
    AND organization_id = current_setting('app.organization_id', true)::uuid
    AND id = current_setting('app.iam_target_membership_id', true)::uuid
) WITH CHECK (true);

CREATE FUNCTION trg_org_membership_revoke() RETURNS trigger AS $$
BEGIN
    IF TG_OP <> 'UPDATE' THEN RETURN NEW; END IF;
    IF NEW.session_generation <> OLD.session_generation + 1 THEN
        RAISE EXCEPTION 'session_generation must incremented by exactly 1';
    END IF;
    IF NEW.reauthentication_required_after <> transaction_timestamp() THEN
        RAISE EXCEPTION 'reauthentication_required_after must be transaction_timestamp()';
    END IF;
    IF NEW.id <> OLD.id OR NEW.organization_id <> OLD.organization_id
        OR NEW.user_id <> OLD.user_id OR NEW.person_id <> OLD.person_id
        OR NEW.status <> OLD.status OR NEW.granted_by_user_id <> OLD.granted_by_user_id
        OR NEW.granted_at <> OLD.granted_at THEN
        RAISE EXCEPTION 'immutable columns changed';
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_org_membership_revoke
    BEFORE UPDATE ON organization_memberships
    FOR EACH ROW EXECUTE FUNCTION trg_org_membership_revoke();

-- GLOBAL refresh policies: PLATFORM_DEVICE_REVOKE (device-specific) vs user-global

DROP POLICY refresh_token_families_select_global ON refresh_token_families;
DROP POLICY refresh_token_families_update_global ON refresh_token_families;
DROP POLICY refresh_tokens_select_global ON refresh_tokens;
DROP POLICY refresh_tokens_update_global ON refresh_tokens;

CREATE POLICY refresh_token_families_select_plat_dev_revoke ON refresh_token_families FOR SELECT USING (
    current_user = 'iam_runtime'
    AND current_setting('app.iam_operation_scope', true) = 'GLOBAL'
    AND current_setting('app.iam_operation_code', true) = 'PLATFORM_DEVICE_REVOKE'
    AND user_id = current_setting('app.iam_target_user_id', true)::uuid
    AND trusted_device_id = current_setting('app.iam_target_device_id', true)::uuid
);

CREATE POLICY refresh_token_families_update_plat_dev_revoke ON refresh_token_families FOR UPDATE USING (
    current_user = 'iam_runtime'
    AND current_setting('app.iam_operation_scope', true) = 'GLOBAL'
    AND current_setting('app.iam_operation_code', true) = 'PLATFORM_DEVICE_REVOKE'
    AND user_id = current_setting('app.iam_target_user_id', true)::uuid
    AND trusted_device_id = current_setting('app.iam_target_device_id', true)::uuid
    AND revoked_at IS NULL
) WITH CHECK (revoked_at IS NOT NULL);

CREATE POLICY refresh_token_families_select_user_global ON refresh_token_families FOR SELECT USING (
    current_user = 'iam_runtime'
    AND current_setting('app.iam_operation_scope', true) = 'GLOBAL'
    AND current_setting('app.iam_operation_code', true) IN ('USER_DISABLE','USER_LOGOUT','PASSWORD_RESET')
    AND user_id = current_setting('app.iam_target_user_id', true)::uuid
);

CREATE POLICY refresh_token_families_update_user_global ON refresh_token_families FOR UPDATE USING (
    current_user = 'iam_runtime'
    AND current_setting('app.iam_operation_scope', true) = 'GLOBAL'
    AND current_setting('app.iam_operation_code', true) IN ('USER_DISABLE','USER_LOGOUT','PASSWORD_RESET')
    AND user_id = current_setting('app.iam_target_user_id', true)::uuid
    AND revoked_at IS NULL
) WITH CHECK (revoked_at IS NOT NULL);

CREATE POLICY refresh_tokens_select_plat_dev_revoke ON refresh_tokens FOR SELECT USING (
    current_user = 'iam_runtime'
    AND current_setting('app.iam_operation_scope', true) = 'GLOBAL'
    AND current_setting('app.iam_operation_code', true) = 'PLATFORM_DEVICE_REVOKE'
    AND EXISTS (
        SELECT 1 FROM refresh_token_families rtf WHERE rtf.id = family_id
        AND rtf.user_id = current_setting('app.iam_target_user_id', true)::uuid
        AND rtf.trusted_device_id = current_setting('app.iam_target_device_id', true)::uuid
    )
);

CREATE POLICY refresh_tokens_update_plat_dev_revoke ON refresh_tokens FOR UPDATE USING (
    current_user = 'iam_runtime'
    AND current_setting('app.iam_operation_scope', true) = 'GLOBAL'
    AND current_setting('app.iam_operation_code', true) = 'PLATFORM_DEVICE_REVOKE'
    AND EXISTS (
        SELECT 1 FROM refresh_token_families rtf WHERE rtf.id = family_id
        AND rtf.user_id = current_setting('app.iam_target_user_id', true)::uuid
        AND rtf.trusted_device_id = current_setting('app.iam_target_device_id', true)::uuid
    )
    AND revoked_at IS NULL
) WITH CHECK (revoked_at IS NOT NULL);

CREATE POLICY refresh_tokens_select_user_global ON refresh_tokens FOR SELECT USING (
    current_user = 'iam_runtime'
    AND current_setting('app.iam_operation_scope', true) = 'GLOBAL'
    AND current_setting('app.iam_operation_code', true) IN ('USER_DISABLE','USER_LOGOUT','PASSWORD_RESET')
    AND EXISTS (
        SELECT 1 FROM refresh_token_families rtf WHERE rtf.id = family_id
        AND rtf.user_id = current_setting('app.iam_target_user_id', true)::uuid
    )
);

CREATE POLICY refresh_tokens_update_user_global ON refresh_tokens FOR UPDATE USING (
    current_user = 'iam_runtime'
    AND current_setting('app.iam_operation_scope', true) = 'GLOBAL'
    AND current_setting('app.iam_operation_code', true) IN ('USER_DISABLE','USER_LOGOUT','PASSWORD_RESET')
    AND EXISTS (
        SELECT 1 FROM refresh_token_families rtf WHERE rtf.id = family_id
        AND rtf.user_id = current_setting('app.iam_target_user_id', true)::uuid
    )
    AND revoked_at IS NULL
) WITH CHECK (revoked_at IS NOT NULL);


-- Fix: DROP broad permissive refresh SELECT policies, replace with operation-specific

DROP POLICY IF EXISTS refresh_token_families_select_iam_auth ON refresh_token_families;
DROP POLICY IF EXISTS refresh_tokens_select_iam_auth ON refresh_tokens;

CREATE POLICY refresh_token_families_select_sr ON refresh_token_families FOR SELECT USING (
    current_user = 'iam_runtime'
    AND current_setting('app.iam_operation_scope', true) = 'IAM_AUTH'
    AND current_setting('app.iam_operation_code', true) = 'SESSION_REFRESH'
    AND user_id = current_setting('app.iam_actor_user_id', true)::uuid
    AND trusted_device_id = current_setting('app.iam_current_trusted_device_id', true)::uuid
);

CREATE POLICY refresh_token_families_select_sl ON refresh_token_families FOR SELECT USING (
    current_user = 'iam_runtime'
    AND current_setting('app.iam_operation_scope', true) = 'IAM_AUTH'
    AND current_setting('app.iam_operation_code', true) = 'SESSION_LOGOUT'
    AND user_id = current_setting('app.iam_actor_user_id', true)::uuid
    AND trusted_device_id = current_setting('app.iam_current_trusted_device_id', true)::uuid
);

CREATE POLICY refresh_tokens_select_sr ON refresh_tokens FOR SELECT USING (
    current_user = 'iam_runtime'
    AND current_setting('app.iam_operation_scope', true) = 'IAM_AUTH'
    AND current_setting('app.iam_operation_code', true) = 'SESSION_REFRESH'
    AND EXISTS (
        SELECT 1 FROM refresh_token_families rtf WHERE rtf.id = family_id
        AND rtf.user_id = current_setting('app.iam_actor_user_id', true)::uuid
        AND rtf.trusted_device_id = current_setting('app.iam_current_trusted_device_id', true)::uuid
    )
);

CREATE POLICY refresh_tokens_select_sl ON refresh_tokens FOR SELECT USING (
    current_user = 'iam_runtime'
    AND current_setting('app.iam_operation_scope', true) = 'IAM_AUTH'
    AND current_setting('app.iam_operation_code', true) = 'SESSION_LOGOUT'
    AND EXISTS (
        SELECT 1 FROM refresh_token_families rtf WHERE rtf.id = family_id
        AND rtf.user_id = current_setting('app.iam_actor_user_id', true)::uuid
        AND rtf.trusted_device_id = current_setting('app.iam_current_trusted_device_id', true)::uuid
    )
);

-- GLOBAL provider command INSERT for USER_DISABLE/USER_LOGOUT/PASSWORD_RESET

CREATE POLICY iam_provider_commands_insert_global ON iam_provider_commands FOR INSERT WITH CHECK (
    current_user = 'iam_runtime'
    AND current_setting('app.iam_operation_scope', true) = 'GLOBAL'
    AND current_setting('app.iam_operation_code', true) IN ('USER_DISABLE','USER_LOGOUT','PASSWORD_RESET')
    AND command_type IN ('USER_DISABLE','USER_LOGOUT','PASSWORD_RESET')
    AND EXISTS (
        SELECT 1 FROM user_identities ui WHERE ui.id = target_identity_id
        AND ui.user_id = current_setting('app.iam_target_user_id', true)::uuid
    )
);

-- Worker SELECT for provider commands

CREATE POLICY iam_provider_commands_select_worker ON iam_provider_commands FOR SELECT USING (
    current_user = 'iam_runtime'
    AND current_setting('app.iam_operation_scope', true) = 'GLOBAL'
    AND current_setting('app.iam_operation_code', true) = 'PROVIDER_COMMAND_CLAIM'
    AND (
        (status = 'PENDING' AND lease_owner IS NULL)
        OR (status = 'CLAIMED' AND lease_expires_at IS NOT NULL AND lease_expires_at < transaction_timestamp())
        OR (status = 'CLAIMED' AND lease_owner = current_setting('app.iam_worker_id', true) AND fencing_token = current_setting('app.iam_fencing_token', true)::bigint)
    )
);

-- Secret delivery: force command/target/recipient/organization chain on SELECT/INSERT

DROP POLICY IF EXISTS iam_secret_deliveries_select_provisioning ON iam_secret_deliveries;
DROP POLICY IF EXISTS iam_secret_deliveries_insert_provisioning ON iam_secret_deliveries;

CREATE POLICY iam_secret_deliveries_select_provisioning ON iam_secret_deliveries FOR SELECT USING (
    current_user = 'iam_runtime'
    AND current_setting('app.iam_operation_scope', true) = 'IAM_PROVISIONING'
    AND recipient_actor_user_id = current_setting('app.iam_actor_user_id', true)::uuid
    AND EXISTS (
        SELECT 1 FROM iam_provider_commands ipc WHERE ipc.id = provider_command_id
        AND ipc.target_user_id = current_setting('app.iam_target_user_id', true)::uuid
        AND ipc.organization_id = current_setting('app.organization_id', true)::uuid
    )
);

CREATE POLICY iam_secret_deliveries_insert_provisioning ON iam_secret_deliveries FOR INSERT WITH CHECK (
    current_user = 'iam_runtime'
    AND current_setting('app.iam_operation_scope', true) = 'IAM_PROVISIONING'
    AND recipient_actor_user_id = current_setting('app.iam_actor_user_id', true)::uuid
    AND EXISTS (
        SELECT 1 FROM iam_provider_commands ipc WHERE ipc.id = provider_command_id
        AND ipc.target_user_id = current_setting('app.iam_target_user_id', true)::uuid
        AND ipc.organization_id = current_setting('app.organization_id', true)::uuid
    )
);

-- Phase 2: exact family binding, global completion, secret delivery tightening
DROP POLICY IF EXISTS refresh_token_families_select_sr ON refresh_token_families;
DROP POLICY IF EXISTS refresh_token_families_select_sl ON refresh_token_families;
DROP POLICY IF EXISTS refresh_tokens_select_sr ON refresh_tokens;
DROP POLICY IF EXISTS refresh_tokens_select_sl ON refresh_tokens;

CREATE POLICY refresh_token_families_select_sr ON refresh_token_families FOR SELECT USING (
    current_user = 'iam_runtime'
    AND current_setting('app.iam_operation_scope',true) = 'IAM_AUTH'
    AND current_setting('app.iam_operation_code',true) = 'SESSION_REFRESH'
    AND id = current_setting('app.iam_current_family_id',true)::uuid
    AND user_id = current_setting('app.iam_actor_user_id',true)::uuid
    AND trusted_device_id = current_setting('app.iam_current_trusted_device_id',true)::uuid
);

CREATE POLICY refresh_token_families_select_sl ON refresh_token_families FOR SELECT USING (
    current_user = 'iam_runtime'
    AND current_setting('app.iam_operation_scope',true) = 'IAM_AUTH'
    AND current_setting('app.iam_operation_code',true) = 'SESSION_LOGOUT'
    AND id = current_setting('app.iam_current_family_id',true)::uuid
    AND user_id = current_setting('app.iam_actor_user_id',true)::uuid
    AND trusted_device_id = current_setting('app.iam_current_trusted_device_id',true)::uuid
);

CREATE POLICY refresh_tokens_select_sr ON refresh_tokens FOR SELECT USING (
    current_user = 'iam_runtime'
    AND current_setting('app.iam_operation_scope',true) = 'IAM_AUTH'
    AND current_setting('app.iam_operation_code',true) = 'SESSION_REFRESH'
    AND family_id = current_setting('app.iam_current_family_id',true)::uuid
    AND EXISTS (SELECT 1 FROM refresh_token_families rtf WHERE rtf.id=family_id AND rtf.user_id=current_setting('app.iam_actor_user_id',true)::uuid AND rtf.trusted_device_id=current_setting('app.iam_current_trusted_device_id',true)::uuid)
);

CREATE POLICY refresh_tokens_select_sl ON refresh_tokens FOR SELECT USING (
    current_user = 'iam_runtime'
    AND current_setting('app.iam_operation_scope',true) = 'IAM_AUTH'
    AND current_setting('app.iam_operation_code',true) = 'SESSION_LOGOUT'
    AND family_id = current_setting('app.iam_current_family_id',true)::uuid
    AND EXISTS (SELECT 1 FROM refresh_token_families rtf WHERE rtf.id=family_id AND rtf.user_id=current_setting('app.iam_actor_user_id',true)::uuid AND rtf.trusted_device_id=current_setting('app.iam_current_trusted_device_id',true)::uuid)
);


DROP POLICY IF EXISTS refresh_tokens_sr ON refresh_tokens;
DROP POLICY IF EXISTS refresh_tokens_sl ON refresh_tokens;
DROP POLICY IF EXISTS refresh_token_families_sl ON refresh_token_families;

CREATE POLICY refresh_tokens_sr ON refresh_tokens FOR UPDATE USING (
    current_user = 'iam_runtime'
    AND current_setting('app.iam_operation_scope',true) = 'IAM_AUTH'
    AND current_setting('app.iam_operation_code',true) = 'SESSION_REFRESH'
    AND family_id = current_setting('app.iam_current_family_id',true)::uuid
    AND EXISTS (SELECT 1 FROM refresh_token_families rtf WHERE rtf.id=family_id AND rtf.user_id=current_setting('app.iam_actor_user_id',true)::uuid AND rtf.trusted_device_id=current_setting('app.iam_current_trusted_device_id',true)::uuid)
    AND used_at IS NULL AND revoked_at IS NULL
) WITH CHECK (used_at=transaction_timestamp());

CREATE POLICY refresh_tokens_sl ON refresh_tokens FOR UPDATE USING (
    current_user = 'iam_runtime'
    AND current_setting('app.iam_operation_scope',true) = 'IAM_AUTH'
    AND current_setting('app.iam_operation_code',true) = 'SESSION_LOGOUT'
    AND family_id = current_setting('app.iam_current_family_id',true)::uuid
    AND EXISTS (SELECT 1 FROM refresh_token_families rtf WHERE rtf.id=family_id AND rtf.user_id=current_setting('app.iam_actor_user_id',true)::uuid AND rtf.trusted_device_id=current_setting('app.iam_current_trusted_device_id',true)::uuid)
    AND revoked_at IS NULL
) WITH CHECK (revoked_at=transaction_timestamp());

CREATE POLICY refresh_token_families_sl ON refresh_token_families FOR UPDATE USING (
    current_user = 'iam_runtime'
    AND current_setting('app.iam_operation_scope',true) = 'IAM_AUTH'
    AND current_setting('app.iam_operation_code',true) = 'SESSION_LOGOUT'
    AND id = current_setting('app.iam_current_family_id',true)::uuid
    AND user_id = current_setting('app.iam_actor_user_id',true)::uuid
    AND revoked_at IS NULL
) WITH CHECK (revoked_at=transaction_timestamp());

DROP POLICY IF EXISTS iam_provider_commands_insert_global ON iam_provider_commands;
CREATE POLICY iam_provider_commands_insert_global ON iam_provider_commands FOR INSERT WITH CHECK (
    current_user = 'iam_runtime'
    AND current_setting('app.iam_operation_scope',true) = 'GLOBAL'
    AND current_setting('app.iam_operation_code',true) IN ('USER_DISABLE','USER_LOGOUT','PASSWORD_RESET')
    AND command_type::text = current_setting('app.iam_operation_code',true)
    AND EXISTS (SELECT 1 FROM user_identities ui WHERE ui.id=target_identity_id AND ui.user_id=current_setting('app.iam_target_user_id',true)::uuid)
);

CREATE POLICY ipc_complete_global ON iam_provider_commands FOR UPDATE USING (
    current_user = 'iam_runtime'
    AND current_setting('app.iam_operation_scope',true) = 'GLOBAL'
    AND current_setting('app.iam_operation_code',true) IN ('USER_DISABLE','USER_LOGOUT','PASSWORD_RESET')
    AND command_type::text = current_setting('app.iam_operation_code',true)
    AND status = 'CLAIMED'
    AND lease_owner = current_setting('app.iam_worker_id',true)
    AND fencing_token = current_setting('app.iam_fencing_token',true)::bigint
    AND lease_expires_at IS NOT NULL
    AND lease_expires_at >= transaction_timestamp()
) WITH CHECK (status IN ('COMPLETED','FAILED') AND lease_owner IS NULL AND lease_expires_at IS NULL);


-- Phase 3: Secret delivery membership/role/permission gated access

DROP POLICY IF EXISTS iam_secret_deliveries_select_provisioning ON iam_secret_deliveries;
DROP POLICY IF EXISTS iam_secret_deliveries_insert_provisioning ON iam_secret_deliveries;

CREATE POLICY iam_secret_deliveries_insert_create ON iam_secret_deliveries FOR INSERT WITH CHECK (
    current_user = 'iam_runtime'
    AND current_setting('app.iam_operation_scope', true) = 'IAM_PROVISIONING'
    AND current_setting('app.iam_operation_code', true) = 'TEACHER_ACCOUNT_CREATE'
    AND recipient_actor_user_id = current_setting('app.iam_actor_user_id', true)::uuid
    AND EXISTS (
        SELECT 1 FROM iam_provider_commands ipc WHERE ipc.id = provider_command_id
        AND ipc.command_type = 'TEACHER_ACCOUNT_CREATE'
        AND ipc.target_user_id = current_setting('app.iam_target_user_id', true)::uuid
        AND ipc.organization_id = current_setting('app.organization_id', true)::uuid
    )
    AND EXISTS (
        SELECT 1 FROM organization_memberships om
        WHERE om.user_id = current_setting('app.iam_actor_user_id', true)::uuid
        AND om.organization_id = current_setting('app.organization_id', true)::uuid
        AND om.status = 'ACTIVE'
        AND (
            EXISTS (
                SELECT 1 FROM organization_membership_roles omr
                WHERE omr.organization_membership_id = om.id
                AND omr.role = 'ORG_ADMIN' AND omr.revoked_at IS NULL
            )
            OR EXISTS (
                SELECT 1 FROM organization_membership_roles omr2
                JOIN organization_membership_permissions omp ON omp.target_membership_role_id = omr2.id
                    AND omp.organization_id = om.organization_id
                    AND omp.permission_code = 'TEACHER_ACCOUNT_MANAGE'
                    AND omp.revoked_at IS NULL
                WHERE omr2.organization_membership_id = om.id
                AND omr2.role = 'TEACHER' AND omr2.revoked_at IS NULL
            )
        )
    )
);

CREATE POLICY iam_secret_deliveries_select_finalize ON iam_secret_deliveries FOR SELECT USING (
    current_user = 'iam_runtime'
    AND current_setting('app.iam_operation_scope', true) = 'IAM_PROVISIONING'
    AND current_setting('app.iam_operation_code', true) = 'TEACHER_ACCOUNT_FINALIZE'
    AND recipient_actor_user_id = current_setting('app.iam_actor_user_id', true)::uuid
    AND status IN ('ESCROWED', 'READY', 'CONSUMED', 'EXPIRED')
    AND EXISTS (
        SELECT 1 FROM iam_provider_commands ipc WHERE ipc.id = provider_command_id
        AND ipc.command_type = 'TEACHER_ACCOUNT_CREATE'
        AND ipc.target_user_id = current_setting('app.iam_target_user_id', true)::uuid
        AND ipc.organization_id = current_setting('app.organization_id', true)::uuid
    )
    AND EXISTS (
        SELECT 1 FROM organization_memberships om
        WHERE om.user_id = current_setting('app.iam_actor_user_id', true)::uuid
        AND om.organization_id = current_setting('app.organization_id', true)::uuid
        AND om.status = 'ACTIVE'
        AND (
            EXISTS (
                SELECT 1 FROM organization_membership_roles omr
                WHERE omr.organization_membership_id = om.id
                AND omr.role = 'ORG_ADMIN' AND omr.revoked_at IS NULL
            )
            OR EXISTS (
                SELECT 1 FROM organization_membership_roles omr2
                JOIN organization_membership_permissions omp ON omp.target_membership_role_id = omr2.id
                    AND omp.organization_id = om.organization_id
                    AND omp.permission_code = 'TEACHER_ACCOUNT_MANAGE'
                    AND omp.revoked_at IS NULL
                WHERE omr2.organization_membership_id = om.id
                AND omr2.role = 'TEACHER' AND omr2.revoked_at IS NULL
            )
        )
    )
);

-- Phase 4: Minimal FORCE RLS SELECT policies for membership/role/permission tables
-- Only for same actor+organization+allow-listed provisioning operations

DROP POLICY IF EXISTS organization_memberships_select_provisioning ON organization_memberships;
DROP POLICY IF EXISTS organization_membership_roles_select_provisioning ON organization_membership_roles;
DROP POLICY IF EXISTS organization_membership_permissions_select_provisioning ON organization_membership_permissions;

CREATE POLICY organization_memberships_select_provisioning ON organization_memberships FOR SELECT USING (
    current_user = 'iam_runtime'
    AND current_setting('app.iam_operation_scope', true) = 'IAM_PROVISIONING'
    AND current_setting('app.iam_operation_code', true) IN ('TEACHER_ACCOUNT_CREATE', 'TEACHER_ACCOUNT_FINALIZE')
    AND user_id = current_setting('app.iam_actor_user_id', true)::uuid
    AND organization_id = current_setting('app.organization_id', true)::uuid
);

CREATE POLICY organization_membership_roles_select_provisioning ON organization_membership_roles FOR SELECT USING (
    current_user = 'iam_runtime'
    AND current_setting('app.iam_operation_scope', true) = 'IAM_PROVISIONING'
    AND current_setting('app.iam_operation_code', true) IN ('TEACHER_ACCOUNT_CREATE', 'TEACHER_ACCOUNT_FINALIZE')
    AND organization_id = current_setting('app.organization_id', true)::uuid
    AND EXISTS (
        SELECT 1 FROM organization_memberships om WHERE om.id = organization_membership_id
        AND om.user_id = current_setting('app.iam_actor_user_id', true)::uuid
        AND om.organization_id = current_setting('app.organization_id', true)::uuid
    )
);

CREATE POLICY organization_membership_permissions_select_provisioning ON organization_membership_permissions FOR SELECT USING (
    current_user = 'iam_runtime'
    AND current_setting('app.iam_operation_scope', true) = 'IAM_PROVISIONING'
    AND current_setting('app.iam_operation_code', true) IN ('TEACHER_ACCOUNT_CREATE', 'TEACHER_ACCOUNT_FINALIZE')
    AND organization_id = current_setting('app.organization_id', true)::uuid
    AND EXISTS (
        SELECT 1 FROM organization_memberships om WHERE om.id IN (
            SELECT omr.organization_membership_id FROM organization_membership_roles omr
            WHERE omr.id = target_membership_role_id
        )
        AND om.user_id = current_setting('app.iam_actor_user_id', true)::uuid
        AND om.organization_id = current_setting('app.organization_id', true)::uuid
    )
);
