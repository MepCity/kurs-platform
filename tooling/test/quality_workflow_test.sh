#!/usr/bin/env bash

set -euo pipefail

repo_root=$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)
workflow="$repo_root/.github/workflows/quality-gates.yml"
expected_gates=$(cat <<'EOF'
backend-gate|Backend kalite kapısı
dependency-gate|Bağımlılık güvenliği
mobile-gate|Mobil kalite kapısı
repo-gate|Repo kalite kapısı
EOF
)

# Workflow sözleşme testi tek başına da path/rename seçicisinin gerçek Git davranışını kanıtlar.
"$repo_root/tooling/test/detect_changed_areas_test.sh"

actual_gates=$(awk '
  /^jobs:$/ { in_jobs = 1; next }
  in_jobs && /^  [a-z0-9-]+:$/ {
    job = $0
    sub(/^  /, "", job)
    sub(/:$/, "", job)
    next
  }
  in_jobs && /^    name: / {
    name = $0
    sub(/^    name: /, "", name)
    if (name == "Repo kalite kapısı" ||
        name == "Backend kalite kapısı" ||
        name == "Mobil kalite kapısı" ||
        name == "Bağımlılık güvenliği") {
      print job "|" name
    }
  }
' "$workflow" | sort)

if [[ "$actual_gates" != "$expected_gates" ]]; then
  echo "Kararlı gate kimliği/adı sözleşmesi bozuldu." >&2
  diff -u <(printf '%s\n' "$expected_gates") <(printf '%s\n' "$actual_gates") || true
  exit 1
fi

assert_job_text() {
  local job_id=$1
  local expected_text=$2
  local job_block

  job_block=$(awk -v target="$job_id" '
    $0 == "  " target ":" { in_job = 1; next }
    in_job && /^  [a-z0-9-]+:$/ { exit }
    in_job { print }
  ' "$workflow")

  if ! grep -F "$expected_text" <<< "$job_block" >/dev/null; then
    echo "$job_id işi beklenen sözleşmeyi içermiyor: $expected_text" >&2
    exit 1
  fi
}

for gate_id in repo-gate backend-gate mobile-gate dependency-gate; do
  assert_job_text "$gate_id" "if: always()"
done
assert_job_text repo-gate "needs: [changes, repo]"
assert_job_text backend-gate "needs: [changes, backend]"
assert_job_text mobile-gate "needs: [changes, mobile-android, mobile-ios]"
assert_job_text dependency-gate "needs: dependency-review"

required_texts=(
  "./gradlew test build --no-daemon"
  "dart format --output=none --set-exit-if-changed lib test"
  "flutter analyze"
  "flutter test"
  "flutter build apk --debug"
  "runs-on: macos-15"
  "flutter build ios --debug --simulator --no-codesign"
  "format: cyclonedx-json"
  "fail-on-severity: high"
  'if parent_sha=$(git rev-parse HEAD^ 2>/dev/null); then'
  'git hash-object -t tree /dev/null'
  'test "$BACKEND_RESULT" = success || test "$BACKEND_RESULT" = skipped'
  'test "$ANDROID_RESULT" = success || test "$ANDROID_RESULT" = skipped'
  'test "$IOS_RESULT" = success || test "$IOS_RESULT" = skipped'
  'test "$REVIEW_RESULT" = success'
)

for required_text in "${required_texts[@]}"; do
  grep -F "$required_text" "$workflow" >/dev/null
done

if grep -E '^[[:space:]]*uses: [^[:space:]@]+@(main|master|v[0-9]+([.][0-9]+)*)[[:space:]]*(#.*)?$' "$workflow"; then
  echo "Workflow action referansları tam commit SHA'sına sabitlenmelidir." >&2
  exit 1
fi

echo "Kalite workflow davranış ve kararlı gate sözleşmesi testleri geçti."
