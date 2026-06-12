# LightChat

LightChat 是一个面向 IM 核心链路实践的 Android 即时通讯项目。项目包含 Android 客户端、Kotlin/JVM 服务端、本地 SQLite 缓存、MySQL 持久化、阿里云 OSS 图片存储、WebSocket 自定义二进制协议、消息同步、已读回执、群聊、转发、图片预览和本地 Mock 离线推送等能力。

## 仓库说明

本仓库包含 LightChat 的 Android 客户端源码、Kotlin/JVM 服务端源码、Gradle 构建配置、GitHub Actions CI 配置、单元测试代码、架构说明、数据库表设计、测试记录、性能优化记录、AI 使用记录和关键决策文档。客户端代码位于 `app/`，服务端代码位于 `server/`，两端共用同一套 IM 业务模型和自定义 WebSocket 二进制协议。

仓库只提交可以复现项目构建、测试和答辩说明所需的内容；本机启动脚本、数据库数据目录、运行日志、抓包截图、录屏文件、AccessKey、个人环境变量和临时压测数据不进入 Git 管理，避免泄露敏感信息，也避免把本地调试状态混入源码。

## 目录结构

```text
LightChat/
|-- .github
|   `-- workflows
|       `-- ci.yml
|-- .gitignore
|-- AI_USAGE.md
|-- app
|   |-- build.gradle.kts
|   |-- proguard-rules.pro
|   `-- src
|       |-- main
|       |   |-- AndroidManifest.xml
|       |   |-- java
|       |   |   `-- com
|       |   |       `-- lightchat
|       |   |           |-- data
|       |   |           |   |-- local
|       |   |           |   |   |-- dao
|       |   |           |   |   |   |-- ConversationDao.kt
|       |   |           |   |   |   |-- FriendRequestDao.kt
|       |   |           |   |   |   |-- GroupDao.kt
|       |   |           |   |   |   |-- MessageDao.kt
|       |   |           |   |   |   |-- SyncStateDao.kt
|       |   |           |   |   |   `-- UserDao.kt
|       |   |           |   |   |-- DatabaseHelper.kt
|       |   |           |   |   |-- DatabaseSeeder.kt
|       |   |           |   |   |-- TokenManager.kt
|       |   |           |   |   `-- UserSession.kt
|       |   |           |   |-- remote
|       |   |           |   |   `-- AuthApiClient.kt
|       |   |           |   `-- repository
|       |   |           |       |-- AuthRepository.kt
|       |   |           |       |-- ConversationRepository.kt
|       |   |           |       |-- MessageRepository.kt
|       |   |           |       `-- UserRepository.kt
|       |   |           |-- event
|       |   |           |   `-- AppEvents.kt
|       |   |           |-- im
|       |   |           |   |-- ConnectionManager.kt
|       |   |           |   |-- ConnectionState.kt
|       |   |           |   |-- HeartbeatManager.kt
|       |   |           |   |-- ImClient.kt
|       |   |           |   |-- ImForegroundService.kt
|       |   |           |   `-- ReconnectManager.kt
|       |   |           |-- LightChatApplication.kt
|       |   |           |-- MainActivity.kt
|       |   |           |-- MainContent.kt
|       |   |           |-- model
|       |   |           |   |-- Conversation.kt
|       |   |           |   |-- ConversationId.kt
|       |   |           |   |-- FriendRequest.kt
|       |   |           |   |-- GroupMember.kt
|       |   |           |   |-- ImGroup.kt
|       |   |           |   |-- Message.kt
|       |   |           |   `-- User.kt
|       |   |           |-- notification
|       |   |           |   |-- MockVendorPushReceiver.kt
|       |   |           |   `-- NotificationHelper.kt
|       |   |           |-- protocol
|       |   |           |   |-- Cmd.kt
|       |   |           |   |-- Packet.kt
|       |   |           |   `-- ProtocolCodec.kt
|       |   |           |-- sync
|       |   |           |   |-- EventProcessor.kt
|       |   |           |   |-- EventType.kt
|       |   |           |   |-- SyncEvent.kt
|       |   |           |   `-- SyncManager.kt
|       |   |           |-- ui
|       |   |           |   |-- chat
|       |   |           |   |   |-- ChatInputBar.kt
|       |   |           |   |   |-- ChatMentionDialog.kt
|       |   |           |   |   |-- ChatMessageBubble.kt
|       |   |           |   |   |-- ChatMessageList.kt
|       |   |           |   |   |-- ChatMessageTime.kt
|       |   |           |   |   |-- ChatScreen.kt
|       |   |           |   |   |-- ChatScreenComponents.kt
|       |   |           |   |   |-- ChatScreenDialogs.kt
|       |   |           |   |   |-- ChatScrollController.kt
|       |   |           |   |   |-- ImageDoodleCanvas.kt
|       |   |           |   |   |-- ImageEditScreen.kt
|       |   |           |   |   |-- ImageGesture.kt
|       |   |           |   |   |-- ImageUtils.kt
|       |   |           |   |   |-- ImageViewerScreen.kt
|       |   |           |   |   |-- PhotoDetailScreen.kt
|       |   |           |   |   |-- PhotoEditScreen.kt
|       |   |           |   |   |-- PhotoPickerScreen.kt
|       |   |           |   |   `-- PictureCacheManager.kt
|       |   |           |   |-- components
|       |   |           |   |   |-- AvatarCacheLoader.kt
|       |   |           |   |   `-- LightChatAvatar.kt
|       |   |           |   |-- contact
|       |   |           |   |   `-- ContactScreen.kt
|       |   |           |   |-- conversation
|       |   |           |   |   `-- ConversationListScreen.kt
|       |   |           |   |-- forward
|       |   |           |   |   |-- ForwardPreviewScreen.kt
|       |   |           |   |   |-- ForwardSelectScreen.kt
|       |   |           |   |   `-- MergeForwardDetailScreen.kt
|       |   |           |   |-- friend
|       |   |           |   |   |-- AddFriendScreen.kt
|       |   |           |   |   `-- FriendRequestScreen.kt
|       |   |           |   |-- group
|       |   |           |   |   |-- GroupCreateScreen.kt
|       |   |           |   |   |-- GroupInviteScreen.kt
|       |   |           |   |   |-- GroupListScreen.kt
|       |   |           |   |   `-- GroupMemberListScreen.kt
|       |   |           |   |-- login
|       |   |           |   |   `-- LoginScreen.kt
|       |   |           |   |-- main
|       |   |           |   |   `-- MainScreen.kt
|       |   |           |   |-- navigation
|       |   |           |   |   `-- NavGraph.kt
|       |   |           |   |-- profile
|       |   |           |   |   |-- ProfileScreen.kt
|       |   |           |   |   `-- UserCardShareScreen.kt
|       |   |           |   |-- search
|       |   |           |   |   |-- ChatSearchScreen.kt
|       |   |           |   |   `-- SearchScreen.kt
|       |   |           |   `-- theme
|       |   |           |       |-- Color.kt
|       |   |           |       |-- Theme.kt
|       |   |           |       `-- Type.kt
|       |   |           |-- util
|       |   |           |   `-- ToastExt.kt
|       |   |           `-- viewmodel
|       |   |               |-- ChatViewModel.kt
|       |   |               |-- ContactViewModel.kt
|       |   |               |-- ConversationListViewModel.kt
|       |   |               |-- GroupCreateViewModel.kt
|       |   |               `-- LoginViewModel.kt
|       |   `-- res
|       |       |-- drawable
|       |       |   `-- ic_launcher_foreground.xml
|       |       |-- mipmap-hdpi
|       |       |   |-- ic_launcher.png
|       |       |   `-- ic_launcher_round.png
|       |       |-- mipmap-mdpi
|       |       |   |-- ic_launcher.png
|       |       |   `-- ic_launcher_round.png
|       |       |-- mipmap-xhdpi
|       |       |   |-- ic_launcher.png
|       |       |   `-- ic_launcher_round.png
|       |       |-- mipmap-xxhdpi
|       |       |   |-- ic_launcher.png
|       |       |   `-- ic_launcher_round.png
|       |       |-- mipmap-xxxhdpi
|       |       |   |-- ic_launcher.png
|       |       |   `-- ic_launcher_round.png
|       |       `-- values
|       |           |-- colors.xml
|       |           |-- strings.xml
|       |           `-- themes.xml
|       `-- test
|           `-- java
|               `-- com
|                   `-- lightchat
|                       |-- model
|                       |   `-- ConversationIdTest.kt
|                       |-- protocol
|                       |   |-- CmdTest.kt
|                       |   `-- ProtocolCodecTest.kt
|                       `-- ui
|                           `-- chat
|                               |-- ChatMessageTimeTest.kt
|                               `-- ChatScrollControllerTest.kt
|-- BUGFIX.md
|-- build.gradle.kts
|-- DECISIONS.md
|-- gradle
|   `-- wrapper
|       |-- gradle-wrapper.jar
|       `-- gradle-wrapper.properties
|-- gradle.properties
|-- gradlew
|-- gradlew.bat
|-- README.md
|-- server
|   |-- build.gradle.kts
|   `-- src
|       |-- main
|       |   `-- kotlin
|       |       `-- com
|       |           `-- lightchat
|       |               `-- server
|       |                   |-- handler
|       |                   |   |-- MessageDeliveryService.kt
|       |                   |   `-- PacketDispatcher.kt
|       |                   |-- http
|       |                   |   `-- AuthHttpServer.kt
|       |                   |-- Main.kt
|       |                   |-- media
|       |                   |   |-- AliyunOssMediaStorage.kt
|       |                   |   `-- MediaStorage.kt
|       |                   |-- model
|       |                   |   |-- InboxEvent.kt
|       |                   |   |-- ServerConversation.kt
|       |                   |   |-- ServerGroup.kt
|       |                   |   |-- ServerMessage.kt
|       |                   |   `-- ServerUser.kt
|       |                   |-- netty
|       |                   |   |-- NettyClientConnection.kt
|       |                   |   `-- NettyLightChatWebSocketServer.kt
|       |                   |-- protocol
|       |                   |   |-- Cmd.kt
|       |                   |   |-- Packet.kt
|       |                   |   `-- ProtocolCodec.kt
|       |                   |-- push
|       |                   |   `-- MockVendorPushGateway.kt
|       |                   |-- security
|       |                   |   |-- JwtService.kt
|       |                   |   `-- PasswordHasher.kt
|       |                   |-- session
|       |                   |   |-- ClientConnection.kt
|       |                   |   `-- ConnectionRegistry.kt
|       |                   `-- store
|       |                       |-- DataStore.kt
|       |                       |-- EventService.kt
|       |                       |-- MySqlStatePersistence.kt
|       |                       `-- StatePersistence.kt
|       `-- test
|           `-- kotlin
|               `-- com
|                   `-- lightchat
|                       `-- server
|                           |-- handler
|                           |   `-- MessageDeliveryServiceTest.kt
|                           |-- protocol
|                           |   `-- ProtocolCodecTest.kt
|                           |-- security
|                           |   `-- PasswordHasherTest.kt
|                           |-- session
|                           |   `-- ConnectionRegistryTest.kt
|                           `-- store
|                               |-- DataStoreTest.kt
|                               `-- EventServiceTest.kt
|-- settings.gradle.kts
|-- TESTING.md
`-- 双端数据库表设计说明.md
```

## 项目文档

| 文档 | 内容 |
| --- | --- |
| `README.md` | 项目简介、架构图、运行指南、自定义协议、功能勾选表和当前限制 |
| `双端数据库表设计说明.md` | 客户端 SQLite 与服务端 MySQL 的表结构、索引、迁移历史和设计决策 |
| `TESTING.md` | 本地测试、CI 自动测试、抓包验证和人工验收说明 |
| `性能优化说明.md` | 开屏、会话列表、聊天记录、图片和同步链路的性能优化记录 |
| `问题修复与测试记录.md` | 主要缺陷、排查过程、修复方式和回归验证 |
| `AI_USAGE.md` | AI 给出的方案中采纳/未采纳的案例与原因 |
| `DECISIONS.md` | 关键架构决策记录，采用 ADR 格式 |
| `BUGFIX.md` | 真实踩坑、根因分析和最终修复方案 |

## 技术栈

| 模块 | 技术 |
| --- | --- |
| Android UI | Kotlin, Jetpack Compose, Material 3, Navigation Compose |
| 客户端状态 | ViewModel, StateFlow, Repository |
| 客户端存储 | SQLiteOpenHelper, DAO, 本地多账号隔离 |
| 客户端网络 | OkHttp HTTP + WebSocket |
| 服务端 | Kotlin/JVM, Netty WebSocket, JDK HttpServer |
| 服务端存储 | 内存 DataStore + MySQL 快照/镜像表 |
| 媒体存储 | 阿里云 OSS，私有 Bucket 签名 URL，本地缓存 |
| 自定义协议 | WebSocket 二进制帧 + JSON body + CRC32 |
| 测试与 CI | JUnit4, Gradle, GitHub Actions |

## 工具使用要求对照

| 类别 | 要求 | 项目使用情况 | 证据位置 |
| --- | --- | --- | --- |
| 语言 | Kotlin | 已使用。客户端和服务端业务代码均为 Kotlin。 | `app/src/main/java/com/lightchat/`、`server/src/main/kotlin/com/lightchat/server/` |
| 长连接 | OkHttp WebSocket 或裸 Socket + 自写协议帧 | 已使用 OkHttp WebSocket，WebSocket payload 为自定义二进制协议帧。 | `app/src/main/java/com/lightchat/im/ConnectionManager.kt`、`app/src/main/java/com/lightchat/protocol/ProtocolCodec.kt` |
| 数据库 | SQLiteOpenHelper（手写） | 已使用手写 SQLiteOpenHelper 和 DAO。 | `app/src/main/java/com/lightchat/data/local/DatabaseHelper.kt` |
| 异步 | Coroutines + Flow + Channel | 已使用 Coroutines、StateFlow、SharedFlow；网络事件通过同步管理器和事件处理器串行落库。 | `app/src/main/java/com/lightchat/sync/`、`app/src/main/java/com/lightchat/event/AppEvents.kt` |
| UI | Jetpack Compose | 已使用 Compose，未使用 XML 页面实现主要 UI。 | `app/src/main/java/com/lightchat/ui/` |
| 抓包 | 必须使用 Charles/Wireshark 抓自己的协议包并提交截图 | 已使用 Wireshark 抓 WebSocket/TCP 包，确认端口 `8080` 上存在 WebSocket Binary Frame；截图属于答辩/验收材料，不提交到仓库。 | 详情见文件：抓包详情 |

## 架构图

```mermaid
flowchart TD
    UI["Android UI\nJetpack Compose"] --> VM["ViewModel + StateFlow"]
    VM --> Repo["Repository"]
    Repo --> SQLite["SQLite\nuser / friend / conversation / message / group / sync_state"]
    VM --> Sync["SyncManager + EventProcessor\n串行处理网络事件"]
    Sync --> IM["ImClient + ConnectionManager\nOkHttp WebSocket"]
    Repo --> HTTP["AuthApiClient / MediaApiClient\nHTTP API"]
    IM --> WS["Netty WebSocket Server\n自定义二进制协议"]
    HTTP --> API["JDK HttpServer\n登录/资料/图片/Bootstrap"]
    WS --> Service["CommandHandler\nMessageDeliveryService\nEventService"]
    API --> Service
    Service --> Store["DataStore\n运行时权威状态"]
    Store --> MySQL["MySQL\nlightchat_state + 镜像表"]
    API --> OSS["Aliyun OSS\n原图 + 缩略图"]
    Service --> Push["Mock Push Gateway\nADB 显式广播"]
