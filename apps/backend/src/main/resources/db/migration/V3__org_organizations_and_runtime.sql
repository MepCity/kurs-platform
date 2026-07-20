-- Görev: ORG-003
-- ORG yaşam döngüsü için kuruluş tablosu, dar runtime rolü ve fail-closed RLS yüzeyi.

CREATE TYPE organization_status_enum AS ENUM ('ACTIVE', 'SUSPENDED', 'ARCHIVED');

ALTER TABLE organizations
    ALTER COLUMN status DROP DEFAULT,
    ALTER COLUMN status TYPE organization_status_enum USING status::organization_status_enum,
    ALTER COLUMN status SET DEFAULT 'ACTIVE',
    ADD COLUMN primary_color TEXT,
    ADD COLUMN secondary_color TEXT,
    ADD CONSTRAINT organizations_name_not_blank_chk
        CHECK (char_length(btrim(name)) BETWEEN 1 AND 200),
    ADD CONSTRAINT organizations_short_name_not_blank_chk
        CHECK (short_name IS NULL OR char_length(btrim(short_name)) BETWEEN 1 AND 50),
    ADD CONSTRAINT organizations_primary_color_hex_chk
        CHECK (primary_color IS NULL OR primary_color ~ '^#[0-9A-Fa-f]{6}$'),
    -- secondary_color'un kolonu ORG-002'nin VERI_MODELI.md §5.1'e eklediği sözleşmeyle bağımsızdır:
    -- primary_color'dan türetilmez, aynı hex biçim kısıtına tabidir; uygulama (WCAG kontrast vb.)
    -- doğrulaması ORG-005'in yazma yolunda yapılır, bu CHECK yalnız biçimi zorunlu kılar.
    ADD CONSTRAINT organizations_secondary_color_hex_chk
        CHECK (secondary_color IS NULL OR secondary_color ~ '^#[0-9A-Fa-f]{6}$'),
    ADD CONSTRAINT organizations_default_timezone_not_blank_chk
        CHECK (char_length(btrim(default_timezone)) BETWEEN 1 AND 100),
    ADD CONSTRAINT organizations_row_version_positive_chk CHECK (row_version >= 1);

ALTER TABLE organizations
    ADD CONSTRAINT organizations_created_by_user_fk FOREIGN KEY (created_by_user_id) REFERENCES users(id),
    ADD CONSTRAINT organizations_updated_by_user_fk FOREIGN KEY (updated_by_user_id) REFERENCES users(id);

CREATE INDEX organizations_status_name_id_idx ON organizations (status, name, id);

CREATE ROLE org_runtime WITH LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOINHERIT NOBYPASSRLS;

REVOKE ALL ON SCHEMA public FROM org_runtime;
GRANT USAGE ON SCHEMA public TO org_runtime;
REVOKE ALL ON ALL TABLES IN SCHEMA public FROM org_runtime;
REVOKE ALL ON ALL SEQUENCES IN SCHEMA public FROM org_runtime;

ALTER DEFAULT PRIVILEGES FOR ROLE current_user IN SCHEMA public REVOKE ALL ON TABLES FROM org_runtime;
ALTER DEFAULT PRIVILEGES FOR ROLE current_user IN SCHEMA public REVOKE ALL ON SEQUENCES FROM org_runtime;

-- Tablo geneli UPDATE kesinlikle verilmez. Identity ve lifecycle kolonları ayrı yüzeylerdir.
GRANT SELECT, INSERT (id, name, short_name, primary_color, secondary_color, status, default_timezone,
    created_by_user_id, updated_by_user_id) ON organizations TO org_runtime;
GRANT UPDATE (name, short_name, primary_color, secondary_color, default_timezone, updated_at, row_version,
    updated_by_user_id) ON organizations TO org_runtime;
GRANT UPDATE (status, updated_at, row_version, updated_by_user_id) ON organizations TO org_runtime;
GRANT SELECT ON platform_administrators TO org_runtime;
GRANT SELECT (id, organization_id, session_generation) ON organization_memberships TO org_runtime;
GRANT UPDATE (session_generation, reauthentication_required_after) ON organization_memberships TO org_runtime;
GRANT SELECT (id, user_id, trusted_device_id, organization_membership_id, authenticated_at,
    issued_at_session_generation, revoked_at, created_at) ON refresh_token_families TO org_runtime;
GRANT UPDATE (revoked_at) ON refresh_token_families TO org_runtime;
GRANT SELECT (id, family_id, revoked_at) ON refresh_tokens TO org_runtime;
GRANT UPDATE (revoked_at) ON refresh_tokens TO org_runtime;
-- idempotency_keys: lookup/claim (INSERT PENDING), terminal update (COMPLETED) ve replay SELECT.
-- scope_type, organization_id, user_id, client_mutation_id, operation_type, request_fingerprint
-- INSERT tarafından yazılır; status PENDING'ten COMPLETED'a güncellenir. FAILED terminal yazımı
-- audit/DB hatasında denenmez; transaction rollback olduğu için kayıt ya hiç oluşmaz ya PENDING olarak
-- yok olur (CLAIM -> ROLLBACK). key_retention_expires_at her INSERT'te yazılır.
GRANT SELECT, INSERT (id, scope_type, organization_id, user_id, client_mutation_id, operation_type,
    request_fingerprint, status, lease_owner, lease_generation, lease_expires_at, key_retention_expires_at)
    ON idempotency_keys TO org_runtime;
GRANT UPDATE (status, result_entity_id, terminal_http_status, terminal_error_code, result_payload,
    result_reference, lease_owner, lease_generation, lease_expires_at, completed_at,
    result_expires_at, key_retention_expires_at) ON idempotency_keys TO org_runtime;

