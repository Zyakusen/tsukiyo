package io.github.zyakusen.tsukiyo.data

import android.content.Context
import com.google.gson.reflect.TypeToken
import io.github.zyakusen.tsukiyo.data.api.NetworkModule
import io.github.zyakusen.tsukiyo.data.model.Circle
import io.github.zyakusen.tsukiyo.data.model.Tag
import io.github.zyakusen.tsukiyo.data.model.Va
import java.io.File

/**
 * 静态元数据（标签/社团/声优）缓存，TTL 内直接复用、离线时回退旧缓存。
 */
class MetadataCache(context: Context) {

    private val gson = NetworkModule.gson
    private val file = File(context.filesDir, "metadata_cache.json")
    private val ttlMs = 7L * 24 * 3600 * 1000

    private data class Entry<T>(val time: Long = 0L, val data: T? = null)
    private data class CacheData(
        val tags: Entry<List<Tag>>? = null,
        val circles: Entry<List<Circle>>? = null,
        val vas: Entry<List<Va>>? = null
    )

    private val type = object : TypeToken<CacheData>() {}.type
    private var mem: CacheData = load()

    private fun load(): CacheData = runCatching {
        gson.fromJson<CacheData>(file.readText(), type)
    }.getOrNull() ?: CacheData()

    private fun save() = runCatching { file.writeText(gson.toJson(mem)) }

    private fun <T> fresh(entry: Entry<T>?): T? =
        entry?.data?.takeIf { System.currentTimeMillis() - entry.time < ttlMs }

    fun freshTags(): List<Tag>? = fresh(mem.tags)
    fun freshCircles(): List<Circle>? = fresh(mem.circles)
    fun freshVas(): List<Va>? = fresh(mem.vas)

    fun staleTags(): List<Tag>? = mem.tags?.data
    fun staleCircles(): List<Circle>? = mem.circles?.data
    fun staleVas(): List<Va>? = mem.vas?.data

    fun putTags(data: List<Tag>) { mem = mem.copy(tags = Entry(System.currentTimeMillis(), data)); save() }
    fun putCircles(data: List<Circle>) { mem = mem.copy(circles = Entry(System.currentTimeMillis(), data)); save() }
    fun putVas(data: List<Va>) { mem = mem.copy(vas = Entry(System.currentTimeMillis(), data)); save() }
}
