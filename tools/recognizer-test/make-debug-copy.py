#!/usr/bin/env python3
"""Make a debug copy of Recognizer.kt with a debugMatch() entry point
that reports per-pass per-name scores. Probe-only; the shipped file
is never modified."""
import re
import sys

src = "../../app/src/main/java/com/ibfavas/flick/gesture/Recognizer.kt"
with open(src) as f:
    text = f.read()

# 1. Add debug state + entry point as OBJECT MEMBERS, right before
# recognizeMultiPrepared (not inside its body).
anchor = """    fun recognizeMultiPrepared("""
assert anchor in text
debug_code = """    /** Probe-only debug state: one entry per scoring pass. */
    data class PassDebug(val open: Boolean, val perName: Map<String, Float>, val best: Pair<String?, Float>)
    private val debugPasses = mutableListOf<PassDebug>()

    /** Probe-only: run the shipped two-pass matcher and report what each pass saw. */
    fun debugMatch(
        rawStrokes: List<List<StrokePoint>>,
        prepared: List<PreparedTemplate>,
        threshold: Float = DEFAULT_THRESHOLD
    ): Pair<Match?, List<PassDebug>> {
        debugPasses.clear()
        val m = recognizeMultiPrepared(rawStrokes, prepared, threshold)
        return m to debugPasses.toList()
    }

    /** Probe-only: run BOTH scoring passes unconditionally and report
     * what each pass saw. The shipped matcher only runs pass 2 when
     * pass 1 finds no match, so this is the only way to see whether
     * pass 2 would have disagreed with a pass 1 match. */
    fun debugBoth(
        rawStrokes: List<List<StrokePoint>>,
        prepared: List<PreparedTemplate>,
        threshold: Float = DEFAULT_THRESHOLD
    ): List<PassDebug> {
        debugPasses.clear()
        val usable = rawStrokes.filter { it.size >= 2 }
        if (usable.isEmpty() || prepared.isEmpty()) return emptyList()
        matchPrepared(usable, prepared, threshold, openDtw = false)
        matchPrepared(usable, prepared, threshold, openDtw = true)
        return debugPasses.toList()
    }

""" + anchor
text = text.replace(anchor, debug_code, 1)

# 2. Record each pass inside matchPrepared, just before the threshold check.
anchor2 = """        if (bestName == null || bestScore < threshold) return null"""
assert anchor2 in text
text = text.replace(
    anchor2,
    """        debugPasses.add(PassDebug(openDtw, perName.toMap(), bestName to bestScore))
""" + anchor2,
    1,
)

with open("DebugRecognizer.kt", "w") as f:
    f.write(text)
print("wrote DebugRecognizer.kt")
