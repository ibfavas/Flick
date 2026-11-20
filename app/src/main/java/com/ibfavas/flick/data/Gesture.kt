package com.ibfavas.flick.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Types of actions a gesture can trigger. */
enum class ActionType {
    LAUNCH_APP,
    OPEN_URL,
    FLASHLIGHT_ON,
    FLASHLIGHT_OFF,
    OPEN_CAMERA,
    MEDIA_PLAY_PAUSE,
    /** Call a phone contact. Payload: "Name\nNumber". */
    CALL_CONTACT,
    /** Lock the screen via the accessibility service. Payload unused. */
    LOCK_SCREEN,
    /** Take a screenshot via the accessibility service. Payload unused. */
    TAKE_SCREENSHOT,
    /** Open the system Wi-Fi panel (Android 12+ cannot flip Wi-Fi silently). */
    TOGGLE_WIFI,
    /** Open the system Bluetooth panel (same platform limit as Wi-Fi). */
    TOGGLE_BLUETOOTH,
    /** Flip Do Not Disturb, needs notification-policy access. */
    TOGGLE_DND,
    CHAT_CONTACT
}

/** One saved gesture: a name, a normalized template, and the action it runs. */
data class Gesture(
    val id: Long = 0,
    val name: String,
    // Multi-stroke template, serialized by
    // Recognizer.strokesToString: strokes separated by "|",
    // points inside a stroke are "x,y" separated by ";". Old
    // single-stroke templates with no "|" load back as one
    // stroke, so gestures recorded before multi-stroke support
    // keep working.
    val template: String,
    val actionType: ActionType,
    /** Action payload: package name for LAUNCH_APP, URL for OPEN_URL, empty otherwise. */
    val payload: String = "",
    // Extra recorded samples of the same gesture: additional
    // strokesToString strings, one per sample, joined by "\n".
    // The recognizer matches against every sample and takes
    // the best score, so a gesture drawn a little differently
    // still matches. At most 3 samples in total (template + 2
    // extras). Empty for gestures recorded before multi-sample
    // support.
    val extraTemplates: String = "",
    // Whether the gesture runs from the pad. Disabled gestures
    // stay in the list, dimmed, but never match or run. Toggled
    // from the gesture detail sheet.
    val enabled: Boolean = true,
    val useCount: Long = 0,
    val learnedUses: Long = 0
) {
    /** All recorded samples of this gesture, first sample first. */
    fun allTemplates(): List<String> =
        (listOf(template) + extraTemplates.split("\n").filter { it.isNotBlank() })
            .take(MAX_SAMPLES)

    /** Number of recorded samples (1 for older gestures recorded before multi-sample support). */
    fun sampleCount(): Int = allTemplates().size

    /** Every sample as a recognizer template, all under this gesture's name. */
    fun multiTemplates(): List<com.ibfavas.flick.gesture.Recognizer.MultiTemplate> =
        allTemplates().map { sample ->
            com.ibfavas.flick.gesture.Recognizer.MultiTemplate(
                name,
                com.ibfavas.flick.gesture.Recognizer.strokesFromString(sample)
            )
        }

    companion object {
        /** Maximum samples per gesture: the first plus two extras. */
        const val MAX_SAMPLES = 3

        // A name no other gesture uses (case-insensitive,
        // ignoring the gesture with excludeId when renaming).
        // Duplicate names made recognition ambiguous: the pad
        // looked the matched gesture up by name, so the wrong
        // gesture could run. Returns the base name unchanged
        // when it is free, otherwise appends " 2", " 3", and
        // so on.
        fun uniqueName(base: String, existing: List<Gesture>, excludeId: Long = 0): String {
            val taken = existing.filter { it.id != excludeId }
                .map { it.name.trim().lowercase() }.toSet()
            val name = base.trim()
            if (name.lowercase() !in taken) return name
            var n = 2
            while ("$name $n".lowercase() in taken) n++
            return "$name $n"
        }
    }
}

class GestureDao(context: Context) {

