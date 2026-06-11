package com.lightchat.ui.group

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lightchat.LightChatApplication
import com.lightchat.model.User
import com.lightchat.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupInviteScreen(
    groupId: String,
    onBack: () -> Unit,
    onInvited: () -> Unit
) {
    val app = LightChatApplication.instance
    val context = LocalContext.current
    var selectedIds by remember { mutableStateOf(setOf<String>()) }
    val candidates by remember(groupId) { mutableStateOf(loadInviteCandidates(groupId)) }
    var isSubmitting by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }

    val filteredCandidates = remember(searchQuery, candidates) {
        if (searchQuery.isBlank()) candidates
        else candidates.filter {
            it.nickname.contains(searchQuery, ignoreCase = true) ||
            it.userId.contains(searchQuery, ignoreCase = true)
        }
    }

    DisposableEffect(groupId) {
        val ackListener: (String, Int) -> Unit = { ackGroupId, _ ->
            if (ackGroupId == groupId && isSubmitting) {
                isSubmitting = false
                app.syncManager.requestSync()
            }
        }
        val errorListener: (Int, String) -> Unit = { _, message ->
            if (isSubmitting) {
                isSubmitting = false
                Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
            }
        }
        app.imClient.onAddGroupMembersAck(ackListener)
        app.imClient.onError(errorListener)
        onDispose {
            app.imClient.removeAddGroupMembersAckListener(ackListener)
            app.imClient.removeErrorListener(errorListener)
        }
    }

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = { Text("邀请成员", color = Color.Black) },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                        }
                    },
                    actions = {
                        TextButton(
                            onClick = {
                                val selectedUsers = candidates.filter { it.userId in selectedIds }
                                isSubmitting = true
                                val sent = app.imClient.addGroupMembers(groupId, selectedUsers.map { it.userId })
                                if (!sent) {
                                    isSubmitting = false
                                    Toast.makeText(context, "连接服务端后才能邀请成员", Toast.LENGTH_SHORT).show()
                                } else {
                                    Toast.makeText(context, "已邀请", Toast.LENGTH_SHORT).show()
                                    app.syncManager.requestSync()
                                    onInvited()
                                }
                            },
                            enabled = selectedIds.isNotEmpty() && !isSubmitting
                        ) {
                            Text(
                                text = if (isSubmitting) "邀请中..." else if (selectedIds.isEmpty()) "完成" else "完成(${selectedIds.size})",
                                color = if (selectedIds.isNotEmpty()) WeChatGreen else TextSecondary
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = TopBarBackground)
                )
                HorizontalDivider(thickness = 0.5.dp, color = DividerColor)
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(WeChatWhite)
        ) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = TextSecondary) },
                placeholder = { Text("搜索联系人", fontSize = 14.sp) },
                singleLine = true,
                shape = RoundedCornerShape(20.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color.Transparent,
                    unfocusedBorderColor = Color.Transparent,
                    focusedContainerColor = BottomBarBackground,
                    unfocusedContainerColor = BottomBarBackground
                ),
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Default.Close, contentDescription = "清除")
                        }
                    }
                }
            )
            Text(
                text = "选择要邀请进群的好友",
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                fontSize = 14.sp,
                color = TextSecondary
            )
            HorizontalDivider(color = DividerColor, thickness = 0.5.dp)

            if (candidates.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("暂无可邀请的好友", color = TextSecondary)
                }
            } else if (searchQuery.isNotBlank() && filteredCandidates.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("未找到相关联系人", color = TextSecondary)
                }
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(filteredCandidates, key = { it.userId }) { friend ->
                        GroupMemberSelectItem(
                            user = friend,
                            isSelected = friend.userId in selectedIds,
                            onClick = {
                                selectedIds = if (friend.userId in selectedIds) {
                                    selectedIds - friend.userId
                                } else {
                                    selectedIds + friend.userId
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

private fun loadInviteCandidates(groupId: String): List<User> {
    val app = LightChatApplication.instance
    val currentUserId = app.userSession.currentUserId ?: return emptyList()
    val existingMemberIds = app.groupDao.getMembers(groupId).map { it.userId }.toSet()
    return app.userDao.getFriends(currentUserId).filter { it.userId !in existingMemberIds }
}
