#!/usr/bin/env bash
# Sweep for v1.3.0 round 2: orientation + margin + open-DTW params.
# Usage: ./sweep5.sh  (run from tools/recognizer-test)
# Each combo: label orientMax free full dtwBand margin skip maxskip openBand
set -u
cd "$(dirname "$0")"
export PATH=/home/hatch/jdk17/bin:$PATH
KOTLINC=/home/hatch/kotlin/kotlinc/bin/kotlinc
SRC=../../app/src/main/java/com/ibfavas/flick/gesture/Recognizer.kt

run_combo() {
    local label="$1" omax="$2" free="$3" full="$4" band="$5" margin="$6" skip="$7" maxskip="$8" openband="$9"
    local tmp="SweepRecognizer.kt"
    sed -e "s/private const val ORIENTATION_PENALTY_MAX = [0-9.]*f/private const val ORIENTATION_PENALTY_MAX = ${omax}f/" \
        -e "s/private const val ORIENTATION_FREE_DEGREES = [0-9.]*f/private const val ORIENTATION_FREE_DEGREES = ${free}f/" \
        -e "s/private const val ORIENTATION_FULL_DEGREES = [0-9.]*f/private const val ORIENTATION_FULL_DEGREES = ${full}f/" \
        -e "s/private const val DTW_BAND = [0-9]*/private const val DTW_BAND = $band/" \
        -e "s/private const val MARGIN = [0-9.]*f/private const val MARGIN = ${margin}f/" \
        -e "s/private const val DTW_SKIP_PENALTY = [0-9.]*f/private const val DTW_SKIP_PENALTY = ${skip}f/" \
        -e "s/private const val DTW_MAX_SKIP = [0-9]*/private const val DTW_MAX_SKIP = $maxskip/" \
        -e "s/private const val DTW_OPEN_BAND = [0-9]*/private const val DTW_OPEN_BAND = $openband/" \
        "$SRC" > "$tmp"
    "$KOTLINC" "$tmp" RecognizerTest.kt ShapeGen.kt SloppyGen.kt LiveGen.kt -include-runtime -d sweep.jar 2>/dev/null
    echo "=== $label (omax=$omax free=$free full=$full band=$band margin=$margin skip=$skip maxskip=$maxskip openband=$openband) ==="
    /home/hatch/jdk17/bin/java -jar sweep.jar 2>/dev/null | grep -E "VARIATIONS|FALSE MATCHES|TILT:|SLOPPY:|REV:|LIVE:"
    rm -f "$tmp" sweep.jar
}

run_combo "omax25-margin025"        0.25 20 40 10 0.025 10 24 28
run_combo "omax25-margin02"         0.25 20 40 10 0.02  10 24 28
run_combo "omax30-margin025"        0.30 20 40 10 0.025 10 24 28
run_combo "omax25-m025-skip15"      0.25 20 40 10 0.025 15 24 28
run_combo "omax25-m025-maxskip16"   0.25 20 40 10 0.025 10 16 28
run_combo "omax25-m025-openband20"  0.25 20 40 10 0.025 10 24 20
run_combo "omax25-m025-skip5"       0.25 20 40 10 0.025 5  24 28
run_combo "omax25-m025-maxskip32"   0.25 20 40 10 0.025 10 32 28
