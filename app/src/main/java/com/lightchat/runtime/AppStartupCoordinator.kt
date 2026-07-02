package com.lightchat.runtime

import android.app.Activity
import android.app.Application
import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.os.Bundle
import com.lightchat.im.ImClient
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AppStartupCoordinator @Inject constructor(
    @ApplicationContext context: Context,
    private val imClient: ImClient,
    private val presence: DefaultAppPresence
) {
    private val application = context as Application
    @Volatile private var started = false

    fun start() {
        if (started) return
        synchronized(this) {
            if (started) return
            started = true
            registerProcessVisibility()
            registerNetworkRecovery()
        }
    }

    private fun registerProcessVisibility() {
        var startedActivities = 0
        application.registerActivityLifecycleCallbacks(object : Application.ActivityLifecycleCallbacks {
            override fun onActivityStarted(activity: Activity) {
                if (startedActivities++ == 0) {
                    presence.isForeground = true
                    imClient.onAppForeground()
                }
            }

            override fun onActivityStopped(activity: Activity) {
                startedActivities = (startedActivities - 1).coerceAtLeast(0)
                if (startedActivities == 0) {
                    presence.isForeground = false
                    imClient.onAppBackground()
                }
            }

            override fun onActivityCreated(activity: Activity, state: Bundle?) = Unit
            override fun onActivityResumed(activity: Activity) = Unit
            override fun onActivityPaused(activity: Activity) = Unit
            override fun onActivitySaveInstanceState(activity: Activity, state: Bundle) = Unit
            override fun onActivityDestroyed(activity: Activity) = Unit
        })
    }

    private fun registerNetworkRecovery() {
        application.getSystemService(ConnectivityManager::class.java)
            .registerDefaultNetworkCallback(object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) = imClient.onNetworkAvailable()
                override fun onLost(network: Network) = imClient.onNetworkLost()
            })
    }
}
