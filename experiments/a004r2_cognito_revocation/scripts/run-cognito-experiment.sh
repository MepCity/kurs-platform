#!/usr/bin/env bash
set -Eeuo pipefail

readonly REQUIRED_REGION="eu-central-1"
readonly REQUIRED_ROLE_NAME="kurs-platform-a004r2-experiment"
readonly BUDGET_NAME="kurs-platform-a004r1-monthly-cost"

for command_name in aws jq openssl; do
  command -v "$command_name" >/dev/null 2>&1 || {
    echo "FAIL: gerekli komut bulunamadı: $command_name" >&2
    exit 2
  }
done

: "${A004R2_USER_POOL_ID:?A004R2_USER_POOL_ID güvenli oturum değişkeni zorunludur}"
: "${A004R2_USERNAME:?A004R2_USERNAME sentetik kullanıcı değişkeni zorunludur}"

export AWS_PAGER=""
export AWS_REGION="${AWS_REGION:-$REQUIRED_REGION}"
[[ "$AWS_REGION" == "$REQUIRED_REGION" ]] || {
  echo "FAIL: deney yalnız $REQUIRED_REGION bölgesinde çalışır" >&2
  exit 2
}

client_id=""
client_name=""
password=""
user_mutated="false"
temporary_files=()

cleanup() {
  local original_status=$?
  local cleanup_status=0
  local cleanup_client_id="$client_id"
  trap - EXIT
  set +e

  if [[ "$user_mutated" == "true" ]]; then
    aws cognito-idp admin-enable-user \
      --user-pool-id "$A004R2_USER_POOL_ID" \
      --username "$A004R2_USERNAME" >/dev/null 2>&1 || cleanup_status=1
  fi
  if [[ -z "$cleanup_client_id" && -n "$client_name" ]]; then
    cleanup_client_id="$(aws cognito-idp list-user-pool-clients \
      --user-pool-id "$A004R2_USER_POOL_ID" --max-results 20 --output json 2>/dev/null |
      jq -r --arg NAME "$client_name" \
        '[.UserPoolClients[] | select(.ClientName == $NAME)][0].ClientId // empty')"
  fi
  if [[ -n "$cleanup_client_id" ]]; then
    aws cognito-idp delete-user-pool-client \
      --user-pool-id "$A004R2_USER_POOL_ID" \
      --client-id "$cleanup_client_id" >/dev/null 2>&1 || cleanup_status=1
  fi
  if [[ -n "$client_name" ]]; then
    remaining_helpers="$(aws cognito-idp list-user-pool-clients \
      --user-pool-id "$A004R2_USER_POOL_ID" --max-results 20 --output json 2>/dev/null |
      jq --arg NAME "$client_name" \
        '[.UserPoolClients[] | select(.ClientName == $NAME)] | length')"
    [[ "$remaining_helpers" == "0" ]] || cleanup_status=1
  fi
  if ((${#temporary_files[@]} > 0)); then
    rm -f -- "${temporary_files[@]}"
  fi
  unset password auth_json refresh_one refresh_two refresh_global refresh_disabled

  if ((cleanup_status != 0)); then
    echo "FAIL: provider cleanup tamamlanamadı; A-004R3 öncesi manuel envanter gerekir" >&2
    exit 1
  fi
  if ((original_status != 0)); then
    echo "FAIL: Cognito deneyi fail-closed durdu; geçici kaynaklar temizlendi" >&2
    exit "$original_status"
  fi
  echo "HELPER_CLEANUP=PASS"
  exit 0
}
trap cleanup EXIT

fail() {
  echo "FAIL: $1" >&2
  return 1
}

expect_rejection() {
  local label=$1
  shift
  local error_file
  error_file="$(mktemp)"
  temporary_files+=("$error_file")
  if "$@" >/dev/null 2>"$error_file"; then
    fail "$label beklenmedik biçimde başarılı oldu"
  fi
  local error_code
  error_code="$(sed -n 's/.*(\([^)]*\)).*/\1/p' "$error_file" | head -1)"
  case "$error_code" in
    NotAuthorizedException | RefreshTokenReuseException)
      echo "$label=PASS"
      ;;
    *)
      fail "$label güvenli ret kodu üretmedi: ${error_code:-UNCLASSIFIED}"
      ;;
  esac
}

