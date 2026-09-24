package io.github.zyakusen.tsukiyo.data

import android.content.Context
import com.google.gson.reflect.TypeToken
import io.github.zyakusen.tsukiyo.data.api.NetworkModule
import io.github.zyakusen.tsukiyo.data.model.Track
import io.github.zyakusen.tsukiyo.data.model.Work
import java.io.File

/**
 * 作品详情（基本信息 + 音轨树）缓存，TTL 内复用、弱网/离线可回看。
 */
class WorkCache(context: Context) {

    private val gson = NetworkModule.gson
    private val dir = File(context.filesDir, "work_cache").apply { mkdirs() }
    private val ttlMs = 7L * 24 * 3600 * 1000

    private data class Entry(val work: Work, val tracks: List<Track>, val time: Long)

    private val type = object : TypeToken<Entry>() {}.type

    fun get(id: Long): Pair<Work, List<Track>>? {
        val f = File(dir, "$id.json")
        if (!f.exists()) return null
        return runCatching {
            val e = gson.fromJson<Entry>(f.readText(), type)
            if (System.currentTimeMillis() - e.time > ttlMs) null else (e.work to e.tracks)
        }.getOrNull()
    }

    fun put(id: Long, work: Work, tracks: List<Track>) {
        runCatching { File(dir, "$id.json").writeText(gson.toJson(Entry(work, tracks, System.currentTimeMillis()))) }
    }
}