```

## 消息链路

```mermaid
sequenceDiagram
    participant A as 发送端
    participant S as 服务端
    participant B as 接收端
    participant DB as SQLite/MySQL

    A->>A: 生成 messageId/clientSeq，本地插入 SENDING
    A->>S: SEND_MESSAGE(WebSocket 二进制协议)
    S->>DB: 分配 conversationSeq，保存 message
    S->>DB: 为参与者写入 inbox_events(userSeq)
    S-->>A: MESSAGE_ACK
    S-->>B: NEW_EVENT_NOTIFY(latestUserSeq)
    B->>S: SYNC(lastUserSeq)
    S-->>B: SYNC_RESULT(events)
    B->>DB: EventProcessor 串行落库
    B->>S: MARK_READ(conversationSeq)
    S->>DB: 更新 conversation_members.last_read_seq / message_receipts
    S-->>A: READ_NOTIFY
    A->>DB: 更新本地已读状态
```

关键原则：

- `clientSeq` 管理客户端本地发送顺序，覆盖发送中、失败、重发等状态。
- `conversationSeq` 由服务端分配，管理会话内最终顺序、分页锚点和已读进度。
- `userSeq` 由服务端按用户维度分配，管理 Inbox 增量同步进度。
- 客户端收到网络事件后由 `EventProcessor` 串行写库，先落库再推进 `lastUserSeq`。

## 自定义协议

WebSocket 使用自定义二进制包。协议字段全部使用 Java/Kotlin `ByteBuffer` 默认的大端序（Big Endian）写入；`body` 是 UTF-8 JSON 字节数组；`crc32` 对 `header + body` 计算，不包含尾部 CRC 自身。

```text
总长度 = 16 字节 Header + N 字节 Body + 4 字节 CRC32

