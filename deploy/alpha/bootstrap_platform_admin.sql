\set ON_ERROR_STOP on

-- Yalnız sentetik kapalı alfa kullanıcısını platform yöneticisi olarak eşler.
-- :provider_subject gerçek Cognito sub değeridir ve bu dosyada sabitlenmez.
BEGIN;

INSERT INTO users (id, status)
VALUES ('10000000-0000-4000-8000-000000000001', 'ACTIVE')
ON CONFLICT (id) DO UPDATE SET status = 'ACTIVE', updated_at = transaction_timestamp();

INSERT INTO user_identities (id, user_id, issuer, subject)
VALUES (
    '10000000-0000-4000-8000-000000000002',
    '10000000-0000-4000-8000-000000000001',
    :'provider_issuer',
    :'provider_subject'
)
ON CONFLICT (issuer, subject) DO NOTHING;

SELECT count(*) AS identity_binding_count
FROM user_identities
WHERE issuer = :'provider_issuer'
  AND subject = :'provider_subject'
  AND user_id = '10000000-0000-4000-8000-000000000001'
\gset
\if :identity_binding_count != 1
  \echo 'Cognito identity baska bir platform kullanicisina bagli'
  \quit 3
\endif

INSERT INTO platform_administrator_profiles (id, user_id, first_name, last_name)
VALUES (
    '10000000-0000-4000-8000-000000000003',
    '10000000-0000-4000-8000-000000000001',
    'Sentetik',
    'Alfa Yöneticisi'
)
ON CONFLICT (user_id) DO UPDATE
SET first_name = EXCLUDED.first_name,
    last_name = EXCLUDED.last_name,
    updated_at = transaction_timestamp();

INSERT INTO platform_administrators (id, user_id, granted_at)
VALUES (
    '10000000-0000-4000-8000-000000000004',
    '10000000-0000-4000-8000-000000000001',
    transaction_timestamp()
)
ON CONFLICT (user_id) DO UPDATE SET revoked_at = NULL;

COMMIT;
