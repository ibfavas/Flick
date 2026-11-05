#!/usr/bin/env bash
# Fresh-seed check for the omax plateau edges (0.27 / 0.29) at margin 0.025:
# the fresh-seed run showed a single VARIATIONS no-match at 0.28; check
# whether the plateau edges avoid it without losing the LIVE gain.
# Usage: ./sweep-fresh2.sh  (run from tools/recognizer-test)
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
    "$KOTLINC" "$tmp" TestFresh.kt ShapeGen.kt SloppyGen.kt LiveGen.kt -include-runtime -d sweep.jar 2>/dev/null
    echo "=== FRESH $label (omax=$omax free=$free full=$full margin=$margin) ==="
    /home/hatch/jdk17/bin/java -jar sweep.jar 2>/dev/null | grep -E "VARIATIONS|FALSE MATCHES|TILT:|SLOPPY:|REV:|LIVE:"
    rm -f "$tmp" sweep.jar
}

run_combo "omax27-m025"  0.27 20 40 0.025
run_combo "omax29-m025"  0.29 20 40 0.025
