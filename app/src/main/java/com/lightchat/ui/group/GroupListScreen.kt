package com.lightchat.ui.group

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lightchat.ui.theme.GroupAccentBlue
import com.lightchat.LightChatApplication
import com.lightchat.model.ConversationId
import com.lightchat.model.ImGroup
import com.lightchat.ui.theme.DividerColor
import com.lightchat.ui.theme.TextSecondary
import com.lightchat.ui.theme.TopBarBackground
import com.lightchat.ui.theme.WeChatGreen
import com.lightchat.ui.theme.WeChatWhite

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupListScreen(
    onBack: () -> Unit,
    onGroupClick: (conversationId: String, title: String) -> Unit
) {
    val groups = remember { LightChatApplication.instance.groupDao.getCurrentOwnerGroups() }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(WeChatWhite)
    ) {
        Column {
            TopAppBar(
                title = { Text("群聊", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = TopBarBackground)
            )
            HorizontalDivider(thickness = 0.5.dp, color = DividerColor)
        }
        if (groups.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("暂无群聊", color = TextSecondary, fontSize = 14.sp)
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(groups, key = { it.groupId }) { group ->
                    GroupListItem(
                        group = group,
                        onClick = { onGroupClick(ConversationId.group(group.groupId), group.groupName) }
                    )
                }
            }
        }
    }
}

@Composable
private fun GroupListItem(group: ImGroup, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(GroupAccentBlue.copy(alpha = 0.16f)),
            contentAlignment = Alignment.Center
        ) {
            Text("群", color = GroupAccentBlue, fontWeight = FontWeight.Bold, fontSize = 18.sp)
        }
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = group.groupName.ifBlank { group.groupId },
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
    HorizontalDivider(modifier = Modifier.padding(start = 72.dp), color = DividerColor, thickness = 0.5.dp)
}
