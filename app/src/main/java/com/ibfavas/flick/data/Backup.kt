package com.ibfavas.flick.data

import org.json.JSONArray
import org.json.JSONObject

/*
 * Backup and restore for the gesture library.
 *
 * Format, one JSON object:
 * {"version":1,"app":"Flick","gestures":[
 *   {"name":"...","template":"...","extraTemplates":"...","actionType":"LAUNCH_APP","payload":"...","enabled":true}
 * ]}
 *
 * "enabled" is optional on import: a backup written
 * before it existed imports as enabled.
 *
 * Import is defensive: the file is validated before
 * anything is written, invalid entries are skipped and
 * counted, and gestures whose name already exists
 * (case-insensitive, trimmed) are skipped, never
 * duplicated. A bad file returns an error instead of
 * crashing.
 */
object Backup {

    const val CURRENT_VERSION = 1

    data class ImportResult(
        val imported: Int,
        val skippedDuplicates: Int,
        val skippedInvalid: Int,
        val error: String? = null
    )

    fun export(gestures: List<Gesture>): String {
        val array = JSONArray()
        for (gesture in gestures) {
            array.put(
                JSONObject()
                    .put("name", gesture.name)
                    .put("template", gesture.template)
                    .put("extraTemplates", gesture.extraTemplates)
                    .put("actionType", gesture.actionType.name)
                    .put("payload", gesture.payload)
                    .put("enabled", gesture.enabled)
            )
        }
        return JSONObject()
            .put("version", CURRENT_VERSION)
            .put("app", "Flick")
            .put("gestures", array)
            .toString(2)
    }

    /**
     * Parse and validate a backup file. Returns the gestures to import
     * plus counts of what was skipped, or an error if the file is not
     * a Flick backup at all. Nothing is written here; the caller
     * inserts the returned gestures.
     */
    fun parse(text: String, existingNames: Set<String>): Pair<List<Gesture>, ImportResult> {
        val existing = existingNames.map { it.trim().lowercase() }.toSet()
        val seen = existing.toMutableSet()
        val out = ArrayList<Gesture>()
        var duplicates = 0
        var invalid = 0
        try {
            val root = JSONObject(text)
            if (root.optString("app") != "Flick") {
                return emptyList<Gesture>() to ImportResult(
                    0, 0, 0, "Not a Flick backup file"
                )
            }
            val array = root.optJSONArray("gestures")
                ?: return emptyList<Gesture>() to ImportResult(
                    0, 0, 0, "Backup has no gestures"
                )
            for (i in 0 until array.length()) {
                val obj = array.optJSONObject(i)
                if (obj == null) {
                    invalid++
                    continue
                }
                val name = obj.optString("name").trim()
                val template = obj.optString("template")
                val typeName = obj.optString("actionType")
                val type = try {
                    ActionType.valueOf(typeName)
                } catch (e: IllegalArgumentException) {
                    null
                }
                if (name.isBlank() || template.isBlank() || type == null) {
                    invalid++
                    continue
                }
                if (!seen.add(name.lowercase())) {
                    duplicates++
                    continue
                }
                out.add(
                    Gesture(
                        name = name,
                        template = template,
                        actionType = type,
                        payload = obj.optString("payload", ""),
                        extraTemplates = obj.optString("extraTemplates", ""),
                        enabled = obj.optBoolean("enabled", true)
                    )
                )
            }
        } catch (e: Exception) {
            return emptyList<Gesture>() to ImportResult(
                0, 0, 0, "Could not read this file: ${e.message ?: "bad format"}"
            )
        }
        return out to ImportResult(out.size, duplicates, invalid)
    }
}
