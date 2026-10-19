// SPDX-License-Identifier: Apache-2.0
// Derived from Fitbit Air Notification Bridge; modified for Air Remote.
package dev.airremote

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.fitbit.bluetooth.fbgatt.FitbitBluetoothDevice
import com.fitbit.bluetooth.fbgatt.FitbitGatt
import com.fitbit.bluetooth.fbgatt.GattConnection
import com.fitbit.bluetooth.fbgatt.rx.PeripheralConnectionChangeListener
import com.fitbit.goldengate.bindings.coap.CoapEndpoint
import com.fitbit.goldengate.bindings.coap.CoapEndpointBuilder
import com.fitbit.goldengate.bindings.node.BluetoothAddressNodeKey
import com.fitbit.goldengate.bindings.stack.DtlsSocketNetifGattlink
import com.fitbit.goldengate.bt.DEFAULT_CLIENT_ADDRESS
import com.fitbit.goldengate.bt.DEFAULT_SERVER_ADDRESS
import com.fitbit.goldengate.bt.PeerRole
import com.fitbit.goldengate.node.stack.StackPeer
import io.reactivex.Single
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.schedulers.Schedulers
import java.net.Inet4Address
import java.util.ArrayDeque
import java.util.concurrent.TimeUnit
import timber.log.Timber

