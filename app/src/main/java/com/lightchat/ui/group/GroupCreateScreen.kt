package com.lightchat.ui.group

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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lightchat.model.User
import com.lightchat.ui.components.LightChatAvatar
import com.lightchat.ui.theme.*
import com.lightchat.viewmodel.GroupCreateViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupCreateScreen(
    onBack: () -> Unit,
    onGroupCreated: (groupId: String, groupName: String) -> Unit,
    viewModel: GroupCreateViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var showNameDialog by remember { mutableStateOf(false) }
    var groupNameInput by remember { mutableStateOf("") }
    var searchQuery by remember { mutableStateOf("") }

    val filteredFriends = remember(searchQuery, uiState.friends) {
        if (searchQuery.isBlank()) uiState.friends
        else uiState.friends.filter {
            it.nickname.contains(searchQuery, ignoreCase = true) ||
            it.userId.contains(searchQuery, ignoreCase = true)
        }
    }

    LaunchedEffect(uiState.isCreated) {
        if (uiState.isCreated) onGroupCreated(uiState.createdGroupId, uiState.createdGroupName)
    }

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = { Text("发起群聊", color = Color.Black) },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                        }
                    },
                    actions = {
                        TextButton(
                            onClick = { showNameDialog = true },
                            enabled = uiState.selectedMemberIds.size >= 2 && !uiState.isCreating
                        ) {
                            Text(
                                if (uiState.isCreating) "创建中..." else "完成(${uiState.selectedMemberIds.size})",
                                color = if (uiState.selectedMemberIds.size >= 2) WeChatGreen else TextSecondary
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
                text = "已选择 ${uiState.selectedMemberIds.size} 位联系人",
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                fontSize = 14.sp,
                color = TextSecondary
            )
            HorizontalDivider(color = DividerColor, thickness = 0.5.dp)
            if (uiState.errorMessage.isNotBlank()) {
                Text(
                    text = uiState.errorMessage,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    color = MaterialTheme.colorScheme.error,
                    fontSize = 13.sp
                )
            }
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(filteredFriends, key = { it.userId }) { friend ->
                    GroupMemberSelectItem(
                        user = friend,
                        isSelected = friend.userId in uiState.selectedMemberIds,
                        onClick = { viewModel.toggleMember(friend.userId) }
                    )
                }
                if (searchQuery.isNotBlank() && filteredFriends.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier.fillMaxWidth().padding(32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("未找到相关联系人", color = TextSecondary, fontSize = 14.sp)
                        }
                    }
                }
            }
        }
    }

    // Group name dialog
    if (showNameDialog) {
        AlertDialog(
            onDismissRequest = { showNameDialog = false },
            containerColor = TopBarBackground,
            title = { Text("设置群名称") },
            text = {
                OutlinedTextField(
                    value = groupNameInput,
                    onValueChange = { groupNameInput = it },
                    label = { Text("群名称") },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = WeChatGreen,
                        unfocusedBorderColor = DividerColor,
                        focusedLabelColor = WeChatGreen,
                        cursorColor = WeChatGreen
                    ),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.onGroupNameChange(groupNameInput)
                        viewModel.createGroup()
                        showNameDialog = false
                    }
                ) {
                    Text("确定", color = WeChatGreen)
                }
            },
            dismissButton = {
                TextButton(onClick = { showNameDialog = false }) {
                    Text("取消")
                }
            }
        )
    }
}

@Composable
fun GroupMemberSelectItem(user: User, isSelected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(
            checked = isSelected,
            onCheckedChange = { onClick() },
            colors = CheckboxDefaults.colors(checkedColor = WeChatGreen)
        )
        Spacer(modifier = Modifier.width(4.dp))
        LightChatAvatar(avatar = user.avatar, avatarUrl = user.avatarUrl, avatarVersion = user.avatarVersion, userId = user.userId, name = user.nickname, size = 44.dp, allowNetwork = true)
        Spacer(modifier = Modifier.width(12.dp))
        Text(user.nickname, fontWeight = FontWeight.Medium, fontSize = 16.sp)
    }
    HorizontalDivider(modifier = Modifier.padding(start = 72.dp), color = DividerColor, thickness = 0.5.dp)
}
