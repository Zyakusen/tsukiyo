package io.github.zyakusen.tsukiyo.data.repository

import io.github.zyakusen.tsukiyo.data.AuthManager
import io.github.zyakusen.tsukiyo.data.MetadataCache
import io.github.zyakusen.tsukiyo.data.SettingsStore
import io.github.zyakusen.tsukiyo.data.WorkCache
import io.github.zyakusen.tsukiyo.data.api.AsmrApi
import io.github.zyakusen.tsukiyo.data.model.AuthResponse
import io.github.zyakusen.tsukiyo.data.model.Circle
import io.github.zyakusen.tsukiyo.data.model.Playlist
import io.github.zyakusen.tsukiyo.data.model.PlaylistsResponse
import io.github.zyakusen.tsukiyo.data.model.Tag
import io.github.zyakusen.tsukiyo.data.model.Track
import io.github.zyakusen.tsukiyo.data.model.Va
import io.github.zyakusen.tsukiyo.data.model.Work
import io.github.zyakusen.tsukiyo.data.model.WorksResponse

/**
 * 数据仓库：封装所有 API 调用，统一处理认证与业务逻辑。
 */
class AsmrRepository(
    private val apiProvider: () -> AsmrApi,
    private val authManager: AuthManager,
    private val settingsStore: SettingsStore,
    private val metadataCache: MetadataCache,
    private val workCache: WorkCache
) {

    private val api: AsmrApi get() = apiProvider()

    // ---------- 认证 ----------

    suspend fun guestLogin(): Boolean = runCatching {
        val resp = api.login(io.github.zyakusen.tsukiyo.data.model.LoginRequest("guest", "guest"))
        authManager.save(resp.user, resp.token)
        true
    }.getOrDefault(false)

    suspend fun login(name: String, password: String, recommenderUuid: String? = null): Result<AuthResponse> =
        runCatching {
            val resp = api.login(io.github.zyakusen.tsukiyo.data.model.LoginRequest(name, password, recommenderUuid))
            authManager.save(resp.user, resp.token)
            resp
        }

    suspend fun register(name: String, password: String, recommenderUuid: String? = null): Result<AuthResponse> =
        runCatching {
            val resp = api.register(io.github.zyakusen.tsukiyo.data.model.LoginRequest(name, password, recommenderUuid))
            authManager.save(resp.user, resp.token)
            resp
        }

    fun logout() = authManager.logout()

    // ---------- 浏览 / 搜索 ----------

    suspend fun getWorks(
        order: String = "release",
        sort: String = "desc",
        page: Int = 1,
        pageSize: Int = 20,
        filter: String? = null
    ): WorksResponse = api.getWorks(
        order = order,
        sort = sort,
        page = page,
        pageSize = pageSize,
        subtitle = if (settingsStore.subtitlesOnly) 1 else 0,
        filter = filter
    )

    suspend fun search(
        query: String,
        order: String = "release",
        sort: String = "desc",
        page: Int = 1,
        pageSize: Int = 20
    ): WorksResponse = api.search(
        query = android.net.Uri.encode(query),
        order = order,
        sort = sort,
        page = page,
        pageSize = pageSize,
        subtitle = if (settingsStore.subtitlesOnly) 1 else 0
    )

    suspend fun getWorkInfo(id: Long): Work = api.getWorkInfo(id)

    suspend fun getTracks(id: Long): List<Track> = api.getTracks(id)

    /** 获取作品详情 + 音轨树，优先走缓存，force 时强制刷新。 */
    suspend fun getWorkWithTracks(id: Long, force: Boolean = false): Pair<Work, List<Track>> {
        if (!force) workCache.get(id)?.let { return it }
        val work = api.getWorkInfo(id)
        val tracks = api.getTracks(id)
        workCache.put(id, work, tracks)
        return work to tracks
    }

    // ---------- 标签 / 社团 / 声优 ----------

    suspend fun getTags(force: Boolean = false): List<Tag> {
        if (!force) metadataCache.freshTags()?.let { return it }
        return runCatching { api.getTags() }
            .onSuccess { metadataCache.putTags(it) }
            .getOrElse { metadataCache.staleTags() ?: emptyList() }
    }

    suspend fun getTag(id: Long): Tag = api.getTag(id)

    suspend fun getCircles(force: Boolean = false): List<Circle> {
        if (!force) metadataCache.freshCircles()?.let { return it }
        return runCatching { api.getCircles() }
            .onSuccess { metadataCache.putCircles(it) }
            .getOrElse { metadataCache.staleCircles() ?: emptyList() }
    }

    suspend fun getCircle(id: Long): Circle = api.getCircle(id)

    suspend fun getVas(force: Boolean = false): List<Va> {
        if (!force) metadataCache.freshVas()?.let { return it }
        return runCatching { api.getVas() }
            .onSuccess { metadataCache.putVas(it) }
            .getOrElse { metadataCache.staleVas() ?: emptyList() }
    }

    suspend fun getVa(id: String): Va = api.getVa(id)

    // ---------- 评分 / 进度 ----------

    suspend fun rateWork(workId: Long, rating: Double?, reviewText: String? = null) {
        api.putReview(io.github.zyakusen.tsukiyo.data.model.ReviewRequest(workId, rating, reviewText, null))
    }

    suspend fun markProgress(workId: Long, progress: String) {
        api.putReview(io.github.zyakusen.tsukiyo.data.model.ReviewRequest(workId, null, null, progress))
    }

    suspend fun unmark(workId: Long) {
        api.deleteReview(workId)
    }

    suspend fun getReviews(
        order: String = "updated_at",
        sort: String = "desc",
        page: Int = 1,
        pageSize: Int = 20,
        filter: String? = null
    ): WorksResponse = api.getReviews(order, sort, page, pageSize, filter)

    suspend fun voteTag(workId: Long, tagId: Long, status: Int) {
        api.voteTag(io.github.zyakusen.tsukiyo.data.model.VoteRequest(workId, tagId, status))
    }

    // ---------- 推荐 ----------

    suspend fun popularWorks(page: Int = 1, pageSize: Int = 20): WorksResponse =
        api.popular(io.github.zyakusen.tsukiyo.data.model.RecommenderRequest(page = page, pageSize = pageSize))

    suspend fun recommendWorks(page: Int = 1, pageSize: Int = 20): WorksResponse =
        api.recommendForUser(io.github.zyakusen.tsukiyo.data.model.RecommenderRequest(page = page, pageSize = pageSize))

    suspend fun similarWorks(itemId: Long): WorksResponse =
        api.itemNeighbors(io.github.zyakusen.tsukiyo.data.model.ItemNeighborsRequest(itemId = itemId))

    suspend fun changePassword(oldPassword: String, newPassword: String) {
        api.changePassword(mapOf("oldPassword" to oldPassword, "newPassword" to newPassword))
    }

    // ---------- 播放列表 ----------

    suspend fun getPlaylists(page: Int = 1, pageSize: Int = 20, filterBy: String = "all"): PlaylistsResponse =
        api.getPlaylists(page, pageSize, filterBy)

    suspend fun getPlaylistMetadata(id: String): Playlist = api.getPlaylistMetadata(id)

    suspend fun getPlaylistWorks(id: String, page: Int = 1, pageSize: Int = 20): WorksResponse =
        api.getPlaylistWorks(id, page, pageSize)

    suspend fun getMarkedPlaylist(): Playlist = api.getMarkedPlaylist()

    suspend fun getWorkExistStatus(workId: Long): PlaylistsResponse = api.getWorkExistStatus(workId)

    suspend fun createPlaylist(name: String, privacy: Int = 0, description: String = ""): Playlist =
        api.createPlaylist(
            io.github.zyakusen.tsukiyo.data.model.CreatePlaylistRequest(
                name = name,
                privacy = privacy,
                locale = "zh-cn",
                description = description,
                works = null
            )
        )

    suspend fun addWorksToPlaylist(playlistId: String, workIds: List<Long>) {
        api.addWorksToPlaylist(io.github.zyakusen.tsukiyo.data.model.PlaylistWorksRequest(playlistId, workIds))
    }

    /** 通过播放列表 id 导入（复制）到自己的播放列表，返回新建的播放列表。 */
    suspend fun importPlaylist(id: String): Playlist {
        val meta = getPlaylistMetadata(id)
        val workIds = mutableListOf<Long>()
        var page = 1
        while (true) {
            val resp = getPlaylistWorks(id, page, 100)
            val works = resp.works ?: emptyList()
            workIds.addAll(works.mapNotNull { it.id })
            if (works.size < 100) break
            page++
        }
        return api.createPlaylist(
            io.github.zyakusen.tsukiyo.data.model.CreatePlaylistRequest(
                name = meta.name ?: "导入的播放列表",
                privacy = (meta.privacy ?: 0).coerceIn(0, 2),
                locale = "zh-cn",
                description = meta.description ?: "",
                works = workIds
            )
        )
    }

    suspend fun removeWorksFromPlaylist(playlistId: String, workIds: List<Long>) {
        api.removeWorksFromPlaylist(io.github.zyakusen.tsukiyo.data.model.PlaylistWorksRequest(playlistId, workIds))
    }

    suspend fun deletePlaylist(playlistId: String) {
        api.deletePlaylist(mapOf("id" to playlistId))
    }

    suspend fun editPlaylist(playlistId: String, name: String, privacy: Int, description: String) {
        api.editPlaylistMetadata(
            io.github.zyakusen.tsukiyo.data.model.EditPlaylistRequest(
                playlistId,
                io.github.zyakusen.tsukiyo.data.model.EditPlaylistData(name, privacy, description)
            )
        )
    }

    /** 从文本中解析 RJ 号并解析为作品 id。 */
    suspend fun resolveWorkIdsByRjText(text: String): List<Long> {
        val codes = Regex("(?i)\\bRJ\\d{6,8}\\b").findAll(text).map { it.value.uppercase() }.toList().distinct()
        val ids = mutableListOf<Long>()
        for (code in codes) {
            val resp = runCatching { search(code, page = 1, pageSize = 1) }.getOrNull()
            val work = resp?.works?.firstOrNull { it.sourceId.equals(code, true) }
            if (work?.id != null) ids.add(work.id)
        }
        return ids
    }

    // ---------- 工具 ----------

    /** 解析音轨的实际播放地址，根据画质设置优先使用低码率 CDN 以提升流畅度。 */
    fun resolveStreamUrl(track: Track): String? {
        val low = track.streamLowQualityUrl
        val high = track.mediaStreamUrl
        return when (settingsStore.audioQuality) {
            SettingsStore.QUALITY_LOW -> low?.takeIf { it.isNotBlank() } ?: high
            else -> high ?: low?.takeIf { it.isNotBlank() }
        }
    }

    fun resolveDownloadUrl(track: Track): String? =
        track.mediaDownloadUrl ?: track.mediaStreamUrl

    /** 将音轨树展平为有序的音频列表（忽略文件夹与文本/图片等其他类型）。 */
    fun flattenAudioTracks(tracks: List<Track>): List<Track> {
        val result = mutableListOf<Track>()
        fun walk(list: List<Track>) {
            for (t in list) {
                when (t.type) {
                    "audio" -> result.add(t)
                    "folder" -> t.children?.let { walk(it) }
                }
            }
        }
        walk(tracks)
        return result
    }
}