/** Opens a temporary Bluetooth session for each command, then closes its own peer. */
class VibrationService : Service() {
    private val queue = ArrayDeque<VibrationCommand>()
    private val disposables = CompositeDisposable()
    private var processing = false
    private var activePeer: StackPeer<CoapEndpoint>? = null
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onCreate() {
        super.onCreate()
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                "Vibration commands",
                NotificationManager.IMPORTANCE_LOW
            )
        )
        startForeground(NOTIFICATION_ID, notification("Preparing your vibration…"))
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val requestId = intent?.getStringExtra(EXTRA_REQUEST_ID)
        val pattern = intent?.getStringExtra(EXTRA_PATTERN)?.let(VibrationPattern::fromWireName)
        if (requestId != null && pattern != null) {
            if (queue.size < MAX_QUEUE_SIZE) {
                queue.addLast(VibrationCommand(requestId, pattern))
            } else {
                DeviceStore.saveResult(this, "failure source=$requestId: Queue is full")
            }
        }
        processNext()
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        disposables.dispose()
        releasePeer()
        releaseWakeLock()
        super.onDestroy()
    }

    private fun processNext() {
        if (processing) return
        val command = queue.pollFirst()
        if (command == null) {
            releaseWakeLock()
            stopForeground(true)
            stopSelf()
            return
        }
        acquireWakeLock()
        processing = true
        updateStatus("Connecting to your Fitbit…")
        disposables.add(
            executeWithRetry(command, 0)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                    { result ->
                        DeviceStore.saveResult(
                            this,
                            "success source=${command.requestId} groups=${result.groups.size} " +
                                "initial=${result.initialIntensity} final=${result.finalIntensity}"
                        )
                        finishCommand("Vibration sent")
                    },
                    { error ->
                        DeviceStore.saveResult(this, "failure source=${command.requestId}: $error")
                        Timber.w(error, "Vibration command failed")
                        finishCommand("Could not reach your Fitbit")
                    }
                )
        )
    }

    private fun finishCommand(status: String) {
        updateStatus(status)
        processing = false
        processNext()
    }

    private fun executeWithRetry(command: VibrationCommand, attempt: Int): Single<VibrationResult> =
        executeOnce(command).onErrorResumeNext { error ->
            releasePeer()
            if (attempt >= RETRY_DELAYS_MILLIS.size) {
                Single.error(error)
            } else {
                updateStatus("Your Fitbit may be busy. Retrying…")
                Single.timer(RETRY_DELAYS_MILLIS[attempt], TimeUnit.MILLISECONDS)
                    .flatMap { executeWithRetry(command, attempt + 1) }
            }
        }

    private fun executeOnce(command: VibrationCommand): Single<VibrationResult> {
        val address = DeviceStore.loadAddress(this)
            ?: return Single.error(IllegalStateException("No paired Fitbit selected"))
        return Single.fromCallable {
            BluetoothRuntime.ensureInitialized(this)
            registerBondedDevice(address)
        }.flatMap { connectStack(it.device.address) }
            .flatMap { endpoint ->
                updateStatus("Sending vibration to your Fitbit…")
                VibrationExecutor.execute(endpoint, command.pattern)
            }
            .flatMap { result ->
                releasePeer()
                Single.timer(RELEASE_SETTLE_MILLIS, TimeUnit.MILLISECONDS).map { result }
            }
            .doOnError { releasePeer() }
    }

    private fun registerBondedDevice(address: String): GattConnection {
        val fitbitGatt = FitbitGatt.getInstance()
        fitbitGatt.getMatchingConnectionsForDeviceNames(null)
            .firstOrNull { it.device.address.equals(address, ignoreCase = true) }
            ?.let { return it }
        val adapter = getSystemService(BluetoothManager::class.java).adapter
        val device = FitbitBluetoothDevice(adapter.getRemoteDevice(address))
        return GattConnection(device, Looper.getMainLooper()).also {
            fitbitGatt.putConnectionIntoDevices(device, it)
        }
    }

    private fun connectStack(address: String): Single<CoapEndpoint> {
        val configuration = DtlsSocketNetifGattlink(
            Inet4Address.getByName(DEFAULT_SERVER_ADDRESS) as Inet4Address,
            0,
            Inet4Address.getByName(DEFAULT_CLIENT_ADDRESS) as Inet4Address,
            0
        )
        val peer = StackPeer(
            BluetoothAddressNodeKey(address),
            PeerRole.Peripheral,
            configuration,
            CoapEndpointBuilder(),
            { connection -> PeripheralConnectionChangeListener().register(connection) },
            { stack -> stack.dtlsEventObservable }
        )
        activePeer = peer
        return peer.connection().firstOrError()
            .timeout(CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .map { peer.stackService }
    }

    @Synchronized
    private fun releasePeer() {
        activePeer?.let { peer ->
            activePeer = null
            runCatching {
                peer.close()
            }.onFailure { Timber.w(it, "Could not close Bluetooth peer") }
        }
    }

    private fun updateStatus(status: String) {
        DeviceStore.saveLastStatus(this, status)
        getSystemService(
            NotificationManager::class.java
        ).notify(NOTIFICATION_ID, notification(status))
    }

    private fun acquireWakeLock() {
        if (wakeLock?.isHeld == true) return
        wakeLock = getSystemService(PowerManager::class.java)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "airremote:command")
            .apply { acquire(WAKE_LOCK_TIMEOUT_MILLIS) }
    }

    private fun releaseWakeLock() {
        wakeLock?.let { if (it.isHeld) it.release() }
        wakeLock = null
    }

    private fun notification(status: String) = NotificationCompat.Builder(this, CHANNEL_ID)
        .setSmallIcon(R.drawable.ic_air_remote)
        .setContentTitle("Air Remote")
        .setContentText(status)
        .setOngoing(true)
        .setOnlyAlertOnce(true)
        .setContentIntent(
            PendingIntent.getActivity(
                this,
                0,
                Intent(this, MainActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        ).build()

    companion object {
        private const val EXTRA_REQUEST_ID = "request_id"
        private const val EXTRA_PATTERN = "pattern"
        private const val CHANNEL_ID = "vibration_commands"
        private const val NOTIFICATION_ID = 14001
        private const val MAX_QUEUE_SIZE = 10
        private const val CONNECT_TIMEOUT_SECONDS = 18L
        private const val RELEASE_SETTLE_MILLIS = 700L
        private const val WAKE_LOCK_TIMEOUT_MILLIS = 120_000L
        private val RETRY_DELAYS_MILLIS = listOf(2_000L, 5_000L)

        fun enqueue(context: Context, command: VibrationCommand): Boolean {
            if (DeviceStore.loadAddress(context) == null) return false
            val intent = Intent(context, VibrationService::class.java)
                .putExtra(EXTRA_REQUEST_ID, command.requestId)
                .putExtra(EXTRA_PATTERN, command.pattern.wireName)
            return runCatching {
                ContextCompat.startForegroundService(context, intent)
                true
            }.getOrElse {
                DeviceStore.saveResult(context, "failure source=${command.requestId}: $it")
                false
            }
        }
    }
}
