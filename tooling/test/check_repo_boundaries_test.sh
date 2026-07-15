#!/bin/sh

set -eu

tooling_root=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
check_script="$tooling_root/check_repo_boundaries.sh"
fixture=$(mktemp -d)
trap 'rm -rf "$fixture"' EXIT HUP INT TERM

reset_fixture() {
  rm -rf "$fixture/apps" "$fixture/experiments"
  mkdir -p \
    "$fixture/apps/mobile" \
    "$fixture/apps/backend/gradle/wrapper" \
    "$fixture/experiments/sample"
  touch \
    "$fixture/apps/mobile/pubspec.yaml" \
    "$fixture/apps/mobile/pubspec.lock" \
    "$fixture/apps/backend/build.gradle" \
    "$fixture/apps/backend/gradlew" \
    "$fixture/apps/backend/gradle/wrapper/gradle-wrapper.properties"
}

expect_rejected() {
  label=$1
  if REPO_ROOT="$fixture" "$check_script" >/dev/null 2>&1
  then
    echo "Reddedilmesi gereken senaryo geçti: $label" >&2
    exit 1
  fi
}

reset_fixture
REPO_ROOT="$fixture" "$check_script" >/dev/null

printf '%s\n' \
  'dependencies:' \
  '  spike:' \
  '    path: ../../experiments/sample' >"$fixture/apps/mobile/pubspec.yaml"
expect_rejected 'pubspec path dependency'

reset_fixture
printf '%s\n' \
  'includeBuild("../../experiments/sample")' >"$fixture/apps/backend/settings.gradle.kts"
expect_rejected 'Gradle settings dependency'

reset_fixture
printf '%s\n' \
  '{"dependencies":{"spike":"file:../../experiments/sample"}}' >"$fixture/apps/mobile/package.json"
expect_rejected 'other dependency manifest'

reset_fixture
mkdir -p "$fixture/apps/mobile/lib"
printf '%s\n' \
  "import '../../../experiments/sample/spike.dart';" >"$fixture/apps/mobile/lib/main.dart"
expect_rejected 'production source dependency'

reset_fixture
ln -s ../../experiments/sample "$fixture/apps/mobile/experiment-link"
expect_rejected 'experiments symlink'

reset_fixture
mkdir -p "$fixture/apps/mobile/ios/Runner.xcodeproj"
printf '%s\n' \
  'path = ../../experiments/sample/Spike.swift;' >"$fixture/apps/mobile/ios/Runner.xcodeproj/project.pbxproj"
expect_rejected 'Xcode project source path'

reset_fixture
mkdir -p "$fixture/apps/mobile/ios/Flutter"
printf '%s\n' \
  'HEADER_SEARCH_PATHS = ../../experiments/sample' >"$fixture/apps/mobile/ios/Flutter/Debug.xcconfig"
expect_rejected 'Xcode xcconfig path'

reset_fixture
mkdir -p "$fixture/apps/mobile/ios/Runner.xcworkspace"
printf '%s\n' \
  '<FileRef location="group:../../experiments/sample"></FileRef>' \
  >"$fixture/apps/mobile/ios/Runner.xcworkspace/contents.xcworkspacedata"
expect_rejected 'Xcode workspace link'

reset_fixture
mkdir -p "$fixture/apps/mobile/ios/Runner.xcodeproj/xcshareddata/xcschemes"
printf '%s\n' \
  '<BuildableReference ReferencedContainer="container:../../experiments/sample"></BuildableReference>' \
  >"$fixture/apps/mobile/ios/Runner.xcodeproj/xcshareddata/xcschemes/Runner.xcscheme"
expect_rejected 'Xcode scheme link'

reset_fixture
mkdir -p "$fixture/apps/mobile/build" "$fixture/apps/mobile/ios"
printf '%s\n' \
  'path = ../../experiments/sample/Generated.swift;' >"$fixture/apps/mobile/build/generated.pbxproj"
ln -s ../../../experiments/sample "$fixture/apps/mobile/build/generated-link"
printf '%s\n' \
  '../../experiments/sample' >"$fixture/apps/mobile/ios/binary-fixture.bin"
REPO_ROOT="$fixture" "$check_script" >/dev/null

echo 'Repo sınır regresyon testleri geçti.'
