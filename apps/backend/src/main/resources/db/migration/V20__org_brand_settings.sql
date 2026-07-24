-- Görev: ORG-005
-- Dosyasız kurum marka, yardımcı palet ve etkin modül ayarları.

CREATE TABLE organization_brand_colors (
    organization_id UUID NOT NULL REFERENCES organizations(id),
    color_hex TEXT NOT NULL CHECK (color_hex ~ '^#[0-9A-Fa-f]{6}$'),
    sort_order INTEGER NOT NULL CHECK (sort_order BETWEEN 0 AND 999),
    PRIMARY KEY (organization_id, color_hex)
);
CREATE INDEX organization_brand_colors_order_idx ON organization_brand_colors (organization_id, sort_order, color_hex);

CREATE TABLE organization_modules (
    organization_id UUID NOT NULL REFERENCES organizations(id),
    module_code TEXT NOT NULL CHECK (module_code IN ('ATT','PROGRAM','CONTENT','PROGRESS','EXPORT','AUDIT')),
    is_enabled BOOLEAN NOT NULL,
    sort_order INTEGER NOT NULL CHECK (sort_order BETWEEN 0 AND 999),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT transaction_timestamp(),
    -- Bootstrap/test-created organizations may predate an acting user; subsequent PATCH writes
    -- always set this field to the authenticated actor.
    updated_by_user_id UUID REFERENCES users(id),
    PRIMARY KEY (organization_id, module_code)
);

-- ORG-005 write quota.  The actor+institution+operation key is shared across instances;
-- 60 new mutations/minute is the documented safe default, configurable at runtime.
CREATE TABLE organization_brand_rate_limits (
    actor_user_id UUID NOT NULL REFERENCES users(id),
    organization_id UUID NOT NULL REFERENCES organizations(id),
    operation_code TEXT NOT NULL CHECK (operation_code IN ('ORG_VIEW_BRAND','ORG_UPDATE_BRAND','ORG_VIEW_BRAND_COLORS',
                                                           'ORG_UPDATE_BRAND_COLORS','ORG_VIEW_MODULES','ORG_UPDATE_MODULES')),
    window_started_at TIMESTAMPTZ NOT NULL,
    request_count INTEGER NOT NULL CHECK (request_count >= 1),
    PRIMARY KEY (actor_user_id, organization_id, operation_code, window_started_at)
);
CREATE INDEX organization_brand_rate_limits_expiry_idx ON organization_brand_rate_limits (window_started_at);
GRANT SELECT, INSERT, UPDATE ON organization_brand_rate_limits TO org_runtime;
ALTER TABLE organization_brand_rate_limits ENABLE ROW LEVEL SECURITY;
ALTER TABLE organization_brand_rate_limits FORCE ROW LEVEL SECURITY;
CREATE POLICY organization_brand_rate_limits_org_runtime ON organization_brand_rate_limits FOR ALL TO org_runtime
USING (
    current_user = 'org_runtime'
    AND current_setting('app.iam_operation_scope', true) = 'ORGANIZATION'
    AND actor_user_id = current_setting('app.iam_actor_user_id', true)::uuid
    AND organization_id = current_setting('app.organization_id', true)::uuid
    AND operation_code = current_setting('app.iam_operation_code', true)
    AND operation_code IN ('ORG_VIEW_BRAND','ORG_UPDATE_BRAND','ORG_VIEW_BRAND_COLORS',
                           'ORG_UPDATE_BRAND_COLORS','ORG_VIEW_MODULES','ORG_UPDATE_MODULES')
)
WITH CHECK (
    current_user = 'org_runtime'
    AND current_setting('app.iam_operation_scope', true) = 'ORGANIZATION'
    AND actor_user_id = current_setting('app.iam_actor_user_id', true)::uuid
    AND organization_id = current_setting('app.organization_id', true)::uuid
    AND operation_code = current_setting('app.iam_operation_code', true)
    AND operation_code IN ('ORG_VIEW_BRAND','ORG_UPDATE_BRAND','ORG_VIEW_BRAND_COLORS',
                           'ORG_UPDATE_BRAND_COLORS','ORG_VIEW_MODULES','ORG_UPDATE_MODULES')
);
-- Retention is an operations job: delete windows older than the configured maximum retention
-- through a separately privileged maintenance connection; runtime tenants never receive a
-- cross-tenant delete grant. The expiry index above makes that bounded cleanup efficient.
CREATE INDEX organization_modules_order_idx ON organization_modules (organization_id, sort_order, module_code);

