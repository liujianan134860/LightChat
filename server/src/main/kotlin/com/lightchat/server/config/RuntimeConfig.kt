package com.lightchat.server.config

data class RuntimeConfig(
    val environment: String,
    val serverPort: Int,
    val httpPort: Int,
    val mysqlUrl: String,
    val mysqlUser: String,
    val mysqlPassword: String
) {
    val production: Boolean
        get() = environment.equals("production", ignoreCase = true)

    companion object {
        fun from(environment: Map<String, String> = System.getenv()): RuntimeConfig {
            val config = RuntimeConfig(
                environment = environment["APP_ENV"]?.trim().orEmpty().ifBlank { "development" },
                serverPort = environment["SERVER_PORT"]?.toIntOrNull() ?: 8080,
                httpPort = environment["SERVER_HTTP_PORT"]?.toIntOrNull() ?: 8081,
                mysqlUrl = environment["MYSQL_URL"]
                    ?: "jdbc:mysql://127.0.0.1:3307/lightchat?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&createDatabaseIfNotExist=true",
                mysqlUser = environment["MYSQL_USER"] ?: "root",
                mysqlPassword = environment["MYSQL_PASSWORD"].orEmpty()
            )
            config.validate(environment)
            return config
        }
    }

    private fun validate(environment: Map<String, String>) {
        require(serverPort in 1..65535) { "SERVER_PORT 必须是有效端口" }
        require(httpPort in 1..65535) { "SERVER_HTTP_PORT 必须是有效端口" }
        require(serverPort != httpPort) { "WebSocket 与 HTTP 端口不能相同" }
        if (!production) return

        val jwtSecret = environment["JWT_SECRET"].orEmpty()
        require(jwtSecret.length >= 32 && jwtSecret != "lightchat-dev-secret-change-me") {
            "生产环境必须设置至少 32 字符的 JWT_SECRET"
        }
        require(mysqlPassword.isNotBlank()) {
            "生产环境必须设置 MYSQL_PASSWORD"
        }
        require(environment["MYSQL_URL"].orEmpty().isNotBlank()) {
            "生产环境必须显式设置 MYSQL_URL"
        }
        require(environment["MOCK_PUSH_KEY"].orEmpty().length >= 24) {
            "生产环境必须设置至少 24 字符的 MOCK_PUSH_KEY"
        }
        require(!environment["ALIYUN_OSS_ACCESS_KEY_ID"].isNullOrBlank()) {
            "生产环境必须设置 ALIYUN_OSS_ACCESS_KEY_ID"
        }
        require(!environment["ALIYUN_OSS_ACCESS_KEY_SECRET"].isNullOrBlank()) {
            "生产环境必须设置 ALIYUN_OSS_ACCESS_KEY_SECRET"
        }
        require(!environment["ALIYUN_OSS_BUCKET"].isNullOrBlank()) {
            "生产环境必须设置 ALIYUN_OSS_BUCKET"
        }
        require(!environment["ALIYUN_OSS_ENDPOINT"].isNullOrBlank()) {
            "生产环境必须设置 ALIYUN_OSS_ENDPOINT"
        }
    }
}