caller_arn="$(aws sts get-caller-identity --query Arn --output text)"
case "$caller_arn" in
  arn:aws:sts::*:assumed-role/"$REQUIRED_ROLE_NAME"/*) echo "ROLE_GATE=PASS" ;;
  *) fail "root/IAM user/yanlış STS rolüyle deney çalıştırılamaz" ;;
esac
account_id="$(aws sts get-caller-identity --query Account --output text)"

pool_name="$(aws cognito-idp describe-user-pool \
  --user-pool-id "$A004R2_USER_POOL_ID" \
  --query 'UserPool.Name' --output text)"
[[ "$pool_name" == kurs-platform-a004r1-* ]] || fail "user pool A-004R1 deney kaynağı değil"

enabled_before="$(aws cognito-idp admin-get-user \
  --user-pool-id "$A004R2_USER_POOL_ID" \
  --username "$A004R2_USERNAME" \
  --query Enabled --output text)"
[[ "$enabled_before" == "True" ]] || fail "sentetik kullanıcı başlangıçta etkin değil"

budget_limit="$(aws budgets describe-budget \
  --account-id "$account_id" \
  --budget-name "$BUDGET_NAME" \
  --query 'Budget.BudgetLimit.Amount' --output text)"
[[ "$budget_limit" == "5.0" ]] || fail "5 USD bütçe kapısı doğrulanamadı"
echo "BUDGET_GATE=PASS"

password="A004r2!$(openssl rand -hex 18)zZ9!"
aws cognito-idp admin-set-user-password \
  --user-pool-id "$A004R2_USER_POOL_ID" \
  --username "$A004R2_USERNAME" \
  --password "$password" --permanent >/dev/null
user_mutated="true"

client_name="kurs-platform-a004r2-repro-$(date +%s)"
client_json="$(aws cognito-idp create-user-pool-client \
  --user-pool-id "$A004R2_USER_POOL_ID" \
  --client-name "$client_name" \
  --explicit-auth-flows ALLOW_ADMIN_USER_PASSWORD_AUTH \
  --refresh-token-validity 1 \
  --access-token-validity 10 \
  --id-token-validity 10 \
  --token-validity-units AccessToken=minutes,IdToken=minutes,RefreshToken=days \
  --enable-token-revocation \
  --refresh-token-rotation Feature=ENABLED,RetryGracePeriodSeconds=0 \
  --output json)"
client_id="$(jq -r '.UserPoolClient.ClientId' <<<"$client_json")"
rotation_feature="$(jq -r '.UserPoolClient.RefreshTokenRotation.Feature' <<<"$client_json")"
[[ "$rotation_feature" == "ENABLED" ]] || fail "rotation etkinleşmedi"
echo "ROTATION_CLIENT=PASS"

authenticate() {
  aws cognito-idp admin-initiate-auth \
    --user-pool-id "$A004R2_USER_POOL_ID" \
    --client-id "$client_id" \
    --auth-flow ADMIN_USER_PASSWORD_AUTH \
    --auth-parameters USERNAME="$A004R2_USERNAME",PASSWORD="$password" \
    --output json
}

auth_json="$(authenticate)"
refresh_one="$(jq -r '.AuthenticationResult.RefreshToken' <<<"$auth_json")"
[[ -n "$refresh_one" && "$refresh_one" != "null" ]] || fail "ilk refresh üretilmedi"
refresh_two="$(aws cognito-idp get-tokens-from-refresh-token \
  --client-id "$client_id" --refresh-token "$refresh_one" \
  --query 'AuthenticationResult.RefreshToken' --output text)"
[[ -n "$refresh_two" && "$refresh_two" != "$refresh_one" ]] || fail "refresh rotation başarısız"
echo "REFRESH_ROTATION=PASS"

sleep 2
expect_rejection OLD_REFRESH_REUSE \
  aws cognito-idp get-tokens-from-refresh-token \
  --client-id "$client_id" --refresh-token "$refresh_one"

auth_json="$(authenticate)"
refresh_global="$(jq -r '.AuthenticationResult.RefreshToken' <<<"$auth_json")"
aws cognito-idp admin-user-global-sign-out \
  --user-pool-id "$A004R2_USER_POOL_ID" \
  --username "$A004R2_USERNAME"
expect_rejection GLOBAL_SIGNOUT_REFRESH \
  aws cognito-idp get-tokens-from-refresh-token \
  --client-id "$client_id" --refresh-token "$refresh_global"

auth_json="$(authenticate)"
refresh_disabled="$(jq -r '.AuthenticationResult.RefreshToken' <<<"$auth_json")"
aws cognito-idp admin-disable-user \
  --user-pool-id "$A004R2_USER_POOL_ID" \
  --username "$A004R2_USERNAME"

enabled_snapshot="$(aws cognito-idp admin-get-user \
  --user-pool-id "$A004R2_USER_POOL_ID" \
  --username "$A004R2_USERNAME" \
  --query Enabled --output text)"
[[ "$enabled_snapshot" == "False" ]] || fail "kaçırılmış disable olayı uzlaştırılamadı"
echo "MISSED_EVENT_RECONCILIATION=PASS"

expect_rejection DISABLE_REFRESH \
  aws cognito-idp get-tokens-from-refresh-token \
  --client-id "$client_id" --refresh-token "$refresh_disabled"
expect_rejection DISABLED_NEW_LOGIN authenticate

aws cognito-idp admin-enable-user \
  --user-pool-id "$A004R2_USER_POOL_ID" \
  --username "$A004R2_USERNAME"
enabled_after="$(aws cognito-idp admin-get-user \
  --user-pool-id "$A004R2_USER_POOL_ID" \
  --username "$A004R2_USERNAME" \
  --query Enabled --output text)"
[[ "$enabled_after" == "True" ]] || fail "sentetik kullanıcı etkin duruma getirilemedi"

echo "USER_RESTORED=PASS"
echo "PROVIDER_SECRET_OUTPUT=NONE"
