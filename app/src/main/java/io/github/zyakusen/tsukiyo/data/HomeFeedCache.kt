package io.github.zyakusen.tsukiyo.data

import android.content.Context
import com.google.gson.reflect.TypeToken
import io.github.zyakusen.tsukiyo.data.api.NetworkModule
import io.github.zyakusen.tsukiyo.data.model.Work
import java.io.File

/**
 * 首页「热门作品 / 推荐作品」两行的本地缓存。
 *
 * 目的**不是减少请求**，而是让首页首帧就有东西：
 *  * 冷启动时先拿上次的列表占位，请求回来再替换；
 *  * 避免"顶部两行后到 → 下面的作品整体下移、要上滑才知道加载好了"。
 *
 * 与 [WorkCache] 一样按 TTL 复用；只存列表本身，键是 "popular" / "recommend"。
 */
class HomeFeedCache(context: Context) {

    private val gson = NetworkModule.gson
    private val dir = File(context.filesDir, "home_feed").apply { mkdirs() }
    private val ttlMs = 7L * 24 * 3600 * 1000

    private data class Entry(val works: List<Work>, val time: Long)

    private val type = object : TypeToken<Entry>() {}.type

    fun get(key: String): List<Work> {
        val f = File(dir, "$key.json")
        if (!f.exists()) return emptyList()
        return runCatching {
            val e = gson.fromJson<Entry>(f.readText(), type)
            if (e == null || System.currentTimeMillis() - e.time > ttlMs) emptyList() else e.works
        }.getOrDefault(emptyList())
    }

    fun put(key: String, works: List<Work>) {
        if (works.isEmpty()) return
        runCatching { File(dir, "$key.json").writeText(gson.toJson(Entry(works, System.currentTimeMillis()))) }
    }

    companion object {
        const val KEY_POPULAR = "popular"
        const val KEY_RECOMMEND = "recommend"
    }
}
