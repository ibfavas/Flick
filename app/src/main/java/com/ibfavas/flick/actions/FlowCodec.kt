package com.ibfavas.flick.actions

import com.ibfavas.flick.data.ActionType
import org.json.JSONArray
import org.json.JSONObject

/**
 * A flow step: one leaf action inside a RUN_FLOW gesture. Flows never
 * contain another flow, so a step's type is always a leaf action.
 */
data class FlowStep(val type: ActionType, val payload: String = "")

/**
 * Encodes a flow (a list of steps) into the gesture payload string and
 * back. Plain org.json, which ships in the Android framework, no extra
 * dependency. Format: [{"type":"LAUNCH_APP","payload":"com.example"},
 * {"type":"FLASHLIGHT_ON","payload":""}]. Invalid entries are skipped
 * on decode instead of failing the whole flow.
 */
object FlowCodec {

    fun encode(steps: List<FlowStep>): String {
        val array = JSONArray()
        for (step in steps) {
            if (step.type == ActionType.RUN_FLOW) continue
            array.put(
                JSONObject()
                    .put("type", step.type.name)
                    .put("payload", step.payload)
            )
        }
        return array.toString()
    }

    fun decode(payload: String): List<FlowStep> {
        if (payload.isBlank()) return emptyList()
        return try {
            val array = JSONArray(payload)
            val out = ArrayList<FlowStep>()
            for (i in 0 until array.length()) {
                val obj = array.optJSONObject(i) ?: continue
                val type = try {
                    ActionType.valueOf(obj.optString("type"))
                } catch (e: IllegalArgumentException) {
                    continue
                }
                if (type == ActionType.RUN_FLOW) continue
                out.add(FlowStep(type, obj.optString("payload", "")))
            }
            out
        } catch (e: Exception) {
            emptyList()
        }
    }
}
