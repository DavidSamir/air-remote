// SPDX-License-Identifier: Apache-2.0
// Derived from Fitbit Air Notification Bridge; modified for Air Remote.
package dev.airremote

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class SettingsProtocolTest {

    @Test
    fun encodesPackedHapticsSettingsRequest() {
        assertArrayEquals(
            byteArrayOf(0x0A, 0x01, 0x01),
            SettingsProtocol.encodeHapticsSettingsRequest()
        )
    }

    @Test
    fun parsesHighHapticsIntensity() {
        // SettingsResponse.settings[0].haptics_setting.haptics_intensity = HIGH (3)
        val response = byteArrayOf(0x0A, 0x04, 0x0A, 0x02, 0x08, 0x03)
        assertEquals(3, SettingsProtocol.parseHapticsIntensity(response))
        assertEquals("HIGH", SettingsProtocol.hapticsIntensityName(3))
    }

    @Test
    fun encodesHighHapticsSettingsResponse() {
        assertArrayEquals(
            byteArrayOf(0x0A, 0x04, 0x0A, 0x02, 0x08, 0x03),
            SettingsProtocol.encodeHapticsSettingsResponse(3)
        )
    }

    @Test
    fun ignoresUnknownFieldsBeforeHaptics() {
        val response = byteArrayOf(0x10, 0x07, 0x0A, 0x04, 0x0A, 0x02, 0x08, 0x02)
        assertEquals(2, SettingsProtocol.parseHapticsIntensity(response))
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsTruncatedLengthDelimitedResponse() {
        SettingsProtocol.parseHapticsIntensity(byteArrayOf(0x0A, 0x04, 0x0A))
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsUnsupportedIntensityWrite() {
        SettingsProtocol.encodeHapticsSettingsResponse(99)
    }
}
