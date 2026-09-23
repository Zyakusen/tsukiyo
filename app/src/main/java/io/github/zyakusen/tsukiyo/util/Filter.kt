package io.github.zyakusen.tsukiyo.util

enum class FilterType(val rawPrefix: String) {
    TAG("tag"),
    CIRCLE("circle"),
    VA("va"),
    DURATION("duration"),
    RATE("rate"),
    PRICE("price"),
    SELL("sell"),
    AGE("age"),
    LANG("lang")
}

data class SearchFilter(
    val type: FilterType,
    val name: String,
    val label: String,
    val isExclude: Boolean = false
) {
    val display: String get() = (if (isExclude) "排除 " else "") + label

    val raw: String get() = "\$" + (if (isExclude) "-" else "") + "${type.rawPrefix}:$name\$"

    fun toggleExclude(): SearchFilter = copy(isExclude = !isExclude)
}

/** 供其它页面（如作品详情长按标签）向搜索页传递预置筛选条件。 */
object SearchPreset {
    var pendingFilter: SearchFilter? = null
}
