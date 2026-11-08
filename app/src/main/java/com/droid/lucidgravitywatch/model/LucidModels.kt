package com.droid.lucidgravitywatch.model

import kotlin.math.roundToInt

enum class LucidRegion(val code: String, val label: String, val apiHost: String) {
    US("us", "United States", "mobile.deneb.prod.infotainment.pdx.atieva.com"),
    EU("eu", "Europe", "mobile.do.prod.eu.lcid.io"),
    SA("sa", "Saudi Arabia", "mobile.ksag.prod.do.lucidcars.io"),
    ME("me", "Middle East", "mobile.ksap.prod.do.lucidcars.io");

    companion object {
        fun fromCode(code: String?): LucidRegion =
            entries.firstOrNull { it.code.equals(code, ignoreCase = true) } ?: US
    }
}

enum class VehicleModel(val protoValue: Int, val displayName: String) {
    UNKNOWN(0, "Lucid"),
    AIR(1, "Air"),
    GRAVITY(2, "Gravity");

    companion object {
        fun fromProto(value: Int): VehicleModel =
            entries.firstOrNull { it.protoValue == value } ?: UNKNOWN
    }
}

enum class LockState(val protoValue: Int, val displayName: String) {
    UNKNOWN(0, "Unknown"),
    UNLOCKED(1, "Unlocked"),
    LOCKED(2, "Locked");

    companion object {
        fun fromProto(value: Int): LockState =
            entries.firstOrNull { it.protoValue == value } ?: UNKNOWN
    }
}

enum class DoorState(val protoValue: Int, val displayName: String) {
    UNKNOWN(0, "Unknown"),
    OPEN(1, "Open"),
    CLOSED(2, "Closed"),
    AJAR(3, "Ajar"),
    CLOSE_ERROR(4, "Close Error");

    val isOpen: Boolean
        get() = this == OPEN || this == AJAR

    companion object {
        fun fromProto(value: Int): DoorState =
            entries.firstOrNull { it.protoValue == value } ?: UNKNOWN
    }
}

enum class WalkawayState(val protoValue: Int, val displayName: String) {
    UNKNOWN(0, "Unknown"),
    IDLE(1, "Idle"),
    ACTIVE(2, "Walk-Away Active"),
    DISABLE(3, "Walk-Away Off");

    companion object {
        fun fromProto(value: Int): WalkawayState =
            entries.firstOrNull { it.protoValue == value } ?: UNKNOWN
    }
}

enum class PowerState(val protoValue: Int, val displayName: String) {
    UNKNOWN(0, "Unknown"),
    SLEEP(1, "Sleep"),
    WINK(2, "Wink"),
    ACCESSORY(3, "Accessory"),
    DRIVE(4, "Drive"),
    LIVE_CHARGE(5, "Charging"),
    SLEEP_CHARGE(6, "Sleep Charge"),
    LIVE_UPDATE(7, "Updating"),
    SLEEP_UPDATE(8, "Sleep Update"),
    CLOUD_1(9, "Awake"),
    CLOUD_2(10, "Awake"),
    MONITOR(11, "Monitor"),

    // 12 and 13 are not in Lucid's published PowerState enum (it ends at 11). A Gravity has been
    // seen reporting 13 intermittently, apparently as a monitor/keep-alive state, so neither is
    // treated as asleep.
    UNDOCUMENTED_12(12, "State 12"),
    UNDOCUMENTED_13(13, "State 13");

    /** The documented sleep states: the car's main systems are off. */
    val isSleeping: Boolean
        get() = this == SLEEP || this == SLEEP_CHARGE || this == SLEEP_UPDATE

    /**
     * True when the state itself says the car is awake enough to act on remote commands. A car in
     * a sleep state (notably [SLEEP_CHARGE]) or [UNKNOWN] may still be reachable; the view model
     * also treats a car whose reports keep arriving as reachable.
     */
    val isAwake: Boolean
        get() = !isSleeping && this != UNKNOWN

    companion object {
        fun fromProto(value: Int): PowerState =
            entries.firstOrNull { it.protoValue == value } ?: UNKNOWN
    }
}

