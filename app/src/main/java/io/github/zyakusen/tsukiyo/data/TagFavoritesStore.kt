package io.github.zyakusen.tsukiyo.data

import android.content.Context
import io.github.zyakusen.tsukiyo.util.FilterType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 收藏的筛选项：标签 / 社团 / 声优，各自按加入顺序保存。
 *
 * 只存**名字**：搜索用的 `SearchFilter.raw` 与显示用的 label 在这三类里都是同一个名字
 * （见 `SearchFilter(FilterType.TAG, name, name)`），所以不需要额外的 id/显示名映射。
 */
data class TagFavorites(
    val tags: List<String> = emptyList(),
    val circles: List<String> = emptyList(),
    val vas: List<String> = emptyList()
) {
    fun of(type: FilterType): List<String> = when (type) {
        FilterType.CIRCLE -> circles
        FilterType.VA -> vas
        else -> tags
    }

    fun contains(type: FilterType, name: String): Boolean = of(type).contains(name)
}

/**
 * 标签收藏的本地存储（SharedPreferences，换行分隔）。
 *
 * 三类里只有 TAG / CIRCLE / VA 可收藏，其它类型（时长/评分…）不参与。
 */
class TagFavoritesStore(context: Context) {

    private val prefs = context.getSharedPreferences("tag_favorites", Context.MODE_PRIVATE)

    private val _state = MutableStateFlow(
        TagFavorites(
            tags = readList(KEY_TAGS),
            circles = readList(KEY_CIRCLES),
            vas = readList(KEY_VAS)
        )
    )
    val state: StateFlow<TagFavorites> = _state.asStateFlow()

    fun isFavorite(type: FilterType, name: String): Boolean = _state.value.contains(type, name)

    /** 加入 / 移出收藏；返回操作后**是否处于已收藏**状态。 */
    fun toggle(type: FilterType, name: String): Boolean {
        if (name.isBlank()) return false
        val cur = _state.value
        val list = cur.of(type)
        val added = !list.contains(name)
        val next = if (added) list + name else list - name
        _state.value = when (type) {
            FilterType.CIRCLE -> cur.copy(circles = next)
            FilterType.VA -> cur.copy(vas = next)
            else -> cur.copy(tags = next)
        }
        prefs.edit().putString(keyOf(type), next.joinToString(SEP)).apply()
        return added
    }

    /** 该类型是否属于可收藏的三类之一。 */
    fun canFavorite(type: FilterType): Boolean =
        type == FilterType.TAG || type == FilterType.CIRCLE || type == FilterType.VA

    private fun readList(key: String): List<String> =
        prefs.getString(key, null)?.split(SEP)?.filter { it.isNotBlank() } ?: emptyList()

    private fun keyOf(type: FilterType) = when (type) {
        FilterType.CIRCLE -> KEY_CIRCLES
        FilterType.VA -> KEY_VAS
        else -> KEY_TAGS
    }

    private companion object {
        const val KEY_TAGS = "tags"
        const val KEY_CIRCLES = "circles"
        const val KEY_VAS = "vas"
        const val SEP = "\n"
    }
}
