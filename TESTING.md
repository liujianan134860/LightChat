# LightChat 测试文档

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
