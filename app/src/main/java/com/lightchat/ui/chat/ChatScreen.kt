package com.lightchat.ui.chat

import android.net.Uri

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Forward
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.SentimentSatisfiedAlt
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lightchat.LightChatApplication
import com.lightchat.model.ConversationType
import com.lightchat.model.Message
import com.lightchat.model.MessageStatus
import com.lightchat.model.MessageType
import com.lightchat.ui.forward.parseMergeForwardLines
import com.lightchat.ui.theme.*
import com.lightchat.viewmodel.ChatUiState
import com.lightchat.viewmodel.ChatViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale


@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ChatScreen(
    conversationId: String,
    title: String,
    targetMessageId: String = "",
    onBack: () -> Unit,
    onForwardMessage: (messageId: String) -> Unit = {},
    onMultiForward: (List<String>) -> Unit = {},
    onSearchClick: () -> Unit = {},
    onShowGroupMembers: (String) -> Unit = {},
    onMergeForwardClick: (String) -> Unit = {},
    onUserCardClick: (String) -> Unit = {},
    onAvatarClick: (String) -> Unit = {},
    onPhotoPickerClick: () -> Unit = {},
    viewModel: ChatViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val listState = rememberLazyListState()
    val inputFocusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    val messageAreaInteraction = remember { MutableInteractionSource() }
    val density = LocalDensity.current
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val scope = rememberCoroutineScope()
    var anchoredMenu by remember { mutableStateOf<AnchoredMessageMenu?>(null) }
    var retryMessage by remember { mutableStateOf<Message?>(null) }
    var isMultiSelectMode by rememberSaveable { mutableStateOf(false) }
    var selectedMessageIds by rememberSaveable { mutableStateOf(setOf<String>()) }
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }
    var singleDeleteMessageId by remember { mutableStateOf<String?>(null) }
    var showMentionDialog by remember { mutableStateOf(false) }
    var mentionedUserIds by remember { mutableStateOf(listOf<String>()) }
    var mentionSelectedIds by remember { mutableStateOf(setOf<String>()) }
    var bottomPanel by remember { mutableStateOf(ChatBottomPanel.NONE) }
    var fullscreenImageMessageId by remember { mutableStateOf<String?>(null) }
    var highlightedMessageId by remember { mutableStateOf<String?>(null) }
    var consumedHighlightTarget by rememberSaveable(conversationId, targetMessageId) { mutableStateOf(false) }
    var targetScrollSettled by rememberSaveable(conversationId, targetMessageId) { mutableStateOf(targetMessageId.isBlank()) }
    var lastLoadOlderTriggerMs by remember { mutableLongStateOf(0L) }
    var lastLoadNewerTriggerMs by remember { mutableLongStateOf(0L) }
    var pendingPrependAnchor by remember(conversationId) { mutableStateOf<Pair<String, Int>?>(null) }
    var inputBarHeightPx by remember { mutableIntStateOf(0) }
    val showEmojiPanel = bottomPanel == ChatBottomPanel.EMOJI
    val showMorePanel = bottomPanel == ChatBottomPanel.MORE
    val targetPanelProgress = when (bottomPanel) {
        ChatBottomPanel.EMOJI,
        ChatBottomPanel.MORE -> 1f
        ChatBottomPanel.NONE -> 0f
    }
    val animatedPanelProgress by animateFloatAsState(
        targetValue = targetPanelProgress,
        animationSpec = tween(
            durationMillis = BottomPanelPullMs,
            easing = FastOutSlowInEasing
        ),
        label = "chat_bottom_panel_progress"
    )
    val panelFullHeightPx = with(density) { DefaultInputPanelHeight.roundToPx() }
    val animatedPanelOffsetPx = (panelFullHeightPx * animatedPanelProgress).toInt()
    val messageListBottomPadding = with(density) {
        inputBarHeightPx.toDp() + 8.dp
    }
    var keepBottomAnchored by rememberSaveable(conversationId) { mutableStateOf(true) }
    var handledScrollToBottomRequest by rememberSaveable(conversationId) { mutableIntStateOf(0) }
    val currentConversation = remember(conversationId) {
        LightChatApplication.instance.conversationRepository.getConversation(conversationId)
    }
    val displayTitle = uiState.title.ifBlank { title }

    val view = LocalView.current
    DisposableEffect(Unit) {
        val window = (view.context as? android.app.Activity)?.window
        val originalStatusColor = window?.statusBarColor
        val originalNavColor = window?.navigationBarColor
        val insetsController = window?.let { w -> WindowCompat.getInsetsController(w, view) }
        val originalLightStatus = insetsController?.isAppearanceLightStatusBars
        val originalLightNav = insetsController?.isAppearanceLightNavigationBars

        window?.statusBarColor = TopBarBackground.toArgb()
        window?.navigationBarColor = BottomBarBackground.toArgb()
        insetsController?.apply {
            isAppearanceLightStatusBars = true
            isAppearanceLightNavigationBars = true
        }

        onDispose {
            originalStatusColor?.let { window?.statusBarColor = it }
            originalNavColor?.let { window?.navigationBarColor = it }
            insetsController?.apply {
                originalLightStatus?.let { isAppearanceLightStatusBars = it }
                originalLightNav?.let { isAppearanceLightNavigationBars = it }
            }
        }
    }

    fun closeBottomPanels() {
        bottomPanel = ChatBottomPanel.NONE
        keyboardController?.hide()
        focusManager.clearFocus(force = true)
    }

    fun prepareBottomThen(action: () -> Unit) {
        scope.launch {
            if (uiState.hasNewerMessages || uiState.isPositionedMidConversation) {
                viewModel.jumpToLatest()
                targetScrollSettled = true
            }
            scrollConversationToHardBottom(listState, uiState)
            keepBottomAnchored = !uiState.hasMoreMessages && !uiState.hasNewerMessages && !(uiState.isPositionedMidConversation && uiState.totalMessageCount > 1000)
            withFrameNanos { }
            action()
        }
    }

    // Load mention-able participants
    val mentionCandidates = remember(conversationId) {
        val app = LightChatApplication.instance
        val conv = app.conversationRepository.getConversation(conversationId)
        if (conv?.type == ConversationType.GROUP) {
            app.groupDao.getMembers(conversationId.replace("group_", ""))
                .filter { it.userId != app.userSession.currentUserId }
                .map { it.userId to it.nickname }
        } else {
            emptyList()
        }
    }

    retryMessage?.let { failedMessage ->
        RetryMessageDialog(
            onDismiss = { retryMessage = null },
            onConfirm = {
                retryMessage = null
                viewModel.resendMessage(failedMessage.messageId)
            }
        )
    }

    if (showDeleteConfirmDialog) {
        DeleteSelectedMessagesDialog(
            selectedCount = selectedMessageIds.size,
            onDismiss = { showDeleteConfirmDialog = false },
            onConfirm = {
                showDeleteConfirmDialog = false
                selectedMessageIds.forEach { viewModel.deleteMessage(it) }
                selectedMessageIds = emptySet()
                isMultiSelectMode = false
            }
        )
    }

    if (singleDeleteMessageId != null) {
        DeleteSingleMessageDialog(
            onDismiss = { singleDeleteMessageId = null },
            onConfirm = {
                val msgId = singleDeleteMessageId!!
                singleDeleteMessageId = null
                viewModel.deleteMessage(msgId)
            }
        )
    }

    LaunchedEffect(conversationId, targetMessageId) {
        viewModel.loadConversation(conversationId, targetMessageId)
    }

    DisposableEffect(conversationId) {
        LightChatApplication.instance.currentOpenConversationId = conversationId
        onDispose {
            if (LightChatApplication.instance.currentOpenConversationId == conversationId) {
                LightChatApplication.instance.currentOpenConversationId = null
            }
        }
    }

    DisposableEffect(lifecycleOwner, conversationId) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                val app = LightChatApplication.instance
                if (app.pendingImageSendConversationId == conversationId) {
                    scope.launch {
                        delay(250)
                        if (app.pendingImageSendConversationId == conversationId) {
                            app.pendingImageSendConversationId = null
                        }
                        keepBottomAnchored = true
                        viewModel.refreshVisibleMessages(scrollToBottom = true)
                    }
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(uiState.scrollToBottomRequest) {
        if (
            uiState.scrollToBottomRequest > handledScrollToBottomRequest &&
            uiState.messages.isNotEmpty()
        ) {
            handledScrollToBottomRequest = uiState.scrollToBottomRequest
            keepBottomAnchored = true
            scrollConversationToHardBottom(listState, uiState)
        }
    }

    LaunchedEffect(targetMessageId, uiState.messages) {
        val targetId = targetMessageId.takeIf { it.isNotBlank() } ?: return@LaunchedEffect
        if (consumedHighlightTarget) return@LaunchedEffect
        if (uiState.messages.isEmpty()) return@LaunchedEffect
        val targetIndex = messageContentIndex(uiState, targetId)
        if (targetIndex >= 0) {
            consumedHighlightTarget = true
            keepBottomAnchored = false
            withFrameNanos { }
            scrollMessageToViewportCenter(listState, targetIndex)
            withFrameNanos { }
            highlightedMessageId = targetId
            delay(3_000)
            if (highlightedMessageId == targetId) {
                highlightedMessageId = null
            }
            targetScrollSettled = true
        }
    }

    LaunchedEffect(targetMessageId) {
        if (targetMessageId.isNotBlank()) {
            delay(8_000)
            targetScrollSettled = true
        }
    }

    LaunchedEffect(listState) {
        snapshotFlow {
            Triple(
                listState.firstVisibleItemIndex,
                uiState.hasMoreMessages,
                uiState.isLoadingMoreMessages
            )
        }
            .distinctUntilChanged()
            .collect { (firstVisible, hasMore, isLoadingMore) ->
                val now = System.currentTimeMillis()
                if (hasMore && !isLoadingMore && firstVisible <= ChatHistoryPrefetchThreshold
                    && targetScrollSettled && now - lastLoadOlderTriggerMs >= 500
                ) {
                    lastLoadOlderTriggerMs = now
                    val anchorId = listState.layoutInfo.visibleItemsInfo
                        .asSequence()
                        .mapNotNull {
                            visibleMessageIdAtContentIndex(uiState, it.index)
                                ?.let { id -> id to (it.offset - listState.layoutInfo.viewportStartOffset) }
                        }
                        .firstOrNull()
                    if (anchorId != null) {
                        pendingPrependAnchor = anchorId
                    }
                    viewModel.loadMoreMessages()
                }
            }
    }

    LaunchedEffect(listState) {
        snapshotFlow {
            val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
            val lastContent = lastConversationContentIndex(uiState)
            NewerLoadSignal(
                lastVisible = lastVisible,
                lastContent = lastContent,
                hasNewer = uiState.hasNewerMessages,
                isLoading = uiState.isLoadingNewerMessages
            )
        }
            .distinctUntilChanged()
            .collect { signal ->
                val now = System.currentTimeMillis()
                val loadNewerAllowed = targetScrollSettled
                if (
                    signal.hasNewer &&
                    !signal.isLoading &&
                    signal.lastContent >= 0 &&
                    signal.lastVisible >= signal.lastContent - ChatHistoryPrefetchThreshold &&
                    loadNewerAllowed &&
                    now - lastLoadNewerTriggerMs >= 500
                ) {
                    lastLoadNewerTriggerMs = now
                    runCatching { viewModel.loadNewerMessages() }
                }
            }
    }

    LaunchedEffect(uiState.messages.firstOrNull()?.messageId, uiState.messages.size, uiState.isLoadingMoreMessages) {
        val anchor = pendingPrependAnchor ?: return@LaunchedEffect
        if (uiState.isLoadingMoreMessages) return@LaunchedEffect
        val index = messageContentIndex(uiState, anchor.first)
        if (index >= 0 && index < listState.layoutInfo.totalItemsCount) {
            withFrameNanos { }
            listState.scrollToItem(index, -anchor.second)
        }
        pendingPrependAnchor = null
    }

    LaunchedEffect(inputBarHeightPx, keepBottomAnchored) {
        if (keepBottomAnchored && uiState.messages.isNotEmpty()) {
            withFrameNanos { }
            scrollConversationToHardBottom(listState, uiState)
        }
    }

    LaunchedEffect(listState, uiState.messages.size, uiState.isLoadingMoreMessages) {
        snapshotFlow {
            Triple(
                listState.firstVisibleItemIndex,
                listState.firstVisibleItemScrollOffset,
                listState.isScrollInProgress
            )
        }.collect { (_, _, isScrolling) ->
            if (isScrolling && uiState.messages.isNotEmpty()) {
                keepBottomAnchored = false
            }
        }
    }


    LaunchedEffect(uiState.focusInputRequest) {
        if (uiState.focusInputRequest > 0) {
            inputFocusRequester.requestFocus()
            keyboardController?.show()
        }
    }

    if (showMentionDialog && mentionCandidates.isNotEmpty()) {
        ChatMentionDialog(
            candidates = mentionCandidates,
            selectedIds = mentionSelectedIds,
            inputText = uiState.inputText,
            onSelectedIdsChange = { mentionSelectedIds = it },
            onClearFocus = {
                focusManager.clearFocus()
                keyboardController?.hide()
            },
            onConfirm = { newInputText, selectedIds ->
                viewModel.onInputChange(newInputText)
                mentionedUserIds = (mentionedUserIds + selectedIds).distinct()
                mentionSelectedIds = emptySet()
                showMentionDialog = false
            },
            onDismiss = {
                mentionSelectedIds = emptySet()
                showMentionDialog = false
            }
        )
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val screenWidthPx = with(density) { maxWidth.roundToPx() }
        val screenHeightPx = with(density) { maxHeight.roundToPx() }
        Scaffold(
            modifier = Modifier.imePadding(),
            contentWindowInsets = WindowInsets.systemBars,
            topBar = {
                Column {
                    ChatTopBar(
                        displayTitle = displayTitle,
                        isMultiSelectMode = isMultiSelectMode,
                        selectedCount = selectedMessageIds.size,
                        isGroup = currentConversation?.type == ConversationType.GROUP,
                        onBack = onBack,
                        onCancelMultiSelect = {
                            isMultiSelectMode = false
                            selectedMessageIds = emptySet()
                        },
                        onForwardSelected = {
                            onMultiForward(selectedMessageIds.toList().sorted())
                            isMultiSelectMode = false
                            selectedMessageIds = emptySet()
                        },
                        onDeleteSelected = { showDeleteConfirmDialog = true },
                        onShowGroupMembers = {
                            currentConversation?.targetId?.let(onShowGroupMembers)
                        },
                        onSearchClick = onSearchClick
                    )
                    HorizontalDivider(thickness = 0.5.dp, color = DividerColor)
                }
            }
        ) { padding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .background(TopBarBackground)
                    .clipToBounds()
            ) {
                ChatMessageList(
                    uiState = uiState,
                    listState = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .offset { IntOffset(0, -animatedPanelOffsetPx) },
                    isMultiSelectMode = isMultiSelectMode,
                    selectedMessageIds = selectedMessageIds,
                    highlightedMessageId = highlightedMessageId,
                    isGroupChat = currentConversation?.type == ConversationType.GROUP,
                    messageAreaInteraction = messageAreaInteraction,
                    contentPadding = PaddingValues(
                        start = 12.dp,
                        top = 8.dp,
                        end = 12.dp,
                        bottom = messageListBottomPadding
                    ),
                    onCloseBottomPanels = { closeBottomPanels() },
                    onAvatarClick = onAvatarClick,
                    onUserCardClick = onUserCardClick,
                    onRecallEdit = { messageId -> viewModel.reeditRecalledMessage(messageId) },
                    onImageClick = { messageId ->
                        closeBottomPanels()
                        fullscreenImageMessageId = messageId
                    },
                    onRetryMessage = { message -> retryMessage = message },
                    onMessageLongPress = { message, bounds ->
                        if (!isMultiSelectMode) {
                            bottomPanel = ChatBottomPanel.NONE
                            keyboardController?.hide()
                            focusManager.clearFocus(force = true)
                            anchoredMenu = AnchoredMessageMenu(message, bounds)
                        }
                    },
                    onMessageSelectionToggle = { messageId ->
                        selectedMessageIds = if (messageId in selectedMessageIds) {
                            selectedMessageIds - messageId
                        } else {
                            selectedMessageIds + messageId
                        }
                    },
                    onMergeForwardClick = onMergeForwardClick
                )
                if (bottomPanel != ChatBottomPanel.NONE) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .height(DefaultInputPanelHeight)
                            .offset { IntOffset(0, (panelFullHeightPx * (1f - animatedPanelProgress)).toInt()) }
                            .alpha(animatedPanelProgress)
                            .background(BottomBarBackground)
                            .clipToBounds()
                    ) {
                        ChatBottomPanelContent(
                            showEmojiPanel = showEmojiPanel,
                            showMorePanel = showMorePanel,
                            onEmojiSelected = { emoji ->
                                viewModel.onInputChange(uiState.inputText + emoji)
                            },
                            onPhotoClick = {
                                bottomPanel = ChatBottomPanel.NONE
                                onPhotoPickerClick()
                            }
                        )
                    }
                }
                ChatInputBar(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .offset { IntOffset(0, -animatedPanelOffsetPx) }
                        .onGloballyPositioned { coordinates ->
                            inputBarHeightPx = coordinates.size.height
                        },
                    visible = true,
                    inputText = uiState.inputText,
                    inputFocusRequester = inputFocusRequester,
                    onInputChange = { value ->
                        val wasAtTyped = value.endsWith("@") && value.length > uiState.inputText.length
                        viewModel.onInputChange(value)
                        if (wasAtTyped && mentionCandidates.isNotEmpty()) {
                            mentionSelectedIds = emptySet()
                            showMentionDialog = true
                        }
                    },
                    onInputFocused = {
                        prepareBottomThen {
                            bottomPanel = ChatBottomPanel.NONE
                        }
                    },
                    onEmojiClick = {
                        prepareBottomThen {
                            focusManager.clearFocus(force = true)
                            keyboardController?.hide()
                            bottomPanel = if (bottomPanel == ChatBottomPanel.EMOJI) ChatBottomPanel.NONE else ChatBottomPanel.EMOJI
                        }
                    },
                    onMoreClick = {
                        prepareBottomThen {
                            focusManager.clearFocus(force = true)
                            keyboardController?.hide()
                            bottomPanel = if (bottomPanel == ChatBottomPanel.MORE) ChatBottomPanel.NONE else ChatBottomPanel.MORE
                        }
                    },
                    onSendClick = {
                        viewModel.sendMessage(mentionedUserIds)
                        mentionedUserIds = emptyList()
                        bottomPanel = ChatBottomPanel.NONE
                    }
                )
                if (uiState.isPositionedMidConversation && uiState.totalMessageCount > 1000) {
                    SmallFloatingActionButton(
                        onClick = {
                            closeBottomPanels()
                            keepBottomAnchored = true
                            highlightedMessageId = null
                            viewModel.jumpToLatest()
                        },
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(end = 18.dp, bottom = messageListBottomPadding + 16.dp),
                        containerColor = DividerColor,
                        contentColor = Color.Black
                    ) {
                        Text("↓", fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        fullscreenImageMessageId?.let { messageId ->
            ImageViewerScreen(
                conversationId = conversationId,
                initialMessageId = messageId,
                onBack = { fullscreenImageMessageId = null },
                onForward = { forwardMessageId ->
                    fullscreenImageMessageId = null
                    onForwardMessage(forwardMessageId)
                }
            )
        }

        anchoredMenu?.let { menu ->
            AnchoredMessageActionMenu(
                message = menu.message,
                bounds = menu.bounds,
                screenWidthPx = screenWidthPx,
                screenHeightPx = screenHeightPx,
                inputBarHeightPx = inputBarHeightPx,
                onDismiss = { anchoredMenu = null },
                onCopy = {
                    clipboardManager.setText(AnnotatedString(menu.message.content))
                    Toast.makeText(context, "已复制", Toast.LENGTH_SHORT).show()
                    anchoredMenu = null
                },
                onForward = {
                    anchoredMenu = null
                    onForwardMessage(menu.message.messageId)
                },
                onMultiSelect = {
                    anchoredMenu = null
                    bottomPanel = ChatBottomPanel.NONE
                    keyboardController?.hide()
                    focusManager.clearFocus(force = true)
                    isMultiSelectMode = true
                    selectedMessageIds = setOf(menu.message.messageId)
                },
                onRecall = {
                    anchoredMenu = null
                    viewModel.recallMessage(menu.message.messageId)
                },
                onDelete = {
                    anchoredMenu = null
                    singleDeleteMessageId = menu.message.messageId
                }
            )
        }
    }
}
