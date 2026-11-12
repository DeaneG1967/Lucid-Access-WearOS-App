package com.droid.lucidgravitywatch.model

import com.droid.lucidgravitywatch.protocol.LucidProtobufCodec
import org.junit.Assert.assertEquals
import org.junit.Test

class TemperatureFormatTest {

    private val vehicle = LucidProtobufCodec.decodeGetUserVehiclesResponse(
        LucidProtobufCodec.buildGetUserVehiclesResponseBytesForTest(vehicleId = "v-1", vin = "VIN", nickname = "Car")
    ).single()

    @Test
    fun celsiusReadings_showWholeDegrees() {
        assertEquals("19°C", vehicle.formatTemperature(18.9, useFahrenheit = false))
        assertEquals("15°C", vehicle.formatTemperature(15.4, useFahrenheit = false))
    }

    @Test
    fun celsiusSetpoints_showHalvesOnlyWhenPresent() {
        assertEquals("21.5°C", vehicle.formatTemperature(21.5, useFahrenheit = false, halfDegrees = true))
        assertEquals("21°C", vehicle.formatTemperature(21.0, useFahrenheit = false, halfDegrees = true))
    }

    @Test
    fun fahrenheit_andMissingReadings() {
        assertEquals("66°F", vehicle.formatTemperature(18.9, useFahrenheit = true))
        assertEquals("--°F", vehicle.formatTemperature(Double.NaN, useFahrenheit = true))
        assertEquals("--°C", vehicle.formatTemperature(Double.NaN, useFahrenheit = false))
    }
}
