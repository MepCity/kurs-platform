#!/bin/sh
set -eu

command -v aws >/dev/null 2>&1 || { echo 'aws CLI bulunamadi.' >&2; exit 1; }
command -v python3 >/dev/null 2>&1 || { echo 'python3 bulunamadi.' >&2; exit 1; }

region=eu-central-1
expected_account_id=604561273748
stack_name=kurs-platform-alpha-cognito-development
pool_name=kurs-platform-alpha-development
client_name=kurs-platform-alpha-mobile-development
domain_name=kurs-platform-alpha-development-604561273748
runtime_user=kurs-platform-alpha-cognito-development
synthetic_email=alpha-smoke@invalid.example
budget_name=kurs-platform-alpha-monthly-development
topic_name=kurs-platform-alpha-budget-alerts-development
topic_arn="arn:aws:sns:${region}:${expected_account_id}:${topic_name}"
budget_arn="arn:aws:budgets::${expected_account_id}:budget/${budget_name}"
execute=${ALPHA_TEARDOWN_EXECUTE:-false}

case "$execute" in
  true|false) : ;;
  *) echo 'ALPHA_TEARDOWN_EXECUTE yalnız true veya false olabilir.' >&2; exit 1 ;;
esac
if [ "${AWS_REGION:-$region}" != "$region" ] || [ "${AWS_DEFAULT_REGION:-$region}" != "$region" ]; then
  echo "Teardown yalnız sabit ${region} bölgesinde çalışır." >&2
  exit 1
fi

account_id=$(aws sts get-caller-identity --query Account --output text)
caller_arn=$(aws sts get-caller-identity --query Arn --output text)
[ "$account_id" = "$expected_account_id" ] || {
  echo 'AWS account kimliği beklenen ALPHA-002 hesabıyla eşleşmiyor.' >&2
  exit 1
}
case "$caller_arn" in
  *:root)
    [ "${ALPHA_ALLOW_ROOT_TEARDOWN:-false}" = true ] || {
      echo 'Root teardown varsayılan olarak kapalıdır; ALPHA_ALLOW_ROOT_TEARDOWN=true gerekir.' >&2
      exit 1
    }
    ;;
esac

normalize_list() {
  printf '%s\n' "$1" | tr '\t ' '\n\n' | sed '/^None$/d; /^$/d' | sort -u
}

require_only() {
  label=$1
  actual=$(normalize_list "$2")
  expected=$3
  [ "$actual" = "$expected" ] || {
    echo "$label envanteri beklenen tek ALPHA-002 kaynağıyla eşleşmiyor." >&2
    exit 1
  }
}

require_value() {
  label=$1
  actual=$2
  expected=$3
  [ "$actual" = "$expected" ] || {
    echo "$label doğrulaması başarısız." >&2
    exit 1
  }
}

stack_matches=$(aws cloudformation list-stacks --region "$region" \
  --query "StackSummaries[?starts_with(StackName, 'kurs-platform-alpha-') && StackStatus!='DELETE_COMPLETE'].StackName" \
  --output text)
require_only 'CloudFormation stack' "$stack_matches" "$stack_name"
stack_resources=$(aws cloudformation list-stack-resources --region "$region" --stack-name "$stack_name" \
  --query 'StackResourceSummaries[].LogicalResourceId' --output text)
expected_stack_resources=$(printf '%s\n' NativeMobileClient RuntimePolicy RuntimeUser UserPool UserPoolDomain | sort)
require_only 'CloudFormation stack kaynak' "$stack_resources" "$expected_stack_resources"

pool_matches=$(aws cognito-idp list-user-pools --region "$region" --max-results 60 \
  --query "UserPools[?starts_with(Name, 'kurs-platform-alpha-')].Name" --output text)
require_only 'Cognito user pool' "$pool_matches" "$pool_name"

iam_matches=$(aws iam list-users \
  --query "Users[?starts_with(UserName, 'kurs-platform-alpha-')].UserName" --output text)
require_only 'IAM user' "$iam_matches" "$runtime_user"

budget_matches=$(aws budgets describe-budgets --account-id "$account_id" \
  --query "Budgets[?starts_with(BudgetName, 'kurs-platform-alpha-')].BudgetName" --output text)
require_only 'AWS budget' "$budget_matches" "$budget_name"

topic_matches=$(aws sns list-topics --region "$region" \
  --query "Topics[?starts_with(TopicArn, 'arn:aws:sns:${region}:${account_id}:kurs-platform-alpha-')].TopicArn" \
  --output text)
require_only 'SNS topic' "$topic_matches" "$topic_arn"

stack_application=$(aws cloudformation describe-stacks --region "$region" --stack-name "$stack_name" \
  --query "Stacks[0].Tags[?Key=='application'].Value | [0]" --output text)
