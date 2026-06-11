# LightChat

LightChat 是一个用于学习和实践 IM 核心链路的 Android 即时通讯项目。项目包含 Android 客户端和 Kotlin/JVM 服务端，围绕单聊、群聊、好友关系、消息同步、图片消息、已读回执、撤回、转发、离线通知等功能做了完整实现。

> 当前仓库以源码、构建配置、测试文档和 CI 配置为主；本机启动脚本、数据库目录、测试截图、录屏、AccessKey 等调试产物不会上传到 GitHub。

## 项目结构

```text
LightChat/
├── app/                    # Android 客户端
├── server/                 # Kotlin/JVM 服务端
├── .github/workflows/      # GitHub Actions CI
├── gradle/                 # Gradle wrapper 配置
├── build.gradle.kts
├── settings.gradle.kts
├── TESTING.md              # 测试范围与运行方式
└── README.md
```

## 技术栈

| 模块 | 技术 |
| --- | --- |
| Android | Kotlin, Jetpack Compose, Material 3, Navigation Compose |
| 客户端存储 | SQLiteOpenHelper, DAO, Repository |
| 客户端网络 | OkHttp HTTP + WebSocket |
| 服务端 | Kotlin/JVM, Netty WebSocket, JDK HttpServer |
| 服务端存储 | MySQL 快照持久化 |
| 媒体存储 | 阿里云 OSS |
| 协议 | 自定义二进制协议 + JSON body + CRC32 |
| 测试 | JUnit4, Gradle, GitHub Actions |

## 功能勾选表

