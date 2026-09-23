package io.github.zyakusen.tsukiyo.data.model

import com.google.gson.annotations.SerializedName

// ---------- 认证 ----------

data class AuthUser(
    @SerializedName("loggedIn") val loggedIn: Boolean?,
    @SerializedName("name") val name: String?,
    @SerializedName("group") val group: String?,
    @SerializedName("email") val email: String?,
    @SerializedName("recommenderUuid") val recommenderUuid: String?
)

data class AuthResponse(
    @SerializedName("user") val user: AuthUser?,
    @SerializedName("token") val token: String?
)

// ---------- 分页 ----------

data class Pagination(
    @SerializedName("currentPage") val currentPage: Int?,
    @SerializedName("pageSize") val pageSize: Int?,
    @SerializedName("totalCount") val totalCount: Int?
)

data class WorksResponse(
    @SerializedName("works") val works: List<Work>?,
    @SerializedName("pagination") val pagination: Pagination?
)

// ---------- 作品 ----------

data class Work(
    @SerializedName("id") val id: Long?,
    @SerializedName("title") val title: String?,
    @SerializedName("circle_id") val circleId: Long?,
    @SerializedName("name") val circleName: String?,
    @SerializedName("nsfw") val nsfw: Boolean?,
    @SerializedName("release") val release: String?,
    @SerializedName("dl_count") val dlCount: Int?,
    @SerializedName("price") val price: Int?,
    @SerializedName("review_count") val reviewCount: Int?,
    @SerializedName("rate_count") val rateCount: Int?,
    @SerializedName("rate_average_2dp") val rateAverage2dp: Double?,
    @SerializedName("rate_count_detail") val rateCountDetail: List<RateCount>?,
    @SerializedName("rank") val rank: List<Rank>?,
    @SerializedName("has_subtitle") val hasSubtitle: Boolean?,
    @SerializedName("create_date") val createDate: String?,
    @SerializedName("vas") val vas: List<Va>?,
    @SerializedName("tags") val tags: List<Tag>?,
    @SerializedName("language_editions") val languageEditions: List<LanguageEdition>?,
    @SerializedName("other_language_editions_in_db") val otherLanguageEditionsInDb: List<LanguageEditionInDb>?,
    @SerializedName("work_attributes") val workAttributes: String?,
    @SerializedName("age_category_string") val ageCategoryString: String?,
    @SerializedName("duration") val duration: Long?,
    @SerializedName("source_type") val sourceType: String?,
    @SerializedName("source_id") val sourceId: String?,
    @SerializedName("source_url") val sourceUrl: String?,
    @SerializedName("userRating") val userRating: Double?,
    @SerializedName("review_text") val reviewText: String?,
    @SerializedName("progress") val progress: String?,
    @SerializedName("updated_at") val updatedAt: String?,
    @SerializedName("user_name") val reviewUserName: String?,
    @SerializedName("circle") val circle: Circle?,
    @SerializedName("samCoverUrl") val samCoverUrl: String?,
    @SerializedName("thumbnailCoverUrl") val thumbnailCoverUrl: String?,
    @SerializedName("mainCoverUrl") val mainCoverUrl: String?
) {
    val coverUrl: String?
        get() = mainCoverUrl ?: samCoverUrl ?: thumbnailCoverUrl

    val rjCode: String?
        get() = sourceId
}

data class RateCount(
    @SerializedName("review_point") val reviewPoint: Int?,
    @SerializedName("count") val count: Int?,
    @SerializedName("ratio") val ratio: Int?
)

data class Rank(
    @SerializedName("term") val term: String?,
    @SerializedName("category") val category: String?,
    @SerializedName("rank") val rank: Int?,
    @SerializedName("rank_date") val rankDate: String?
)

data class LanguageEdition(
    @SerializedName("lang") val lang: String?,
    @SerializedName("label") val label: String?,
    @SerializedName("workno") val workno: String?,
    @SerializedName("edition_id") val editionId: Long?,
    @SerializedName("edition_type") val editionType: String?,
    @SerializedName("display_order") val displayOrder: Int?
)

/** 其它语言版本（已在库中、可直接跳转的作品）。 */
data class LanguageEditionInDb(
    @SerializedName("id") val id: Long?,
    @SerializedName("lang") val lang: String?,
    @SerializedName("title") val title: String?,
    @SerializedName("source_id") val sourceId: String?,
    @SerializedName("is_original") val isOriginal: Boolean?,
    @SerializedName("source_type") val sourceType: String?
)

// ---------- 标签 / 社团 / 声优 ----------

data class TagI18n(
    @SerializedName("name") val name: String?,
    @SerializedName("censored") val censored: String?,
    @SerializedName("history") val history: List<TagHistory>?
)

data class TagHistory(
    @SerializedName("name") val name: String?,
    @SerializedName("deprecatedAt") val deprecatedAt: Long?
)

