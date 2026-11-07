package com.ps2gamepad.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import com.ps2gamepad.model.ControllerProfile
import com.ps2gamepad.settings.DefaultProfiles
import com.ps2gamepad.settings.ProfileManager
import com.ps2gamepad.ui.controller.ControllerPreview

private val CardTop = Color(0xFF1F2438)
private val CardBottom = Color(0xFF161A28)

/**
 * Shown every time the controller is opened: pick the loadout (per-game setup) to play with,
 * or create a new one (max 5). Each loadout keeps its own layout, sizes, opacity, style, stick
 * and combo buttons.
 */
@Composable
fun LoadoutPickerScreen(vm: MainViewModel) {
    val store by vm.profiles.collectAsStateWithLifecycle()
    val loadouts = ProfileManager.loadouts(store)
    val default = store.profiles.firstOrNull { it.id == DefaultProfiles.STANDARD }
    var toDelete by remember { mutableStateOf<ControllerProfile?>(null) }

    ScreenFrame("Choose a loadout", onBack = { vm.navigate(Screen.HOME) }, actions = {
        Text("${loadouts.size} / ${ProfileManager.MAX_LOADOUTS} loadouts", color = Palette.TextDim, fontSize = 13.sp, modifier = Modifier.testTag("loadoutCount"))
    }) {
        Text(
            "Each loadout remembers its own button layout, sizes, opacity, button style, sticks and combo buttons. Changes you make while playing are saved to the loadout in use.",
            color = Palette.TextDim, fontSize = 12.sp,
        )
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 170.dp), // 2 per row on a phone in portrait, 3+ on wide screens
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (default != null) {
                item(key = "default") {
                    LoadoutCard(vm, default, title = "Default", subtitle = "Standard PS2 layout", lastUsed = store.activeId == default.id, onDelete = null)
                }
            }
            items(loadouts, key = { it.id }) { p ->
                LoadoutCard(vm, p, title = p.name, subtitle = (if (p.placements.isEmpty()) "Default layout" else "Custom layout") +
                    if (p.extraButtons.any { it.isCombo }) " · Combos" else "",
                    lastUsed = store.activeId == p.id, onDelete = { toDelete = p })
            }
            if (ProfileManager.canCreateLoadout(store)) {
                item(key = "new") { NewLoadoutCard(vm) }
            }
        }
    }

    toDelete?.let { p ->
        AlertDialog(
            onDismissRequest = { toDelete = null },
            title = { Text("Delete loadout \"${p.name}\"?") },
            text = { Text("Its layout and settings will be lost.") },
            confirmButton = { TextButton(onClick = { vm.deleteLoadout(p.id); toDelete = null }, modifier = Modifier.testTag("confirmDeleteLoadout")) { Text("Delete") } },
            dismissButton = { TextButton(onClick = { toDelete = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun LoadoutCard(vm: MainViewModel, p: ControllerProfile, title: String, subtitle: String, lastUsed: Boolean, onDelete: (() -> Unit)?) {
    val accent = Color(0xFF22C55E)
    Column(
        Modifier.clip(RoundedCornerShape(18.dp))
            .background(Brush.verticalGradient(listOf(CardTop, CardBottom)))
            .border(if (lastUsed) 2.dp else 1.dp, if (lastUsed) accent else Color(0x33FFFFFF), RoundedCornerShape(18.dp))
            .clickable { vm.playLoadout(p.id) }
            .padding(10.dp)
            .testTag("loadout_${p.id}"),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        ControllerPreview(vm.input, p, Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)))
        if (lastUsed) {
            Box(Modifier.background(accent, RoundedCornerShape(50)).padding(horizontal = 8.dp, vertical = 2.dp)) {
                Text("LAST USED", color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold)
            }
        }
        Text(title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp, maxLines = 1)
        Text(subtitle, color = Palette.TextDim, fontSize = 11.sp, maxLines = 1)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.weight(1f).height(40.dp).clip(RoundedCornerShape(50))
                    .background(Brush.horizontalGradient(listOf(Color(0xFF22C55E), Color(0xFF16A34A))))
                    .clickable { vm.playLoadout(p.id) }
                    .testTag("play_${p.id}"),
                contentAlignment = Alignment.Center,
            ) { Text("Play", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 15.sp) }
            if (onDelete != null) {
                Text(
                    "Delete", color = Color(0xFFFF8A8A), fontSize = 12.sp,
                    modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onDelete).padding(8.dp).testTag("delete_${p.id}"),
                )
            }
        }
    }
}

@Composable
private fun NewLoadoutCard(vm: MainViewModel) {
    var name by remember { mutableStateOf("") }
    Column(
        Modifier.clip(RoundedCornerShape(18.dp))
            .background(Color(0x0FFFFFFF))
            .border(1.dp, Color(0x55FFFFFF), RoundedCornerShape(18.dp))
            .padding(12.dp)
            .testTag("newLoadoutCard"),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("+ New loadout", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
        Text("Starts as a copy of your current setup, then opens Edit so you can arrange the buttons.", color = Palette.TextDim, fontSize = 11.sp)
        OutlinedTextField(
            value = name, onValueChange = { name = it.take(ControllerProfile.MAX_NAME) },
            singleLine = true, label = { Text("Name, e.g. GTA San Andreas") },
            modifier = Modifier.fillMaxWidth().testTag("newLoadoutName"),
        )
        ActionButton("Create & arrange buttons", enabled = name.isNotBlank()) {
            if (vm.createLoadout(name)) name = ""
        }
    }
}