| 模块 | 状态 | 已实现功能 |
| --- | --- | --- |
| 账号体系 | ✅ | 注册、登录、自动登录、退出登录 |
| 账号体系 | ✅ | JWT 签发、Bearer 鉴权、服务端会话校验、账号异地登录踢旧连接 |
| 账号体系 | ✅ | 密码哈希存储，支持旧明文密码登录后自动升级为哈希 |
| 个人资料 | ✅ | 昵称、头像、个性签名、地区编辑 |
| 个人资料 | ✅ | 用户资料服务端同步，好友端收到 `USER_UPDATE` 后更新本地缓存 |
| 个人资料 | ✅ | 头像 URL、头像版本号、本地头像缓存、远端头像拉取 |
| 好友关系 | ✅ | 按账号搜索用户 |
| 好友关系 | ✅ | 发送好友申请、收到好友申请通知、同意申请、拒绝申请 |
| 好友关系 | ✅ | 好友关系双向建立，自动创建单聊会话 |
| 好友关系 | ✅ | 联系人列表分页加载和联系人搜索 |
| 会话列表 | ✅ | 单聊、群聊会话展示 |
| 会话列表 | ✅ | 会话置顶、免打扰、隐藏、删除、标记未读 |
| 会话列表 | ✅ | 未读计数、免打扰红点、底部导航未读汇总 |
| 会话列表 | ✅ | @我摘要，支持 `[有人@我]` 和多条 @我 数量展示 |
| 会话列表 | ✅ | LightChat 助手欢迎会话，新用户默认展示“欢迎使用LightChat!” |
| 单聊消息 | ✅ | 文本消息发送、接收、ACK、失败状态、重发 |
| 单聊消息 | ✅ | 消息本地排序，失败消息保持正确时间顺序 |
| 单聊消息 | ✅ | 已读回执，双方停留聊天详情页时可即时更新 |
| 单聊消息 | ✅ | 消息撤回，限制本人 2 分钟内撤回 |
| 单聊消息 | ✅ | 删除本地气泡，不影响对方消息 |
| 群聊消息 | ✅ | 群聊创建、群成员同步、群成员列表 |
| 群聊消息 | ✅ | 邀请群成员，聊天中展示“xxx邀请xxx加入了群聊”系统提示 |
| 群聊消息 | ✅ | 群聊文本消息收发和群成员投递 |
| 群聊消息 | ✅ | 群聊已读人数 `x/y 已读` |
| 群聊消息 | ✅ | @成员、@多人、@所有人 |
| 群聊消息 | ✅ | @我的未读消息进入群聊后定位并高亮 |
| 群聊消息 | ✅ | @他人的消息单独显示被 @ 人的已读/未读状态 |
| 长按菜单 | ✅ | 文字、emoji、图片、名片、合并转发气泡长按菜单 |
| 长按菜单 | ✅ | 复制、转发、多选、撤回、删除等操作按消息类型显示 |
| 长按菜单 | ✅ | 微信式气泡附近浮层菜单，不再使用底部弹窗 |
| 多选模式 | ✅ | 多选消息、取消多选、左侧选择框、批量删除、批量转发 |
| 转发 | ✅ | 单条转发 |
| 转发 | ✅ | 多选后支持逐条转发和合并转发 |
| 转发 | ✅ | 图片、名片、合并转发消息均支持转发 |
| 转发 | ✅ | 合并转发详情页支持文本、图片、名片、嵌套聊天记录展示 |
| 名片 | ✅ | 推荐好友名片 |
| 名片 | ✅ | 名片消息可点击查看资料，非好友可从资料页添加好友 |
| 名片 | ✅ | 名片消息支持长按、转发、多选、撤回、删除 |
| 图片消息 | ✅ | 相册网格选择、多图选择、按选择顺序发送 |
| 图片消息 | ✅ | 相册分页加载、快速滑动选择、预览页选择状态保持 |
| 图片消息 | ✅ | 图片预览、放大、缩小、左右滑动切换 |
| 图片消息 | ✅ | 图片编辑、涂鸦编辑、编辑后仅在确认后生效 |
| 图片消息 | ✅ | 发送端直接显示本地原图 |
| 图片消息 | ✅ | 接收端先显示缩略图，点击全屏或后台拉取后替换为原图 |
| 图片消息 | ✅ | 全屏图片滑动切换时自动拉取当前图片原图 |
| 图片消息 | ✅ | 全屏图片下载到系统相册 |
| 图片消息 | ✅ | 图片全屏转发 |
| 媒体存储 | ✅ | HTTP multipart 上传原图和缩略图 |
| 媒体存储 | ✅ | 阿里云 OSS 对象存储，支持公开 URL 和私有签名 URL |
| 媒体存储 | ✅ | 图片 URL 过期后刷新签名 URL |
| 媒体存储 | ✅ | 本地图片、缩略图、头像缓存优先加载 |
| 搜索 | ✅ | 首页全局搜索联系人和聊天记录 |
| 搜索 | ✅ | 单聊右上角会话内搜索 |
| 搜索 | ✅ | 群聊右上角会话内搜索 |
| 搜索 | ✅ | 搜索结果点击后跳转目标消息并高亮 3 秒 |
| 搜索 | ✅ | 长聊天记录定位到屏幕中部，避免目标消息出现在窗口外 |
| 搜索 | ✅ | 搜索聊天记录时排除图片路径 |
| 聊天分页 | ✅ | 历史消息分页加载 |
| 聊天分页 | ✅ | 定位到中间消息后支持向上加载历史和向下加载新消息 |
| 聊天分页 | ✅ | 一万条消息场景下的中间定位和滚动优化 |
| 聊天输入 | ✅ | 文本输入、重新编辑撤回消息、@成员后光标定位到尾部 |
| 聊天输入 | ✅ | 真机键盘适配，输入栏跟随键盘 |
| 页面动画 | ✅ | 聊天详情、资料页、名片页、群成员页等打开/关闭配套滑动动画 |
| 页面动画 | ✅ | 相册页上滑打开、下滑关闭 |
| 页面动画 | ✅ | 图片全屏从聊天气泡放大进入，退出时缩回原气泡位置 |
| 通知 | ✅ | 前台/后台消息通知分流 |
| 通知 | ✅ | 好友申请推送通知 |
| 通知 | ✅ | 群聊推送显示群聊名和消息摘要 |
| 通知 | ✅ | 免打扰会话不弹对应推送，且不参与底部未读汇总 |
| Mock 推送 | ✅ | WebSocket 断开后服务端写入 mock push 队列 |
| Mock 推送 | ✅ | ADB 显式广播模拟厂商推送，应用被杀后可展示本地通知 |
| 长连接 | ✅ | OkHttp WebSocket 长连接 |
| 长连接 | ✅ | AUTH 鉴权、20 秒心跳、连续 3 次心跳失败重连 |
| 长连接 | ✅ | 指数退避重连，1s -> 2s -> 4s -> 8s -> 16s -> 30s |
| 长连接 | ✅ | 网络恢复、应用回前台后自动恢复连接 |
| 自定义协议 | ✅ | Magic、Version、Cmd、Seq、Body Length、Body、CRC32 |
| 自定义协议 | ✅ | 客户端和服务端同源命令映射，当前支持 19 个业务命令和 ERROR |
| 同步模型 | ✅ | `NEW_EVENT_NOTIFY -> SYNC -> SYNC_RESULT` 增量同步 |
| 同步模型 | ✅ | `userSeq` 用户维度同步进度 |
| 同步模型 | ✅ | `conversationSeq` 会话内最终排序 |
| 同步模型 | ✅ | `clientSeq` 客户端本地发送顺序 |
| 同步模型 | ✅ | EventProcessor 串行处理网络事件并写入 SQLite |
| 同步模型 | ✅ | 先落库再推进 `lastUserSeq`，避免同步中断丢消息 |
| 服务端 | ✅ | Netty WebSocket 服务端 |
| 服务端 | ✅ | JDK HttpServer HTTP API |
| 服务端 | ✅ | DataStore 内存状态管理 |
| 服务端 | ✅ | MySQL 状态快照持久化 |
| 服务端 | ✅ | 用户收件箱 InboxEvent、事件序号修复、同步结果分页 |
| 服务端 | ✅ | MessageDeliveryService 收件人判定、在线通知、离线推送入队 |
| 本地数据库 | ✅ | SQLite 多账号隔离 |
| 本地数据库 | ✅ | 用户、好友、好友申请、会话、消息、群组、群成员、同步状态等本地表 |
| 本地数据库 | ✅ | 会话置顶排序、消息分页查询、搜索查询 |
| CI 与测试 | ✅ | GitHub Actions 自动运行单元测试和构建 |
| CI 与测试 | ✅ | App 协议、命令、会话 ID、时间戳、聊天定位索引单元测试 |
| CI 与测试 | ✅ | Server 协议、密码、DataStore、EventService、连接注册、消息投递单元测试 |
| 工程化 | ✅ | `.gitignore` 排除启动脚本、本地数据库、截图录屏、密钥和调试产物 |
| 工程化 | ✅ | `TESTING.md` 记录测试范围和 CI 流程 |

