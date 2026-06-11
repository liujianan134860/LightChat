# LightChat 测试文档

## 2026-06-11 本次验证结果

本次文档更新前已重新执行自动化测试和完整构建：

| 命令 | 结果 |
| --- | --- |
| `.\gradlew.bat :app:testDebugUnitTest :server:test` | 通过，`BUILD SUCCESSFUL in 7s` |
| `.\gradlew.bat :app:assembleDebug :server:installDist` | 通过，`BUILD SUCCESSFUL in 15s` |

本次还用代码检索确认了工具使用要求：

| 要求 | 结果 |
| --- | --- |
| Kotlin | 通过，客户端和服务端均使用 Kotlin |
| OkHttp WebSocket + 自定义协议帧 | 通过，客户端使用 `OkHttpClient.newWebSocket`，协议由 `ProtocolCodec` 编解码 |
| SQLiteOpenHelper | 通过，客户端使用手写 `DatabaseHelper : SQLiteOpenHelper` |
| Coroutines + Flow + Channel/串行事件处理 | 通过，项目使用 CoroutineScope、StateFlow、SharedFlow，并通过同步/事件处理层串行落库 |
| Jetpack Compose | 通过，主要 UI 均为 `@Composable` |
| Wireshark/Charles 抓包 | 已使用 Wireshark；项目文档说明抓包方式，截图作为验收材料单独提交，不纳入 Git 仓库 |

## 验收标准对照

| 项目 | 标准 | 当前验证情况 |
| --- | --- | --- |
| 消息延迟 | 同网环境收发 < 200ms | 历史真机/模拟器联调记录显示同网收发正常；当前 CI 不测真实网络延迟，需要真机或模拟器现场复测 |
| 消息有序 | 100 条并发发送，接收端 100% 顺序正确 | 单元测试覆盖协议、事件排序和聊天定位索引；真实 100 条并发收发属于端到端测试，需现场用双端设备复测 |
| 重连成功率 | 模拟 50 次断网，重连成功率 100% | 单元测试覆盖连接注册、在线/离线投递和推送入队；50 次断网属于设备级压力测试，需现场复测 |
| 数据库性能 | 1 万条消息，会话列表加载 < 200ms | 已注入并验证过 1 万条消息性能数据，代码已使用分页、索引和 `conversationSeq` cursor；当前 CI 不跑本地设备性能测试，需现场复测 |

说明：CI 能稳定验证纯 JVM 逻辑和构建完整性；延迟、断网重连、真机会话列表性能属于设备/网络相关验收，不适合放进 GitHub Actions 的普通单元测试，需要使用真机或模拟器在同网环境下复测。

## 抓包验证指南

项目 WebSocket 默认端口为 `8080`，HTTP API 默认端口为 `8081`。Wireshark 抓包时建议：

1. 如果抓本机服务端与模拟器通信，优先选择有流量的 WLAN/VMware/Android tcpdump 接口；如果服务端和客户端都走 `127.0.0.1`，可选择 loopback。
2. 常用显示过滤器：

```text
websocket || tcp.port == 8080
```

3. 只看 WebSocket 二进制帧：

```text
websocket
```

4. 只看 HTTP 图片/登录接口：

```text
tcp.port == 8081 || http
```

抓到发送消息包后，应能看到端口 `8080` 上的 WebSocket Binary Frame。当前协议未使用 WSS，加密不由协议本身完成；如果需要防止明文被抓，应部署 HTTPS/WSS。

## 测试范围

当前 CI 覆盖两个 Gradle 模块，并在模块内拆分为更小粒度的单元测试：

- `app`：Android 客户端纯 JVM 单元测试，覆盖自定义二进制协议、命令映射、会话 ID 生成、聊天时间戳显示规则、聊天列表定位索引等不依赖设备的核心逻辑。
- `server`：服务端 JVM 单元测试，覆盖协议编解码、密码哈希校验、用户注册登录、群聊成员、用户事件序号、连接注册表、消息投递和离线推送入队等核心业务逻辑。

## 当前测试清单

### App

- `ConversationIdTest`：验证单聊会话 ID 的稳定性和顺序无关性。
- `ProtocolCodecTest`：验证客户端自定义二进制协议编解码与 CRC 校验。
- `CmdTest`：验证客户端命令号和命令名映射，避免新增命令遗漏。
- `ChatMessageTimeTest`：验证聊天时间戳的 5 分钟显示规则。
- `ChatScrollControllerTest`：验证聊天列表中消息行、时间戳行、加载行的索引计算，防止搜索定位和高亮错位。

### Server

- `ProtocolCodecTest`：验证服务端协议编解码和非法包拒绝。
- `PasswordHasherTest`：验证密码哈希、校验和旧密码升级判断。
- `DataStoreTest`：验证用户注册登录、好友关系、群聊成员、持久化恢复等数据层行为。
- `EventServiceTest`：验证 `userSeq` 修复、增量事件排序和同步结果 JSON。
- `ConnectionRegistryTest`：验证 WebSocket 连接和用户 ID 的双向映射、上下线状态。
- `MessageDeliveryServiceTest`：验证单聊/群聊收件人计算、在线通知、离线推送入队和会话参与者维护。

## 本地运行

```powershell
.\gradlew.bat :app:testDebugUnitTest :server:test
```

完整构建：

```powershell
.\gradlew.bat :app:assembleDebug :server:installDist
```

## CI 流程

GitHub Actions 在 `push`、`pull_request` 和手动触发时运行：

1. 拉取代码。
2. 配置 JDK 17 和 Android SDK。
3. 执行 `:app:testDebugUnitTest` 与 `:server:test`。
4. 执行 `:app:assembleDebug` 与 `:server:installDist`。

CI 不启动 MySQL、不连接阿里云 OSS、不连接模拟器或真机，因此不会依赖本机启动脚本、AccessKey、ADB 设备映射或本地数据库。

## 已排除内容

以下内容属于本地调试数据或敏感配置，不进入 GitHub：

- `start-all.ps1` / `start-server.ps1` / `stop-all.ps1`
- `local.properties`
- `mysql-data/`
- `*.db`
- `*.log`
- 截图、录屏、UI dump、性能分析产物
- 阿里云 OSS AccessKey、本机 ADB 设备映射等本地配置

## 后续建议

- 对数据库 DAO 增加 Robolectric 或 instrumentation 测试。
- 对 WebSocket 链路增加端到端集成测试。
- 对图片上传、签名 URL 刷新和本地缓存增加可注入 HTTP client 的单元测试。
