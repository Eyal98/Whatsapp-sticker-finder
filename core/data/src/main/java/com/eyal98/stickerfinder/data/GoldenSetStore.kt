package com.eyal98.stickerfinder.data

import android.content.Context
import com.eyal98.stickerfinder.search.GoldenQuery
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

/**
 * The labelled test searches, kept in app-private storage (excluded from backups). They name
 * stickers only by image hash, so an exported file holds the test searches' text but no images.
 */
class GoldenSetStore(context: Context) {

    private val file = File(context.applicationContext.noBackupFilesDir, "eval/golden.json")

    fun load(): List<GoldenQuery> =
        try {
            if (file.isFile) file.inputStream().use { read(it) } else emptyList()
        } catch (e: IOException) {
            emptyList()
        } catch (e: JSONException) {
            emptyList()
        }

    fun save(queries: List<GoldenQuery>) {
        file.parentFile?.mkdirs()
        val tmp = File(file.path + ".tmp")
        tmp.outputStream().use { write(queries, it) }
        if (!tmp.renameTo(file)) throw IOException("Could not save test searches")
    }

    companion object {
        private const val VERSION = 1

        fun write(queries: List<GoldenQuery>, out: OutputStream) {
            val json = JSONObject()
                .put("version", VERSION)
                .put(
                    "queries",
                    JSONArray(
                        queries.map { q ->
                            JSONObject()
                                .put("id", q.id)
                                .put("text", q.text)
                                .put("relevant", JSONArray(q.relevant.sorted()))
                        },
                    ),
                )
            out.write(json.toString(2).toByteArray(Charsets.UTF_8))
        }

        /** @throws JSONException for a file that isn't a test-search export. */
        fun read(input: InputStream): List<GoldenQuery> {
            val json = JSONObject(input.readBytes().toString(Charsets.UTF_8))
            val array = json.getJSONArray("queries")
            return (0 until array.length()).map { i ->
                val q = array.getJSONObject(i)
                val relevant = q.getJSONArray("relevant")
                GoldenQuery(
                    id = q.getString("id"),
                    text = q.getString("text"),
                    relevant = (0 until relevant.length()).mapTo(LinkedHashSet()) { relevant.getString(it) },
                )
            }
        }
    }
}
