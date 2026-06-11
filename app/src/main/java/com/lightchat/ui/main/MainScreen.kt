package com.lightchat.ui.main

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.Contacts
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.ChatBubble
import androidx.compose.material.icons.outlined.Contacts
import androidx.compose.material3.Badge
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.lightchat.LightChatApplication
import com.lightchat.event.AppEvents
import com.lightchat.ui.contact.ContactScreen
import com.lightchat.ui.conversation.ConversationListScreen
import com.lightchat.ui.navigation.Routes
import com.lightchat.ui.profile.ProfileScreen
import com.lightchat.ui.theme.BottomBarBackground
import com.lightchat.ui.theme.DividerColor
import com.lightchat.ui.theme.UnreadRed
import com.lightchat.ui.theme.WeChatGreen
import com.lightchat.ui.theme.WeChatWhite
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class BottomNavItem(
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val badge: Int = 0
)

@Composable
fun MainScreen(navController: NavHostController) {
    val app = LightChatApplication.instance
    val currentUserId = app.userSession.currentUserId

    var selectedTab by rememberSaveable(currentUserId) {
        mutableIntStateOf(app.lastMainTab.coerceIn(0, 2))
    }

    var messageBadge by remember(currentUserId) { mutableIntStateOf(0) }
    var contactBadge by remember(currentUserId) { mutableIntStateOf(0) }

    LaunchedEffect(selectedTab) {
        app.lastMainTab = selectedTab
    }

    LaunchedEffect(currentUserId) {
        suspend fun refreshMessageBadge() {
            messageBadge = withContext(Dispatchers.IO) {
                app.conversationDao.getTotalUnreadCount()
            }
        }

        suspend fun refreshContactBadge() {
            contactBadge = currentUserId?.let { userId ->
                withContext(Dispatchers.IO) {
                    app.friendRequestDao.getPendingCount(userId)
                }
            } ?: 0
        }

        refreshMessageBadge()
        refreshContactBadge()

        launch {
            AppEvents.conversationChanged.collect {
                refreshMessageBadge()
            }
        }

        launch {
            AppEvents.friendRequestChanged.collect {
                refreshContactBadge()
            }
        }
    }

    Scaffold(
        bottomBar = {
            MainBottomBar(
                selectedTab = selectedTab,
                messageBadge = messageBadge,
                contactBadge = contactBadge,
                onTabSelected = { index ->
                    selectedTab = index
                }
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            when (selectedTab) {
                0 -> ConversationListScreen(
                    modifier = Modifier.fillMaxSize(),
                    onChatClick = { conversationId, title, targetMessageId ->
                        navController.navigate(
                            Routes.chat(
                                conversationId,
                                title,
                                targetMessageId
                            )
                        )
                    },
                    onSearchClick = {
                        navController.navigate(Routes.SEARCH)
                    },
                    onCreateGroup = {
                        navController.navigate(Routes.GROUP_CREATE)
                    },
                    onAddFriend = {
                        navController.navigate(Routes.ADD_FRIEND)
                    }
                )

                1 -> ContactScreen(
                    modifier = Modifier.fillMaxSize(),
                    onGroupListClick = {
                        app.lastMainTab = 1
                        navController.navigate(Routes.GROUP_LIST)
                    },
                    onGroupClick = { conversationId, title ->
                        app.lastMainTab = 1
                        navController.navigate(Routes.chat(conversationId, title))
                    },
                    onProfileClick = { userId ->
                        app.lastMainTab = 1
                        navController.navigate(Routes.profile(userId))
                    },
                    onFriendRequestsClick = {
                        app.lastMainTab = 1
                        navController.navigate(Routes.FRIEND_REQUESTS)
                    }
                )

                else -> ProfileScreen(
                    modifier = Modifier.fillMaxSize(),
                    isSelf = true,
                    onLogout = {
                        app.apply {
                            syncManager.destroy()
                            imClient.disconnect()
                            authRepository.logout()
                        }

                        navController.navigate(Routes.LOGIN) {
                            popUpTo(navController.graph.startDestinationId) {
                                inclusive = true
                            }
                            launchSingleTop = true
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun MainBottomBar(
    selectedTab: Int,
    messageBadge: Int,
    contactBadge: Int,
    onTabSelected: (Int) -> Unit
) {
    val items = listOf(
        BottomNavItem(
            label = "消息",
            selectedIcon = Icons.Filled.ChatBubble,
            unselectedIcon = Icons.Outlined.ChatBubble
        ),
        BottomNavItem(
            label = "联系人",
            selectedIcon = Icons.Filled.Contacts,
            unselectedIcon = Icons.Outlined.Contacts
        ),
        BottomNavItem(
            label = "我的",
            selectedIcon = Icons.Filled.AccountCircle,
            unselectedIcon = Icons.Outlined.AccountCircle
        )
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(BottomBarBackground)
    ) {
        HorizontalDivider(
            thickness = BottomBarDividerThickness,
            color = DividerColor
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(MainBottomBarHeight)
                .background(BottomBarBackground),
            verticalAlignment = Alignment.CenterVertically
        ) {
            items.forEachIndexed { index, item ->
                val selected = selectedTab == index

                val badgeCount = when (index) {
                    0 -> messageBadge
                    1 -> contactBadge
                    else -> 0
                }

                BottomBarItemContent(
                    modifier = Modifier.weight(1f),
                    item = item,
                    selected = selected,
                    badgeCount = badgeCount,
                    onClick = {
                        onTabSelected(index)
                    }
                )
            }
        }
    }
}

@Composable
private fun BottomBarItemContent(
    modifier: Modifier = Modifier,
    item: BottomNavItem,
    selected: Boolean,
    badgeCount: Int,
    onClick: () -> Unit
) {
    val contentColor = if (selected) {
        WeChatGreen
    } else {
        BottomBarUnselectedColor
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box {
                Icon(
                    imageVector = if (selected) {
                        item.selectedIcon
                    } else {
                        item.unselectedIcon
                    },
                    contentDescription = item.label,
                    tint = contentColor,
                    modifier = Modifier.size(BottomBarIconSize)
                )

                if (badgeCount > 0) {
                    Badge(
                        containerColor = UnreadRed,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .offset(
                                x = BottomBarBadgeOffsetX,
                                y = BottomBarBadgeOffsetY
                            )
                    ) {
                        Text(
                            text = formatTabBadge(badgeCount),
                            color = WeChatWhite,
                            fontSize = BottomBarBadgeFontSize
                        )
                    }
                }
            }

            Spacer(
                modifier = Modifier.height(BottomBarIconTextSpacing)
            )

            Text(
                text = item.label,
                fontSize = BottomBarLabelFontSize,
                fontWeight = if (selected) {
                    FontWeight.Bold
                } else {
                    FontWeight.Normal
                },
                color = contentColor
            )
        }
    }
}

@Preview(
    name = "MainBottomBar 单独预览",
    showBackground = true,
    widthDp = 390,
    heightDp = 52
)
@Composable
fun MainBottomBarPreview() {
    MainBottomBar(
        selectedTab = 0,
        messageBadge = 8,
        contactBadge = 2,
        onTabSelected = {}
    )
}

private fun formatTabBadge(count: Int): String {
    return if (count > 99) "99+" else count.toString()
}

/**
 * 底部栏样式参数统一在这里修改。
 */
private val MainBottomBarHeight = 60.dp
private val BottomBarIconSize = 21.dp
private val BottomBarLabelFontSize = 10.sp
private val BottomBarBadgeFontSize = 9.sp
private val BottomBarIconTextSpacing = 1.dp
private val BottomBarDividerThickness = 0.5.dp
private val BottomBarUnselectedColor = Color(0xFF333333)

private val BottomBarBadgeOffsetX = 10.dp
private val BottomBarBadgeOffsetY = (-6).dp
