package io.github.zyakusen.tsukiyo.data

import io.github.zyakusen.tsukiyo.data.dao.ReviewDao
import io.github.zyakusen.tsukiyo.data.entity.WorkReview

/**
 * 本地保存用户对作品的评分/评论/进度，避免重启后（服务端不随详情返回）丢失显示。
 */
class ReviewStore(private val dao: ReviewDao) {

    suspend fun get(workId: Long): WorkReview? = dao.get(workId)

    suspend fun saveRating(workId: Long, rating: Double?, reviewText: String?) {
        val cur = dao.get(workId)
        dao.insert(WorkReview(workId, rating, reviewText, cur?.progress))
    }

    suspend fun saveProgress(workId: Long, progress: String?) {
        val cur = dao.get(workId)
        dao.insert(WorkReview(workId, cur?.rating, cur?.reviewText, progress))
    }

    suspend fun saveAll(workId: Long, rating: Double?, reviewText: String?, progress: String?) {
        val cur = dao.get(workId)
        dao.insert(
            WorkReview(
                workId,
                rating,
                reviewText?.takeIf { it.isNotBlank() } ?: cur?.reviewText,
                progress
            )
        )
    }
}