┌──────────────┬──────────────┬──────────┬────────────┬────────────────────┐
│ 偏移         │ 字段         │ 长度     │ 示例       │ 说明               │
├──────────────┼──────────────┼──────────┼────────────┼────────────────────┤
│ 0..1         │ magic        │ 2 bytes  │ 4C 43      │ 固定为 0x4C43，即 LC │
│ 2            │ version      │ 1 byte   │ 01         │ 当前版本为 1       │
│ 3            │ cmd          │ 1 byte   │ 0A         │ 命令号             │
│ 4..11        │ seq          │ 8 bytes  │ 00..01     │ 请求序号 Long      │
│ 12..15       │ bodyLength   │ 4 bytes  │ 00 00 00 N │ Body 字节长度 Int  │
│ 16..15+N     │ body         │ N bytes  │ JSON       │ UTF-8 JSON         │
│ 16+N..19+N   │ crc32        │ 4 bytes  │ xx xx xx xx│ CRC32(header+body) │
└──────────────┴──────────────┴──────────┴────────────┴────────────────────┘
```

最小包长度为 20 字节：16 字节 Header + 0 字节 Body + 4 字节 CRC32。心跳包就是这种无 Body 包。

抓包时如果看到：

```text
4C 43 01 03 ...
```

含义是：

```text
magic=0x4C43(LC), version=0x01, cmd=0x03(HEARTBEAT)
```

命令号如下：

| 十进制 | 十六进制 | 名称 | 说明 |
| ---: | ---: | --- | --- |
| 1 | `0x01` | `AUTH` | 客户端 WebSocket 鉴权 |
| 2 | `0x02` | `AUTH_ACK` | 服务端鉴权成功响应 |
| 3 | `0x03` | `HEARTBEAT` | 客户端心跳 |
| 4 | `0x04` | `HEARTBEAT_ACK` | 服务端心跳响应 |
| 5 | `0x05` | `NEW_EVENT_NOTIFY` | 服务端通知客户端有新事件 |
| 6 | `0x06` | `SYNC` | 客户端按 `lastUserSeq` 拉取增量 |
| 7 | `0x07` | `SYNC_RESULT` | 服务端返回增量事件 |
| 8 | `0x08` | `RECALL_MESSAGE` | 撤回消息或撤回 ACK |
| 9 | `0x09` | `CREATE_GROUP` | 创建群聊或创建 ACK |
| 10 | `0x0A` | `SEND_MESSAGE` | 客户端发送消息 |
| 11 | `0x0B` | `MESSAGE_ACK` | 服务端消息 ACK |
| 12 | `0x0C` | `SEND_FRIEND_REQUEST` | 发送好友申请或 ACK |
| 13 | `0x0D` | `ACCEPT_FRIEND_REQUEST` | 同意好友申请或 ACK |
| 14 | `0x0E` | `MARK_READ` | 客户端上报已读或 ACK |
| 15 | `0x0F` | `UPDATE_PROFILE` | 更新资料或 ACK |
| 16 | `0x10` | `ADD_GROUP_MEMBERS` | 邀请群成员或 ACK |
| 17 | `0x11` | `REJECT_FRIEND_REQUEST` | 拒绝好友申请或 ACK |
| 18 | `0x12` | `READ_NOTIFY` | 服务端通知对方已读进度 |
| 19 | `0x13` | `UPDATE_CONVERSATION_SETTINGS` | 更新置顶/免打扰等会话设置 |
| 99 | `0x63` | `ERROR` | 服务端错误响应 |

注意：CRC32 只用于发现包损坏，不等于加密。使用 `ws://` / `http://` 时，局域网抓包仍可能看到明文 JSON。生产环境应使用 HTTPS/WSS 和可信证书。

