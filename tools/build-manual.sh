#!/usr/bin/env bash
# Daemon-free manual build for Flick.
# Used because Gradle cannot run in the build sandbox: its daemon protocol
# needs loopback TCP, which the sandbox blocks (other_tcp = Deny).
# Toolchain: Temurin JDK 17, Android SDK (platform-35, build-tools 35.0.0),
# kotlinc 2.0.21 + Compose compiler plugin 2.0.21, aapt2, d8, apksigner.
#
# Usage: tools/build-manual.sh
# Output: build/flick-release.apk (signed with ~/.config/flick/flick-release.keystore)
set -euo pipefail

PROJECT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$PROJECT"

export JAVA_HOME="${JAVA_HOME:-/home/hatch/jdk17}"
export PATH="$JAVA_HOME/bin:$PATH"
SDK="${ANDROID_SDK_ROOT:-/home/hatch/android-sdk}"
BUILD_TOOLS="$SDK/build-tools/35.0.0"
PLATFORM="$SDK/platforms/android-35"
AAPT2="$BUILD_TOOLS/aapt2"
D8="$BUILD_TOOLS/d8"
ZIPALIGN="$BUILD_TOOLS/zipalign"
APKSIGNER="$BUILD_TOOLS/apksigner"
KOTLINC="${KOTLINC:-/home/hatch/kotlin/kotlinc/bin/kotlinc}"
# NOTE: use the NON-embeddable plugin with the kotlinc CLI. The -embeddable
# plugin relocates intellij packages and only loads under the Gradle plugin.
COMPOSE_PLUGIN="${COMPOSE_PLUGIN:-/home/hatch/sdk-downloads/kotlin-compose-compiler-plugin-2.0.21.jar}"
KEYSTORE="${KEYSTORE:-/home/hatch/.config/flick/flick-release.keystore}"
KEY_PROPS="${KEY_PROPS:-/home/hatch/.config/flick/keystore.properties}"

# shellcheck disable=SC1090
source "$KEY_PROPS"  # provides store_password, key_password, key_alias

BUILD="$PROJECT/build/manual"
rm -rf "$BUILD"
mkdir -p "$BUILD/compiled" "$BUILD/gen" "$BUILD/classes" "$BUILD/dex"

echo "== aapt2 compile =="
"$AAPT2" compile --dir app/src/main/res -o "$BUILD/compiled-app.zip"
FLATS="$BUILD/compiled-app.zip"
# Library resources (e.g. material3 themes) must be linked too.
for resdir in build/deps/*-res; do
  [ -d "$resdir" ] || continue
  flat="$BUILD/compiled-$(basename "$resdir").zip"
  "$AAPT2" compile --dir "$resdir" -o "$flat" 2>/dev/null || true
  [ -f "$flat" ] && FLATS="$FLATS $flat"
done

echo "== aapt2 link (also generates R.java) =="
# aapt2 needs the package in the manifest itself (Gradle normally injects
# the package, versionCode and versionName from the Gradle config).
sed -e 's|<manifest xmlns:android="http://schemas.android.com/apk/res/android">|<manifest xmlns:android="http://schemas.android.com/apk/res/android"\n    package="com.ibfavas.flick"\n    android:versionCode="8"\n    android:versionName="1.0.7">|' \
  app/src/main/AndroidManifest.xml > "$BUILD/AndroidManifest.xml"
# BUG 1 (fixed in v1.0.4): aapt2 link --java generates ONLY the app's R.java.
# Library resources are linked as bare compiled flats with no package
# attribution, so no library R.java files are produced — yet library
# bytecode references its own R classes (e.g. PoolingContainer reads
# androidx.customview.poolingcontainer.R$id in its <clinit>, which crashed
# the app on first setContent with NoClassDefFoundError). --extra-packages
# makes aapt2 emit the same R.java under each library package name, with
# IDs identical to the app's R.java (verified: 3443 fields, 0 mismatches).
# Keep this list in sync with the R classes the dex references; enumerate
# them with: dexdump -d classes*.dex | grep -o 'L[a-z]*/R\$[a-z]*;'
# shellcheck disable=SC2086
"$AAPT2" link \
  -o "$BUILD/base-unaligned.apk" \
  --manifest "$BUILD/AndroidManifest.xml" \
  -I "$PLATFORM/android.jar" \
  --min-sdk-version 26 \
  --target-sdk-version 35 \
  --java "$BUILD/gen" \
  --extra-packages "androidx.activity:androidx.compose.foundation:androidx.compose.material3:androidx.compose.ui:androidx.compose.ui.graphics:androidx.core:androidx.customview.poolingcontainer:androidx.lifecycle.runtime:androidx.lifecycle.viewmodel:androidx.savedstate:androidx.startup" \
  $FLATS

