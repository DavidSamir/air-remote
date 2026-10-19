// SPDX-License-Identifier: Apache-2.0
// Derived from Fitbit Air Notification Bridge; modified for Air Remote.
package dev.airremote

import android.bluetooth.BluetoothDevice
import android.content.Context

/** Device selection and the last command result stay in this app's private storage. */
object DeviceStore {
    private fun preferences(context: Context) =
        context.getSharedPreferences("air_remote", Context.MODE_PRIVATE)

    fun saveDevice(context: Context, device: BluetoothDevice) {
        preferences(context).edit().putString("address", device.address).apply()
    }

    fun loadAddress(context: Context): String? = preferences(context).getString("address", null)

    fun saveResult(context: Context, result: String) {
        preferences(context).edit().putString("last_result", result).apply()
    }

    fun lastResult(context: Context): String =
        preferences(context).getString("last_result", "") ?: ""

    fun saveLastStatus(context: Context, status: String) {
        preferences(context).edit().putString("last_status", status).apply()
    }
}
