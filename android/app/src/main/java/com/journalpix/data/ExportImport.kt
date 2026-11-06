package com.journalpix.data

import android.content.Context
import android.net.Uri
import android.util.Log
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * Export/import the whole journal (trips, places, photos metadata + image files)
 * as a single ZIP archive: "store.json" + "photos/<file>" entries.
 */
object ExportImport {
    private const val TAG = "JPXEI"

    /** Export the current offline store + all photo files to [outUri]. Returns true on success. */
    fun exportToZip(context: Context, outUri: Uri): Boolean {
        Log.i(TAG, "exportToZip: outUri=$outUri")
        return try {
            val photos = OfflineStore.photosDir(context).listFiles().orEmpty()
            context.contentResolver.openOutputStream(outUri)?.use { out ->
                ZipOutputStream(out).use { zip ->
                    zip.putNextEntry(ZipEntry("store.json"))
                    zip.write(OfflineStore.snapshotJson().toByteArray())
                    zip.closeEntry()
                    photos.forEach { f ->
                        zip.putNextEntry(ZipEntry("photos/${f.name}"))
                        f.inputStream().use { it.copyTo(zip) }
                        zip.closeEntry()
                    }
                }
            } ?: run {
                Log.e(TAG, "exportToZip: could not open output stream")
                return false
            }
            Log.i(TAG, "exportToZip: wrote ${photos.size} photo files + store.json")
            true
        } catch (e: Exception) {
            Log.e(TAG, "exportToZip: failed: ${e.message}", e)
            false
        }
    }

    /** Import a journal ZIP from [inUri], replacing the current store. Returns true on success. */
    fun importFromZip(context: Context, inUri: Uri): Boolean {
        Log.i(TAG, "importFromZip: inUri=$inUri")
        return try {
            var storeJson: String? = null
            val photoFiles = mutableMapOf<String, ByteArray>()
            context.contentResolver.openInputStream(inUri)?.use { input ->
                ZipInputStream(input).use { zip ->
                    var entry = zip.nextEntry
                    while (entry != null) {
                        if (entry.name == "store.json") {
                            storeJson = zip.readBytes().toString(Charsets.UTF_8)
                        } else if (entry.name.startsWith("photos/")) {
                            photoFiles[entry.name.removePrefix("photos/")] = zip.readBytes()
                        }
                        zip.closeEntry()
                        entry = zip.nextEntry
                    }
                }
            }
            val json = storeJson
            if (json == null) {
                Log.e(TAG, "importFromZip: no store.json in archive")
                return false
            }
            // Restore metadata first (atomic in-memory swap + persist), then copy photos
            if (!OfflineStore.restoreFromJson(json)) {
                Log.e(TAG, "importFromZip: store.json parse failed — aborting, photos not touched")
                return false
            }
            val photosDir = OfflineStore.photosDir(context)
            photoFiles.forEach { (name, bytes) ->
                File(photosDir, name).writeBytes(bytes)
            }
            Log.i(TAG, "importFromZip: imported ${photoFiles.size} photo files")
            true
        } catch (e: Exception) {
            Log.e(TAG, "importFromZip: failed: ${e.message}", e)
            false
        }
    }
}
