-- IAM-006: the SECURITY DEFINER authorization helper is usable only from its exact RLS operation.
-- GUC guards are inside the helper so a direct invocation cannot become an authorization oracle.
CREATE OR REPLACE FUNCTION iam_device_session_revoke_authorized(p_actor UUID, p_organization UUID)
RETURNS BOOLEAN LANGUAGE sql STABLE SECURITY DEFINER SET search_path = pg_catalog, public AS $$
 SELECT CASE
   WHEN session_user <> 'iam_runtime'
     OR current_setting('app.iam_operation_scope', true) <> 'ORGANIZATION'
     OR current_setting('app.iam_operation_code', true) <> 'DEVICE_SESSION_REVOKE'
     OR current_setting('app.iam_actor_user_id', true) <> p_actor::text
     OR current_setting('app.iam_target_organization_id', true) <> p_organization::text
   THEN false
   ELSE EXISTS (
     SELECT 1 FROM public.organization_memberships m
     JOIN public.organization_membership_roles r ON r.organization_membership_id=m.id AND r.revoked_at IS NULL
     WHERE m.user_id=p_actor AND m.organization_id=p_organization AND m.status='ACTIVE' AND r.role='ORG_ADMIN'
   ) OR EXISTS (
     SELECT 1 FROM public.organization_memberships m
     JOIN public.organization_membership_roles r ON r.organization_membership_id=m.id AND r.revoked_at IS NULL AND r.role='TEACHER'
     JOIN public.organization_membership_permissions p ON p.target_membership_role_id=r.id AND p.revoked_at IS NULL AND p.permission_code='DEVICE_SESSION_REVOKE'
     WHERE m.user_id=p_actor AND m.organization_id=p_organization AND m.status='ACTIVE'
   )
 END;
$$;
REVOKE ALL ON FUNCTION iam_device_session_revoke_authorized(UUID, UUID) FROM PUBLIC;
REVOKE ALL ON FUNCTION iam_device_session_revoke_authorized(UUID, UUID) FROM app_runtime;
GRANT EXECUTE ON FUNCTION iam_device_session_revoke_authorized(UUID, UUID) TO iam_runtime;
