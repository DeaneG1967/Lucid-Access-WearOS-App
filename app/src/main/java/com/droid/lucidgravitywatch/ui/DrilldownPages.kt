package com.droid.lucidgravitywatch.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.rotary.onRotaryScrollEvent
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.key
import androidx.wear.compose.foundation.BasicSwipeToDismissBox
import androidx.wear.compose.foundation.rememberSwipeToDismissBoxState
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.ScalingLazyListScope
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import com.droid.lucidgravitywatch.R
import com.droid.lucidgravitywatch.model.ChargeState
import com.droid.lucidgravitywatch.model.GravityVehicleSnapshot
import com.droid.lucidgravitywatch.model.HeatedSeat
import com.droid.lucidgravitywatch.model.SeatClimateMode
import com.droid.lucidgravitywatch.theme.AuroraGreen
import com.droid.lucidgravitywatch.theme.CrimsonAlert
import com.droid.lucidgravitywatch.theme.EurekaGold
import com.droid.lucidgravitywatch.theme.LucidPlatinum
import com.droid.lucidgravitywatch.theme.LucidSilver
import com.droid.lucidgravitywatch.theme.ObsidianBlack
import com.droid.lucidgravitywatch.theme.StealthBorder
import com.droid.lucidgravitywatch.theme.StealthCard
import com.droid.lucidgravitywatch.theme.StealthSurface
import com.droid.lucidgravitywatch.viewmodel.GravityWatchUiState
import com.droid.lucidgravitywatch.viewmodel.GravityWatchViewModel
import kotlin.math.abs
import kotlin.math.roundToInt

private val StatusYellow = Color(0xFFFFEA00)

/** Crown/bezel scroll distance that moves an edited value by one step. */
private const val ROTARY_PIXELS_PER_STEP = 40f

// ---------------------------------------------------------------------------------------------
// Climate
// ---------------------------------------------------------------------------------------------

/**
 * Climate drill-down: inside temperature, target temperature and climate on/off first; seat,
 * steering wheel and defrost controls further down the list.
 */