## 数据存储

Android 本地 SQLite 主要表：

| 表 | 作用 |
| --- | --- |
| `user` | 用户资料缓存，包含头像 URL/版本 |
| `friend` | 好友关系缓存 |
| `friend_request` | 好友申请缓存 |
| `conversation` | 会话列表快照、最后消息、未读、置顶、免打扰 |
| `conversation_member` | 每用户每会话状态，包含 `last_read_seq`、置顶、免打扰 |
| `message` | 本地消息缓存，包含 `client_seq`、`conversation_seq`、`extra` |
| `message_receipt` | 群聊回执预留/结构化存储 |
| `im_group` / `group_member` | 群资料和群成员 |
| `sync_state` | 用户维度同步游标 `lastUserSeq` |
| `auth_session` | 本地登录态 |

服务端 MySQL 默认数据库为 `lightchat`。当前服务端以内存 `DataStore` 为运行时权威状态，通过 `lightchat_state` 快照和多张镜像表落地，便于重启恢复、Navicat 查询和后续迁移到纯关系型实现。核心服务端表包括 `users`、`credentials`、`conversations`、`conversation_members`、`messages`、`message_receipts`、`chat_groups`、`group_members`、`user_seq_counters`、`conversation_seq_counters`、`inbox_events`。

## 运行指南

