package com.lightchat.server.config

import org.junit.Assert.assertEquals
import org.junit.Test

class RuntimeConfigTest {
    @Test
    fun developmentUsesLocalDefaults() {
        val config = RuntimeConfig.from(emptyMap())
        assertEquals("development", config.environment)
        assertEquals(8080, config.serverPort)
        assertEquals(8081, config.httpPort)
    }

    @Test(expected = IllegalArgumentException::class)
    fun productionRejectsMissingSecrets() {
        RuntimeConfig.from(mapOf("APP_ENV" to "production"))
    }

    @Test
    fun productionAcceptsCompleteEnvironment() {
        val config = RuntimeConfig.from(
            mapOf(
                "APP_ENV" to "production",
                "MYSQL_URL" to "jdbc:mysql://rds.internal:3306/lightchat",
                "MYSQL_USER" to "lightchat",
                "MYSQL_PASSWORD" to "strong-password",
                "JWT_SECRET" to "0123456789abcdef0123456789abcdef",
                "MOCK_PUSH_KEY" to "0123456789abcdef01234567",
                "ALIYUN_OSS_ACCESS_KEY_ID" to "test-id",
                "ALIYUN_OSS_ACCESS_KEY_SECRET" to "test-secret",
                "ALIYUN_OSS_BUCKET" to "lightchat",
                "ALIYUN_OSS_ENDPOINT" to "oss-cn-hangzhou.aliyuncs.com"
            )
        )
        assertEquals(true, config.production)
    }
}
