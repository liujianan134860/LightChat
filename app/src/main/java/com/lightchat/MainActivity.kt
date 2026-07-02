package com.lightchat

import android.Manifest
import android.content.Intent
import android.widget.Toast
import android.os.Bundle
import android.os.Build
import android.view.Gravity
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.graphics.toArgb
import androidx.core.content.ContextCompat
import android.content.pm.PackageManager
import com.lightchat.event.AppEvents
import com.lightchat.ui.navigation.Routes
import com.lightchat.ui.theme.TopBarBackground
import com.lightchat.ui.theme.WeChatGreen
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setMaxRefreshRate()

        val startupView = createStartupView()
        setContentView(startupView)
        startupView.postDelayed({
            val app = application as LightChatApplication
            val loggedInAtLaunch = app.authRepository.isLoggedIn()
            val startDestination = if (loggedInAtLaunch) Routes.MAIN else Routes.LOGIN
            val initialConversationId = intent.getStringExtra(EXTRA_CONVERSATION_ID)
            showLightChatMainContent(
                app = app,
                startDestination = startDestination,
                initialConversationId = initialConversationId,
                initialConversationTitle = intent.getStringExtra(EXTRA_CONVERSATION_TITLE),
                initialTargetMessageId = resolveTargetMessageId(intent),
                initialOpenFriendRequests = intent.getBooleanExtra(EXTRA_OPEN_FRIEND_REQUESTS, false),
                loggedInAtLaunch = loggedInAtLaunch,
                onRequestNotificationPermission = { requestNotificationPermissionIfNeeded() }
            )
        }, StartupComposeDelayMs)
    }

    private fun createStartupView(): FrameLayout {
        return FrameLayout(this).apply {
            setBackgroundColor(TopBarBackground.toArgb())
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            addView(
                TextView(context).apply {
                    text = "LightChat"
                    textSize = 28f
                    setTextColor(WeChatGreen.toArgb())
                    gravity = Gravity.CENTER
                    typeface = android.graphics.Typeface.DEFAULT_BOLD
                },
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT
                )
            )
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        val conversationId = intent.getStringExtra(EXTRA_CONVERSATION_ID).orEmpty()
        if (intent.getBooleanExtra(EXTRA_OPEN_FRIEND_REQUESTS, false)) {
            AppEvents.notifyOpenFriendRequests()
        } else if (conversationId.isNotBlank()) {
            val title = intent.getStringExtra(EXTRA_CONVERSATION_TITLE).orEmpty()
            val targetMessageId = resolveTargetMessageId(intent)
            AppEvents.notifyOpenConversation(conversationId, title, targetMessageId)
        }
    }

    private fun resolveTargetMessageId(intent: Intent): String {
        val conversationId = intent.getStringExtra(EXTRA_CONVERSATION_ID).orEmpty()
        if (conversationId.isBlank()) return intent.getStringExtra(EXTRA_TARGET_MESSAGE_ID).orEmpty()
        val app = application as? LightChatApplication ?: return ""
        val conversation = app.conversationDao.getById(conversationId) ?: return ""
        if (!conversation.atMe) return intent.getStringExtra(EXTRA_TARGET_MESSAGE_ID).orEmpty()
        val currentUserId = app.userSession.currentUserId ?: return ""
        return app.messageDao.findFirstUnreadMentionForUser(
            conversationId,
            currentUserId,
            conversation.atMeCount
        )?.messageId.orEmpty()
    }

    private fun setMaxRefreshRate() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return
        val display = display ?: return
        val supportedModes = display.supportedModes ?: return
        val bestMode = supportedModes.maxByOrNull { it.refreshRate } ?: return
        if (bestMode.refreshRate < 60f) return
        val params = window.attributes
        params.preferredDisplayModeId = bestMode.modeId
        window.attributes = params
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val granted = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
        if (!granted) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    companion object {
        const val EXTRA_CONVERSATION_ID = "extra_conversation_id"
        const val EXTRA_CONVERSATION_TITLE = "extra_conversation_title"
        const val EXTRA_TARGET_MESSAGE_ID = "extra_target_message_id"
        const val EXTRA_OPEN_FRIEND_REQUESTS = "extra_open_friend_requests"
        private const val StartupComposeDelayMs = 120L
    }
}
