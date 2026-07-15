#!/usr/bin/env bash

set -euo pipefail

repo_root=$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)
script="$repo_root/tooling/ci/detect_changed_areas.sh"
fixtures=$(mktemp -d)
trap 'rm -rf "$fixtures"' EXIT
case_dir=
base=

start_case() {
  local case_name=$1

  case_dir="$fixtures/$case_name"
  mkdir -p "$case_dir"
  git -C "$case_dir" init -q
  git -C "$case_dir" config user.name "CI Test"
  git -C "$case_dir" config user.email "ci-test@example.invalid"
}

commit_base() {
  git -C "$case_dir" add .
  git -C "$case_dir" commit -qm başlangıç
  base=$(git -C "$case_dir" rev-parse HEAD)
}

commit_change() {
  git -C "$case_dir" add -A
  git -C "$case_dir" commit -qm değişiklik
}

assert_outputs() {
  local expected_backend=$1
  local expected_mobile=$2
  local output

  output=$(cd "$case_dir" && GITHUB_OUTPUT=/dev/stdout "$script" "$base" HEAD)
  grep -Fx "backend=$expected_backend" <<< "$output" >/dev/null
  grep -Fx "mobile=$expected_mobile" <<< "$output" >/dev/null
}

create_file() {
  local path=$1

  mkdir -p "$(dirname "$case_dir/$path")"
  printf '%s\n' "$path" > "$case_dir/$path"
}

move_file() {
  local source=$1
  local target=$2

  mkdir -p "$(dirname "$case_dir/$target")"
  git -C "$case_dir" mv "$source" "$target"
}

# Normal path davranışları.
start_case backend-only
create_file README.md
commit_base
create_file apps/backend/change.txt
commit_change
assert_outputs true false

start_case mobile-only
create_file README.md
commit_base
create_file apps/mobile/change.txt
commit_change
assert_outputs false true

start_case experiments-only
create_file README.md
commit_base
create_file experiments/a-test/change.txt
commit_change
assert_outputs false false

# Silme, kaynak uygulamanın kapısını çalıştırmalıdır.
start_case backend-delete
create_file apps/backend/deleted.txt
commit_base
git -C "$case_dir" rm -q apps/backend/deleted.txt
commit_change
assert_outputs true false

start_case mobile-delete
create_file apps/mobile/deleted.txt
commit_base
git -C "$case_dir" rm -q apps/mobile/deleted.txt
commit_change
assert_outputs false true

# --no-renames eski ve yeni yolları ayrı delete/add olarak fail-closed değerlendirir.
start_case backend-to-experiments
create_file apps/backend/moved.txt
commit_base
move_file apps/backend/moved.txt experiments/a-test/moved.txt
commit_change
assert_outputs true false

start_case experiments-to-backend
create_file experiments/a-test/moved.txt
commit_base
move_file experiments/a-test/moved.txt apps/backend/moved.txt
commit_change
assert_outputs true false

start_case mobile-to-experiments
create_file apps/mobile/moved.txt
commit_base
move_file apps/mobile/moved.txt experiments/a-test/moved.txt
commit_change
assert_outputs false true

start_case experiments-to-mobile
create_file experiments/a-test/moved.txt
commit_base
move_file experiments/a-test/moved.txt apps/mobile/moved.txt
commit_change
assert_outputs false true

start_case backend-to-mobile
create_file apps/backend/moved.txt
commit_base
move_file apps/backend/moved.txt apps/mobile/moved.txt
commit_change
assert_outputs true true

start_case common-to-experiments
create_file MERGE_POLICY.md
commit_base
move_file MERGE_POLICY.md experiments/a-test/MERGE_POLICY.md
commit_change
assert_outputs true true

# İlk push'ta parent yoksa workflow'un kullandığı boş Git ağacı başlangıç diff'ini korur.
start_case initial-backend
create_file apps/backend/initial.txt
git -C "$case_dir" add .
git -C "$case_dir" commit -qm başlangıç
base=$(git -C "$case_dir" hash-object -t tree /dev/null)
assert_outputs true false

echo "Değişen alan seçimi; normal, silme ve rename regresyonlarında geçti."
