#!/usr/bin/env bash
set -euo pipefail

repo_root=$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)
script="$repo_root/deploy/alpha/teardown_cognito.sh"
test_dir=$(mktemp -d)
trap 'rm -rf "$test_dir"' EXIT
mkdir -p "$test_dir/bin" "$test_dir/state"

cat >"$test_dir/bin/aws" <<'MOCK'
#!/usr/bin/env bash
set -euo pipefail
printf '%s\n' "$*" >>"$MOCK_AWS_LOG"
service=${1:-}
operation=${2:-}
args=$*

if [[ "$service $operation" == 'sts get-caller-identity' ]]; then
  if [[ "$args" == *'--query Account'* ]]; then
    [[ ${MOCK_SCENARIO:-ok} == wrong-account ]] && printf '000000000000\n' || printf '604561273748\n'
  else
    printf 'arn:aws:iam::604561273748:root\n'
  fi
  exit 0
fi

case "$service $operation" in
  'cloudformation list-stacks')
    if [[ ${MOCK_SCENARIO:-ok} == unexpected ]]; then
      printf 'kurs-platform-alpha-cognito-development\tkurs-platform-alpha-other\n'
    else
      printf 'kurs-platform-alpha-cognito-development\n'
    fi
    ;;
  'cloudformation list-stack-resources')
    if [[ ${MOCK_SCENARIO:-ok} == unexpected-stack-resource ]]; then
      printf 'NativeMobileClient\tRuntimePolicy\tRuntimeUser\tUserPool\tUserPoolDomain\tUnexpectedResource\n'
    else
      printf 'NativeMobileClient\tRuntimePolicy\tRuntimeUser\tUserPool\tUserPoolDomain\n'
    fi
    ;;
  'cloudformation describe-stacks')
    if [[ -f "$MOCK_STATE_DIR/stack-deleted" ]]; then
      echo 'ValidationError: Stack does not exist' >&2
      exit 254
    fi
    case "$args" in
      *"Key=='application'"*) [[ ${MOCK_SCENARIO:-ok} == bad-tag ]] && printf 'other\n' || printf 'kurs-platform\n' ;;
      *"Key=='environment'"*) printf 'development\n' ;;
      *"OutputKey=='UserPoolId'"*) printf 'eu-central-1_1GH5JivoG\n' ;;
      *"OutputKey=='AppClientId'"*) printf '2c59dh2nf60fmk6chn6qq3eoqu\n' ;;
      *) printf '{}\n' ;;
    esac
    ;;
  'cloudformation delete-stack') touch "$MOCK_STATE_DIR/stack-deleted" ;;
  'cloudformation wait') : ;;
  'cognito-idp list-user-pools') printf 'kurs-platform-alpha-development\n' ;;
  'cognito-idp list-tags-for-resource')
    [[ "$args" == *'UserPoolTags.application'* ]] && printf 'kurs-platform\n' || printf 'development\n'
    ;;
  'cognito-idp describe-user-pool-client') printf 'kurs-platform-alpha-mobile-development\n' ;;
  'cognito-idp describe-user-pool-domain') printf 'eu-central-1_1GH5JivoG\n' ;;
  'cognito-idp list-users')
    printf '%s\n' '{"Users":[{"Username":"synthetic-id","Attributes":[{"Name":"email","Value":"alpha-smoke@invalid.example"}]}]}'
    ;;
  'cognito-idp admin-delete-user') touch "$MOCK_STATE_DIR/user-deleted" ;;
  'cognito-idp describe-user-pool')
    if [[ -f "$MOCK_STATE_DIR/stack-deleted" ]]; then
      echo 'ResourceNotFoundException' >&2
      exit 254
    fi
    printf '{}\n'
    ;;
  'iam list-users') printf 'kurs-platform-alpha-cognito-development\n' ;;
  'iam list-user-tags')
    [[ "$args" == *"Key=='application'"* ]] && printf 'kurs-platform\n' || printf 'development\n'
    ;;
  'iam list-access-keys')
    [[ "$args" == *'AccessKeyId'* ]] && printf 'SYNTHETICKEYID\n' || printf 'Active\n'
    ;;
  'iam delete-access-key') touch "$MOCK_STATE_DIR/key-deleted" ;;
  'iam get-user')
    if [[ -f "$MOCK_STATE_DIR/stack-deleted" ]]; then
      echo 'NoSuchEntity' >&2
      exit 254
    fi
    printf '{}\n'
    ;;
  'budgets describe-budgets') printf 'kurs-platform-alpha-monthly-development\n' ;;
  'budgets list-tags-for-resource')
    [[ "$args" == *"Key=='application'"* ]] && printf 'kurs-platform\n' || printf 'development\n'
    ;;
  'budgets describe-budget')
    if [[ -f "$MOCK_STATE_DIR/budget-deleted" ]]; then
      echo 'NotFoundException' >&2
      exit 254
    fi
    case "$args" in
      *BudgetLimit.Amount*) printf '5\n' ;;
      *BudgetLimit.Unit*) printf 'USD\n' ;;
      *Budget.TimeUnit*) printf 'MONTHLY\n' ;;
      *) printf '{}\n' ;;
    esac
    ;;
  'budgets describe-notifications-for-budget') printf '1\n' ;;
  'budgets describe-subscribers-for-notification')
    printf 'arn:aws:sns:eu-central-1:604561273748:kurs-platform-alpha-budget-alerts-development\n'
    ;;
  'budgets delete-budget') touch "$MOCK_STATE_DIR/budget-deleted" ;;
  'sns list-topics') printf 'arn:aws:sns:eu-central-1:604561273748:kurs-platform-alpha-budget-alerts-development\n' ;;
  'sns list-tags-for-resource')
    [[ "$args" == *"Key=='application'"* ]] && printf 'kurs-platform\n' || printf 'development\n'
    ;;
  'sns list-subscriptions-by-topic')
    printf 'arn:aws:sns:eu-central-1:604561273748:kurs-platform-alpha-budget-alerts-development:synthetic-subscription\n'
    ;;
  'sns unsubscribe') touch "$MOCK_STATE_DIR/subscription-deleted" ;;
  'sns delete-topic') touch "$MOCK_STATE_DIR/topic-deleted" ;;
  'sns get-topic-attributes')
    if [[ -f "$MOCK_STATE_DIR/topic-deleted" ]]; then
      echo 'NotFound' >&2
      exit 254
    fi
    printf '{}\n'
    ;;
  *) echo "Beklenmeyen mock AWS çağrısı: $args" >&2; exit 90 ;;