-- Every organization has the same six visible modules.  Existing rows are backfilled in this
-- deterministic order; newly created organizations are seeded by the after-insert trigger below.
INSERT INTO organization_modules (organization_id, module_code, is_enabled, sort_order, updated_by_user_id)
SELECT o.id, seed.module_code, true, seed.sort_order, o.updated_by_user_id
FROM organizations o
CROSS JOIN (VALUES ('ATT',0),('PROGRAM',1),('CONTENT',2),('PROGRESS',3),('EXPORT',4),('AUDIT',5))
    AS seed(module_code, sort_order)
ON CONFLICT (organization_id, module_code) DO NOTHING;

GRANT SELECT, INSERT (organization_id, color_hex, sort_order), DELETE ON organization_brand_colors TO org_runtime;
GRANT SELECT, INSERT (organization_id, module_code, is_enabled, sort_order, updated_by_user_id),
    UPDATE (is_enabled, sort_order, updated_at, updated_by_user_id) ON organization_modules TO org_runtime;

ALTER TABLE organization_brand_colors ENABLE ROW LEVEL SECURITY;
ALTER TABLE organization_brand_colors FORCE ROW LEVEL SECURITY;
ALTER TABLE organization_modules ENABLE ROW LEVEL SECURITY;
ALTER TABLE organization_modules FORCE ROW LEVEL SECURITY;

CREATE OR REPLACE FUNCTION public.org_actor_has_brand_access(target_org UUID, actor UUID, required_permission TEXT)
RETURNS boolean
LANGUAGE sql
STABLE
SECURITY DEFINER
SET search_path = pg_catalog, public
AS $$
    SELECT current_setting('app.iam_operation_scope', true) = 'ORGANIZATION'
       AND target_org = current_setting('app.organization_id', true)::uuid
       AND actor = current_setting('app.iam_actor_user_id', true)::uuid
       AND (
           (required_permission IS NULL AND current_setting('app.iam_operation_code', true) IN
               ('ORG_VIEW_BRAND', 'ORG_UPDATE_BRAND', 'ORG_VIEW_BRAND_COLORS',
                'ORG_UPDATE_BRAND_COLORS', 'ORG_VIEW_MODULES', 'ORG_UPDATE_MODULES'))
           OR (required_permission = 'BRAND_MANAGE' AND current_setting('app.iam_operation_code', true) IN
               ('ORG_UPDATE_BRAND', 'ORG_UPDATE_BRAND_COLORS'))
           OR (required_permission = 'MODULE_MANAGE' AND current_setting('app.iam_operation_code', true) =
               'ORG_UPDATE_MODULES')
       )
       AND EXISTS (
        SELECT 1 FROM public.organization_memberships om
        WHERE om.organization_id = target_org AND om.user_id = actor AND om.status = 'ACTIVE'
          AND (EXISTS (SELECT 1 FROM public.organization_membership_roles r WHERE r.organization_membership_id = om.id
                       AND r.role = 'ORG_ADMIN' AND r.revoked_at IS NULL)
               OR (required_permission IS NULL AND EXISTS (SELECT 1 FROM public.organization_membership_roles r
                    WHERE r.organization_membership_id = om.id AND r.revoked_at IS NULL))
               OR EXISTS (SELECT 1 FROM public.organization_membership_roles r JOIN public.organization_membership_permissions p
                    ON p.target_membership_role_id = r.id WHERE r.organization_membership_id = om.id
                    AND r.role = 'TEACHER' AND r.revoked_at IS NULL AND p.permission_code = required_permission
                    AND p.revoked_at IS NULL))
    );
$$;
REVOKE ALL ON FUNCTION public.org_actor_has_brand_access(UUID, UUID, TEXT) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION public.org_actor_has_brand_access(UUID, UUID, TEXT) TO org_runtime;

