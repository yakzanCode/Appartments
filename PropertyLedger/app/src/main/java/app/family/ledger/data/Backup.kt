package app.family.ledger.data

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.exifinterface.media.ExifInterface
import androidx.sqlite.db.SupportSQLiteDatabase
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.InputStream
import java.time.LocalDateTime
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/** Local photo storage. Nothing here ever leaves the device unless the user shares a backup. */
object PhotoStore {
    private val dirs = mapOf(
        "buildings" to "properties", "units" to "units", "tenants" to "tenants",
        "maintenance" to "maintenance", "expenses" to "expenses"
    )

    fun save(ctx: Context, uri: Uri, ownerType: String): String? {
        val sub = dirs[ownerType] ?: "other"
        val rel = "photos/$sub/${newId()}.jpg"
        val out = File(ctx.filesDir, rel)
        out.parentFile?.mkdirs()
        val resolver = ctx.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) } ?: return null
        val maxSide = 1600
        var sample = 1
        while (bounds.outWidth / sample > maxSide * 2 || bounds.outHeight / sample > maxSide * 2) sample *= 2
        var bmp = resolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
        } ?: return null
        val rotation = try {
            resolver.openInputStream(uri)?.use { s ->
                when (ExifInterface(s).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                    ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                    ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                    ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                    else -> 0f
                }
            } ?: 0f
        } catch (e: Exception) { 0f }
        val scale = maxSide.toFloat() / maxOf(bmp.width, bmp.height)
        if (rotation != 0f || scale < 1f) {
            val m = Matrix()
            if (scale < 1f) m.postScale(scale, scale)
            if (rotation != 0f) m.postRotate(rotation)
            bmp = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, m, true)
        }
        out.outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 82, it) }
        return rel
    }

    fun delete(ctx: Context, rel: String) {
        if (rel.isBlank()) return
        File(ctx.filesDir, rel).delete()
    }

    /** Small thumbnail for lists. Never loads the full image. */
    fun thumb(ctx: Context, rel: String, size: Int): Bitmap? {
        val f = File(ctx.filesDir, rel)
        if (!f.exists()) return null
        val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(f.path, o)
        var s = 1
        while (o.outWidth / (s * 2) >= size && o.outHeight / (s * 2) >= size) s *= 2
        return BitmapFactory.decodeFile(f.path, BitmapFactory.Options().apply { inSampleSize = s })
    }
}

data class BackupInfo(
    val created: String, val dbVersion: Int, val buildings: Int, val units: Int, val tenants: Int,
    val financialRecords: Int, val photoRows: Int, val photoFiles: Int
)

object Backup {
    private fun stamp() = java.time.LocalDate.now().toString()

    private fun dumpTable(sdb: SupportSQLiteDatabase, table: String): JSONArray {
        val arr = JSONArray()
        sdb.query("SELECT * FROM $table").use { c ->
            while (c.moveToNext()) {
                val o = JSONObject()
                for (i in 0 until c.columnCount) {
                    val n = c.getColumnName(i)
                    when (c.getType(i)) {
                        Cursor.FIELD_TYPE_NULL -> o.put(n, JSONObject.NULL)
                        Cursor.FIELD_TYPE_INTEGER -> o.put(n, c.getLong(i))
                        Cursor.FIELD_TYPE_FLOAT -> o.put(n, c.getDouble(i))
                        else -> o.put(n, c.getString(i))
                    }
                }
                arr.put(o)
            }
        }
        return arr
    }

    fun buildJson(db: AppDatabase): JSONObject {
        val sdb = db.openHelper.readableDatabase
        val tables = JSONObject()
        for (t in TABLES) tables.put(t, dumpTable(sdb, t))
        return JSONObject()
            .put("app", "property-ledger")
            .put("dbVersion", DB_VERSION)
            .put("created", LocalDateTime.now().toString())
            .put("tables", tables)
    }

    private fun exportDir(ctx: Context) = File(ctx.cacheDir, "exports").apply { mkdirs() }

    fun exportZip(ctx: Context, db: AppDatabase, withPhotos: Boolean): File {
        val name = if (withPhotos) "property_full_backup_${stamp()}.zip" else "property_data_backup_${stamp()}.zip"
        val file = File(exportDir(ctx), name)
        ZipOutputStream(file.outputStream().buffered()).use { z ->
            z.putNextEntry(ZipEntry("backup/database.json"))
            z.write(buildJson(db).toString().toByteArray(Charsets.UTF_8))
            z.closeEntry()
            if (withPhotos) {
                val root = File(ctx.filesDir, "photos")
                root.walkTopDown().filter { it.isFile }.forEach { f ->
                    val rel = f.relativeTo(ctx.filesDir).path.replace('\\', '/')
                    z.putNextEntry(ZipEntry("backup/$rel"))
                    f.inputStream().use { it.copyTo(z) }
                    z.closeEntry()
                }
            }
        }
        return file
    }

