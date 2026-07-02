package com.lightchat.runtime

import com.lightchat.domain.session.AppPresence
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DefaultAppPresence @Inject constructor() : AppPresence {
    @Volatile override var isForeground: Boolean = false
    @Volatile override var currentConversationId: String? = null
}
