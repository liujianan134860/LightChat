package com.lightchat.ui.profile

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.Image
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lightchat.LightChatApplication
import com.lightchat.data.remote.AuthApiClient
import com.lightchat.event.AppEvents
import com.lightchat.model.User
import com.lightchat.ui.theme.*
import com.lightchat.util.showToast
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val LIGHTCHAT_ASSISTANT_ID = "lightchat_assistant"

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ProfileScreen(
    modifier: Modifier = Modifier,
    isSelf: Boolean = true,
    targetUserId: String? = null,
    onBack: (() -> Unit)? = null,
    onLogout: (() -> Unit)? = null,
    onChat: (() -> Unit)? = null,
    onRecommendCard: ((String) -> Unit)? = null
) {
    val app = LightChatApplication.instance
    val currentUserId = app.userSession.currentUserId
    val userId = if (isSelf) currentUserId else targetUserId

    var user by remember { mutableStateOf(userId?.let { app.userRepository.getUserById(it) }) }
    val displayNickname = user?.nickname
        ?.takeIf { it.isNotBlank() && it != user?.userId }
        ?: "未设置"
    var isFriend by remember {
        mutableStateOf(
            if (currentUserId != null && userId != null && currentUserId != userId)
                app.userDao.isFriend(currentUserId, userId)
            else false
        )
    }
    var isEditing by remember { mutableStateOf(false) }
    var editNickname by remember { mutableStateOf(user?.nickname ?: "") }
    var editSignature by remember { mutableStateOf(user?.signature ?: "") }
    var editRegion by remember(user) { mutableStateOf(user?.region ?: "") }
    var editAvatar by remember { mutableStateOf(user?.avatar ?: "") }
    var pickedAvatarUri by remember { mutableStateOf<Uri?>(null) }
    var showAvatarPicker by remember { mutableStateOf(false) }
    var showLogoutConfirm by remember { mutableStateOf(false) }
    val scrollState = rememberScrollState()
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val scope = rememberCoroutineScope()
    val nicknameRequester = remember { BringIntoViewRequester() }
    val signatureRequester = remember { BringIntoViewRequester() }
    val regionRequester = remember { BringIntoViewRequester() }
    val clearKeyboardFocus = {
        focusManager.clearFocus(force = true)
        keyboardController?.hide()
    }
    val avatarPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        uri?.let {
            pickedAvatarUri = it
            editAvatar = "uri://picked"
        }
    }

    LaunchedEffect(user?.userId, user?.nickname, user?.signature, user?.region, user?.avatar) {
        if (!isEditing) {
            editNickname = user?.nickname.orEmpty()
            editSignature = user?.signature.orEmpty()
            editRegion = user?.region.orEmpty()
            editAvatar = user?.avatar.orEmpty()
        }
    }

    val presetAvatars = remember {
        listOf(
            "" to Color(0xFF1BC49D),  // Default teal
            "#FF5722" to Color(0xFFFF5722),  // Deep Orange
            "#2196F3" to Color(0xFF2196F3),  // Blue
            "#9C27B0" to Color(0xFF9C27B0),  // Purple
            "#FF9800" to Color(0xFFFF9800),  // Orange
            "#00BCD4" to Color(0xFF00BCD4),  // Cyan
            "#E91E63" to Color(0xFFE91E63),  // Pink
            "#4CAF50" to Color(0xFF4CAF50),  // Green
        )
    }

    // Avatar picker dialog
    if (showAvatarPicker) {
        AlertDialog(
            onDismissRequest = { showAvatarPicker = false },
            title = { Text("选择头像") },
            containerColor = TopBarBackground,
            text = {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        presetAvatars.take(4).forEach { (hex, color) ->
                            val initial = user?.nickname?.take(1) ?: "我"
                            Box(
                                modifier = Modifier
                                    .size(56.dp)
                                    .clip(CircleShape)
                                    .background(color.copy(alpha = 0.2f))
                                    .clickable {
                                        editAvatar = hex
                                        showAvatarPicker = false
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(initial, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = color)
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        presetAvatars.drop(4).forEach { (hex, color) ->
                            val initial = user?.nickname?.take(1) ?: "我"
                            Box(
                                modifier = Modifier
                                    .size(56.dp)
                                    .clip(CircleShape)
                                    .background(color.copy(alpha = 0.2f))
                                    .clickable {
                                        editAvatar = hex
                                        showAvatarPicker = false
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(initial, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = color)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Row {
                    TextButton(onClick = {
                        showAvatarPicker = false
                        avatarPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    }) {
                        Text("相册")
                    }
                    TextButton(onClick = { editAvatar = ""; showAvatarPicker = false }) {
                        Text("默认")
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { showAvatarPicker = false }) {
                    Text("取消")
                }
            }
        )
    }

    if (showLogoutConfirm) {
        AlertDialog(
            onDismissRequest = { showLogoutConfirm = false },
            title = { Text("退出登录") },
            text = { Text("确认退出当前账号？") },
            containerColor = TopBarBackground,
            dismissButton = {
                TextButton(onClick = { showLogoutConfirm = false }) {
                    Text("取消")
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    showLogoutConfirm = false
                    onLogout?.invoke()
                }) {
                    Text("确认", color = UnreadRed)
                }
            }
        )
    }

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = { Text(if (isSelf) "我的" else "详细资料") },
                    navigationIcon = {
                        if (onBack != null) {
                            IconButton(onClick = onBack) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                            }
                        }
                    },
                    actions = {
                        if (isSelf && onLogout != null) {
                            TextButton(onClick = {
                                if (isEditing) {
                                    scope.launch {
                                        user?.let { it ->
                                            val normalizedNickname = editNickname.trim().ifBlank { it.nickname.ifBlank { it.userId } }
                                            val isPickedImage = pickedAvatarUri != null
                                            val isColor = editAvatar.startsWith("#")

                                            val (avatarValue, avatarUrlValue, avatarVersionValue) = if (isPickedImage) {
                                                val token = app.tokenManager.getToken() ?: ""
                                                try {
                                                    val bytes = withContext(Dispatchers.IO) {
                                                        pickedAvatarUri?.let { uri ->
                                                            val input = app.applicationContext.contentResolver.openInputStream(uri)
                                                            input?.readBytes()
                                                        }
                                                    } ?: run {
                                                        app.showToast("读取头像图片失败")
                                                        return@launch
                                                    }
                                                    val uploaded = withContext(Dispatchers.IO) {
                                                        AuthApiClient().uploadImage(bytes, token)
                                                    }
                                                    val newVersion = it.avatarVersion + 1
                                                    Triple(it.avatar, uploaded.imageUrl, newVersion)
                                                } catch (e: Exception) {
                                                    app.showToast("头像上传失败: ${e.message}")
                                                    return@launch
                                                }
                                            } else if (isColor) {
                                                Triple(editAvatar, "", 0)
                                            } else {
                                                Triple(editAvatar, it.avatarUrl, it.avatarVersion)
                                            }

                                            val updated = it.copy(
                                                nickname = normalizedNickname,
                                                signature = editSignature.trim(),
                                                region = editRegion.trim(),
                                                avatar = avatarValue,
                                                avatarUrl = avatarUrlValue,
                                                avatarVersion = avatarVersionValue
                                            )
                                            if (updated != it) {
                                                app.userRepository.saveUser(updated)
                                                app.userSession.currentNickname = normalizedNickname
                                                user = updated
                                                AppEvents.notifyUserChanged(updated.userId)
                                                app.imClient.updateProfile(
                                                    nickname = normalizedNickname,
                                                    avatar = if (isColor) avatarValue else null,
                                                    avatarUrl = if (isPickedImage) avatarUrlValue else null,
                                                    avatarVersion = if (isPickedImage) avatarVersionValue else null,
                                                    signature = updated.signature,
                                                    region = updated.region
                                                )
                                            }
                                        }
                                        isEditing = false
                                        pickedAvatarUri = null
                                    }
                                } else {
                                    editNickname = user?.nickname ?: ""
                                    editSignature = user?.signature ?: ""
                                    editRegion = user?.region ?: ""
                                    editAvatar = user?.avatar ?: ""
                                    isEditing = true
                                }
                            }) {
                                Text(if (isEditing) "保存" else "编辑", color = WeChatGreen)
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = TopBarBackground)
                )
                HorizontalDivider(thickness = 0.5.dp, color = DividerColor)
            }
        }
    ) { padding ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(padding)
                .background(WeChatWhite)
                .then(if (isEditing) Modifier.imePadding() else Modifier)
                .pointerInput(isEditing) {
                    if (isEditing) {
                        detectTapGestures(onTap = { clearKeyboardFocus() })
                    }
                }
                .verticalScroll(scrollState),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(20.dp))

            // Avatar
            val pickedBitmap = remember(pickedAvatarUri) {
                pickedAvatarUri?.let { uri ->
                    try {
                        val bytes = app.applicationContext.contentResolver.openInputStream(uri)?.readBytes()
                        bytes?.let { BitmapFactory.decodeByteArray(it, 0, it.size) }
                    } catch (_: Exception) { null }
                }
            }
            var urlAvatarBitmap by remember { mutableStateOf<Bitmap?>(null) }
            LaunchedEffect(user?.avatarUrl, user?.avatarVersion) {
                val u = user
                if (!isEditing && u?.avatarUrl?.isNotBlank() == true) {
                    val result = com.lightchat.ui.components.AvatarCacheLoader.loadAvatar(
                        context = app.applicationContext,
                        userId = u.userId,
                        avatarUrl = u.avatarUrl,
                        avatarVersion = u.avatarVersion,
                        avatarFallback = "",
                        allowNetwork = true
                    )
                    urlAvatarBitmap = result.bitmap
                } else {
                    urlAvatarBitmap = null
                }
            }
            var lightchatIconBitmap by remember { mutableStateOf<Bitmap?>(null) }
            LaunchedEffect(user?.avatar) {
                if (user?.avatar == "lightchat://app-icon") {
                    lightchatIconBitmap = withContext(Dispatchers.IO) {
                        try {
                            val drawable = app.applicationContext.packageManager
                                .getApplicationIcon(app.applicationContext.packageName)
                            (drawable as? android.graphics.drawable.BitmapDrawable)?.bitmap
                        } catch (_: Exception) { null }
                    }
                } else {
                    lightchatIconBitmap = null
                }
            }
            val avatarBitmap = pickedBitmap ?: urlAvatarBitmap ?: lightchatIconBitmap
            val isColorAvatar = editAvatar.isNotEmpty() && avatarBitmap == null && editAvatar != "uri://picked"
            val avatarColor = if (isColorAvatar) {
                try { Color(android.graphics.Color.parseColor(editAvatar)) } catch (_: Exception) { WeChatGreen }
            } else if (editAvatar.isEmpty() && avatarBitmap == null) WeChatGreen else Color.Unspecified
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .clip(CircleShape)
                    .background(avatarColor.copy(alpha = 0.2f))
                    .then(
                        if (isSelf && isEditing) Modifier.clickable { showAvatarPicker = true }
                        else Modifier
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (avatarBitmap != null) {
                    Image(
                        bitmap = avatarBitmap.asImageBitmap(),
                        contentDescription = if (isSelf && isEditing) "更换头像" else "头像",
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Icon(
                        Icons.Default.Person,
                        contentDescription = if (isSelf && isEditing) "更换头像" else null,
                        modifier = Modifier.size(40.dp),
                        tint = avatarColor
                    )
                }
            }
            if (isSelf && isEditing) {
                Spacer(modifier = Modifier.height(4.dp))
                Text("点击更换头像", fontSize = 12.sp, color = TextSecondary)
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (isEditing) {
                // Edit mode
                OutlinedTextField(
                    value = editNickname,
                    onValueChange = { editNickname = it },
                    label = { Text("昵称") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 32.dp)
                        .bringIntoViewRequester(nicknameRequester)
                        .onFocusChanged { state ->
                            if (state.isFocused) {
                                scope.launch {
                                    delay(120)
                                    nicknameRequester.bringIntoView()
                                }
                            }
                        },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Text,
                        imeAction = ImeAction.Next
                    )
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = editSignature,
                    onValueChange = { editSignature = it },
                    label = { Text("个性签名") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 32.dp)
                        .bringIntoViewRequester(signatureRequester)
                        .onFocusChanged { state ->
                            if (state.isFocused) {
                                scope.launch {
                                    delay(120)
                                    signatureRequester.bringIntoView()
                                }
                            }
                        },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Text,
                        imeAction = ImeAction.Next
                    )
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = editRegion,
                    onValueChange = { editRegion = it },
                    label = { Text("所在地区") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 32.dp)
                        .bringIntoViewRequester(regionRequester)
                        .onFocusChanged { state ->
                            if (state.isFocused) {
                                scope.launch {
                                    delay(120)
                                    regionRequester.bringIntoView()
                                }
                            }
                        },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Text,
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(
                        onDone = { clearKeyboardFocus() }
                    )
                )
            } else {
                // Display mode
                Text(
                    text = displayNickname,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "ID: ${user?.userId ?: ""}",
                    fontSize = 14.sp,
                    color = TextSecondary
                )
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Info rows
            if (!isEditing) {
                ProfileInfoRow("昵称", displayNickname)
                if (isSelf || isFriend) {
                    ProfileDivider()
                    ProfileInfoRow("ID", user?.userId ?: "")
                    ProfileDivider()
                    ProfileInfoRow("个性签名", user?.signature?.ifEmpty { "未设置" } ?: "未设置")
                    ProfileDivider()
                    ProfileInfoRow("所在地区", user?.region?.ifEmpty { "未设置" } ?: "未设置")
                }
            }

            if (isSelf && onLogout != null && !isEditing) {
                Spacer(modifier = Modifier.height(48.dp))
                Button(
                    onClick = { showLogoutConfirm = true },
                    colors = ButtonDefaults.buttonColors(containerColor = UnreadRed),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 32.dp)
                        .height(48.dp)
                ) {
                    Text("退出登录", fontSize = 16.sp)
                }
            }

            if (!isSelf && user != null && userId == LIGHTCHAT_ASSISTANT_ID) {
                Spacer(modifier = Modifier.height(48.dp))
                Button(
                    onClick = { onChat?.invoke() },
                    colors = ButtonDefaults.buttonColors(containerColor = WeChatGreen),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 32.dp)
                        .height(48.dp)
                ) {
                    Text("发消息", fontSize = 16.sp)
                }
            }

            if (!isSelf && user != null && userId != LIGHTCHAT_ASSISTANT_ID) {
                Spacer(modifier = Modifier.height(48.dp))
                if (isFriend) {
                    Button(
                        onClick = { onChat?.invoke() },
                        colors = ButtonDefaults.buttonColors(containerColor = WeChatGreen),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 32.dp)
                            .height(48.dp)
                    ) {
                        Text("发消息", fontSize = 16.sp)
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedButton(
                        onClick = {
                            user?.userId?.let { onRecommendCard?.invoke(it) }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 32.dp)
                            .height(48.dp)
                    ) {
                        Text("推荐给朋友", fontSize = 16.sp, color = WeChatGreen)
                    }
                } else {
                    var requestSent by remember { mutableStateOf(false) }
                    Button(
                        onClick = {
                            val targetId = userId ?: return@Button
                            app.imClient.sendFriendRequest(targetId, "")
                            requestSent = true
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = WeChatGreen),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 32.dp)
                            .height(48.dp),
                        enabled = !requestSent
                    ) {
                        Text(if (requestSent) "已发送申请" else "添加到通讯录", fontSize = 16.sp)
                    }
                }
            }

            Spacer(modifier = Modifier.height(96.dp))
        }
    }
}

@Composable
private fun ProfileInfoRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            label,
            fontSize = 15.sp,
            color = TextSecondary,
            modifier = Modifier.width(76.dp),
            maxLines = 1
        )
        Spacer(modifier = Modifier.width(32.dp))
        Text(
            value,
            fontSize = 15.sp,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun ProfileDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(start = 108.dp),
        color = DividerColor,
        thickness = 0.5.dp
    )
}