-- The same function is used by Java authorization and RLS; a platform-admin support flag is
-- accepted only after the actor-only platform_administrators SELECT has succeeded.
CREATE POLICY organizations_org_brand_select ON organizations FOR SELECT TO org_runtime USING (
    current_user = 'org_runtime' AND current_setting('app.iam_operation_scope', true) = 'ORGANIZATION'
    AND id = current_setting('app.organization_id', true)::uuid
    AND current_setting('app.iam_operation_code', true) IN
       ('ORG_VIEW_BRAND','ORG_UPDATE_BRAND','ORG_VIEW_BRAND_COLORS','ORG_UPDATE_BRAND_COLORS','ORG_VIEW_MODULES','ORG_UPDATE_MODULES')
    AND ((current_setting('app.iam_platform_admin_support_access', true) = 'true' AND EXISTS
          (SELECT 1 FROM platform_administrators p WHERE p.user_id = current_setting('app.iam_actor_user_id', true)::uuid AND p.revoked_at IS NULL))
         OR (current_setting('app.iam_platform_admin_support_access', true) IS DISTINCT FROM 'true'
             AND status = 'ACTIVE' AND org_actor_has_brand_access(id, current_setting('app.iam_actor_user_id', true)::uuid, NULL))
    )
);
CREATE POLICY organizations_org_brand_update ON organizations FOR UPDATE TO org_runtime USING (
    current_user = 'org_runtime' AND id = current_setting('app.organization_id', true)::uuid
    AND current_setting('app.iam_operation_code', true) IN ('ORG_UPDATE_BRAND','ORG_UPDATE_BRAND_COLORS','ORG_UPDATE_MODULES')
    AND ((current_setting('app.iam_platform_admin_support_access', true) = 'true'
          AND EXISTS (SELECT 1 FROM platform_administrators p WHERE p.user_id=current_setting('app.iam_actor_user_id',true)::uuid AND p.revoked_at IS NULL))
         OR (current_setting('app.iam_operation_code',true) IN ('ORG_UPDATE_BRAND','ORG_UPDATE_BRAND_COLORS')
             AND org_actor_has_brand_access(id, current_setting('app.iam_actor_user_id', true)::uuid, 'BRAND_MANAGE'))
         OR (current_setting('app.iam_operation_code',true)='ORG_UPDATE_MODULES'
             AND org_actor_has_brand_access(id, current_setting('app.iam_actor_user_id', true)::uuid, 'MODULE_MANAGE')))
) WITH CHECK (status <> 'ARCHIVED');

CREATE POLICY brand_colors_org_runtime_select ON organization_brand_colors FOR SELECT TO org_runtime USING (
    current_user = 'org_runtime'
    AND current_setting('app.iam_operation_scope', true) = 'ORGANIZATION'
    AND organization_id = current_setting('app.organization_id', true)::uuid
    AND current_setting('app.iam_operation_code', true) IN ('ORG_VIEW_BRAND_COLORS', 'ORG_UPDATE_BRAND_COLORS')
    AND ( (current_setting('app.iam_platform_admin_support_access', true) = 'true'
           AND EXISTS (SELECT 1 FROM platform_administrators p WHERE p.user_id=current_setting('app.iam_actor_user_id',true)::uuid AND p.revoked_at IS NULL))
          OR (current_setting('app.iam_platform_admin_support_access', true) IS DISTINCT FROM 'true'
              AND org_actor_has_brand_access(organization_id, current_setting('app.iam_actor_user_id', true)::uuid,
                  CASE WHEN current_setting('app.iam_operation_code', true) = 'ORG_UPDATE_BRAND_COLORS' THEN 'BRAND_MANAGE' ELSE NULL END)) )
);
CREATE POLICY brand_colors_org_runtime_insert ON organization_brand_colors FOR INSERT TO org_runtime WITH CHECK (
    current_user = 'org_runtime'
    AND current_setting('app.iam_operation_scope', true) = 'ORGANIZATION'
    AND current_setting('app.iam_operation_code', true) = 'ORG_UPDATE_BRAND_COLORS'
    AND organization_id = current_setting('app.organization_id', true)::uuid
    AND ( (current_setting('app.iam_platform_admin_support_access', true) = 'true'
           AND EXISTS (SELECT 1 FROM platform_administrators p WHERE p.user_id=current_setting('app.iam_actor_user_id',true)::uuid AND p.revoked_at IS NULL))
          OR (current_setting('app.iam_platform_admin_support_access', true) IS DISTINCT FROM 'true'
              AND org_actor_has_brand_access(organization_id, current_setting('app.iam_actor_user_id', true)::uuid,
                  'BRAND_MANAGE')) )
);
CREATE POLICY brand_colors_org_runtime_delete ON organization_brand_colors FOR DELETE TO org_runtime USING (
    current_user = 'org_runtime'
    AND current_setting('app.iam_operation_scope', true) = 'ORGANIZATION'
    AND current_setting('app.iam_operation_code', true) = 'ORG_UPDATE_BRAND_COLORS'
    AND organization_id = current_setting('app.organization_id', true)::uuid
    AND ( (current_setting('app.iam_platform_admin_support_access', true) = 'true'
           AND EXISTS (SELECT 1 FROM platform_administrators p WHERE p.user_id=current_setting('app.iam_actor_user_id',true)::uuid AND p.revoked_at IS NULL))
          OR (current_setting('app.iam_platform_admin_support_access', true) IS DISTINCT FROM 'true'
              AND org_actor_has_brand_access(organization_id, current_setting('app.iam_actor_user_id', true)::uuid,
                  'BRAND_MANAGE')) )
);

