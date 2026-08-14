package com.oles.durga

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import org.json.JSONObject

/**
 * Local SQLite database. Every reading is stored here first (so nothing is lost
 * offline), then synced to the server. This is the app's own on-device database.
 */
class LocalDb(ctx: Context) : SQLiteOpenHelper(ctx.applicationContext, "durga.db", null, 1) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE queue (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "endpoint TEXT NOT NULL, " +   // e.g. api/save_vitals.php
                "payload TEXT NOT NULL, " +    // JSON string
                "created_at INTEGER NOT NULL, " +
                "synced INTEGER NOT NULL DEFAULT 0)"
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldV: Int, newV: Int) {
        db.execSQL("DROP TABLE IF EXISTS queue")
        onCreate(db)
    }

    /** Save a reading locally and return its row id. */
    fun enqueue(endpoint: String, payload: JSONObject): Long {
        val cv = ContentValues().apply {
            put("endpoint", endpoint)
            put("payload", payload.toString())
            put("created_at", System.currentTimeMillis())
            put("synced", 0)
        }
        return writableDatabase.insert("queue", null, cv)
    }

    data class Row(val id: Long, val endpoint: String, val payload: String)

    fun pending(limit: Int = 100): List<Row> {
        val out = ArrayList<Row>()
        readableDatabase.rawQuery(
            "SELECT id,endpoint,payload FROM queue WHERE synced=0 ORDER BY id ASC LIMIT ?",
            arrayOf(limit.toString())
        ).use { c ->
            while (c.moveToNext()) out.add(Row(c.getLong(0), c.getString(1), c.getString(2)))
        }
        return out
    }

    fun markSynced(id: Long) {
        writableDatabase.execSQL("UPDATE queue SET synced=1 WHERE id=?", arrayOf(id))
    }

    fun purgeSyncedOlderThan(ms: Long) {
        writableDatabase.execSQL(
            "DELETE FROM queue WHERE synced=1 AND created_at < ?",
            arrayOf((System.currentTimeMillis() - ms).toString())
        )
    }

    fun pendingCount(): Int {
        readableDatabase.rawQuery("SELECT COUNT(*) FROM queue WHERE synced=0", null).use { c ->
            return if (c.moveToFirst()) c.getInt(0) else 0
        }
    }
}
