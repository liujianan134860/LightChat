package com.lightchat.ui.chat

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import com.lightchat.ui.theme.TopBarBackground
import com.lightchat.ui.theme.UnreadRed

@Composable
internal fun RetryMessageDialog(
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = TopBarBackground,
        title = { Text("消息发送失败") },
        text = { Text("是否重新发送这条消息？") },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("重新发送")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}

@Composable
internal fun DeleteSelectedMessagesDialog(
    selectedCount: Int,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("确认删除？") },
        text = { Text("将删除已选择的 $selectedCount 条消息") },
        containerColor = TopBarBackground,
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("删除", color = UnreadRed)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}

@Composable
internal fun DeleteSingleMessageDialog(
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("确认删除？") },
        text = { Text("将删除该条消息") },
        containerColor = TopBarBackground,
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("删除", color = UnreadRed)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}