@Composable
internal fun ClimatePage(
    uiState: GravityWatchUiState,
    vehicle: GravityVehicleSnapshot,
    onToggleClimate: () -> Unit,
    onSetTargetTemperature: (Double) -> Unit,
    onToggleDefrost: () -> Unit,
    onSetSeatHeat: (HeatedSeat, SeatClimateMode) -> Unit,
    onCycleSteeringHeater: () -> Unit,
    onToggleTemperatureUnit: () -> Unit
) {
    var editingTarget by rememberSaveable { mutableStateOf(false) }
    val controlsEnabled = uiState.controlsLockedReason == null
    val useF = uiState.useFahrenheit
    val climateOn = vehicle.hvacPower.isActive

    if (editingTarget) {
        val scale = TemperatureScale.of(useF)
        ValueEditor(
            title = if (climateOn) "TARGET TEMP" else "START CLIMATE AT",
            initialValue = scale.fromCelsius(vehicle.targetTempCelsius),
            minValue = scale.min,
            maxValue = scale.max,
            step = scale.step,
            format = scale::format,
            confirmEnabled = controlsEnabled,
            statusText = uiState.controlsLockedReason,
            testTagPrefix = "target_temp",
            onCancel = { editingTarget = false },
            onConfirm = { value ->
                editingTarget = false
                onSetTargetTemperature(scale.toCelsius(value))
            }
        )
        return
    }

    DrilldownList(title = "CLIMATE", uiState = uiState, testTag = "climate_page") {
        item {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "CABIN",
                    color = LucidSilver,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.2.sp
                )
                Text(
                    text = vehicle.formatTemperature(vehicle.interiorTempCelsius, useF),
                    color = Color.White,
                    fontSize = 30.sp,
                    lineHeight = 30.sp,
                    fontWeight = FontWeight.ExtraBold,
                    modifier = Modifier.testTag("climate_inside_temp")
                )
                Text(
                    text = "Outside ${vehicle.formatTemperature(vehicle.exteriorTempCelsius, useF)}",
                    color = LucidSilver,
                    fontSize = 10.sp
                )
            }
        }
        item {
            ValueRow(
                label = "Target",
                value = vehicle.formatTemperature(vehicle.targetTempCelsius, useF),
                enabled = controlsEnabled,
                onClick = { editingTarget = true },
                testTag = "climate_target_row"
            )
        }
        item {
            ToggleRow(
                label = if (climateOn) "Climate On" else "Climate Off",
                detail = vehicle.hvacPower.displayName.takeIf {
                    climateOn && vehicle.hvacPower.displayName != "On"
                },
                isOn = climateOn,
                enabled = controlsEnabled,
                onClick = onToggleClimate,
                testTag = "climate_power_toggle"
            )
        }
        item { SectionLabel("SEATS & WHEEL") }
        val seats = buildList {
            add(HeatedSeat.DRIVER)
            add(HeatedSeat.PASSENGER)
            if (vehicle.showsRearSeatHeat) add(HeatedSeat.REAR)
        }
        seats.forEach { seat ->
            item(key = "seat_${seat.name}") {
                SeatLevelRow(
                    label = if (seat == HeatedSeat.REAR) "Rear seats" else "${seat.label} seat",
                    current = vehicle.seatHeatMode(seat),
                    enabled = controlsEnabled,
                    onSelect = { mode -> onSetSeatHeat(seat, mode) },
                    testTag = "climate_seat_${seat.name.lowercase()}"
                )
            }
        }
        item {
            ValueRow(
                label = "Steering wheel",
                value = when {
                    !vehicle.hasOnOffSteeringHeater -> vehicle.steeringHeaterLevel.displayName
                    vehicle.isSteeringHeaterOn -> "On"
                    else -> "Off"
                },
                highlighted = vehicle.isSteeringHeaterOn,
                enabled = controlsEnabled,
                onClick = onCycleSteeringHeater,
                testTag = "climate_steering_heat"
            )
        }
        item {
            ToggleRow(
                label = "Max Defrost",
                isOn = vehicle.defrostState.isEnabled,
                enabled = controlsEnabled,
                onClick = onToggleDefrost,
                testTag = "climate_defrost_toggle"
            )
        }
        item {
            SmallChipButton(
                label = if (useF) "Show °C" else "Show °F",
                active = false,
                onClick = onToggleTemperatureUnit,
                testTag = "climate_unit_toggle"
            )
        }
    }
}

/** Display units for the target temperature editor: whole °F, or half °C. */
private enum class TemperatureScale(val min: Double, val max: Double, val step: Double) {
    FAHRENHEIT(60.0, 85.0, 1.0),
    CELSIUS(GravityWatchViewModel.MIN_CABIN_TEMP_C, GravityWatchViewModel.MAX_CABIN_TEMP_C, 0.5);

    fun fromCelsius(celsius: Double): Double = when (this) {
        FAHRENHEIT -> (celsius * 9.0 / 5.0 + 32.0).roundToInt().toDouble()
        CELSIUS -> (celsius * 2.0).roundToInt() / 2.0
    }.coerceIn(min, max)

    fun toCelsius(value: Double): Double = when (this) {
        FAHRENHEIT -> (value - 32.0) * 5.0 / 9.0
        CELSIUS -> value
    }

    fun format(value: Double): String = when (this) {
        FAHRENHEIT -> "${value.roundToInt()}°F"
        CELSIUS -> "$value°C"
    }

    companion object {
        fun of(useFahrenheit: Boolean) = if (useFahrenheit) FAHRENHEIT else CELSIUS
    }
}

// ---------------------------------------------------------------------------------------------
// Charging
// ---------------------------------------------------------------------------------------------