stack_environment=$(aws cloudformation describe-stacks --region "$region" --stack-name "$stack_name" \
  --query "Stacks[0].Tags[?Key=='environment'].Value | [0]" --output text)
require_value 'Stack application tagı' "$stack_application" kurs-platform
require_value 'Stack environment tagı' "$stack_environment" development

pool_id=$(aws cloudformation describe-stacks --region "$region" --stack-name "$stack_name" \
  --query "Stacks[0].Outputs[?OutputKey=='UserPoolId'].OutputValue | [0]" --output text)
client_id=$(aws cloudformation describe-stacks --region "$region" --stack-name "$stack_name" \
  --query "Stacks[0].Outputs[?OutputKey=='AppClientId'].OutputValue | [0]" --output text)
[ -n "$pool_id" ] && [ "$pool_id" != None ] || { echo 'User pool ID alınamadı.' >&2; exit 1; }
[ -n "$client_id" ] && [ "$client_id" != None ] || { echo 'App client ID alınamadı.' >&2; exit 1; }

pool_application=$(aws cognito-idp list-tags-for-resource --region "$region" \
  --resource-arn "arn:aws:cognito-idp:${region}:${account_id}:userpool/${pool_id}" \
  --query "UserPoolTags.application" --output text)
pool_environment=$(aws cognito-idp list-tags-for-resource --region "$region" \
  --resource-arn "arn:aws:cognito-idp:${region}:${account_id}:userpool/${pool_id}" \
  --query "UserPoolTags.environment" --output text)
require_value 'User pool application tagı' "$pool_application" kurs-platform
require_value 'User pool environment tagı' "$pool_environment" development

client_actual=$(aws cognito-idp describe-user-pool-client --region "$region" \
  --user-pool-id "$pool_id" --client-id "$client_id" --query UserPoolClient.ClientName --output text)
domain_pool_id=$(aws cognito-idp describe-user-pool-domain --region "$region" --domain "$domain_name" \
  --query DomainDescription.UserPoolId --output text)
require_value 'Cognito client adı' "$client_actual" "$client_name"
require_value 'Cognito domain pool bağı' "$domain_pool_id" "$pool_id"

users_json=$(aws cognito-idp list-users --region "$region" --user-pool-id "$pool_id" --output json)
printf '%s' "$users_json" | python3 -c '
import json, sys
expected = sys.argv[1]
users = json.load(sys.stdin).get("Users", [])
emails = [next((a.get("Value") for a in u.get("Attributes", []) if a.get("Name") == "email"), None) for u in users]
if len(users) != 1 or emails != [expected]:
    raise SystemExit("Cognito kullanıcı envanteri beklenen tek sentetik kullanıcıyla eşleşmiyor.")
' "$synthetic_email"

user_application=$(aws iam list-user-tags --user-name "$runtime_user" \
  --query "Tags[?Key=='application'].Value | [0]" --output text)
user_environment=$(aws iam list-user-tags --user-name "$runtime_user" \
  --query "Tags[?Key=='environment'].Value | [0]" --output text)
require_value 'IAM user application tagı' "$user_application" kurs-platform
require_value 'IAM user environment tagı' "$user_environment" development

key_statuses=$(aws iam list-access-keys --user-name "$runtime_user" \
  --query 'AccessKeyMetadata[].Status' --output text)
key_count=$(normalize_list "$key_statuses" | awk 'END { print NR }')
case "$key_count" in
  0|1) : ;;
  *) echo 'Runtime IAM kullanıcısında beklenmeyen sayıda access key var.' >&2; exit 1 ;;
esac

budget_application=$(aws budgets list-tags-for-resource --resource-arn "$budget_arn" \
  --query "ResourceTags[?Key=='application'].Value | [0]" --output text)
budget_environment=$(aws budgets list-tags-for-resource --resource-arn "$budget_arn" \
  --query "ResourceTags[?Key=='environment'].Value | [0]" --output text)
require_value 'Budget application tagı' "$budget_application" kurs-platform
require_value 'Budget environment tagı' "$budget_environment" development
require_value 'Budget tutarı' "$(aws budgets describe-budget --account-id "$account_id" --budget-name "$budget_name" --query Budget.BudgetLimit.Amount --output text)" 5
require_value 'Budget para birimi' "$(aws budgets describe-budget --account-id "$account_id" --budget-name "$budget_name" --query Budget.BudgetLimit.Unit --output text)" USD
require_value 'Budget periyodu' "$(aws budgets describe-budget --account-id "$account_id" --budget-name "$budget_name" --query Budget.TimeUnit --output text)" MONTHLY
notification_count=$(aws budgets describe-notifications-for-budget --account-id "$account_id" \
  --budget-name "$budget_name" --query 'length(Notifications)' --output text)
