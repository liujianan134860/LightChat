# LightChat — 自研 IM 客户端

参考微信聊天产品形态和 seqsvr 同步思想设计的自研即时通讯客户端。

## 技术栈

| 层面 | 技术选型 |
|------|----------|
| 语言 | Kotlin |
| UI | Jetpack Compose + Material 3 |
| 架构 | MVVM (ViewModel + StateFlow) |
| 数据库 | SQLite (SQLiteOpenHelper) |
| 网络 | OkHttp WebSocket |
| 协议 | 自定义二进制协议 (Header + Body + CRC) |
| 导航 | Navigation Compose |

## 服务端启动

一键启动本地 MySQL 和 LightChat 服务端：

```powershell
.\start-all.ps1
```

如果 8080/8081 已经有旧服务端占用：

```powershell
.\start-all.ps1 -RestartServer
```

停止服务端：

```powershell
.\stop-all.ps1
```

只停止服务端，保留项目本地 MySQL：

```powershell
.\stop-all.ps1 -KeepMysql
```

默认使用 JSON 文件持久化：

```powershell
.\start-server.ps1 -Storage json
```

使用本机 MySQL 持久化前，先检查并初始化数据库：

```powershell
.\scripts\start-local-mysql.ps1
.\scripts\check-mysql.ps1 -Init -User root -Password "你的密码"
.\start-server.ps1 -MysqlUser root -MysqlPassword "你的密码"
```

MySQL 模式默认连接 `127.0.0.1:3307/lightchat`。初始化 SQL 位于 `scripts/init-mysql.sql`，当前服务端会把完整状态快照写入 `lightchat_state` 表。

服务端登录鉴权使用 HMAC-SHA256 JWT。开发环境默认 `JwtSecret` 可直接使用；如果要模拟更真实的环境，可以启动时指定：

```powershell
.\start-all.ps1 -JwtSecret "换成你自己的长随机字符串"
```

如果使用项目自带的本地开发实例，root 默认无密码：

```powershell
.\scripts\start-local-mysql.ps1
.\scripts\check-mysql.ps1 -Init -User root
.\start-server.ps1 -MysqlUser root
```

## 系统架构

```
┌─────────────────────────────────────────────────────┐
│                   UI Layer (Compose)                 │
│  LoginScreen │ MainScreen │ ChatScreen │ ...12 routes│
├─────────────────────────────────────────────────────┤
│               ViewModel (StateFlow)                  │
├─────────────────────────────────────────────────────┤
│                Repository Layer                      │
├─────────────────────────────────────────────────────┤
│            DAO Layer (ContentValues/Cursor)          │
├─────────────────────────────────────────────────────┤
│              SQLite (11 tables, v8)                  │
└─────────────────────────────────────────────────────┘
                            ↕
┌─────────────────────────────────────────────────────┐
│                    IM Layer                          │
│  ImClient → ConnectionManager → WebSocket            │
│       ↓                      ↓                       │
│  SyncManager            HeartbeatManager             │
│       ↓                  ReconnectManager            │
│  EventProcessor                                      │
├─────────────────────────────────────────────────────┤
│               Protocol Layer                         │
│  ProtocolCodec (encode/decode + CRC32)               │
│  Packet │ Cmd (18 command types)                     │
└─────────────────────────────────────────────────────┘
```

## 自定义协议

```
┌─────────┬─────────┬─────────┬─────────┬───────────┬─────────┬─────────┐
│ Magic(2)│ Ver(1)  │ Cmd(1)  │ Seq(8)  │ Len(4)    │ Body(N) │ CRC(4)  │
│ 0x4C43  │   1     │  1-99   │ uint64  │ uint32    │ JSON    │ CRC32   │
└─────────┴─────────┴─────────┴─────────┴───────────┴─────────┴─────────┘
```

### 命令类型

