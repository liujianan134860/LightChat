package com.lightchat.ui.contact

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lightchat.LightChatApplication
import com.lightchat.event.AppEvents
import com.lightchat.model.ConversationId
import com.lightchat.model.ImGroup
import com.lightchat.model.User
import com.lightchat.ui.components.AvatarCacheLoader
import com.lightchat.ui.theme.*
import com.lightchat.viewmodel.ContactViewModel
import com.lightchat.viewmodel.GroupCreateViewModel
import java.text.Collator
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContactScreen(
    modifier: Modifier = Modifier,
    onGroupListClick: () -> Unit = {},
    onGroupClick: (conversationId: String, title: String) -> Unit = { _, _ -> },
    onProfileClick: (String) -> Unit = {},
    onFriendRequestsClick: () -> Unit = {},
    viewModel: ContactViewModel = viewModel(),
    groupCreateViewModel: GroupCreateViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val groupCreateState by groupCreateViewModel.uiState.collectAsState()
    val app = LightChatApplication.instance
    val currentUserId = app.userSession.currentUserId
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    var pendingCount by remember { mutableIntStateOf(0) }
    var showGroupNameDialog by remember { mutableStateOf(false) }
    var groupNameInput by remember { mutableStateOf("") }
    val groupedFriends = remember(uiState.filteredFriends) {
        uiState.filteredFriends
            .sortedWith(contactComparator())
            .groupBy { contactSectionLetter(it) }
            .toSortedMap(compareBy { sectionSortKey(it) })
    }
    val sectionIndexes = remember(uiState.query, groupedFriends, uiState.filteredGroups) {
        val result = linkedMapOf<String, Int>()
        var index = 0
        if (uiState.query.isBlank()) {
            index += 1 // search
            index += 1 // new friends
            index += 1 // groups entry
        } else {
            index += 1 // search
            if (uiState.filteredGroups.isNotEmpty()) {
                result["群"] = index
                index += 1 + uiState.filteredGroups.size
            }
        }
        groupedFriends.forEach { (letter, friends) ->
            result[letter] = index
            index += 1 + friends.size
        }
        result
    }

    LaunchedEffect(currentUserId) {
        fun refreshPendingCount() {
            pendingCount = currentUserId?.let { app.friendRequestDao.getPendingCount(it) } ?: 0
        }
        refreshPendingCount()
        AppEvents.friendRequestChanged.collect {
            refreshPendingCount()
        }
    }

    LaunchedEffect(listState, uiState.friends.size, uiState.hasMore) {
        snapshotFlow { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0 }
            .collect { lastVisibleIndex ->
                if (uiState.hasMore && lastVisibleIndex >= uiState.friends.size - 4) {
                    viewModel.loadMoreFriends()
                }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            viewModel.exitSelectionMode()
        }
    }

    LaunchedEffect(groupCreateState.isCreated) {
        if (groupCreateState.isCreated) {
            val groupId = groupCreateState.createdGroupId
            val groupName = groupCreateState.createdGroupName
            groupCreateViewModel.consumeCreatedState()
            viewModel.exitSelectionMode()
            onGroupClick(ConversationId.group(groupId), groupName)
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
    Column(modifier = Modifier.fillMaxSize()) {
        // Top bar
        TopAppBar(
            title = {
                if (uiState.isSelectionMode) {
                    Text("已选择 ${uiState.selectedFriendIds.size} 人", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                } else {
                    Text("联系人", fontWeight = FontWeight.Bold, fontSize = 20.sp)
                }
            },
            navigationIcon = {
                if (uiState.isSelectionMode) {
                    TextButton(onClick = { viewModel.exitSelectionMode() }) {
                        Text("取消")
                    }
                }
            },
            actions = {
                if (uiState.isSelectionMode) {
                    TextButton(
                        onClick = {
                            groupCreateViewModel.setSelectedMembers(uiState.selectedFriendIds)
                            showGroupNameDialog = true
                        },
                        enabled = uiState.selectedFriendIds.size >= 2
                    ) {
                        Text("确定(${uiState.selectedFriendIds.size})", color = if (uiState.selectedFriendIds.size >= 2) WeChatGreen else TextSecondary)
                    }
                } else {
                    TextButton(onClick = { viewModel.enterSelectionMode() }) {
                        Text("发起群聊", color = WeChatGreen)
                    }
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = TopBarBackground)
        )

        HorizontalDivider(thickness = 0.5.dp, color = DividerColor)

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(WeChatWhite),
            state = listState
        ) {
            item(key = "contact_search") {
                ContactSearchBar(
                    query = uiState.query,
                    onQueryChange = viewModel::onQueryChange
                )
            }

            if (uiState.query.isBlank()) {
                // "新的朋友" entry
                item(key = "new_friends") {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                focusManager.clearFocus()
                                keyboardController?.hide()
                                onFriendRequestsClick()
                            }
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(WeChatGreen.copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.PersonAdd,
                                contentDescription = null,
                                tint = WeChatGreen,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Text("新的朋友", fontWeight = FontWeight.Medium, fontSize = 16.sp)
                        if (pendingCount > 0) {
                            Spacer(modifier = Modifier.weight(1f))
                            Badge(containerColor = UnreadRed) {
                                Text(pendingCount.toString(), color = WeChatWhite, fontSize = 11.sp)
                            }
                        }
                    }
                    HorizontalDivider(color = DividerColor, thickness = 0.5.dp)
                }

                item(key = "group_entry") {
                    ContactShortcutItem(
                        icon = { Icon(Icons.Default.Groups, contentDescription = null, tint = GroupAccentBlue, modifier = Modifier.size(24.dp)) },
                        title = "群聊",
                        subtitle = if (uiState.groups.isEmpty()) "暂无群聊" else "${uiState.groups.size} 个群聊",
                        background = GroupAccentBlue.copy(alpha = 0.16f),
                        onClick = {
                            focusManager.clearFocus()
                            keyboardController?.hide()
                            onGroupListClick()
                        }
                    )
                }
            }

            if (uiState.query.isNotBlank() && uiState.filteredGroups.isNotEmpty()) {
                item(key = "groups_header") { ContactSectionHeader("群聊") }
                items(uiState.filteredGroups, key = { "group_${it.groupId}" }) { group ->
                    GroupContactItem(group = group) {
                        focusManager.clearFocus()
                        keyboardController?.hide()
                        onGroupClick(ConversationId.group(group.groupId), group.groupName)
                    }
                }
            }

            groupedFriends.forEach { (letter, friends) ->
                item(key = "section_$letter") { ContactSectionHeader(letter) }
                items(friends, key = { it.userId }) { friend ->
                    ContactItem(
                        user = friend,
                        isSelected = friend.userId in uiState.selectedFriendIds,
                        isSelectionMode = uiState.isSelectionMode,
                        onClick = {
                            focusManager.clearFocus()
                            keyboardController?.hide()
                            if (uiState.isSelectionMode) {
                                viewModel.toggleFriendSelection(friend.userId)
                            } else {
                                onProfileClick(friend.userId)
                            }
                        }
                    )
                }
            }

            if (uiState.filteredFriends.isEmpty() && uiState.filteredGroups.isEmpty() && !uiState.isLoading) {
                item(key = "empty_contacts") {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 80.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(if (uiState.query.isBlank()) "暂无联系人" else "未找到相关联系人", color = TextSecondary, fontSize = 14.sp)
                    }
                }
            }
            if (uiState.isLoadingMore) {
                item(key = "loading_more_contacts") {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 14.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                            color = WeChatGreen
                        )
                    }
                }
            }
        }
    }

        if (sectionIndexes.isNotEmpty()) {
            Column(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                sectionIndexes.forEach { (letter, index) ->
                    Text(
                        text = letter,
                        color = WeChatGreen,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier
                            .clip(CircleShape)
                            .clickable {
                                coroutineScope.launch {
                                    listState.animateScrollToItem(index)
                                }
                            }
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }
        }
    }

    if (showGroupNameDialog) {
        AlertDialog(
            onDismissRequest = { showGroupNameDialog = false },
            containerColor = TopBarBackground,
            title = { Text("设置群名称") },
            text = {
                Column {
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
                    if (groupCreateState.errorMessage.isNotBlank()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = groupCreateState.errorMessage,
                            color = MaterialTheme.colorScheme.error,
                            fontSize = 13.sp
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        groupCreateViewModel.setSelectedMembers(uiState.selectedFriendIds)
                        groupCreateViewModel.onGroupNameChange(groupNameInput)
                        groupCreateViewModel.createGroup()
                        showGroupNameDialog = false
                    },
                    enabled = !groupCreateState.isCreating
                ) {
                    Text(if (groupCreateState.isCreating) "创建中..." else "确定", color = WeChatGreen)
                }
            },
            dismissButton = {
                TextButton(onClick = { showGroupNameDialog = false }) {
                    Text("取消")
                }
            }
        )
    }
}