## 架构概览

```text
Android UI (Compose)
        ↓
ViewModel + StateFlow
        ↓
Repository
        ↓
DAO + SQLite
        ↓
EventProcessor / SyncManager / ImClient
        ↓
OkHttp WebSocket + HTTP
        ↓
Netty WebSocket Server + HTTP API
        ↓
DataStore + EventService + MySQL snapshot
```

客户端 UI 不直接依赖网络结果展示消息，消息、会话、用户和群组状态会先落到 SQLite，再由本地数据驱动页面刷新。服务端维护内存状态并通过 MySQL 保存状态快照。

## 自定义协议

WebSocket 使用二进制包，包体是 UTF-8 JSON。

```text
┌──────────┬─────────┬────────┬────────┬──────────┬─────────┬────────┐
│ Magic(2) │ Ver(1)  │ Cmd(1) │ Seq(8) │ Len(4)   │ Body(N) │ CRC(4) │
│ 0x4C43   │ 1       │ byte   │ int64  │ int32    │ JSON    │ CRC32  │
└──────────┴─────────┴────────┴────────┴──────────┴─────────┴────────┘
```

当前命令号：

| Cmd | 名称 | 方向 |
| --- | --- | --- |
| 1 | AUTH | C -> S |
| 2 | AUTH_ACK | S -> C |
| 3 | HEARTBEAT | C -> S |
| 4 | HEARTBEAT_ACK | S -> C |
| 5 | NEW_EVENT_NOTIFY | S -> C |
| 6 | SYNC | C -> S |
| 7 | SYNC_RESULT | S -> C |
| 8 | RECALL_MESSAGE | C -> S / S -> C ACK |
| 9 | CREATE_GROUP | C -> S / S -> C ACK |
| 10 | SEND_MESSAGE | C -> S |
| 11 | MESSAGE_ACK | S -> C |
| 12 | SEND_FRIEND_REQUEST | C -> S |
| 13 | ACCEPT_FRIEND_REQUEST | C -> S |
| 14 | MARK_READ | C -> S |
| 15 | UPDATE_PROFILE | C -> S |
| 16 | ADD_GROUP_MEMBERS | C -> S |
| 17 | REJECT_FRIEND_REQUEST | C -> S |
| 18 | READ_NOTIFY | S -> C |
| 19 | UPDATE_CONVERSATION_SETTINGS | C -> S |
| 99 | ERROR | S -> C |