-- audit_logs: yalnızca sözleşmenin gerektirdiği kolonlarda column-level INSERT. Tablo-geneli INSERT,
-- UPDATE veya DELETE hiçbir koşulda org_runtime'a verilmez. scope_class_id ve operation_group_id
-- katalog sözleşmesi (requires_class_scope=false, requires_operation_group=false) gereği NULL kalır;
-- occurred_at DEFAULT transaction_timestamp() ile dolar. ip_address/device_id V1 ORG akışında NULL.
GRANT INSERT (id, organization_id, actor_user_id, request_id, action_type, payload_schema_version,
    event_scope, target_entity_type, event_kind, requires_target_entity, requires_class_scope,
    requires_operation_group, target_entity_id, old_value, new_value, event_metadata, reason_code,
    operation_group_id, is_undo, undo_of_audit_log_id) ON audit_logs TO org_runtime;

ALTER TABLE organizations ENABLE ROW LEVEL SECURITY;
ALTER TABLE organizations FORCE ROW LEVEL SECURITY;

CREATE OR REPLACE FUNCTION organizations_org_runtime_write_guard()
RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    -- Migration/operasyon sahibi satırları hazırlarken runtime kısıtları uygulanmaz;
    -- uygulama erişimi ise yalnız org_runtime ile ve FORCE RLS altında gerçekleşir.
    IF current_user <> 'org_runtime' THEN
        RETURN NEW;
    END IF;

    IF TG_OP = 'INSERT' THEN
        IF NEW.status <> 'ACTIVE' OR NEW.row_version <> 1
           OR NEW.created_by_user_id IS DISTINCT FROM current_setting('app.iam_actor_user_id', true)::uuid
           OR NEW.updated_by_user_id IS DISTINCT FROM current_setting('app.iam_actor_user_id', true)::uuid THEN
            RAISE EXCEPTION 'ORG-003 organizations insert guard rejected' USING ERRCODE = '42501';
        END IF;
        RETURN NEW;
    END IF;

    IF NEW.id IS DISTINCT FROM OLD.id
       OR NEW.created_at IS DISTINCT FROM OLD.created_at
       OR NEW.created_by_user_id IS DISTINCT FROM OLD.created_by_user_id THEN
        RAISE EXCEPTION 'ORG-003 immutable organization column update rejected' USING ERRCODE = '42501';
    END IF;
    IF NEW.updated_by_user_id IS DISTINCT FROM current_setting('app.iam_actor_user_id', true)::uuid
       OR NEW.row_version <> OLD.row_version + 1 THEN
        RAISE EXCEPTION 'ORG-003 organization metadata update rejected' USING ERRCODE = '42501';
    END IF;

    IF current_setting('app.iam_operation_code', true) = 'ORG_UPDATE_IDENTITY' THEN
        IF NEW.status IS DISTINCT FROM OLD.status THEN
            RAISE EXCEPTION 'ORG-003 identity update cannot change status' USING ERRCODE = '42501';
        END IF;
    ELSIF current_setting('app.iam_platform_admin_support_access', true) = 'true'
          AND ((current_setting('app.iam_operation_code', true) = 'ORG_SUSPEND'
                    AND OLD.status = 'ACTIVE' AND NEW.status = 'SUSPENDED')
              OR (current_setting('app.iam_operation_code', true) = 'ORG_ACTIVATE'
                    AND OLD.status = 'SUSPENDED' AND NEW.status = 'ACTIVE')
              OR (current_setting('app.iam_operation_code', true) = 'ORG_ARCHIVE'
                    AND OLD.status IN ('ACTIVE', 'SUSPENDED') AND NEW.status = 'ARCHIVED')) THEN
        IF NEW.name IS DISTINCT FROM OLD.name OR NEW.short_name IS DISTINCT FROM OLD.short_name
           OR NEW.primary_color IS DISTINCT FROM OLD.primary_color
           OR NEW.secondary_color IS DISTINCT FROM OLD.secondary_color
           OR NEW.default_timezone IS DISTINCT FROM OLD.default_timezone THEN
            RAISE EXCEPTION 'ORG-003 lifecycle update may only change status' USING ERRCODE = '42501';
        END IF;
    ELSE
        RAISE EXCEPTION 'ORG-003 unsupported organization update operation' USING ERRCODE = '42501';
    END IF;
    RETURN NEW;
END;
$$;

CREATE TRIGGER organizations_org_runtime_write_guard
    BEFORE INSERT OR UPDATE ON organizations
    FOR EACH ROW EXECUTE FUNCTION organizations_org_runtime_write_guard();

-- ============================================================================
-- Kurum aktörü canlı yetkilendirmesi — ORG_UPDATE_IDENTITY
-- ============================================================================
-- Aktif organization_membership + iptal edilmemiş aktif rol (ORG_ADMIN izin tablosuna bakmadan
-- kabul; TEACHER yalnız BRAND_MANAGE devriyle) doğrular. SECURITY DEFINER DEĞİLDİR: çağıranın
-- (org_runtime) kendi RLS/GRANT sınırları içinde çalışır — bu fonksiyonun herhangi bir satırı
-- görebilmesi, aşağıdaki dar SELECT policy'lerinin zaten izin verdiği satırlarla sınırlıdır.
-- Hem organizations_update_identity/audit_logs_insert_org_setting_changed/idempotency_keys_
-- org_runtime_organization DB policy'lerinde hem de OrganizationLifecycleService.updateIdentity'de
-- (aynı transaction, idempotency değerlendirmesinden önce) kullanılır — iki katmanlı savunma.
CREATE OR REPLACE FUNCTION org_actor_has_identity_update_access(target_org UUID, actor UUID)
RETURNS boolean LANGUAGE sql STABLE AS $$
    SELECT EXISTS (
        SELECT 1 FROM organization_memberships om
        WHERE om.organization_id = target_org AND om.user_id = actor AND om.status = 'ACTIVE'
          AND (
              EXISTS (SELECT 1 FROM organization_membership_roles omr
                  WHERE omr.organization_membership_id = om.id AND omr.role = 'ORG_ADMIN'
                    AND omr.revoked_at IS NULL)
              OR EXISTS (SELECT 1 FROM organization_membership_roles omr
                  JOIN organization_membership_permissions omp ON omp.target_membership_role_id = omr.id
                  WHERE omr.organization_membership_id = om.id AND omr.role = 'TEACHER'
                    AND omr.revoked_at IS NULL
                    AND omp.permission_code = 'BRAND_MANAGE' AND omp.revoked_at IS NULL)
          )
    );
