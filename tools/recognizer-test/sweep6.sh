#!/usr/bin/env bash
# Sweep for v1.3.0 round 3: fine-tune orientation around omax30 to
# fix the single -25deg 'c' TILT regression without losing LIVE gains.
# Usage: ./sweep6.sh  (run from tools/recognizer-test)
# Each combo: label orientMax free full margin
set -u
cd "$(dirname "$0")"
export PATH=/home/hatch/jdk17/bin:$PATH
KOTLINC=/home/hatch/kotlin/kotlinc/bin/kotlinc
SRC=../../app/src/main/java/com/ibfavas/flick/gesture/Recognizer.kt

run_combo() {
    local label="$1" omax="$2" free="$3" full="$4" margin="$5"
    local tmp="SweepRecognizer.kt"
    sed -e "s/private const val ORIENTATION_PENALTY_MAX = [0-9.]*f/private const val ORIENTATION_PENALTY_MAX = ${omax}f/" \
        -e "s/private const val ORIENTATION_FREE_DEGREES = [0-9.]*f/private const val ORIENTATION_FREE_DEGREES = ${free}f/" \
        -e "s/private const val ORIENTATION_FULL_DEGREES = [0-9.]*f/private const val ORIENTATION_FULL_DEGREES = ${full}f/" \
        -e "s/private const val MARGIN = [0-9.]*f/private const val MARGIN = ${margin}f/" \
        "$SRC" > "$tmp"
    "$KOTLINC" "$tmp" RecognizerTest.kt ShapeGen.kt SloppyGen.kt LiveGen.kt -include-runtime -d sweep.jar 2>/dev/null
    echo "=== $label (omax=$omax free=$free full=$full margin=$margin) ==="
    /home/hatch/jdk17/bin/java -jar sweep.jar 2>/dev/null | grep -E "VARIATIONS|FALSE MATCHES|TILT:|SLOPPY:|REV:|LIVE:"
    rm -f "$tmp" sweep.jar
}

run_combo "omax30-free21-m025"  0.30 21 40 0.025
run_combo "omax30-free22-m025"  0.30 22 40 0.025
run_combo "omax30-free23-m025"  0.30 23 40 0.025
run_combo "omax32-free22-m025"  0.32 22 40 0.025
run_combo "omax28-free20-m025"  0.28 20 40 0.025
run_combo "omax30-free22-m03"   0.30 22 40 0.03
