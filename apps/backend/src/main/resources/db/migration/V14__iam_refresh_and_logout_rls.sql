-- IAM-005: refresh/logout first discover a family through the presented opaque refresh-token
-- hash, then narrow the same transaction to app.iam_current_family_id before any mutation.
CREATE POLICY refresh_tokens_select_refresh_logout_bootstrap ON refresh_tokens FOR SELECT USING (
    current_user = 'iam_runtime'
    AND current_setting('app.iam_operation_scope', true) = 'IAM_AUTH'
    AND current_setting('app.iam_operation_code', true) IN ('SESSION_REFRESH', 'SESSION_LOGOUT')
    AND token_hash = current_setting('app.iam_refresh_token_hash', true)
);

CREATE POLICY refresh_tokens_select_logout_access ON refresh_tokens FOR SELECT USING (
    current_user = 'iam_runtime' AND current_setting('app.iam_operation_scope', true) = 'IAM_AUTH'
    AND current_setting('app.iam_operation_code', true) = 'SESSION_LOGOUT'
    AND access_token_hash = current_setting('app.iam_access_token_hash', true)
);

CREATE POLICY refresh_token_families_select_refresh_logout ON refresh_token_families FOR SELECT USING (
    current_user = 'iam_runtime'
    AND current_setting('app.iam_operation_scope', true) = 'IAM_AUTH'
    AND current_setting('app.iam_operation_code', true) IN ('SESSION_REFRESH', 'SESSION_LOGOUT')
    AND id = current_setting('app.iam_current_family_id', true)::uuid
    AND user_id = current_setting('app.iam_actor_user_id', true)::uuid
    AND trusted_device_id = current_setting('app.iam_current_trusted_device_id', true)::uuid
);

-- Bootstrap is restricted to the family id learned from the presented opaque token hash in the
-- same transaction; actor/device are set immediately after this read and protect all later work.
CREATE POLICY refresh_token_families_select_refresh_logout_bootstrap ON refresh_token_families FOR SELECT USING (
    current_user = 'iam_runtime' AND current_setting('app.iam_operation_scope', true) = 'IAM_AUTH'
    AND current_setting('app.iam_operation_code', true) IN ('SESSION_REFRESH', 'SESSION_LOGOUT')
    AND id = current_setting('app.iam_current_family_id', true)::uuid
);

CREATE POLICY refresh_tokens_select_refresh_logout_family ON refresh_tokens FOR SELECT USING (
    current_user = 'iam_runtime'
    AND current_setting('app.iam_operation_scope', true) = 'IAM_AUTH'
    AND current_setting('app.iam_operation_code', true) IN ('SESSION_REFRESH', 'SESSION_LOGOUT')
    AND family_id = current_setting('app.iam_current_family_id', true)::uuid
    AND EXISTS (SELECT 1 FROM refresh_token_families rtf WHERE rtf.id = refresh_tokens.family_id
        AND rtf.user_id = current_setting('app.iam_actor_user_id', true)::uuid
        AND rtf.trusted_device_id = current_setting('app.iam_current_trusted_device_id', true)::uuid)
);

CREATE POLICY refresh_tokens_update_refresh_logout ON refresh_tokens FOR UPDATE USING (
    current_user = 'iam_runtime'
    AND current_setting('app.iam_operation_scope', true) = 'IAM_AUTH'
    AND current_setting('app.iam_operation_code', true) IN ('SESSION_REFRESH', 'SESSION_LOGOUT')
    AND family_id = current_setting('app.iam_current_family_id', true)::uuid
    AND EXISTS (SELECT 1 FROM refresh_token_families rtf WHERE rtf.id = refresh_tokens.family_id
        AND rtf.user_id = current_setting('app.iam_actor_user_id', true)::uuid
        AND rtf.trusted_device_id = current_setting('app.iam_current_trusted_device_id', true)::uuid)
) WITH CHECK (family_id = current_setting('app.iam_current_family_id', true)::uuid);

CREATE POLICY refresh_token_families_update_refresh_logout ON refresh_token_families FOR UPDATE USING (
    current_user = 'iam_runtime'
    AND current_setting('app.iam_operation_scope', true) = 'IAM_AUTH'
    AND current_setting('app.iam_operation_code', true) IN ('SESSION_REFRESH', 'SESSION_LOGOUT')
    AND id = current_setting('app.iam_current_family_id', true)::uuid
    AND user_id = current_setting('app.iam_actor_user_id', true)::uuid
    AND trusted_device_id = current_setting('app.iam_current_trusted_device_id', true)::uuid
) WITH CHECK (id = current_setting('app.iam_current_family_id', true)::uuid);

CREATE POLICY trusted_devices_select_refresh_logout ON trusted_devices FOR SELECT USING (
    current_user = 'iam_runtime'
    AND current_setting('app.iam_operation_scope', true) = 'IAM_AUTH'
    AND current_setting('app.iam_operation_code', true) IN ('SESSION_REFRESH', 'SESSION_LOGOUT')
    AND user_id = current_setting('app.iam_actor_user_id', true)::uuid
    AND id = current_setting('app.iam_current_trusted_device_id', true)::uuid
    AND revoked_at IS NULL
);

CREATE POLICY platform_administrators_select_refresh ON platform_administrators FOR SELECT USING (
    current_user = 'iam_runtime'
    AND current_setting('app.iam_operation_scope', true) = 'IAM_AUTH'
    AND current_setting('app.iam_operation_code', true) = 'SESSION_REFRESH'
    AND user_id = current_setting('app.iam_actor_user_id', true)::uuid AND revoked_at IS NULL
);

-- Existing actor-list policies do the membership/role reads for SESSION_INFO and activation;
-- refresh needs the same self-only visibility.
CREATE POLICY organization_memberships_select_refresh ON organization_memberships FOR SELECT USING (
    current_user = 'iam_runtime' AND current_setting('app.iam_operation_scope', true) = 'IAM_AUTH'
    AND current_setting('app.iam_operation_code', true) = 'SESSION_REFRESH'
    AND user_id = current_setting('app.iam_actor_user_id', true)::uuid
);
CREATE POLICY organization_membership_roles_select_refresh ON organization_membership_roles FOR SELECT USING (
    current_user = 'iam_runtime' AND current_setting('app.iam_operation_scope', true) = 'IAM_AUTH'
    AND current_setting('app.iam_operation_code', true) = 'SESSION_REFRESH'
    AND EXISTS (SELECT 1 FROM organization_memberships om WHERE om.id = organization_membership_roles.organization_membership_id
        AND om.user_id = current_setting('app.iam_actor_user_id', true)::uuid)
);
