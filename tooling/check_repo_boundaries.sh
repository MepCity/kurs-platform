#!/bin/sh

set -eu

default_repo_root=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
repo_root=${REPO_ROOT:-$default_repo_root}
cd "$repo_root"

for required in \
  apps/mobile/pubspec.yaml \
  apps/mobile/pubspec.lock \
  apps/backend/build.gradle \
  apps/backend/gradlew \
  apps/backend/gradle/wrapper/gradle-wrapper.properties
do
  if [ ! -f "$required" ]; then
    echo "Eksik uygulama sınırı: $required" >&2
    exit 1
  fi
done

for forbidden in src lib shared common utils
do
  if [ -e "$forbidden" ]; then
    echo "Kökte yasak ortak kaynak dizini bulundu: $forbidden" >&2
    exit 1
  fi
done

if find apps \
  \( -type d \( -name build -o -name .dart_tool -o -name .gradle -o -name Pods -o -name DerivedData \) -prune \) -o \
  \( -type f \( -name '*.dart' -o -name '*.java' -o -name '*.kt' -o -name '*.swift' \) \
    -exec grep -Hn -E '(^|[/\\])experiments([/\\]|$)' {} + \) | grep .
then
  echo 'Üretim uygulaması kaynağı experiments dizinine başvuruyor.' >&2
  exit 1
fi

if find apps \
  \( -type d \( -name build -o -name .dart_tool -o -name .gradle -o -name Pods -o -name DerivedData \) -prune \) -o \
  \( -type f \( \
      -name 'pubspec.yaml' -o -name 'pubspec.lock' -o \
      -name '*.gradle' -o -name '*.gradle.kts' -o -name 'gradle.properties' -o \
      -name '*.toml' -o -name '*.lock' -o \
      -name 'package.json' -o -name 'package-lock.json' -o -name 'npm-shrinkwrap.json' -o \
      -name 'yarn.lock' -o -name 'pnpm-lock.yaml' -o \
      -name 'pom.xml' -o -name 'Podfile' -o -name 'Podfile.lock' -o \
      -name 'Cartfile' -o -name 'Package.swift' -o \
      -name 'Gemfile' -o -name 'Gemfile.lock' -o \
      -name 'pyproject.toml' -o -name 'requirements*.txt' -o -name 'Pipfile' -o \
      -name 'Cargo.toml' -o -name 'composer.json' -o \
      -name '*.pbxproj' -o -name '*.xcconfig' -o -name '*.xcsettings' -o \
      -name 'contents.xcworkspacedata' -o -name '*.xcscheme' \
    \) -exec grep -Hn -E '(^|[/\\])experiments([/\\]|$)' {} + \) | grep .
then
  echo 'Uygulama dependency manifesti experiments dizinine başvuruyor.' >&2
  exit 1
fi

symlink_list=$(mktemp)
trap 'rm -f "$symlink_list"' EXIT HUP INT TERM
find apps \
  \( -type d \( -name build -o -name .dart_tool -o -name .gradle -o -name Pods -o -name DerivedData \) -prune \) -o \
  \( -type l -print \) >"$symlink_list"
while IFS= read -r link
do
  link_target=$(readlink "$link")
  resolved_target=$(realpath "$link" 2>/dev/null || true)
  if printf '%s\n' "$link_target" | grep -Eq '(^|[/\\])experiments([/\\]|$)'
  then
    echo "Uygulama experiments dizinine symlink içeriyor: $link" >&2
    exit 1
  fi
  case "$resolved_target" in
    "$repo_root/experiments"|"$repo_root/experiments/"*)
      echo "Uygulama experiments dizinine çözümlenen symlink içeriyor: $link" >&2
      exit 1
      ;;
  esac
done <"$symlink_list"

echo 'Repo uygulama ve deney sınırları geçerli.'