$$;

-- org_runtime'a yalnız gereken kolonlarda, ORG_UPDATE_IDENTITY canlı yetkilendirmesi için dar SELECT.
-- Genel SELECT/yazma yetkisi verilmez; RLS aşağıdaki policy'lerle actor+organization'a kilitlenir.
GRANT SELECT (id, organization_id, user_id, status) ON organization_memberships TO org_runtime;
GRANT SELECT (id, organization_membership_id, organization_id, role, revoked_at) ON organization_membership_roles TO org_runtime;
GRANT SELECT (id, organization_id, target_membership_role_id, permission_code, revoked_at)
    ON organization_membership_permissions TO org_runtime;
GRANT SELECT ON permission_catalog TO org_runtime;

CREATE POLICY organization_memberships_select_identity_update ON organization_memberships FOR SELECT USING (
    current_user = 'org_runtime' AND current_setting('app.iam_operation_scope', true) = 'ORGANIZATION'
    AND current_setting('app.iam_operation_code', true) = 'ORG_UPDATE_IDENTITY'
    AND organization_id = current_setting('app.organization_id', true)::uuid
    AND user_id = current_setting('app.iam_actor_user_id', true)::uuid
);
CREATE POLICY organization_membership_roles_select_identity_update ON organization_membership_roles FOR SELECT USING (
    current_user = 'org_runtime' AND current_setting('app.iam_operation_scope', true) = 'ORGANIZATION'
    AND current_setting('app.iam_operation_code', true) = 'ORG_UPDATE_IDENTITY'
    AND organization_id = current_setting('app.organization_id', true)::uuid
    AND EXISTS (SELECT 1 FROM organization_memberships om
        WHERE om.id = organization_membership_roles.organization_membership_id
          AND om.user_id = current_setting('app.iam_actor_user_id', true)::uuid)
);
CREATE POLICY organization_membership_permissions_select_identity_update
    ON organization_membership_permissions FOR SELECT USING (
    current_user = 'org_runtime' AND current_setting('app.iam_operation_scope', true) = 'ORGANIZATION'
    AND current_setting('app.iam_operation_code', true) = 'ORG_UPDATE_IDENTITY'
    AND organization_id = current_setting('app.organization_id', true)::uuid
    AND EXISTS (SELECT 1 FROM organization_membership_roles omr
        JOIN organization_memberships om ON om.id = omr.organization_membership_id
        WHERE omr.id = organization_membership_permissions.target_membership_role_id
          AND om.user_id = current_setting('app.iam_actor_user_id', true)::uuid)
);

-- GLOBAL yüzey, aktif admin satırını RLS altındaki actor-only SELECT ile doğruladıktan sonra açılır.
CREATE POLICY organizations_select_global ON organizations FOR SELECT USING (
    current_user = 'org_runtime'
    AND current_setting('app.iam_operation_scope', true) = 'GLOBAL'
    AND current_setting('app.iam_operation_code', true) = 'ORG_LIST'
    AND EXISTS (SELECT 1 FROM platform_administrators pa
        WHERE pa.user_id = current_setting('app.iam_actor_user_id', true)::uuid AND pa.revoked_at IS NULL)
);
CREATE POLICY organizations_insert_global ON organizations FOR INSERT WITH CHECK (
    current_user = 'org_runtime' AND current_setting('app.iam_operation_scope', true) = 'GLOBAL'
    AND current_setting('app.iam_operation_code', true) = 'ORG_CREATE'
    AND status = 'ACTIVE' AND row_version = 1
    AND created_by_user_id = current_setting('app.iam_actor_user_id', true)::uuid
    AND updated_by_user_id = current_setting('app.iam_actor_user_id', true)::uuid
    AND EXISTS (SELECT 1 FROM platform_administrators pa
        WHERE pa.user_id = current_setting('app.iam_actor_user_id', true)::uuid AND pa.revoked_at IS NULL)
);