enum class ChargeState(val protoValue: Int, val displayName: String) {
    UNKNOWN(0, "Unknown"),
    NOT_CONNECTED(1, "Unplugged"),
    CABLE_CONNECTED(2, "Plugged In"),
    ESTABLISHING_SESSION(3, "Connecting"),
    AUTHORIZING_PNC(4, "Authorizing PnC"),
    AUTHORIZING_EXTERNAL(5, "Authorizing"),
    AUTHORIZED(6, "Authorized"),
    CHARGER_PREPARATION(7, "Preparing"),
    CHARGING(8, "Charging"),
    CHARGING_END_OK(9, "Complete"),
    CHARGING_END_ERROR(10, "Charge Error"),
    CHARGING_STOPPED(13, "Stopped"),
    CHARGING_PAUSED(14, "Paused"),
    DISCHARGING(19, "Discharging"),
    EVSE_NOT_READY(25, "Plugged In"),
    CHARGING_SCHEDULED(30, "Scheduled");

    val isActivelyCharging: Boolean
        get() = this == CHARGING

    val isCablePluggedIn: Boolean
        get() = this != UNKNOWN && this != NOT_CONNECTED

    companion object {
        fun fromProto(value: Int): ChargeState =
            entries.firstOrNull { it.protoValue == value } ?: UNKNOWN
    }
}

enum class ChargeAction(val protoValue: Int) {
    UNKNOWN(0),
    START(1),
    STOP(2)
}

enum class HvacPower(val protoValue: Int, val displayName: String) {
    UNKNOWN(0, "Unknown"),
    ON(1, "On"),
    OFF(2, "Off"),
    PRECONDITION(3, "Preconditioning"),
    RESIDUAL_HEATING(5, "Residual Heat"),
    KEEP_TEMP(6, "Keep Climate"),
    HEATSTROKE_PREVENTION(7, "Cabin Overheat Prot");

    val isActive: Boolean
        get() = this == ON || this == PRECONDITION || this == KEEP_TEMP

    companion object {
        fun fromProto(value: Int): HvacPower =
            entries.firstOrNull { it.protoValue == value } ?: UNKNOWN
    }
}

enum class DefrostState(val protoValue: Int, val displayName: String) {
    UNKNOWN(0, "Unknown"),
    ON(1, "Defrost On"),
    OFF(2, "Defrost Off");

    val isEnabled: Boolean
        get() = this == ON

    companion object {
        fun fromProto(value: Int): DefrostState =
            entries.firstOrNull { it.protoValue == value } ?: UNKNOWN
    }
}

enum class BatteryPreconStatus(val protoValue: Int, val displayName: String) {
    UNKNOWN(0, "Unknown"),
    OFF(1, "Off"),
    ON(2, "Preconditioning"),
    UNAVAILABLE(3, "Unavailable");

    val isEnabled: Boolean
        get() = this == ON

    companion object {
        fun fromProto(value: Int): BatteryPreconStatus =
            entries.firstOrNull { it.protoValue == value } ?: UNKNOWN
    }
}

enum class SeatClimateMode(val protoValue: Int, val displayName: String, val bars: Int) {
    UNKNOWN(0, "Off", 0),
    OFF(2, "Off", 0),
    LOW(3, "Low", 1),
    MEDIUM(4, "Med", 2),
    HIGH(5, "High", 3);

    val isOn: Boolean
        get() = bars > 0

    /** The seat diagram's tap order, as in the Lucid app: Off -> High -> Med -> Low -> Off. */
    fun nextTap(): SeatClimateMode = when (this) {
        UNKNOWN, OFF -> HIGH
        HIGH -> MEDIUM
        MEDIUM -> LOW
        LOW -> OFF
    }

    companion object {
        fun fromProto(value: Int): SeatClimateMode =
            entries.firstOrNull { it.protoValue == value } ?: OFF
    }
}

/** The seats the watch controls; the rear seats share one (heat-only) setting. */
enum class ClimateSeat(val label: String) {
    DRIVER("Driver"),
    PASSENGER("Passenger"),
    REAR("Rear")
}

enum class SeatClimateKind(val label: String) {
    HEAT("Heat"),
    COOL("Cooling")
}

/**
 * Heat and ventilation levels for the seats the watch controls. A seat only runs one of the two
 * at a time; rear seats have heat only.
 */
