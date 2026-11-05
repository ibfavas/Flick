#!/usr/bin/env bash
# Parameter sweep for the $N recognizer: try constant combos on a temp
# copy of Recognizer.kt and report SINGLE/MULTI suite numbers for each.
# Usage: ./sweep.sh  (run from tools/recognizer-test)
set -u
cd "$(dirname "$0")"
export PATH=/home/hatch/jdk17/bin:$PATH
KOTLINC=/home/hatch/kotlin/kotlinc/bin/kotlinc
SRC=../../app/src/main/java/com/ibfavas/flick/gesture/Recognizer.kt

run_combo() {
    local label="$1" resample="$2" thr="$3" openmax="$4" free="$5" full="$6"
    local tmp="SweepRecognizer.kt"
    sed -e "s/const val RESAMPLE_COUNT = [0-9]*/const val RESAMPLE_COUNT = $resample/" \
        -e "s/const val DEFAULT_THRESHOLD = [0-9.]*f/const val DEFAULT_THRESHOLD = ${thr}f/" \
        -e "s/private const val ORIENTATION_PENALTY_MAX = [0-9.]*f/private const val ORIENTATION_PENALTY_MAX = ${openmax}f/" \
        -e "s/private const val ORIENTATION_FREE_DEGREES = [0-9.]*f/private const val ORIENTATION_FREE_DEGREES = ${free}f/" \
        -e "s/private const val ORIENTATION_FULL_DEGREES = [0-9.]*f/private const val ORIENTATION_FULL_DEGREES = ${full}f/" \
        "$SRC" > "$tmp"
    "$KOTLINC" "$tmp" RecognizerTest.kt ShapeGen.kt -include-runtime -d sweep.jar 2>/dev/null
    echo "=== $label (resample=$resample thr=$thr orientMax=$openmax free=$free full=$full) ==="
    /home/hatch/jdk17/bin/java -jar sweep.jar 2>/dev/null | grep -E "VARIATIONS|FALSE MATCHES"
    rm -f "$tmp" sweep.jar
}

run_combo "baseline"        96 0.85 0.15 15 45
run_combo "thr-0.88"        96 0.88 0.15 15 45
run_combo "thr-0.87-o20"    96 0.87 0.20 10 40
run_combo "thr-0.88-o20"    96 0.88 0.20 10 40
run_combo "thr-0.88-o25"    96 0.88 0.25 10 35
run_combo "r128-thr-0.88-o20" 128 0.88 0.20 10 40
run_combo "thr-0.90"        96 0.90 0.15 15 45
run_combo "thr-0.87-o18"    96 0.87 0.18 12 42
