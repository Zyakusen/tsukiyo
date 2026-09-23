package io.github.zyakusen.tsukiyo.data.api

import io.github.zyakusen.tsukiyo.data.model.AuthResponse
import io.github.zyakusen.tsukiyo.data.model.CreatePlaylistRequest
import io.github.zyakusen.tsukiyo.data.model.LoginRequest
import io.github.zyakusen.tsukiyo.data.model.Playlist
import io.github.zyakusen.tsukiyo.data.model.PlaylistWorksRequest
import io.github.zyakusen.tsukiyo.data.model.PlaylistsResponse
import io.github.zyakusen.tsukiyo.data.model.ReviewRequest
import io.github.zyakusen.tsukiyo.data.model.Tag
import io.github.zyakusen.tsukiyo.data.model.Circle
import io.github.zyakusen.tsukiyo.data.model.Va
import io.github.zyakusen.tsukiyo.data.model.VoteRequest
import io.github.zyakusen.tsukiyo.data.model.Work
import io.github.zyakusen.tsukiyo.data.model.WorksResponse
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

interface AsmrApi {

    // 认证
    @POST("api/auth/me")
    suspend fun login(@Body body: LoginRequest): AuthResponse

    @GET("api/auth/reg")
    suspend fun checkRegEnable(): Any

    @POST("api/auth/reg")
    suspend fun register(@Body body: LoginRequest): AuthResponse

    // 作品列表
    @GET("api/works")
    suspend fun getWorks(
        @Query("order") order: String = "release",
        @Query("sort") sort: String = "desc",
        @Query("page") page: Int = 1,
        @Query("pageSize") pageSize: Int = 20,
        @Query("seed") seed: Long? = null,
        @Query("subtitle") subtitle: Int = 0,
        @Query("filter") filter: String? = null
    ): WorksResponse

    // 搜索
    @GET("api/search/{query}")
    suspend fun search(
        @Path(value = "query", encoded = true) query: String,
        @Query("order") order: String = "release",
        @Query("sort") sort: String = "desc",
        @Query("page") page: Int = 1,
        @Query("pageSize") pageSize: Int = 20,
        @Query("subtitle") subtitle: Int = 0,
        @Query("includeTranslationWorks") includeTranslationWorks: Boolean = true
    ): WorksResponse

    // 详情 / 音轨
    @GET("api/workinfo/{id}")
    suspend fun getWorkInfo(@Path("id") id: Long): Work

    @GET("api/tracks/{id}")
    suspend fun getTracks(@Path("id") id: Long): List<io.github.zyakusen.tsukiyo.data.model.Track>

    // 标签 / 社团 / 声优
    @GET("api/tags")
    suspend fun getTags(): List<Tag>

    @GET("api/tags/{id}")
    suspend fun getTag(@Path("id") id: Long): Tag

    @GET("api/circles")
    suspend fun getCircles(): List<Circle>

    @GET("api/circles/{id}")
    suspend fun getCircle(@Path("id") id: Long): Circle

    @GET("api/vas")
    suspend fun getVas(): List<Va>

    @GET("api/vas/{id}")
    suspend fun getVa(@Path("id") id: String): Va

    // 评分 / 进度
    @PUT("api/review")
    suspend fun putReview(@Body body: ReviewRequest): Any

    @GET("api/review")
    suspend fun getReviews(
        @Query("order") order: String = "updated_at",
        @Query("sort") sort: String = "desc",
        @Query("page") page: Int = 1,
        @Query("pageSize") pageSize: Int = 20,
        @Query("filter") filter: String? = null
    ): WorksResponse

    @DELETE("api/review")
    suspend fun deleteReview(@Query("work_id") workId: Long): Any

    // 投票
    @POST("api/vote/vote-work-tag")
    suspend fun voteTag(@Body body: VoteRequest): Any

    // 推荐
    @POST("api/recommender/popular")
    suspend fun popular(@Body body: io.github.zyakusen.tsukiyo.data.model.RecommenderRequest): WorksResponse

    @POST("api/recommender/recommend-for-user")
    suspend fun recommendForUser(@Body body: io.github.zyakusen.tsukiyo.data.model.RecommenderRequest): WorksResponse

    @POST("api/recommender/item-neighbors")
    suspend fun itemNeighbors(@Body body: io.github.zyakusen.tsukiyo.data.model.ItemNeighborsRequest): WorksResponse

    @POST("api/auth/change-password")
    suspend fun changePassword(@Body body: Map<String, String>): Any

    // 播放列表
    @GET("api/playlist/get-playlists")
    suspend fun getPlaylists(
        @Query("page") page: Int = 1,
        @Query("pageSize") pageSize: Int = 20,
        @Query("filterBy") filterBy: String = "all"
    ): PlaylistsResponse

    @GET("api/playlist/get-playlist-metadata")
    suspend fun getPlaylistMetadata(@Query("id") id: String): Playlist

    @GET("api/playlist/get-playlist-works")
    suspend fun getPlaylistWorks(
        @Query("id") id: String,
        @Query("page") page: Int = 1,
        @Query("pageSize") pageSize: Int = 20
    ): WorksResponse

    @GET("api/playlist/get-default-mark-target-playlist")
    suspend fun getMarkedPlaylist(): Playlist

    @GET("api/playlist/get-work-exist-status-in-my-playlists")
    suspend fun getWorkExistStatus(
        @Query("workID") workId: Long,
        @Query("page") page: Int = 1,
        @Query("pageSize") pageSize: Int = 50,
        @Query("version") version: Int = 2
    ): PlaylistsResponse

    @POST("api/playlist/create-playlist")
    suspend fun createPlaylist(@Body body: CreatePlaylistRequest): Playlist

    @POST("api/playlist/add-works-to-playlist")
    suspend fun addWorksToPlaylist(@Body body: PlaylistWorksRequest): Any

    @POST("api/playlist/remove-works-from-playlist")
    suspend fun removeWorksFromPlaylist(@Body body: PlaylistWorksRequest): Any

    @POST("api/playlist/delete-playlist")
    suspend fun deletePlaylist(@Body body: Map<String, String>): Any

    @POST("api/playlist/edit-playlist-metadata")
    suspend fun editPlaylistMetadata(@Body body: io.github.zyakusen.tsukiyo.data.model.EditPlaylistRequest): Any
}
