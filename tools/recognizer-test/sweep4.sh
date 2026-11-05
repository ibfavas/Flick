#!/usr/bin/env bash
# Sweep for the v1.3.0 decoupled-threshold variant (RecognizerV2):
# try constant combos on a temp copy and report ALL suite numbers.
# Usage: ./sweep4.sh  (run from tools/recognizer-test)
# Each combo: label useShape shapeThr orientMax free full margin
set -u
cd "$(dirname "$0")"
export PATH=/home/hatch/jdk17/bin:$PATH
KOTLINC=/home/hatch/kotlin/kotlinc/bin/kotlinc
SRC=RecognizerV2.kt

run_combo() {
    local label="$1" useShape="$2" shapeThr="$3" omax="$4" free="$5" full="$6" margin="$7"
    local tmp="SweepV2.kt"
    sed -e "s/private const val USE_SHAPE_THRESHOLD = [a-z]*/private const val USE_SHAPE_THRESHOLD = $useShape/" \
        -e "s/private const val SHAPE_THRESHOLD = [0-9.]*f/private const val SHAPE_THRESHOLD = ${shapeThr}f/" \
        -e "s/private const val ORIENTATION_PENALTY_MAX = [0-9.]*f/private const val ORIENTATION_PENALTY_MAX = ${omax}f/" \
        -e "s/private const val ORIENTATION_FREE_DEGREES = [0-9.]*f/private const val ORIENTATION_FREE_DEGREES = ${free}f/" \
        -e "s/private const val ORIENTATION_FULL_DEGREES = [0-9.]*f/private const val ORIENTATION_FULL_DEGREES = ${full}f/" \
        -e "s/private const val MARGIN = [0-9.]*f/private const val MARGIN = ${margin}f/" \
        "$SRC" > "$tmp"
    "$KOTLINC" "$tmp" TestV2.kt ShapeGen.kt SloppyGen.kt LiveGen.kt -include-runtime -d sweep.jar 2>/dev/null
    echo "=== $label (shapeThr=$useShape/$shapeThr orientMax=$omax free=$free full=$full margin=$margin) ==="
    /home/hatch/jdk17/bin/java -jar sweep.jar 2>/dev/null | grep -E "VARIATIONS|FALSE MATCHES|TILT:|SLOPPY:|REV:|LIVE:"
    rm -f "$tmp" sweep.jar
}

# Control: V2 with the flag off must reproduce the v1.2.2 baseline.
run_combo "v2-control-off"      false 0.90 0.20 20 40 0.03
# Decoupled threshold, baseline orientation.
run_combo "shape087-omax20"     true 0.87 0.20 20 40 0.03
run_combo "shape090-omax20"     true 0.90 0.20 20 40 0.03
run_combo "shape092-omax20"     true 0.92 0.20 20 40 0.03
# Decoupled threshold + stronger orientation (free 15).
run_combo "shape087-omax30f15"  true 0.87 0.30 15 40 0.03
run_combo "shape090-omax30f15"  true 0.90 0.30 15 40 0.03
run_combo "shape092-omax30f15"  true 0.92 0.30 15 40 0.03
run_combo "shape090-omax25f15"  true 0.90 0.25 15 40 0.03
# Decoupled threshold + omax30 free 20.
run_combo "shape090-omax30f20"  true 0.90 0.30 20 40 0.03
run_combo "shape087-omax30f20"  true 0.87 0.30 20 40 0.03
