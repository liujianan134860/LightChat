package com.lightchat.domain.session

interface ConnectionController {
    fun connect(token: String)
    fun disconnect()
    fun onAppForeground()
    fun onAppBackground()
    fun onNetworkAvailable()
    fun onNetworkLost()
}
