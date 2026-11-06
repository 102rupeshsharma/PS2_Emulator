package com.ps2gamepad.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ps2gamepad.model.GamingMode

/** "Which kind of game?" Emulator Mode (unchanged controller) or PC Gaming Mode. Remembers the last choice. */
@Composable
fun ModeSelectScreen(vm: MainViewModel) {
    val store by vm.profiles.collectAsStateWithLifecycle()
    ScreenFrame("Choose gaming mode", onBack = { vm.navigate(Screen.HOME) }) {
        Row(Modifier.fillMaxWidth().fillMaxHeight(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            ModeCard(
                Modifier.weight(1f), "Emulator Gaming", "PCSX2 and other emulators",
                listOf("PS2-style gamepad", "Your loadouts and styles", "Gyro right stick"),
                listOf(Color(0xFF4C6FFF), Color(0xFF8E5CFF)), lastUsed = store.lastMode == GamingMode.EMULATOR,
                status = null, tag = "mode_EMULATOR",
            ) { vm.chooseMode(GamingMode.EMULATOR) }
            ModeCard(
                Modifier.weight(1f), "PC Gaming", "Games played with keyboard + mouse",
                listOf("W A S D movement stick", "Gyro mouse aiming + mouse buttons", "Custom buttons per game"),
                listOf(Color(0xFF06B6D4), Color(0xFF22C55E)), lastUsed = store.lastMode == GamingMode.PC,
                status = if (store.pcInputEnabled) "PC input: ON" else "PC input: OFF (one-time setup inside)", tag = "mode_PC",
            ) { vm.chooseMode(GamingMode.PC) }
        }
    }
}

@Composable
private fun ModeCard(
    modifier: Modifier, title: String, subtitle: String, points: List<String>, colors: List<Color>,
    lastUsed: Boolean, status: String?, tag: String, onClick: () -> Unit,
) {
    Column(
        modifier.fillMaxHeight().clip(RoundedCornerShape(24.dp))
            .background(Brush.verticalGradient(listOf(Color(0xFF1F2438), Color(0xFF151927))))
            .border(if (lastUsed) 2.dp else 1.dp, if (lastUsed) colors[0] else Color(0x33FFFFFF), RoundedCornerShape(24.dp))
            .clickable(onClick = onClick).padding(20.dp).testTag(tag),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.height(6.dp).fillMaxWidth(0.25f).background(Brush.horizontalGradient(colors), RoundedCornerShape(50)))
            Box(Modifier.weight(1f))
            if (lastUsed) Text("LAST USED", color = colors[0], fontSize = 11.sp, fontWeight = FontWeight.Bold)
        }
        Text(title, color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Bold)
        Text(subtitle, color = Palette.TextDim, fontSize = 13.sp)
        points.forEach { Text("•  $it", color = Color(0xFFCDD2E0), fontSize = 14.sp) }
        Box(Modifier.weight(1f))
        status?.let { Text(it, color = if (it.contains("ON")) Palette.Good else Palette.Warn, fontSize = 12.sp) }
        Box(
            Modifier.fillMaxWidth().height(52.dp).clip(RoundedCornerShape(50)).background(Brush.horizontalGradient(colors)).clickable(onClick = onClick),
            contentAlignment = Alignment.Center,
        ) { Text("Start", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.SemiBold) }
    }
}