CREATE POLICY organizations_select_organization ON organizations FOR SELECT USING (
    current_user = 'org_runtime' AND current_setting('app.iam_operation_scope', true) = 'ORGANIZATION'
    AND id = current_setting('app.organization_id', true)::uuid
    AND ((current_setting('app.iam_platform_admin_support_access', true) = 'true'
          AND current_setting('app.iam_operation_code', true) IN
              ('ORG_DETAIL', 'ORG_UPDATE_IDENTITY', 'ORG_SUSPEND', 'ORG_ACTIVATE', 'ORG_ARCHIVE')
          AND EXISTS (SELECT 1 FROM platform_administrators pa
              WHERE pa.user_id = current_setting('app.iam_actor_user_id', true)::uuid AND pa.revoked_at IS NULL))
         OR (current_setting('app.iam_platform_admin_support_access', true) IS DISTINCT FROM 'true'
          AND NOT EXISTS (SELECT 1 FROM platform_administrators pa
              WHERE pa.user_id = current_setting('app.iam_actor_user_id', true)::uuid AND pa.revoked_at IS NULL)
          AND status = 'ACTIVE'
          AND current_setting('app.iam_operation_code', true) IN ('ORG_LIST', 'ORG_DETAIL', 'ORG_UPDATE_IDENTITY')))
);
-- Kurum aktörü kolu artık gerçek canlı yetkilendirmeyle korunur: aktif üyelik + iptal edilmemiş
-- ORG_ADMIN rolü (izin tablosuna bakmadan) veya iptal edilmemiş TEACHER rolü + devredilmiş, iptal
-- edilmemiş BRAND_MANAGE izni (org_actor_has_identity_update_access). Eskiden yalnız "aktif admin
-- YOKTUR" kontrolü vardı; bu, üyeliği/rolü/izni hiç doğrulamayan yarı açık bir yüzeydi.
-- ARCHIVED kısıtı kasıtlı olarak USING'te DEĞİL, WITH CHECK'tedir: PostgreSQL SELECT ... FOR UPDATE
-- için SELECT görünürlüğüne ek olarak ilgili UPDATE policy'sinin USING koşulunu da birleştirir (bkz.
-- CREATE POLICY belgeleri, satır kilitleme bölümü). ARCHIVED kısıtı USING'te olsaydı, platform admin
-- ARCHIVED satırı kilitli okuyamaz (findByIdForUpdate boş döner) ve servis kendi açık
-- "ARCHIVED terminal" kontrolünü (OrganizationConflictException) hiç çalıştıramazdı — satır
-- OrganizationNotVisibleException ile "bulunamadı" gibi görünürdü, ki bu yanlış hata türüdür. WITH
-- CHECK'e taşınması: admin ARCHIVED satırı görebilir/kilitleyebilir (servis kendi kontrolüyle
-- fail-closed reddeder), ama identity update status'u değiştirmediğinden NEW.status = OLD.status =
-- 'ARCHIVED' kalır ve gerçek UPDATE denemesi (uygulama katmanı atlanırsa dahi) RLS'in kendisi
-- tarafından da reddedilir — iki katmanlı savunma korunur.
CREATE POLICY organizations_update_identity ON organizations FOR UPDATE USING (
    current_user = 'org_runtime' AND current_setting('app.iam_operation_scope', true) = 'ORGANIZATION'
    AND id = current_setting('app.organization_id', true)::uuid
    AND current_setting('app.iam_operation_code', true) = 'ORG_UPDATE_IDENTITY'
    AND ((current_setting('app.iam_platform_admin_support_access', true) IS DISTINCT FROM 'true'
          AND org_actor_has_identity_update_access(id, current_setting('app.iam_actor_user_id', true)::uuid)
          AND status = 'ACTIVE')
         OR (current_setting('app.iam_platform_admin_support_access', true) = 'true'
          AND EXISTS (SELECT 1 FROM platform_administrators pa
             WHERE pa.user_id = current_setting('app.iam_actor_user_id', true)::uuid AND pa.revoked_at IS NULL)))
) WITH CHECK (
    id = current_setting('app.organization_id', true)::uuid
    AND status <> 'ARCHIVED'
);
CREATE POLICY organizations_update_lifecycle ON organizations FOR UPDATE USING (
    current_user = 'org_runtime' AND current_setting('app.iam_operation_scope', true) = 'ORGANIZATION'
    AND id = current_setting('app.organization_id', true)::uuid
    AND current_setting('app.iam_platform_admin_support_access', true) = 'true'
    AND current_setting('app.iam_operation_code', true) IN ('ORG_SUSPEND', 'ORG_ACTIVATE', 'ORG_ARCHIVE')
    AND EXISTS (SELECT 1 FROM platform_administrators pa
        WHERE pa.user_id = current_setting('app.iam_actor_user_id', true)::uuid AND pa.revoked_at IS NULL)
) WITH CHECK (id = current_setting('app.organization_id', true)::uuid);

-- Kanonik ORGANIZATION-scope allow-list: ORG-001'in 5 kodu + ORG-002'nin marka/palet/modül/logo
-- 9 kodu (ORG_KURUM_YASAM_DONGUSU_API_SOZLESMESI.md §4.1a). Tek liste; ORG-002 için ayrı policy
-- tanımlanmaz. Bu SELECT, app.iam_platform_admin_support_access bayrağına bağımlı DEĞİLDİR —
-- bayrak yalnız bu satır bulunduktan SONRA server-set edilir (ORG_KURUM §2.2 madde 5).
CREATE POLICY platform_administrators_select_org_runtime_organization ON platform_administrators FOR SELECT USING (
    current_user = 'org_runtime' AND current_setting('app.iam_operation_scope', true) = 'ORGANIZATION'
    AND user_id = current_setting('app.iam_actor_user_id', true)::uuid AND revoked_at IS NULL
    AND current_setting('app.iam_operation_code', true) IN
        ('ORG_DETAIL', 'ORG_UPDATE_IDENTITY', 'ORG_SUSPEND', 'ORG_ACTIVATE', 'ORG_ARCHIVE',
         'ORG_VIEW_BRAND', 'ORG_UPDATE_BRAND', 'ORG_VIEW_BRAND_COLORS', 'ORG_UPDATE_BRAND_COLORS',
         'ORG_VIEW_MODULES', 'ORG_UPDATE_MODULES', 'ORG_UPLOAD_LOGO', 'ORG_REMOVE_LOGO', 'ORG_VIEW_LOGO')
);
CREATE POLICY platform_administrators_select_org_runtime_global ON platform_administrators FOR SELECT USING (
    current_user = 'org_runtime' AND current_setting('app.iam_operation_scope', true) = 'GLOBAL'
    AND user_id = current_setting('app.iam_actor_user_id', true)::uuid AND revoked_at IS NULL
    AND current_setting('app.iam_operation_code', true) IN ('ORG_CREATE', 'ORG_LIST')
);

