#!/usr/bin/env bash
set -euo pipefail
project_dir="$(cd "$(dirname "$0")/.." && pwd)"
cd "$project_dir"
if [[ -z "${JAVA_HOME:-}" && -d /opt/homebrew/opt/openjdk@21 ]]; then
  export JAVA_HOME=/opt/homebrew/opt/openjdk@21
fi
export PATH="${JAVA_HOME:+$JAVA_HOME/bin:}$PATH"
sdk_dir="${ANDROID_HOME:-$HOME/Library/Android/sdk}"
export ANDROID_HOME="$sdk_dir"
key_dir="$HOME/.local/share/timecapsule-signing"
mkdir -p "$key_dir" apk
chmod 700 "$key_dir"
if [[ ! -f "$key_dir/local-release.jks" ]]; then
  umask 077
  openssl rand -base64 32 > "$key_dir/password"
  keytool -genkeypair -keystore "$key_dir/local-release.jks" -storepass:file "$key_dir/password" \
    -keypass:file "$key_dir/password" -alias timecapsule -keyalg RSA -keysize 3072 -validity 3650 \
    -dname 'CN=TimeCapsule Local Development' >/dev/null 2>&1
fi
./gradlew --no-daemon :app:testDebugUnitTest :app:lintDebug :app:assembleRelease :app:assembleDebug
"$sdk_dir/build-tools/35.0.0/zipalign" -f -p 4 app/build/outputs/apk/release/app-release-unsigned.apk apk/timecapsule-aligned.apk
"$sdk_dir/build-tools/35.0.0/apksigner" sign --ks "$key_dir/local-release.jks" \
  --ks-key-alias timecapsule --ks-pass "file:$key_dir/password" \
  --out apk/타임캡슐_release.apk apk/timecapsule-aligned.apk
rm apk/timecapsule-aligned.apk
"$sdk_dir/build-tools/35.0.0/apksigner" verify --verbose apk/타임캡슐_release.apk
shasum -a 256 apk/타임캡슐_release.apk > apk/SHA256SUMS