echo "== javac (all generated R.java: app + every library package) =="
# R.java files contain only int constants, so no classpath is needed.
# They must be compiled BEFORE kotlinc and land in $BUILD/classes: the
# Kotlin code references the app's R class (FlickWidgetProvider imports
# com.ibfavas.flick.R), so kotlinc needs the compiled R classes on its
# classpath. They also end up in the jar step below, so d8 sees them.
# shellcheck disable=SC2086
"$JAVA_HOME/bin/javac" -d "$BUILD/classes" $(find "$BUILD/gen" -name '*.java')

echo "== kotlinc =="
CP="$(cat build/deps/classpath.txt):$PLATFORM/android.jar:$BUILD/classes"
# BUG 2 (fixed in v1.0.4): kotlinc SILENTLY IGNORES .java files.
# Passing the generated R.java files here compiled only the .kt files —
# `kotlinc R.java -d out` fails with "no source files" and mixed input
# drops the .java silently — so v1.0.0–v1.0.3 shipped with NO R class at
# all in the dex (verified with dexdump: zero R class descriptors, not
# even com.ibfavas.flick.R). Never pass .java files to kotlinc again;
# compile them with javac above instead.
# shellcheck disable=SC2086
"$KOTLINC" \
  -Xplugin="$COMPOSE_PLUGIN" \
  -cp "$CP" \
  -d "$BUILD/classes" \
  $(find app/src/main/java -name '*.kt')

echo "== d8 =="
KOTLIN_LIB="$(dirname "$KOTLINC")/../lib"
# d8 rejects a raw classes directory here (it trips on the .kotlin_module
# file), so jar the app classes first, as AGP does.
(cd "$BUILD/classes" && "$JAVA_HOME/bin/jar" cf ../app-classes.jar .)
# shellcheck disable=SC2086
"$D8" --release --min-api 26 --lib "$PLATFORM/android.jar" \
  --output "$BUILD/dex" \
  "$BUILD/app-classes.jar" \
  $(cat build/deps/classpath.txt | tr ':' ' ') \
  "$KOTLIN_LIB/kotlin-stdlib.jar"

echo "== package dex + META-INF/services into apk =="
# META-INF/services/* must be merged from the dependency jars into the
# APK: ServiceLoader (used by kotlinx-coroutines to find the Android
# MainDispatcherFactory) only looks inside the APK. v1.0.0 omitted them,
# so the app crashed on launch with "Module with the Main dispatcher
# is missing". Same-named service files are concatenated (deduped);
# signature files are never copied. Runs before zipalign + sign.
python3 - "$BUILD/base-unaligned.apk" "$BUILD/dex" "$PROJECT/build/deps" "$KOTLIN_LIB/kotlin-stdlib.jar" <<'PYEOF'
import glob, os, sys, zipfile, shutil
apk, dex_dir, deps_dir, stdlib = sys.argv[1:5]
tmp = apk + ".tmp"
shutil.copyfile(apk, tmp)
services = {}
jars = sorted(glob.glob(os.path.join(deps_dir, "*.jar"))) + [stdlib]
for jar in jars:
    try:
        with zipfile.ZipFile(jar) as z:
            for name in z.namelist():
                if not name.startswith("META-INF/services/") or name.endswith("/"):
                    continue
                data = z.read(name).decode("utf-8", "replace")
                lines = services.setdefault(name, [])
                for line in data.splitlines():
                    line = line.strip()
                    if line and line not in lines:
                        lines.append(line)
    except zipfile.BadZipFile:
        continue
with zipfile.ZipFile(tmp, "a", zipfile.ZIP_DEFLATED) as z:
    for dex in sorted(glob.glob(dex_dir + "/classes*.dex")):
        z.write(dex, dex.split("/")[-1])
    for name, lines in sorted(services.items()):
        z.writestr(name, "\n".join(lines) + "\n")
shutil.move(tmp, apk)
PYEOF

echo "== zipalign + sign =="
"$ZIPALIGN" -f 4 "$BUILD/base-unaligned.apk" "$BUILD/flick-aligned.apk"
"$APKSIGNER" sign \
  --ks "$KEYSTORE" --ks-pass "pass:$store_password" \
  --key-pass "pass:$key_password" --ks-key-alias "$key_alias" \
  --out "$PROJECT/build/flick-release.apk" "$BUILD/flick-aligned.apk"

echo "== verify =="
"$APKSIGNER" verify --verbose "$PROJECT/build/flick-release.apk"
"$AAPT2" dump badging "$PROJECT/build/flick-release.apk" | head -n 3 || true
ls -la "$PROJECT/build/flick-release.apk"
