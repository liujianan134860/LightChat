package com.lightchat.ui.theme

import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color

private val LightColorScheme = lightColorScheme(
    primary = WeChatGreen,
    onPrimary = WeChatWhite,
    secondary = WeChatGreenDark,
    background = WeChatBg,
    surface = WeChatWhite,
    onBackground = TextPrimary,
    onSurface = TextPrimary,
    outline = DividerColor
)

@Composable
fun LightChatTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = LightColorScheme,
        typography = Typography,
    ) {
        CompositionLocalProvider(
            LocalTextSelectionColors provides TextSelectionColors(
                handleColor = Color.Transparent,
                backgroundColor = Color.Transparent
            )
        ) {
            content()
        }
    }
}