## 消息同步模型

LightChat 采用类似 seqsvr 的用户维度增量同步：

```text
服务端生成 InboxEvent(userSeq)
        ↓
在线用户收到 NEW_EVENT_NOTIFY(latestUserSeq)
        ↓
客户端发现 latestUserSeq > localLastUserSeq
        ↓
客户端发送 SYNC(lastUserSeq)
        ↓
服务端返回 userSeq 更大的事件列表
        ↓
客户端 EventProcessor 串行处理事件并写入 SQLite
        ↓
落库成功后更新 lastUserSeq
```

关键原则是先落库，再推进 `lastUserSeq`。这样即使客户端处理过程中崩溃，下次仍可以从旧的 `lastUserSeq` 重新拉取，避免丢消息。

## 三套序列号

| 字段 | 生成方 | 作用 |
| --- | --- | --- |
| `clientSeq` | 客户端 | 标识本地发送顺序，用于发送中、失败、重发等本地状态管理。 |
| `conversationSeq` | 服务端 | 标识同一会话内的消息顺序，用于最终展示排序和已读进度。 |
| `userSeq` | 服务端 | 标识某个用户收件箱中的事件顺序，用于增量同步。 |

## 数据存储

### Android SQLite

当前本地数据库版本为 `12`，主要表包括：

- `user`
- `friend`
- `friend_request`
- `conversation`
- `message`
- `im_group`
- `group_member`
- `sync_state`
- `conversation_member`
- `message_receipt`
- `auth_session`

### 服务端 MySQL

服务端默认连接：

```text
jdbc:mysql://127.0.0.1:3307/lightchat?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&createDatabaseIfNotExist=true
```

服务端通过 `MySqlStatePersistence` 将完整状态快照保存到 MySQL。当前 GitHub 仓库不上传本机 MySQL 数据目录。

可用环境变量：

| 变量 | 默认值 | 说明 |
| --- | --- | --- |
| `MYSQL_URL` | `jdbc:mysql://127.0.0.1:3307/lightchat?...` | MySQL JDBC 地址 |
| `MYSQL_USER` | `root` | MySQL 用户名 |
| `MYSQL_PASSWORD` | 空 | MySQL 密码 |
| `MYSQL_STATE_KEY` | `default` | 状态快照 key |

## 媒体存储

图片上传走 HTTP 接口，消息本身只通过 WebSocket 发送图片元信息。服务端当前支持阿里云 OSS。

必填环境变量：

| 变量 | 说明 |
| --- | --- |
| `MEDIA_STORAGE_PROVIDER` | 设为 `aliyun_oss`，也可省略，默认就是阿里云 OSS |
| `ALIYUN_OSS_ACCESS_KEY_ID` | AccessKey ID |
| `ALIYUN_OSS_ACCESS_KEY_SECRET` | AccessKey Secret |
| `ALIYUN_OSS_BUCKET` | Bucket 名称 |
| `ALIYUN_OSS_ENDPOINT` | 例如 `oss-cn-beijing.aliyuncs.com` |

可选环境变量：

| 变量 | 默认值 | 说明 |
| --- | --- | --- |
| `ALIYUN_OSS_KEY_PREFIX` | `lightchat` | 对象 key 前缀 |
| `ALIYUN_OSS_PUBLIC_READ` | `true` | 是否直接返回公开 URL |
| `ALIYUN_OSS_SIGNED_URL_SECONDS` | `3600` | 私有 Bucket 签名 URL 有效期 |
| `ALIYUN_OSS_PUBLIC_BASE_URL` | 空 | 自定义域名或 CDN 域名 |

接收端会先展示缩略图，进入全屏或后台拉取成功后再替换为原图。本地会缓存头像、缩略图和原图。

## HTTP API

服务端 HTTP 默认端口为 `8081`。

| 路径 | 方法 | 说明 |
| --- | --- | --- |
| `/api/register` | POST | 注册 |
| `/api/login` | POST | 登录 |
| `/api/bootstrap` | GET | 登录后拉取用户基础数据 |
| `/api/users/search` | GET | 搜索用户 |
| `/api/users/profile` | GET | 拉取用户资料 |
| `/api/images/upload` | POST | 上传原图和缩略图 |
| `/api/images/refresh-url` | POST | 刷新私有 OSS 签名 URL |
| `/api/mock-push/pending` | GET | 本地 mock 推送拉取待投递消息 |

