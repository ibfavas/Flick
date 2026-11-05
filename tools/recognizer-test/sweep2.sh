#!/usr/bin/env bash
# Parameter sweep for the v1.2.0 open-ended DTW: try constant combos on
# a temp copy of Recognizer.kt and report all suite numbers for each.
# Usage: ./sweep2.sh  (run from tools/recognizer-test)
set -u
cd "$(dirname "$0")"
export PATH=/home/hatch/jdk17/bin:$PATH
KOTLINC=/home/hatch/kotlin/kotlinc/bin/kotlinc
SRC=../../app/src/main/java/com/ibfavas/flick/gesture/Recognizer.kt

run_combo() {
    local label="$1" skip="$2" maxskip="$3" band="$4" margin="$5" resample="$6" thr="$7"
    local tmp="SweepRecognizer.kt"
    sed -e "s/private const val DTW_SKIP_PENALTY = [0-9.]*f/private const val DTW_SKIP_PENALTY = ${skip}f/" \
        -e "s/private const val DTW_MAX_SKIP = [0-9]*/private const val DTW_MAX_SKIP = $maxskip/" \
        -e "s/private const val DTW_OPEN_BAND = [0-9]*/private const val DTW_OPEN_BAND = $band/" \
        -e "s/private const val MARGIN = [0-9.]*f/private const val MARGIN = ${margin}f/" \
        -e "s/const val RESAMPLE_COUNT = [0-9]*/const val RESAMPLE_COUNT = $resample/" \
        -e "s/const val DEFAULT_THRESHOLD = [0-9.]*f/const val DEFAULT_THRESHOLD = ${thr}f/" \
        "$SRC" > "$tmp"
    "$KOTLINC" "$tmp" RecognizerTest.kt ShapeGen.kt SloppyGen.kt LiveGen.kt -include-runtime -d sweep.jar 2>/dev/null
    echo "=== $label (skip=$skip maxskip=$maxskip band=$band margin=$margin resample=$resample thr=$thr) ==="
    /home/hatch/jdk17/bin/java -jar sweep.jar 2>/dev/null | grep -E "VARIATIONS|FALSE MATCHES|TILT:|SLOPPY:|REV:|LIVE:"
    rm -f "$tmp" sweep.jar
}

run_combo "skip20"  20 24 28 0.03 96 0.87
run_combo "skip60"  60 24 28 0.03 96 0.87
run_combo "skip80"  80 24 28 0.03 96 0.87
run_combo "maxskip16" 40 16 28 0.03 96 0.87
run_combo "maxskip32" 40 32 28 0.03 96 0.87
run_combo "band20" 40 24 20 0.03 96 0.87
run_combo "margin02" 40 24 28 0.02 96 0.87
run_combo "margin05" 40 24 28 0.05 96 0.87