CREATE POLICY modules_org_runtime_select ON organization_modules FOR SELECT TO org_runtime USING (
    current_user = 'org_runtime'
    AND current_setting('app.iam_operation_scope', true) = 'ORGANIZATION'
    AND organization_id = current_setting('app.organization_id', true)::uuid
    AND current_setting('app.iam_operation_code', true) IN ('ORG_VIEW_MODULES', 'ORG_UPDATE_MODULES')
    AND ( (current_setting('app.iam_platform_admin_support_access', true) = 'true'
           AND EXISTS (SELECT 1 FROM platform_administrators p WHERE p.user_id=current_setting('app.iam_actor_user_id',true)::uuid AND p.revoked_at IS NULL))
          OR (current_setting('app.iam_platform_admin_support_access', true) IS DISTINCT FROM 'true'
              AND org_actor_has_brand_access(organization_id, current_setting('app.iam_actor_user_id', true)::uuid,
                  CASE WHEN current_setting('app.iam_operation_code', true) = 'ORG_UPDATE_MODULES' THEN 'MODULE_MANAGE' ELSE NULL END)) )
);
CREATE POLICY modules_org_runtime_update ON organization_modules FOR UPDATE TO org_runtime USING (
    current_user = 'org_runtime'
    AND current_setting('app.iam_operation_scope', true) = 'ORGANIZATION'
    AND current_setting('app.iam_operation_code', true) = 'ORG_UPDATE_MODULES'
    AND organization_id = current_setting('app.organization_id', true)::uuid
    AND ( (current_setting('app.iam_platform_admin_support_access', true) = 'true'
           AND EXISTS (SELECT 1 FROM platform_administrators p WHERE p.user_id=current_setting('app.iam_actor_user_id',true)::uuid AND p.revoked_at IS NULL))
          OR (current_setting('app.iam_platform_admin_support_access', true) IS DISTINCT FROM 'true'
              AND org_actor_has_brand_access(organization_id, current_setting('app.iam_actor_user_id', true)::uuid,
                  'MODULE_MANAGE')) )
) WITH CHECK (organization_id = current_setting('app.organization_id', true)::uuid);
CREATE POLICY modules_org_create_seed ON organization_modules FOR INSERT TO org_runtime WITH CHECK (
    current_user='org_runtime' AND current_setting('app.iam_operation_scope',true)='GLOBAL'
    AND current_setting('app.iam_operation_code',true)='ORG_CREATE'
    AND EXISTS (SELECT 1 FROM platform_administrators p
        WHERE p.user_id=current_setting('app.iam_actor_user_id',true)::uuid AND p.revoked_at IS NULL)
);

-- ORG-004 creates an organization under GLOBAL/ORG_CREATE.  This trigger runs in that same
-- transaction, therefore module seed rollback is atomic with organization creation.
CREATE OR REPLACE FUNCTION org_seed_modules_after_organization_insert()
RETURNS trigger
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = pg_catalog, public
AS $$
BEGIN
    INSERT INTO public.organization_modules (organization_id, module_code, is_enabled, sort_order, updated_by_user_id)
    VALUES (NEW.id,'ATT',true,0,NEW.updated_by_user_id), (NEW.id,'PROGRAM',true,1,NEW.updated_by_user_id),
           (NEW.id,'CONTENT',true,2,NEW.updated_by_user_id), (NEW.id,'PROGRESS',true,3,NEW.updated_by_user_id),
           (NEW.id,'EXPORT',true,4,NEW.updated_by_user_id), (NEW.id,'AUDIT',true,5,NEW.updated_by_user_id)
    ON CONFLICT (organization_id,module_code) DO NOTHING;
    RETURN NEW;
END;
$$;
CREATE TRIGGER organizations_seed_modules AFTER INSERT ON organizations
FOR EACH ROW EXECUTE FUNCTION org_seed_modules_after_organization_insert();

