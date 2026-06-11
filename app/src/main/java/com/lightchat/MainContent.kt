package com.lightchat

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.navigation.compose.rememberNavController
import com.lightchat.event.AppEvents
import com.lightchat.ui.navigation.LightChatNavGraph
import com.lightchat.ui.theme.LightChatTheme
import kotlinx.coroutines.delay

fun ComponentActivity.showLightChatMainContent(
    app: LightChatApplication,
    startDestination: String,
    initialConversationId: String?,
    initialConversationTitle: String?,
    initialTargetMessageId: String?,
    initialOpenFriendRequests: Boolean,
    loggedInAtLaunch: Boolean,
    onRequestNotificationPermission: () -> Unit
) {
    setContent {
        LightChatTheme {
            LaunchedEffect(Unit) {
                app.imClient.onKicked { reason ->
                    app.syncManager.destroy()
                    app.authRepository.logout()
                    AppEvents.notifyForcedLogout(reason.ifBlank { "账号已在其他设备登录" })
                }
                if (loggedInAtLaunch) {
                    app.tokenManager.getToken()?.let { token ->
                        app.imClient.connect(token)
                        app.syncManager.start()
                    }
                }
                delay(500)
                onRequestNotificationPermission()
            }
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.background
            ) {
                val navController = rememberNavController()
                LightChatNavGraph(
                    navController = navController,
                    startDestination = startDestination,
                    initialConversationId = initialConversationId,
                    initialConversationTitle = initialConversationTitle,
                    initialTargetMessageId = initialTargetMessageId,
                    initialOpenFriendRequests = initialOpenFriendRequests
                )
            }
        }
    }
}