data class SeatClimateSettings(
    val driverHeat: SeatClimateMode,
    val driverVent: SeatClimateMode,
    val passengerHeat: SeatClimateMode,
    val passengerVent: SeatClimateMode,
    val rearHeat: SeatClimateMode
) {
    fun level(seat: ClimateSeat, kind: SeatClimateKind): SeatClimateMode = when (kind) {
        SeatClimateKind.HEAT -> when (seat) {
            ClimateSeat.DRIVER -> driverHeat
            ClimateSeat.PASSENGER -> passengerHeat
            ClimateSeat.REAR -> rearHeat
        }
        SeatClimateKind.COOL -> when (seat) {
            ClimateSeat.DRIVER -> driverVent
            ClimateSeat.PASSENGER -> passengerVent
            ClimateSeat.REAR -> SeatClimateMode.OFF
        }
    }

    /** Sets one seat's heat or cooling; turning one on turns the other off for that seat. */
    fun with(seat: ClimateSeat, kind: SeatClimateKind, mode: SeatClimateMode): SeatClimateSettings {
        val other = if (mode.isOn) SeatClimateMode.OFF else null
        return when (seat) {
            ClimateSeat.DRIVER -> if (kind == SeatClimateKind.HEAT) {
                copy(driverHeat = mode, driverVent = other ?: driverVent)
            } else {
                copy(driverVent = mode, driverHeat = other ?: driverHeat)
            }
            ClimateSeat.PASSENGER -> if (kind == SeatClimateKind.HEAT) {
                copy(passengerHeat = mode, passengerVent = other ?: passengerVent)
            } else {
                copy(passengerVent = mode, passengerHeat = other ?: passengerHeat)
            }
            ClimateSeat.REAR -> if (kind == SeatClimateKind.HEAT) copy(rearHeat = mode) else this
        }
    }

    /** Same levels, treating UNKNOWN (an unset level) as Off. */
    fun sameLevelsAs(other: SeatClimateSettings): Boolean =
        ClimateSeat.entries.all { seat ->
            SeatClimateKind.entries.all { kind -> level(seat, kind).bars == other.level(seat, kind).bars }
        }

    fun describe(): String =
        "driver heat ${driverHeat.displayName}/vent ${driverVent.displayName}, " +
            "passenger heat ${passengerHeat.displayName}/vent ${passengerVent.displayName}, " +
            "rear heat ${rearHeat.displayName}"
}

enum class SteeringWheelHeaterLevel(val protoValue: Int, val displayName: String) {
    UNKNOWN(0, "Off"),
    OFF(1, "Off"),
    LEVEL_1(2, "L1"),
    LEVEL_2(3, "L2"),
    LEVEL_3(4, "L3");

    val bars: Int
        get() = when (this) {
            UNKNOWN, OFF -> 0
            LEVEL_1 -> 1
            LEVEL_2 -> 2
            LEVEL_3 -> 3
        }

    /** The seat diagram's tap order, matching the seats: Off -> L3 -> L2 -> L1 -> Off. */
    fun nextTap(): SteeringWheelHeaterLevel = when (this) {
        UNKNOWN, OFF -> LEVEL_3
        LEVEL_3 -> LEVEL_2
        LEVEL_2 -> LEVEL_1
        LEVEL_1 -> OFF
    }

    companion object {
        fun fromProto(value: Int): SteeringWheelHeaterLevel =
            entries.firstOrNull { it.protoValue == value } ?: OFF
    }
}

/**
 * Whether the steering wheel heater is on. The Air reports only this (its heater level stays
 * UNKNOWN); cars with heater levels report a level as well.
 */
enum class SteeringHeaterStatus(val protoValue: Int) {
    UNKNOWN(0),
    OFF(1),
    ON(2);

    companion object {
        fun fromProto(value: Int): SteeringHeaterStatus =
            entries.firstOrNull { it.protoValue == value } ?: UNKNOWN
    }
}

enum class LightAction(val protoValue: Int) {
    UNKNOWN(0),
    FLASH(1),
    ON(2),
    OFF(3)
}

data class LucidSession(
    val uid: String,
    val idToken: String,
    val refreshToken: String,
    val gigyaJwt: String,
    val expiryTimeSec: Int,
    val userEmail: String,
    val firstName: String,
    val lastName: String,
    val region: LucidRegion
) {
    fun isExpired(nowEpochSec: Long = System.currentTimeMillis() / 1000L): Boolean {
        return expiryTimeSec > 0 && nowEpochSec >= (expiryTimeSec - 60L)
    }
}