-- Extend the existing column-level write guard without widening any lifecycle operation.
CREATE OR REPLACE FUNCTION organizations_org_runtime_write_guard()
RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF current_user <> 'org_runtime' THEN RETURN NEW; END IF;
    IF TG_OP = 'INSERT' THEN
        IF NEW.status <> 'ACTIVE' OR NEW.row_version <> 1
           OR NEW.created_by_user_id IS DISTINCT FROM current_setting('app.iam_actor_user_id', true)::uuid
           OR NEW.updated_by_user_id IS DISTINCT FROM current_setting('app.iam_actor_user_id', true)::uuid THEN
            RAISE EXCEPTION 'ORG organizations insert guard rejected' USING ERRCODE = '42501';
        END IF;
        RETURN NEW;
    END IF;
    IF NEW.id IS DISTINCT FROM OLD.id OR NEW.created_at IS DISTINCT FROM OLD.created_at
       OR NEW.created_by_user_id IS DISTINCT FROM OLD.created_by_user_id
       OR NEW.updated_by_user_id IS DISTINCT FROM current_setting('app.iam_actor_user_id', true)::uuid
       OR NEW.row_version <> OLD.row_version + 1 THEN
        RAISE EXCEPTION 'ORG organization metadata update rejected' USING ERRCODE = '42501';
    END IF;
    IF current_setting('app.iam_operation_code', true) = 'ORG_UPDATE_BRAND' THEN
        IF NEW.status IS DISTINCT FROM OLD.status OR NEW.name IS DISTINCT FROM OLD.name
           OR NEW.short_name IS DISTINCT FROM OLD.short_name OR NEW.default_timezone IS DISTINCT FROM OLD.default_timezone THEN
            RAISE EXCEPTION 'ORG brand update may only change colors' USING ERRCODE = '42501';
        END IF;
    ELSIF current_setting('app.iam_operation_code', true) IN ('ORG_UPDATE_BRAND_COLORS', 'ORG_UPDATE_MODULES') THEN
        IF NEW.status IS DISTINCT FROM OLD.status OR NEW.name IS DISTINCT FROM OLD.name
           OR NEW.short_name IS DISTINCT FROM OLD.short_name OR NEW.default_timezone IS DISTINCT FROM OLD.default_timezone
           OR NEW.primary_color IS DISTINCT FROM OLD.primary_color OR NEW.secondary_color IS DISTINCT FROM OLD.secondary_color THEN
            RAISE EXCEPTION 'ORG palette/module update may only increment row version' USING ERRCODE = '42501';
        END IF;
    ELSIF current_setting('app.iam_operation_code', true) = 'ORG_UPDATE_IDENTITY' THEN
        IF NEW.status IS DISTINCT FROM OLD.status THEN RAISE EXCEPTION 'ORG identity update cannot change status' USING ERRCODE = '42501'; END IF;
    ELSIF current_setting('app.iam_platform_admin_support_access', true) = 'true'
          AND ((current_setting('app.iam_operation_code', true) = 'ORG_SUSPEND' AND OLD.status = 'ACTIVE' AND NEW.status = 'SUSPENDED')
            OR (current_setting('app.iam_operation_code', true) = 'ORG_ACTIVATE' AND OLD.status = 'SUSPENDED' AND NEW.status = 'ACTIVE')
            OR (current_setting('app.iam_operation_code', true) = 'ORG_ARCHIVE' AND OLD.status IN ('ACTIVE','SUSPENDED') AND NEW.status = 'ARCHIVED')) THEN
        IF NEW.name IS DISTINCT FROM OLD.name OR NEW.short_name IS DISTINCT FROM OLD.short_name
           OR NEW.primary_color IS DISTINCT FROM OLD.primary_color OR NEW.secondary_color IS DISTINCT FROM OLD.secondary_color
           OR NEW.default_timezone IS DISTINCT FROM OLD.default_timezone THEN
            RAISE EXCEPTION 'ORG lifecycle update may only change status' USING ERRCODE = '42501';
        END IF;
    ELSE RAISE EXCEPTION 'ORG unsupported organization update operation' USING ERRCODE = '42501'; END IF;
    RETURN NEW;
END;
$$;

