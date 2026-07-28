#!/bin/sh
set -eu

command -v aws >/dev/null 2>&1 || { echo 'aws CLI bulunamadi.' >&2; exit 1; }
region=${AWS_REGION:-eu-central-1}
stack_name=${ALPHA_COGNITO_STACK_NAME:-kurs-platform-alpha-cognito-development}
caller_arn=$(aws sts get-caller-identity --query Arn --output text)
case "$caller_arn" in
  *:root) echo 'AWS root hesabi ile teardown yasaktir.' >&2; exit 1 ;;
esac

aws cloudformation delete-stack --region "$region" --stack-name "$stack_name"
aws cloudformation wait stack-delete-complete --region "$region" --stack-name "$stack_name"
printf '%s\n' "Cognito stack silindi: $stack_name ($region)"
