package com.droid.lucidgravitywatch.viewmodel

import com.droid.lucidgravitywatch.model.BatteryPreconStatus
import com.droid.lucidgravitywatch.model.ChargeState
import com.droid.lucidgravitywatch.model.DefrostState
import com.droid.lucidgravitywatch.model.DoorState
import com.droid.lucidgravitywatch.model.GravityVehicleSnapshot
import com.droid.lucidgravitywatch.model.ClimateSeat
import com.droid.lucidgravitywatch.model.HvacPower
import com.droid.lucidgravitywatch.model.LockState
import com.droid.lucidgravitywatch.model.LucidRegion
import com.droid.lucidgravitywatch.model.LucidSession
import com.droid.lucidgravitywatch.model.PowerState
import com.droid.lucidgravitywatch.model.SeatClimateKind
import com.droid.lucidgravitywatch.model.SeatClimateMode
import com.droid.lucidgravitywatch.model.SteeringHeaterStatus
import com.droid.lucidgravitywatch.model.SteeringWheelHeaterLevel
import com.droid.lucidgravitywatch.model.VehicleModel
import com.droid.lucidgravitywatch.model.WalkawayState
import com.droid.lucidgravitywatch.network.LucidApiException
import com.droid.lucidgravitywatch.repository.LucidVehicleRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class GravityWatchViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    private class FakeLucidVehicleRepository : LucidVehicleRepository {
        var currentSession: LucidSession? = null
        var useFahrenheit: Boolean = true
        var shouldFailRequests: Boolean = false
        var flashedLightsCount: Int = 0
        var honkedHornCount: Int = 0
        var fetchVehiclesCount: Int = 0
        var fetchStateCount: Int = 0
        /** Thrown by the next fetchVehicleState call, then cleared. */
        var nextStateFetchError: Throwable? = null
        /** When > 0, each fetchVehicleState call returns a report this much newer (an online car). */
        var advanceReportOnStateFetchMs: Long = 0L
        /** When true, WakeupVehicle is accepted but the car's state does not change. */
        var wakeHasNoEffect: Boolean = false
        var wakeCount: Int = 0
        var frunkCommandCount: Int = 0
        var climateCommandCount: Int = 0
        var chargingCommandCount: Int = 0
        val steeringLevelsSent = mutableListOf<SteeringWheelHeaterLevel>()
        val rearHeatSent = mutableListOf<SeatClimateMode?>()
        val passengerVentSent = mutableListOf<SeatClimateMode?>()
        /** Behave like a Lucid Air: Max AC and Max Defrost also turn climate on, Max AC cools the driver seat. */
        var airClimateSideEffects = false
        var deferCloudStateUpdateOnCommand: Boolean = false
        var fetchGate: CompletableDeferred<Unit>? = null
        var currentSelectedVehicleId: String? = "veh-gravity-01"
        var extraVehicles: List<GravityVehicleSnapshot> = emptyList()
        var snapshot = GravityVehicleSnapshot(
            vehicleId = "veh-gravity-01",
            vin = "50EA1GBA7RA000101",
            nickname = "Lucid Gravity GT",
            model = VehicleModel.GRAVITY,
            variantName = "Grand Touring",
            paintColorName = "Aurora Green",
            powerState = PowerState.SLEEP,
            lockState = LockState.LOCKED,
            walkawayState = WalkawayState.ACTIVE,
            frunkState = DoorState.CLOSED,
            rearTrunkState = DoorState.CLOSED,
            chargePortState = DoorState.CLOSED,
            frontLeftDoor = DoorState.CLOSED,
            frontRightDoor = DoorState.CLOSED,
            rearLeftDoor = DoorState.CLOSED,
            rearRightDoor = DoorState.CLOSED,
            batteryPercent = 78.0,
            remainingRangeKm = 540.0,
            batteryKwh = 95.0,
            batteryCapacityKwh = 120.0,
            batteryPreconStatus = BatteryPreconStatus.OFF,
            chargeState = ChargeState.CABLE_CONNECTED,
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
            lastUpdatedMs = 1792113000000L
        )

        override fun getActiveSession(): LucidSession? = currentSession
        override fun isFahrenheitPreferred(): Boolean = useFahrenheit
        override fun setFahrenheitPreferred(fahrenheit: Boolean) {
            useFahrenheit = fahrenheit
        }

        override fun getSelectedVehicleId(): String? = currentSelectedVehicleId
        override fun setSelectedVehicleId(vehicleId: String) {
            currentSelectedVehicleId = vehicleId
        }

        override suspend fun login(
            email: String,
            password: String,
            region: LucidRegion
        ): List<GravityVehicleSnapshot> {
            if (shouldFailRequests) throw IllegalStateException("Login failed")
            currentSession = LucidSession(
                uid = "u-1",
                idToken = "token-1",
                refreshToken = "ref-1",
                gigyaJwt = "gig-1",
                expiryTimeSec = ((System.currentTimeMillis() / 1000L) + 3600L).toInt(),
                userEmail = email,
                firstName = "Taylor",
                lastName = "Chen",
                region = region
            )
            return listOf(snapshot) + extraVehicles
        }

        override suspend fun fetchVehicles(): List<GravityVehicleSnapshot> {
            fetchVehiclesCount++
            fetchGate?.await()
            if (shouldFailRequests) throw IllegalStateException("Fetch failed")
            return listOf(snapshot) + extraVehicles
        }

        override suspend fun fetchVehicleState(vehicle: GravityVehicleSnapshot): GravityVehicleSnapshot {
            fetchStateCount++
            fetchGate?.await()
            nextStateFetchError?.let {
                nextStateFetchError = null
                throw it
            }
            if (shouldFailRequests) throw IllegalStateException("Fetch failed")
            if (advanceReportOnStateFetchMs > 0L) {
                snapshot = snapshot.copy(lastUpdatedMs = snapshot.lastUpdatedMs + advanceReportOnStateFetchMs)
            }
            return (listOf(snapshot) + extraVehicles).first { it.vehicleId == vehicle.vehicleId }
        }

        override suspend fun wakeVehicle(vehicle: GravityVehicleSnapshot): GravityVehicleSnapshot {
            if (shouldFailRequests) throw IllegalStateException("Wake failed")
            wakeCount++
            if (!wakeHasNoEffect) {
                snapshot = snapshot.copy(powerState = PowerState.CLOUD_1)
            }
            return snapshot
        }

        override suspend fun setDoorLocks(
            vehicle: GravityVehicleSnapshot,
            lockState: LockState
        ): GravityVehicleSnapshot {
            if (!deferCloudStateUpdateOnCommand) {
                snapshot = snapshot.copy(lockState = lockState)
            }
            return vehicle.copy(lockState = lockState)
        }

        override suspend fun setFrunkState(
            vehicle: GravityVehicleSnapshot,
            doorState: DoorState
        ): GravityVehicleSnapshot {
            frunkCommandCount++
            if (!deferCloudStateUpdateOnCommand) {
                snapshot = snapshot.copy(frunkState = doorState)
            }
            return vehicle.copy(frunkState = doorState)
        }

        override suspend fun setRearTrunkState(
            vehicle: GravityVehicleSnapshot,
            doorState: DoorState
        ): GravityVehicleSnapshot {
            if (!deferCloudStateUpdateOnCommand) {
                snapshot = snapshot.copy(rearTrunkState = doorState)
            }
            return vehicle.copy(rearTrunkState = doorState)
        }

        override suspend fun setChargePortState(
            vehicle: GravityVehicleSnapshot,
            doorState: DoorState
        ): GravityVehicleSnapshot {
            snapshot = snapshot.copy(chargePortState = doorState)
            return snapshot
        }

        override suspend fun setCabinTemperature(
            vehicle: GravityVehicleSnapshot,
            temperatureCelsius: Double?
        ): GravityVehicleSnapshot {
            climateCommandCount++
            if (deferCloudStateUpdateOnCommand) return vehicle
            snapshot = if (temperatureCelsius == null) {
                // On the Air, climate off also turns the seat cooling off
                snapshot.copy(
                    hvacPower = HvacPower.OFF,
                    driverSeatVentMode = if (airClimateSideEffects) SeatClimateMode.OFF else snapshot.driverSeatVentMode,
                    passengerSeatVentMode = if (airClimateSideEffects) SeatClimateMode.OFF else snapshot.passengerSeatVentMode
                )
            } else {
                snapshot.copy(
                    targetTempCelsius = temperatureCelsius,
                    hvacPower = HvacPower.PRECONDITION
                )
            }
            return snapshot
        }

        override suspend fun setDefrost(
            vehicle: GravityVehicleSnapshot,
            defrostState: DefrostState
        ): GravityVehicleSnapshot {
            snapshot = snapshot.copy(defrostState = defrostState)
            if (defrostState == DefrostState.ON && airClimateSideEffects) {
                snapshot = snapshot.copy(hvacPower = HvacPower.PRECONDITION)
            }
            return snapshot
        }

        override suspend fun setAllWindows(
            vehicle: GravityVehicleSnapshot,
            vent: Boolean
        ): GravityVehicleSnapshot {
            snapshot = snapshot.copy(windowsOpen = vent)
            return snapshot
        }

        override suspend fun setAcCurrentLimit(
            vehicle: GravityVehicleSnapshot,
            amps: Int
        ): GravityVehicleSnapshot {
            snapshot = snapshot.copy(sessionAcCurrentLimitAmps = amps)
            return snapshot
        }

        override suspend fun setMaxAc(
            vehicle: GravityVehicleSnapshot,
            enabled: Boolean
        ): GravityVehicleSnapshot {
            snapshot = snapshot.copy(maxAcEnabled = enabled)
            if (enabled && airClimateSideEffects) {
                snapshot = snapshot.copy(hvacPower = HvacPower.PRECONDITION, driverSeatVentMode = SeatClimateMode.HIGH)
            }
            return snapshot
        }

        override suspend fun setSeatClimate(
            vehicle: GravityVehicleSnapshot,
            driverHeat: SeatClimateMode,
            driverVent: SeatClimateMode,
            passengerHeat: SeatClimateMode,
            rearHeat: SeatClimateMode?,
            passengerVent: SeatClimateMode?
        ): GravityVehicleSnapshot {
            rearHeatSent += listOf(rearHeat)
            passengerVentSent += listOf(passengerVent)
            snapshot = snapshot.copy(
                driverSeatHeatMode = driverHeat,
                driverSeatVentMode = driverVent,
                passengerSeatHeatMode = passengerHeat,
                rearSeatHeatMode = rearHeat ?: snapshot.rearSeatHeatMode,
                passengerSeatVentMode = passengerVent ?: snapshot.passengerSeatVentMode
            )
            return snapshot
        }

        override suspend fun setSteeringWheelHeater(
            vehicle: GravityVehicleSnapshot,
            level: SteeringWheelHeaterLevel
        ): GravityVehicleSnapshot {
            steeringLevelsSent += level
            snapshot = snapshot.copy(
                steeringHeaterLevel = if (snapshot.hasOnOffSteeringHeater) snapshot.steeringHeaterLevel else level,
                steeringHeaterStatus = if (level == SteeringWheelHeaterLevel.OFF) {
                    SteeringHeaterStatus.OFF
                } else {
                    SteeringHeaterStatus.ON
                }
            )
            return snapshot
        }

        override suspend fun setBatteryPreconditioning(
            vehicle: GravityVehicleSnapshot,
            status: BatteryPreconStatus
        ): GravityVehicleSnapshot {
            snapshot = snapshot.copy(batteryPreconStatus = status)
            return snapshot
        }

        override suspend fun setChargeLimit(
            vehicle: GravityVehicleSnapshot,
            limitPercent: Int
        ): GravityVehicleSnapshot {
            snapshot = snapshot.copy(chargeLimitPercent = limitPercent)
            return snapshot
        }

        override suspend fun setChargingActive(
            vehicle: GravityVehicleSnapshot,
            active: Boolean
        ): GravityVehicleSnapshot {
            chargingCommandCount++
            if (deferCloudStateUpdateOnCommand) return vehicle
            snapshot = snapshot.copy(
                chargeState = if (active) ChargeState.CHARGING else ChargeState.CHARGING_STOPPED
            )
            return snapshot
        }

        override suspend fun flashLights(vehicle: GravityVehicleSnapshot) {
            if (shouldFailRequests) throw IllegalStateException("Flash failed")
            flashedLightsCount++
        }

        override suspend fun honkHorn(vehicle: GravityVehicleSnapshot) {
            if (shouldFailRequests) throw IllegalStateException("Horn failed")
            honkedHornCount++
        }

        override fun logout() {
            currentSession = null
        }
    }

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun loginAndControls_updateUiStateProperly() = runTest {
        val repo = FakeLucidVehicleRepository()
        val viewModel = GravityWatchViewModel.Factory(repo).create(GravityWatchViewModel::class.java)
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isAuthenticated)

        // Blank credentials validation
        viewModel.login("", "", LucidRegion.US)
        assertNotNull(viewModel.uiState.value.errorMessage)
        viewModel.clearMessages()
        assertNull(viewModel.uiState.value.errorMessage)

        viewModel.selectRegion(LucidRegion.EU)
        assertEquals(LucidRegion.EU, viewModel.uiState.value.selectedRegion)

        viewModel.login("owner@lucidmotors.com", "password123", LucidRegion.US)
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.isAuthenticated)
        assertNotNull(viewModel.uiState.value.activeVehicle)
        assertEquals(LockState.LOCKED, viewModel.uiState.value.activeVehicle?.lockState)

        // UI preferences and dial switching
        viewModel.openPage(WatchPage.CLIMATE)
        assertEquals(WatchPage.CLIMATE, viewModel.uiState.value.currentPage)
        viewModel.openPage(WatchPage.HOME)
        assertEquals(WatchPage.HOME, viewModel.uiState.value.currentPage)
        viewModel.toggleTemperatureUnit()
        assertFalse(viewModel.uiState.value.useFahrenheit)
        viewModel.toggleWatchBezelFrame()
        assertFalse(viewModel.uiState.value.watchBezelFrameEnabled)
        viewModel.selectVehicle("veh-gravity-01")

        // Car is asleep -> controls frozen and commands are ignored
        assertEquals("Car asleep", viewModel.uiState.value.controlsLockedReason)
        viewModel.toggleFrunk()
        advanceUntilIdle()
        assertEquals(0, repo.frunkCommandCount)

        // Wake vehicle -> controls unfreeze once Lucid Cloud reports the car awake
        viewModel.wakeVehicle()
        advanceUntilIdle()
        assertEquals(1, repo.wakeCount)
        viewModel.refreshVehicles()
        advanceUntilIdle()
        assertEquals(PowerState.CLOUD_1, viewModel.uiState.value.activeVehicle?.powerState)
        assertFalse(viewModel.uiState.value.areControlsLocked)

        // Toggle lock -> UNLOCKED and back -> LOCKED
        viewModel.toggleLock()
        advanceUntilIdle()
        assertEquals(LockState.UNLOCKED, viewModel.uiState.value.activeVehicle?.lockState)
        viewModel.toggleLock()
        advanceUntilIdle()
        assertEquals(LockState.LOCKED, viewModel.uiState.value.activeVehicle?.lockState)

        // Toggle Frunk -> OPEN and back -> CLOSED
        viewModel.toggleFrunk()
        advanceUntilIdle()
        assertEquals(DoorState.OPEN, viewModel.uiState.value.activeVehicle?.frunkState)
        viewModel.toggleFrunk()
        advanceUntilIdle()
        assertEquals(DoorState.CLOSED, viewModel.uiState.value.activeVehicle?.frunkState)

        // Toggle Rear Trunk -> OPEN and back -> CLOSED
        viewModel.toggleRearTrunk()
        advanceUntilIdle()
        assertEquals(DoorState.OPEN, viewModel.uiState.value.activeVehicle?.rearTrunkState)
        viewModel.toggleRearTrunk()
        advanceUntilIdle()
        assertEquals(DoorState.CLOSED, viewModel.uiState.value.activeVehicle?.rearTrunkState)

        // Toggle Charge Port -> OPEN and back -> CLOSED (unplugged; the port can't move on a cable)
        repo.snapshot = repo.snapshot.copy(chargeState = ChargeState.NOT_CONNECTED)
        viewModel.refreshVehicles()
        advanceUntilIdle()
        viewModel.toggleChargePort()
        advanceUntilIdle()
        assertEquals(DoorState.OPEN, viewModel.uiState.value.activeVehicle?.chargePortState)
        viewModel.toggleChargePort()
        advanceUntilIdle()
        assertEquals(DoorState.CLOSED, viewModel.uiState.value.activeVehicle?.chargePortState)

        // With a cable connected, port commands are not sent
        repo.snapshot = repo.snapshot.copy(chargeState = ChargeState.CABLE_CONNECTED)
        viewModel.refreshVehicles()
        advanceUntilIdle()
        viewModel.toggleChargePort()
        advanceUntilIdle()
        assertEquals(DoorState.CLOSED, viewModel.uiState.value.activeVehicle?.chargePortState)

        // Toggle Climate Precondition -> PRECONDITION and back -> OFF
        viewModel.toggleClimatePrecondition()
        advanceUntilIdle()
        assertEquals(HvacPower.PRECONDITION, viewModel.uiState.value.activeVehicle?.hvacPower)
        viewModel.toggleClimatePrecondition()
        advanceUntilIdle()
        assertEquals(HvacPower.OFF, viewModel.uiState.value.activeVehicle?.hvacPower)

        // Setting the cabin temperature also turns climate on (SetCabinTemperature carries power)
        viewModel.setCabinTargetTemperature(22.0)
        advanceUntilIdle()
        assertEquals(22.0, viewModel.uiState.value.activeVehicle?.targetTempCelsius ?: 0.0, 0.01)
        assertEquals(HvacPower.PRECONDITION, viewModel.uiState.value.activeVehicle?.hvacPower)
        assertFalse(viewModel.uiState.value.areControlsLocked)

        // Toggle Defrost -> ON and back -> OFF
        viewModel.toggleDefrost()
        advanceUntilIdle()
        assertEquals(DefrostState.ON, viewModel.uiState.value.activeVehicle?.defrostState)
        viewModel.toggleDefrost()
        advanceUntilIdle()
        assertEquals(DefrostState.OFF, viewModel.uiState.value.activeVehicle?.defrostState)

        // Vent all windows, then close them
        viewModel.setAllWindows(vent = true)
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.activeVehicle!!.windowsOpen)
        viewModel.setAllWindows(vent = false)
        advanceUntilIdle()
        assertFalse(viewModel.uiState.value.activeVehicle!!.windowsOpen)

        // Toggle Max AC -> on and back -> off
        viewModel.toggleMaxAc()
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.activeVehicle!!.maxAcEnabled)
        viewModel.toggleMaxAc()
        advanceUntilIdle()
        assertFalse(viewModel.uiState.value.activeVehicle!!.maxAcEnabled)

        // Steering wheel tap on a car with heater levels -> LEVEL_3 (taps go High first)
        viewModel.tapSteeringWheel()
        advanceUntilIdle()
        assertEquals(
            SteeringWheelHeaterLevel.LEVEL_3,
            viewModel.uiState.value.activeVehicle?.steeringHeaterLevel
        )

        // Charge rate 40 A, clamped to the 10 A minimum below it
        viewModel.setChargeCurrentLimit(40)
        advanceUntilIdle()
        assertEquals(40, viewModel.uiState.value.activeVehicle!!.sessionAcCurrentLimitAmps)
        viewModel.setChargeCurrentLimit(4)
        advanceUntilIdle()
        assertEquals(GravityWatchViewModel.MIN_CHARGE_CURRENT_AMPS, viewModel.uiState.value.activeVehicle!!.sessionAcCurrentLimitAmps)
        assertFalse(viewModel.uiState.value.areControlsLocked)

        // Adjust Charge Limit to 90%
        viewModel.setChargeLimit(90)
        advanceUntilIdle()
        assertEquals(90, viewModel.uiState.value.activeVehicle?.chargeLimitPercent)

        // Toggle Charging -> CHARGING and back -> STOPPED
        viewModel.toggleCharging()
        advanceUntilIdle()
        assertEquals(ChargeState.CHARGING, viewModel.uiState.value.activeVehicle?.chargeState)
        viewModel.toggleCharging()
        advanceUntilIdle()
        assertEquals(ChargeState.CHARGING_STOPPED, viewModel.uiState.value.activeVehicle?.chargeState)

        // Toggle Battery Preconditioning -> ON and back -> OFF
        viewModel.toggleBatteryPreconditioning()
        advanceUntilIdle()
        assertEquals(
            BatteryPreconStatus.ON,
            viewModel.uiState.value.activeVehicle?.batteryPreconStatus
        )
        viewModel.toggleBatteryPreconditioning()
        advanceUntilIdle()
        assertEquals(
            BatteryPreconStatus.OFF,
            viewModel.uiState.value.activeVehicle?.batteryPreconStatus
        )

        // Flash lights & honk horn
        viewModel.flashLights()
        advanceUntilIdle()
        assertEquals(1, repo.flashedLightsCount)

        viewModel.honkHorn()
        advanceUntilIdle()
        assertEquals(1, repo.honkedHornCount)

        // Error handling branch
        repo.shouldFailRequests = true
        viewModel.wakeVehicle()
        advanceUntilIdle()
        assertNotNull(viewModel.uiState.value.errorMessage)

        // Logout
        viewModel.logout()
        assertFalse(viewModel.uiState.value.isAuthenticated)
    }

    @Test
    fun multipleVehicles_requireOneTimeSelection_andSwapResetsPendingState() = runTest {
        val repo = FakeLucidVehicleRepository().apply {
            currentSelectedVehicleId = null
            snapshot = snapshot.copy(powerState = PowerState.CLOUD_1)
            extraVehicles = listOf(
                snapshot.copy(vehicleId = "veh-air-02", nickname = "PE GT", model = VehicleModel.AIR)
            )
        }
        val viewModel = GravityWatchViewModel.Factory(repo).create(GravityWatchViewModel::class.java)
        viewModel.login("owner@lucidmotors.com", "password123", LucidRegion.US)
        advanceUntilIdle()

        // No saved choice with two vehicles -> selector shown, nothing controllable
        assertTrue(viewModel.uiState.value.needsVehicleSelection)
        assertNull(viewModel.uiState.value.activeVehicle)
        viewModel.toggleFrunk()
        advanceUntilIdle()
        assertEquals(0, repo.frunkCommandCount)

        // One-time choice is saved and survives refreshes
        viewModel.selectVehicle("veh-air-02")
        assertEquals("veh-air-02", repo.currentSelectedVehicleId)
        assertFalse(viewModel.uiState.value.needsVehicleSelection)
        assertEquals("PE GT", viewModel.uiState.value.activeVehicle?.nickname)
        viewModel.refreshVehicles()
        advanceUntilIdle()
        assertEquals("veh-air-02", viewModel.uiState.value.activeVehicle?.vehicleId)

        // Swap button reopens the selector; cancel keeps the current car
        viewModel.openVehicleSelector()
        assertTrue(viewModel.uiState.value.needsVehicleSelection)
        viewModel.cancelVehicleSelection()
        assertFalse(viewModel.uiState.value.needsVehicleSelection)
        assertEquals("veh-air-02", viewModel.uiState.value.activeVehicle?.vehicleId)

        // Swapping while a command awaits confirmation drops it and unfreezes the new car
        repo.deferCloudStateUpdateOnCommand = true
        viewModel.toggleFrunk()
        runCurrent()
        assertTrue(viewModel.uiState.value.areControlsLocked)
        viewModel.openVehicleSelector()
        viewModel.selectVehicle("veh-gravity-01")
        assertEquals("veh-gravity-01", repo.currentSelectedVehicleId)
        assertFalse(viewModel.uiState.value.areControlsLocked)
    }

    @Test
    fun chargingCar_inSleepCharge_withAdvancingReports_isReachable_andNotWokenRepeatedly() = runTest {
        // Seen live on a Gravity: SLEEP_CHARGE throughout, but a new report every 1-3 seconds
        val repo = FakeLucidVehicleRepository().apply {
            snapshot = snapshot.copy(powerState = PowerState.SLEEP_CHARGE, chargeState = ChargeState.EVSE_NOT_READY)
            advanceReportOnStateFetchMs = 1_500L
            wakeHasNoEffect = true
        }
        val viewModel = GravityWatchViewModel.Factory(repo) { testScheduler.currentTime }
            .create(GravityWatchViewModel::class.java)
        try {
            viewModel.login("owner@lucidmotors.com", "password123", LucidRegion.US)
            runCurrent()

            viewModel.setInteractivePowerState(true)
            runCurrent()
            // First poll only sets the baseline report time -> one wake request, still frozen
            assertEquals(1, repo.wakeCount)
            assertEquals("Waking car…", viewModel.uiState.value.controlsLockedReason)

            // One new report is not enough...
            advanceTimeBy(1_000L)
            runCurrent()
            assertTrue(viewModel.uiState.value.areControlsLocked)

            // ...two new reports 1.5 s apart make the car reachable while it still says SLEEP_CHARGE
            advanceTimeBy(1_000L)
            runCurrent()
            assertEquals(PowerState.SLEEP_CHARGE, viewModel.uiState.value.activeVehicle?.powerState)
            assertTrue(viewModel.uiState.value.hasLiveReports)
            assertFalse(viewModel.uiState.value.areControlsLocked)
            assertFalse(viewModel.uiState.value.isWakingVehicle)
            assertEquals(GravityWatchViewModel.IDLE_POLL_INTERVAL_MS, viewModel.uiState.value.currentPollIntervalMs)

            // While the reports keep coming, no further wake requests are sent
            advanceTimeBy(90_000L)
            runCurrent()
            assertEquals(1, repo.wakeCount)
            assertFalse(viewModel.uiState.value.areControlsLocked)

            // If the car stops reporting, it is no longer treated as reachable and is woken again
            repo.advanceReportOnStateFetchMs = 0L
            advanceTimeBy(GravityWatchViewModel.LIVE_REPORT_STALE_AFTER_MS + 3_000L)
            runCurrent()
            assertFalse(viewModel.uiState.value.hasLiveReports)
            assertEquals("Waking car…", viewModel.uiState.value.controlsLockedReason)
            assertEquals(2, repo.wakeCount)
        } finally {
            viewModel.suspendPolling()
        }
    }

    @Test
    fun sleepingCar_occasionalCheckIns_doNotCountAsLiveReports() = runTest {
        val repo = FakeLucidVehicleRepository().apply {
            wakeHasNoEffect = true // stays in SLEEP
        }
        val viewModel = GravityWatchViewModel.Factory(repo) { testScheduler.currentTime }
            .create(GravityWatchViewModel::class.java)
        try {
            viewModel.login("owner@lucidmotors.com", "password123", LucidRegion.US)
            runCurrent()
            viewModel.setInteractivePowerState(true)
            runCurrent()
            advanceTimeBy(20_000L)
            runCurrent()
            assertEquals("Waking car…", viewModel.uiState.value.controlsLockedReason)

            // A single check-in moves the report time once -> still not reachable
            repo.snapshot = repo.snapshot.copy(lastUpdatedMs = repo.snapshot.lastUpdatedMs + 150_000L)
            advanceTimeBy(5_000L)
            runCurrent()
            assertFalse(viewModel.uiState.value.hasLiveReports)
            assertTrue(viewModel.uiState.value.areControlsLocked)

            // The next check-in minutes later is too far apart to count either
            repo.snapshot = repo.snapshot.copy(lastUpdatedMs = repo.snapshot.lastUpdatedMs + 150_000L)
            advanceTimeBy(5_000L)
            runCurrent()
            assertFalse(viewModel.uiState.value.hasLiveReports)
            assertEquals("Waking car…", viewModel.uiState.value.controlsLockedReason)
        } finally {
            viewModel.suspendPolling()
        }
    }

    @Test
    fun undocumentedAndUnknownPowerStates_doNotLeaveTheWatchWaiting() = runTest {
        // State 13 (seen on a Gravity) is treated as awake without needing reports
        val gravity = FakeLucidVehicleRepository().apply {
            snapshot = snapshot.copy(powerState = PowerState.UNDOCUMENTED_13, powerStateCode = 13)
        }
        val gravityVm = GravityWatchViewModel.Factory(gravity) { testScheduler.currentTime }
            .create(GravityWatchViewModel::class.java)
        try {
            gravityVm.login("owner@lucidmotors.com", "password123", LucidRegion.US)
            gravityVm.setInteractivePowerState(true)
            runCurrent()
            assertFalse(gravityVm.uiState.value.areControlsLocked)
            assertEquals(0, gravity.wakeCount)
        } finally {
            gravityVm.suspendPolling()
        }

        // An unrecognised value becomes UNKNOWN; live reports still make the car reachable
        val unknown = FakeLucidVehicleRepository().apply {
            snapshot = snapshot.copy(powerState = PowerState.UNKNOWN, powerStateCode = 42)
            advanceReportOnStateFetchMs = 2_000L
            wakeHasNoEffect = true
        }
        val unknownVm = GravityWatchViewModel.Factory(unknown) { testScheduler.currentTime }
            .create(GravityWatchViewModel::class.java)
        try {
            unknownVm.login("owner@lucidmotors.com", "password123", LucidRegion.US)
            unknownVm.setInteractivePowerState(true)
            runCurrent()
            advanceTimeBy(3_000L) // baseline poll + two new reports
            runCurrent()
            assertFalse(unknownVm.uiState.value.areControlsLocked)
            assertEquals(42, unknownVm.uiState.value.activeVehicle?.powerStateCode)
        } finally {
            unknownVm.suspendPolling()
        }
    }

    @Test
    fun singleVehicle_withNoSavedChoice_isUsedAutomatically() = runTest {
        val repo = FakeLucidVehicleRepository().apply {
            currentSelectedVehicleId = null
            snapshot = snapshot.copy(powerState = PowerState.CLOUD_1)
        }
        val viewModel = GravityWatchViewModel.Factory(repo).create(GravityWatchViewModel::class.java)
        try {
            viewModel.login("owner@lucidmotors.com", "password123", LucidRegion.US)
            runCurrent()

            assertFalse(viewModel.uiState.value.needsVehicleSelection)
            assertEquals("veh-gravity-01", viewModel.uiState.value.activeVehicle?.vehicleId)
            assertFalse(viewModel.uiState.value.areControlsLocked)

            viewModel.setInteractivePowerState(true)
            runCurrent()
            assertEquals(1, repo.fetchStateCount) // polls the single car directly
            viewModel.honkHorn()
            runCurrent()
            assertEquals(1, repo.honkedHornCount)
        } finally {
            viewModel.suspendPolling()
        }
    }

    @Test
    fun restoredSession_fetchesVehicleListOnce_beforePollingTheCar() = runTest {
        val repo = FakeLucidVehicleRepository().apply {
            snapshot = snapshot.copy(powerState = PowerState.CLOUD_1)
        }
        repo.login("owner@lucidmotors.com", "password123", LucidRegion.US) // saved session
        val listGate = CompletableDeferred<Unit>()
        repo.fetchGate = listGate

        // App start: restoring the session starts the vehicle-list fetch...
        val viewModel = GravityWatchViewModel.Factory(repo).create(GravityWatchViewModel::class.java)
        // ...and becoming interactive must not start a second one while it is in flight
        try {
            viewModel.setInteractivePowerState(true)
            runCurrent()
            assertEquals(1, repo.fetchVehiclesCount)

            repo.fetchGate = null
            listGate.complete(Unit)
            runCurrent()
            assertEquals(1, repo.fetchVehiclesCount)
            assertNotNull(viewModel.uiState.value.activeVehicle)

            // Later polls use the per-car state call
            advanceTimeBy(GravityWatchViewModel.IDLE_POLL_INTERVAL_MS)
            runCurrent()
            assertEquals(1, repo.fetchVehiclesCount)
            assertTrue(repo.fetchStateCount >= 1)
        } finally {
            viewModel.suspendPolling()
        }
    }

    @Test
    fun interactivePolling_wakesSleepingCar_pollsFastAfterCommand_slowsInAmbient_andStopsWhenSuspended() = runTest {
        val repo = FakeLucidVehicleRepository().apply {
            deferCloudStateUpdateOnCommand = true
        }
        val viewModel = GravityWatchViewModel.Factory(repo).create(GravityWatchViewModel::class.java)
        runCurrent()

        viewModel.login("owner@lucidmotors.com", "password123", LucidRegion.US)
        runCurrent()
        // Car is asleep, but the watch is not interactive yet -> not woken, controls frozen
        assertEquals(0, repo.wakeCount)
        assertEquals("Car asleep", viewModel.uiState.value.controlsLockedReason)

        // 1. Interactive -> polls the selected car (GetVehicleState) immediately, yellow dot while in flight
        val gate = CompletableDeferred<Unit>()
        repo.fetchGate = gate
        viewModel.setInteractivePowerState(true)
        runCurrent()
        assertTrue(viewModel.uiState.value.isPolling)
        assertEquals(1, repo.fetchStateCount)
        assertEquals(0, repo.fetchVehiclesCount)

        // Poll finds the car asleep -> wake sent, polling every second while waking
        repo.fetchGate = null
        gate.complete(Unit)
        runCurrent()
        assertFalse(viewModel.uiState.value.isPolling)
        assertEquals(1, repo.wakeCount)
        assertEquals("Waking car…", viewModel.uiState.value.controlsLockedReason)
        assertEquals(1_000L, viewModel.uiState.value.currentPollIntervalMs)

        // 2. Next poll sees the car awake -> controls unfreeze, idle polling every 3 seconds
        advanceTimeBy(1_000L)
        runCurrent()
        assertEquals(2, repo.fetchStateCount)
        assertFalse(viewModel.uiState.value.areControlsLocked)
        assertEquals(3_000L, viewModel.uiState.value.currentPollIntervalMs)

        advanceTimeBy(2_999L)
        runCurrent()
        assertEquals(2, repo.fetchStateCount)
        advanceTimeBy(1L)
        runCurrent()
        assertEquals(3, repo.fetchStateCount)

        // 3. Press Frunk -> command sent, controls freeze, immediate check, then every second
        viewModel.toggleFrunk()
        runCurrent()
        assertEquals(1, repo.frunkCommandCount)
        assertEquals(4, repo.fetchStateCount) // post-command fetch
        assertEquals("Opening Frunk…", viewModel.uiState.value.controlsLockedReason)
        assertEquals(1_000L, viewModel.uiState.value.currentPollIntervalMs)
        // No optimistic update: the displayed state is what Lucid Cloud reports
        assertEquals(DoorState.CLOSED, viewModel.uiState.value.activeVehicle?.frunkState)

        // Further presses are ignored while frozen
        viewModel.toggleFrunk()
        runCurrent()
        assertEquals(1, repo.frunkCommandCount)

        advanceTimeBy(1_000L)
        runCurrent()
        assertEquals(5, repo.fetchStateCount)
        assertTrue(viewModel.uiState.value.areControlsLocked)

        // Cloud now reflects frunk OPEN -> the next one-second poll unfreezes the controls
        repo.snapshot = repo.snapshot.copy(frunkState = DoorState.OPEN, batteryPercent = 82.0)
        advanceTimeBy(1_000L)
        runCurrent()
        assertEquals(6, repo.fetchStateCount)
        assertEquals(DoorState.OPEN, viewModel.uiState.value.activeVehicle?.frunkState)
        assertEquals(82.0, viewModel.uiState.value.activeVehicle?.batteryPercent ?: 0.0, 0.01)
        assertFalse(viewModel.uiState.value.areControlsLocked)

        // After the 10-second fast window, polling returns to every 3 seconds
        advanceTimeBy(GravityWatchViewModel.FAST_POLL_WINDOW_MS)
        runCurrent()
        assertEquals(3_000L, viewModel.uiState.value.currentPollIntervalMs)
        val idleCount = repo.fetchStateCount
        advanceTimeBy(3_000L)
        runCurrent()
        assertEquals(idleCount + 1, repo.fetchStateCount)

        // 4. Ambient (display dimmed) -> polls every 30 seconds without the yellow dot
        viewModel.setInteractivePowerState(false)
        runCurrent()
        assertTrue(viewModel.uiState.value.isAmbient)
        assertFalse(viewModel.uiState.value.isPolling)
        advanceTimeBy(3_000L) // poll already scheduled on the interactive cadence
        runCurrent()
        val ambientCount = repo.fetchStateCount
        assertEquals(30_000L, viewModel.uiState.value.currentPollIntervalMs)

        // Exactly one poll per 30 seconds from here on, with no yellow dot while in flight
        advanceTimeBy(30_000L)
        runCurrent()
        assertEquals(ambientCount + 1, repo.fetchStateCount)
        val ambientGate = CompletableDeferred<Unit>()
        repo.fetchGate = ambientGate
        advanceTimeBy(30_000L)
        runCurrent()
        assertEquals(ambientCount + 2, repo.fetchStateCount)
        assertFalse(viewModel.uiState.value.isPolling)
        repo.fetchGate = null
        ambientGate.complete(Unit)
        runCurrent()

        // 5. Suspending polling (activity stopped) stops the loop entirely
        viewModel.suspendPolling()
        runCurrent()
        assertFalse(viewModel.uiState.value.isInteractive)
        assertFalse(viewModel.uiState.value.isAmbient)
        val suspendedCount = repo.fetchStateCount
        advanceTimeBy(120_000L)
        runCurrent()
        assertEquals(suspendedCount, repo.fetchStateCount)
        // Only GetVehicleState was polled; the vehicle list came from login
        assertEquals(0, repo.fetchVehiclesCount)
    }

    @Test
    fun rateLimitedPoll_showsShortMessage_andBacksOffUntilBackoffEnds() = runTest {
        val repo = FakeLucidVehicleRepository().apply {
            snapshot = snapshot.copy(powerState = PowerState.CLOUD_1)
        }
        val viewModel = GravityWatchViewModel.Factory(repo).create(GravityWatchViewModel::class.java)
        viewModel.login("owner@lucidmotors.com", "password123", LucidRegion.US)
        runCurrent()

        repo.nextStateFetchError = LucidApiException(8, "Lucid Cloud API Error (8): gRPC status code 8")
        viewModel.setInteractivePowerState(true)
        runCurrent()
        assertTrue(viewModel.uiState.value.isRateLimited)
        assertEquals("Rate limited, retrying", viewModel.uiState.value.errorMessage)
        assertTrue(viewModel.uiState.value.areControlsLocked)
        assertEquals(GravityWatchViewModel.RATE_LIMITED_POLL_INTERVAL_MS, viewModel.uiState.value.currentPollIntervalMs)

        // Next (slow) poll succeeds -> message clears, but polling stays slow until the backoff ends
        advanceTimeBy(GravityWatchViewModel.RATE_LIMITED_POLL_INTERVAL_MS)
        runCurrent()
        assertFalse(viewModel.uiState.value.isRateLimited)
        assertNull(viewModel.uiState.value.errorMessage)
        assertFalse(viewModel.uiState.value.areControlsLocked)
        assertEquals(GravityWatchViewModel.RATE_LIMITED_POLL_INTERVAL_MS, viewModel.uiState.value.currentPollIntervalMs)

        advanceTimeBy(GravityWatchViewModel.RATE_LIMIT_BACKOFF_MS)
        runCurrent()
        assertEquals(GravityWatchViewModel.IDLE_POLL_INTERVAL_MS, viewModel.uiState.value.currentPollIntervalMs)
        viewModel.suspendPolling()
    }

    @Test
    fun frunkCommand_notConfirmedByCar_unfreezesControlsWithErrorAfterTimeout() = runTest {
        val repo = FakeLucidVehicleRepository().apply {
            deferCloudStateUpdateOnCommand = true
            snapshot = snapshot.copy(powerState = PowerState.CLOUD_1)
        }
        val viewModel = GravityWatchViewModel.Factory(repo).create(GravityWatchViewModel::class.java)
        viewModel.login("owner@lucidmotors.com", "password123", LucidRegion.US)
        runCurrent()
        try {
            viewModel.setInteractivePowerState(true)
            runCurrent()
            assertFalse(viewModel.uiState.value.areControlsLocked)

            viewModel.toggleFrunk()
            runCurrent()
            assertEquals("Opening Frunk…", viewModel.uiState.value.controlsLockedReason)

            advanceTimeBy(GravityWatchViewModel.STATE_CONFIRMATION_TIMEOUT_MS - 1)
            runCurrent()
            assertTrue(viewModel.uiState.value.areControlsLocked)

            advanceTimeBy(1L)
            runCurrent()
            assertFalse(viewModel.uiState.value.areControlsLocked)
            assertEquals("Opening Frunk not confirmed by car", viewModel.uiState.value.errorMessage)
            assertEquals(DoorState.CLOSED, viewModel.uiState.value.activeVehicle?.frunkState)
        } finally {
            viewModel.suspendPolling()
        }
    }

    @Test
    fun climateCommand_keepsControlsFrozenUntilCarReportsClimateOn() = runTest {
        val repo = FakeLucidVehicleRepository().apply {
            deferCloudStateUpdateOnCommand = true
            snapshot = snapshot.copy(powerState = PowerState.CLOUD_1)
        }
        val viewModel = GravityWatchViewModel.Factory(repo).create(GravityWatchViewModel::class.java)
        viewModel.login("owner@lucidmotors.com", "password123", LucidRegion.US)
        runCurrent()
        try {
            viewModel.setInteractivePowerState(true)
            runCurrent()

            viewModel.setCabinTargetTemperature(23.0)
            runCurrent()
            assertEquals(1, repo.climateCommandCount)
            assertEquals("Starting Climate…", viewModel.uiState.value.controlsLockedReason)

            // A poll that still shows climate off keeps the controls frozen
            advanceTimeBy(GravityWatchViewModel.FAST_POLL_INTERVAL_MS)
            runCurrent()
            assertTrue(viewModel.uiState.value.areControlsLocked)

            // The car reports climate on at roughly the requested temperature -> confirmed
            repo.snapshot = repo.snapshot.copy(hvacPower = HvacPower.ON, targetTempCelsius = 23.0)
            advanceTimeBy(GravityWatchViewModel.FAST_POLL_INTERVAL_MS)
            runCurrent()
            assertFalse(viewModel.uiState.value.areControlsLocked)
            assertNull(viewModel.uiState.value.errorMessage)
        } finally {
            viewModel.suspendPolling()
        }
    }

    @Test
    fun airSteeringHeater_togglesOnAndOff_usingLevel2ForOn() = runTest {
        val repo = FakeLucidVehicleRepository().apply {
            snapshot = snapshot.copy(
                model = VehicleModel.AIR,
                powerState = PowerState.CLOUD_1,
                steeringHeaterLevel = SteeringWheelHeaterLevel.UNKNOWN,
                steeringHeaterStatus = SteeringHeaterStatus.OFF
            )
        }
        val viewModel = GravityWatchViewModel.Factory(repo).create(GravityWatchViewModel::class.java)
        viewModel.login("owner@lucidmotors.com", "password123", LucidRegion.US)
        advanceUntilIdle()

        viewModel.tapSteeringWheel()
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.activeVehicle!!.isSteeringHeaterOn)
        assertFalse(viewModel.uiState.value.areControlsLocked)

        viewModel.tapSteeringWheel()
        advanceUntilIdle()
        assertFalse(viewModel.uiState.value.activeVehicle!!.isSteeringHeaterOn)
        assertEquals(
            listOf(SteeringWheelHeaterLevel.LEVEL_2, SteeringWheelHeaterLevel.OFF),
            repo.steeringLevelsSent
        )
    }

    private fun seatTestRepo() = FakeLucidVehicleRepository().apply {
        snapshot = snapshot.copy(
            powerState = PowerState.CLOUD_1,
            driverSeatHeatMode = SeatClimateMode.OFF,
            driverSeatVentMode = SeatClimateMode.OFF,
            passengerSeatHeatMode = SeatClimateMode.OFF,
            passengerSeatVentMode = SeatClimateMode.OFF,
            rearSeatHeatMode = SeatClimateMode.OFF,
            hasHeatedRearSeats = true,
            hasVentilatedFrontSeats = true
        )
    }

    @Test
    fun seatTaps_areQueuedAndSentAsOneCommandOnceTapsStop() = runTest {
        val repo = seatTestRepo()
        val viewModel = GravityWatchViewModel.Factory(repo).create(GravityWatchViewModel::class.java)
        viewModel.login("owner@lucidmotors.com", "password123", LucidRegion.US)
        advanceUntilIdle()

        // Driver: Off -> High -> Med. Rear: Off -> High. Shown at once, nothing sent yet.
        viewModel.tapSeat(ClimateSeat.DRIVER, SeatClimateKind.HEAT)
        viewModel.tapSeat(ClimateSeat.DRIVER, SeatClimateKind.HEAT)
        viewModel.tapSeat(ClimateSeat.REAR, SeatClimateKind.HEAT)
        val draft = viewModel.uiState.value.seatDraft!!
        assertEquals(SeatClimateMode.MEDIUM, draft.driverHeat)
        assertEquals(SeatClimateMode.HIGH, draft.rearHeat)
        advanceTimeBy(GravityWatchViewModel.SEAT_TAP_SETTLE_MS - 500)
        runCurrent()
        assertTrue(repo.rearHeatSent.isEmpty())

        // Another tap restarts the wait
        viewModel.tapSeat(ClimateSeat.PASSENGER, SeatClimateKind.COOL)
        advanceTimeBy(GravityWatchViewModel.SEAT_TAP_SETTLE_MS - 100)
        runCurrent()
        assertTrue(repo.rearHeatSent.isEmpty())

        advanceUntilIdle()
        assertEquals(listOf(SeatClimateMode.HIGH), repo.rearHeatSent)
        assertEquals(listOf(SeatClimateMode.HIGH), repo.passengerVentSent)
        val car = viewModel.uiState.value.activeVehicle!!
        assertEquals(SeatClimateMode.MEDIUM, car.driverSeatHeatMode)
        assertEquals(SeatClimateMode.HIGH, car.rearSeatHeatMode)
        assertEquals(SeatClimateMode.HIGH, car.passengerSeatVentMode)
        // Confirmed by the car: the draft is gone and the controls are free again
        assertNull(viewModel.uiState.value.seatDraft)
        assertFalse(viewModel.uiState.value.areControlsLocked)
    }

    @Test
    fun seatTaps_backToTheCarsLevel_sendNothing() = runTest {
        val repo = seatTestRepo()
        val viewModel = GravityWatchViewModel.Factory(repo).create(GravityWatchViewModel::class.java)
        viewModel.login("owner@lucidmotors.com", "password123", LucidRegion.US)
        advanceUntilIdle()

        // High, Med, Low, Off: a full cycle ends where the car already is
        repeat(4) { viewModel.tapSeat(ClimateSeat.DRIVER, SeatClimateKind.HEAT) }
        advanceUntilIdle()
        assertTrue(repo.rearHeatSent.isEmpty())
        assertNull(viewModel.uiState.value.seatDraft)
    }

    @Test
    fun coolingAHeatedSeat_turnsItsHeatOff() = runTest {
        val repo = seatTestRepo().apply {
            snapshot = snapshot.copy(driverSeatHeatMode = SeatClimateMode.HIGH)
        }
        val viewModel = GravityWatchViewModel.Factory(repo).create(GravityWatchViewModel::class.java)
        viewModel.login("owner@lucidmotors.com", "password123", LucidRegion.US)
        advanceUntilIdle()

        viewModel.tapSeat(ClimateSeat.DRIVER, SeatClimateKind.COOL)
        advanceUntilIdle()
        val car = viewModel.uiState.value.activeVehicle!!
        assertEquals(SeatClimateMode.HIGH, car.driverSeatVentMode)
        assertEquals(SeatClimateMode.OFF, car.driverSeatHeatMode)
        // Unchanged rear heat and passenger cooling are not sent
        assertEquals(listOf<SeatClimateMode?>(null), repo.rearHeatSent)
        assertEquals(listOf<SeatClimateMode?>(null), repo.passengerVentSent)
    }

    private fun airClimateRepo(climateOn: Boolean = false) = seatTestRepo().apply {
        airClimateSideEffects = true
        snapshot = snapshot.copy(
            hvacPower = if (climateOn) HvacPower.ON else HvacPower.OFF,
            maxAcEnabled = false,
            defrostState = DefrostState.OFF
        )
    }

    @Test
    fun maxAcOff_putsBackWhatMaxAcChanged() = runTest {
        val repo = airClimateRepo()
        val viewModel = GravityWatchViewModel.Factory(repo).create(GravityWatchViewModel::class.java)
        viewModel.login("owner@lucidmotors.com", "password123", LucidRegion.US)
        advanceUntilIdle()

        viewModel.toggleMaxAc()
        advanceUntilIdle()
        var car = viewModel.uiState.value.activeVehicle!!
        assertTrue(car.maxAcEnabled)
        assertTrue(car.hvacPower.isActive)
        assertEquals(SeatClimateMode.HIGH, car.driverSeatVentMode)

        // One tap off: Max AC off and climate back off, which also ends the seat cooling
        viewModel.toggleMaxAc()
        advanceUntilIdle()
        car = viewModel.uiState.value.activeVehicle!!
        assertFalse(car.maxAcEnabled)
        assertEquals(SeatClimateMode.OFF, car.driverSeatVentMode)
        assertFalse(car.hvacPower.isActive)
        assertFalse(viewModel.uiState.value.areControlsLocked)
        assertNull(viewModel.uiState.value.errorMessage)
    }

    @Test
    fun maxAcOff_leavesClimateOn_whenItWasOnBefore() = runTest {
        val repo = airClimateRepo(climateOn = true)
        val viewModel = GravityWatchViewModel.Factory(repo).create(GravityWatchViewModel::class.java)
        viewModel.login("owner@lucidmotors.com", "password123", LucidRegion.US)
        advanceUntilIdle()

        viewModel.toggleMaxAc()
        advanceUntilIdle()
        val climateCommandsBefore = repo.climateCommandCount
        viewModel.toggleMaxAc()
        advanceUntilIdle()
        val car = viewModel.uiState.value.activeVehicle!!
        assertFalse(car.maxAcEnabled)
        assertTrue(car.hvacPower.isActive)
        assertEquals(climateCommandsBefore, repo.climateCommandCount)
    }

    @Test
    fun maxAcOff_leavesClimateOn_whenTheUserChangedClimateMeanwhile() = runTest {
        val repo = airClimateRepo()
        val viewModel = GravityWatchViewModel.Factory(repo).create(GravityWatchViewModel::class.java)
        viewModel.login("owner@lucidmotors.com", "password123", LucidRegion.US)
        advanceUntilIdle()

        viewModel.toggleMaxAc()
        advanceUntilIdle()
        viewModel.setCabinTargetTemperature(20.0)
        advanceUntilIdle()
        viewModel.toggleMaxAc()
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.activeVehicle!!.hvacPower.isActive)
    }

    @Test
    fun defrostOff_turnsClimateBackOff_unlessTheUserTurnedItOn() = runTest {
        val repo = airClimateRepo()
        val viewModel = GravityWatchViewModel.Factory(repo).create(GravityWatchViewModel::class.java)
        viewModel.login("owner@lucidmotors.com", "password123", LucidRegion.US)
        advanceUntilIdle()

        viewModel.toggleDefrost()
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.activeVehicle!!.hvacPower.isActive)
        viewModel.toggleDefrost()
        advanceUntilIdle()
        assertEquals(DefrostState.OFF, viewModel.uiState.value.activeVehicle!!.defrostState)
        assertFalse(viewModel.uiState.value.activeVehicle!!.hvacPower.isActive)

        // Without a restore point (defrost turned on elsewhere) only defrost goes off
        repo.snapshot = repo.snapshot.copy(defrostState = DefrostState.ON, hvacPower = HvacPower.PRECONDITION)
        viewModel.refreshVehicles()
        advanceUntilIdle()
        viewModel.toggleDefrost()
        advanceUntilIdle()
        assertEquals(DefrostState.OFF, viewModel.uiState.value.activeVehicle!!.defrostState)
        assertTrue(viewModel.uiState.value.activeVehicle!!.hvacPower.isActive)
    }

    @Test
    fun startCharging_releasesControlsAtPreparing_butKeepsPollingFastUntilCharging() = runTest {
        val repo = FakeLucidVehicleRepository().apply {
            snapshot = snapshot.copy(powerState = PowerState.CLOUD_1, chargeState = ChargeState.CABLE_CONNECTED)
            deferCloudStateUpdateOnCommand = true
        }
        val viewModel = GravityWatchViewModel.Factory(repo).create(GravityWatchViewModel::class.java)
        viewModel.login("owner@lucidmotors.com", "password123", LucidRegion.US)
        advanceUntilIdle()
        viewModel.setInteractivePowerState(true)
        try {
            runCurrent()
            viewModel.toggleCharging()
            runCurrent()
            assertTrue(viewModel.uiState.value.areControlsLocked)

            // The car starts preparing: the command is taken, the controls are free again...
            repo.snapshot = repo.snapshot.copy(chargeState = ChargeState.CHARGER_PREPARATION)
            advanceTimeBy(GravityWatchViewModel.FAST_POLL_INTERVAL_MS)
            runCurrent()
            assertFalse(viewModel.uiState.value.areControlsLocked)

            // ...but polling stays fast past the usual window while it prepares
            advanceTimeBy(GravityWatchViewModel.FAST_POLL_WINDOW_MS + 5_000)
            runCurrent()
            assertEquals(GravityWatchViewModel.FAST_POLL_INTERVAL_MS, viewModel.uiState.value.currentPollIntervalMs)

            // Charging: back to the normal interval
            repo.snapshot = repo.snapshot.copy(chargeState = ChargeState.CHARGING)
            advanceTimeBy(GravityWatchViewModel.FAST_POLL_INTERVAL_MS)
            runCurrent()
            assertEquals(GravityWatchViewModel.IDLE_POLL_INTERVAL_MS, viewModel.uiState.value.currentPollIntervalMs)
        } finally {
            viewModel.suspendPolling()
        }
    }

    @Test
    fun startCharging_isIgnoredWithoutChargeCable() = runTest {
        val repo = FakeLucidVehicleRepository().apply {
            snapshot = snapshot.copy(powerState = PowerState.CLOUD_1, chargeState = ChargeState.NOT_CONNECTED)
        }
        val viewModel = GravityWatchViewModel.Factory(repo).create(GravityWatchViewModel::class.java)
        viewModel.login("owner@lucidmotors.com", "password123", LucidRegion.US)
        advanceUntilIdle()

        viewModel.toggleCharging()
        advanceUntilIdle()
        assertEquals(0, repo.chargingCommandCount)
        assertFalse(viewModel.uiState.value.areControlsLocked)
    }

    @Test
    fun commandConfirmedWhileScreenWasOff_showsConfirmedStateOnReturn_withoutTimeoutError() = runTest {
        val repo = FakeLucidVehicleRepository().apply {
            deferCloudStateUpdateOnCommand = true
            snapshot = snapshot.copy(powerState = PowerState.CLOUD_1)
        }
        val viewModel = GravityWatchViewModel.Factory(repo).create(GravityWatchViewModel::class.java)
        viewModel.login("owner@lucidmotors.com", "password123", LucidRegion.US)
        runCurrent()
        try {
            viewModel.setInteractivePowerState(true)
            runCurrent()
            viewModel.toggleFrunk()
            runCurrent()

            // Wrist lowered: the activity stops and polling is suspended
            advanceTimeBy(5_000L)
            viewModel.suspendPolling()
            runCurrent()
            // The car opens the frunk while the screen is off
            repo.snapshot = repo.snapshot.copy(frunkState = DoorState.OPEN)

            // Five minutes with the screen off: the timeout does not run down
            advanceTimeBy(5 * 60_000L)
            runCurrent()
            assertNull(viewModel.uiState.value.errorMessage)
            assertEquals("Opening Frunk…", viewModel.uiState.value.controlsLockedReason)

            // Raising the wrist polls immediately and confirms
            viewModel.setInteractivePowerState(true)
            runCurrent()
            assertEquals(DoorState.OPEN, viewModel.uiState.value.activeVehicle?.frunkState)
            assertFalse(viewModel.uiState.value.areControlsLocked)
            assertNull(viewModel.uiState.value.errorMessage)
        } finally {
            viewModel.suspendPolling()
        }
    }

    @Test
    fun confirmationTimeout_countsOnlyTimeWhileAppIsPolling() = runTest {
        val repo = FakeLucidVehicleRepository().apply {
            deferCloudStateUpdateOnCommand = true // the car never confirms
            snapshot = snapshot.copy(powerState = PowerState.CLOUD_1)
        }
        val viewModel = GravityWatchViewModel.Factory(repo).create(GravityWatchViewModel::class.java)
        viewModel.login("owner@lucidmotors.com", "password123", LucidRegion.US)
        runCurrent()
        try {
            viewModel.setInteractivePowerState(true)
            runCurrent()
            viewModel.toggleFrunk()
            runCurrent()

            // 30 s on screen, then 10 minutes off screen
            advanceTimeBy(30_000L)
            viewModel.suspendPolling()
            advanceTimeBy(10 * 60_000L)
            runCurrent()
            assertTrue(viewModel.uiState.value.areControlsLocked)
            assertNull(viewModel.uiState.value.errorMessage)

            // Back on screen: the remaining ~15 s of polling time then times out
            viewModel.setInteractivePowerState(true)
            runCurrent()
            advanceTimeBy(14_000L)
            runCurrent()
            assertTrue(viewModel.uiState.value.areControlsLocked)
            advanceTimeBy(2_000L)
            runCurrent()
            assertFalse(viewModel.uiState.value.areControlsLocked)
            assertEquals("Opening Frunk not confirmed by car", viewModel.uiState.value.errorMessage)
        } finally {
            viewModel.suspendPolling()
        }
    }

    @Test
    fun failedTelemetryFetch_freezesControlsUntilNextSuccessfulFetch() = runTest {
        val repo = FakeLucidVehicleRepository().apply {
            snapshot = snapshot.copy(powerState = PowerState.CLOUD_1)
        }
        val viewModel = GravityWatchViewModel.Factory(repo).create(GravityWatchViewModel::class.java)
        viewModel.login("owner@lucidmotors.com", "password123", LucidRegion.US)
        advanceUntilIdle()
        assertFalse(viewModel.uiState.value.areControlsLocked)

        repo.shouldFailRequests = true
        viewModel.refreshVehicles()
        advanceUntilIdle()
        assertEquals("Reading car status…", viewModel.uiState.value.controlsLockedReason)
        viewModel.honkHorn()
        advanceUntilIdle()
        assertEquals(0, repo.honkedHornCount)

        repo.shouldFailRequests = false
        viewModel.refreshVehicles()
        advanceUntilIdle()
        assertFalse(viewModel.uiState.value.areControlsLocked)
        viewModel.honkHorn()
        advanceUntilIdle()
        assertEquals(1, repo.honkedHornCount)
    }
}
