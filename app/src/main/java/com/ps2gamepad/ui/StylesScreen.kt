package com.ps2gamepad.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
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
import com.ps2gamepad.model.ControllerSkin
import com.ps2gamepad.ui.controller.ControllerPreview
import com.ps2gamepad.ui.controller.Skins

/**
 * Premium style picker: a live preview of every style. A style only changes the look; every
 * position sends the same button to the PC, so emulator settings keep working.
 */
@Composable
fun StylesScreen(vm: MainViewModel) {
    val profile by vm.activeProfile.collectAsStateWithLifecycle()
    val current = profile.appearance.skin
    ScreenFrame("Controller styles", onBack = { vm.navigate(Screen.HOME) }) {
        Text(
            "Tap a style to use it. Only the look changes: your PCSX2 / emulator button setup keeps working.",
            color = Palette.TextDim, fontSize = 12.sp,
        )
        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(ControllerSkin.entries) { skin ->
                val selected = skin == current
                val theme = Skins.of(skin)
                Column(
                    Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .background(Brush.verticalGradient(listOf(Palette.SurfaceHigh, Palette.Surface)))
                        .border(if (selected) 2.dp else 1.dp, if (selected) theme.accent else Palette.SurfaceHigh, RoundedCornerShape(16.dp))
                        .clickable { vm.updateAppearance { it.copy(skin = skin) } }
                        .padding(10.dp)
                        .testTag("style_${skin.name}"),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    ControllerPreview(
                        vm.input, profile.copy(appearance = profile.appearance.copy(skin = skin)),
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)),
                    )
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(skin.label, color = Palette.Text, fontWeight = FontWeight.Bold, fontSize = 15.sp, modifier = Modifier.weight(1f))
                        if (selected) {
                            Box(Modifier.background(theme.accent, RoundedCornerShape(50)).padding(horizontal = 8.dp, vertical = 2.dp)) {
                                Text("IN USE", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                    Text(skin.tagline, color = Palette.TextDim, fontSize = 11.sp)
                }
            }
        }
    }
}
