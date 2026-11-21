#!/bin/sh
set -eu
src=$(CDPATH='' cd -- "$(dirname -- "$0")" && pwd)
out=${1:?Usage: build.sh output.apk}
sdk=${ANDROID_HOME:-/opt/homebrew/share/android-commandlinetools}
bt="$sdk/build-tools/36.0.0"
jar="$sdk/platforms/android-36/android.jar"
tmp=$(mktemp -d)
trap 'rm -rf "$tmp"' EXIT
mkdir "$tmp/classes"
javac --release 8 -cp "$jar" -d "$tmp/classes" "$src/Probe.java"
"$bt/d8" --min-api 33 --lib "$jar" --output "$tmp" "$tmp/classes/local/codex/vdaccess/Probe.class"
"$bt/aapt2" link -I "$jar" --manifest "$src/AndroidManifest.xml" -o "$tmp/base.apk"
(cd "$tmp" && zip -q base.apk classes.dex)
"$bt/zipalign" -f 4 "$tmp/base.apk" "$tmp/aligned.apk"
keytool -genkeypair -keystore "$tmp/test.p12" -storepass android -alias test -keyalg RSA -keysize 2048 -validity 2 -dname CN=VDProbe >/dev/null 2>&1
"$bt/apksigner" sign --ks "$tmp/test.p12" --ks-pass pass:android --out "$out" "$tmp/aligned.apk"
