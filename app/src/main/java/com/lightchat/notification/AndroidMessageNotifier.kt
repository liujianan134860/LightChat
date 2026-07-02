package com.lightchat.notification

import android.content.Context
import com.lightchat.domain.notification.MessageNotifier
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AndroidMessageNotifier @Inject constructor(
    @ApplicationContext private val context: Context
) : MessageNotifier {
    override fun showMessage(
        conversationId: String,
        title: String,
        content: String,
        targetMessageId: String
    ) {
        NotificationHelper.showMessage(context, conversationId, title, content, targetMessageId)
    }
}