除注册、登录和 mock 推送接口外，其余接口需要 `Authorization: Bearer <token>`。

## 本地运行

### 环境要求

- JDK 17
- Android Studio / Android SDK
- MySQL 8.x
- 阿里云 OSS Bucket 和 AccessKey

### 1. 配置服务端环境变量

PowerShell 示例：

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
```

### 2. 启动服务端

```powershell
.\gradlew.bat :server:run
```

默认服务：

```text
WebSocket: ws://<服务端IP>:8080/ws
HTTP:      http://<服务端IP>:8081
```

### 3. 配置 Android 客户端服务地址

当前客户端服务地址写在代码里，需要按你的电脑局域网 IP 修改：

- HTTP: [AuthApiClient.kt](app/src/main/java/com/lightchat/data/remote/AuthApiClient.kt)
- WebSocket: [ConnectionManager.kt](app/src/main/java/com/lightchat/im/ConnectionManager.kt)

示例：

```kotlin
const val DEFAULT_BASE_URL = "http://192.168.1.10:8081"
const val DEFAULT_URL = "ws://192.168.1.10:8080/ws"
```

模拟器访问宿主机时可以使用 Android 模拟器网关地址；真机需要和电脑在同一网络下，并使用电脑的局域网 IP。

### 4. 构建 APK

```powershell
.\gradlew.bat :app:assembleDebug
```

输出路径：

```text
app/build/outputs/apk/debug/app-debug.apk
```

## 测试与 CI

本地运行单元测试：

```powershell
.\gradlew.bat :app:testDebugUnitTest :server:test
```

完整构建：

```powershell
.\gradlew.bat :app:assembleDebug :server:installDist
```

GitHub Actions 会在 `push`、`pull_request` 和手动触发时执行：

1. 配置 JDK 17 和 Android SDK。
2. 运行 Android 与服务端单元测试。
3. 构建 Android debug APK 和服务端 installDist。

测试范围详见 [TESTING.md](TESTING.md)。

## 离线通知说明

当前项目没有接入真实厂商推送 SDK，而是提供了本地 Mock 厂商推送能力：

- 服务端发现用户 WebSocket 不在线时，将消息写入 mock push 队列。
- 本地开发环境可以通过 ADB 显式广播投递给 Android。
- `MockVendorPushReceiver` 收到广播后展示系统通知，并支持从通知进入对应会话。

相关环境变量：

| 变量 | 说明 |
| --- | --- |
| `MOCK_PUSH_KEY` | 拉取 mock 推送队列的密钥，默认 `lightchat-local-mock` |
| `MOCK_PUSH_ADB_PATH` | adb 路径，默认 `adb` |
| `MOCK_PUSH_ADB_DEVICES` | 用户 ID 到设备序列号的映射 |
| `MOCK_PUSH_ADB_ALL` | 没有单独映射时广播到的设备列表 |

正式线上环境应接入 FCM、华为、小米、OPPO、vivo 等厂商推送 SDK，由 Android 上报 push token，服务端调用厂商 API 投递通知。

## 安全说明

- WebSocket 自定义协议当前只做结构校验和 CRC32，不等于加密。
- 如果使用 `ws://` 和 `http://`，局域网抓包可以看到明文 JSON body。
- 生产环境应使用 HTTPS/WSS，并配置可信证书。
- AccessKey、MySQL 密码、JWT Secret 不能提交到仓库。
- README 中的变量名只用于说明，真实密钥应通过环境变量或密钥管理服务配置。

## 当前限制

- 客户端服务地址仍是编译期常量，尚未抽成 BuildConfig 或运行时配置。
- 服务端状态以 MySQL 快照方式保存，尚未拆成完整关系型业务表写入模型。
- Mock 厂商推送只适合本地调试，不代表真实离线推送能力。
- UI 自动化测试尚未接入，当前 CI 主要覆盖纯 JVM 单元测试和构建。
- 图片存储当前只支持阿里云 OSS。

## 贡献与开发建议

- 业务代码修改后先运行 `:app:testDebugUnitTest` 和 `:server:test`。
- 涉及协议命令时，同时修改客户端和服务端的 `Cmd`、`ProtocolCodec` 和相关测试。
- 涉及消息同步时，优先保证“落库成功后再推进 `lastUserSeq`”。
- 涉及图片或头像时，保留本地缓存优先策略，避免列表反复网络加载。
