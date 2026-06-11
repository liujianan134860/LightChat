package com.lightchat.ui.group

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.lightchat.LightChatApplication
import com.lightchat.model.GroupMember
import com.lightchat.model.MemberRole
import com.lightchat.model.User
import com.lightchat.ui.components.LightChatAvatar
import com.lightchat.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupMemberListScreen(
    groupId: String,
    onBack: () -> Unit,
    onMemberClick: (String) -> Unit,
    onInviteClick: (String) -> Unit
) {
    val app = LightChatApplication.instance
    val lifecycleOwner = LocalLifecycleOwner.current
    val group = remember(groupId) { app.groupDao.getGroupById(groupId) }
    var members by remember { mutableStateOf(app.groupDao.getMembers(groupId)) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                members = app.groupDao.getMembers(groupId)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = { Text("群聊成员") },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                        }
                    },
                    actions = {
                        TextButton(onClick = { onInviteClick(groupId) }) {
                            Text("邀请", color = WeChatGreen)
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
            Text(
                text = "共 ${members.size} 位成员",
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                fontSize = 14.sp,
                color = TextSecondary
            )
            HorizontalDivider(color = DividerColor, thickness = 0.5.dp)
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(members, key = { it.userId }) { member ->
                    GroupMemberRow(
                        member = member,
                        onClick = {
                            upsertMemberUser(member)
                            onMemberClick(member.userId)
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun GroupMemberRow(member: GroupMember, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        LightChatAvatar(avatar = member.avatar, avatarUrl = member.avatarUrl, avatarVersion = member.avatarVersion, userId = member.userId, name = member.nickname, size = 44.dp, allowNetwork = true)
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(member.nickname.ifBlank { member.userId }, fontSize = 16.sp, color = TextPrimary)
            if (member.aliasInGroup.isNotBlank()) {
                Text("群昵称：${member.aliasInGroup}", fontSize = 12.sp, color = TextSecondary)
            }
        }
        if (member.role == MemberRole.OWNER) {
            Text("群主", fontSize = 12.sp, color = TextSecondary)
        }
    }
    HorizontalDivider(modifier = Modifier.padding(start = 72.dp), color = DividerColor, thickness = 0.5.dp)
}

private fun upsertMemberUser(member: GroupMember) {
    LightChatApplication.instance.userDao.upsertPreservingExisting(
        User(
            userId = member.userId,
            nickname = member.nickname.ifBlank { member.userId },
            avatar = member.avatar,
            avatarUrl = member.avatarUrl,
            avatarVersion = member.avatarVersion
        )
    )
}