    private val helper = object : SQLiteOpenHelper(
        context.applicationContext, "flick.db", null, 4
    ) {
        fun createHistory(db: SQLiteDatabase) {
            db.execSQL("CREATE TABLE usage (gestureId INTEGER NOT NULL, name TEXT NOT NULL, time INTEGER NOT NULL, recognized INTEGER NOT NULL)")
            db.execSQL("CREATE INDEX usage_gesture ON usage(gestureId)")
        }

        override fun onCreate(db: SQLiteDatabase) {
            createHistory(db)
            db.execSQL(
                "CREATE TABLE gestures (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    "name TEXT NOT NULL, " +
                    "template TEXT NOT NULL, " +
                    "actionType TEXT NOT NULL, " +
                    "payload TEXT NOT NULL DEFAULT '', " +
                    "extraTemplates TEXT NOT NULL DEFAULT '', " +
                    "enabled INTEGER NOT NULL DEFAULT 1)"
            )
        }

        override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
            if (oldVersion < 4) createHistory(db)
            // v2: extra recorded samples per gesture.
            if (oldVersion < 2) {
                db.execSQL(
                    "ALTER TABLE gestures ADD COLUMN extraTemplates TEXT NOT NULL DEFAULT ''"
                )
            }
            // v3: enable/disable per gesture from the detail sheet.
            if (oldVersion < 3) {
                db.execSQL(
                    "ALTER TABLE gestures ADD COLUMN enabled INTEGER NOT NULL DEFAULT 1"
                )
            }
        }
    }

    private val scope = CoroutineScope(Dispatchers.IO)
    private val _all = MutableStateFlow<List<Gesture>>(emptyList())

    data class Use(val name: String, val time: Long)

    private val _history = MutableStateFlow<List<Use>>(emptyList())
    val history: Flow<List<Use>> get() = _history

    fun recordUse(gesture: Gesture, recognized: Boolean) {
        scope.launch {
            helper.writableDatabase.insertOrThrow("usage", null, ContentValues().apply {
                put("gestureId", gesture.id)
                put("name", gesture.name)
                put("time", System.currentTimeMillis())
                put("recognized", if (recognized) 1 else 0)
            })
            refresh()
        }
    }

    init {
        refresh()
    }

    /** Flow of all gestures, ordered by name, refreshed after every change. */
    fun observeAll(): Flow<List<Gesture>> = _all

    suspend fun getAll(): List<Gesture> = withContext(Dispatchers.IO) {
        readAll(helper.readableDatabase)
    }

    suspend fun insert(gesture: Gesture): Long = withContext(Dispatchers.IO) {
        val id = helper.writableDatabase.insert("gestures", null, gesture.toValues())
        refresh()
        id
    }

    suspend fun importBackup(text: String): Backup.ImportResult = withContext(Dispatchers.IO) {
        val db = helper.writableDatabase
        db.beginTransaction()
        val result = try {
            val names = readAll(db).mapTo(mutableSetOf()) { it.name }
            val (gestures, report) = Backup.parse(text, names)
            for (gesture in gestures) db.insertOrThrow("gestures", null, gesture.toValues())
            db.setTransactionSuccessful()
            report
        } finally {
            db.endTransaction()
        }
        if (result.imported > 0) refresh()
        result
    }

    suspend fun update(gesture: Gesture) = withContext(Dispatchers.IO) {
        helper.writableDatabase.update(
            "gestures", gesture.toValues(), "id = ?",
            arrayOf(gesture.id.toString())
        )
        refresh()
    }

    suspend fun duplicate(gesture: Gesture) = withContext(Dispatchers.IO) {
        val db = helper.writableDatabase
        db.beginTransaction()
        try {
            val name = Gesture.uniqueName(gesture.name, readAll(db))
            db.insertOrThrow("gestures", null, gesture.copy(name = name).toValues())
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        refresh()
    }

    suspend fun restore(gesture: Gesture) = withContext(Dispatchers.IO) {
        val db = helper.writableDatabase
        db.beginTransaction()
        try {
            val name = Gesture.uniqueName(gesture.name, readAll(db))
            db.insertOrThrow("gestures", null, gesture.copy(name = name).toValues().apply { put("id", gesture.id) })
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        refresh()
    }

    suspend fun delete(gesture: Gesture) = withContext(Dispatchers.IO) {
        helper.writableDatabase.delete(
            "gestures", "id = ?", arrayOf(gesture.id.toString())
        )
        refresh()
    }

    private fun refresh() {
        scope.launch {
            synchronized(helper) {
                val db = helper.readableDatabase
                _all.value = readAll(db)
                _history.value = db.rawQuery("SELECT name,time FROM usage ORDER BY time DESC,rowid DESC LIMIT 100", null).use { rows ->
                    buildList { while (rows.moveToNext()) add(Use(rows.getString(0), rows.getLong(1))) }
                }
            }
        }
    }

    private fun readAll(db: SQLiteDatabase): List<Gesture> {
        db.rawQuery("""SELECT g.*, COUNT(u.gestureId) AS useCount, COALESCE(SUM(u.recognized),0) AS learnedUses
            FROM gestures g LEFT JOIN usage u ON g.id=u.gestureId
            WHERE g.actionType != 'RUN_FLOW' GROUP BY g.id ORDER BY g.name""", null).use { cursor ->
            val out = ArrayList<Gesture>()
            while (cursor.moveToNext()) out.add(rowToGesture(cursor))
            return out
        }
    }

    private fun rowToGesture(cursor: android.database.Cursor): Gesture {
        val extraIdx = cursor.getColumnIndex("extraTemplates")
        val enabledIdx = cursor.getColumnIndex("enabled")
        return Gesture(
            id = cursor.getLong(cursor.getColumnIndexOrThrow("id")),
            name = cursor.getString(cursor.getColumnIndexOrThrow("name")),
            template = cursor.getString(cursor.getColumnIndexOrThrow("template")),
            actionType = ActionType.valueOf(
                cursor.getString(cursor.getColumnIndexOrThrow("actionType"))
            ),
            useCount = cursor.getLong(cursor.getColumnIndexOrThrow("useCount")),
            learnedUses = cursor.getLong(cursor.getColumnIndexOrThrow("learnedUses")),
            payload = cursor.getString(cursor.getColumnIndexOrThrow("payload")),
            extraTemplates = if (extraIdx >= 0 && !cursor.isNull(extraIdx)) {
                cursor.getString(extraIdx)
            } else {
                ""
            },
            enabled = if (enabledIdx >= 0 && !cursor.isNull(enabledIdx)) {
                cursor.getInt(enabledIdx) != 0
            } else {
                true
            }
        )
    }

    private fun Gesture.toValues(): ContentValues {
        return ContentValues().apply {
            put("name", name)
            put("template", template)
            put("actionType", actionType.name)
            put("payload", payload)
            put("extraTemplates", extraTemplates)
            put("enabled", if (enabled) 1 else 0)
        }
    }
}

/** Simple singleton holder for the gesture library. */
object DatabaseProvider {
    @Volatile
    private var dao: GestureDao? = null

    fun get(context: Context): DatabaseProvider {
        if (dao == null) {
            synchronized(this) {
                if (dao == null) dao = GestureDao(context.applicationContext)
            }
        }
        return this
    }

    fun gestureDao(): GestureDao = dao
        ?: throw IllegalStateException("DatabaseProvider.get(context) must be called first")
}