esac
MOCK
chmod +x "$test_dir/bin/aws"

export PATH="$test_dir/bin:$PATH"
export MOCK_AWS_LOG="$test_dir/aws.log"
export MOCK_STATE_DIR="$test_dir/state"

if MOCK_SCENARIO=ok "$script" >"$test_dir/root-denied.out" 2>&1; then
  echo 'Root açık opt-in olmadan reddedilmeliydi.' >&2
  exit 1
fi
grep -F 'ALPHA_ALLOW_ROOT_TEARDOWN=true' "$test_dir/root-denied.out" >/dev/null

if MOCK_SCENARIO=wrong-account ALPHA_ALLOW_ROOT_TEARDOWN=true "$script" >"$test_dir/account.out" 2>&1; then
  echo 'Yanlış AWS hesabı fail-closed durmalıydı.' >&2
  exit 1
fi
grep -F 'AWS account kimliği' "$test_dir/account.out" >/dev/null

if MOCK_SCENARIO=ok AWS_REGION=us-east-1 ALPHA_ALLOW_ROOT_TEARDOWN=true "$script" >"$test_dir/region.out" 2>&1; then
  echo 'Yanlış AWS bölgesi fail-closed durmalıydı.' >&2
  exit 1
fi
grep -F 'yalnız sabit eu-central-1' "$test_dir/region.out" >/dev/null

if MOCK_SCENARIO=unexpected ALPHA_ALLOW_ROOT_TEARDOWN=true "$script" >"$test_dir/unexpected.out" 2>&1; then
  echo 'Beklenmeyen alpha kaynağı fail-closed durmalıydı.' >&2
  exit 1
fi
grep -F 'envanteri beklenen tek' "$test_dir/unexpected.out" >/dev/null

if MOCK_SCENARIO=unexpected-stack-resource ALPHA_ALLOW_ROOT_TEARDOWN=true "$script" >"$test_dir/stack-resource.out" 2>&1; then
  echo 'Beklenmeyen stack içi kaynak fail-closed durmalıydı.' >&2
  exit 1
fi
grep -F 'stack kaynak envanteri' "$test_dir/stack-resource.out" >/dev/null

if MOCK_SCENARIO=bad-tag ALPHA_ALLOW_ROOT_TEARDOWN=true "$script" >"$test_dir/tag.out" 2>&1; then
  echo 'Tag uyuşmazlığı fail-closed durmalıydı.' >&2
  exit 1
fi
grep -F 'tagı doğrulaması başarısız' "$test_dir/tag.out" >/dev/null

: >"$MOCK_AWS_LOG"
MOCK_SCENARIO=ok ALPHA_ALLOW_ROOT_TEARDOWN=true "$script" >"$test_dir/preflight.out"
grep -F 'ALPHA-002 teardown preflight PASS' "$test_dir/preflight.out" >/dev/null
grep -F 'DRY-RUN: hiçbir kaynak silinmedi' "$test_dir/preflight.out" >/dev/null
if grep -E '(^| )(delete-|admin-delete-user|unsubscribe)( |$)' "$MOCK_AWS_LOG"; then
  echo 'Preflight silme çağrısı yapmamalıdır.' >&2
  exit 1
fi

: >"$MOCK_AWS_LOG"
rm -f "$MOCK_STATE_DIR"/*
MOCK_SCENARIO=ok \
ALPHA_ALLOW_ROOT_TEARDOWN=true \
ALPHA_TEARDOWN_EXECUTE=true \
ALPHA_RENDER_SERVICE_DELETED=true \
  "$script" >"$test_dir/execute.out"
grep -F 'AWS teardown ve yokluk kontrolleri PASS' "$test_dir/execute.out" >/dev/null

assert_before() {
  first=$(grep -n -m1 "$1" "$MOCK_AWS_LOG" | cut -d: -f1)
  second=$(grep -n -m1 "$2" "$MOCK_AWS_LOG" | cut -d: -f1)
  [[ $first -lt $second ]] || { echo "Silme sırası bozuk: $1 -> $2" >&2; exit 1; }
}
assert_before 'iam delete-access-key' 'cognito-idp admin-delete-user'
assert_before 'cognito-idp admin-delete-user' 'cloudformation delete-stack'
assert_before 'cloudformation delete-stack' 'budgets delete-budget'
assert_before 'budgets delete-budget' 'sns unsubscribe'
assert_before 'sns unsubscribe' 'sns delete-topic'

echo 'ALPHA-002 teardown fail-closed preflight ve mock execute testleri geçti.'