-- SUSPEND/ARCHIVE'da yalnız hedef kurumun üyelik ve token zinciri erişilebilir.
CREATE POLICY organization_memberships_org_lifecycle ON organization_memberships FOR SELECT USING (
    current_user = 'org_runtime' AND current_setting('app.iam_operation_scope', true) = 'ORGANIZATION'
    AND current_setting('app.iam_platform_admin_support_access', true) = 'true'
    AND current_setting('app.iam_operation_code', true) IN ('ORG_SUSPEND', 'ORG_ARCHIVE')
    AND organization_id = current_setting('app.organization_id', true)::uuid
);
CREATE POLICY organization_memberships_org_lifecycle_update ON organization_memberships FOR UPDATE USING (
    current_user = 'org_runtime' AND current_setting('app.iam_operation_scope', true) = 'ORGANIZATION'
    AND current_setting('app.iam_platform_admin_support_access', true) = 'true'
    AND current_setting('app.iam_operation_code', true) IN ('ORG_SUSPEND', 'ORG_ARCHIVE')
    AND organization_id = current_setting('app.organization_id', true)::uuid
) WITH CHECK (organization_id = current_setting('app.organization_id', true)::uuid);
CREATE POLICY refresh_token_families_org_lifecycle ON refresh_token_families FOR SELECT USING (
    current_user = 'org_runtime' AND current_setting('app.iam_operation_scope', true) = 'ORGANIZATION'
    AND current_setting('app.iam_platform_admin_support_access', true) = 'true'
    AND current_setting('app.iam_operation_code', true) IN ('ORG_SUSPEND', 'ORG_ARCHIVE')
    AND EXISTS (SELECT 1 FROM organization_memberships om WHERE om.id = organization_membership_id
        AND om.organization_id = current_setting('app.organization_id', true)::uuid)
);
CREATE POLICY refresh_token_families_org_lifecycle_update ON refresh_token_families FOR UPDATE USING (
    current_user = 'org_runtime' AND current_setting('app.iam_operation_scope', true) = 'ORGANIZATION'
    AND current_setting('app.iam_platform_admin_support_access', true) = 'true'
    AND current_setting('app.iam_operation_code', true) IN ('ORG_SUSPEND', 'ORG_ARCHIVE')
    AND revoked_at IS NULL
    AND EXISTS (SELECT 1 FROM organization_memberships om WHERE om.id = organization_membership_id
        AND om.organization_id = current_setting('app.organization_id', true)::uuid)
) WITH CHECK (revoked_at = transaction_timestamp());
CREATE POLICY refresh_tokens_org_lifecycle ON refresh_tokens FOR SELECT USING (
    current_user = 'org_runtime' AND current_setting('app.iam_operation_scope', true) = 'ORGANIZATION'
    AND current_setting('app.iam_platform_admin_support_access', true) = 'true'
    AND current_setting('app.iam_operation_code', true) IN ('ORG_SUSPEND', 'ORG_ARCHIVE')
    AND EXISTS (SELECT 1 FROM refresh_token_families rtf WHERE rtf.id = family_id)
);
CREATE POLICY refresh_tokens_org_lifecycle_update ON refresh_tokens FOR UPDATE USING (
    current_user = 'org_runtime' AND current_setting('app.iam_operation_scope', true) = 'ORGANIZATION'
    AND current_setting('app.iam_platform_admin_support_access', true) = 'true'
    AND current_setting('app.iam_operation_code', true) IN ('ORG_SUSPEND', 'ORG_ARCHIVE')
    AND revoked_at IS NULL
    AND EXISTS (SELECT 1 FROM refresh_token_families rtf WHERE rtf.id = family_id)
) WITH CHECK (revoked_at = transaction_timestamp());

CREATE POLICY idempotency_keys_org_runtime_global ON idempotency_keys FOR ALL USING (
    current_user = 'org_runtime' AND current_setting('app.iam_operation_scope', true) = 'GLOBAL'
    AND current_setting('app.iam_operation_code', true) = 'ORG_CREATE' AND scope_type = 'GLOBAL'
    AND organization_id IS NULL AND user_id = current_setting('app.iam_actor_user_id', true)::uuid
    AND EXISTS (SELECT 1 FROM platform_administrators pa
        WHERE pa.user_id = current_setting('app.iam_actor_user_id', true)::uuid AND pa.revoked_at IS NULL)
) WITH CHECK (
    current_user = 'org_runtime' AND current_setting('app.iam_operation_scope', true) = 'GLOBAL'
    AND current_setting('app.iam_operation_code', true) = 'ORG_CREATE'
    AND scope_type = 'GLOBAL' AND organization_id IS NULL
    AND user_id = current_setting('app.iam_actor_user_id', true)::uuid AND operation_type = 'ORG_CREATE'
    AND EXISTS (SELECT 1 FROM platform_administrators pa
        WHERE pa.user_id = current_setting('app.iam_actor_user_id', true)::uuid AND pa.revoked_at IS NULL)
);
-- Kurum aktörü kolu (yalnız ORG_UPDATE_IDENTITY) artık org_actor_has_identity_update_access ile
-- gerçek üyelik/rol/izin doğrulaması taşır; eskiden yalnız "aktif admin YOKTUR" kontrolü vardı.
CREATE POLICY idempotency_keys_org_runtime_organization ON idempotency_keys FOR ALL USING (
    current_user = 'org_runtime' AND current_setting('app.iam_operation_scope', true) = 'ORGANIZATION'
    AND scope_type = 'ORGANIZATION' AND organization_id = current_setting('app.organization_id', true)::uuid
    AND user_id = current_setting('app.iam_actor_user_id', true)::uuid
    AND ((current_setting('app.iam_platform_admin_support_access', true) = 'true'
          AND current_setting('app.iam_operation_code', true) IN ('ORG_UPDATE_IDENTITY','ORG_SUSPEND','ORG_ACTIVATE','ORG_ARCHIVE')
          AND EXISTS (SELECT 1 FROM platform_administrators pa WHERE pa.user_id = current_setting('app.iam_actor_user_id', true)::uuid AND pa.revoked_at IS NULL))
         OR (current_setting('app.iam_platform_admin_support_access', true) IS DISTINCT FROM 'true'
          AND current_setting('app.iam_operation_code', true) = 'ORG_UPDATE_IDENTITY'
          AND org_actor_has_identity_update_access(organization_id, current_setting('app.iam_actor_user_id', true)::uuid)))
) WITH CHECK (
    current_user = 'org_runtime' AND current_setting('app.iam_operation_scope', true) = 'ORGANIZATION'
    AND scope_type = 'ORGANIZATION' AND organization_id = current_setting('app.organization_id', true)::uuid
    AND user_id = current_setting('app.iam_actor_user_id', true)::uuid
    AND operation_type = current_setting('app.iam_operation_code', true)
    AND ((current_setting('app.iam_platform_admin_support_access', true) = 'true'
          AND current_setting('app.iam_operation_code', true) IN ('ORG_UPDATE_IDENTITY','ORG_SUSPEND','ORG_ACTIVATE','ORG_ARCHIVE')
          AND EXISTS (SELECT 1 FROM platform_administrators pa WHERE pa.user_id = current_setting('app.iam_actor_user_id', true)::uuid AND pa.revoked_at IS NULL))
         OR (current_setting('app.iam_platform_admin_support_access', true) IS DISTINCT FROM 'true'
          AND current_setting('app.iam_operation_code', true) = 'ORG_UPDATE_IDENTITY'
          AND org_actor_has_identity_update_access(organization_id, current_setting('app.iam_actor_user_id', true)::uuid)))
);

