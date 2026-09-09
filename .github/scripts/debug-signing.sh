#!/usr/bin/env bash
set -euo pipefail
# Pin AGP to the same private, cached development key in both CI jobs.
mkdir -p "$HOME/.android"
if [ ! -f "$HOME/.android/debug.keystore" ]; then
  keytool -genkeypair -keystore "$HOME/.android/debug.keystore" \
    -storepass android -keypass android -alias androiddebugkey \
    -keyalg RSA -keysize 2048 -validity 10000 \
    -dname 'CN=Android Debug,O=Android,C=US'
fi
echo "HALO_DEBUG_KEYSTORE=$HOME/.android/debug.keystore" >> "$GITHUB_ENV"
keytool -list -keystore "$HOME/.android/debug.keystore" -storepass android -alias androiddebugkey