### 环境要求

- JDK 17
- Android Studio / Android SDK / adb
- MySQL 8.x，本地开发默认端口 `3307`
- 阿里云 OSS Bucket 和 RAM AccessKey
- Windows PowerShell 或等价终端

### 服务端环境变量

```powershell
$env:SERVER_PORT="8080"
$env:SERVER_HTTP_PORT="8081"
$env:JWT_SECRET="请换成自己的长随机字符串"

$env:MYSQL_URL="jdbc:mysql://127.0.0.1:3307/lightchat?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&createDatabaseIfNotExist=true"
$env:MYSQL_USER="root"
$env:MYSQL_PASSWORD="你的 MySQL 密码"
$env:MYSQL_STATE_KEY="default"

$env:MEDIA_STORAGE_PROVIDER="aliyun_oss"
$env:ALIYUN_OSS_ACCESS_KEY_ID="你的 AccessKey ID"
$env:ALIYUN_OSS_ACCESS_KEY_SECRET="你的 AccessKey Secret"
$env:ALIYUN_OSS_BUCKET="你的 Bucket"
$env:ALIYUN_OSS_ENDPOINT="oss-cn-beijing.aliyuncs.com"
$env:ALIYUN_OSS_KEY_PREFIX="lightchat"
$env:ALIYUN_OSS_PUBLIC_READ="false"
$env:ALIYUN_OSS_SIGNED_URL_SECONDS="3600"
```