data class Tag(
    @SerializedName("id") val id: Long?,
    @SerializedName("name") val name: String?,
    @SerializedName("i18n") val i18n: Map<String, TagI18n>?,
    @SerializedName("upvote") val upvote: Int?,
    @SerializedName("downvote") val downvote: Int?,
    @SerializedName("voteRank") val voteRank: Int?,
    @SerializedName("voteStatus") val voteStatus: Int?,
    @SerializedName("count") val count: Int?
)

data class Va(
    @SerializedName("id") val id: String?,
    @SerializedName("name") val name: String?,
    @SerializedName("count") val count: Int?
)

data class Circle(
    @SerializedName("id") val id: Long?,
    @SerializedName("name") val name: String?,
    @SerializedName("count") val count: Int?,
    @SerializedName("source_type") val sourceType: String?,
    @SerializedName("source_id") val sourceId: String?,
    @SerializedName("i18n") val i18n: Map<String, TagI18n>?
)

// ---------- 音轨 ----------

data class Track(
    @SerializedName("type") val type: String?,
    @SerializedName("title") val title: String?,
    @SerializedName("hash") val hash: String?,
    @SerializedName("work") val work: TrackWork?,
    @SerializedName("workTitle") val workTitle: String?,
    @SerializedName("mediaStreamUrl") val mediaStreamUrl: String?,
    @SerializedName("mediaDownloadUrl") val mediaDownloadUrl: String?,
    @SerializedName("streamLowQualityUrl") val streamLowQualityUrl: String?,
    @SerializedName("duration") val duration: Double?,
    @SerializedName("size") val size: Long?,
    @SerializedName("children") val children: List<Track>?
)

data class TrackWork(
    @SerializedName("id") val id: Long?,
    @SerializedName("source_id") val sourceId: String?,
    @SerializedName("source_type") val sourceType: String?
)

// ---------- 播放列表 ----------

data class Playlist(
    @SerializedName("id") val id: String?,
    @SerializedName("user_name") val userName: String?,
    @SerializedName("privacy") val privacy: Int?,
    @SerializedName("locale") val locale: String?,
    @SerializedName("playback_count") val playbackCount: Int?,
    @SerializedName("name") val name: String?,
    @SerializedName("description") val description: String?,
    @SerializedName("created_at") val createdAt: String?,
    @SerializedName("updated_at") val updatedAt: String?,
    @SerializedName("works_count") val worksCount: Int?,
    @SerializedName("liked_count") val likedCount: Int?,
    @SerializedName("latestWorkID") val latestWorkId: Long?,
    @SerializedName("mainCoverUrl") val mainCoverUrl: String?,
    @SerializedName("exist") val exist: Boolean?
)

data class PlaylistsResponse(
    @SerializedName("playlists") val playlists: List<Playlist>?,
    @SerializedName("pagination") val pagination: Pagination?
)

// ---------- 请求体 ----------

data class LoginRequest(
    @SerializedName("name") val name: String,
    @SerializedName("password") val password: String,
    @SerializedName("recommenderUuid") val recommenderUuid: String? = null
)

data class ReviewRequest(
    @SerializedName("work_id") val workId: Long,
    @SerializedName("rating") val rating: Double? = null,
    @SerializedName("review_text") val reviewText: String? = null,
    @SerializedName("progress") val progress: String? = null
)

data class VoteRequest(
    @SerializedName("workID") val workId: Long,
    @SerializedName("tagID") val tagId: Long,
    @SerializedName("status") val status: Int
)

data class CreatePlaylistRequest(
    @SerializedName("name") val name: String,
    @SerializedName("privacy") val privacy: Int = 0,
    @SerializedName("locale") val locale: String = "zh-cn",
    @SerializedName("description") val description: String = "",
    @SerializedName("works") val works: List<Long>? = null
)

data class PlaylistWorksRequest(
    @SerializedName("id") val id: String,
    @SerializedName("works") val works: List<Long>
)

data class RecommenderRequest(
    @SerializedName("order") val order: String = "release",
    @SerializedName("sort") val sort: String = "desc",
    @SerializedName("page") val page: Int = 1,
    @SerializedName("pageSize") val pageSize: Int = 20,
    @SerializedName("subtitle") val subtitle: Int = 0,
    @SerializedName("localSubtitledWorks") val localSubtitledWorks: List<String> = emptyList(),
    @SerializedName("withPlaylistStatus") val withPlaylistStatus: List<String> = emptyList()
)

data class EditPlaylistData(
    @SerializedName("name") val name: String,
    @SerializedName("privacy") val privacy: Int,
    @SerializedName("description") val description: String
)

data class EditPlaylistRequest(
    @SerializedName("id") val id: String,
    @SerializedName("data") val data: EditPlaylistData
)

data class ItemNeighborsRequest(
    @SerializedName("keyword") val keyword: String = "",
    @SerializedName("itemId") val itemId: Long,
    @SerializedName("localSubtitledWorks") val localSubtitledWorks: List<String> = emptyList(),
    @SerializedName("withPlaylistStatus") val withPlaylistStatus: List<String> = emptyList()
)