/** Charging drill-down: charge limit, start/stop (only with a cable connected) and charge port. */
@Composable
internal fun ChargingPage(
    uiState: GravityWatchUiState,
    vehicle: GravityVehicleSnapshot,
    onSetChargeLimit: (Int) -> Unit,
    onToggleCharging: () -> Unit,
    onToggleChargePort: () -> Unit
) {
    var editingLimit by rememberSaveable { mutableStateOf(false) }
    var confirmStop by rememberSaveable { mutableStateOf(false) }
    val controlsEnabled = uiState.controlsLockedReason == null
    if (!controlsEnabled && confirmStop) confirmStop = false

    if (editingLimit) {
        ValueEditor(
            title = "CHARGE LIMIT",
            initialValue = vehicle.chargeLimitPercent
                .coerceIn(
                    GravityWatchViewModel.MIN_CHARGE_LIMIT_PERCENT,
                    GravityWatchViewModel.MAX_CHARGE_LIMIT_PERCENT
                ).toDouble(),
            minValue = GravityWatchViewModel.MIN_CHARGE_LIMIT_PERCENT.toDouble(),
            maxValue = GravityWatchViewModel.MAX_CHARGE_LIMIT_PERCENT.toDouble(),
            step = 1.0,
            format = { "${it.roundToInt()}%" },
            confirmEnabled = controlsEnabled,
            statusText = uiState.controlsLockedReason,
            testTagPrefix = "charge_limit",
            onCancel = { editingLimit = false },
            onConfirm = { value ->
                editingLimit = false
                onSetChargeLimit(value.roundToInt())
            }
        )
        return
    }

    val chargeState = vehicle.chargeState
    val isCharging = chargeState.isActivelyCharging
    val pluggedIn = chargeState.isCablePluggedIn

    Box(modifier = Modifier.fillMaxSize()) {
        DrilldownList(title = "CHARGING", uiState = uiState, testTag = "charging_page") {
            item {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "${vehicle.batteryPercent.roundToInt().coerceIn(0, 100)}%",
                        color = Color.White,
                        fontSize = 30.sp,
                        lineHeight = 30.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                    Text(
                        text = chargingSummary(vehicle),
                        color = if (isCharging) AuroraGreen else LucidSilver,
                        fontSize = 10.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.testTag("charging_summary")
                    )
                }
            }
            item {
                ValueRow(
                    label = "Charge limit",
                    value = "${vehicle.chargeLimitPercent}%",
                    enabled = controlsEnabled,
                    onClick = { editingLimit = true },
                    testTag = "charging_limit_row"
                )
            }
            item {
                ToggleRow(
                    label = when {
                        isCharging -> "Stop Charging"
                        pluggedIn -> "Start Charging"
                        else -> "Not Plugged In"
                    },
                    isOn = isCharging,
                    enabled = controlsEnabled && pluggedIn,
                    onClick = { if (isCharging) confirmStop = true else onToggleCharging() },
                    testTag = "charging_start_stop"
                )
            }
            item {
                ToggleRow(
                    label = "Charge Port",
                    detail = if (vehicle.chargePortState.isOpen) "Open · tap to close" else "Closed · tap to open",
                    isOn = vehicle.chargePortState.isOpen,
                    onColor = StatusYellow,
                    enabled = controlsEnabled,
                    onClick = onToggleChargePort,
                    testTag = "charging_port_toggle"
                )
            }
        }
        if (confirmStop) {
            ConfirmationOverlay(
                prompt = "Stop Charging?",
                onCancel = { confirmStop = false },
                onConfirm = {
                    confirmStop = false
                    onToggleCharging()
                }
            )
        }
    }
}

private fun chargingSummary(vehicle: GravityVehicleSnapshot): String {
    val state = vehicle.chargeState
    if (state == ChargeState.UNKNOWN || state == ChargeState.NOT_CONNECTED) return "Unplugged"
    val parts = mutableListOf(state.displayName)
    if (abs(vehicle.chargeRateKw) > 0.1) parts += vehicle.formatChargingPowerKw()
    if (state.isActivelyCharging && vehicle.sessionMinutesRemaining > 0) {
        val h = vehicle.sessionMinutesRemaining / 60
        val m = vehicle.sessionMinutesRemaining % 60
        parts += if (h > 0) "${h}h ${m}m left" else "${m}m left"
    }
    return parts.joinToString(" · ")
}

// ---------------------------------------------------------------------------------------------
// Flonk (flash + honk)
// ---------------------------------------------------------------------------------------------

