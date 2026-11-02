#!/usr/bin/env bash
set -euo pipefail
sdk_dir="${ANDROID_HOME:-$HOME/Library/Android/sdk}"
if ! "$sdk_dir/emulator/emulator" -list-avds | grep -qx TimeCapsule_Pleos; then
  echo 'TimeCapsule_Pleos AVD가 없습니다. README의 공식 Pleos 이미지로 AVD를 먼저 생성하세요.' >&2
  exit 1
fi
exec "$sdk_dir/emulator/emulator" -avd TimeCapsule_Pleos -no-snapshot -gpu host "$@"
