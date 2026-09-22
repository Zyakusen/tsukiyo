# Tsukiyo（月夜）

<p align="center">
  <img src="docs/icon.png" alt="Tsukiyo 图标" width="160" />
</p>

一个基于 [asmr.one](https://www.asmr.one/) 的开源安卓客户端。原生 Kotlin 开发，专注于同人音声（ASMR）的浏览、搜索、在线播放与离线下载，提供贴合手机的操作体验与流畅的音频播放。

## ✨ 功能特性

- **浏览与搜索**：按发售日/下载量/评分等排序浏览，支持关键词搜索，以及标签、社团、声优、时长、评分、价格、销量、年龄、语言等多维筛选（正选/反选）
- **作品详情**：文件树式浏览（含字幕/图片/PDF 等文件预览），标记进度（想听/在听/已听/重听/搁置）、评分
- **在线播放**：Media3/ExoPlayer 后台播放 + 系统通知栏控制，播放队列、快进快退、音量调节、画质切换（流畅/高音质）、定时停止
- **字幕**：自动匹配并渲染 LRC / WebVTT 字幕
- **离线下载**：多任务后台下载，按作品分组管理，本地播放
- **播放列表 / 收藏**：创建、编辑、删除播放列表，支持按 RJ 号批量导入作品；「我的收藏」聚合已评分/标记/评论的作品
- **个性化**：首页热门/推荐作品，深色/浅色/跟随系统主题，标签显示语言，HTTP/SOCKS 代理，镜像测速自动择优

## 📥 下载安装

从 [Releases](../../releases) 页面下载最新版 `Tsukiyo-v1.0.0.apk`，安装时允许「未知来源」即可。

> 当前为 1.0.0 版本，Android 7.0（API 24）及以上系统可用。

## 🔨 从源码构建

环境要求：

- JDK 17
- Android SDK（`compileSdk 36` / `build-tools 36`）

```bash
# 调试版（开箱即用，无需签名配置）
./gradlew assembleDebug

# 发布版（需先准备签名密钥）
keytool -genkeypair -v \
  -keystore app/release.keystore \
  -alias tsukiyo -keyalg RSA -keysize 2048 -validity 10950 \
  -storepass <密码> -keypass <密码> \
  -dname "CN=Tsukiyo, OU=App, O=zyakusen, L=Tokyo, ST=Tokyo, C=JP"
./gradlew assembleRelease
```

> 签名密钥 `app/release.keystore` 已加入 `.gitignore`，不会提交到仓库。发布前请自行生成并妥善保管。

## 🛠 技术栈

- **语言**：Kotlin
- **UI**：Jetpack Compose（Material 3）+ Navigation Compose
- **播放**：Media3（ExoPlayer）
- **网络**：Retrofit + OkHttp + Gson
- **本地存储**：Room + SharedPreferences
- **下载**：WorkManager
- **图片加载**：Coil

## 🔐 权限与隐私

应用申请以下权限：

| 权限 | 用途 |
| --- | --- |
| `INTERNET` / `ACCESS_NETWORK_STATE` | 访问 asmr.one 接口与音频 CDN |
| `FOREGROUND_SERVICE` / `FOREGROUND_SERVICE_MEDIA_PLAYBACK` | 后台音频播放 |
| `POST_NOTIFICATIONS` | 播放控制通知（Android 13+） |
| `WAKE_LOCK` | 播放期间保持唤醒 |
| `WRITE_EXTERNAL_STORAGE`（仅 Android 9 及以下） | 保存图片到相册 |

**隐私说明**：应用不收集、不上传任何个人信息；账号凭证仅保存在本机。所有数据均直接来自 asmr.one 官方接口，本项目与其无任何隶属关系。

## 📄 许可证

本项目使用 [MIT License](LICENSE)。

## 📮 联系方式

- GitHub：[zyakusen](https://github.com/zyakusen)
- 问题反馈：请在 [Issues](../../issues) 提交

---

*Tsukiyo（月夜）仅用于个人学习交流，请遵守 asmr.one 的使用条款与当地法律法规。*
