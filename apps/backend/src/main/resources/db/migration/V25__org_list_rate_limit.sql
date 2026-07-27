-- ORG-009A: persistent read quota.  The table is deliberately actor-scoped and carries no
-- tenant data; the surrounding ORG_LIST transaction already binds the actor and operation code.
CREATE TABLE organization_list_rate_limits (
    actor_user_id UUID NOT NULL REFERENCES users(id),
    window_started_at TIMESTAMPTZ NOT NULL,
    request_count INTEGER NOT NULL CHECK (request_count >= 1),
    PRIMARY KEY (actor_user_id, window_started_at)
);

CREATE INDEX organization_list_rate_limits_expiry_idx ON organization_list_rate_limits (window_started_at);
GRANT SELECT, INSERT, UPDATE ON organization_list_rate_limits TO org_runtime;
ALTER TABLE organization_list_rate_limits ENABLE ROW LEVEL SECURITY;
ALTER TABLE organization_list_rate_limits FORCE ROW LEVEL SECURITY;

CREATE POLICY organization_list_rate_limits_org_runtime ON organization_list_rate_limits FOR ALL TO org_runtime
USING (
    current_user = 'org_runtime'
    AND current_setting('app.iam_operation_code', true) = 'ORG_LIST'
    AND current_setting('app.iam_operation_scope', true) IN ('GLOBAL', 'ORGANIZATION')
    AND actor_user_id = current_setting('app.iam_actor_user_id', true)::uuid
)
WITH CHECK (
    current_user = 'org_runtime'
    AND current_setting('app.iam_operation_code', true) = 'ORG_LIST'
    AND current_setting('app.iam_operation_scope', true) IN ('GLOBAL', 'ORGANIZATION')
    AND actor_user_id = current_setting('app.iam_actor_user_id', true)::uuid
);

-- An ORGANIZATION-scoped access token is rechecked against live membership state inside the
-- ORG_LIST transaction. This closes the window between token issuance and membership/role
-- revocation and prevents a forged actor/organization GUC pair from opening a tenant row.
CREATE POLICY organization_memberships_select_org_list
    ON organization_memberships FOR SELECT TO org_runtime
    USING (
        current_user = 'org_runtime'
        AND current_setting('app.iam_operation_scope', true) = 'ORGANIZATION'
        AND current_setting('app.iam_operation_code', true) = 'ORG_LIST'
        AND organization_id = current_setting('app.organization_id', true)::uuid
        AND user_id = current_setting('app.iam_actor_user_id', true)::uuid
        AND status = 'ACTIVE'
    );

CREATE POLICY organization_membership_roles_select_org_list
    ON organization_membership_roles FOR SELECT TO org_runtime
    USING (
        current_user = 'org_runtime'
        AND current_setting('app.iam_operation_scope', true) = 'ORGANIZATION'
        AND current_setting('app.iam_operation_code', true) = 'ORG_LIST'
        AND organization_id = current_setting('app.organization_id', true)::uuid
        AND revoked_at IS NULL
        AND EXISTS (
            SELECT 1
            FROM organization_memberships membership
            WHERE membership.id = organization_membership_roles.organization_membership_id
              AND membership.organization_id = organization_membership_roles.organization_id
              AND membership.user_id = current_setting('app.iam_actor_user_id', true)::uuid
              AND membership.status = 'ACTIVE'
        )
    );
