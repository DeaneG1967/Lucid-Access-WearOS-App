package com.droid.lucidgravitywatch.viewmodel

import com.droid.lucidgravitywatch.model.DoorState
import com.droid.lucidgravitywatch.model.GravityVehicleSnapshot
import com.droid.lucidgravitywatch.model.PowerState
import com.droid.lucidgravitywatch.model.VehicleModel

/**
 * Covers a Gravity's first few seconds awake, when its reports are still placeholders.
 *
 * In three logged wakes, the first awake report was the undocumented power state 12, with frunk
 * and trunk UNKNOWN and the charge port OPEN, although the port was closed throughout. The false
 * OPEN cleared 2.3-7.3 s (car report time) after that first awake report, and it doesn't clear at
 * the same time as frunk and trunk, which resolve on their own schedule.
 *
 * So from a sleep to awake transition until the reports settle ([isSettling]):
 * - A port that was CLOSED before the wake is still shown CLOSED while the car says otherwise.
 *   The first CLOSED report ends this hold; a reading still not CLOSED [portHoldMaxMs] after the
 *   first awake report is believed (someone may really have opened it).
 * - With no reading from before the wake, the port is shown UNKNOWN while the car is in state 12.
 * - Settling ends once the power state is documented (not 12/13), frunk and trunk are known, and
 *   the port is no longer held. The view model polls fast meanwhile.
 *
 * Only the Gravity is handled: its port door is manual and display-only, and the Air hasn't shown
 * this. Report times are Lucid's clock and are only compared with each other.
 */
internal class WakeSettleTracker(private val portHoldMaxMs: Long = PORT_HOLD_MAX_MS) {

    /** [shown] is what to display; [event] describes a change worth logging, if any. */
    data class Result(val shown: GravityVehicleSnapshot, val event: String? = null)

    private var vehicleId: String? = null
    private var lastRaw: GravityVehicleSnapshot? = null
    private var firstAwakeReportMs = 0L
    private var holdingPort = false
    private var noPriorReading = false

    /** True from a Gravity's sleep to awake transition until its reports have settled. */
    var isSettling = false
        private set

    /**
     * The car's last report as received, before any adjustment. Pass this to GetVehicleState as
     * the base for fields a response leaves out, so a held port isn't mistaken for a real CLOSED.
     */
    fun lastRawReport(vehicleId: String): GravityVehicleSnapshot? =
        lastRaw?.takeIf { it.vehicleId == vehicleId }

    fun reset() {
        vehicleId = null
        lastRaw = null
        endSettling()
    }

    /** Stops settling (e.g. its time cap ran out); later reports are shown as received. */
    fun endSettling() {
        isSettling = false
        holdingPort = false
        noPriorReading = false
    }

    fun apply(raw: GravityVehicleSnapshot): Result {
        if (raw.vehicleId != vehicleId) {
            reset()
            vehicleId = raw.vehicleId
        }
        val previous = lastRaw
        lastRaw = raw
        if (raw.model != VehicleModel.GRAVITY) return Result(raw)

        if (!raw.powerState.isAwake) {
            endSettling()
            return Result(raw)
        }

        var event: String? = null
        if (!isSettling) {
            when {
                previous != null && previous.powerState.isSleeping -> {
                    startSettling(raw)
                    holdingPort = previous.chargePortState == DoorState.CLOSED
                    event = "woke from ${previous.powerState.name}; settling" +
                        if (holdingPort) " (port CLOSED before wake)" else ""
                }
                previous == null && raw.powerState == PowerState.UNDOCUMENTED_12 -> {
                    startSettling(raw)
                    noPriorReading = true
                    event = "first report is state 12 with no earlier reading; settling, port unknown"
                }
                else -> return Result(raw)
            }
        }

        val elapsedMs = raw.lastUpdatedMs - firstAwakeReportMs
        var shown = raw
        if (holdingPort) {
            when {
                raw.chargePortState == DoorState.CLOSED -> {
                    holdingPort = false
                    event = join(event, "car now reports port CLOSED after ${seconds(elapsedMs)}")
                }
                elapsedMs >= portHoldMaxMs -> {
                    holdingPort = false
                    event = join(
                        event,
                        "port still ${raw.chargePortState.name} after ${seconds(elapsedMs)}; showing it"
                    )
                }
                else -> shown = raw.copy(chargePortState = DoorState.CLOSED)
            }
        }
        val booting = raw.powerState == PowerState.UNDOCUMENTED_12
        if (noPriorReading && booting) {
            shown = shown.copy(chargePortState = DoorState.UNKNOWN)
        }

        val settled = !raw.powerState.isUndocumented &&
            raw.frunkState != DoorState.UNKNOWN &&
            raw.rearTrunkState != DoorState.UNKNOWN &&
            !holdingPort &&
            !(noPriorReading && booting)
        if (settled) {
            endSettling()
            event = join(event, "settled ${seconds(elapsedMs)} after first awake report")
        }
        return Result(shown, event)
    }

    private fun startSettling(raw: GravityVehicleSnapshot) {
        isSettling = true
        firstAwakeReportMs = raw.lastUpdatedMs
        holdingPort = false
        noPriorReading = false
    }

    private fun join(a: String?, b: String): String = if (a == null) b else "$a; $b"

    private fun seconds(ms: Long): String = "%.1fs".format(ms / 1000.0)

    companion object {
        /** Longest a Gravity's port is held CLOSED after waking (car report time). */
        const val PORT_HOLD_MAX_MS = 8_000L
    }
}
