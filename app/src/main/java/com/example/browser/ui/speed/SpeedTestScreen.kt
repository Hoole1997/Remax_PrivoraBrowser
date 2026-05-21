package com.example.browser.ui.speed

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import android.widget.FrameLayout
import com.example.browser.R
import java.util.Locale
import kotlin.math.cos
import kotlin.math.sin

// ─────────────────────────────────────────────────────────────────────────────
// Colors
// ─────────────────────────────────────────────────────────────────────────────

private val BgDark = Color(0xFF0D1B2A)
private val BgMid = Color(0xFF1B2838)
private val Accent = Color(0xFF00E5FF)
private val AccentEnd = Color(0xFF4ECDC4)
private val TextPrimary = Color.White
private val TextSecondary = Color(0xFF8899AA)
private val CardBg = Color(0xFF162636)
private val GaugeBg = Color(0xFF1E3A5F)
private val ContentMaxWidth = 420.dp

// ─────────────────────────────────────────────────────────────────────────────
// Main Screen
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun SpeedTestScreen(
    state: SpeedTestState,
    ispInfo: IspInfo?,
    onStartClick: () -> Unit,
    onBackClick: () -> Unit,
    adContainer: FrameLayout?,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(BgDark, BgMid))),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding(),
        ) {
            // Toolbar
            SpeedToolbar(onBackClick = onBackClick)

            // Content
            when (state) {
                is SpeedTestState.Idle -> {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .padding(top = 20.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        IdleContent(onStartClick = onStartClick)
                    }
                }
                is SpeedTestState.Connecting -> {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .padding(top = 20.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        ConnectingContent()
                    }
                }
                is SpeedTestState.Testing -> {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        contentAlignment = Alignment.TopCenter,
                    ) {
                        Column(
                            modifier = Modifier
                                .widthIn(max = ContentMaxWidth)
                                .fillMaxWidth()
                                .verticalScroll(rememberScrollState())
                                .padding(horizontal = 24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Spacer(modifier = Modifier.height(32.dp))
                            TestingContent(state = state)
                            Spacer(modifier = Modifier.height(24.dp))
                        }
                    }
                }
                is SpeedTestState.Completed -> {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        contentAlignment = Alignment.TopCenter,
                    ) {
                        Column(
                            modifier = Modifier
                                .widthIn(max = ContentMaxWidth)
                                .fillMaxWidth()
                                .verticalScroll(rememberScrollState())
                                .padding(horizontal = 24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Spacer(modifier = Modifier.height(32.dp))
                            CompletedContent(
                                state = state,
                                onTestAgain = onStartClick,
                                adContainer = adContainer,
                            )
                            Spacer(modifier = Modifier.height(24.dp))
                        }
                    }
                }
            }

            // ISP Info (always visible except completed)
            if (state !is SpeedTestState.Completed) {
                IspInfoBar(ispInfo = ispInfo)
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Toolbar
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun SpeedToolbar(onBackClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .padding(horizontal = 8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_back_arrow),
                contentDescription = "Back",
                tint = TextPrimary,
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .clickable(onClick = onBackClick)
                    .padding(8.dp),
            )
            Spacer(modifier = Modifier.weight(1f))
            Spacer(modifier = Modifier.size(40.dp))
        }
        Text(
            text = stringResource(R.string.speed_test_title),
            color = TextPrimary,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.align(Alignment.Center),
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Idle State: GO button with pulse animation
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun IdleContent(onStartClick: () -> Unit) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulse1 by infiniteTransition.animateFloat(
        initialValue = 0.8f,
        targetValue = 1.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "pulse1",
    )
    val pulse2 by infiniteTransition.animateFloat(
        initialValue = 0.9f,
        targetValue = 1.3f,
        animationSpec = infiniteRepeatable(
            animation = tween(2500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "pulse2",
    )
    val pulseAlpha1 by infiniteTransition.animateFloat(
        initialValue = 0.15f,
        targetValue = 0.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "alpha1",
    )
    val pulseAlpha2 by infiniteTransition.animateFloat(
        initialValue = 0.10f,
        targetValue = 0.03f,
        animationSpec = infiniteRepeatable(
            animation = tween(2500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "alpha2",
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(240.dp),
        ) {
            // Outer pulse rings
            Canvas(modifier = Modifier.size(240.dp)) {
                val center = Offset(size.width / 2, size.height / 2)
                drawCircle(
                    color = Accent.copy(alpha = pulseAlpha2),
                    radius = 120.dp.toPx() * pulse2,
                    center = center,
                )
                drawCircle(
                    color = Accent.copy(alpha = pulseAlpha1),
                    radius = 100.dp.toPx() * pulse1,
                    center = center,
                )
            }

            // Main button
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(160.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(
                            colors = listOf(Accent.copy(alpha = 0.3f), Accent.copy(alpha = 0.05f)),
                        ),
                    )
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onStartClick,
                    ),
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(120.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.verticalGradient(listOf(Accent, AccentEnd)),
                        ),
                ) {
                    Text(
                        text = "GO",
                        color = BgDark,
                        fontSize = 36.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        Text(
            text = "Tap to start speed test",
            color = TextSecondary,
            fontSize = 14.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Connecting State: Ripple animation
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun ConnectingContent() {
    val infiniteTransition = rememberInfiniteTransition(label = "ripple")
    val ripple1 by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = LinearEasing),
        ),
        label = "ripple1",
    )
    val ripple2 by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = LinearEasing, delayMillis = 500),
        ),
        label = "ripple2",
    )
    val ripple3 by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = LinearEasing, delayMillis = 1000),
        ),
        label = "ripple3",
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(240.dp),
        ) {
            Canvas(modifier = Modifier.size(240.dp)) {
                val center = Offset(size.width / 2, size.height / 2)
                val maxRadius = 120.dp.toPx()

                listOf(ripple1, ripple2, ripple3).forEach { progress ->
                    drawCircle(
                        color = Accent.copy(alpha = (1f - progress) * 0.3f),
                        radius = maxRadius * progress,
                        center = center,
                        style = Stroke(width = 2.dp.toPx()),
                    )
                }
            }

            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(100.dp)
                    .clip(CircleShape)
                    .background(CardBg),
            ) {
                Text(
                    text = "...",
                    color = Accent,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        Text(
            text = stringResource(R.string.speed_selecting_server),
            color = TextSecondary,
            fontSize = 14.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Testing State: Gauge + metrics
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun TestingContent(state: SpeedTestState.Testing) {
    // Gauge
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .fillMaxWidth()
            .height(248.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(0.82f)
                .widthIn(max = 320.dp)
                .height(248.dp),
        ) {
            SpeedGauge(
                speed = state.currentSpeed,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(248.dp),
            )

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 20.dp),
            ) {
                Text(
                    text = formatSpeed(state.currentSpeed),
                    color = TextPrimary,
                    fontSize = 44.sp,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = "Mbps",
                    color = TextSecondary,
                    fontSize = 13.sp,
                )
            }
        }
    }

    Spacer(modifier = Modifier.height(16.dp))

    // Phase indicator
    Text(
        text = when (state.phase) {
            TestPhase.PING -> "Testing Ping..."
            TestPhase.DOWNLOAD -> "Testing Download..."
            TestPhase.UPLOAD -> "Testing Upload..."
        },
        color = Accent,
        fontSize = 13.sp,
        fontWeight = FontWeight.Medium,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth(),
    )

    Spacer(modifier = Modifier.height(24.dp))

    // Metrics grid
    MetricsGrid(
        ping = state.ping,
        download = state.download,
        jitter = state.jitter,
        upload = state.upload,
    )
}