-- ============================================================================
-- audit_action_catalog v2 seed — ORG_SETTING_CHANGED (ORG-002 marka/palet/modül alanları)
-- ============================================================================
-- AUDIT-001A'nın V2__audit_core.sql'i değişmez: v1 katalog satırı (payload_schema_version=1)
-- burada değiştirilmez veya silinmez. Bu, ORG-002'nin ihtiyaç duyduğu secondaryColor/brandColors
-- alanlarını taşıyan YENİ, ayrı bir immutable v2 satırıdır (payload_schema_version=2).
-- ORG_MARKA_AYARLARI_API_SOZLESMESI.md §2.8.2 şablonuyla birebir aynıdır: izinli alan üst kümesi
-- v1 + secondaryColor + brandColors; rejectUnknown=true korunur. ORG-005 (marka API backend)
-- yalnız bu v2 satırıyla audit yazabilir; v1 yoluna düşerse Java tarafında fail-closed reddedilir
-- (AuditEvent.Factory.orgSettingChanged v1 metodu secondaryColor/brandColors alanlarını asla kabul
-- etmez, yalnız orgSettingChangedV2 kabul eder).
INSERT INTO audit_action_catalog (code, payload_schema_version, target_entity_type, event_scope, event_kind,
    requires_target_entity, requires_class_scope, requires_operation_group, is_undoable, payload_schema) VALUES
('ORG_SETTING_CHANGED', 2, 'ORGANIZATION', 'ORGANIZATION', 'DATA_MUTATION', true, false, false, true,
 '{"oldValue":{"allowed":["name","shortName","defaultTimezone","primaryColor","secondaryColor","logoAssetId","enabledModules","brandColors","attendanceStatuses","rowVersion"]},"newValue":{"allowed":["name","shortName","defaultTimezone","primaryColor","secondaryColor","logoAssetId","enabledModules","brandColors","attendanceStatuses","rowVersion"]},"eventMetadata":{"allowed":["operationCode"]},"reasonCodes":[],"rejectUnknown":true}'::jsonb);

-- ============================================================================
-- audit_logs INSERT RLS (org_runtime)
-- ============================================================================
-- Tüm ORG audit olayları V2 katalog sözleşmesi gereği event_scope='ORGANIZATION' taşır.
-- GLOBAL transaction scope ile audit event_scope karışmaz: GLOBAL transaction'da
-- app.organization_id kurulmaz ama yine de her audit satırı ORGANIZATION scope'tur ve
-- organization_id = target_entity_id ile yazılır (ORG_CREATE yeni kurum, ORG_LIST her kurum).
-- requires_class_scope=false -> scope_class_id NULL; requires_operation_group=false -> operation_group_id NULL.
-- is_undo=false ve undo_of_audit_log_id NULL (V1 ORG akışında geri alma yok).
-- app.iam_platform_admin_support_access='true' yalnız server-settir; RLS yalnızca değerin 'true'
-- olup olmadığını doğrular. Değerin backend dışında kurulamayacağı güven sınırı org_runtime
-- kimlik bilgilerinin yalnız backend tarafından kullanılmasına dayanır.

