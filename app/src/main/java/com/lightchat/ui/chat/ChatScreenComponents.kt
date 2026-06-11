package com.lightchat.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Forward
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import com.lightchat.LightChatApplication
import com.lightchat.model.Message
import com.lightchat.model.MessageStatus
import com.lightchat.model.MessageType
import com.lightchat.ui.theme.TopBarBackground
import com.lightchat.ui.theme.UnreadRed

internal data class AnchoredMessageMenu(
    val message: Message,
    val bounds: Rect
)

@Composable
internal fun AnchoredMessageActionMenu(
    message: Message,
    bounds: Rect,
    screenWidthPx: Int,
    screenHeightPx: Int,
    inputBarHeightPx: Int,
    onDismiss: () -> Unit,
    onCopy: () -> Unit,
    onForward: () -> Unit,
    onMultiSelect: () -> Unit,
    onRecall: () -> Unit,
    onDelete: () -> Unit
) {
    val density = androidx.compose.ui.platform.LocalDensity.current
    val canCopy = message.messageType == MessageType.TEXT
    val isFailed = message.status == MessageStatus.FAILED
    val canRecall = message.senderId == LightChatApplication.instance.userSession.currentUserId &&
        !isFailed &&
        System.currentTimeMillis() - message.sendTime <= 120_000
    val actions = buildList {
        if (isFailed) {
            if (canCopy) add(MenuAction("复制", Icons.Default.ContentCopy, onCopy))
            add(MenuAction("删除", Icons.Default.Delete, onDelete))
            return@buildList
        }
        if (canCopy) add(MenuAction("复制", Icons.Default.ContentCopy, onCopy))
        add(MenuAction("转发", Icons.AutoMirrored.Filled.Forward, onForward))
        add(MenuAction("多选", Icons.Default.Checklist, onMultiSelect))
        if (canRecall) add(MenuAction("撤回", Icons.AutoMirrored.Filled.Undo, onRecall))
        add(MenuAction("删除", Icons.Default.Delete, onDelete))
    }
    val itemWidth = 64.dp
    val menuWidth = itemWidth * actions.size
    val menuHeight = 76.dp
    val horizontalMarginPx = with(density) { 12.dp.roundToPx() }
    val menuWidthPx = with(density) { menuWidth.roundToPx() }
    val menuHeightPx = with(density) { menuHeight.roundToPx() }
    val aboveGapPx = with(density) { 6.dp.roundToPx() }
    val belowGapPx = with(density) { 6.dp.roundToPx() }
    val topLimitPx = WindowInsets.statusBars.getTop(density) + with(density) { 108.dp.roundToPx() }
    val bottomLimitPx = screenHeightPx - inputBarHeightPx - with(density) { 12.dp.roundToPx() }

    val positionProvider = remember(bounds, menuWidthPx, menuHeightPx, aboveGapPx, belowGapPx, topLimitPx, bottomLimitPx, horizontalMarginPx) {
        object : PopupPositionProvider {
            override fun calculatePosition(
                anchorBounds: IntRect,
                windowSize: IntSize,
                layoutDirection: LayoutDirection,
                popupContentSize: IntSize
            ): IntOffset {
                val popupX = (bounds.center.x - popupContentSize.width / 2f).toInt()
                    .coerceIn(horizontalMarginPx, (windowSize.width - popupContentSize.width - horizontalMarginPx).coerceAtLeast(horizontalMarginPx))
                val aboveY = (bounds.top - popupContentSize.height - aboveGapPx).toInt()
                val belowY = (bounds.bottom + belowGapPx).toInt()
                val popupY = when {
                    aboveY >= topLimitPx -> aboveY
                    belowY + popupContentSize.height <= bottomLimitPx -> belowY
                    else -> aboveY.coerceIn(topLimitPx, (bottomLimitPx - popupContentSize.height).coerceAtLeast(topLimitPx))
                }
                return IntOffset(popupX, popupY)
            }
        }
    }

    Popup(
        popupPositionProvider = positionProvider,
        onDismissRequest = onDismiss,
        properties = PopupProperties(focusable = true)
    ) {
        Row(
            modifier = Modifier
                .width(menuWidth)
                .height(menuHeight)
                .clip(RoundedCornerShape(10.dp))
                .background(Color(0xEE3F3F3F)),
            verticalAlignment = Alignment.CenterVertically
        ) {
            actions.forEach { action ->
                Column(
                    modifier = Modifier
                        .width(itemWidth)
                        .fillMaxHeight()
                        .clickable(onClick = action.onClick),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = action.icon,
                        contentDescription = action.label,
                        tint = Color(0xFFECECEC),
                        modifier = Modifier.size(25.dp)
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(action.label, color = Color(0xFFECECEC), fontSize = 13.sp)
                }
            }
        }
    }
}

private data class MenuAction(
    val label: String,
    val icon: ImageVector,
    val onClick: () -> Unit
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ChatTopBar(
    displayTitle: String,
    isMultiSelectMode: Boolean,
    selectedCount: Int,
    isGroup: Boolean,
    onBack: () -> Unit,
    onCancelMultiSelect: () -> Unit,
    onForwardSelected: () -> Unit,
    onDeleteSelected: () -> Unit = {},
    onShowGroupMembers: () -> Unit,
    onSearchClick: () -> Unit
) {
    if (isMultiSelectMode) {
        TopAppBar(
            title = { Text("已选择 $selectedCount 条") },
            navigationIcon = {
                TextButton(onClick = onCancelMultiSelect) {
                    Text("取消")
                }
            },
            actions = {
                TextButton(
                    onClick = onDeleteSelected,
                    enabled = selectedCount > 0,
                    colors = ButtonDefaults.textButtonColors(contentColor = UnreadRed)
                ) {
                    Text("删除")
                }
                TextButton(
                    onClick = onForwardSelected,
                    enabled = selectedCount > 0
                ) {
                    Text("转发")
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = TopBarBackground)
        )
    } else {
        TopAppBar(
            title = { Text(displayTitle) },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                }
            },
            actions = {
                if (isGroup) {
                    IconButton(onClick = onShowGroupMembers) {
                        Icon(Icons.Default.Group, contentDescription = "群成员")
                    }
                }
                IconButton(onClick = onSearchClick) {
                    Icon(Icons.Default.Search, contentDescription = "搜索")
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = TopBarBackground)
        )
    }
}

internal val DefaultInputPanelHeight = 260.dp
internal const val BottomPanelPullMs = 300
internal const val ChatHistoryPrefetchThreshold = 30

internal enum class ChatBottomPanel {
    NONE,
    EMOJI,
    MORE
}

internal data class NewerLoadSignal(
    val lastVisible: Int,
    val lastContent: Int,
    val hasNewer: Boolean,
    val isLoading: Boolean
)