/** Flash the lights or honk the horn; both fire immediately, as they did on the home screen. */
@Composable
internal fun FlonkPage(
    uiState: GravityWatchUiState,
    onHonkHorn: () -> Unit,
    onFlashLights: () -> Unit
) {
    val controlsEnabled = uiState.controlsLockedReason == null
    Column(
        modifier = Modifier
            .fillMaxSize()
            .testTag("flonk_page"),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        PageTitle("FLONK")
        PageStatusLine(uiState)
        Spacer(modifier = Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(22.dp)) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                ControlIconButton(
                    drawableRes = R.drawable.btn_lights,
                    contentDesc = "Flash Lights Button",
                    isActive = false,
                    isPrimaryCenter = false,
                    activeColor = EurekaGold,
                    onClick = onFlashLights,
                    enabled = controlsEnabled,
                    testTag = "flonk_lights_button"
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text("Flash", color = LucidSilver, fontSize = 11.sp)
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                ControlIconButton(
                    drawableRes = R.drawable.btn_horn,
                    contentDesc = "Honk Horn Button",
                    isActive = false,
                    isPrimaryCenter = false,
                    activeColor = EurekaGold,
                    onClick = onHonkHorn,
                    enabled = controlsEnabled,
                    testTag = "flonk_horn_button"
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text("Honk", color = LucidSilver, fontSize = 11.sp)
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Shared building blocks
// ---------------------------------------------------------------------------------------------

/**
 * Shows [content] with Wear OS swipe-to-dismiss: swiping right reveals [background] (the screen
 * underneath) and calls [onBack]. The hardware/system back action does the same. Only wrap
 * screens that have somewhere to go back to: on the home screen, the system's own swipe should
 * still close the app.
 */
@Composable
internal fun SwipeBackBox(
    pageKey: Any,
    onBack: () -> Unit,
    background: @Composable () -> Unit,
    content: @Composable () -> Unit
) {
    BackHandler(onBack = onBack)
    key(pageKey) {
        val swipeState = rememberSwipeToDismissBoxState()
        BasicSwipeToDismissBox(
            onDismissed = onBack,
            state = swipeState,
            backgroundKey = "background",
            contentKey = pageKey
        ) { isBackground ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(ObsidianBlack)
            ) {
                if (isBackground) background() else content()
            }
        }
    }
}

/** A crown-scrollable, round-screen list with the page title and status line at the top. */
@Composable
private fun DrilldownList(
    title: String,
    uiState: GravityWatchUiState,
    testTag: String,
    content: ScalingLazyListScope.() -> Unit
) {
    // Start with the title at the top edge instead of centered, so the first screen shows as
    // much as possible (no auto-centering padding above the first item).
    val listState = rememberScalingLazyListState(initialCenterItemIndex = 0)
    ScalingLazyColumn(
        state = listState,
        modifier = Modifier
            .fillMaxSize()
            .testTag(testTag),
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 20.dp),
        autoCentering = null,
        verticalArrangement = Arrangement.spacedBy(5.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        item {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                PageTitle(title)
                PageStatusLine(uiState)
            }
        }
        content()
    }
}

@Composable
private fun PageTitle(title: String) {
    Text(
        text = title,
        color = EurekaGold,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.4.sp
    )
}

/** Why controls are frozen, or the last error; nothing when the car is ready. */
@Composable
private fun PageStatusLine(uiState: GravityWatchUiState) {
    val text = uiState.errorMessage ?: uiState.controlsLockedReason ?: return
    Text(
        text = text,
        color = if (uiState.errorMessage != null && !uiState.isRateLimited) CrimsonAlert else StatusYellow,
        fontSize = 10.sp,
        fontWeight = FontWeight.SemiBold,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.testTag("page_status_text")
    )
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        color = LucidSilver,
        fontSize = 9.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.2.sp,
        modifier = Modifier.padding(top = 6.dp)
    )
}

/** Full-width row showing a label and its current value; tapping it changes the value. */
@Composable
private fun ValueRow(
    label: String,
    value: String,
    enabled: Boolean,
    onClick: () -> Unit,
    testTag: String,
    highlighted: Boolean = false
) = key(enabled, highlighted) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(StealthCard.dimmedUnless(enabled))
            .border(1.dp, (if (highlighted) EurekaGold else StealthBorder).dimmedUnless(enabled), RoundedCornerShape(20.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 9.dp)
            .testTag(testTag),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, color = LucidPlatinum.dimmedUnless(enabled), fontSize = 12.sp, maxLines = 1)
        Text(
            text = value,
            color = (if (highlighted) EurekaGold else Color.White).dimmedUnless(enabled),
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1
        )
    }
}