| Cmd | 名称 | 说明 |
|-----|------|------|
| 1 | AUTH | 客户端认证 |
| 2 | AUTH_ACK | 认证确认 |
| 3 | HEARTBEAT | 心跳 |
| 4 | HEARTBEAT_ACK | 心跳确认 |
| 5 | NEW_EVENT_NOTIFY | 新事件通知 (携带 latestUserSeq) |
| 6 | SYNC | 增量同步请求 |
| 7 | SYNC_RESULT | 同步结果 (事件列表) |
| 8 | RECALL_MESSAGE | 撤回消息 (C→S) |
| 9 | CREATE_GROUP | 创建群组 (C→S) |
| 10 | SEND_MESSAGE | 发送消息 |
| 11 | MESSAGE_ACK | 消息确认 |
| 12 | SEND_FRIEND_REQUEST | 发送好友申请 (C→S) |
| 13 | ACCEPT_FRIEND_REQUEST | 同意好友申请 (C→S) |
| 14 | MARK_READ | 已读回执 (C→S) |
| 15 | UPDATE_PROFILE | 更新个人资料 (C→S) |
| 16 | ADD_GROUP_MEMBERS | 邀请群成员 (C→S) |
| 18 | READ_NOTIFY | 在线即时已读通知 (S→C) |
| 99 | ERROR | 错误 |

## seqsvr 同步模型

```
服务端推送 NEW_EVENT_NOTIFY(latestUserSeq)
  ↓
客户端发现 latestUserSeq > localLastUserSeq
  ↓
客户端发送 SYNC(lastUserSeq)
  ↓
服务端返回 userSeq > lastUserSeq 的事件列表
  ↓
EventProcessor 按 userSeq 顺序处理 9 种事件:
  NEW_MESSAGE(1) → 写入 message 表 + 更新 conversation
  MESSAGE_RECALL(2) → 标记消息撤回
  MESSAGE_READ(3) → 更新已读状态
  GROUP_CREATED(4) → 创建群组 + 成员 + 会话
  GROUP_MEMBER_JOIN(5) → 添加群成员
  GROUP_MEMBER_LEAVE(6) → 移除群成员
  FRIEND_REQUEST(7) → 写入好友申请表
  FRIEND_ACCEPTED(8) → 建立好友关系
  USER_UPDATE(9) → 更新用户资料
  ↓
更新 lastUserSeq
```

核心原则：**先成功落库，再更新 lastUserSeq**，防止数据丢失。

## 消息状态机

```
CREATED → SENDING → SENT → DELIVERED → READ
                ↘ (10s超时) → FAILED → (手动重发) → SENDING
```

## 数据库表 (11 张, v8)

| 表 | 说明 | 关键字段 |
|----|------|----------|
| user | 用户 | user_id, nickname, avatar, signature, region |
| friend | 好友关系 | user_id, friend_id |
| friend_request | 好友申请 | from_user_id, to_user_id, status |
| conversation | 会话 | type, last_message, unread_count, at_me_count, is_pinned, is_hidden, mute |
| message | 消息 | message_id(幂等), client_seq, conversation_seq, user_seq |
| im_group | 群组 | group_id, group_name, owner_id, member_count |
| group_member | 群成员 | group_id, user_id, role |
| sync_state | 同步状态 | key-value (last_user_seq) |
| conversation_member | 会话成员镜像 | last_read_seq, unread_count, mention_count |
| message_receipt | 消息回执镜像 | message_id, user_id, receipt_type |
| auth_session | 登录会话镜像 | token_id, user_id, expires_at |

### 会话排序

```sql
ORDER BY is_pinned DESC, pinned_time DESC, last_message_time DESC
```

### 消息三套序列号

- `client_seq` — 客户端本地序列号
- `conversation_seq` — 会话内消息顺序
- `user_seq` — 用户维度增量同步序列号

## UI 架构 (13 个路由)

```
NavGraph
├── login → LoginScreen
├── main → MainScreen (底部导航)
│   ├── [消息] Tab → ConversationListScreen
│   │   ├── 长按: 置顶/免打扰/隐藏/删除/标记未读
│   │   └── → ChatScreen (状态指示/撤回/多选转发/@提醒)
│   ├── [联系人] Tab → ContactScreen
│   │   └── → FriendRequestScreen / ProfileScreen / GroupCreateScreen
│   └── [我的] Tab → ProfileScreen (编辑/退出)
├── chat/{id}/{title} → ChatScreen
├── chat_search/{id}/{title} → ChatSearchScreen (会话内搜索)
├── search → SearchScreen (全局搜索: 联系人+聊天记录)
├── add_friend → AddFriendScreen (全库用户搜索+加好友)
├── group_create → GroupCreateScreen (选人+群名)
├── friend_requests → FriendRequestScreen (同意/拒绝)
├── profile?userId={uid} → ProfileScreen (自浏览/编辑/好友/陌生人)
├── forward_select → ForwardSelectScreen (逐条/合并)
└── forward_preview → ForwardPreviewScreen
```

