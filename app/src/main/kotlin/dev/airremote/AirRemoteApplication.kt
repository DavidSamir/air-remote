// SPDX-License-Identifier: Apache-2.0
// Derived from Fitbit's Golden Gate Android sample; modified for Air Remote.
package dev.airremote

import android.app.Application
import io.reactivex.exceptions.UndeliverableException
import io.reactivex.plugins.RxJavaPlugins
import timber.log.Timber

class AirRemoteApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        if (BuildConfig.DEBUG) Timber.plant(Timber.DebugTree())
        RxJavaPlugins.setErrorHandler { error ->
            val cause = if (error is UndeliverableException) error.cause ?: error else error
            // A disposed Bluetooth operation can report cancellation after the service closes.
            if (cause is InterruptedException) return@setErrorHandler
            if (cause is NullPointerException ||
                cause is IllegalArgumentException ||
                cause is IllegalStateException
            ) {
                val thread = Thread.currentThread()
                thread.uncaughtExceptionHandler?.uncaughtException(thread, cause)
            } else {
                Timber.w(cause, "Bluetooth operation ended after disposal")
            }
        }
    }
}