// ─────────────────────────────────────────────────────────────────────────────
// Completed State: Results + Test Again
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun CompletedContent(
    state: SpeedTestState.Completed,
    onTestAgain: () -> Unit,
    adContainer: FrameLayout?,
) {
    Spacer(modifier = Modifier.height(16.dp))

    // Speed rating
    val rating = getSpeedRating(state.download)

    Text(
        text = formatSpeed(state.download),
        color = TextPrimary,
        fontSize = 56.sp,
        fontWeight = FontWeight.Bold,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth(),
    )
    Text(
        text = "Mbps Download",
        color = TextSecondary,
        fontSize = 14.sp,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth(),
    )

    Spacer(modifier = Modifier.height(12.dp))

    // Rating badge
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(rating.color.copy(alpha = 0.15f))
            .padding(horizontal = 16.dp, vertical = 6.dp),
    ) {
        Text(
            text = rating.label,
            color = rating.color,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }

    Spacer(modifier = Modifier.height(32.dp))

    // Metrics grid
    MetricsGrid(
        ping = state.ping,
        download = state.download,
        jitter = state.jitter,
        upload = state.upload,
    )

    Spacer(modifier = Modifier.height(28.dp))

    // Test Again button (outline style)
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .clip(RoundedCornerShape(24.dp))
            .background(Color.Transparent)
            .clickable(onClick = onTestAgain)
            .padding(1.dp),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .clip(RoundedCornerShape(24.dp))
                .background(Accent.copy(alpha = 0.1f))
                .padding(horizontal = 48.dp, vertical = 12.dp),
        ) {
            Text(
                text = stringResource(R.string.speed_test_again),
                color = Accent,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }

    // Ad container
    if (adContainer != null) {
        Spacer(modifier = Modifier.height(24.dp))
        AndroidView(
            factory = { adContainer },
            modifier = Modifier
                .fillMaxWidth()
                .height(280.dp),
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Gauge (Compose Canvas)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun SpeedGauge(
    speed: Float,
    modifier: Modifier = Modifier,
) {
    val animatedRatio by animateFloatAsState(
        targetValue = speedToRatio(speed),
        animationSpec = tween(500, easing = FastOutSlowInEasing),
        label = "gauge",
    )

    Canvas(modifier = modifier) {
        val strokeWidth = 14.dp.toPx()
        val radius = minOf(
            (size.width - strokeWidth * 2) / 2f,
            size.height * 0.56f,
        ) * 0.9f
        val center = Offset(size.width / 2, size.height * 0.64f)
        val arcRect = Size(radius * 2, radius * 2)
        val topLeft = Offset(center.x - radius, center.y - radius)

        val startAngle = 150f
        val sweepAngle = 240f

        // Background arc
        drawArc(
            color = GaugeBg,
            startAngle = startAngle,
            sweepAngle = sweepAngle,
            useCenter = false,
            topLeft = topLeft,
            size = arcRect,
            style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
        )

        // Progress arc
        val progressSweep = sweepAngle * animatedRatio
        if (progressSweep > 0f) {
            drawArc(
                brush = Brush.sweepGradient(
                    colors = listOf(Accent, AccentEnd, Accent),
                ),
                startAngle = startAngle,
                sweepAngle = progressSweep,
                useCenter = false,
                topLeft = topLeft,
                size = arcRect,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
            )
        }

        // Scale labels
        val scaleValues = floatArrayOf(0f, 5f, 10f, 50f, 100f, 250f, 500f, 750f, 1000f)
        val scaleLabels = arrayOf("0", "5", "10", "50", "100", "250", "500", "750", "1K")
        val labelRadius = radius - strokeWidth - 12.dp.toPx()

        scaleValues.forEachIndexed { i, _ ->
            val angle = startAngle + sweepAngle * (i.toFloat() / (scaleValues.size - 1))
            val rad = Math.toRadians(angle.toDouble())
            val x = center.x + labelRadius * cos(rad).toFloat()
            val y = center.y + labelRadius * sin(rad).toFloat()

            drawCircle(
                color = TextSecondary.copy(alpha = 0.5f),
                radius = 2.dp.toPx(),
                center = Offset(x, y),
            )
        }

        // Needle
        val needleAngle = startAngle + sweepAngle * animatedRatio
        val needleRad = Math.toRadians(needleAngle.toDouble())
        val needleLength = radius * 0.65f
        val needleEnd = Offset(
            center.x + needleLength * cos(needleRad).toFloat(),
            center.y + needleLength * sin(needleRad).toFloat(),
        )

        // Needle glow
        drawLine(
            color = Accent.copy(alpha = 0.3f),
            start = center,
            end = needleEnd,
            strokeWidth = 6.dp.toPx(),
            cap = StrokeCap.Round,
        )
        // Needle line
        drawLine(
            color = Accent,
            start = center,
            end = needleEnd,
            strokeWidth = 2.5.dp.toPx(),
            cap = StrokeCap.Round,
        )

        // Center dot
        drawCircle(color = Accent, radius = 8.dp.toPx(), center = center)
        drawCircle(color = BgDark, radius = 4.dp.toPx(), center = center)
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Metrics Grid
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun MetricsGrid(
    ping: Int,
    download: Float,
    jitter: Int,
    upload: Float,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            MetricCard(
                icon = R.drawable.ic_speed_ping,
                label = stringResource(R.string.speed_metric_ping),
                value = if (ping > 0) "$ping" else "—",
                unit = stringResource(R.string.speed_unit_ms),
                modifier = Modifier.weight(1f),
            )
            MetricCard(
                icon = R.drawable.ic_speed_download,
                label = stringResource(R.string.speed_metric_download),
                value = if (download > 0) formatSpeed(download) else "—",
                unit = stringResource(R.string.speed_unit_mbps),
                modifier = Modifier.weight(1f),
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            MetricCard(
                icon = R.drawable.ic_speed_jitter,
                label = stringResource(R.string.speed_metric_jitter),
                value = if (jitter > 0) "$jitter" else "—",
                unit = stringResource(R.string.speed_unit_ms),
                modifier = Modifier.weight(1f),
            )
            MetricCard(
                icon = R.drawable.ic_speed_upload,
                label = stringResource(R.string.speed_metric_upload),
                value = if (upload > 0) formatSpeed(upload) else "—",
                unit = stringResource(R.string.speed_unit_mbps),
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun MetricCard(
    icon: Int,
    label: String,
    value: String,
    unit: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(CardBg)
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                painter = painterResource(icon),
                contentDescription = null,
                tint = Accent,
                modifier = Modifier.size(18.dp),
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = label,
                color = TextSecondary,
                fontSize = 12.sp,
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = value,
            color = TextPrimary,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = unit,
            color = TextSecondary,
            fontSize = 11.sp,
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// ISP Info Bar
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun IspInfoBar(ispInfo: IspInfo?) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            modifier = Modifier
                .widthIn(max = ContentMaxWidth)
                .wrapContentWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_speed_wifi),
                contentDescription = null,
                tint = Accent,
                modifier = Modifier.size(20.dp),
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column {
                Text(
                    text = ispInfo?.name?.ifEmpty { stringResource(R.string.speed_isp_unknown) } ?: "—",
                    color = TextPrimary,
                    fontSize = 13.sp,
                    maxLines = 1,
                )
                Text(
                    text = ispInfo?.ip?.ifEmpty { "—" } ?: "—",
                    color = TextSecondary,
                    fontSize = 11.sp,
                )
            }
        } 
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Helpers
// ─────────────────────────────────────────────────────────────────────────────

private fun formatSpeed(speed: Float): String {
    return when {
        speed >= 100 -> String.format(Locale.US, "%.0f", speed)
        speed >= 10 -> String.format(Locale.US, "%.1f", speed)
        speed > 0 -> String.format(Locale.US, "%.2f", speed)
        else -> "0"
    }
}

private data class SpeedRating(val label: String, val color: Color)

private fun getSpeedRating(downloadMbps: Float): SpeedRating {
    return when {
        downloadMbps >= 100 -> SpeedRating("Excellent", Color(0xFF4ECDC4))
        downloadMbps >= 50 -> SpeedRating("Very Good", Color(0xFF6DC882))
        downloadMbps >= 25 -> SpeedRating("Good", Color(0xFFFEBE42))
        downloadMbps >= 10 -> SpeedRating("Fair", Color(0xFFFFA840))
        else -> SpeedRating("Slow", Color(0xFFFC4643))
    }
}

private val scaleValues = floatArrayOf(0f, 5f, 10f, 50f, 100f, 250f, 500f, 750f, 1000f)

private fun speedToRatio(speed: Float): Float {
    if (speed <= 0f) return 0f
    if (speed >= 1000f) return 1f
    val segmentCount = scaleValues.size - 1
    for (i in 0 until segmentCount) {
        if (speed <= scaleValues[i + 1]) {
            val segStart = scaleValues[i]
            val segEnd = scaleValues[i + 1]
            val segFraction = (speed - segStart) / (segEnd - segStart)
            return (i + segFraction) / segmentCount
        }
    }
    return 1f
}
