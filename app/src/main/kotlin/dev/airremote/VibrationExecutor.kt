// SPDX-License-Identifier: Apache-2.0
// Derived from Fitbit Air Notification Bridge; modified for Air Remote.
package dev.airremote

import com.fitbit.goldengate.bindings.coap.CoapEndpoint
import com.fitbit.goldengate.bindings.coap.data.IncomingResponse
import com.fitbit.goldengate.bindings.coap.data.Method
import com.fitbit.goldengate.bindings.coap.data.OutgoingRequestBuilder
import com.fitbit.goldengate.bindings.coap.data.ok
import io.reactivex.Single
import java.util.concurrent.TimeUnit

data class VibrationGroupResult(
    val alternateResponse: IncomingResponse,
    val restoreResponse: IncomingResponse
)

data class VibrationResult(
    val initialIntensity: Int,
    val finalIntensity: Int,
    val groups: List<VibrationGroupResult>
)

/** Executes bounded pattern groups and verifies that the original setting was restored. */
object VibrationExecutor {
    private const val RESTORE_DELAY_MILLIS = 800L
    private const val RESTORE_RETRIES = 2

    fun execute(endpoint: CoapEndpoint, pattern: VibrationPattern): Single<VibrationResult> =
        readIntensity(endpoint).flatMap { initialIntensity ->
            require(
                initialIntensity in SettingsProtocol.HAPTICS_LOW..SettingsProtocol.HAPTICS_HIGH
            ) {
                "Unsupported starting haptics intensity ${SettingsProtocol.hapticsIntensityName(
                    initialIntensity
                )}"
            }
            val alternateIntensity = when (initialIntensity) {
                SettingsProtocol.HAPTICS_HIGH -> SettingsProtocol.HAPTICS_LOW
                else -> SettingsProtocol.HAPTICS_HIGH
            }
            var chain: Single<List<VibrationGroupResult>> = Single.just(emptyList())
            repeat(pattern.groupCount) { groupIndex ->
                val delayBefore = if (groupIndex == 0) {
                    0L
                } else {
                    pattern.betweenGroupDelaysMillis[groupIndex - 1]
                }
                chain = chain.flatMap { completed ->
                    val wait = if (delayBefore == 0L) {
                        Single.just(0L)
                    } else {
                        Single.timer(delayBefore, TimeUnit.MILLISECONDS)
                    }
                    wait.flatMap {
                        executeGroup(endpoint, initialIntensity, alternateIntensity)
                    }.map { completed + it }
                }
            }
            chain.flatMap { groups ->
                readIntensity(endpoint).map { finalIntensity ->
                    require(finalIntensity == initialIntensity) {
                        "Haptics restore verification failed: initial=$initialIntensity final=$finalIntensity"
                    }
                    VibrationResult(initialIntensity, finalIntensity, groups)
                }
            }
        }

    private fun executeGroup(
        endpoint: CoapEndpoint,
        originalIntensity: Int,
        alternateIntensity: Int
    ): Single<VibrationGroupResult> =
        writeIntensity(endpoint, alternateIntensity).flatMap { alternateResponse ->
            require(alternateResponse.responseCode.ok()) {
                "Alternate write returned ${alternateResponse.responseCode}"
            }
            Single.timer(RESTORE_DELAY_MILLIS, TimeUnit.MILLISECONDS)
                .flatMap {
                    writeIntensity(endpoint, originalIntensity)
                        .map { restoreResponse ->
                            require(restoreResponse.responseCode.ok()) {
                                "Restore write returned ${restoreResponse.responseCode}"
                            }
                            restoreResponse
                        }
                        .retry(RESTORE_RETRIES.toLong())
                }
                .map { restoreResponse -> VibrationGroupResult(alternateResponse, restoreResponse) }
        }

    private fun readIntensity(endpoint: CoapEndpoint): Single<Int> {
        val request = OutgoingRequestBuilder("/settings", Method.PUT)
            .expectSuccess(false)
            .maxResendCount(0)
            .body(SettingsProtocol.encodeHapticsSettingsRequest())
            .build()
        return endpoint.responseFor(request).flatMap { response ->
            require(response.responseCode.ok()) {
                "Settings read returned ${response.responseCode}"
            }
            response.body.asData().map { body ->
                SettingsProtocol.parseHapticsIntensity(body)
                    ?: throw IllegalStateException(
                        "Haptics intensity missing from settings response"
                    )
            }
        }
    }

    private fun writeIntensity(endpoint: CoapEndpoint, intensity: Int): Single<IncomingResponse> {
        val request = OutgoingRequestBuilder("/settings", Method.POST)
            .expectSuccess(false)
            .maxResendCount(0)
            .body(SettingsProtocol.encodeHapticsSettingsResponse(intensity))
            .build()
        return endpoint.responseFor(request)
    }
}
