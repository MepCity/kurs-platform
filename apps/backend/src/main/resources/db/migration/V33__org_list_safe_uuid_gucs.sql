-- ALPHA-003: PostgreSQL does not guarantee short-circuit evaluation between permissive RLS
-- policies. Supabase session-pooler connections can therefore expose a transaction-local custom
-- GUC as an empty string after the transaction ends. Treat that residual value as absent before
-- UUID conversion so unrelated SELECT policies deny access instead of raising SQLSTATE 22P02.

DROP POLICY organizations_select_global ON organizations;
CREATE POLICY organizations_select_global ON organizations FOR SELECT TO org_runtime USING (
    current_user = 'org_runtime'
    AND current_setting('app.iam_operation_scope', true) = 'GLOBAL'
    AND current_setting('app.iam_operation_code', true) = 'ORG_LIST'
    AND EXISTS (SELECT 1 FROM platform_administrators pa
        WHERE pa.user_id = NULLIF(current_setting('app.iam_actor_user_id', true), '')::uuid
          AND pa.revoked_at IS NULL)
);

DROP POLICY organizations_select_organization ON organizations;
CREATE POLICY organizations_select_organization ON organizations FOR SELECT TO org_runtime USING (
    current_user = 'org_runtime'
    AND current_setting('app.iam_operation_scope', true) = 'ORGANIZATION'
    AND id = NULLIF(current_setting('app.organization_id', true), '')::uuid
    AND ((current_setting('app.iam_platform_admin_support_access', true) = 'true'
          AND current_setting('app.iam_operation_code', true) IN
              ('ORG_DETAIL', 'ORG_UPDATE_IDENTITY', 'ORG_SUSPEND', 'ORG_ACTIVATE', 'ORG_ARCHIVE')
          AND EXISTS (SELECT 1 FROM platform_administrators pa
              WHERE pa.user_id = NULLIF(current_setting('app.iam_actor_user_id', true), '')::uuid
                AND pa.revoked_at IS NULL))
         OR (current_setting('app.iam_platform_admin_support_access', true) IS DISTINCT FROM 'true'
          AND NOT EXISTS (SELECT 1 FROM platform_administrators pa
              WHERE pa.user_id = NULLIF(current_setting('app.iam_actor_user_id', true), '')::uuid
                AND pa.revoked_at IS NULL)
          AND status = 'ACTIVE'
          AND current_setting('app.iam_operation_code', true) IN
              ('ORG_LIST', 'ORG_DETAIL', 'ORG_UPDATE_IDENTITY')))
);

DROP POLICY organizations_select_iam_auth_actor_membership ON organizations;
CREATE POLICY organizations_select_iam_auth_actor_membership ON organizations FOR SELECT TO iam_runtime USING (
    current_user = 'iam_runtime'
    AND current_setting('app.iam_operation_scope', true) = 'IAM_AUTH'
    AND current_setting('app.iam_operation_code', true) IN
        ('CONTEXT_SELECTION_LIST', 'CONTEXT_ACTIVATE', 'SESSION_INFO')
    AND EXISTS (
        SELECT 1 FROM organization_memberships om
        WHERE om.organization_id = organizations.id
          AND om.user_id = NULLIF(current_setting('app.iam_actor_user_id', true), '')::uuid
    )
);

DROP POLICY organizations_org_brand_select ON organizations;
CREATE POLICY organizations_org_brand_select ON organizations FOR SELECT TO org_runtime USING (
    current_user = 'org_runtime'
    AND current_setting('app.iam_operation_scope', true) = 'ORGANIZATION'
    AND id = NULLIF(current_setting('app.organization_id', true), '')::uuid
    AND current_setting('app.iam_operation_code', true) IN
       ('ORG_VIEW_BRAND', 'ORG_UPDATE_BRAND', 'ORG_VIEW_BRAND_COLORS',
        'ORG_UPDATE_BRAND_COLORS', 'ORG_VIEW_MODULES', 'ORG_UPDATE_MODULES')
    AND ((current_setting('app.iam_platform_admin_support_access', true) = 'true'
          AND EXISTS (SELECT 1 FROM platform_administrators p
              WHERE p.user_id = NULLIF(current_setting('app.iam_actor_user_id', true), '')::uuid
                AND p.revoked_at IS NULL))
         OR (current_setting('app.iam_platform_admin_support_access', true) IS DISTINCT FROM 'true'
             AND status = 'ACTIVE'
             AND org_actor_has_brand_access(
                 id,
                 NULLIF(current_setting('app.iam_actor_user_id', true), '')::uuid,
                 NULL)))
);
