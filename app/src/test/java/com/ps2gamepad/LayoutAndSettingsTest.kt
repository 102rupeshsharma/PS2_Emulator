package com.ps2gamepad

import com.ps2gamepad.controller.ControlRect
import com.ps2gamepad.controller.LayoutEngine
import com.ps2gamepad.model.Appearance
import com.ps2gamepad.model.ControlId
import com.ps2gamepad.model.Placement
import com.ps2gamepad.settings.DefaultProfiles
import com.ps2gamepad.settings.InMemoryTextStorage
import com.ps2gamepad.settings.ProfileCodec
import com.ps2gamepad.settings.ProfileManager
import com.ps2gamepad.settings.SettingsRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LayoutEngineTest {
    // landscape dp sizes: 16:9, 18:9, POCO F6 (20:9), 21:9, a 4:3 tablet
    private val screens = listOf(640f to 360f, 760f to 380f, 986f to 443f, 900f to 386f, 1024f to 768f)

    @Test fun defaultLayoutHasNoOverlapsOnAnyScreen() {
        for ((w, h) in screens) {
            val l = LayoutEngine.defaults(w, h)
            assertEquals("$w x $h: ${LayoutEngine.overlaps(l)}", emptyList<Any>(), LayoutEngine.overlaps(l))
            assertEquals(ControlId.entries.toSet(), l.keys)
        }
    }

    @Test fun defaultLayoutIsFullyOnScreen() {
        for ((w, h) in screens) for (r in LayoutEngine.defaults(w, h).values) {
            assertTrue("$w x $h $r", r.left >= -0.01f && r.top >= -0.01f && r.right <= w + 0.01f && r.bottom <= h + 0.01f)
        }
    }

    @Test fun moderateScalesStayOverlapFreeOnThePhone() {
        for (bs in listOf(0.6f, 0.8f, 1.1f)) for (ss in listOf(0.6f, 1f, 1.1f)) {
            val l = LayoutEngine.defaults(986f, 443f, Appearance(buttonScale = bs, stickScale = ss))
            assertTrue("bs=$bs ss=$ss ${LayoutEngine.overlaps(l)}", LayoutEngine.overlaps(l).isEmpty())
        }
    }

    @Test fun hugeScalesAreDetectedAsOverlaps() {
        val l = LayoutEngine.defaults(640f, 360f, Appearance(buttonScale = 1.6f, stickScale = 1.6f))
        assertTrue(LayoutEngine.overlaps(l).isNotEmpty())
    }

    @Test fun scalingChangesSizeAroundSameAnchors() {
        val d = LayoutEngine.defaults(986f, 443f)
        val big = LayoutEngine.defaults(986f, 443f, Appearance(buttonScale = 1.2f, stickScale = 1.3f))
        assertEquals(d.getValue(ControlId.L3).w * 1.2f, big.getValue(ControlId.L3).w, 0.01f)
        assertEquals(d.getValue(ControlId.LEFT_STICK).w * 1.3f, big.getValue(ControlId.LEFT_STICK).w, 0.01f)
    }

    @Test fun placementsAreRelativeAndSurviveScreenChanges() {
        val p = mapOf(ControlId.FACE to Placement(0.7f, 0.5f, 1.2f))
        for ((w, h) in screens) {
            val r = LayoutEngine.resolve(w, h, Appearance(), p).getValue(ControlId.FACE)
            val d = LayoutEngine.defaults(w, h).getValue(ControlId.FACE)
            assertEquals(0.7f * w, r.cx, 0.5f)
            assertEquals(d.w * 1.2f, r.w, 0.01f)
        }
    }

    @Test fun placementRoundTrip() {
        val w = 986f; val h = 443f
        val d = LayoutEngine.defaults(w, h).getValue(ControlId.START)
        val moved = d.copy(cx = 300f, cy = 200f, w = d.w * 1.5f, h = d.h * 1.5f)
        val back = LayoutEngine.resolve(w, h, Appearance(), mapOf(ControlId.START to LayoutEngine.toPlacement(moved, d, w, h))).getValue(ControlId.START)
        assertEquals(moved.cx, back.cx, 0.01f); assertEquals(moved.w, back.w, 0.01f)
    }

    @Test fun offscreenPlacementsAreClampedInside() {
        val r = LayoutEngine.clampInside(ControlRect(-50f, 900f, 100f, 80f), 640f, 360f)
        assertEquals(50f, r.cx); assertEquals(320f, r.cy)
    }

    @Test fun collisionDetection() {
        val l = LayoutEngine.defaults(986f, 443f)
        val onDpad = l.getValue(ControlId.L3).copy(cx = l.getValue(ControlId.DPAD).cx, cy = l.getValue(ControlId.DPAD).cy)
        assertEquals(listOf(ControlId.DPAD), LayoutEngine.collisions(l, ControlId.L3, onDpad))
        assertTrue(LayoutEngine.collisions(l, ControlId.L3, l.getValue(ControlId.L3)).isEmpty())
        // touching edges is not an overlap
        val a = ControlRect(0f, 0f, 10f, 10f); val b = ControlRect(10f, 0f, 10f, 10f)
        assertTrue(!a.overlaps(b))
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsRepositoryTest {
    private val dispatcher = StandardTestDispatcher()

    private fun TestScope.repo(storage: InMemoryTextStorage) = SettingsRepository(storage, this, debounceMs = 400)

    @Test fun firstStartUsesDefaults() = runTest(dispatcher) {
        val r = repo(InMemoryTextStorage()); runCurrent()
        assertEquals(DefaultProfiles.store(), r.store.value)
        assertTrue(r.loaded.value)
    }

    @Test fun changesAreSavedAfterDebounceAndSurviveRestart() = runTest(dispatcher) {
        val disk = InMemoryTextStorage()
        val r = repo(disk); runCurrent()
        repeat(20) { i -> r.update { s -> ProfileManager.updateActive(s) { it.copy(appearance = it.appearance.copy(buttonOpacity = 0.3f + i * 0.01f)) } } }
        advanceTimeBy(200); runCurrent()
        assertEquals("debounced: nothing written yet", 0, disk.writes)
        advanceUntilIdle()
        assertEquals("20 rapid edits -> 1 write", 1, disk.writes)

        // "Restart": a new repository on the same storage
        val r2 = repo(disk); runCurrent()
        assertEquals(0.49f, r2.store.value.active.appearance.buttonOpacity, 1e-4f)
    }

    @Test fun activeProfileAndLayoutPersist() = runTest(dispatcher) {
        val disk = InMemoryTextStorage()
        val r = repo(disk); runCurrent()
        r.update { ProfileManager.switchTo(it, DefaultProfiles.GTA_SA) }
        r.update { s -> ProfileManager.updateActive(s) { it.copy(placements = mapOf(ControlId.R1 to Placement(0.9f, 0.3f, 1.3f))) } }
        r.flush(); runCurrent()
        val r2 = repo(disk); runCurrent()
        assertEquals(DefaultProfiles.GTA_SA, r2.store.value.activeId)
        assertEquals(Placement(0.9f, 0.3f, 1.3f), r2.store.value.active.placements[ControlId.R1])
    }

    @Test fun editsBeforeLoadAreReplayedOnTopOfSavedData() = runTest(dispatcher) {
        val saved = ProfileManager.switchTo(DefaultProfiles.store(), DefaultProfiles.NFS_MW)
        val disk = InMemoryTextStorage(ProfileCodec.encode(saved))
        val r = repo(disk)
        // edit before the load coroutine ran
        r.update { s -> ProfileManager.updateActive(s) { it.copy(appearance = it.appearance.copy(showLabels = false)) } }
        advanceUntilIdle()
        assertEquals("saved active profile not clobbered", DefaultProfiles.NFS_MW, r.store.value.activeId)
        assertEquals(false, r.store.value.active.appearance.showLabels)
        assertEquals(DefaultProfiles.NFS_MW, ProfileCodec.decode(disk.text).activeId)
    }

    @Test fun flushWithoutChangesWritesNothing() = runTest(dispatcher) {
        val disk = InMemoryTextStorage()
        val r = repo(disk); runCurrent()
        r.flush(); advanceUntilIdle()
        assertEquals(0, disk.writes)
        assertNull(disk.text)
    }

    @Test fun flushRequestedDuringLoadIsWrittenRightAfterLoad() = runTest(dispatcher) {
        val disk = InMemoryTextStorage()
        val r = repo(disk)
        r.update { ProfileManager.switchTo(it, DefaultProfiles.GOW) } // before load
        r.flush()                                                   // e.g. onStop before load finished
        runCurrent()                                                // load completes, no debounce wait
        assertEquals(1, disk.writes)
        assertEquals(DefaultProfiles.GOW, ProfileCodec.decode(disk.text).activeId)
    }
}
