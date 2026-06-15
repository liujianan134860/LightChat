# LightChat

LightChat 是一个 Android 即时通讯项目，用于完整实践 IM 客户端与服务端核心链路。项目覆盖登录注册、WebSocket 长连接、自定义二进制协议、单聊、群聊、@提醒、图片消息、消息撤回、已读回执、会话列表、历史消息分页、本地 SQLite 持久化、MySQL 服务端持久化和本地 Mock 离线推送。

## 项目结构

```text
LightChat/
|-- app/                         # Android 客户端源码
|-- server/                      # Kotlin/JVM 服务端源码
|-- .github/workflows/ci.yml     # GitHub Actions CI
|-- README.md                    # 项目首页说明
|-- 技术方案文档.md              # 架构、核心方案、难点和亮点
|-- TESTING.md                   # 测试范围、运行方式和抓包验证
|-- DECISIONS.md                 # ADR 架构决策记录
|-- BUGFIX.md                    # 真实问题、排查过程和修复记录
`-- AI_USAGE.md                  # AI 辅助方案采纳记录
```

## 技术栈

| 模块 | 技术 |
| --- | --- |
| Android UI | Kotlin, Jetpack Compose, Material 3, Navigation Compose |
| 客户端状态 | ViewModel, StateFlow, Repository |
| 客户端存储 | SQLiteOpenHelper, DAO, 多账号本地缓存 |
| 客户端网络 | OkHttp HTTP + OkHttp WebSocket |
| 服务端 | Kotlin/JVM, Netty WebSocket, JDK HttpServer |
| 服务端存储 | DataStore 运行时缓存 + MySQL 结构化表持久化 |
| 媒体存储 | 阿里云 OSS 私有 Bucket + 签名 URL + 本地缓存 |
| 协议 | WebSocket Binary Frame + 自定义 Header + JSON Body + CRC32 |
| 测试与工程化 | JUnit4, Gradle, GitHub Actions |

## 已实现功能

### 要求功能

| 编号 | 状态 | 功能 | 关键点 |
| --- | --- | --- | --- |
| B1 | ✅ | 登录/注册 | HTTP API, JWT Token |
| B2 | ✅ | 单聊文本消息 | WebSocket 长连接实时收发 |
| B3 | ✅ | 会话列表 | 最近会话、未读数、最后消息预览 |
| B4 | ✅ | 历史消息分页 | 上拉加载更多，按 `conversationSeq` 翻页 |
| B5 | ✅ | 消息持久化 | 直接使用 SQLiteOpenHelper，未使用 Room |
| B6 | ✅ | 自定义二进制协议 | Magic, Version, Cmd, Seq, BodyLength, CRC32 |
| B7 | ✅ | 心跳与断线重连 | 心跳 ACK、指数退避、前后台感知 |
| B8 | ✅ | 消息有序性 | `clientSeq` / `conversationSeq` / `userSeq` |
| B9 | ✅ | 消息可靠性 | ACK、失败重发、`messageId` 去重 |
| B10 | ✅ | 群聊 + @提醒 | 群成员、@高亮、@我未读计数 |
| B11 | ✅ | 图片消息 | OSS 上传、缩略图优先、原图渐进加载 |
| B12 | ✅ | 撤回 / 已读回执 | 2 分钟内撤回，单聊/群聊已读状态同步 |
| B13 | ✅ | 推送 | 本地 Mock Push 模拟离线通知 |

### 额外功能

| 模块 | 状态 | 功能 |
| --- | --- | --- |
| 账号与资料 | ✅ | JWT 登录态保存、多设备登录挤下线、个人资料编辑、头像/签名/地区同步 |
| 好友体系 | ✅ | 搜索用户、发送好友申请、同意/拒绝申请、好友申请通知、非好友资料页添加好友 |
| 名片分享 | ✅ | 好友名片推荐、名片消息展示、名片转发、合并转发内名片可继续点击交互 |
| 单聊体验 | ✅ | 文本、表情、图片、名片、合并转发消息；发送失败提示、重发、删除本地气泡 |
| 群聊体验 | ✅ | 创建群聊、设置群名、邀请成员、群成员列表、群系统提示、非好友群成员资料页 |
| @提醒 | ✅ | @单人、@所有人、@我摘要、@消息高亮定位、@消息已读/未读名单 |
| 已读状态 | ✅ | 单聊即时已读、群聊 `x/y` 已读统计、@消息按被@成员展示已读/未读 |
| 会话管理 | ✅ | 置顶、免打扰、隐藏会话、删除会话、未读角标统计 |
| 免打扰 | ✅ | 免打扰会话只显示红点、不计入底部总未读、普通推送屏蔽但 @ 摘要保留 |
| 图片消息 | ✅ | 阿里云 OSS 上传、私有签名 URL、缩略图优先、原图按需加载、URL 过期刷新 |
| 相册选择 | ✅ | 网格选择、多选顺序、预览左右切换、预览缩放、图片编辑后确认生效 |
| 图片预览 | ✅ | 聊天图片全屏/退出全屏动画、左右滑动切换、滑到目标图自动拉取原图、下载保存到相册 |
| 转发能力 | ✅ | 单条转发、多选转发、逐条/合并转发选择、合并转发详情页、嵌套聊天记录 |
| 长按菜单 | ✅ | 文本复制、转发、多选、撤回、删除；图片/名片/合并转发支持对应长按操作 |
| 搜索能力 | ✅ | 主页搜索、单聊/群聊聊天记录搜索、搜索结果定位到目标消息并高亮 |
| 同步恢复 | ✅ | Bootstrap 全量同步、`inbox_events` 增量同步、断线重连后按 `lastUserSeq` 恢复 |
| 本地缓存 | ✅ | SQLite 多账号隔离、头像和图片磁盘缓存优先、图片/头像内存 LruCache |
| 性能优化 | ✅ | 轻量开屏、会话列表分页、联系人列表分页、相册加载分页、搜索结果分页 |

## 快速运行

### 环境要求

- JDK 17
- Android Studio / Android SDK / adb
- MySQL 8.x，本地开发默认端口 `3307`
- 阿里云 OSS Bucket 和 RAM AccessKey

### 服务端环境变量

```powershell
$env:SERVER_PORT="8080"
$env:SERVER_HTTP_PORT="8081"
$env:JWT_SECRET="请换成自己的长随机字符串"

