package com.droid.lucidgravitywatch.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.droid.lucidgravitywatch.model.ClimateSeat
import com.droid.lucidgravitywatch.model.GravityVehicleSnapshot
import com.droid.lucidgravitywatch.model.SeatClimateKind
import com.droid.lucidgravitywatch.theme.CryoBlue
import com.droid.lucidgravitywatch.theme.LucidSilver
import com.droid.lucidgravitywatch.theme.StealthBorder
import com.droid.lucidgravitywatch.theme.StealthCard
import com.droid.lucidgravitywatch.viewmodel.GravityWatchUiState

internal val SeatHeatRed = Color(0xFFFF5B3A)
internal val SeatCoolBlue = CryoBlue
private val UnlitBar = Color(0xFF3A4352)
private val SeatOutlineOff = Color(0xFF6B7684)

internal fun SeatClimateKind.accent(): Color =
    if (this == SeatClimateKind.HEAT) SeatHeatRed else SeatCoolBlue

// Diagram geometry (dp), front of the car at the top, driver on the left.
private const val DIAGRAM_WIDTH = 120
private const val HEAT_DIAGRAM_HEIGHT = 158
private const val COOL_DIAGRAM_HEIGHT = 60
private const val FRONT_SEAT_WIDTH = 42
private const val FRONT_SEAT_HEIGHT = 52
private const val DRIVER_SEAT_X = 12
private const val PASSENGER_SEAT_X = 66
/** Share of a seat's height taken by the cushion; the backrest strip is the rest. */
private const val CUSHION_FRACTION = 0.66f

/**
 * Top-down cabin for seat heat or seat cooling. Each tap on a seat steps it Off -> High -> Med ->
 * Low -> Off; the view model sends the result once taps stop. In heat mode the steering wheel and
 * the rear bench (one shared setting) are tappable too; cooling is front seats only.
 */
@Composable
internal fun SeatClimatePage(
    uiState: GravityWatchUiState,
    vehicle: GravityVehicleSnapshot,
    kind: SeatClimateKind,
    onTapSeat: (ClimateSeat, SeatClimateKind) -> Unit,
    onTapSteeringWheel: () -> Unit
) {
    val enabled = uiState.controlsLockedReason == null
    val seats = uiState.seatDraft ?: vehicle.seatClimate
    val wheel = uiState.steeringDraft ?: vehicle.steeringHeaterSetting
    val accent = kind.accent()
    val isHeat = kind == SeatClimateKind.HEAT

    Column(
        modifier = Modifier
            .fillMaxSize()
            .testTag(if (isHeat) "seat_heat_page" else "seat_cooling_page"),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        PageTitle(if (isHeat) "SEAT HEAT" else "SEAT COOLING")
        // No caption while taps are queued; once sent, the status line shows the command.
        PageStatusLine(uiState)
        Spacer(modifier = Modifier.height(6.dp))

        val height = if (isHeat) HEAT_DIAGRAM_HEIGHT else COOL_DIAGRAM_HEIGHT
        Box(modifier = Modifier.size(DIAGRAM_WIDTH.dp, height.dp)) {
            if (isHeat) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    drawRoundRect(
                        color = StealthBorder,
                        cornerRadius = CornerRadius(28.dp.toPx()),
                        style = Stroke(width = 1.5.dp.toPx())
                    )
                }
            }
            val frontY = if (isHeat) 42 else 4
            SeatZone(
                seat = ClimateSeat.DRIVER,
                kind = kind,
                bars = seats.level(ClimateSeat.DRIVER, kind).bars,
                accent = accent,
                enabled = enabled,
                x = DRIVER_SEAT_X.dp, y = frontY.dp,
                width = FRONT_SEAT_WIDTH.dp, height = FRONT_SEAT_HEIGHT.dp,
                onTap = { onTapSeat(ClimateSeat.DRIVER, kind) }
            )
            SeatZone(
                seat = ClimateSeat.PASSENGER,
                kind = kind,
                bars = seats.level(ClimateSeat.PASSENGER, kind).bars,
                accent = accent,
                enabled = enabled,
                x = PASSENGER_SEAT_X.dp, y = frontY.dp,
                width = FRONT_SEAT_WIDTH.dp, height = FRONT_SEAT_HEIGHT.dp,
                onTap = { onTapSeat(ClimateSeat.PASSENGER, kind) }
            )
            if (isHeat) {
                SteeringWheelZone(
                    bars = wheel.bars,
                    showLevel = !vehicle.hasOnOffSteeringHeater,
                    enabled = enabled,
                    // Centred over the driver's seat.
                    x = (DRIVER_SEAT_X + FRONT_SEAT_WIDTH / 2 - 18).dp, y = 6.dp,
                    onTap = onTapSteeringWheel
                )
                if (vehicle.showsRearSeatHeat) {
                    SeatZone(
                        seat = ClimateSeat.REAR,
                        kind = kind,
                        bars = seats.level(ClimateSeat.REAR, kind).bars,
                        accent = accent,
                        enabled = enabled,
                        x = 10.dp, y = 104.dp,
                        width = 100.dp, height = 44.dp,
                        onTap = { onTapSeat(ClimateSeat.REAR, kind) }
                    )
                }
            }
        }
    }
}

