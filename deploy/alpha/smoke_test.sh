#!/bin/sh
set -eu

: "${ALPHA_API_BASE_URL:?ALPHA_API_BASE_URL zorunlu}"
: "${ALPHA_COGNITO_ACCESS_TOKEN:?Gercek PKCE akisindan alinan access token zorunlu}"

command -v curl >/dev/null 2>&1 || { echo 'curl bulunamadi.' >&2; exit 1; }
command -v python3 >/dev/null 2>&1 || { echo 'python3 bulunamadi.' >&2; exit 1; }

tmp_dir=$(mktemp -d)
trap 'find "$tmp_dir" -type f -exec sh -c '\''for f do : >"$f"; done'\'' sh {} +; rm -rf "$tmp_dir"' EXIT HUP INT TERM
chmod 700 "$tmp_dir"

uuid() {
  python3 -c 'import uuid; print(uuid.uuid4())'
}

json_value() {
  key=$1
  python3 -c "import json,sys; value=json.load(sys.stdin); print($key)"
}

request() {
  method=$1
  path=$2
  config_file=$3
  body_file=$4
  output_file=$5
  if [ -n "$body_file" ]; then
    status=$(curl --silent --show-error --output "$output_file" --write-out '%{http_code}' \
      --request "$method" --config "$config_file" --data-binary "@$body_file" \
      "$ALPHA_API_BASE_URL$path")
  else
    status=$(curl --silent --show-error --output "$output_file" --write-out '%{http_code}' \
      --request "$method" --config "$config_file" "$ALPHA_API_BASE_URL$path")
  fi
  printf '%s' "$status"
}

health_status=$(curl --silent --show-error --output "$tmp_dir/health.json" --write-out '%{http_code}' \
  "$ALPHA_API_BASE_URL/health")
[ "$health_status" = 200 ] || { echo "Health basarisiz: HTTP $health_status" >&2; exit 1; }
[ "$(json_value "value['status']" <"$tmp_dir/health.json")" = UP ] || exit 1

printf 'header = "Authorization: Bearer %s"\nheader = "Idempotency-Key: %s"\nheader = "Content-Type: application/json"\n' \
  "$ALPHA_COGNITO_ACCESS_TOKEN" "$(uuid)" >"$tmp_dir/exchange.curl"
chmod 600 "$tmp_dir/exchange.curl"
printf '{"deviceIdentifier":"%s","platform":"IOS","deviceName":"Sentetik Kapali Alfa"}' "$(uuid)" \
  >"$tmp_dir/exchange-body.json"
exchange_status=$(request POST /api/v1/iam/auth/provider-token-exchange \
  "$tmp_dir/exchange.curl" "$tmp_dir/exchange-body.json" "$tmp_dir/exchange.json")
[ "$exchange_status" = 200 ] || { echo "Provider exchange basarisiz: HTTP $exchange_status" >&2; exit 1; }
context_token=$(json_value "value['contextSelectionToken']" <"$tmp_dir/exchange.json")

printf 'header = "Authorization: Bearer %s"\nheader = "Idempotency-Key: %s"\n' \
  "$context_token" "$(uuid)" >"$tmp_dir/activate.curl"
chmod 600 "$tmp_dir/activate.curl"
activate_status=$(request POST /api/v1/iam/auth/platform-admin/activate \
  "$tmp_dir/activate.curl" '' "$tmp_dir/activate.json")
[ "$activate_status" = 200 ] || { echo "Platform aktivasyonu basarisiz: HTTP $activate_status" >&2; exit 1; }
access_token=$(json_value "value['session']['accessToken']" <"$tmp_dir/activate.json")
refresh_token=$(json_value "value['session']['refreshToken']" <"$tmp_dir/activate.json")

printf 'header = "Authorization: Bearer %s"\n' "$access_token" >"$tmp_dir/access.curl"
chmod 600 "$tmp_dir/access.curl"
me_status=$(request GET /api/v1/iam/sessions/me "$tmp_dir/access.curl" '' "$tmp_dir/me.json")
[ "$me_status" = 200 ] || { echo "sessions/me basarisiz: HTTP $me_status" >&2; exit 1; }

list_request_id=$(uuid)
printf 'header = "Authorization: Bearer %s"\nheader = "X-Request-Id: %s"\n' \
  "$access_token" "$list_request_id" >"$tmp_dir/list.curl"
chmod 600 "$tmp_dir/list.curl"
list_status=$(request GET /api/v1/organizations "$tmp_dir/list.curl" '' "$tmp_dir/list.json")
[ "$list_status" = 200 ] || {
  echo "Kurum listeleme basarisiz: HTTP $list_status; requestId=$list_request_id" >&2
  exit 1
}
[ "$(json_value "isinstance(value.get('items'), list) and isinstance(value.get('page'), dict) and isinstance(value['page'].get('hasNextPage'), bool)" <"$tmp_dir/list.json")" = True ] || {
  echo "Kurum listeleme cevabi sozlesmeye uymuyor; requestId=$list_request_id" >&2
  exit 1
}

printf 'header = "Idempotency-Key: %s"\nheader = "Content-Type: application/json"\n' "$(uuid)" \
  >"$tmp_dir/refresh.curl"
printf '{"refreshToken":"%s"}' "$refresh_token" >"$tmp_dir/refresh-body.json"
refresh_status=$(request POST /api/v1/iam/sessions/refresh \
  "$tmp_dir/refresh.curl" "$tmp_dir/refresh-body.json" "$tmp_dir/refresh.json")
[ "$refresh_status" = 200 ] || { echo "Refresh basarisiz: HTTP $refresh_status" >&2; exit 1; }
new_access_token=$(json_value "value['session']['accessToken']" <"$tmp_dir/refresh.json")
new_refresh_token=$(json_value "value['session']['refreshToken']" <"$tmp_dir/refresh.json")

printf 'header = "Authorization: Bearer %s"\nheader = "Idempotency-Key: %s"\nheader = "Content-Type: application/json"\n' \
  "$new_access_token" "$(uuid)" >"$tmp_dir/logout.curl"
printf '{"refreshToken":"%s"}' "$new_refresh_token" >"$tmp_dir/logout-body.json"
logout_status=$(request POST /api/v1/iam/sessions/logout \
  "$tmp_dir/logout.curl" "$tmp_dir/logout-body.json" "$tmp_dir/logout.json")
[ "$logout_status" = 204 ] || { echo "Logout basarisiz: HTTP $logout_status" >&2; exit 1; }

printf 'header = "Authorization: Bearer broken.synthetic.token"\nheader = "Idempotency-Key: %s"\nheader = "Content-Type: application/json"\n' \
  "$(uuid)" >"$tmp_dir/negative.curl"
negative_status=$(request POST /api/v1/iam/auth/provider-token-exchange \
  "$tmp_dir/negative.curl" "$tmp_dir/exchange-body.json" "$tmp_dir/negative.json")
case "$negative_status" in
  401|403) : ;;
  *) echo "Bozuk token fail-closed reddedilmedi: HTTP $negative_status" >&2; exit 1 ;;
esac

printf '%s\n' \
  "PASS: health, provider exchange, platform activation, sessions/me, kurum listeleme, refresh, logout, bozuk token. organizationRequestId=$list_request_id"
