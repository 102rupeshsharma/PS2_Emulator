package com.ps2gamepad.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/**
 * TESTING ONLY: full diagnostic log with Copy (to paste into a chat) and Clear.
 * To remove later: delete this file, Screen.LOGS and the "Logs" button on the home screen.
 */
@Composable
fun LogsScreen(vm: MainViewModel) {
    val lines by vm.log.lines.collectAsStateWithLifecycle()
    val name by vm.phoneName.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val listState = rememberLazyListState()
    LaunchedEffect(lines.size) { if (lines.isNotEmpty()) listState.scrollToItem(lines.lastIndex) }

    ScreenFrame("Logs (testing)", onBack = { vm.navigate(Screen.HOME) }, actions = {
        ActionButton("Copy all") {
            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            cm.setPrimaryClip(ClipData.newPlainText("PS2 Gamepad log", lines.joinToString("\n")))
            vm.log.add("Log copied to clipboard (${lines.size} lines)")
        }
        ActionButton("Clear", outlined = true) { vm.log.clear() }
        ActionButton("Restore phone name", outlined = true) { vm.restorePhoneName() }
    }) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Phone Bluetooth name: \"${name ?: "?"}\"", color = Palette.TextDim, fontSize = 12.sp)
            Text("${lines.size} lines", color = Palette.TextDim, fontSize = 12.sp)
        }
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().background(Color.Black, RoundedCornerShape(8.dp)).padding(8.dp).testTag("logList"),
        ) {
            items(lines) { Text(it, color = Color(0xFFB8F5C8), fontFamily = FontFamily.Monospace, fontSize = 12.sp) }
        }
    }
}

