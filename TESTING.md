# LightChat 测试文档

## 测试范围

当前 CI 覆盖两个 Gradle 模块：

- `app`：Android 客户端纯 JVM 单元测试，覆盖自定义二进制协议、命令映射、会话 ID 生成等不依赖设备的核心逻辑。
- `server`：服务端 JVM 单元测试，覆盖协议编解码、密码哈希校验、用户注册登录、群聊成员等核心业务逻辑。

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

