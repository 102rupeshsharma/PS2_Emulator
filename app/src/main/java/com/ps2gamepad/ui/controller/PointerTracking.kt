package com.ps2gamepad.ui.controller

import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.changedToDownIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput

/** A finger owned by one control: where it went down and where it is now (local pixels). */
data class TrackedPointer(val id: Long, val down: Offset, val position: Offset)

/**
 * Per-control multi-touch tracking.
 *
 * Compose hit-tests every pointer when it goes down and then delivers that pointer's events
 * only to the nodes it hit, even after it moves outside them. So each control sees only its own
 * fingers, and several controls can be held at once.
 *
 * [onChange] gets the full list of this control's fingers whenever one goes down, moves or lifts.
 * It is also called with an empty list when:
 *  - the system cancels the gesture (Compose turns cancellation into "up" events);
 *  - the control leaves the composition or [resetKey] changes (input manager reset). After a reset,
 *    fingers that are still down are ignored until lifted, so a reset can never be undone by a
 *    stale finger.
 */
@Composable
fun Modifier.trackPointers(resetKey: Any?, onChange: (pointers: List<TrackedPointer>, eventUptimeMs: Long) -> Unit): Modifier {
    val callback by rememberUpdatedState(onChange)
    return this.pointerInput(resetKey) {
        val active = LinkedHashMap<PointerId, TrackedPointer>()
        try {
            awaitPointerEventScope {
                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Main)
                    var changed = false
                    var time = 0L
                    for (c in event.changes) {
                        time = maxOf(time, c.uptimeMillis)
                        when {
                            c.changedToDownIgnoreConsumed() -> {
                                active[c.id] = TrackedPointer(c.id.value, c.position, c.position)
                                changed = true
                                c.consume()
                            }
                            !c.pressed -> if (active.remove(c.id) != null) { changed = true; c.consume() }
                            else -> {
                                val p = active[c.id] ?: continue // not ours, or down before a reset
                                if (p.position != c.position) {
                                    active[c.id] = p.copy(position = c.position)
                                    changed = true
                                }
                                c.consume()
                            }
                        }
                    }
                    if (changed) callback(active.values.toList(), time)
                }
            }
        } finally {
            if (active.isNotEmpty()) {
                active.clear()
                callback(emptyList(), android.os.SystemClock.uptimeMillis())
            }
        }
    }
}