-- ORG_CREATED: GLOBAL transaction, ORG_CREATE operation code. Yeni kurum için tek audit satırı.
-- event_metadata->>'operationCode' = current_setting('app.iam_operation_code') zorunlu: Java tarafı
-- her factory metodunda operation code'u metadata'ya gömer (AuditEvent.CreatedMetadata), bu satır
-- o değerin RLS'in doğruladığı transaction context'iyle bizzat DB'de eşleştiğini kanıtlar --
-- application katmanı atlanıp yalnış bir operationCode taşıyan event_metadata ile INSERT denenirse
-- (context doğru olsa dahi) 42501 ile reddedilir.
CREATE POLICY audit_logs_insert_org_created ON audit_logs FOR INSERT TO org_runtime
    WITH CHECK (
        current_user = 'org_runtime'
        AND current_setting('app.iam_operation_scope', true) = 'GLOBAL'
        AND current_setting('app.iam_operation_code', true) = 'ORG_CREATE'
        AND event_metadata->>'operationCode' = current_setting('app.iam_operation_code', true)
        AND event_scope = 'ORGANIZATION'
        AND action_type = 'ORG_CREATED'
        AND event_kind = 'DATA_MUTATION'
        AND target_entity_type = 'ORGANIZATION'
        AND requires_target_entity
        AND NOT requires_class_scope AND scope_class_id IS NULL
        AND NOT requires_operation_group AND operation_group_id IS NULL
        AND NOT is_undo AND undo_of_audit_log_id IS NULL
        AND organization_id IS NOT NULL AND organization_id = target_entity_id
        AND actor_user_id = current_setting('app.iam_actor_user_id', true)::uuid
        AND EXISTS (SELECT 1 FROM platform_administrators pa
            WHERE pa.user_id = current_setting('app.iam_actor_user_id', true)::uuid
            AND pa.revoked_at IS NULL)
    );

-- PLATFORM_ADMIN_ORG_ACCESS: hem GLOBAL (ORG_LIST her görüntülenen kurum) hem ORGANIZATION
-- (ORG_DETAIL ve platform-admin destekli lifecycle/identity komutları) transaction scope'larında
-- yazılabilir. Her durumda event_scope='ORGANIZATION', organization_id = target_entity_id.
-- event_metadata->>'operationCode' transaction context'iyle eşleşmeli; event_metadata->>'outcome'
-- ile reason_code arasındaki korelasyon (ALLOWED -> reason_code NULL, FORBIDDEN -> reason_code
-- 'FORBIDDEN') Java tarafındaki AuditEvent.Factory doğrulamasının DB'deki aynası -- application
-- katmanı atlanıp bu ikisi tutarsız yazılmaya çalışılırsa 42501 ile reddedilir.
CREATE POLICY audit_logs_insert_platform_admin_org_access ON audit_logs FOR INSERT TO org_runtime
    WITH CHECK (
        current_user = 'org_runtime'
        AND action_type = 'PLATFORM_ADMIN_ORG_ACCESS'
        AND event_kind = 'ACCESS'
        AND event_scope = 'ORGANIZATION'
        AND target_entity_type = 'ORGANIZATION'
        AND requires_target_entity
        AND NOT requires_class_scope AND scope_class_id IS NULL
        AND NOT requires_operation_group AND operation_group_id IS NULL
        AND NOT is_undo AND undo_of_audit_log_id IS NULL
        AND organization_id IS NOT NULL AND organization_id = target_entity_id
        AND actor_user_id = current_setting('app.iam_actor_user_id', true)::uuid
        AND event_metadata->>'operationCode' = current_setting('app.iam_operation_code', true)
        AND (
            (event_metadata->>'outcome' = 'ALLOWED' AND reason_code IS NULL)
            OR (event_metadata->>'outcome' = 'FORBIDDEN' AND reason_code = 'FORBIDDEN')
        )
        AND EXISTS (SELECT 1 FROM platform_administrators pa
            WHERE pa.user_id = current_setting('app.iam_actor_user_id', true)::uuid
            AND pa.revoked_at IS NULL)
        AND (
            -- GLOBAL LIST: her görüntülenen kurum için ayrı satır. app.organization_id kurulmaz.
            (current_setting('app.iam_operation_scope', true) = 'GLOBAL'
             AND current_setting('app.iam_operation_code', true) = 'ORG_LIST'
             AND current_setting('app.iam_platform_admin_support_access', true) IS DISTINCT FROM 'true')
            OR
            -- ORGANIZATION DETAIL/lifecycle/marka-palet-modül-logo: doğrulanmış support flag
            -- zorunlu. Liste, platform_administrators_select_org_runtime_organization ile aynı
            -- kanonik 14 kodu taşır (ORG-001 + ORG-002); ayrı bir ORG-002 listesi tanımlanmaz.
            (current_setting('app.iam_operation_scope', true) = 'ORGANIZATION'
             AND current_setting('app.iam_platform_admin_support_access', true) = 'true'
             AND current_setting('app.iam_operation_code', true) IN
                 ('ORG_DETAIL', 'ORG_UPDATE_IDENTITY', 'ORG_SUSPEND', 'ORG_ACTIVATE', 'ORG_ARCHIVE',
                  'ORG_VIEW_BRAND', 'ORG_UPDATE_BRAND', 'ORG_VIEW_BRAND_COLORS', 'ORG_UPDATE_BRAND_COLORS',
                  'ORG_VIEW_MODULES', 'ORG_UPDATE_MODULES', 'ORG_UPLOAD_LOGO', 'ORG_REMOVE_LOGO', 'ORG_VIEW_LOGO'))
        )
    );

