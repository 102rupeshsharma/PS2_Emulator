package com.ps2gamepad

import com.ps2gamepad.model.Appearance
import com.ps2gamepad.model.ControlId
import com.ps2gamepad.model.ControllerProfile
import com.ps2gamepad.model.LastConnection
import com.ps2gamepad.model.Placement
import com.ps2gamepad.model.ProfileStore
import com.ps2gamepad.model.StickSettings
import com.ps2gamepad.settings.DefaultProfiles
import com.ps2gamepad.settings.ProfileCodec
import com.ps2gamepad.settings.ProfileManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProfileManagerTest {
    private var n = 0
    private val ids = { "id${n++}" }
    private val base = DefaultProfiles.store()

    @Test fun builtInsExist() {
        assertEquals(
            listOf("Standard PS2", "God of War", "God of War II", "GTA San Andreas", "Need for Speed Most Wanted", "Custom Profile"),
            base.profiles.map { it.name },
        )
        assertEquals(DefaultProfiles.STANDARD, base.activeId)
        assertTrue(base.profiles.all { it.builtIn })
        assertEquals(base.profiles.size, base.profiles.map { it.id }.toSet().size)
    }

    @Test fun createActivatesNewProfileWithUniqueName() {
        val s1 = ProfileManager.create(base, "Mine", ids)
        assertEquals("Mine", s1.active.name)
        assertFalse(s1.active.builtIn)
        val s2 = ProfileManager.create(s1, "mine", ids)
        assertEquals("mine 2", s2.active.name)
        assertEquals("New profile", ProfileManager.create(base, "  ", ids).active.name)
    }

    @Test fun renameRules() {
        val s = ProfileManager.create(base, "A", ids)
        val id = s.activeId
        assertEquals("B", ProfileManager.rename(s, id, "  B  ").active.name)
        assertEquals("blank is rejected", "A", ProfileManager.rename(s, id, "   ").active.name)
        assertEquals("duplicate is rejected", "A", ProfileManager.rename(s, id, "standard ps2").active.name)
        assertEquals(ControllerProfile.MAX_NAME, ProfileManager.rename(s, id, "x".repeat(100)).active.name.length)
    }

    @Test fun duplicateCopiesSettingsAsCustom() {
        val gow = base.profiles.first { it.id == DefaultProfiles.GOW }
        val s = ProfileManager.duplicate(base, DefaultProfiles.GOW, ids)
        assertEquals("God of War copy", s.active.name)
        assertFalse(s.active.builtIn)
        assertEquals(gow.right, s.active.right)
        assertEquals(gow.appearance, s.active.appearance)
        assertNotEquals(gow.id, s.active.id)
    }

    @Test fun deleteRules() {
        assertEquals("built-ins cannot be deleted", base, ProfileManager.delete(base, DefaultProfiles.GOW))
        val s = ProfileManager.create(base, "Temp", ids)
        val d = ProfileManager.delete(s, s.activeId)
        assertEquals(base.profiles.size, d.profiles.size)
        assertEquals("active falls back to an existing profile", d.profiles.first().id, d.activeId)
        assertEquals(base, ProfileManager.delete(base, "missing"))
    }

    @Test fun switchOnlyToExisting() {
        assertEquals(DefaultProfiles.NFS_MW, ProfileManager.switchTo(base, DefaultProfiles.NFS_MW).activeId)
        assertEquals(base.activeId, ProfileManager.switchTo(base, "nope").activeId)
    }

    @Test fun updateActiveOnlyTouchesActiveAndSanitizes() {
        val s = ProfileManager.updateActive(base) { it.copy(appearance = Appearance(buttonScale = 99f), id = "hacked", builtIn = false) }
        assertEquals(1.6f, s.active.appearance.buttonScale)
        assertEquals("id and built-in flag are protected", DefaultProfiles.STANDARD, s.active.id)
        assertTrue(s.active.builtIn)
        assertEquals(base.profiles.drop(1), s.profiles.drop(1))
    }

    @Test fun restoreDefaultsKeepsName() {
        val changed = ProfileManager.updateActive(ProfileManager.switchTo(base, DefaultProfiles.GOW)) {
            it.copy(right = StickSettings(sensitivity = 0.5f), placements = mapOf(ControlId.L1 to Placement(0.5f, 0.5f)))
        }
        val renamed = ProfileManager.rename(changed, DefaultProfiles.GOW, "My GoW")
        val restored = ProfileManager.restoreDefaults(renamed, DefaultProfiles.GOW)
        val p = restored.profiles.first { it.id == DefaultProfiles.GOW }
        assertEquals(DefaultProfiles.byId(DefaultProfiles.GOW)!!.right, p.right)
        assertTrue(p.placements.isEmpty())
        assertEquals("My GoW", p.name)
    }

    @Test fun codecRoundTripKeepsEverything() {
        var s = ProfileManager.create(base, "Round trip", ids)
        s = ProfileManager.updateActive(s) {
            it.copy(
                appearance = Appearance(buttonScale = 1.2f, buttonOpacity = 0.5f, showLabels = false, haptics = false, background = com.ps2gamepad.model.ControllerBackground.BLACK),
                left = StickSettings(deadZone = 0.2f, sensitivity = 1.4f, maxRange = 0.8f, curve = 1.7f, invertY = true, floating = false, centerOffsetX = 0.1f, centerOffsetY = -0.2f),
                placements = mapOf(ControlId.FACE to Placement(0.8f, 0.4f, 1.2f), ControlId.L3 to Placement(0.1f, 0.9f)),
            )
        }
        s = ProfileManager.recordConnection(s, LastConnection("LAPTOP", 1_700_000_000_000, "AA:BB:CC:DD:EE:FF"))
        assertEquals(s, ProfileCodec.decode(ProfileCodec.encode(s)))
    }

    @Test fun corruptOrMissingDataFallsBackToDefaults() {
        assertEquals(base, ProfileCodec.decode(null))
        assertEquals(base, ProfileCodec.decode("{not json"))
        assertEquals(base, ProfileCodec.decode(""))
    }

    @Test fun unknownFieldsAndOutOfRangeValuesAreRepaired() {
        val json = """{"activeId":"missing","futureField":1,"profiles":[
            {"id":"x","name":"X","appearance":{"buttonScale":50.0,"newThing":true},"left":{"deadZone":-1.0}},
            {"id":"x","name":"duplicate id"}]}"""
        val s = ProfileCodec.decode(json)
        val x = s.profiles.first { it.id == "x" }
        assertEquals(1.6f, x.appearance.buttonScale)
        assertEquals(0f, x.left.deadZone)
        assertEquals(1, s.profiles.count { it.id == "x" })
        assertTrue("built-ins re-added", DefaultProfiles.all.all { b -> s.profiles.any { it.id == b.id } })
        assertEquals(DefaultProfiles.STANDARD, s.activeId)
    }

    @Test fun profilesNeverContainButtonMappings() {
        // Regression guard: a profile has no field that could remap HID buttons.
        val fields = ControllerProfile::class.java.declaredFields
            .filter { !java.lang.reflect.Modifier.isStatic(it.modifiers) }.map { it.name }
        assertEquals(setOf("id", "name", "builtIn", "appearance", "left", "right", "placements"), fields.toSet())
    }

    @Test fun storeActiveFallsBackSafely() {
        val s = ProfileStore("missing", base.profiles)
        assertEquals(base.profiles.first(), s.active)
    }
}