$env:MYSQL_URL="jdbc:mysql://127.0.0.1:3307/lightchat?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&createDatabaseIfNotExist=true"
$env:MYSQL_USER="root"
$env:MYSQL_PASSWORD="你的 MySQL 密码"

$env:MEDIA_STORAGE_PROVIDER="aliyun_oss"
$env:ALIYUN_OSS_ACCESS_KEY_ID="你的 AccessKey ID"
$env:ALIYUN_OSS_ACCESS_KEY_SECRET="你的 AccessKey Secret"
$env:ALIYUN_OSS_BUCKET="你的 Bucket"
$env:ALIYUN_OSS_ENDPOINT="oss-cn-beijing.aliyuncs.com"
$env:ALIYUN_OSS_KEY_PREFIX="lightchat"
$env:ALIYUN_OSS_PUBLIC_READ="false"
$env:ALIYUN_OSS_SIGNED_URL_SECONDS="3600"
```

### 启动服务端

```powershell
.\gradlew.bat :server:run
```

默认地址：

```text
WebSocket: ws://<服务端IP>:8080/ws
HTTP:      http://<服务端IP>:8081
MySQL:     127.0.0.1:3307/lightchat
```

### 构建客户端

```powershell
.\gradlew.bat :app:assembleDebug
adb install -r app\build\outputs\apk\debug\app-debug.apk
```

客户端服务地址目前仍是代码常量，真机调试时需要把服务端地址改为电脑局域网 IP：

- `app/src/main/java/com/lightchat/data/remote/AuthApiClient.kt`
- `app/src/main/java/com/lightchat/im/ConnectionManager.kt`

## 测试

```powershell
.\gradlew.bat :app:testDebugUnitTest :server:test
```

GitHub Actions 会在 `push`、`pull_request` 和手动触发时运行客户端与服务端单元测试，并构建 Android debug APK 和服务端发行包。

## 相关文档

| 文档 | 内容 |
| --- | --- |
| `技术方案文档.md` | 整体架构图、协议详细设计、双端数据库设计、核心技术方案、难点解决方案和技术亮点 |
| `TESTING.md` | 单元测试、CI、抓包验证和验收测试说明 |
| `DECISIONS.md` | 关键架构决策记录 |
| `BUGFIX.md` | 真实问题、根因和修复过程 |
| `AI_USAGE.md` | AI 辅助方案采纳/未采纳记录 |

## 当前限制

- 生产级杀进程离线推送仍需接入 FCM 或厂商 SDK；当前 Mock Push 仅用于本地调试和演示。
- 客户端服务地址仍是代码常量，后续可迁移到 BuildConfig 或运行时配置页。
- 服务端当前通过 `DataStore` 承载运行时状态，并异步 upsert 到 MySQL 结构化表；后续可进一步演进为业务操作直接事务写入 MySQL。
- UI 自动化测试尚未纳入 CI，当前 CI 主要覆盖 JVM 单元测试和构建。
