package com.lightchat.ui.login

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.lightchat.ui.theme.WeChatGreen
import com.lightchat.ui.theme.WeChatWhite
import com.lightchat.viewmodel.LoginViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(
    onLoginSuccess: () -> Unit,
    viewModel: LoginViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(uiState.isLoggedIn) {
        if (uiState.isLoggedIn && !uiState.isCheckingAuth) {
            onLoginSuccess()
        }
    }

    // Show splash while checking auth status
    if (uiState.isCheckingAuth) {
        Box(
            modifier = Modifier.fillMaxSize().background(WeChatWhite),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator(color = WeChatGreen, modifier = Modifier.size(32.dp))
        }
        return
    }

    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(WeChatWhite),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(120.dp))

            Text(
                text = "LightChat",
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
                color = WeChatGreen
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = if (uiState.isRegisterMode) "创建新账号" else "随时随地，保持联系",
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
            )

            Spacer(modifier = Modifier.height(48.dp))

            // Nickname field (only in register mode)
            if (uiState.isRegisterMode) {
                OutlinedTextField(
                    value = uiState.registerNickname,
                    onValueChange = viewModel::onNicknameChange,
                    label = { Text("昵称") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 32.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = WeChatGreen
                ),
                singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Text,
                        imeAction = ImeAction.Next
                    )
                )
                Spacer(modifier = Modifier.height(16.dp))
            }

            OutlinedTextField(
                value = uiState.username,
                onValueChange = viewModel::onUsernameChange,
                label = { Text("账户") },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 32.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = WeChatGreen
                ),
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Text,
                    imeAction = ImeAction.Next
                )
            )

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = uiState.password,
                onValueChange = viewModel::onPasswordChange,
                label = { Text("密码") },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 32.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = WeChatGreen
                ),
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    imeAction = ImeAction.Done
                )
            )

            if (uiState.errorMessage != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = uiState.errorMessage!!,
                    color = MaterialTheme.colorScheme.error,
                    fontSize = 13.sp
                )
            }

            Spacer(modifier = Modifier.height(32.dp))

            Button(
                onClick = {
                    if (uiState.isRegisterMode) viewModel.register() else viewModel.login()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 32.dp)
                    .height(50.dp),
                colors = ButtonDefaults.buttonColors(containerColor = WeChatGreen),
                enabled = !uiState.isLoading
            ) {
                if (uiState.isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = WeChatWhite,
                        strokeWidth = 2.dp
                    )
                } else {
                    Text(
                        if (uiState.isRegisterMode) "注册" else "登录",
                        fontSize = 16.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            TextButton(onClick = { viewModel.toggleRegisterMode() }) {
                Text(
                    if (uiState.isRegisterMode) "已有账号？去登录" else "还没有账号？注册",
                    color = WeChatGreen
                )
            }
        }
    }
}