/**
 * Disabled controls are dimmed through their colours rather than Modifier.alpha: toggling an alpha
 * layer inside the ScalingLazyColumn left rows drawn from a stale frame on a Galaxy Watch (cards
 * vanished, or a highlight border appeared around the row above).
 */
private fun Color.dimmedUnless(enabled: Boolean): Color =
    if (enabled) this else copy(alpha = alpha * 0.4f)

private val SeatLevels = listOf(
    SeatClimateMode.OFF,
    SeatClimateMode.LOW,
    SeatClimateMode.MEDIUM,
    SeatClimateMode.HIGH
)

/** A seat's heat level as Off / Low / Med / High chips: one tap goes straight to any level. */
@Composable
private fun SeatLevelRow(
    label: String,
    current: SeatClimateMode,
    enabled: Boolean,
    onSelect: (SeatClimateMode) -> Unit,
    testTag: String
) = key(enabled, current.displayName) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(StealthCard.dimmedUnless(enabled))
            .border(
                1.dp,
                (if (current.displayName != "Off") EurekaGold else StealthBorder).dimmedUnless(enabled),
                RoundedCornerShape(20.dp)
            )
            .padding(horizontal = 8.dp, vertical = 6.dp)
            .testTag(testTag),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(text = label, color = LucidPlatinum.dimmedUnless(enabled), fontSize = 11.sp, maxLines = 1)
        Spacer(modifier = Modifier.height(4.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            SeatLevels.forEach { level ->
                // The car reports an unset level as UNKNOWN, which is shown as Off.
                val selected = current.displayName == level.displayName
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background((if (selected) EurekaGold else StealthSurface).dimmedUnless(enabled))
                        .clickable(enabled = enabled && !selected) { onSelect(level) }
                        .padding(horizontal = 7.dp, vertical = 5.dp)
                        .semantics { contentDescription = "$label ${level.displayName}" }
                        .testTag("${testTag}_${level.name.lowercase()}"),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = level.displayName,
                        color = if (selected) ObsidianBlack else LucidPlatinum.dimmedUnless(enabled),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

/** Full-width on/off row: filled when on. */
@Composable
private fun ToggleRow(
    label: String,
    isOn: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    testTag: String,
    detail: String? = null,
    onColor: Color = AuroraGreen
) = key(enabled, isOn) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background((if (isOn) onColor.copy(alpha = 0.22f) else StealthCard).dimmedUnless(enabled))
            .border(
                if (isOn) 1.5.dp else 1.dp,
                (if (isOn) onColor else StealthBorder).dimmedUnless(enabled),
                RoundedCornerShape(20.dp)
            )
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 9.dp)
            .semantics { contentDescription = "$label ${if (isOn) "on" else "off"}" }
            .testTag(testTag),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = label,
            color = (if (isOn) Color.White else LucidPlatinum).dimmedUnless(enabled),
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1
        )
        if (detail != null) {
            Text(text = detail, color = LucidSilver.dimmedUnless(enabled), fontSize = 9.sp, maxLines = 1)
        }
    }
}

/**
 * Full-screen value picker: the crown/bezel or the -/+ buttons change the value, ✓ sends it and
 * ✕ (or back / swipe) discards it. Nothing is sent to the car until ✓, so turning the crown
 * doesn't fire a command per step.
 */