本地 Mock 推送可选配置：

```powershell
$env:MOCK_PUSH_KEY="lightchat-local-mock"
$env:MOCK_PUSH_ADB_PATH="adb"
$env:MOCK_PUSH_ADB_DEVICES='{"123456":"EU9TXSGIX4MR55GM","777777":"emulator-5554","2133800438":"emulator-5556"}'
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

### 配置客户端服务地址

当前客户端服务地址仍是代码常量，需要按电脑局域网 IP 修改：

- `app/src/main/java/com/lightchat/data/remote/AuthApiClient.kt`
- `app/src/main/java/com/lightchat/im/ConnectionManager.kt`

模拟器访问宿主机可使用模拟器网关地址；真机需要与电脑在同一局域网，并使用电脑真实局域网 IP。

### 构建和安装

```powershell
.\gradlew.bat :app:assembleDebug
adb install -r app\build\outputs\apk\debug\app-debug.apk
```

### 测试和 CI

本地单元测试：

```powershell
.\gradlew.bat :app:testDebugUnitTest :server:test
```

完整构建：

```powershell
.\gradlew.bat :app:assembleDebug :server:installDist
```

GitHub Actions 会在 `push`、`pull_request` 和手动触发时执行：

1. 配置 JDK 17 和 Android SDK。
2. 运行 Android 与 Server 单元测试。
3. 构建 Android debug APK 和服务端 installDist。

测试范围见 `TESTING.md`。

## 功能勾选表

### 要求功能

| 编号 | 状态 | 功能 | 关键约束 | 实现说明 |
| --- | --- | --- | --- | --- |
| B1 | ✅ | 登录/注册 | HTTP 接口、JWT Token | 已实现 `/api/register`、`/api/login`，登录后保存 Bearer Token，WebSocket 使用 token 进行 `AUTH` 鉴权。 |
| B2 | ✅ | 单聊文本消息 | WebSocket 长连接、实时收发 | 已实现单聊文本消息发送、接收、ACK、失败重发和本地状态刷新。 |
| B3 | ✅ | 会话列表 | 显示最近会话、未读数、最后一条消息预览 | 已实现单聊/群聊/系统会话列表，支持最后消息预览、未读数、免打扰红点和置顶排序。 |
| B4 | ✅ | 历史消息分页拉取 | 上拉加载更多 | 已实现聊天记录双向分页，历史消息使用 `conversationSeq` 作为 cursor，上滑触发加载更多。 |
| B5 | ✅ | 消息持久化 | 直接使用 SQLite，禁用 Room | 已实现手写 `SQLiteOpenHelper`、DAO 和本地多账号缓存，没有使用 Room。 |
| B6 | ✅ | 自定义二进制协议 | Header(magic + version + length + cmd) + Body(JSON) + CRC | 已实现 WebSocket Binary Frame：`magic/version/cmd/seq/bodyLength/body/crc32`，客户端和服务端同源编解码。 |
| B7 | ✅ | 心跳与断线重连 | 指数退避、前后台切换感知 | 已实现 20 秒心跳、`HEARTBEAT_ACK`、网络恢复重连、前后台连接管理和指数退避重连。 |
| B8 | ✅ | 消息有序性 | 客户端生成 seq，服务端确认，乱序重排 | 已实现 `clientSeq`、`conversationSeq`、`userSeq`；本地发送顺序、服务端最终顺序和增量同步进度分离。 |
| B9 | ✅ | 消息可靠性 | ACK 机制、失败重发、去重 messageId | 已实现 `MESSAGE_ACK`、2 秒 ACK 超时失败、失败消息重发、按 `messageId` 去重和状态更新。 |
| B10 | ✅ | 群聊 + @ 提醒 | 群成员列表、@人高亮、@我未读单独计数 | 已实现群聊、群成员列表、@成员/@所有人、@我摘要、进入群聊定位未读 @ 消息并高亮。 |
| B11 | ✅ | 图片消息 | 图片上传、渐进式加载 | 已实现阿里云 OSS 上传原图/缩略图，接收端缩略图优先，全屏或后台拉取原图，本地缓存优先。 |
| B12 | ✅ | 消息撤回 / 已读回执 | 2 分钟内可撤回，已读状态同步 | 已实现 2 分钟内撤回、撤回提示、单聊已读、群聊 `x/y 已读` 和 @消息已读/未读明细。 |
| B13 | ✅ | 推送 | 后台被杀时通过厂商推送通道唤醒，可用 mock | 已实现本地 Mock Push：WebSocket 断开后服务端按用户 ID 路由到 ADB 设备，客户端 Receiver 展示通知。 |

### 额外功能

| 模块 | 状态 | 功能 |
| --- | --- | --- |
| 账号体系 | ✅ | 自动登录、退出登录、服务端会话校验、旧明文密码登录后自动升级为哈希。 |
| 个人资料 | ✅ | 昵称、头像、个性签名、地区编辑，资料更新后好友端同步刷新。 |
| 头像缓存 | ✅ | 头像 URL/版本同步，内存缓存和磁盘缓存优先，断网后可显示已缓存头像。 |
| 好友 | ✅ | 搜索用户、发送申请、同意/拒绝申请、好友申请通知。 |
| 好友 | ✅ | 好友同意后自动建立单聊会话。 |
| 联系人 | ✅ | 联系人列表、联系人搜索、群聊入口、联系人页不展示签名。 |
| 新用户引导 | ✅ | 首次注册登录默认展示 LightChat 助手和欢迎消息。 |
| 会话设置 | ✅ | 置顶、免打扰、隐藏、删除、标记未读。 |
| 未读统计 | ✅ | 免打扰会话不参与底部未读汇总，列表只显示小红点。 |
| 发送失败体验 | ✅ | 发送失败消息保持排序，失败气泡左侧显示失败入口，支持重发、复制和删除。 |
| 长按菜单 | ✅ | 文字、emoji、图片、名片、合并转发气泡支持微信式浮层长按菜单。 |
| 多选 | ✅ | 左侧选择框、取消按钮、批量删除、批量转发。 |
| 转发 | ✅ | 单条转发、多选逐条转发、多选合并转发。 |
| 合并转发 | ✅ | 详情页展示文本、图片、名片和嵌套聊天记录；图片可点开全屏查看。 |
| 名片 | ✅ | 推荐好友名片，收到方可点击查看资料，非好友可添加好友。 |
| 群管理 | ✅ | 创建群聊、设置群名称、邀请成员、群成员列表和成员资料页。 |
| 群系统提示 | ✅ | 邀请成员后在聊天页居中展示“xxx邀请xxx加入了群聊”。 |
| 图片选择 | ✅ | 相册网格、多图选择、按选择顺序发送、长按拖动多选。 |
| 图片预览 | ✅ | 预览页选择状态保持、放大、上下左右拖动查看。 |
| 图片全屏 | ✅ | 从聊天气泡放大进入，全屏左右切换，退出缩回气泡位置。 |
| 图片下载 | ✅ | 全屏图片可保存到系统相册。 |
| 媒体存储 | ✅ | 私有 OSS 签名 URL 过期刷新，刷新后更新本地缓存和消息元信息。 |
| 搜索 | ✅ | 首页全局搜索联系人和聊天记录。 |
| 搜索 | ✅ | 单聊/群聊详情内搜索，结果进入目标消息并居中高亮 3 秒。 |
| 搜索 | ✅ | 图片路径不参与聊天记录搜索。 |
| 分页性能 | ✅ | 会话列表分页预取，聊天记录支持 1 万条消息中间定位和双向加载。 |
| 页面动画 | ✅ | 聊天详情、资料页、名片页、群成员页等打开/关闭配套滑动动画。 |
| 通知 | ✅ | 前台/后台通知、好友申请通知、群聊通知显示群名。 |
| 工程化 | ✅ | `.gitignore`、GitHub Actions、单元测试、测试文档。 |
| 性能验证 | ✅ | 新增 `profile` 构建类型，支持 Android Studio Profiler 以 profileable 方式采集。 |

## 性能优化摘要

- 开屏：核心组件懒初始化，避免启动时同步下载图片缩略图。
- 会话列表：分页加载 80 条，距底部 25 条预取，刷新防抖，批量查用户信息。
- 聊天列表：双向分页 80 条，使用 `conversationSeq` 做 cursor，保持滚动锚点。
- 图片：渐进式加载，本地缓存优先；缩略图约 200px、JPEG 60%；大图解码按 2 的幂次方计算 `inSampleSize`。
- 相册：缩略图后台加载，LruCache 复用，避免打开动画阶段读取整张原图。
- 网络：20 秒心跳，重连指数退避，2 秒 ACK 超时标记失败。

## 当前限制

- 生产级杀进程离线推送仍需接入 FCM 或厂商 SDK；当前 Mock Push 只用于本地三台设备调试。
- 客户端服务地址仍是代码常量，后续可迁移到 BuildConfig 或运行时配置页。
- 服务端 MySQL 当前为快照/镜像混合模式，后续可演进为纯关系型业务写入模型。
- UI 自动化测试尚未纳入 CI，当前 CI 主要覆盖 JVM 单元测试和构建。