## 核心功能

### 已完成

- [x] 登录/注册 (真实 JWT + 服务端会话表 + 自动登录)
- [x] WebSocket 长连接 + 自定义二进制协议 (18 种命令)
- [x] 心跳检测 (20s) + 指数退避重连 (1s→30s) + 网络恢复/回前台重连
- [x] seqsvr 增量同步模型 (NEW_EVENT_NOTIFY → SYNC → SYNC_RESULT)
- [x] EventProcessor 事件处理 (9 种事件类型)
- [x] 单聊/群聊文本消息 (receiverId/groupId 完整链路)
- [x] 消息状态机 (SENDING→SENT→FAILED, ACK超时重试)
- [x] conversationSeq 服务端确认 + 客户端乱序重排
- [x] messageId 严格幂等 (重复发送复用原 ACK，不重复入库或投递)
- [x] 群成员列表 + 邀请成员 + @多人高亮 + 独立 @我未读计数
- [x] 图片消息 (HTTP 上传 + 阿里云 OSS + 缩略图优先显示 + 原图后台加载 + Base64 兼容回退)
- [x] 消息撤回 (本人 2 分钟窗口 + 服务端 ACK 后落本地状态 + 双端同步)
- [x] 消息转发 (逐条/合并, 实际构造消息发送)
- [x] 多选消息 → 批量转发
- [x] 好友申请 (搜索→发送申请→同意/拒绝, 服务端同步)
- [x] 建群 (多选联系人→群名, 服务端同步, 离线降级)
- [x] 会话操作: 置顶/免打扰/标记未读/不显示/删除
- [x] 会话未读角标
- [x] 个人资料编辑 (昵称/签名/地区 + 服务端同步到好友)
- [x] 全局搜索 (联系人 + 聊天记录)
- [x] 会话内搜索 (ChatSearchScreen, SQL LIKE 查询 + 关键词高亮)
- [x] 添加好友独立页面 (全库搜索 + 好友状态判断)
- [x] 聊天气泡头像 + 点击跳转资料页
- [x] 资料页区分本人/好友/陌生人
- [x] 已读回执 (客户端上报 + 在线 READ_NOTIFY 即时更新 + 服务端同步兜底)
- [x] Mock 厂商推送 (离线队列 + 被杀进程广播唤醒 + 系统通知)
- [x] Debug 调试面板 + 压力测试

### Mock 厂商推送验收

服务端会把每条新消息写入 mock 厂商推送队列。开发环境可用脚本模拟厂商系统向 Android 投递：

```powershell
adb -s emulator-5556 shell am force-stop com.lightchat
.\scripts\deliver-mock-push.ps1 -UserId "接收方账号" -DeviceSerial "emulator-5556"
```

脚本会携带 `X-Mock-Push-Key`，从 `GET /api/mock-push/pending?userId=...` 拉取待投递推送，并通过指定模拟器或真机的显式广播唤醒 `MockVendorPushReceiver`。广播携带 `--include-stopped-packages`，可覆盖本地 `force-stop` 验收场景。接收器会展示系统通知；仅当前台已经打开对应会话时抑制重复通知。用户点击通知后进入对应会话。默认 key 为 `lightchat-local-mock`，可通过服务端环境变量 `MOCK_PUSH_KEY` 和脚本参数 `-MockPushKey` 同步替换。

需要自动投递时，启动持续轮询桥接：

```powershell
.\scripts\watch-mock-push.ps1 `
  -Binding "19970295701=emulator-5554","18970261938=emulator-5556" `
  -PollIntervalMs 200
```

连接模拟器或真机调试时，也可以自动扫描所有 ADB 设备，并读取各设备当前登录账号：

```powershell
.\scripts\watch-mock-push.ps1 -AutoDiscoverConnectedDevices -PollIntervalMs 200 -DiscoveryIntervalMs 1000
```

