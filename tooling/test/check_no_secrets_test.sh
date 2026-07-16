#!/bin/sh

set -eu

tooling_root=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
check_script="$tooling_root/check_no_secrets.sh"
fixture=$(mktemp -d "${TMPDIR:-/tmp}/kurs-platform-secret-test.XXXXXX")
trap 'rm -rf "$fixture"' EXIT HUP INT TERM

expect_rejected() {
  label=$1
  if REPO_ROOT="$fixture" "$check_script" >/dev/null 2>&1
  then
    echo "Reddedilmesi gereken secret senaryosu geçti: $label" >&2
    exit 1
  fi
}

mkdir -p "$fixture"
printf '%s\n' \
  'KURS_PLATFORM_DATABASE_URL_SECRET_REF=development/platform/database-url' \
  'KURS_PLATFORM_COGNITO_CLIENT_ID=examplepublicclientid' >"$fixture/.env.example"
REPO_ROOT="$fixture" "$check_script" >/dev/null

printf '%s\n' 'DATABASE_URL=postgres://real.example.invalid/secret' >"$fixture/.env"
expect_rejected 'local env file'
rm "$fixture/.env"

database_url_key=DATABASE_URL
database_url_value_prefix='postgres://'
database_url_value_body='app_user:cleartext-password@db.example.invalid:5432/kurs'
printf '%s\n' "${database_url_key}=${database_url_value_prefix}${database_url_value_body}" >"$fixture/database.txt"
expect_rejected 'raw PostgreSQL database URL'
rm "$fixture/database.txt"

jdbc_url_key=JDBC_URL
jdbc_url_value_prefix='jdbc:postgresql://'
jdbc_url_value_body='app_user:cleartext-password@db.example.invalid:5432/kurs'
printf '%s\n' "${jdbc_url_key}=${jdbc_url_value_prefix}${jdbc_url_value_body}" >"$fixture/jdbc.txt"
expect_rejected 'raw JDBC PostgreSQL URL'
rm "$fixture/jdbc.txt"

spring_url_key=SPRING_DATASOURCE_URL
printf '%s\n' "${spring_url_key}=${jdbc_url_value_prefix}${jdbc_url_value_body}" >"$fixture/spring.txt"
expect_rejected 'raw Spring datasource PostgreSQL URL'
rm "$fixture/spring.txt"

aws_key_prefix=AKIA
aws_key_suffix=1234567890ABCDEF
printf '%s\n' "aws=${aws_key_prefix}${aws_key_suffix} # example" >"$fixture/config.txt"
expect_rejected 'AWS access key'
rm "$fixture/config.txt"

private_key_begin='-----BEGIN '
private_key_kind='PRIVATE KEY-----'
printf '%s\n' "${private_key_begin}${private_key_kind}" >"$fixture/private.pem"
expect_rejected 'private key file'
rm "$fixture/private.pem"

api_key_name=API_KEY
api_key_value=abcdef1234567890abcdef1234567890
printf '%s\n' "${api_key_name}=${api_key_value}" >"$fixture/settings.txt"
expect_rejected 'raw api key assignment'
rm "$fixture/settings.txt"

jwt_header=eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9
jwt_payload=aaaaaaaaaaaaaaaaaaaaaaaa
jwt_signature=bbbbbbbbbbbbbbbbbbbb
printf '%s\n' "TOKEN=${jwt_header}.${jwt_payload}.${jwt_signature}" >"$fixture/jwt.txt"
expect_rejected 'JWT-like token'
rm "$fixture/jwt.txt"

printf '%s\n' 'KURS_PLATFORM_DATABASE_URL_SECRET_REF=postgres://real.example.invalid/secret' >"$fixture/ref.txt"
expect_rejected 'raw-valued secret reference'
rm "$fixture/ref.txt"

printf '%s\n' '# KURS_PLATFORM_IAM_TOKEN_PEPPER_SECRET_REF=placeholder-value' >"$fixture/commented.txt"
expect_rejected 'placeholder commented secret reference'
rm "$fixture/commented.txt"

mkdir -p "$fixture/tooling/test"
test_secret_name=ACCESS_TOKEN
test_secret_value=abcdef1234567890abcdef1234567890
printf '%s\n' "${test_secret_name}=${test_secret_value}" >"$fixture/tooling/test/leaked_test_fixture.sh"
expect_rejected 'secret inside test file'
rm -rf "$fixture/tooling"

mkdir -p "$fixture/dir with spaces"
printf '%s\n' "${api_key_name}=${api_key_value}" >"$fixture/dir with spaces/leaked secret.txt"
expect_rejected 'secret in filename with spaces'
rm -rf "$fixture/dir with spaces"

tab_char=$(printf '\t')
tabbed_file="$fixture/tab${tab_char}name.txt"
printf '%s\n' "${database_url_key}=${database_url_value_prefix}${database_url_value_body}" >"$tabbed_file"
expect_rejected 'raw database URL in filename with tab'
rm "$tabbed_file"

printf '%s\n' \
  'KURS_PLATFORM_IAM_TOKEN_PEPPER_SECRET_REF=development/platform/iam-token-pepper' >"$fixture/README.md"
REPO_ROOT="$fixture" "$check_script" >/dev/null

echo 'Secret taraması regresyon testleri geçti.'
