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
    MEDIA_PLAY_PAUSE
}

/** One saved gesture: a name, a normalized template, and the action it runs. */
data class Gesture(
    val id: Long = 0,
    val name: String,
    /** Multi-stroke template, serialized by Recognizer.strokesToString:
     *  strokes separated by "|", points inside a stroke are "x,y"
     *  separated by ";". Old single-stroke templates ("x,y;x,y;...") with
     *  no "|" load back as one stroke, so gestures recorded before
     *  multi-stroke support keep working. */
    val template: String,
    val actionType: ActionType,
    /** Action payload: package name for LAUNCH_APP, URL for OPEN_URL, empty otherwise. */
    val payload: String = ""
)

/**
 * Gesture library backed by plain SQLite (SQLiteOpenHelper).
 *
 * Note: this was originally specified as Room, but Room's annotation
 * processing needs Gradle/KSP, and Gradle cannot run in the build sandbox
 * used for v1.0.0 (its daemon protocol needs loopback TCP, which the sandbox
 * blocks). The storage behaviour is identical: a local "gestures" table with
 * the same columns, full CRUD, and a Flow of all gestures ordered by name.
 * If a Gradle build is ever used again, this file can go back to Room
 * without touching the UI, which only depends on GestureDao's API.
 */
class GestureDao(context: Context) {

    private val helper = object : SQLiteOpenHelper(
        context.applicationContext, "flick.db", null, 1
    ) {
        override fun onCreate(db: SQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE gestures (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    "name TEXT NOT NULL, " +
                    "template TEXT NOT NULL, " +
                    "actionType TEXT NOT NULL, " +
                    "payload TEXT NOT NULL DEFAULT '')"
            )
        }

        override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
            // v1 only; no migrations yet.
        }
    }

    private val scope = CoroutineScope(Dispatchers.IO)
    private val _all = MutableStateFlow<List<Gesture>>(emptyList())

    init {
        refresh()
    }

    /** Flow of all gestures, ordered by name, refreshed after every change. */
    fun observeAll(): Flow<List<Gesture>> = _all

    suspend fun getAll(): List<Gesture> = withContext(Dispatchers.IO) {
        readAll(helper.readableDatabase)
    }

    suspend fun getById(id: Long): Gesture? = withContext(Dispatchers.IO) {
        helper.readableDatabase.query(
            "gestures", null, "id = ?", arrayOf(id.toString()),
            null, null, null
        ).use { cursor ->
            if (cursor.moveToFirst()) rowToGesture(cursor) else null
        }
    }

    suspend fun insert(gesture: Gesture): Long = withContext(Dispatchers.IO) {
        val id = helper.writableDatabase.insert("gestures", null, gesture.toValues())
        refresh()
        id
    }

    suspend fun update(gesture: Gesture) = withContext(Dispatchers.IO) {
        helper.writableDatabase.update(
            "gestures", gesture.toValues(), "id = ?",
            arrayOf(gesture.id.toString())
        )
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
            _all.value = readAll(helper.readableDatabase)
        }
    }

    private fun readAll(db: SQLiteDatabase): List<Gesture> {
        db.query("gestures", null, null, null, null, null, "name ASC").use { cursor ->
            val out = ArrayList<Gesture>()
            while (cursor.moveToNext()) out.add(rowToGesture(cursor))
            return out
        }
    }

    private fun rowToGesture(cursor: android.database.Cursor): Gesture {
        return Gesture(
            id = cursor.getLong(cursor.getColumnIndexOrThrow("id")),
            name = cursor.getString(cursor.getColumnIndexOrThrow("name")),
            template = cursor.getString(cursor.getColumnIndexOrThrow("template")),
            actionType = ActionType.valueOf(
                cursor.getString(cursor.getColumnIndexOrThrow("actionType"))
            ),
            payload = cursor.getString(cursor.getColumnIndexOrThrow("payload"))
        )
    }

    private fun Gesture.toValues(): ContentValues {
        return ContentValues().apply {
            put("name", name)
            put("template", template)
            put("actionType", actionType.name)
            put("payload", payload)
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