固定 `-Binding` 模式不会扫描设备，延迟最低。需要支持设备内切换账号时，同时启用 `-AutoDiscoverConnectedDevices`：脚本启动时扫描一次，之后先投递缓存绑定，再默认每 `1s` 刷新映射；在线设备检测到的新账号会覆盖该设备旧的固定映射，固定映射仅作为设备暂时断开 USB 时的兜底。`watch-mock-push.ps1` 是本地开发环境中的 mock 厂商服务，依赖 USB、ADB 和电脑上的桥接脚本。正式发布时，需要接入 FCM、华为、小米、OPPO 等真实推送 SDK：Android 上报 push token，服务端调用厂商 API，系统推送服务负责唤醒应用并投递通知。

### 图片对象存储配置

图片消息的 WebSocket 内容只传 `IMAGE` 消息和图片元信息，图片二进制通过 HTTP 上传给服务端。服务端使用阿里云 OSS 保存原图和缩略图，并在消息 `extra` 中写入：

```json
{
  "fileId": "文件ID",
  "imageUrl": "原图访问地址",
  "thumbnailUrl": "缩略图访问地址",
  "objectKey": "原图对象Key",
  "thumbnailObjectKey": "缩略图对象Key",
  "storageProvider": "aliyun_oss"
}
```

客户端发送端直接显示本机原图；接收端先显示缩略图，后台下载原图成功后自动替换为原图。上传失败时，客户端仍保留 Base64 兼容回退，方便本地调试。

#### 阿里云 OSS

启用阿里云 OSS 前，需要在阿里云控制台创建 Bucket，并准备以下信息：

```text
AccessKey ID / AccessKey Secret
Bucket: 例如 lightchat-media
Endpoint: 例如 oss-cn-beijing.aliyuncs.com、oss-cn-shanghai.aliyuncs.com
```

PowerShell 启动服务端前设置环境变量：

```powershell
$env:MEDIA_STORAGE_PROVIDER="aliyun_oss"
$env:ALIYUN_OSS_ACCESS_KEY_ID="你的 AccessKey ID"
$env:ALIYUN_OSS_ACCESS_KEY_SECRET="你的 AccessKey Secret"
$env:ALIYUN_OSS_BUCKET="lightchat-media"
$env:ALIYUN_OSS_ENDPOINT="oss-cn-beijing.aliyuncs.com"
$env:ALIYUN_OSS_KEY_PREFIX="lightchat"
$env:ALIYUN_OSS_PUBLIC_READ="true"

.\start-server.ps1
```

如果 Bucket 不是公共读，设置：

```powershell
$env:ALIYUN_OSS_PUBLIC_READ="false"
$env:ALIYUN_OSS_SIGNED_URL_SECONDS="3600"
```

这时服务端返回临时签名 URL，有效期默认 1 小时。为了 IM 图片长期可见，当前测试阶段建议先使用公共读，或者配置自定义域名/CDN 后设置：

```powershell
$env:ALIYUN_OSS_PUBLIC_BASE_URL="https://你的图片域名"
```

阿里云 OSS 默认公开访问地址格式是：

```text
https://<Bucket>.<Endpoint>/<objectKey>
```

例如：

```text
https://lightchat-media.oss-cn-beijing.aliyuncs.com/lightchat/images/original/2026/06/04/xxx.jpg
```

### 连接状态机

```
DISCONNECTED → CONNECTING → CONNECTED → AUTHENTICATED
     ↑              ↓           ↓
     └── RECONNECTING ←────────┘
```

## 构建与运行

```bash
cd E:\Android\LightChat
./gradlew assembleDebug
```

输出: `app/build/outputs/apk/debug/app-debug.apk`

### 环境要求

- Android Studio Hedgehog | 2023.1+
- AGP 8.2.2
- Kotlin 1.9.22
- minSdk 26 / targetSdk 34
- JDK 17

## 项目亮点

1. 参考微信 seqsvr 思想 — userSeq 增量同步
2. WebSocket 只负责通知 — SYNC 拉取保证最终一致
3. SQLite 作为本地状态源 — UI 不直接依赖网络
4. EventProcessor 统一事件处理 — 可扩展事件类型
5. messageId 幂等键 — 消息去重
6. 三套序列号 — clientSeq/conversationSeq/userSeq
7. 自定义二进制协议 — CRC32 校验 + 可扩展命令
8. 微信式 UI — 底部导航/长按菜单/转发/撤回/名片
9. MySQL 镜像表 — 支持服务端状态恢复与可视化排查
