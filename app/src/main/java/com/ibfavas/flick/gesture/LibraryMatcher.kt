package com.ibfavas.flick.gesture

import com.ibfavas.flick.data.Gesture

/** A normal match always wins. Only a previously successful gesture can rescue a near miss. */
class LibraryMatcher(gestures: List<Gesture>) {
    private val owners = gestures.filter { it.enabled }.flatMap { gesture ->
        gesture.multiTemplates().map { it.copy(name = gesture.id.toString()) to gesture }
    }.toMap()
    val maxStrokeCount = owners.keys.maxOfOrNull { it.strokes.size } ?: 0
    private val prepared = Recognizer.prepareTemplates(owners.keys.toList())
    private val hasExperience = owners.values.any { it.learnedUses > 0 }

    private val openings = java.util.concurrent.ConcurrentHashMap<Int, List<Recognizer.PreparedTemplate>>()

    fun couldContinue(raw: List<List<StrokePoint>>, threshold: Float): Boolean {
        val strokes = raw.filter { it.size >= 2 }
        if (strokes.isEmpty() || strokes.size >= maxStrokeCount) return false
        val candidates = openings.computeIfAbsent(strokes.size) { Recognizer.prepareOpenings(owners.keys, it) }
        return Recognizer.resemblesOpening(strokes, candidates, threshold)
    }

    fun match(raw: List<List<StrokePoint>>, threshold: Float = Recognizer.DEFAULT_THRESHOLD): Pair<Gesture, Float>? {
        val normal = Recognizer.recognizeMultiPrepared(raw, prepared, threshold)
        if (normal != null) return owners[normal.source]?.let { it to normal.score }
        if (!hasExperience) return null
        // Compete against the entire library so the core ambiguity margin still applies.
        val candidate = Recognizer.recognizeMultiPrepared(raw, prepared, threshold - .025f) ?: return null
        val owner = owners[candidate.source] ?: return null
        val allowance = owner.learnedUses.coerceIn(0, 10) * .0025f
        return if (candidate.score >= threshold - allowance) owner to candidate.score else null
    }
}
