#!/bin/sh
set -eu

: "${ALPHA_COGNITO_DOMAIN_PREFIX:?ALPHA_COGNITO_DOMAIN_PREFIX zorunlu}"
: "${ALPHA_TEST_USERNAME:?ALPHA_TEST_USERNAME zorunlu ve sentetik olmali}"
: "${ALPHA_TEST_PASSWORD:?ALPHA_TEST_PASSWORD guvenli ortamdan alinmali}"
case "$ALPHA_TEST_USERNAME" in
  *@*) : ;;
  *) echo 'ALPHA_TEST_USERNAME sentetik e-posta biciminde olmali (or. alpha@invalid.example).' >&2; exit 1 ;;
esac

command -v aws >/dev/null 2>&1 || { echo 'aws CLI bulunamadi.' >&2; exit 1; }

region=${AWS_REGION:-eu-central-1}
stack_name=${ALPHA_COGNITO_STACK_NAME:-kurs-platform-alpha-cognito-development}
script_dir=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
record_dir="$script_dir/.operation-record"
mkdir -p "$record_dir"
chmod 700 "$record_dir"
password_input=$(mktemp "$record_dir/password-input.XXXXXX")
trap ': >"$password_input"; rm -f "$password_input"' EXIT HUP INT TERM
chmod 600 "$password_input"

account_id=$(aws sts get-caller-identity --query Account --output text)
caller_arn=$(aws sts get-caller-identity --query Arn --output text)
case "$caller_arn" in
  *:root) echo 'AWS root hesabi ile provisioning yasaktir.' >&2; exit 1 ;;
esac

aws cloudformation deploy \
  --region "$region" \
  --stack-name "$stack_name" \
  --template-file "$script_dir/cognito.yaml" \
  --capabilities CAPABILITY_NAMED_IAM \
  --parameter-overrides EnvironmentName=development DomainPrefix="$ALPHA_COGNITO_DOMAIN_PREFIX" \
  --tags application=kurs-platform environment=development data-profile=synthetic-only

outputs=$(aws cloudformation describe-stacks --region "$region" --stack-name "$stack_name" \
  --query 'Stacks[0].Outputs' --output json)
pool_id=$(printf '%s' "$outputs" | python3 -c "import json,sys; print(next(x['OutputValue'] for x in json.load(sys.stdin) if x['OutputKey']=='UserPoolId'))")
issuer=$(printf '%s' "$outputs" | python3 -c "import json,sys; print(next(x['OutputValue'] for x in json.load(sys.stdin) if x['OutputKey']=='IssuerUrl'))")
client_id=$(printf '%s' "$outputs" | python3 -c "import json,sys; print(next(x['OutputValue'] for x in json.load(sys.stdin) if x['OutputKey']=='AppClientId'))")

if ! aws cognito-idp admin-get-user --region "$region" --user-pool-id "$pool_id" \
  --username "$ALPHA_TEST_USERNAME" >/dev/null 2>&1; then
  aws cognito-idp admin-create-user --region "$region" --user-pool-id "$pool_id" \
    --username "$ALPHA_TEST_USERNAME" --message-action SUPPRESS >/dev/null
fi
export ALPHA_PROVISION_POOL_ID=$pool_id
python3 -c 'import json,os,sys; json.dump({
    "UserPoolId": os.environ["ALPHA_PROVISION_POOL_ID"],
    "Username": os.environ["ALPHA_TEST_USERNAME"],
    "Password": os.environ["ALPHA_TEST_PASSWORD"],
    "Permanent": True
}, sys.stdout)' >"$password_input"
aws cognito-idp admin-set-user-password --region "$region" \
  --cli-input-json "file://$password_input" >/dev/null
: >"$password_input"
unset ALPHA_PROVISION_POOL_ID ALPHA_TEST_PASSWORD
subject=$(aws cognito-idp admin-get-user --region "$region" --user-pool-id "$pool_id" \
  --username "$ALPHA_TEST_USERNAME" --query "UserAttributes[?Name=='sub'].Value | [0]" --output text)
[ -n "$subject" ] && [ "$subject" != None ] || { echo 'Cognito sub alinamadi.' >&2; exit 1; }

umask 077
cat >"$record_dir/runtime-values.env" <<EOF
KURS_PLATFORM_ENVIRONMENT=development
AWS_REGION=$region
AWS_ACCOUNT_ID=$account_id
IAM_COGNITO_USER_POOL_ID=$pool_id
IAM_COGNITO_ISSUER=$issuer
IAM_COGNITO_CLIENT_ID=$client_id
OAUTH_REDIRECT_URL=kursplatform://oauth2redirect
OAUTH_LOGOUT_REDIRECT_URL=kursplatform://oauth2redirect
ALPHA_PROVIDER_SUBJECT=$subject
EOF

printf '%s\n' "Cognito hazirlandi. Secret olmayan runtime kaydi: $record_dir/runtime-values.env"
printf '%s\n' "Sonraki adim: migration sahibiyle bootstrap_platform_admin.sql dosyasini calistirin."
