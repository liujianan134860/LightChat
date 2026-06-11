package com.lightchat.ui.chat

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.withFrameNanos
import com.lightchat.viewmodel.ChatUiState

fun lastConversationContentIndex(uiState: ChatUiState): Int {
    if (uiState.messages.isEmpty()) return -1
    var index = if (uiState.isLoadingMoreMessages) 1 else 0
    uiState.messages.forEachIndexed { messageIndex, message ->
        val previous = uiState.messages.getOrNull(messageIndex - 1)
        if (shouldShowTimestamp(previous, message)) {
            index++
        }
        index++
    }
    return index - 1
}

fun messageContentIndex(uiState: ChatUiState, messageId: String): Int {
    if (uiState.messages.isEmpty()) return -1
    var index = if (uiState.isLoadingMoreMessages) 1 else 0
    uiState.messages.forEachIndexed { messageIndex, message ->
        val previous = uiState.messages.getOrNull(messageIndex - 1)
        if (shouldShowTimestamp(previous, message)) {
            index++
        }
        if (message.messageId == messageId) {
            return index
        }
        index++
    }
    return -1
}

fun visibleMessageIdAtContentIndex(uiState: ChatUiState, targetIndex: Int): String? {
    if (uiState.messages.isEmpty() || targetIndex < 0) return null
    var index = if (uiState.isLoadingMoreMessages) 1 else 0
    uiState.messages.forEachIndexed { messageIndex, message ->
        val previous = uiState.messages.getOrNull(messageIndex - 1)
        if (shouldShowTimestamp(previous, message)) {
            if (index == targetIndex) return null
            index++
        }
        if (index == targetIndex) {
            return message.messageId
        }
        index++
    }
    return null
}

suspend fun scrollConversationToHardBottom(
    listState: LazyListState,
    uiState: ChatUiState
) {
    val lastContentIndex = lastConversationContentIndex(uiState)
    if (lastContentIndex < 0) return
    val totalItems = listState.layoutInfo.totalItemsCount
    if (totalItems <= 0 || lastContentIndex >= totalItems) return
    runCatching {
        listState.scrollToItem(lastContentIndex, Int.MAX_VALUE)
        withFrameNanos { }
        if (lastContentIndex < listState.layoutInfo.totalItemsCount) {
            listState.scrollToItem(lastContentIndex, Int.MAX_VALUE)
        }
    }
}

suspend fun scrollMessageToViewportCenter(
    listState: LazyListState,
    targetIndex: Int
) {
    if (targetIndex < 0) return
    val totalItems = listState.layoutInfo.totalItemsCount
    if (totalItems <= 0 || targetIndex >= totalItems) return
    listState.scrollToItem(targetIndex)
    withFrameNanos { }
    withFrameNanos { }

    var didCenter = false
    repeat(6) { attempt ->
        val layoutInfo = listState.layoutInfo
        val viewportHeight = layoutInfo.viewportEndOffset - layoutInfo.viewportStartOffset
        if (viewportHeight <= 0) {
            withFrameNanos { }
            return@repeat
        }
        val targetItem = layoutInfo.visibleItemsInfo.firstOrNull { it.index == targetIndex }
        if (targetItem != null) {
            val centeredOffset = ((viewportHeight - targetItem.size) / 2).coerceAtLeast(0)
            val itemMid = targetItem.offset + targetItem.size / 2
            val viewportMid = viewportHeight / 2
            val alreadyRoughlyCentered = kotlin.math.abs(itemMid - viewportMid) < viewportHeight / 6
            if (alreadyRoughlyCentered && attempt > 0) {
                didCenter = true
                return
            }
            listState.scrollToItem(targetIndex, -centeredOffset)
            withFrameNanos { }
            didCenter = true
            return
        }
        withFrameNanos { }
    }
    if (!didCenter) {
        if (targetIndex < listState.layoutInfo.totalItemsCount) {
            listState.scrollToItem(targetIndex)
            withFrameNanos { }
            val layoutInfo = listState.layoutInfo
            val viewportHeight = layoutInfo.viewportEndOffset - layoutInfo.viewportStartOffset
            if (viewportHeight > 0) {
                listState.scrollToItem(targetIndex, -(viewportHeight / 2))
            }
        }
    }
}

fun isConversationNearHardBottom(
    listState: LazyListState,
    uiState: ChatUiState,
    thresholdPx: Int = 24
): Boolean {
    val lastContentIndex = lastConversationContentIndex(uiState)
    if (lastContentIndex < 0) return true
    val layoutInfo = listState.layoutInfo
    val lastItem = layoutInfo.visibleItemsInfo.firstOrNull { it.index == lastContentIndex } ?: return false
    val bottomGap = layoutInfo.viewportEndOffset - (lastItem.offset + lastItem.size)
    return bottomGap <= thresholdPx
}
