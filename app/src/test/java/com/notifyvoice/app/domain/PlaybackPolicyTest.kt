package com.notifyvoice.app.domain

import org.junit.Assert.*
import org.junit.Test

class PlaybackPolicyTest {
    @Test fun `sound and vibrate allow voice with otherwise valid conditions`() {
        listOf(RingerMode.SOUND, RingerMode.VIBRATE).forEach {
            assertNull(playbackConditionsReason(it, communicationActive = false, dndAllowsSound = true, mediaVolume = 5))
        }
    }

    @Test fun `silent still blocks voice`() {
        assertEquals(Reason.SILENT, playbackConditionsReason(RingerMode.SILENT, false, true, 5))
    }

    @Test fun `unknown ringer mode fails closed`() {
        assertEquals(Reason.SILENT, playbackConditionsReason(null, false, true, 5))
    }

    @Test fun `vibrate cannot override do not disturb`() {
        assertEquals(Reason.DND, playbackConditionsReason(RingerMode.VIBRATE, false, false, 5))
    }

    @Test fun `vibrate cannot override an active communication`() {
        assertEquals(Reason.CALL, playbackConditionsReason(RingerMode.VIBRATE, true, true, 5))
    }

    @Test fun `muted media is distinct from silent ringer in either allowed mode`() {
        listOf(RingerMode.SOUND, RingerMode.VIBRATE).forEach {
            assertEquals(Reason.MEDIA_MUTED, playbackConditionsReason(it, false, true, 0))
        }
    }

    @Test fun `every combination of call dnd and media mute stays blocked in vibrate`() {
        listOf(false, true).forEach { call ->
            listOf(false, true).forEach { dndAllowed ->
                listOf(0, 5).forEach { volume ->
                    val result = playbackConditionsReason(RingerMode.VIBRATE, call, dndAllowed, volume)
                    if (!call && dndAllowed && volume > 0) assertNull(result) else assertNotNull(result)
                }
            }
        }
    }
}
