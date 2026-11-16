package com.droid.lucidgravitywatch.viewmodel

import com.droid.lucidgravitywatch.model.BatteryPreconStatus
import com.droid.lucidgravitywatch.model.ChargeState
import com.droid.lucidgravitywatch.model.DefrostState
import com.droid.lucidgravitywatch.model.DoorState
import com.droid.lucidgravitywatch.model.DoorState.CLOSED
import com.droid.lucidgravitywatch.model.DoorState.OPEN
import com.droid.lucidgravitywatch.model.DoorState.UNKNOWN
import com.droid.lucidgravitywatch.model.GravityVehicleSnapshot
import com.droid.lucidgravitywatch.model.HvacPower
import com.droid.lucidgravitywatch.model.LockState
import com.droid.lucidgravitywatch.model.PowerState
import com.droid.lucidgravitywatch.model.PowerState.MONITOR
import com.droid.lucidgravitywatch.model.PowerState.SLEEP
import com.droid.lucidgravitywatch.model.PowerState.UNDOCUMENTED_12
import com.droid.lucidgravitywatch.model.SeatClimateMode
import com.droid.lucidgravitywatch.model.SteeringWheelHeaterLevel
import com.droid.lucidgravitywatch.model.VehicleModel
import com.droid.lucidgravitywatch.model.WalkawayState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Report sequences are from three logged Gravity wakes (2026-11-16). Each step is one report: power
 * state, frunk, trunk, port as received, report time (ms after the first awake report), then the
 * port expected on screen and whether the car should still be settling.
 */
class WakeSettleTrackerTest {

    private data class Step(
        val power: PowerState,
        val frunk: DoorState,
        val trunk: DoorState,
        val port: DoorState,
        val atMs: Long,
        val shownPort: DoorState,
        val settling: Boolean
    )

    private fun replay(steps: List<Step>, model: VehicleModel = VehicleModel.GRAVITY) {
        val tracker = WakeSettleTracker()
        steps.forEachIndexed { i, s ->
            val raw = report(s.power, s.frunk, s.trunk, s.port, s.atMs, model)
            // Polls often return the same report more than once; the result must not change.
            repeat(2) { poll ->
                val shown = tracker.apply(raw).shown
                assertEquals("step $i poll $poll port", s.shownPort, shown.chargePortState)
                assertEquals("step $i poll $poll settling", s.settling, tracker.isSettling)
                assertEquals("only the port is adjusted", raw, shown.copy(chargePortState = raw.chargePortState))
            }
        }
    }

    @Test
    fun wake1214_falseOpenThroughFirstMonitorReport_isHeldUntilCarSaysClosed() = replay(
        listOf(
            Step(SLEEP, UNKNOWN, UNKNOWN, CLOSED, -214_851, CLOSED, settling = false),
            Step(UNDOCUMENTED_12, UNKNOWN, UNKNOWN, OPEN, 0, CLOSED, settling = true),
            Step(MONITOR, CLOSED, CLOSED, OPEN, 4_088, CLOSED, settling = true),
            Step(MONITOR, CLOSED, CLOSED, CLOSED, 7_253, CLOSED, settling = false)
        )
    )

    @Test
    fun wake1200_portClearsBeforeTrunk_keepsSettlingUntilTrunkKnown() = replay(
        listOf(
            Step(SLEEP, UNKNOWN, UNKNOWN, CLOSED, -161_009, CLOSED, settling = false),
            Step(UNDOCUMENTED_12, UNKNOWN, UNKNOWN, OPEN, 0, CLOSED, settling = true),
            Step(MONITOR, CLOSED, UNKNOWN, CLOSED, 2_254, CLOSED, settling = true),
            Step(MONITOR, CLOSED, CLOSED, CLOSED, 6_327, CLOSED, settling = false),
            Step(MONITOR, CLOSED, CLOSED, CLOSED, 8_920, CLOSED, settling = false)
        )
    )

