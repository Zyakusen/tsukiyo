package io.github.zyakusen.tsukiyo.data

import io.github.zyakusen.tsukiyo.data.dao.HistoryDao
import io.github.zyakusen.tsukiyo.data.entity.HistoryItem
import io.github.zyakusen.tsukiyo.data.model.Work
import kotlinx.coroutines.flow.Flow

/**
 * 浏览历史：按时间倒序、去重，并限制最近 N 条。
 */
class HistoryStore(private val dao: HistoryDao) {

    fun observe(): Flow<List<HistoryItem>> = dao.observeAll()

    suspend fun record(work: Work) {
        val id = work.id ?: return
        dao.insert(
            HistoryItem(
                workId = id,
                title = work.title ?: "",
                circleName = work.circleName ?: "",
                coverUrl = work.coverUrl,
                nsfw = work.nsfw == true,
                viewedAt = System.currentTimeMillis()
            )
        )
        dao.trim(MAX_ENTRIES)
    }

    suspend fun clear() = dao.clear()

    companion object {
        const val MAX_ENTRIES = 200
    }
}
