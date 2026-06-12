package com.lightchat.ui.navigation

import android.widget.Toast
import android.net.Uri
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.lightchat.LightChatApplication
import com.lightchat.event.AppEvents
import com.lightchat.model.ConversationId
import com.lightchat.model.ConversationType
import com.lightchat.model.Conversation
import com.lightchat.ui.chat.ChatScreen
import com.lightchat.ui.chat.ImageEditScreen
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lightchat.ui.chat.PhotoDetailScreen
import com.lightchat.ui.chat.PhotoEditScreen
import com.lightchat.viewmodel.ChatViewModel
import com.lightchat.ui.chat.PhotoPickerScreen
import com.lightchat.ui.forward.ForwardPreviewScreen
import com.lightchat.ui.forward.ForwardSelectScreen
import com.lightchat.ui.forward.MergeForwardDetailScreen
import com.lightchat.ui.friend.AddFriendScreen
import com.lightchat.ui.friend.FriendRequestScreen
import com.lightchat.ui.group.GroupCreateScreen
import com.lightchat.ui.group.GroupInviteScreen
import com.lightchat.ui.group.GroupListScreen
import com.lightchat.ui.group.GroupMemberListScreen
import com.lightchat.ui.login.LoginScreen
import com.lightchat.ui.main.MainScreen
import com.lightchat.ui.profile.ProfileScreen
import com.lightchat.ui.profile.UserCardShareScreen
import com.lightchat.ui.search.ChatSearchScreen
import com.lightchat.ui.search.SearchScreen
import kotlinx.coroutines.launch

object Routes {
    const val LOGIN = "login"
    const val MAIN = "main"
    const val CHAT = "chat/{conversationId}/{title}?targetMessageId={targetMessageId}"
    const val SEARCH = "search"
    const val ADD_FRIEND = "add_friend"
    const val GROUP_CREATE = "group_create"
    const val GROUP_LIST = "group_list"
    const val GROUP_INVITE = "group_invite/{groupId}"
    const val GROUP_MEMBERS = "group_members/{groupId}"
    const val FRIEND_REQUESTS = "friend_requests"
    const val PROFILE = "profile?userId={userId}"
    const val USER_CARD_SHARE = "user_card_share/{userId}"
    const val FORWARD_SELECT = "forward_select"
    const val FORWARD_PREVIEW = "forward_preview"
    const val MERGE_FORWARD_DETAIL = "merge_forward_detail/{messageId}"
    const val CHAT_SEARCH = "chat_search/{conversationId}/{title}"
    const val IMAGE_EDIT = "image_edit/{conversationId}"
    const val PHOTO_PICKER = "photo_picker/{conversationId}"
    const val PHOTO_DETAIL = "photo_detail/{conversationId}/{initialIndex}"
    const val PHOTO_EDIT = "photo_edit/{conversationId}/{photoIndex}"

    fun chat(conversationId: String, title: String, targetMessageId: String? = null): String {
        val base = "chat/${Uri.encode(conversationId)}/${Uri.encode(title)}"
        return targetMessageId?.takeIf { it.isNotBlank() }?.let {
            "$base?targetMessageId=${Uri.encode(it)}"
        } ?: base
    }
    fun profile(userId: String? = null) = if (userId != null) "profile?userId=${Uri.encode(userId)}" else "profile"
    fun chatSearch(conversationId: String, title: String) = "chat_search/${Uri.encode(conversationId)}/${Uri.encode(title)}"
    fun groupInvite(groupId: String) = "group_invite/$groupId"
    fun groupMembers(groupId: String) = "group_members/${Uri.encode(groupId)}"
    fun userCardShare(userId: String) = "user_card_share/${Uri.encode(userId)}"
    fun mergeForwardDetail(messageId: String) = "merge_forward_detail/$messageId"
    fun imageEdit(conversationId: String) = "image_edit/$conversationId"
    fun photoPicker(conversationId: String) = "photo_picker/$conversationId"
    fun photoDetail(conversationId: String, initialIndex: Int) = "photo_detail/$conversationId/$initialIndex"
    fun photoEdit(conversationId: String, photoIndex: Int) = "photo_edit/$conversationId/$photoIndex"
}

