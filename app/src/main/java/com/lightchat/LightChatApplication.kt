package com.lightchat

import android.app.Application
import android.app.Activity
import android.os.Bundle
import android.net.ConnectivityManager
import android.net.Network
import com.lightchat.data.local.DatabaseHelper
import com.lightchat.data.local.TokenManager
import com.lightchat.data.local.UserSession
import com.lightchat.data.local.dao.ConversationDao
import com.lightchat.data.local.dao.FriendRequestDao
import com.lightchat.data.local.dao.GroupDao
import com.lightchat.data.local.dao.MessageDao
import com.lightchat.data.local.dao.SyncStateDao
import com.lightchat.data.local.dao.UserDao
import com.lightchat.data.repository.AuthRepository
import com.lightchat.data.repository.ConversationRepository
import com.lightchat.data.repository.MessageRepository
import com.lightchat.data.repository.UserRepository
import com.lightchat.im.ImClient
import com.lightchat.sync.EventProcessor
import com.lightchat.sync.SyncManager

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class LightChatApplication : Application() {

    lateinit var databaseHelper: DatabaseHelper
        private set
    lateinit var tokenManager: TokenManager
        private set
    lateinit var userSession: UserSession
        private set

    val userDao by lazy { UserDao(databaseHelper) }
    val messageDao by lazy { MessageDao(databaseHelper) }
    val conversationDao by lazy { ConversationDao(databaseHelper) }
    val groupDao by lazy { GroupDao(databaseHelper) }
    val syncStateDao by lazy { SyncStateDao(databaseHelper) }
    val friendRequestDao by lazy { FriendRequestDao(databaseHelper) }

    val userRepository by lazy { UserRepository(userDao) }
    val messageRepository by lazy { MessageRepository(messageDao) }
    val conversationRepository by lazy { ConversationRepository(conversationDao) }
    val authRepository by lazy { AuthRepository(userDao, tokenManager, userSession) }
    val imClient by lazy { ImClient() }
    val eventProcessor by lazy {
        EventProcessor(messageDao, conversationDao, groupDao, userDao, friendRequestDao, syncStateDao)
    }
    val syncManager by lazy { SyncManager(imClient, eventProcessor) }

    var currentForwardMessageIds: List<String> = emptyList()
    var currentForwardSnapshotPayloads: List<String> = emptyList()
    var currentForwardTargetConversationId: String? = null
    var currentForwardSourceConversationId: String? = null
    var currentForwardRequiresTypeChoice: Boolean = false
    var currentOpenConversationId: String? = null
    val nextClientSeq: Long get() = _nextClientSeq.getAndIncrement()
    private val _nextClientSeq = java.util.concurrent.atomic.AtomicLong(1)
    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    var lastMainTab: Int = 0
    @Volatile var isAppForeground: Boolean = false
    var pendingImageUris: List<android.net.Uri> = emptyList()
    var pendingImageSendConversationId: String? = null
    var pickerEditedPaths: Map<Int, String> = emptyMap()
        set(value) {
            field = value
            pickerEditedVersion++
        }
    var pickerEditedVersion: Int = 0
        private set
    var pickerSelectedIndices: List<Int> = emptyList()
    var pickerAllPhotoUris: List<android.net.Uri> = emptyList()
    val imageBubbleBounds = mutableMapOf<String, androidx.compose.ui.geometry.Rect>()
    val messageBubbleBounds = mutableMapOf<String, androidx.compose.ui.geometry.Rect>()
    @Volatile private var connectionRecoveryRegistered = false

    override fun onCreate() {
        super.onCreate()
        instance = this
        databaseHelper = DatabaseHelper(this)
        tokenManager = TokenManager(this)
        userSession = UserSession(this)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            val manager = getSystemService(android.app.NotificationManager::class.java)
            val channel = android.app.NotificationChannel(
                "lightchat_connection",
                "连接服务",
                android.app.NotificationManager.IMPORTANCE_LOW
            ).apply { description = "保持后台连接" }
            manager.createNotificationChannel(channel)
        }
    }

    fun ensureConnectionRecoveryStarted() {
        if (connectionRecoveryRegistered) return
        connectionRecoveryRegistered = true
        var startedActivities = 0
        registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
            override fun onActivityStarted(activity: Activity) {
                if (startedActivities++ == 0) {
                    isAppForeground = true
                    imClient.onAppForeground()
                }
            }

            override fun onActivityStopped(activity: Activity) {
                startedActivities = (startedActivities - 1).coerceAtLeast(0)
                if (startedActivities == 0) {
                    isAppForeground = false
                    imClient.onAppBackground()
                }
            }

            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
            override fun onActivityResumed(activity: Activity) = Unit
            override fun onActivityPaused(activity: Activity) = Unit
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
            override fun onActivityDestroyed(activity: Activity) = Unit
        })
        isAppForeground = true
        imClient.onAppForeground()

        val connectivityManager = getSystemService(ConnectivityManager::class.java)
        connectivityManager.registerDefaultNetworkCallback(object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                imClient.onNetworkAvailable()
            }

            override fun onLost(network: Network) {
                imClient.onNetworkLost()
            }
        })
    }

    companion object {
        lateinit var instance: LightChatApplication
            private set

        fun isInitialized(): Boolean = ::instance.isInitialized
    }
}
