package com.lightchat.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.SentimentSatisfiedAlt
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lightchat.ui.theme.BottomBarBackground
import com.lightchat.ui.theme.ChatAlbumIcon
import com.lightchat.ui.theme.ChatIconDark
import com.lightchat.ui.theme.InputStrokeLight
import com.lightchat.ui.theme.PanelDividerLight
import com.lightchat.ui.theme.TextSecondary
import com.lightchat.ui.theme.TopBarBackground
import com.lightchat.ui.theme.WeChatGreen
import com.lightchat.ui.theme.WeChatWhite

@Composable
fun ChatInputBar(
    modifier: Modifier = Modifier,
    visible: Boolean,
    inputText: String,
    inputFocusRequester: FocusRequester,
    onInputChange: (String) -> Unit,
    onInputFocused: () -> Unit,
    onEmojiClick: () -> Unit,
    onMoreClick: () -> Unit,
    onSendClick: () -> Unit
) {
    if (!visible) return

    var inputValue by remember {
        mutableStateOf(
            TextFieldValue(
                text = inputText,
                selection = TextRange(inputText.length)
            )
        )
    }

    LaunchedEffect(inputText) {
        if (inputText != inputValue.text) {
            inputValue = TextFieldValue(
                text = inputText,
                selection = TextRange(inputText.length)
            )
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .height(InputBarHeight)
            .background(BottomBarBackground)
    ) {
        HorizontalDivider(
            thickness = 0.5.dp,
            color = InputStrokeLight
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(
                    horizontal = InputBarHorizontalPadding,
                    vertical = InputBarVerticalPadding
                ),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val inputContainerInteractionSource = remember { MutableInteractionSource() }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(InputFieldHeight)
                    .clip(RoundedCornerShape(InputFieldCornerRadius))
                    .background(WeChatWhite)
                    .clickable(
                        interactionSource = inputContainerInteractionSource,
                        indication = null
                    ) {
                        inputFocusRequester.requestFocus()
                        onInputFocused()
                    }
                    .padding(
                        start = InputFieldHorizontalPaddingStart,
                        end = InputFieldHorizontalPaddingEnd
                    ),
                contentAlignment = Alignment.CenterStart
            ) {
                CompositionLocalProvider(
                    LocalTextSelectionColors provides TextSelectionColors(
                        handleColor = Color.Transparent,
                        backgroundColor = Color.Transparent
                    )
                ) {
                    BasicTextField(
                        value = inputValue,
                        onValueChange = { value ->
                            inputValue = value
                            onInputChange(value.text)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(inputFocusRequester)
                            .onFocusChanged { state ->
                                if (state.isFocused) onInputFocused()
                            },
                        textStyle = TextStyle(
                            fontSize = InputTextFontSize,
                            lineHeight = InputTextLineHeight,
                            color = Color.Black
                        ),
                        cursorBrush = SolidColor(WeChatGreen),
                        maxLines = 1,
                        singleLine = true,
                        decorationBox = { innerTextField ->
                            Box(
                                modifier = Modifier.fillMaxWidth(),
                                contentAlignment = Alignment.CenterStart
                            ) {
                                if (inputValue.text.isEmpty()) {
                                    Text(
                                        text = "输入消息…",
                                        fontSize = InputTextFontSize,
                                        lineHeight = InputTextLineHeight,
                                        color = TextSecondary
                                    )
                                }

                                innerTextField()
                            }
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.width(6.dp))

            CompactIconButton(
                onClick = onEmojiClick
            ) {
                Icon(
                    imageVector = Icons.Default.SentimentSatisfiedAlt,
                    contentDescription = "表情",
                    tint = ChatIconDark,
                    modifier = Modifier.size(24.dp)
                )
            }

            Spacer(modifier = Modifier.width(4.dp))

            if (inputText.isBlank()) {
                CompactIconButton(
                    onClick = onMoreClick
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "更多",
                        tint = ChatIconDark,
                        modifier = Modifier.size(26.dp)
                    )
                }
            } else {
                Button(
                    onClick = onSendClick,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = WeChatGreen
                    ),
                    contentPadding = PaddingValues(
                        horizontal = 12.dp,
                        vertical = 0.dp
                    ),
                    modifier = Modifier.height(SendButtonHeight),
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text(
                        text = "发送",
                        color = WeChatWhite,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
private fun CompactIconButton(
    onClick: () -> Unit,
    content: @Composable BoxScope.() -> Unit
) {
    Box(
        modifier = Modifier
            .size(InputIconButtonSize)
            .clip(CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
        content = content
    )
}

@Composable
fun ChatBottomPanelContent(
    showEmojiPanel: Boolean,
    showMorePanel: Boolean,
    onEmojiSelected: (String) -> Unit,
    onPhotoClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .clipToBounds()
            .background(BottomBarBackground)
    ) {
        HorizontalDivider(
            thickness = 0.5.dp,
            color = PanelDividerColor
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            when {
                showEmojiPanel -> EmojiPanel(onEmojiClick = onEmojiSelected)
                showMorePanel -> MorePanel(onPhotoClick = onPhotoClick)
            }
        }
    }
}

@Composable
private fun EmojiPanel(onEmojiClick: (String) -> Unit) {
    val emojis = listOf(
        "😀", "😁", "😂", "🤣", "😊", "😍", "😘",
        "😎", "😢", "😭", "😡", "👍", "👏", "🙏",
        "💪", "🎉", "❤️", "🤝", "✨", "😏", "😱",
        "😤", "🌙", "⭐", "🤔", "😴", "😅", "😆",
        "🥰", "😋", "🙄", "👌", "😇", "🤩", "😜"
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight()
            .background(BottomBarBackground)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.SpaceEvenly
    ) {
        emojis.chunked(7).forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                row.forEach { emoji ->
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(42.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = emoji,
                            fontSize = 26.sp,
                            modifier = Modifier
                                .clip(CircleShape)
                                .clickable { onEmojiClick(emoji) }
                                .padding(4.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MorePanel(onPhotoClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight()
            .background(BottomBarBackground)
            .padding(horizontal = 24.dp, vertical = 20.dp)
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(WeChatWhite)
                    .clickable(onClick = onPhotoClick),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Image,
                    contentDescription = "照片",
                    tint = ChatAlbumIcon,
                    modifier = Modifier.size(30.dp)
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "照片",
                fontSize = 12.sp,
                color = TextSecondary
            )
        }
    }
}

@Preview(
    name = "ChatInputBar 预览 - 空输入",
    showBackground = true,
    widthDp = 390,
    heightDp = 50
)
@Composable
fun ChatInputBarPreview() {
    val focusRequester = remember { FocusRequester() }

    ChatInputBar(
        visible = true,
        inputText = "",
        inputFocusRequester = focusRequester,
        onInputChange = {},
        onInputFocused = {},
        onEmojiClick = {},
        onMoreClick = {},
        onSendClick = {}
    )
}

@Preview(
    name = "ChatInputBar 预览 - 有输入内容",
    showBackground = true,
    widthDp = 390,
    heightDp = 50
)
@Composable
fun ChatInputBarWithTextPreview() {
    val focusRequester = remember { FocusRequester() }

    ChatInputBar(
        visible = true,
        inputText = "你好，测试输入位置",
        inputFocusRequester = focusRequester,
        onInputChange = {},
        onInputFocused = {},
        onEmojiClick = {},
        onMoreClick = {},
        onSendClick = {}
    )
}

@Preview(
    name = "ChatInputBar 预览 - 调试高度",
    showBackground = true,
    widthDp = 390,
    heightDp = 60
)
@Composable
fun ChatInputBarHeightDebugPreview() {
    val focusRequester = remember { FocusRequester() }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(60.dp)
            .background(TopBarBackground),
        contentAlignment = Alignment.Center
    ) {
        ChatInputBar(
            visible = true,
            inputText = "光标应该垂直居中",
            inputFocusRequester = focusRequester,
            onInputChange = {},
            onInputFocused = {},
            onEmojiClick = {},
            onMoreClick = {},
            onSendClick = {}
        )
    }
}

@Preview(
    name = "ChatInputBar 预览 - 表情面板展开",
    showBackground = true,
    widthDp = 390,
    heightDp = 292
)
@Composable
fun ChatInputBarEmojiPanelPreview() {
    var inputText by remember { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(292.dp)
            .background(BottomBarBackground)
    ) {
        ChatInputBar(
            visible = true,
            inputText = inputText,
            inputFocusRequester = focusRequester,
            onInputChange = { inputText = it },
            onInputFocused = {},
            onEmojiClick = {},
            onMoreClick = {},
            onSendClick = {}
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(250.dp)
        ) {
            ChatBottomPanelContent(
                showEmojiPanel = true,
                showMorePanel = false,
                onEmojiSelected = { emoji ->
                    inputText += emoji
                },
                onPhotoClick = {}
            )
        }
    }
}

@Preview(
    name = "ChatInputBar 预览 - 更多面板展开",
    showBackground = true,
    widthDp = 390,
    heightDp = 292
)
@Composable
fun ChatInputBarMorePanelPreview() {
    val focusRequester = remember { FocusRequester() }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(292.dp)
            .background(BottomBarBackground)
    ) {
        ChatInputBar(
            visible = true,
            inputText = "",
            inputFocusRequester = focusRequester,
            onInputChange = {},
            onInputFocused = {},
            onEmojiClick = {},
            onMoreClick = {},
            onSendClick = {}
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(250.dp)
        ) {
            ChatBottomPanelContent(
                showEmojiPanel = false,
                showMorePanel = true,
                onEmojiSelected = {},
                onPhotoClick = {}
            )
        }
    }
}

private val InputBarHeight = 50.dp

// 白色输入框高度
private val InputFieldHeight = 36.dp

// 输入栏整体左右、上下间距
private val InputBarHorizontalPadding = 8.dp
private val InputBarVerticalPadding = 6.dp

// 白色输入框圆角
private val InputFieldCornerRadius = 6.dp

// 白色输入框内部左右间距
private val InputFieldHorizontalPaddingStart = 16.dp
private val InputFieldHorizontalPaddingEnd = 10.dp

// 图标按钮尺寸
private val InputIconButtonSize = 38.dp

// 发送按钮高度
private val SendButtonHeight = 30.dp

// 输入文字和占位文字字号、行高
private val InputTextFontSize = 14.sp
private val InputTextLineHeight = 20.sp

private val PanelDividerColor = PanelDividerLight
