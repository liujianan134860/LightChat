package com.lightchat.ui.chat

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lightchat.ui.theme.DividerColor
import com.lightchat.ui.theme.TextSecondary
import com.lightchat.ui.theme.TopBarBackground
import com.lightchat.ui.theme.WeChatGreen

@Composable
internal fun ChatMentionDialog(
    candidates: List<Pair<String, String>>,
    selectedIds: Set<String>,
    inputText: String,
    onSelectedIdsChange: (Set<String>) -> Unit,
    onClearFocus: () -> Unit,
    onConfirm: (newInputText: String, selectedIds: Set<String>) -> Unit,
    onDismiss: () -> Unit
) {
    val allMentionIds = remember(candidates) { candidates.map { it.first }.toSet() }
    val isAllMentionSelected = allMentionIds.isNotEmpty() && selectedIds.containsAll(allMentionIds)
    var searchQuery by remember { mutableStateOf("") }
    val filteredCandidates = remember(candidates, searchQuery) {
        if (searchQuery.isBlank()) candidates
        else candidates.filter { it.second.contains(searchQuery, ignoreCase = true) }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text("选择提醒的人")
                Text(
                    "已选 ${selectedIds.size} 人",
                    fontSize = 12.sp,
                    color = TextSecondary
                )
            }
        },
        containerColor = TopBarBackground,
        text = {
            Column {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("搜索群成员") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = DividerColor,
                        unfocusedBorderColor = DividerColor
                    ),
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Default.Close, contentDescription = "清除")
                            }
                        }
                    }
                )
                Spacer(modifier = Modifier.height(8.dp))
                if (searchQuery.isBlank()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                onClearFocus()
                                onSelectedIdsChange(if (isAllMentionSelected) emptySet() else allMentionIds)
                            }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = isAllMentionSelected,
                            onCheckedChange = { checked ->
                                onClearFocus()
                                onSelectedIdsChange(if (checked) allMentionIds else emptySet())
                            },
                            colors = CheckboxDefaults.colors(checkedColor = WeChatGreen)
                        )
                        Text("所有人", modifier = Modifier.weight(1f), fontWeight = FontWeight.Medium)
                    }
                    HorizontalDivider(color = DividerColor, thickness = 0.5.dp)
                }
                LazyColumn(modifier = Modifier.heightIn(max = 360.dp)) {
                    if (filteredCandidates.isEmpty()) {
                        item { Text("无匹配成员", color = TextSecondary, modifier = Modifier.padding(vertical = 8.dp)) }
                    } else {
                        items(filteredCandidates.size) { index ->
                            val (userId, displayName) = filteredCandidates[index]
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        onClearFocus()
                                        onSelectedIdsChange(
                                            if (selectedIds.contains(userId)) selectedIds - userId else selectedIds + userId
                                        )
                                    }
                                    .padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = selectedIds.contains(userId),
                                    onCheckedChange = { checked ->
                                        onClearFocus()
                                        onSelectedIdsChange(if (checked) selectedIds + userId else selectedIds - userId)
                                    },
                                    colors = CheckboxDefaults.colors(checkedColor = WeChatGreen)
                                )
                                Text(displayName, modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val selectedNames = candidates.filter { selectedIds.contains(it.first) }
                    val suffix = if (isAllMentionSelected) {
                        "@所有人"
                    } else {
                        selectedNames.joinToString(" ") { "@${it.second}" }
                    }
                    val base = inputText.removeSuffix("@")
                    onConfirm((base + suffix + " ").trimStart(), selectedIds)
                },
                enabled = selectedIds.isNotEmpty()
            ) {
                Text("确定")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}