-- ORG_SETTING_CHANGED: kurum aktörü (yalnız ORG_UPDATE_IDENTITY, gerçek canlı yetkilendirme) veya
-- platform-admin (support flag DOĞRULANMIŞ) PATCH yolu. Her iki yol da ORGANIZATION scope.
-- ORG_UPDATE_IDENTITY (ORG-001, v1 payload_schema_version=1) ile ORG_UPDATE_BRAND/
-- ORG_UPDATE_BRAND_COLORS/ORG_UPDATE_MODULES/ORG_UPLOAD_LOGO/ORG_REMOVE_LOGO (ORG-002,
-- payload_schema_version=2) aynı action_type'ı üretir; hangi payload_schema_version'ın kabul
-- edildiği audit_action_catalog FK'si ve Java tarafındaki AuditEvent.Factory.orgSettingChanged/
-- orgSettingChangedV2 ayrımıyla belirlenir. ORG-002'nin 5 yazma kodu için kurum aktörü kolu
-- KASITLI OLARAK YOKTUR: bu kodların gerçek üyelik/rol/izin doğrulaması (organization_brand_colors/
-- organization_modules gibi henüz ORG-003 kapsamında olmayan verilere dayanır) ORG-005'e aittir;
-- ORG-003 bunu inşa etmeden yarı açık bir org-actor yolu bırakmak yerine yalnız platform-admin
-- yolunu (zaten tam doğrulanmış) açık bırakır, default-deny. Yalnız ORG_UPDATE_IDENTITY, tabloları
-- zaten ORG-003 kapsamında olan org_actor_has_identity_update_access ile gerçek canlı doğrulamaya
-- sahiptir.
CREATE POLICY audit_logs_insert_org_setting_changed ON audit_logs FOR INSERT TO org_runtime
    WITH CHECK (
        current_user = 'org_runtime'
        AND current_setting('app.iam_operation_scope', true) = 'ORGANIZATION'
        AND current_setting('app.iam_operation_code', true) IN
            ('ORG_UPDATE_IDENTITY', 'ORG_UPDATE_BRAND', 'ORG_UPDATE_BRAND_COLORS', 'ORG_UPDATE_MODULES',
             'ORG_UPLOAD_LOGO', 'ORG_REMOVE_LOGO')
        AND event_metadata->>'operationCode' = current_setting('app.iam_operation_code', true)
        AND action_type = 'ORG_SETTING_CHANGED'
        AND event_kind = 'DATA_MUTATION'
        AND event_scope = 'ORGANIZATION'
        AND target_entity_type = 'ORGANIZATION'
        AND requires_target_entity
        AND NOT requires_class_scope AND scope_class_id IS NULL
        AND NOT requires_operation_group AND operation_group_id IS NULL
        AND NOT is_undo AND undo_of_audit_log_id IS NULL
        AND organization_id IS NOT NULL AND organization_id = target_entity_id
        AND organization_id = current_setting('app.organization_id', true)::uuid
        AND actor_user_id = current_setting('app.iam_actor_user_id', true)::uuid
        AND (
            -- Kurum aktörü yolu: yalnız ORG_UPDATE_IDENTITY, gerçek üyelik/rol/izin doğrulaması.
            (current_setting('app.iam_platform_admin_support_access', true) IS DISTINCT FROM 'true'
             AND current_setting('app.iam_operation_code', true) = 'ORG_UPDATE_IDENTITY'
             AND org_actor_has_identity_update_access(organization_id, current_setting('app.iam_actor_user_id', true)::uuid))
            OR
            -- Platform-admin destek yolu (6 kodun tamamı): doğrulanmış support flag + aktif admin.
            (current_setting('app.iam_platform_admin_support_access', true) = 'true'
             AND EXISTS (SELECT 1 FROM platform_administrators pa
                 WHERE pa.user_id = current_setting('app.iam_actor_user_id', true)::uuid
                 AND pa.revoked_at IS NULL))
        )
    );

-- ORG_STATUS_CHANGED: SUSPEND/ACTIVATE/ARCHIVE. Yalnız platform-admin support yolu.
-- event_metadata->>'operationCode' context ile eşleşmeli; ayrıca old_value/new_value status
-- geçişi operationCode ile uyumlu olmalı -- Java tarafındaki AuditEvent.validateStatusTransition
-- doğrulamasının DB'deki aynası. ORG_SUSPEND yalnız ACTIVE->SUSPENDED, ORG_ACTIVATE yalnız
-- SUSPENDED->ACTIVE, ORG_ARCHIVE yalnız ACTIVE veya SUSPENDED->ARCHIVED. Application katmanı
-- atlanıp uyumsuz bir geçiş taşıyan satır INSERT edilmeye çalışılırsa (context doğru olsa dahi)
-- 42501 ile reddedilir.
CREATE POLICY audit_logs_insert_org_status_changed ON audit_logs FOR INSERT TO org_runtime
    WITH CHECK (
        current_user = 'org_runtime'
        AND current_setting('app.iam_operation_scope', true) = 'ORGANIZATION'
        AND current_setting('app.iam_platform_admin_support_access', true) = 'true'
        AND current_setting('app.iam_operation_code', true) IN ('ORG_SUSPEND', 'ORG_ACTIVATE', 'ORG_ARCHIVE')
        AND event_metadata->>'operationCode' = current_setting('app.iam_operation_code', true)
        AND (
            (current_setting('app.iam_operation_code', true) = 'ORG_SUSPEND'
                AND old_value->>'status' = 'ACTIVE' AND new_value->>'status' = 'SUSPENDED')
            OR (current_setting('app.iam_operation_code', true) = 'ORG_ACTIVATE'
                AND old_value->>'status' = 'SUSPENDED' AND new_value->>'status' = 'ACTIVE')
            OR (current_setting('app.iam_operation_code', true) = 'ORG_ARCHIVE'
                AND old_value->>'status' IN ('ACTIVE', 'SUSPENDED') AND new_value->>'status' = 'ARCHIVED')
        )
        AND action_type = 'ORG_STATUS_CHANGED'
        AND event_kind = 'DATA_MUTATION'
        AND event_scope = 'ORGANIZATION'
        AND target_entity_type = 'ORGANIZATION'
        AND requires_target_entity
        AND NOT requires_class_scope AND scope_class_id IS NULL
        AND NOT requires_operation_group AND operation_group_id IS NULL
        AND NOT is_undo AND undo_of_audit_log_id IS NULL
        AND organization_id IS NOT NULL AND organization_id = target_entity_id
        AND organization_id = current_setting('app.organization_id', true)::uuid
        AND actor_user_id = current_setting('app.iam_actor_user_id', true)::uuid
        AND EXISTS (SELECT 1 FROM platform_administrators pa
            WHERE pa.user_id = current_setting('app.iam_actor_user_id', true)::uuid
            AND pa.revoked_at IS NULL)
    );
