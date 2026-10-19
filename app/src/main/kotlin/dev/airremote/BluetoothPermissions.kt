// SPDX-License-Identifier: Apache-2.0
// Derived from Fitbit Air Notification Bridge; modified for Air Remote.
package dev.airremote

import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

/** Android 12+ permissions needed by the transport library. */
object BluetoothPermissions {
    private const val BLUETOOTH_SCAN = "android.permission.BLUETOOTH_SCAN"
    private const val BLUETOOTH_CONNECT = "android.permission.BLUETOOTH_CONNECT"

    private fun requiredPermissions() = arrayOf(BLUETOOTH_SCAN, BLUETOOTH_CONNECT)

    fun hasAll(context: Context): Boolean = requiredPermissions().all {
        ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
    }

    fun request(activity: Activity, requestCode: Int) {
        ActivityCompat.requestPermissions(activity, requiredPermissions(), requestCode)
    }
}
