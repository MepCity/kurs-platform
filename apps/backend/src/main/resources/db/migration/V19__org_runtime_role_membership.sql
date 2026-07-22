-- ORG-004 production execution chain: application login stays NOINHERIT and must explicitly
-- SET LOCAL ROLE for the narrow ORG transaction. Membership alone does not expose org_runtime
-- table privileges while current_user remains iam_runtime.
GRANT org_runtime TO iam_runtime;