    @Test
    fun wake1007_frunkAndTrunkKnownBeforePort_keepsHolding() = replay(
        listOf(
            Step(SLEEP, UNKNOWN, UNKNOWN, CLOSED, -118_104, CLOSED, settling = false),
            Step(UNDOCUMENTED_12, UNKNOWN, UNKNOWN, OPEN, 0, CLOSED, settling = true),
            Step(MONITOR, CLOSED, CLOSED, OPEN, 2_423, CLOSED, settling = true),
            Step(MONITOR, CLOSED, CLOSED, CLOSED, 6_588, CLOSED, settling = false)
        )
    )

    /** Port left open before sleep: the car reports OPEN throughout, with no false CLOSED. */
    @Test
    fun wake1343_portOpenBeforeSleep_isShownOpenThroughout() = replay(
        listOf(
            Step(SLEEP, UNKNOWN, UNKNOWN, OPEN, -18_383, OPEN, settling = false),
            Step(UNDOCUMENTED_12, UNKNOWN, UNKNOWN, OPEN, 0, OPEN, settling = true),
            Step(MONITOR, CLOSED, UNKNOWN, OPEN, 2_582, OPEN, settling = true),
            Step(MONITOR, CLOSED, CLOSED, OPEN, 5_308, OPEN, settling = false),
            Step(MONITOR, CLOSED, CLOSED, OPEN, 8_356, OPEN, settling = false),
            Step(MONITOR, CLOSED, CLOSED, OPEN, 11_624, OPEN, settling = false)
        )
    )

    @Test
    fun portStillOpenAfterHoldLimit_isShown() = replay(
        listOf(
            Step(SLEEP, UNKNOWN, UNKNOWN, CLOSED, -60_000, CLOSED, settling = false),
            Step(UNDOCUMENTED_12, UNKNOWN, UNKNOWN, OPEN, 0, CLOSED, settling = true),
            Step(MONITOR, CLOSED, CLOSED, OPEN, 7_999, CLOSED, settling = true),
            Step(MONITOR, CLOSED, CLOSED, OPEN, WakeSettleTracker.PORT_HOLD_MAX_MS, OPEN, settling = false)
        )
    )

    @Test
    fun portOpenedAfterSettling_isShownImmediately() = replay(
        listOf(
            Step(SLEEP, UNKNOWN, UNKNOWN, CLOSED, -60_000, CLOSED, settling = false),
            Step(UNDOCUMENTED_12, UNKNOWN, UNKNOWN, OPEN, 0, CLOSED, settling = true),
            Step(MONITOR, CLOSED, CLOSED, CLOSED, 3_000, CLOSED, settling = false),
            Step(MONITOR, CLOSED, CLOSED, OPEN, 5_000, OPEN, settling = false)
        )
    )

    @Test
    fun portOpenBeforeSleep_isNotHeld() = replay(
        listOf(
            Step(SLEEP, UNKNOWN, UNKNOWN, OPEN, -60_000, OPEN, settling = false),
            Step(UNDOCUMENTED_12, UNKNOWN, UNKNOWN, OPEN, 0, OPEN, settling = true),
            Step(MONITOR, CLOSED, CLOSED, OPEN, 2_000, OPEN, settling = false)
        )
    )

    @Test
    fun noReadingBeforeWake_showsPortUnknownDuringState12() = replay(
        listOf(
            Step(UNDOCUMENTED_12, UNKNOWN, UNKNOWN, OPEN, 0, UNKNOWN, settling = true),
            Step(MONITOR, CLOSED, CLOSED, CLOSED, 2_000, CLOSED, settling = false)
        )
    )

    @Test
    fun noReadingBeforeWake_openAfterState12_isBelieved() = replay(
        listOf(
            Step(UNDOCUMENTED_12, UNKNOWN, UNKNOWN, OPEN, 0, UNKNOWN, settling = true),
            Step(MONITOR, CLOSED, CLOSED, OPEN, 2_000, OPEN, settling = false)
        )
    )

    @Test
    fun alreadyAwakeOnFirstReport_isShownAsReceived() = replay(
        listOf(Step(MONITOR, CLOSED, CLOSED, OPEN, 0, OPEN, settling = false))
    )

    @Test
    fun air_isNotAdjusted() = replay(
        listOf(
            Step(SLEEP, UNKNOWN, UNKNOWN, CLOSED, -60_000, CLOSED, settling = false),
            Step(UNDOCUMENTED_12, UNKNOWN, UNKNOWN, OPEN, 0, OPEN, settling = false)
        ),
        model = VehicleModel.AIR
    )