@Composable
fun LightChatNavGraph(
    navController: NavHostController,
    startDestination: String = Routes.LOGIN,
    initialConversationId: String? = null,
    initialConversationTitle: String? = null,
    initialTargetMessageId: String? = null,
    initialOpenFriendRequests: Boolean = false
) {
    val context = LocalContext.current
    LaunchedEffect(Unit) {
        launch {
            AppEvents.forcedLogout.collect { message ->
                Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                navController.navigate(Routes.LOGIN) {
                    popUpTo(navController.graph.startDestinationId) { inclusive = true }
                    launchSingleTop = true
                }
            }
        }
        launch {
            AppEvents.openConversation.collect { target ->
                if (LightChatApplication.instance.authRepository.isLoggedIn()) {
                    navController.navigate(
                        Routes.chat(
                            target.conversationId,
                            target.title.ifBlank { conversationTitle(target.conversationId) },
                            target.targetMessageId
                        )
                    ) {
                        launchSingleTop = false
                    }
                }
            }
        }
        launch {
            AppEvents.openFriendRequests.collect {
                if (LightChatApplication.instance.authRepository.isLoggedIn()) {
                    navController.navigate(Routes.FRIEND_REQUESTS)
                }
            }
        }
    }

    LaunchedEffect(initialOpenFriendRequests, startDestination) {
        if (initialOpenFriendRequests && startDestination != Routes.LOGIN) {
            navController.navigate(Routes.FRIEND_REQUESTS)
        }
    }

    LaunchedEffect(initialConversationId, startDestination) {
        val conversationId = initialConversationId.orEmpty()
        if (conversationId.isNotBlank() && startDestination != Routes.LOGIN) {
            val resolvedTitle = initialConversationTitle.orEmpty().ifBlank { conversationTitle(conversationId) }
            val route = Routes.chat(conversationId, resolvedTitle, initialTargetMessageId.orEmpty())
            navController.navigate(route) {
                launchSingleTop = true
            }
        }
    }

    NavHost(
        navController = navController,
        startDestination = startDestination,
        enterTransition = {
            slideIntoContainer(
                AnimatedContentTransitionScope.SlideDirection.Left,
                animationSpec = tween(NavAnimationMs)
            )
        },
        exitTransition = {
            ExitTransition.None
        },
        popEnterTransition = {
            EnterTransition.None
        },
        popExitTransition = {
            slideOutOfContainer(
                AnimatedContentTransitionScope.SlideDirection.Right,
                animationSpec = tween(NavAnimationMs)
            )
        }
    ) {
        composable(Routes.LOGIN) {
            LoginScreen(
                onLoginSuccess = {
                    LightChatApplication.instance.lastMainTab = 0
                    navController.navigate(Routes.MAIN) {
                        popUpTo(Routes.LOGIN) { inclusive = true }
                    }
                }
            )
        }

        composable(Routes.MAIN) {
            MainScreen(navController = navController)
        }

        composable(
            route = Routes.CHAT,
            arguments = listOf(
                navArgument("conversationId") { type = NavType.StringType },
                navArgument("title") { type = NavType.StringType },
                navArgument("targetMessageId") {
                    type = NavType.StringType
                    defaultValue = ""
                }
            )
        ) { backStackEntry ->
            val conversationId = backStackEntry.arguments?.getString("conversationId") ?: ""
            val title = backStackEntry.arguments?.getString("title") ?: ""
            val targetMessageId = backStackEntry.arguments?.getString("targetMessageId").orEmpty()
            val app = LightChatApplication.instance
            ChatScreen(
                conversationId = conversationId,
                title = title,
                targetMessageId = targetMessageId,
                onBack = { navController.popBackStack() },
                onForwardMessage = { messageId ->
                    app.currentForwardMessageIds = listOf(messageId)
                    app.currentForwardSnapshotPayloads = emptyList()
                    app.currentForwardSourceConversationId = conversationId
                    app.currentForwardRequiresTypeChoice = false
                    navController.navigate(Routes.FORWARD_SELECT)
                },
                onMultiForward = { selectedIds ->
                    app.currentForwardMessageIds = selectedIds
                    app.currentForwardSnapshotPayloads = emptyList()
                    app.currentForwardSourceConversationId = conversationId
                    app.currentForwardRequiresTypeChoice = true
                    navController.navigate(Routes.FORWARD_SELECT)
                },
                onSearchClick = {
                    navController.navigate(Routes.chatSearch(conversationId, title))
                },
                onShowGroupMembers = { groupId ->
                    navController.navigate(Routes.groupMembers(groupId))
                },
                onMergeForwardClick = { messageId ->
                    navController.navigate(Routes.mergeForwardDetail(messageId))
                },
                onUserCardClick = { userId ->
                    val currentUserId = LightChatApplication.instance.userSession.currentUserId
                    navController.navigate(
                        if (userId == currentUserId) Routes.profile() else Routes.profile(userId)
                    )
                },
                onAvatarClick = { userId ->
                    val currentUserId = LightChatApplication.instance.userSession.currentUserId
                    navController.navigate(
                        if (userId == currentUserId) Routes.profile() else Routes.profile(userId)
                    )
                },
                onPhotoPickerClick = {
                    navController.navigate(Routes.photoPicker(conversationId))
                }
            )
        }

        composable(Routes.SEARCH) {
            SearchScreen(
                onBack = { navController.popBackStack() },
                onChatClick = { conversationId, title, targetMessageId ->
                    navController.navigate(Routes.chat(conversationId, title, targetMessageId)) {
                        popUpTo(Routes.SEARCH) { inclusive = true }
                        launchSingleTop = false
                    }
                },
                onContactClick = { userId ->
                    navController.navigate(Routes.profile(userId))
                }
            )
        }

        composable(Routes.ADD_FRIEND) {
            AddFriendScreen(
                onBack = { navController.popBackStack() },
                onChatClick = { conversationId, title ->
                    navController.navigate(Routes.chat(conversationId, title))
                },
                onProfileClick = { userId ->
                    navController.navigate(Routes.profile(userId))
                }
            )
        }

        composable(Routes.GROUP_CREATE) {
            GroupCreateScreen(
                onBack = { navController.popBackStack() },
                onGroupCreated = { groupId, groupName ->
                    navController.navigate(Routes.chat(ConversationId.group(groupId), groupName)) {
                        popUpTo(Routes.GROUP_CREATE) { inclusive = true }
                    }
                }
            )
        }

        composable(Routes.GROUP_LIST) {
            GroupListScreen(
                onBack = { navController.popBackStack() },
                onGroupClick = { conversationId, title ->
                    LightChatApplication.instance.lastMainTab = 0
                    navController.navigate(Routes.MAIN) {
                        popUpTo(Routes.MAIN) { inclusive = true }
                        launchSingleTop = true
                    }
                    navController.navigate(Routes.chat(conversationId, title))
                }
            )
        }

        composable(
            route = Routes.GROUP_INVITE,
            arguments = listOf(navArgument("groupId") { type = NavType.StringType })
        ) { backStackEntry ->
            val groupId = backStackEntry.arguments?.getString("groupId") ?: ""
            GroupInviteScreen(
                groupId = groupId,
                onBack = { navController.popBackStack() },
                onInvited = { navController.popBackStack() }
            )
        }

        composable(
            route = Routes.GROUP_MEMBERS,
            arguments = listOf(navArgument("groupId") { type = NavType.StringType })
        ) { backStackEntry ->
            val groupId = backStackEntry.arguments?.getString("groupId") ?: ""
            GroupMemberListScreen(
                groupId = groupId,
                onBack = { navController.popBackStack() },
                onMemberClick = { memberUserId ->
                    val currentUserId = LightChatApplication.instance.userSession.currentUserId
                    navController.navigate(
                        if (memberUserId == currentUserId) Routes.profile() else Routes.profile(memberUserId)
                    )
                },
                onInviteClick = { navController.navigate(Routes.groupInvite(it)) }
            )
        }

        composable(Routes.FRIEND_REQUESTS) {
            FriendRequestScreen(
                onBack = { navController.popBackStack() }
            )
        }

        composable(
            route = Routes.PROFILE,
            arguments = listOf(
                navArgument("userId") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                }
            )
        ) { backStackEntry ->
            val userId = backStackEntry.arguments?.getString("userId")
            ProfileScreen(
                isSelf = userId == null,
                targetUserId = userId,
                onBack = { navController.popBackStack() },
                onLogout = null,
                onChat = if (userId != null) {
                    {
                        val app = LightChatApplication.instance
                        val currentId = app.userSession.currentUserId
                        if (currentId != null) {
                            val convId = ConversationId.single(currentId, userId)
                            if (app.conversationRepository.getConversation(convId) == null) {
                                val targetUser = app.userRepository.getUserById(userId)
                                app.conversationRepository.saveConversation(
                                    Conversation(
                                        conversationId = convId,
                                        type = ConversationType.SINGLE,
                                        targetId = userId,
                                        title = targetUser?.nickname ?: userId,
                                        lastMessageTime = System.currentTimeMillis()
                                    )
                                )
                            }
                            val targetUser = app.userRepository.getUserById(userId)
                            navController.navigate(Routes.chat(convId, targetUser?.nickname ?: userId))
                        }
                    }
                } else null,
                onRecommendCard = { targetUserId ->
                    navController.navigate(Routes.userCardShare(targetUserId))
                }
            )
        }

        composable(
            route = Routes.USER_CARD_SHARE,
            arguments = listOf(navArgument("userId") { type = NavType.StringType })
        ) { backStackEntry ->
            UserCardShareScreen(
                userId = backStackEntry.arguments?.getString("userId") ?: "",
                onBack = { navController.popBackStack() },
                onShared = { navController.popBackStack() }
            )
        }

        composable(Routes.FORWARD_SELECT) {
            ForwardSelectScreen(
                onBack = { navController.popBackStack() },
                onSend = { _ ->
                    navController.popBackStack(Routes.FORWARD_SELECT, inclusive = true)
                }
            )
        }

        composable(Routes.FORWARD_PREVIEW) {
            ForwardPreviewScreen(
                onBack = { navController.popBackStack() },
                onSend = { navController.popBackStack(Routes.FORWARD_SELECT, inclusive = true) }
            )
        }

        composable(
            route = Routes.MERGE_FORWARD_DETAIL,
            arguments = listOf(navArgument("messageId") { type = NavType.StringType })
        ) { backStackEntry ->
            val mergeForwardMessageId = backStackEntry.arguments?.getString("messageId") ?: ""
            MergeForwardDetailScreen(
                messageId = mergeForwardMessageId,
                onBack = { navController.popBackStack() },
                onForwardSnapshot = { snapshotPayload ->
                    val app = LightChatApplication.instance
                    app.currentForwardMessageIds = emptyList()
                    app.currentForwardSnapshotPayloads = listOf(snapshotPayload)
                    app.currentForwardSourceConversationId = app.messageDao.getById(mergeForwardMessageId)?.conversationId
                    app.currentForwardRequiresTypeChoice = false
                    navController.navigate(Routes.FORWARD_SELECT)
                },
                onUserCardClick = { userId ->
                    val currentUserId = LightChatApplication.instance.userSession.currentUserId
                    navController.navigate(
                        if (userId == currentUserId) Routes.profile() else Routes.profile(userId)
                    )
                }
            )
        }

        composable(
            route = Routes.CHAT_SEARCH,
            arguments = listOf(
                navArgument("conversationId") { type = NavType.StringType },
                navArgument("title") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val conversationId = backStackEntry.arguments?.getString("conversationId") ?: ""
            val title = backStackEntry.arguments?.getString("title") ?: ""
            ChatSearchScreen(
                conversationId = conversationId,
                title = title,
                onBack = { navController.popBackStack() },
                onMessageClick = { messageId ->
                    navController.navigate(Routes.chat(conversationId, title, messageId)) {
                        popUpTo(Routes.MAIN) { inclusive = false }
                        launchSingleTop = false
                    }
                }
            )
        }

        composable(
            route = Routes.PHOTO_DETAIL,
            arguments = listOf(
                navArgument("conversationId") { type = NavType.StringType },
                navArgument("initialIndex") { type = NavType.IntType }
            ),
            enterTransition = {
                slideIntoContainer(
                    AnimatedContentTransitionScope.SlideDirection.Left,
                    animationSpec = tween(260)
                )
            },
            exitTransition = {
                ExitTransition.None
            },
            popEnterTransition = {
                EnterTransition.None
            },
            popExitTransition = {
                slideOutOfContainer(
                    AnimatedContentTransitionScope.SlideDirection.Right,
                    animationSpec = tween(260)
                )
            }
        ) { backStackEntry ->
            val conversationId = backStackEntry.arguments?.getString("conversationId") ?: ""
            val initialIndex = backStackEntry.arguments?.getInt("initialIndex") ?: 0
            val chatVm: ChatViewModel = viewModel()
            PhotoDetailScreen(
                conversationId = conversationId,
                initialIndex = initialIndex,
                onBack = { navController.popBackStack() },
                onEditClick = { photoIndex ->
                    navController.navigate(Routes.photoEdit(conversationId, photoIndex))
                },
                onSendClick = {
                    val uris = LightChatApplication.instance.pendingImageUris
                    LightChatApplication.instance.pendingImageSendConversationId = conversationId
                    chatVm.sendMultipleImages(uris, conversationId = conversationId)
                    LightChatApplication.instance.pendingImageUris = emptyList()
                    LightChatApplication.instance.pickerSelectedIndices = emptyList()
                    LightChatApplication.instance.pickerEditedPaths = emptyMap()
                    LightChatApplication.instance.pickerAllPhotoUris = emptyList()
                    navController.popBackStack(Routes.photoPicker(conversationId), inclusive = true)
                }
            )
        }

        composable(
            route = Routes.PHOTO_EDIT,
            arguments = listOf(
                navArgument("conversationId") { type = NavType.StringType },
                navArgument("photoIndex") { type = NavType.IntType }
            )
        ) { backStackEntry ->
            val conversationId = backStackEntry.arguments?.getString("conversationId") ?: ""
            val photoIndex = backStackEntry.arguments?.getInt("photoIndex") ?: 0
            PhotoEditScreen(
                conversationId = conversationId,
                photoIndex = photoIndex,
                onBack = { navController.popBackStack() },
                onConfirm = { navController.popBackStack() }
            )
        }

        composable(
            route = Routes.IMAGE_EDIT,
            arguments = listOf(navArgument("conversationId") { type = NavType.StringType })
        ) { backStackEntry ->
            val conversationId = backStackEntry.arguments?.getString("conversationId") ?: ""
            ImageEditScreen(
                conversationId = conversationId,
                onBack = { navController.popBackStack() },
                onSent = { navController.popBackStack() }
            )
        }

        composable(
            route = Routes.PHOTO_PICKER,
            arguments = listOf(navArgument("conversationId") { type = NavType.StringType }),
            enterTransition = {
                slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Up, animationSpec = tween(300))
            },
            exitTransition = {
                ExitTransition.None
            },
            popEnterTransition = {
                EnterTransition.None
            },
            popExitTransition = {
                slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.Down, animationSpec = tween(300))
            }
        ) { backStackEntry ->
            val conversationId = backStackEntry.arguments?.getString("conversationId") ?: ""
            val chatVm: ChatViewModel = viewModel()
            PhotoPickerScreen(
                conversationId = conversationId,
                onBack = { navController.popBackStack() },
                onSend = {
                    val uris = LightChatApplication.instance.pendingImageUris
                    LightChatApplication.instance.pendingImageSendConversationId = conversationId
                    chatVm.sendMultipleImages(uris, conversationId = conversationId)
                    LightChatApplication.instance.pendingImageUris = emptyList()
                    LightChatApplication.instance.pickerSelectedIndices = emptyList()
                    LightChatApplication.instance.pickerEditedPaths = emptyMap()
                    LightChatApplication.instance.pickerAllPhotoUris = emptyList()
                    navController.popBackStack(Routes.photoPicker(conversationId), inclusive = true)
                },
                onPhotoClick = { index ->
                    navController.navigate(Routes.photoDetail(conversationId, index))
                }
            )
        }
    }
}

private fun conversationTitle(conversationId: String): String {
    val app = LightChatApplication.instance
    return app.conversationRepository.getConversation(conversationId)?.title?.takeIf { it.isNotBlank() }
        ?: "聊天"
}

private const val NavAnimationMs = 180
