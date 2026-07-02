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
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

@HiltAndroidApp
class LightChatApplication : Application() {

    @Inject lateinit var databaseHelper: DatabaseHelper
    @Inject lateinit var tokenManager: TokenManager
    @Inject lateinit var userSession: UserSession
    @Inject lateinit var userDao: UserDao
    @Inject lateinit var messageDao: MessageDao
    @Inject lateinit var conversationDao: ConversationDao
    @Inject lateinit var groupDao: GroupDao
    @Inject lateinit var syncStateDao: SyncStateDao
    @Inject lateinit var friendRequestDao: FriendRequestDao
    @Inject lateinit var userRepository: UserRepository
    @Inject lateinit var messageRepository: MessageRepository
    @Inject lateinit var conversationRepository: ConversationRepository
    @Inject lateinit var authRepository: AuthRepository
    @Inject lateinit var imClient: ImClient
    @Inject lateinit var eventProcessor: EventProcessor
    @Inject lateinit var syncManager: SyncManager

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
