package com.music.konnekt.data

import android.content.Context
import com.music.konnekt.data.model.HomeShelf
import com.music.konnekt.data.model.ShelfItem
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest

/** A bounded first paint, isolated by account/channel and language; network still refreshes it. */
internal class HomeFeedCache(context: Context) {
    private val directory = File(context.cacheDir, "home-feed")
    private fun file(identity: String): File {
        val key = MessageDigest.getInstance("SHA-256").digest(identity.toByteArray())
            .joinToString("") { "%02x".format(it) }
        return File(directory, "$key.json")
    }

    fun read(identity: String): List<HomeShelf> = runCatching {
        val file = file(identity)
        if (!file.exists() || System.currentTimeMillis() - file.lastModified() > 12 * 60 * 60 * 1000L) return emptyList()
        val array = JSONArray(file.readText())
        (0 until array.length()).map { index ->
            val shelf = array.getJSONObject(index)
            val items = shelf.getJSONArray("items")
            HomeShelf(shelf.getString("title"), (0 until items.length()).map { itemIndex ->
                val item = items.getJSONObject(itemIndex)
                fun optional(key: String) = item.optString(key).takeIf { it.isNotEmpty() }
                ShelfItem(item.getString("title"), item.optString("subtitle"), optional("art"), optional("video"), optional("browse"))
            }, shelf.optString("subtitle"), shelf.optString("more").takeIf(String::isNotEmpty),
                shelf.optString("params").takeIf(String::isNotEmpty))
        }
    }.getOrDefault(emptyList())

    fun write(identity: String, shelves: List<HomeShelf>) = runCatching {
        directory.mkdirs()
        // Bound disk use when several Google/YouTube channels are used on one device.
        directory.listFiles()?.sortedByDescending { it.lastModified() }?.drop(7)?.forEach { it.delete() }
        val array = JSONArray()
        shelves.take(12).forEach { shelf ->
            val items = JSONArray()
            shelf.items.take(24).forEach { item ->
                items.put(JSONObject().put("title", item.title).put("subtitle", item.subtitle)
                    .put("art", item.thumbnailUrl.orEmpty()).put("video", item.videoId.orEmpty())
                    .put("browse", item.browseId.orEmpty()))
            }
            array.put(JSONObject().put("title", shelf.title).put("subtitle", shelf.subtitle)
                .put("more", shelf.moreBrowseId.orEmpty()).put("params", shelf.moreParams.orEmpty()).put("items", items))
        }
        val target = file(identity)
        val partial = File(directory, "${target.name}.part")
        partial.writeText(array.toString())
        partial.renameTo(target)
    }
}
