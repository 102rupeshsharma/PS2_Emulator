package com.ps2gamepad.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ps2gamepad.bluetooth.PhoneNameControl

/**
 * The simple start screen: one status in plain words and the one button needed right now.
 * Everything else (controller settings, profiles, diagnostics) is behind "More".
 */
@Composable
fun SimpleHomeScreen(vm: MainViewModel, actions: AppActions) {
    val bt by vm.bluetoothState.collectAsStateWithLifecycle()
    val perm by vm.permission.collectAsStateWithLifecycle()
    val hid by vm.hidState.collectAsStateWithLifecycle()
    val name by vm.phoneName.collectAsStateWithLifecycle()
    vm.bondedHosts.collectAsStateWithLifecycle().value // recompose when the paired list changes
    vm.profiles.collectAsStateWithLifecycle().value     // ...or the last connection changes

    val phoneName = name ?: PhoneNameControl.GAMEPAD_NAME
    val st = simpleStatus(bt, perm, hid, vm.knownPc, phoneName)
    BackHandler { actions.exit() }

    Box(
        Modifier.fillMaxSize().background(Palette.Background).windowInsetsPadding(WindowInsets.displayCutout).padding(16.dp).testTag("home"),
    ) {
        Column(
            Modifier.align(Alignment.Center).widthIn(max = 620.dp).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(Modifier.size(22.dp).background(st.severity.color, CircleShape))
                Text(st.title, color = Palette.Text, fontSize = 30.sp, fontWeight = FontWeight.Bold, modifier = Modifier.testTag("homeTitle"))
            }
            Text(st.help, color = Palette.TextDim, fontSize = 17.sp, textAlign = TextAlign.Center, modifier = Modifier.testTag("homeHelp"))

            if (st.action != SimpleAction.NONE_WAIT) {
                Button(
                    onClick = {
                        when (st.action) {
                            SimpleAction.ALLOW_PERMISSION -> actions.requestPermissions()
                            SimpleAction.TURN_ON_BLUETOOTH -> actions.enableBluetooth()
                            SimpleAction.RETRY_SETUP -> vm.register()
                            SimpleAction.MAKE_VISIBLE -> { vm.applyPhoneName(); actions.makeDiscoverable() }
                            SimpleAction.CONNECT_PC -> vm.connectKnownPc()
                            SimpleAction.OPEN_CONTROLLER -> vm.navigate(Screen.CONTROLLER)
                            SimpleAction.NONE_WAIT -> Unit
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = if (st.action == SimpleAction.OPEN_CONTROLLER) Palette.Good else Palette.Info),
                    modifier = Modifier.height(64.dp).widthIn(min = 320.dp).testTag("homeAction"),
                ) { Text(st.actionLabel, fontSize = 20.sp, fontWeight = FontWeight.SemiBold) }
            }
            // Pairing a new laptop is always possible, also when one is already known.
            if (st.action == SimpleAction.CONNECT_PC) {
                OutlinedButton(onClick = { vm.applyPhoneName(); actions.makeDiscoverable() }, modifier = Modifier.testTag("homePairNew")) {
                    Text("Pair a laptop again / a new laptop", fontSize = 14.sp)
                }
            }

            Text(
                "On the PC this phone is called:  \"$phoneName\"",
                color = Palette.Text, fontSize = 15.sp,
                modifier = Modifier.background(Palette.Surface, RoundedCornerShape(8.dp)).padding(horizontal = 12.dp, vertical = 6.dp).testTag("homeName"),
            )
        }

        Row(Modifier.align(Alignment.BottomEnd), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { vm.navigate(Screen.LOGS) }, modifier = Modifier.testTag("homeLogs")) { Text("Logs (testing)") }
            OutlinedButton(onClick = { vm.navigate(Screen.DASHBOARD) }, modifier = Modifier.testTag("homeMore")) { Text("More") }
        }
    }
}