    @Test
    fun sleepingAgainMidSettle_stopsSettlingAndNextWakeStartsOver() {
        val tracker = WakeSettleTracker()
        tracker.apply(report(SLEEP, UNKNOWN, UNKNOWN, CLOSED, -60_000))
        tracker.apply(report(UNDOCUMENTED_12, UNKNOWN, UNKNOWN, OPEN, 0))
        assertTrue(tracker.isSettling)
        tracker.apply(report(SLEEP, UNKNOWN, UNKNOWN, CLOSED, 1_000))
        assertFalse(tracker.isSettling)
        val shown = tracker.apply(report(UNDOCUMENTED_12, UNKNOWN, UNKNOWN, OPEN, 100_000)).shown
        assertTrue(tracker.isSettling)
        assertEquals(CLOSED, shown.chargePortState)
    }

    @Test
    fun endSettling_showsLaterReportsAsReceived() {
        val tracker = WakeSettleTracker()
        tracker.apply(report(SLEEP, UNKNOWN, UNKNOWN, CLOSED, -60_000))
        tracker.apply(report(UNDOCUMENTED_12, UNKNOWN, UNKNOWN, OPEN, 0))
        tracker.endSettling()
        assertFalse(tracker.isSettling)
        assertEquals(OPEN, tracker.apply(report(MONITOR, CLOSED, CLOSED, OPEN, 1_000)).shown.chargePortState)
    }

    @Test
    fun lastRawReport_isTheReportAsReceived_forTheSameCarOnly() {
        val tracker = WakeSettleTracker()
        tracker.apply(report(SLEEP, UNKNOWN, UNKNOWN, CLOSED, -60_000))
        val raw = report(UNDOCUMENTED_12, UNKNOWN, UNKNOWN, OPEN, 0)
        assertEquals(CLOSED, tracker.apply(raw).shown.chargePortState)
        assertEquals(raw, tracker.lastRawReport(raw.vehicleId))
        assertEquals(null, tracker.lastRawReport("another-car"))
    }

    private fun report(
        power: PowerState,
        frunk: DoorState,
        trunk: DoorState,
        port: DoorState,
        atMs: Long,
        model: VehicleModel = VehicleModel.GRAVITY
    ) = GravityVehicleSnapshot(
        vehicleId = "veh-gravity-01",
        vin = "50EA1GBA7RA000101",
        nickname = "GRAVITY GT",
        model = model,
        variantName = "Grand Touring",
        paintColorName = "Aurora Green",
        powerState = power,
        powerStateCode = power.protoValue,
        lockState = LockState.LOCKED,
        walkawayState = WalkawayState.ACTIVE,
        frunkState = frunk,
        rearTrunkState = trunk,
        chargePortState = port,
        frontLeftDoor = CLOSED,
        frontRightDoor = CLOSED,
        rearLeftDoor = CLOSED,
        rearRightDoor = CLOSED,
        batteryPercent = 79.0,
        remainingRangeKm = 500.0,
        batteryKwh = 95.0,
        batteryCapacityKwh = 120.0,
        batteryPreconStatus = BatteryPreconStatus.OFF,
        chargeState = ChargeState.NOT_CONNECTED,
        chargeLimitPercent = 80,
        chargeRateKw = 0.0,
        chargeRateMph = 0.0,
        sessionMinutesRemaining = 0,
        interiorTempCelsius = 20.0,
        exteriorTempCelsius = 15.0,
        targetTempCelsius = 21.0,
        hvacPower = HvacPower.OFF,
        defrostState = DefrostState.OFF,
        maxAcEnabled = false,
        driverSeatHeatMode = SeatClimateMode.OFF,
        driverSeatVentMode = SeatClimateMode.OFF,
        passengerSeatHeatMode = SeatClimateMode.OFF,
        steeringHeaterLevel = SteeringWheelHeaterLevel.OFF,
        odometerKm = 1850.0,
        softwareVersion = "2.5.10",
        lastUpdatedMs = WAKE_MS + atMs
    )

    private companion object {
        const val WAKE_MS = 1_794_833_686_612L
    }
}
