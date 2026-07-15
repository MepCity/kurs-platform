#!/usr/bin/env bash

set -euo pipefail

if [[ $# -ne 2 ]]; then
  echo "Kullanım: $0 <base-ref> <head-ref>" >&2
  exit 64
fi

base_ref=$1
head_ref=$2
output_file=${GITHUB_OUTPUT:-/dev/stdout}
backend=false
mobile=false

while IFS= read -r -d '' changed_file; do
  case "$changed_file" in
    apps/backend/*)
      backend=true
      ;;
    apps/mobile/*)
      mobile=true
      ;;
    experiments/*)
      # Deneyler üretim uygulamalarından bağımsızdır.
      ;;
    *)
      # Ortak sözleşme, tooling veya CI değişikliği iki uygulamayı da etkiler.
      backend=true
      mobile=true
      ;;
  esac
done < <(git diff --no-renames --name-only -z "$base_ref" "$head_ref")

{
  echo "backend=$backend"
  echo "mobile=$mobile"
} >> "$output_file"