-- V3's default-deny policies intentionally predate ORG-005. Recreate their ORG-005 branches
-- with live, in-transaction authorization; a support flag is never sufficient on its own.
DROP POLICY idempotency_keys_org_runtime_organization ON idempotency_keys;
CREATE POLICY idempotency_keys_org_runtime_organization ON idempotency_keys FOR ALL TO org_runtime USING (
    current_user='org_runtime' AND current_setting('app.iam_operation_scope',true)='ORGANIZATION'
    AND scope_type='ORGANIZATION' AND organization_id=current_setting('app.organization_id',true)::uuid
    AND user_id=current_setting('app.iam_actor_user_id',true)::uuid
    AND ((current_setting('app.iam_platform_admin_support_access',true)='true'
          AND current_setting('app.iam_operation_code',true) IN ('ORG_UPDATE_IDENTITY','ORG_SUSPEND','ORG_ACTIVATE','ORG_ARCHIVE','ORG_UPDATE_BRAND','ORG_UPDATE_BRAND_COLORS','ORG_UPDATE_MODULES')
          AND EXISTS (SELECT 1 FROM platform_administrators p WHERE p.user_id=current_setting('app.iam_actor_user_id',true)::uuid AND p.revoked_at IS NULL))
         OR (current_setting('app.iam_platform_admin_support_access',true) IS DISTINCT FROM 'true'
          AND ((current_setting('app.iam_operation_code',true)='ORG_UPDATE_IDENTITY' AND org_actor_has_identity_update_access(organization_id,current_setting('app.iam_actor_user_id',true)::uuid))
               OR (current_setting('app.iam_operation_code',true) IN ('ORG_UPDATE_BRAND','ORG_UPDATE_BRAND_COLORS') AND org_actor_has_brand_access(organization_id,current_setting('app.iam_actor_user_id',true)::uuid,'BRAND_MANAGE'))
               OR (current_setting('app.iam_operation_code',true)='ORG_UPDATE_MODULES' AND org_actor_has_brand_access(organization_id,current_setting('app.iam_actor_user_id',true)::uuid,'MODULE_MANAGE')))))
) WITH CHECK (scope_type='ORGANIZATION' AND organization_id=current_setting('app.organization_id',true)::uuid
    AND user_id=current_setting('app.iam_actor_user_id',true)::uuid AND operation_type=current_setting('app.iam_operation_code',true));

DROP POLICY audit_logs_insert_org_setting_changed ON audit_logs;
CREATE POLICY audit_logs_insert_org_setting_changed ON audit_logs FOR INSERT TO org_runtime WITH CHECK (
    current_user='org_runtime' AND current_setting('app.iam_operation_scope',true)='ORGANIZATION'
    AND current_setting('app.iam_operation_code',true) IN ('ORG_UPDATE_IDENTITY','ORG_UPDATE_BRAND','ORG_UPDATE_BRAND_COLORS','ORG_UPDATE_MODULES','ORG_UPLOAD_LOGO','ORG_REMOVE_LOGO')
    AND event_metadata->>'operationCode'=current_setting('app.iam_operation_code',true)
    AND action_type='ORG_SETTING_CHANGED' AND event_kind='DATA_MUTATION' AND event_scope='ORGANIZATION'
    AND target_entity_type='ORGANIZATION' AND requires_target_entity AND NOT requires_class_scope AND scope_class_id IS NULL
    AND NOT requires_operation_group AND operation_group_id IS NULL AND NOT is_undo AND undo_of_audit_log_id IS NULL
    AND organization_id=target_entity_id AND organization_id=current_setting('app.organization_id',true)::uuid
    AND actor_user_id=current_setting('app.iam_actor_user_id',true)::uuid
    AND ((current_setting('app.iam_platform_admin_support_access',true)='true' AND EXISTS (SELECT 1 FROM platform_administrators p WHERE p.user_id=current_setting('app.iam_actor_user_id',true)::uuid AND p.revoked_at IS NULL))
      OR (current_setting('app.iam_platform_admin_support_access',true) IS DISTINCT FROM 'true' AND
          ((current_setting('app.iam_operation_code',true)='ORG_UPDATE_IDENTITY' AND org_actor_has_identity_update_access(organization_id,current_setting('app.iam_actor_user_id',true)::uuid))
           OR (current_setting('app.iam_operation_code',true) IN ('ORG_UPDATE_BRAND','ORG_UPDATE_BRAND_COLORS') AND org_actor_has_brand_access(organization_id,current_setting('app.iam_actor_user_id',true)::uuid,'BRAND_MANAGE'))
           OR (current_setting('app.iam_operation_code',true)='ORG_UPDATE_MODULES' AND org_actor_has_brand_access(organization_id,current_setting('app.iam_actor_user_id',true)::uuid,'MODULE_MANAGE')))))
);