    fun exportCsvZip(ctx: Context, db: AppDatabase): File {
        val file = File(exportDir(ctx), "property_csv_${stamp()}.zip")
        val sdb = db.openHelper.readableDatabase
        fun esc(s: String) = "\"" + s.replace("\"", "\"\"") + "\""
        ZipOutputStream(file.outputStream().buffered()).use { z ->
            for (t in TABLES) {
                z.putNextEntry(ZipEntry("csv/$t.csv"))
                val sb = StringBuilder("﻿") // BOM so Excel reads Arabic correctly
                sdb.query("SELECT * FROM $t").use { c ->
                    sb.append((0 until c.columnCount).joinToString(",") { esc(c.getColumnName(it)) }).append("\r\n")
                    while (c.moveToNext()) {
                        sb.append((0 until c.columnCount).joinToString(",") { i ->
                            if (c.getType(i) == Cursor.FIELD_TYPE_NULL) "" else esc(c.getString(i) ?: "")
                        }).append("\r\n")
                    }
                }
                z.write(sb.toString().toByteArray(Charsets.UTF_8))
                z.closeEntry()
            }
        }
        return file
    }

    fun shareUri(ctx: Context, file: File): Uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.files", file)

    private fun readJson(ctx: Context, uri: Uri): Pair<JSONObject, Int> {
        var json: JSONObject? = null
        var photoFiles = 0
        ctx.contentResolver.openInputStream(uri)?.use { ins ->
            ZipInputStream(ins).use { z ->
                var e = z.nextEntry
                while (e != null) {
                    val n = e.name
                    if (n == "backup/database.json" || n == "database.json") {
                        json = JSONObject(String(z.readBytes(), Charsets.UTF_8))
                    } else if (n.startsWith("backup/photos/") && !e.isDirectory) photoFiles++
                    e = z.nextEntry
                }
            }
        }
        return (json ?: throw IllegalArgumentException("Not a valid backup file")) to photoFiles
    }

    fun inspect(ctx: Context, uri: Uri): BackupInfo {
        val (j, photoFiles) = readJson(ctx, uri)
        val t = j.getJSONObject("tables")
        fun n(name: String) = t.optJSONArray(name)?.length() ?: 0
        return BackupInfo(
            created = j.optString("created"), dbVersion = j.optInt("dbVersion", 0),
            buildings = n("buildings"), units = n("units"), tenants = n("tenants"),
            financialRecords = n("rent_obligations") + n("payments") + n("debts") + n("handovers") + n("expenses"),
            photoRows = n("photos"), photoFiles = photoFiles
        )
    }

    /** Replaces the local database with the backup. A safety copy of the current data is written first. */
    fun restoreReplace(ctx: Context, db: AppDatabase, uri: Uri) {
        val (j, photoFiles) = readJson(ctx, uri)
        if (j.optInt("dbVersion", 0) > DB_VERSION) throw IllegalArgumentException("Backup is from a newer version of the app")

        // 1. never silently destroy: keep a safety copy of the current state
        val safety = File(ctx.filesDir, "safety").apply { mkdirs() }
        exportZip(ctx, db, true).copyTo(File(safety, "before_restore_${System.currentTimeMillis()}.zip"), overwrite = true)

        // 2. photos
        if (photoFiles > 0) extractPhotos(ctx, uri)

        // 3. database
        val tables = j.getJSONObject("tables")
        val sdb = db.openHelper.writableDatabase
        sdb.execSQL("PRAGMA foreign_keys=OFF")
        sdb.beginTransaction()
        try {
            for (t in TABLES.reversed()) sdb.execSQL("DELETE FROM $t")
            for (t in TABLES) {
                val rows = tables.optJSONArray(t) ?: continue
                val cols = HashSet<String>()
                sdb.query("PRAGMA table_info($t)").use { while (it.moveToNext()) cols.add(it.getString(1)) }
                for (i in 0 until rows.length()) {
                    val o = rows.getJSONObject(i)
                    val cv = ContentValues()
                    for (k in o.keys()) {
                        if (k !in cols) continue
                        val v = o.get(k)
                        when {
                            v === JSONObject.NULL -> cv.putNull(k)
                            v is Int -> cv.put(k, v)
                            v is Long -> cv.put(k, v)
                            v is Double -> cv.put(k, v)
                            v is Boolean -> cv.put(k, if (v) 1 else 0)
                            else -> cv.put(k, v.toString())
                        }
                    }
                    sdb.insert(t, SQLiteDatabase.CONFLICT_REPLACE, cv)
                }
            }
            sdb.setTransactionSuccessful()
        } finally {
            sdb.endTransaction()
            sdb.execSQL("PRAGMA foreign_keys=ON")
        }
        db.invalidationTracker.refreshVersionsAsync()
    }

    private fun extractPhotos(ctx: Context, uri: Uri) {
        val root = File(ctx.filesDir, "photos").canonicalFile
        ctx.contentResolver.openInputStream(uri)?.use { ins ->
            ZipInputStream(ins).use { z ->
                var e = z.nextEntry
                while (e != null) {
                    if (e.name.startsWith("backup/photos/") && !e.isDirectory) {
                        val target = File(ctx.filesDir, e.name.removePrefix("backup/")).canonicalFile
                        if (target.path.startsWith(root.path + File.separator)) { // zip-slip guard
                            target.parentFile?.mkdirs()
                            target.outputStream().use { z.copyTo(it) }
                        }
                    }
                    e = z.nextEntry
                }
            }
        }
    }
}