data class GravityVehicleSnapshot(
    val vehicleId: String,
    val vin: String,
    val nickname: String,
    val model: VehicleModel,
    val variantName: String,
    val paintColorName: String,
    val powerState: PowerState,
    val lockState: LockState,
    val walkawayState: WalkawayState,
    val frunkState: DoorState,
    val rearTrunkState: DoorState,
    val chargePortState: DoorState,
    val frontLeftDoor: DoorState,
    val frontRightDoor: DoorState,
    val rearLeftDoor: DoorState,
    val rearRightDoor: DoorState,
    val batteryPercent: Double,
    val remainingRangeKm: Double,
    val batteryKwh: Double,
    val batteryCapacityKwh: Double,
    val batteryPreconStatus: BatteryPreconStatus,
    val chargeState: ChargeState,
    val chargeLimitPercent: Int,
    val chargeRateKw: Double,
    val chargeRateMph: Double,
    val sessionMinutesRemaining: Int,
    val interiorTempCelsius: Double,
    val exteriorTempCelsius: Double,
    val targetTempCelsius: Double,
    val hvacPower: HvacPower,
    val defrostState: DefrostState,
    val maxAcEnabled: Boolean,
    val driverSeatHeatMode: SeatClimateMode,
    val driverSeatVentMode: SeatClimateMode,
    val passengerSeatHeatMode: SeatClimateMode,
    val steeringHeaterLevel: SteeringWheelHeaterLevel,
    val odometerKm: Double,
    val softwareVersion: String,
    /** When the car sent this state, in Lucid's server clock (epoch ms); unchanged while it sleeps. */
    val lastUpdatedMs: Long,
    /** Raw power-state number as reported, kept for logging values [PowerState] does not know. */
    val powerStateCode: Int = powerState.protoValue,
    val steeringHeaterStatus: SteeringHeaterStatus = SteeringHeaterStatus.UNKNOWN,
    /** Rear seat heat as reported for the rear-left seat; the watch sets all rear seats together. */
    val rearSeatHeatMode: SeatClimateMode = SeatClimateMode.UNKNOWN,
    /** From the vehicle config: the car has heated second-row seats. */
    val hasHeatedRearSeats: Boolean = false,
    val passengerSeatVentMode: SeatClimateMode = SeatClimateMode.OFF,
    /** From the vehicle config: the front seats are ventilated (cooled). */
    val hasVentilatedFrontSeats: Boolean = false
) {
    val seatClimate: SeatClimateSettings
        get() = SeatClimateSettings(
            driverHeat = driverSeatHeatMode,
            driverVent = driverSeatVentMode,
            passengerHeat = passengerSeatHeatMode,
            passengerVent = passengerSeatVentMode,
            rearHeat = rearSeatHeatMode
        )

    /** Show seat cooling when the config says the car has it, or a seat reports cooling on. */
    val showsSeatCooling: Boolean
        get() = hasVentilatedFrontSeats || driverSeatVentMode.isOn || passengerSeatVentMode.isOn

    /** Show rear seat heat when the config says the car has it, or the car reports a rear level. */
    val showsRearSeatHeat: Boolean
        get() = hasHeatedRearSeats || rearSeatHeatMode != SeatClimateMode.UNKNOWN

    /**
     * True for cars that report no steering heater level (the Air): Lucid Cloud rejects the
     * other levels for them, so the heater is switched between OFF and LEVEL_2 ("on").
     */
    val hasOnOffSteeringHeater: Boolean
        get() = steeringHeaterLevel == SteeringWheelHeaterLevel.UNKNOWN

    /**
     * The steering heater as a level: on cars that report only on/off (the Air), "on" is LEVEL_2,
     * the level sent to turn it on.
     */
    val steeringHeaterSetting: SteeringWheelHeaterLevel
        get() = when {
            !hasOnOffSteeringHeater -> steeringHeaterLevel
            isSteeringHeaterOn -> SteeringWheelHeaterLevel.LEVEL_2
            else -> SteeringWheelHeaterLevel.OFF
        }

    val isSteeringHeaterOn: Boolean
        get() = if (hasOnOffSteeringHeater) {
            steeringHeaterStatus == SteeringHeaterStatus.ON
        } else {
            steeringHeaterLevel != SteeringWheelHeaterLevel.OFF
        }

    val remainingRangeMiles: Int
        get() = (remainingRangeKm * 0.621371).roundToInt()

    val odometerMiles: Int
        get() = (odometerKm * 0.621371).roundToInt()

    fun formatTemperature(celsius: Double, useFahrenheit: Boolean): String {
        return if (useFahrenheit) {
            val f = (celsius * 9.0 / 5.0 + 32.0).roundToInt()
            "${f}°F"
        } else {
            val rounded = (celsius * 2.0).roundToInt() / 2.0
            "${rounded}°C"
        }
    }

    fun formatChargingPowerKw(): String {
        return "${chargeRateKw.roundToInt()} kW"
    }
}