require_value 'Budget alarm sayısı' "$notification_count" 1
subscriber_address=$(aws budgets describe-subscribers-for-notification --account-id "$account_id" \
  --budget-name "$budget_name" \
  --notification NotificationType=ACTUAL,ComparisonOperator=GREATER_THAN,Threshold=80,ThresholdType=PERCENTAGE \
  --query 'Subscribers[0].Address' --output text)
require_value 'Budget SNS abonesi' "$subscriber_address" "$topic_arn"

topic_application=$(aws sns list-tags-for-resource --region "$region" --resource-arn "$topic_arn" \
  --query "Tags[?Key=='application'].Value | [0]" --output text)
topic_environment=$(aws sns list-tags-for-resource --region "$region" --resource-arn "$topic_arn" \
  --query "Tags[?Key=='environment'].Value | [0]" --output text)
require_value 'SNS application tagı' "$topic_application" kurs-platform
require_value 'SNS environment tagı' "$topic_environment" development
subscription_arns=$(aws sns list-subscriptions-by-topic --region "$region" --topic-arn "$topic_arn" \
  --query 'Subscriptions[].SubscriptionArn' --output text)
subscription_count=$(normalize_list "$subscription_arns" | awk 'END { print NR }')

printf '%s\n' 'ALPHA-002 teardown preflight PASS'
printf '%s\n' "  stack=$stack_name"
printf '%s\n' "  user-pool=$pool_name; client=$client_name; domain=$domain_name; synthetic-users=1"
printf '%s\n' "  runtime-iam-user=$runtime_user; access-key-count=$key_count"
printf '%s\n' "  budget=$budget_name; limit=5 USD; alarm=ACTUAL>80%"
printf '%s\n' "  sns-topic=$topic_name; subscriptions=$subscription_count"

if [ "$execute" != true ]; then
  printf '%s\n' 'DRY-RUN: hiçbir kaynak silinmedi. Silme için ayrıca ALPHA_TEARDOWN_EXECUTE=true gerekir.'
  exit 0
fi

[ "${ALPHA_RENDER_SERVICE_DELETED:-false}" = true ] || {
  echo 'Önce Render servisi silinmeli ve ALPHA_RENDER_SERVICE_DELETED=true ile doğrulanmalıdır.' >&2
  exit 1
}

key_ids=$(aws iam list-access-keys --user-name "$runtime_user" \
  --query 'AccessKeyMetadata[].AccessKeyId' --output text)
for key_id in $key_ids; do
  aws iam delete-access-key --user-name "$runtime_user" --access-key-id "$key_id"
done

aws cognito-idp admin-delete-user --region "$region" --user-pool-id "$pool_id" \
  --username "$synthetic_email"
aws cloudformation delete-stack --region "$region" --stack-name "$stack_name"
aws cloudformation wait stack-delete-complete --region "$region" --stack-name "$stack_name"
aws budgets delete-budget --account-id "$account_id" --budget-name "$budget_name"

for subscription_arn in $subscription_arns; do
  case "$subscription_arn" in
    PendingConfirmation|Deleted) : ;;
    *) aws sns unsubscribe --region "$region" --subscription-arn "$subscription_arn" ;;
  esac
done
aws sns delete-topic --region "$region" --topic-arn "$topic_arn"

assert_absent() {
  label=$1
  expected_error=$2
  shift 2
  error_file=$(mktemp "${TMPDIR:-/tmp}/alpha-teardown-error.XXXXXX")
  if "$@" > /dev/null 2>"$error_file"; then
    rm -f "$error_file"
    echo "$label hâlâ mevcut." >&2
    exit 1
  fi
  if ! grep -F "$expected_error" "$error_file" >/dev/null; then
    cat "$error_file" >&2
    rm -f "$error_file"
    echo "$label yokluk kontrolü beklenen hata kodunu vermedi." >&2
    exit 1
  fi
  rm -f "$error_file"
}

assert_absent 'CloudFormation stack' 'ValidationError' \
  aws cloudformation describe-stacks --region "$region" --stack-name "$stack_name"
assert_absent 'Runtime IAM user' 'NoSuchEntity' \
  aws iam get-user --user-name "$runtime_user"
assert_absent 'Cognito user pool' 'ResourceNotFoundException' \
  aws cognito-idp describe-user-pool --region "$region" --user-pool-id "$pool_id"
assert_absent 'AWS budget' 'NotFoundException' \
  aws budgets describe-budget --account-id "$account_id" --budget-name "$budget_name"
assert_absent 'SNS topic' 'NotFound' \
  aws sns get-topic-attributes --region "$region" --topic-arn "$topic_arn"

printf '%s\n' 'ALPHA-002 AWS teardown ve yokluk kontrolleri PASS.'
printf '%s\n' 'Supabase proje silme ve Render servis/Blueprint yokluk doğrulamasını Dashboard üzerinden tamamlayın.'