@Composable
private fun ContactSearchBar(
    query: String,
    onQueryChange: (String) -> Unit
) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = TextSecondary) },
        placeholder = { Text("搜索联系人、ID、群聊", fontSize = 14.sp) },
        singleLine = true,
        shape = RoundedCornerShape(20.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = Color.Transparent,
            unfocusedBorderColor = Color.Transparent,
            focusedContainerColor = BottomBarBackground,
            unfocusedContainerColor = BottomBarBackground
        ),
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(Icons.Default.Close, contentDescription = "清除")
                }
            }
        }
    )
}

@Composable
private fun ContactSectionHeader(title: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(SectionBackground)
            .padding(horizontal = 16.dp, vertical = 5.dp)
    ) {
        Text(title, color = TextSecondary, fontSize = 12.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun ContactShortcutItem(
    icon: @Composable () -> Unit,
    title: String,
    subtitle: String? = null,
    background: Color,
    onClick: () -> Unit
) {
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
                .background(background),
            contentAlignment = Alignment.Center
        ) {
            icon()
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.Medium, fontSize = 16.sp)
            if (!subtitle.isNullOrBlank()) {
                Text(subtitle, fontSize = 12.sp, color = TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
    HorizontalDivider(color = DividerColor, thickness = 0.5.dp)
}

@Composable
private fun GroupContactItem(group: ImGroup, onClick: () -> Unit) {
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
        Column(modifier = Modifier.weight(1f)) {
            Text(group.groupName.ifBlank { group.groupId }, fontWeight = FontWeight.Medium, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("${group.memberCount} 人", fontSize = 12.sp, color = TextSecondary)
        }
    }
    HorizontalDivider(modifier = Modifier.padding(start = 72.dp), color = DividerColor, thickness = 0.5.dp)
}

@Composable
fun ContactItem(
    user: User,
    isSelected: Boolean,
    isSelectionMode: Boolean,
    onClick: () -> Unit
) {
    val context = LocalContext.current
    val avatarBitmap by produceState<Bitmap?>(initialValue = null, user.avatarUrl, user.avatarVersion) {
        value = if (user.avatarUrl.isNotBlank()) {
            withContext(Dispatchers.IO) {
                AvatarCacheLoader.loadAvatar(
                    context = context,
                    userId = user.userId,
                    avatarUrl = user.avatarUrl,
                    avatarVersion = user.avatarVersion,
                    avatarFallback = "",
                    allowNetwork = true
                ).bitmap
            }
        } else {
            null
        }
    }
    val avatarColor = remember(user.avatar) {
        user.avatar.takeIf { it.startsWith("#") }?.let {
            try { Color(android.graphics.Color.parseColor(it)) } catch (_: Exception) { WeChatGreen }
        } ?: WeChatGreen
    }
    val displayName = user.nickname.ifBlank { user.userId }
    val displayInitial = displayName.take(1).ifBlank { user.userId.take(1) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (isSelectionMode) {
            Checkbox(
                checked = isSelected,
                onCheckedChange = { onClick() },
                colors = CheckboxDefaults.colors(checkedColor = WeChatGreen)
            )
            Spacer(modifier = Modifier.width(4.dp))
        }
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(avatarColor.copy(alpha = 0.2f)),
            contentAlignment = Alignment.Center
        ) {
            val bm = avatarBitmap
            if (bm != null) {
                Image(
                    bitmap = bm.asImageBitmap(),
                    contentDescription = "头像",
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Text(
                    text = displayInitial,
                    color = avatarColor,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
            }
        }
        Spacer(modifier = Modifier.width(12.dp))
        Text(displayName, fontWeight = FontWeight.Medium, fontSize = 16.sp)
    }
    HorizontalDivider(
        modifier = Modifier.padding(start = if (isSelectionMode) 76.dp else 72.dp),
        color = DividerColor,
        thickness = 0.5.dp
    )
}

private fun contactSectionLetter(user: User): String {
    val source = user.nickname.ifBlank { user.userId }.trim()
    val first = source.firstOrNull() ?: return "#"
    chineseInitial(first)?.let { return it }
    return if (first.isLetter()) first.uppercaseChar().toString() else "#"
}

private fun sectionSortKey(section: String): String {
    return if (section == "#") "ZZZ" else section
}

private fun chineseInitial(char: Char): String? {
    if (char !in '\u4e00'..'\u9fa5') return null
    val gbk = runCatching { char.toString().toByteArray(charset("GBK")) }.getOrNull() ?: return "#"
    if (gbk.size < 2) return "#"
    val code = (gbk[0].toInt() and 0xff) * 256 + (gbk[1].toInt() and 0xff)
    val initials = charArrayOf(
        'A', 'B', 'C', 'D', 'E', 'F', 'G', 'H', 'J', 'K', 'L', 'M',
        'N', 'O', 'P', 'Q', 'R', 'S', 'T', 'W', 'X', 'Y', 'Z'
    )
    val ranges = intArrayOf(
        0xB0A1, 0xB0C5, 0xB2C1, 0xB4EE, 0xB6EA, 0xB7A2, 0xB8C1,
        0xB9FE, 0xBBF7, 0xBFA6, 0xC0AC, 0xC2E8, 0xC4C3, 0xC5B6,
        0xC5BE, 0xC6DA, 0xC8BB, 0xC8F6, 0xCBFA, 0xCDDA, 0xCEF4,
        0xD1B9, 0xD4D1
    )
    for (i in ranges.indices.reversed()) {
        if (code >= ranges[i]) return initials[i].toString()
    }
    return "#"
}

private fun contactDisplayName(user: User): String {
    return user.nickname.ifBlank { user.userId }
}

private fun contactComparator(): Comparator<User> {
    val collator = Collator.getInstance(Locale.CHINA)
    return Comparator { left, right ->
        val nameResult = collator.compare(contactDisplayName(left), contactDisplayName(right))
        if (nameResult != 0) nameResult else left.userId.compareTo(right.userId)
    }
}
