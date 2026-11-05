#!/usr/bin/env bash
# Parameter sweep for the v1.3.0 recognizer work: try constant combos on
# a temp copy of Recognizer.kt and report ALL suite numbers for each.
# Usage: ./sweep3.sh  (run from tools/recognizer-test)
# Each combo: label orientMax free full dtwBand margin
set -u
cd "$(dirname "$0")"
export PATH=/home/hatch/jdk17/bin:$PATH
KOTLINC=/home/hatch/kotlin/kotlinc/bin/kotlinc
SRC=../../app/src/main/java/com/ibfavas/flick/gesture/Recognizer.kt

run_combo() {
    local label="$1" omax="$2" free="$3" full="$4" band="$5" margin="$6"
    local tmp="SweepRecognizer.kt"
    sed -e "s/private const val ORIENTATION_PENALTY_MAX = [0-9.]*f/private const val ORIENTATION_PENALTY_MAX = ${omax}f/" \
        -e "s/private const val ORIENTATION_FREE_DEGREES = [0-9.]*f/private const val ORIENTATION_FREE_DEGREES = ${free}f/" \
        -e "s/private const val ORIENTATION_FULL_DEGREES = [0-9.]*f/private const val ORIENTATION_FULL_DEGREES = ${full}f/" \
        -e "s/private const val DTW_BAND = [0-9]*/private const val DTW_BAND = $band/" \
        -e "s/private const val MARGIN = [0-9.]*f/private const val MARGIN = ${margin}f/" \
        "$SRC" > "$tmp"
    "$KOTLINC" "$tmp" RecognizerTest.kt ShapeGen.kt SloppyGen.kt LiveGen.kt -include-runtime -d sweep.jar 2>/dev/null
    echo "=== $label (orientMax=$omax free=$free full=$full band=$band margin=$margin) ==="
    /home/hatch/jdk17/bin/java -jar sweep.jar 2>/dev/null | grep -E "VARIATIONS|FALSE MATCHES|TILT:|SLOPPY:|REV:|LIVE:"
    rm -f "$tmp" sweep.jar
}

run_combo "baseline"        0.20 20 40 10 0.03
run_combo "omax25"          0.25 20 40 10 0.03
run_combo "omax30"          0.30 20 40 10 0.03
run_combo "omax30-free15"   0.30 15 40 10 0.03
run_combo "omax25-free15"   0.25 15 40 10 0.03
run_combo "omax35-free20"   0.35 20 45 10 0.03
run_combo "omax30-free15-f45" 0.30 15 45 10 0.03
run_combo "band14"          0.20 20 40 14 0.03
run_combo "band8"           0.20 20 40 8 0.03
run_combo "margin025"       0.20 20 40 10 0.025
run_combo "margin02"        0.20 20 40 10 0.02