@Composable
private fun ValueEditor(
    title: String,
    initialValue: Double,
    minValue: Double,
    maxValue: Double,
    step: Double,
    format: (Double) -> String,
    confirmEnabled: Boolean,
    statusText: String?,
    testTagPrefix: String,
    onCancel: () -> Unit,
    onConfirm: (Double) -> Unit
) {
    var value by rememberSaveable { mutableStateOf(initialValue.coerceIn(minValue, maxValue)) }
    var rotaryAccumulator by remember { mutableFloatStateOf(0f) }
    val haptics = LocalHapticFeedback.current
    val focusRequester = remember { FocusRequester() }

    fun nudge(steps: Int) {
        val next = (value + steps * step).coerceIn(minValue, maxValue)
        if (next != value) {
            value = next
            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        }
    }

    BackHandler(onBack = onCancel)
    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .onRotaryScrollEvent { event ->
                rotaryAccumulator += event.verticalScrollPixels
                val steps = (rotaryAccumulator / ROTARY_PIXELS_PER_STEP).toInt()
                if (steps != 0) {
                    rotaryAccumulator -= steps * ROTARY_PIXELS_PER_STEP
                    nudge(steps)
                }
                true
            }
            .focusRequester(focusRequester)
            .focusable()
            .testTag("${testTagPrefix}_editor"),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        PageTitle(title)
        if (statusText != null) {
            Text(text = statusText, color = StatusYellow, fontSize = 10.sp, maxLines = 1)
        }
        Spacer(modifier = Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            RoundIconButton(
                icon = Icons.Default.Remove,
                contentDesc = "Decrease",
                enabled = value > minValue,
                onClick = { nudge(-1) },
                testTag = "${testTagPrefix}_decrease"
            )
            Text(
                text = format(value),
                color = Color.White,
                fontSize = 30.sp,
                fontWeight = FontWeight.ExtraBold,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .width(104.dp)
                    .testTag("${testTagPrefix}_value")
            )
            RoundIconButton(
                icon = Icons.Default.Add,
                contentDesc = "Increase",
                enabled = value < maxValue,
                onClick = { nudge(1) },
                testTag = "${testTagPrefix}_increase"
            )
        }
        Spacer(modifier = Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            RoundIconButton(
                icon = Icons.Default.Close,
                contentDesc = "Cancel",
                enabled = true,
                onClick = onCancel,
                testTag = "${testTagPrefix}_cancel"
            )
            RoundIconButton(
                icon = Icons.Default.Check,
                contentDesc = "Confirm",
                enabled = confirmEnabled,
                filled = true,
                onClick = { onConfirm(value) },
                testTag = "${testTagPrefix}_confirm"
            )
        }
    }
}

@Composable
private fun RoundIconButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDesc: String,
    enabled: Boolean,
    onClick: () -> Unit,
    testTag: String,
    filled: Boolean = false
) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(if (filled) EurekaGold else StealthSurface)
            .border(1.dp, if (filled) EurekaGold else StealthBorder, CircleShape)
            .alpha(if (enabled) 1f else 0.35f)
            .clickable(enabled = enabled, onClick = onClick)
            .semantics { contentDescription = contentDesc }
            .testTag(testTag),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (filled) ObsidianBlack else LucidPlatinum,
            modifier = Modifier.size(20.dp)
        )
    }
}

/** Shared ✕ / ✓ confirmation pop-up used before consequential commands. */
@Composable
internal fun ConfirmationOverlay(
    prompt: String,
    onCancel: () -> Unit,
    onConfirm: () -> Unit
) {
    BackHandler(onBack = onCancel)
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(ObsidianBlack.copy(alpha = 0.88f))
            .clickable(enabled = true, onClick = {})
            .testTag("confirmation_popup_overlay"),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .clip(RoundedCornerShape(18.dp))
                .background(StealthCard)
                .border(1.5.dp, EurekaGold, RoundedCornerShape(18.dp))
                .padding(horizontal = 14.dp, vertical = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = prompt,
                color = LucidPlatinum,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier.testTag("confirmation_prompt_text")
            )
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(20.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(StealthSurface)
                        .border(1.dp, StealthBorder, CircleShape)
                        .clickable(onClick = onCancel)
                        .testTag("confirmation_cancel_button"),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Cancel",
                        tint = LucidPlatinum,
                        modifier = Modifier.size(22.dp)
                    )
                }
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(EurekaGold)
                        .border(1.dp, EurekaGold, CircleShape)
                        .clickable(onClick = onConfirm)
                        .testTag("confirmation_confirm_button"),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = "Confirm",
                        tint = ObsidianBlack,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        }
    }
}
