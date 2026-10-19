// SPDX-License-Identifier: Apache-2.0
// Derived from Fitbit Air Notification Bridge; modified for Air Remote.
package dev.airremote

/** The UI uses SINGLE; the remaining patterns are available to contributors. */
enum class VibrationPattern(val wireName: String, val betweenGroupDelaysMillis: List<Long>) {
    SINGLE("single", emptyList()),
    DOUBLE("double", listOf(650L)),
    TRIPLE("triple", listOf(650L, 650L));

    val groupCount: Int get() = betweenGroupDelaysMillis.size + 1

    companion object {
        fun fromWireName(value: String): VibrationPattern? = values().firstOrNull {
            it.wireName == value.trim().toLowerCase(java.util.Locale.ROOT)
        }
    }
}

data class VibrationCommand(val requestId: String, val pattern: VibrationPattern)
