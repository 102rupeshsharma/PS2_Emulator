package com.ps2gamepad

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import com.ps2gamepad.bluetooth.HidEvent
import com.ps2gamepad.bluetooth.HidReportManager
import com.ps2gamepad.bluetooth.HidStateReducer
import com.ps2gamepad.model.Appearance
import com.ps2gamepad.model.BluetoothState
import com.ps2gamepad.model.ControlId
import com.ps2gamepad.model.ControllerButton
import com.ps2gamepad.model.ControllerSkin
import com.ps2gamepad.model.DiagnosticLog
import com.ps2gamepad.model.HostDevice
import com.ps2gamepad.settings.DefaultProfiles
import com.ps2gamepad.settings.ProfileCodec
import com.ps2gamepad.settings.ProfileManager
import com.ps2gamepad.ui.App
import com.ps2gamepad.ui.AppActions
import com.ps2gamepad.ui.MainViewModel
import com.ps2gamepad.ui.Screen
import com.ps2gamepad.ui.controller.FaceMarks
import com.ps2gamepad.ui.controller.Skins
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

class StylesAndOpacityModelTest {
    @Test fun everyStyleIsComplete() {
        for (s in ControllerSkin.entries) {
            val t = Skins.of(s)
            assertEquals(s.name, 4, t.faceColors.size)
            if (t.faceMarks == FaceMarks.LETTERS) assertEquals(s.name, 4, t.faceLetters.size)
            listOf(t.labelL1, t.labelL2, t.labelR1, t.labelR2, t.labelSelect, t.labelStart, t.labelL3, t.labelR3)
                .forEach { assertTrue("${s.name} label", it.isNotBlank()) }
        }
        assertEquals(5, ControllerSkin.entries.size)
    }

    @Test fun xboxLettersFollowPositionsNotNames() {
        // top, right, bottom, left = Y, B, A, X  ->  HID buttons 4, 3, 2, 1 (same as Triangle, Circle, Cross, Square)
        assertEquals(listOf("Y", "B", "A", "X"), Skins.XBOX.faceLetters)
    }

    @Test fun controlOpacityIsClampedAndFullOpacityIsNotStored() {
        val a = Appearance(controlOpacity = mapOf(ControlId.L1 to 0f, ControlId.FACE to 0.5f, ControlId.START to 1f, ControlId.R1 to Float.NaN)).sanitized()
        assertEquals(Appearance.MIN_CONTROL_OPACITY, a.opacityOf(ControlId.L1))
        assertEquals(0.5f, a.opacityOf(ControlId.FACE))
        assertEquals(1f, a.opacityOf(ControlId.START))
        assertEquals(setOf(ControlId.L1, ControlId.FACE), a.controlOpacity.keys) // 1.0 and NaN->1.0 dropped
    }

    @Test fun styleAndOpacityRoundTripThroughStorage() {
        val s = ProfileManager.updateActive(DefaultProfiles.store()) {
            it.copy(appearance = it.appearance.copy(skin = ControllerSkin.NEON, controlOpacity = mapOf(ControlId.DPAD to 0.4f)))
        }
        val back = ProfileCodec.decode(ProfileCodec.encode(s))
        assertEquals(ControllerSkin.NEON, back.active.appearance.skin)
        assertEquals(0.4f, back.active.appearance.opacityOf(ControlId.DPAD))
    }

    @Test fun unknownStyleFromAFutureVersionFallsBackSafely() {
        val json = ProfileCodec.encode(DefaultProfiles.store()).replace("\"PS2_CLASSIC\"", "\"SOME_FUTURE_STYLE\"")
        assertEquals(ControllerSkin.PS2_CLASSIC, ProfileCodec.decode(json).active.appearance.skin)
    }

    @Test fun oldSavedDataWithoutNewFieldsStillLoads() {
        val old = """{"activeId":"standard","profiles":[{"id":"standard","name":"Standard PS2","builtIn":true,"appearance":{"buttonScale":1.2}}]}"""
        val s = ProfileCodec.decode(old)
        assertEquals(1.2f, s.active.appearance.buttonScale)
        assertEquals(ControllerSkin.PS2_CLASSIC, s.active.appearance.skin)
        assertTrue(s.active.appearance.controlOpacity.isEmpty())
    }
}

/** Real UI + MOCK Bluetooth: styles and opacity never change what is sent to the PC. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w891dp-h411dp-land")
class StylesUiTest {
    @get:Rule val rule = createComposeRule()
    private val hid = FakeHid()
    private val laptop = HostDevice("11:22", "RUPESH")
    private lateinit var vm: MainViewModel

    @Before fun setUp() {
        vm = MainViewModel(hid, MutableStateFlow(BluetoothState.ENABLED), { emptyList() }, DiagnosticLog(), emptyList(), { 0L })
        rule.setContent { App(vm, AppActions({}, {}, {}, {})) }
        rule.runOnIdle {
            vm.onPermissionsChanged(true); vm.register()
            hid.emit(HidEvent.AppStatusChanged(true)); hid.emit(HidEvent.ConnectionChanged(laptop, HidStateReducer.STATE_CONNECTED))
        }
    }

    private fun tapFaceBottomAndShoulderIn(skin: ControllerSkin) {
        rule.runOnIdle { vm.updateAppearance { it.copy(skin = skin, controlOpacity = mapOf(ControlId.FACE to 0.3f)) }; vm.navigate(Screen.CONTROLLER) }
        rule.waitForIdle()
        val root = rule.onNodeWithTag("controller")
        val r = root.fetchSemanticsNode().boundsInRoot
        val face = rule.onNodeWithTag("face", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val l1 = rule.onNodeWithTag("btn_L1", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        root.performTouchInput {
            down(0, Offset(face.left + face.width * 0.5f - r.left, face.top + face.height * 0.82f - r.top))
            down(1, Offset(l1.center.x - r.left, l1.center.y - r.top))
        }
        rule.waitForIdle()
        assertEquals(skin.name, ControllerButton.CROSS.mask or ControllerButton.L1.mask, HidReportManager.decode(hid.sent.last()).buttons)
        root.performTouchInput { up(0); up(1) }
        rule.waitForIdle()
        assertEquals(0, HidReportManager.decode(hid.sent.last()).buttons)
    }

    @Test fun everyStyleSendsTheSameHidButtons() {
        for (s in ControllerSkin.entries) tapFaceBottomAndShoulderIn(s)
    }

    @Test fun perControlOpacitySliderInCustomize() {
        rule.runOnIdle { vm.navigate(Screen.CUSTOMIZE) }
        rule.onNodeWithTag("op_DPAD").performScrollTo().performClick()
        rule.onNodeWithTag("s_controlOpacity").performScrollTo().performSemanticsAction(SemanticsActions.SetProgress) { it(0.35f) }
        rule.waitForIdle()
        rule.runOnIdle {
            assertEquals(0.35f, vm.activeProfile.value.appearance.opacityOf(ControlId.DPAD), 0.01f)
            assertEquals(1f, vm.activeProfile.value.appearance.opacityOf(ControlId.FACE))
        }
    }
}