@Composable
private fun SeatZone(
    seat: ClimateSeat,
    kind: SeatClimateKind,
    bars: Int,
    accent: Color,
    enabled: Boolean,
    x: Dp,
    y: Dp,
    width: Dp,
    height: Dp,
    onTap: () -> Unit
) {
    val levelName = when (bars) {
        3 -> "High"
        2 -> "Med"
        1 -> "Low"
        else -> "Off"
    }
    Box(
        modifier = Modifier
            .offset(x, y)
            .size(width, height)
            .clickable(enabled = enabled, onClick = onTap)
            .semantics {
                contentDescription = "${seat.label} seat ${kind.label.lowercase()} $levelName"
            }
            .testTag("seat_zone_${seat.name.lowercase()}")
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawSeat(
                bars = bars,
                accent = accent.dimmedUnless(enabled),
                isBench = seat == ClimateSeat.REAR,
                enabled = enabled
            )
        }
        // Seat name inside the backrest strip (the part of the seat below the cushion).
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(height * (1f - CUSHION_FRACTION) - 3.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = when (seat) {
                    ClimateSeat.DRIVER -> "Left"
                    ClimateSeat.PASSENGER -> "Right"
                    ClimateSeat.REAR -> "Rear"
                },
                color = (if (bars > 0) accent else LucidSilver).dimmedUnless(enabled),
                fontSize = 8.sp,
                lineHeight = 8.sp,
                maxLines = 1
            )
        }
    }
}

/** A seat seen from above: cushion in front, backrest behind it; lit bars show the level. */
private fun DrawScope.drawSeat(bars: Int, accent: Color, isBench: Boolean, enabled: Boolean) {
    val on = bars > 0
    val outline = (if (on) accent else SeatOutlineOff).dimmedUnless(enabled)
    val fill = if (on) accent.copy(alpha = accent.alpha * 0.16f) else StealthCard.dimmedUnless(enabled)
    val inset = 1.5.dp.toPx()
    val gap = 3.dp.toPx()
    val cushionHeight = size.height * CUSHION_FRACTION
    val stroke = Stroke(width = 1.5.dp.toPx())

    val cushionTopLeft = Offset(inset, inset)
    val cushionSize = Size(size.width - inset * 2, cushionHeight - inset)
    val cushionRadius = CornerRadius(10.dp.toPx())
    drawRoundRect(fill, cushionTopLeft, cushionSize, cushionRadius)
    drawRoundRect(outline, cushionTopLeft, cushionSize, cushionRadius, style = stroke)

    val backTopLeft = Offset(inset, cushionHeight + gap)
    val backSize = Size(size.width - inset * 2, size.height - cushionHeight - gap - inset)
    val backRadius = CornerRadius(5.dp.toPx())
    drawRoundRect(fill, backTopLeft, backSize, backRadius)
    drawRoundRect(outline, backTopLeft, backSize, backRadius, style = stroke)

    if (isBench) {
        // Three seats share the bench: faint dividers, one shared setting.
        for (i in 1..2) {
            val dx = size.width * i / 3f
            drawLine(
                outline.copy(alpha = outline.alpha * 0.35f),
                Offset(dx, inset + 5.dp.toPx()),
                Offset(dx, cushionHeight - 5.dp.toPx()),
                strokeWidth = 1.dp.toPx()
            )
        }
    }

    drawLevelBars(
        bars = bars,
        lit = accent,
        center = Offset(size.width / 2f, cushionHeight / 2f + inset / 2f),
        barHeight = cushionHeight * 0.55f,
        enabled = enabled
    )
}

/** Three short wavy lines, [bars] of them lit. */
private fun DrawScope.drawLevelBars(bars: Int, lit: Color, center: Offset, barHeight: Float, enabled: Boolean) {
    val spacing = 7.dp.toPx()
    val amplitude = 2.dp.toPx()
    val stroke = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
    for (i in 0 until 3) {
        val x = center.x + (i - 1) * spacing
        val top = center.y - barHeight / 2f
        val path = Path().apply {
            moveTo(x, top + barHeight)
            val quarter = barHeight / 4f
            for (q in 0 until 4) {
                val y0 = top + barHeight - q * quarter
                val side = if (q % 2 == 0) amplitude else -amplitude
                quadraticTo(x + side, y0 - quarter / 2f, x, y0 - quarter)
            }
        }
        drawPath(path, if (i < bars) lit else UnlitBar.dimmedUnless(enabled), style = stroke)
    }
}

@Composable
private fun SteeringWheelZone(
    bars: Int,
    showLevel: Boolean,
    enabled: Boolean,
    x: Dp,
    y: Dp,
    onTap: () -> Unit
) {
    val on = bars > 0
    Box(
        modifier = Modifier
            .offset(x, y)
            .size(36.dp, 32.dp)
            .clickable(enabled = enabled, onClick = onTap)
            .semantics { contentDescription = "Steering wheel heat ${if (on) "on" else "off"}" }
            .testTag("seat_zone_steering_wheel")
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val color = (if (on) SeatHeatRed else SeatOutlineOff).dimmedUnless(enabled)
            val radius = 10.dp.toPx()
            val center = Offset(size.width / 2f, radius + 2.dp.toPx())
            val stroke = 2.5.dp.toPx()
            drawCircle(color, radius, center, style = Stroke(width = stroke))
            drawCircle(color, 2.5.dp.toPx(), center)
            drawLine(color, Offset(center.x - radius, center.y), Offset(center.x + radius, center.y), stroke)
            drawLine(color, center, Offset(center.x, center.y + radius), stroke)
            if (showLevel) {
                // Cars with heater levels: three dots under the wheel, [bars] of them lit.
                val dotY = center.y + radius + 5.dp.toPx()
                for (i in 0 until 3) {
                    drawCircle(
                        if (i < bars) color else UnlitBar.dimmedUnless(enabled),
                        1.5.dp.toPx(),
                        Offset(center.x + (i - 1) * 6.dp.toPx(), dotY)
                    )
                }
            }
        }
    }
}
