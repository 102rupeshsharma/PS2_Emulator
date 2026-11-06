package com.ps2gamepad.ui.controller

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import com.ps2gamepad.controller.ControllerInputManager
import com.ps2gamepad.controller.LayoutEngine
import com.ps2gamepad.model.ControllerProfile

/**
 * A non-interactive, scaled-down copy of the real controller (same layout code), used for the
 * home screen, the style picker and the customise preview. It can never send input.
 */
@Composable
fun ControllerPreview(input: ControllerInputManager, profile: ControllerProfile, modifier: Modifier = Modifier, aspect: Float = 2.2f, dark: Boolean = false) {
    BoxWithConstraints(modifier.aspectRatio(aspect).clipToBounds()) {
        val refW = 900f
        val refH = refW / aspect
        val k = maxWidth.value / refW
        val rects = remember(profile.appearance, profile.placements, k) {
            LayoutEngine.resolve(refW, refH, profile.appearance, profile.placements)
                .mapValues { (_, r) -> r.copy(cx = r.cx * k, cy = r.cy * k, w = r.w * k, h = r.h * k) }
        }
        androidx.compose.runtime.CompositionLocalProvider(LocalNeu provides NeuThemes.of(dark)) {
            ControllerLayout(input, 0, profile, rects, interactive = false)
        }
    }
}
