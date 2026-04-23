package com.browser.weather.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.browser.weather.R
import com.browser.weather.data.DailyForecastData
import com.browser.weather.data.WeatherData
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
import com.android.common.bill.ads.ext.AdShowExt
import com.blankj.utilcode.util.ActivityUtils

// ========== Figma 设计稿精确颜色 ==========
// 背景渐变
private val GradientTop = Color(0xFF47BFDF)
private val GradientBottom = Color(0xFF4A91FF)

// 卡片背景 - #001026 with alpha 0.3
private val CardBackground = Color(0x4D001026)  // #001026 at 30% opacity

// 文字颜色
private val TextPrimary = Color.White
private val TextSecondary = Color(0xCCFFFFFF)  // 80% white
private val TextTertiary = Color(0x99FFFFFF)   // 60% white

// 分隔线
private val DividerColor = Color(0x40FFFFFF)

/**
 * 天气主界面
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WeatherScreen(
    viewModel: WeatherViewModel,
    onBackClick: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val citySearchState by viewModel.citySearchState.collectAsState()
    val isLoadingCity by viewModel.isLoadingCity.collectAsState()
    
    // 城市搜索弹窗状态
    var showCitySearch by remember { mutableStateOf(false) }
    
    // 当前城市名（用于显示在底部 sheet）
    val currentCityName = when (val state = uiState) {
        is WeatherUiState.Success -> state.data.cityName
        else -> null
    }
    
    // 搜索结果
    val searchResults = when (val state = citySearchState) {
        is CitySearchState.Results -> state.cities
        else -> emptyList()
    }
    
    val isSearching = citySearchState is CitySearchState.Searching

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(GradientTop, GradientBottom)))
    ) {
        when (val state = uiState) {
            is WeatherUiState.Loading -> LoadingContent()
            is WeatherUiState.Success -> WeatherContent(
                data = state.data,
                onBackClick = onBackClick,
                onCityClick = { showCitySearch = true }
            )
            is WeatherUiState.Error -> ErrorContent(
                message = state.message,
                onRetry = { viewModel.refresh() },
                onBackClick = onBackClick
            )
        }
        
        // 城市搜索底部弹窗
        var selectedCityKey by remember { mutableStateOf<String?>(null) }
        
        CitySearchBottomSheet(
            isVisible = showCitySearch,
            onDismiss = {
                showCitySearch = false
                selectedCityKey = null
                viewModel.resetCitySearch()
            },
            onSearch = { query ->
                viewModel.searchCity(query)
            },
            onCitySelected = { city ->
                selectedCityKey = city.key
                viewModel.selectCity(city)
            },
            searchResults = searchResults,
            isSearching = isSearching,
            isLoadingWeather = isLoadingCity,
            selectedCityKey = selectedCityKey,
            currentCity = currentCityName
        )
        
        // 当天气加载完成且选中了城市时关闭弹窗
        LaunchedEffect(isLoadingCity, selectedCityKey) {
            if (!isLoadingCity && selectedCityKey != null) {
                showCitySearch = false
                selectedCityKey = null
            }
        }
    }
}

@Composable
private fun LoadingContent() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = TextPrimary, modifier = Modifier.size(48.dp))
    }
}

@Composable
private fun ErrorContent(message: String, onRetry: () -> Unit, onBackClick: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("加载失败", color = TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.Medium)
        Spacer(modifier = Modifier.height(8.dp))
        Text(message, color = TextSecondary, fontSize = 14.sp, textAlign = TextAlign.Center)
        Spacer(modifier = Modifier.height(24.dp))
        Button(
            onClick = onRetry,
            colors = ButtonDefaults.buttonColors(containerColor = CardBackground),
            shape = RoundedCornerShape(20.dp)
        ) {
            Text("重试", color = TextPrimary)
        }
    }
    Box(modifier = Modifier.padding(16.dp).statusBarsPadding()) {
        BackButton(onClick = onBackClick)
    }
}

@Composable
private fun WeatherContent(data: WeatherData, onBackClick: () -> Unit, onCityClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
    ) {
        // ===== 顶部导航 =====
        TopNavigationBar(cityName = data.cityName, onBackClick = onBackClick, onCityClick = onCityClick)
        
        // ===== 日期 =====
        Text(
            text = getCurrentDateString(),
            color = TextSecondary,
            fontSize = 15.sp,
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .padding(top = 4.dp)
        )
        
        Spacer(modifier = Modifier.height(32.dp))
        
        // ===== 主天气区域 =====
        MainWeatherDisplay(data = data)
        
        Spacer(modifier = Modifier.height(40.dp))
        
        // ===== 风速/降水卡片 =====
        WindPrecipitationCard(data = data)
        
        Spacer(modifier = Modifier.height(12.dp))
        
        // ===== 原生广告 =====
        NativeAdCard()
        
        Spacer(modifier = Modifier.height(12.dp))
        
        // ===== 5天预报卡片 =====
        DailyForecastCard(dailyForecasts = data.dailyForecasts)
        
        Spacer(modifier = Modifier.height(12.dp))
        
        // ===== 更多详情卡片 =====
        MoreDetailsCard(data = data)
        
        Spacer(modifier = Modifier.height(24.dp))
    }
}

// ===== 返回按钮 =====
@Composable
private fun BackButton(onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(32.dp)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter = painterResource(id = R.drawable.ic_weather_back),
            contentDescription = "Back",
            modifier = Modifier.size(24.dp),
            colorFilter = ColorFilter.tint(TextPrimary)
        )
    }
}

// ===== 顶部导航栏 =====
@Composable
private fun TopNavigationBar(cityName: String, onBackClick: () -> Unit, onCityClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 12.dp)
    ) {
        // 返回按钮 - 左侧
        BackButton(onClick = onBackClick)
        
        // 城市选择器 - 居中，可点击
        Row(
            modifier = Modifier
                .align(Alignment.Center)
                .clickable { onCityClick() },
            verticalAlignment = Alignment.CenterVertically
        ) {
            Image(
                painter = painterResource(id = R.drawable.ic_weather_location),
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                colorFilter = ColorFilter.tint(TextPrimary)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = cityName,
                color = TextPrimary,
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.width(4.dp))
            // 使用三角形下拉符号
            Image(
                painter = painterResource(id = R.drawable.ic_weather_dropdown),
                contentDescription = null,
                modifier = Modifier.size(8.dp),
                colorFilter = ColorFilter.tint(TextPrimary)
            )
        }
    }
}

// ===== 主天气显示 =====
@Composable
private fun MainWeatherDisplay(data: WeatherData) {
    // 温度单位状态：true = 摄氏度, false = 华氏度
    var isCelsius by remember { mutableStateOf(true) }
    
    // 温度转换函数
    fun celsiusToFahrenheit(celsius: Int): Int = (celsius * 9 / 5) + 32
    
    // 当前显示的温度
    val displayTemp = if (isCelsius) data.temperature else celsiusToFahrenheit(data.temperature)
    val displayTempHigh = if (isCelsius) data.temperatureHigh else celsiusToFahrenheit(data.temperatureHigh)
    val displayTempLow = if (isCelsius) data.temperatureLow else celsiusToFahrenheit(data.temperatureLow)
    
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // 天气图标 + 温度
        Row(
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.Center
        ) {
            // 天气图标
            Image(
                painter = painterResource(id = getWeatherIconRes(data.weatherIcon, data.isDayTime)),
                contentDescription = null,
                modifier = Modifier.size(100.dp)
            )
            
            Spacer(modifier = Modifier.width(12.dp))
            
            // 温度显示 + 单位切换
            TemperatureWithUnitToggle(
                temperature = displayTemp,
                isCelsius = isCelsius,
                onUnitChange = { isCelsius = it }
            )
        }
        
        Spacer(modifier = Modifier.height(12.dp))
        
        // 天气描述
        Text(
            text = data.weatherText,
            color = TextPrimary,
            fontSize = 18.sp,
            fontWeight = FontWeight.Medium
        )
        
        Spacer(modifier = Modifier.height(4.dp))
        
        // 最高/最低温度
        Text(
            text = "High: $displayTempHigh°  Low: $displayTempLow°",
            color = TextSecondary,
            fontSize = 14.sp
        )
    }
}

/**
 * 温度显示 + 单位切换弹窗
 */
@Composable
private fun TemperatureWithUnitToggle(
    temperature: Int,
    isCelsius: Boolean,
    onUnitChange: (Boolean) -> Unit
) {
    var showPopup by remember { mutableStateOf(false) }
    
    Row(verticalAlignment = Alignment.Top) {
        // 温度数字
        Text(
            text = "$temperature",
            color = TextPrimary,
            fontSize = 72.sp,
            fontWeight = FontWeight.Light,
            letterSpacing = (-2).sp
        )
        
        // 单位选择器 - 使用 Box 包裹以便定位弹窗
        Box {
            Column(modifier = Modifier.padding(top = 12.dp)) {
                Row(
                    modifier = Modifier.clickable { showPopup = true },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (isCelsius) "°C" else "°F",
                        color = TextPrimary,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Normal
                    )
                    Spacer(modifier = Modifier.width(2.dp))
                    // 下拉三角形
                    Image(
                        painter = painterResource(id = R.drawable.ic_weather_dropdown),
                        contentDescription = null,
                        modifier = Modifier.size(6.dp),
                        colorFilter = ColorFilter.tint(TextPrimary)
                    )
                }
            }
            
            // 温度单位选择弹窗 - 显示在单位文字下方，左对齐
            if (showPopup) {
                Popup(
                    alignment = Alignment.TopStart,
                    offset = androidx.compose.ui.unit.IntOffset(0, 80),
                    onDismissRequest = { showPopup = false },
                    properties = PopupProperties(focusable = true)
                ) {
                    // 弹窗内容
                    Column(
                        modifier = Modifier
                            .width(65.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color.White)
                    ) {
                        // °F 选项
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onUnitChange(false)
                                    showPopup = false
                                }
                                .padding(vertical = 12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "°F",
                                color = if (!isCelsius) Color(0xFF4A91FF) else Color(0xFF333333),
                                fontSize = 16.sp,
                                fontWeight = if (!isCelsius) FontWeight.SemiBold else FontWeight.Normal
                            )
                        }
                        
                        // 分隔线
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp)
                                .height(1.dp)
                                .background(Color(0xFFE0E0E0))
                        )
                        
                        // °C 选项
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onUnitChange(true)
                                    showPopup = false
                                }
                                .padding(vertical = 12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "°C",
                                color = if (isCelsius) Color(0xFF4A91FF) else Color(0xFF333333),
                                fontSize = 16.sp,
                                fontWeight = if (isCelsius) FontWeight.SemiBold else FontWeight.Normal
                            )
                        }
                    }
                }
            }
        }
    }
}

// ===== 风速/降水卡片 =====
@Composable
private fun WindPrecipitationCard(data: WeatherData) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(CardBackground)
            .padding(vertical = 20.dp, horizontal = 24.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 风速
        Row(verticalAlignment = Alignment.CenterVertically) {
            Image(
                painter = painterResource(id = R.drawable.ic_weather_wind),
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                colorFilter = ColorFilter.tint(TextPrimary)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Column {
                Text(androidx.compose.ui.res.stringResource(R.string.weather_wind_speed), color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                Text("${data.windSpeed.toInt()} km/h", color = TextSecondary, fontSize = 13.sp)
            }
        }
        
        // 分隔线
        Box(
            modifier = Modifier
                .width(1.dp)
                .height(36.dp)
                .background(DividerColor)
        )
        
        // 降水
        Row(verticalAlignment = Alignment.CenterVertically) {
            Image(
                painter = painterResource(id = R.drawable.ic_weather_precipitation),
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                colorFilter = ColorFilter.tint(TextPrimary)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Column {
                Text(androidx.compose.ui.res.stringResource(R.string.weather_precipitation), color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                Text("${data.precipitationProbability} %", color = TextSecondary, fontSize = 13.sp)
            }
        }
    }
}

// ===== 原生广告卡片 =====
@Composable
private fun NativeAdCard() {
    val coroutineScope = rememberCoroutineScope()
    var isAdLoaded by remember { mutableStateOf(false) }
    
    // 使用 AndroidView 嵌入原生广告容器
    AndroidView(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .then(if (isAdLoaded) Modifier else Modifier.height(0.dp)),
        factory = { context ->
            FrameLayout(context).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
            }
        },
        update = { container ->
            // 只加载一次广告
            if (container.childCount == 0) {
                coroutineScope.launch {
                    try {
                        val success = AdShowExt.showNativeAdInContainer(
                            context = ActivityUtils.getTopActivity(),
                            container = container
                        )
                        isAdLoaded = success
                        container.visibility = if (success) View.VISIBLE else View.GONE
                    } catch (e: Exception) {
                        container.visibility = View.GONE
                        isAdLoaded = false
                    }
                }
            }
        }
    )
}

// ===== 5天预报卡片 =====
@Composable
private fun DailyForecastCard(dailyForecasts: List<DailyForecastData>) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(CardBackground)
            .padding(16.dp)
    ) {
        Text(
            text = androidx.compose.ui.res.stringResource(R.string.weather_daily_forecast),
            color = TextPrimary,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            dailyForecasts.forEach { forecast ->
                DayItem(
                    day = forecast.dayOfWeek,
                    iconRes = getWeatherIconRes(forecast.iconId, true),
                    temps = "${forecast.tempLow}°/${forecast.tempHigh}°",
                    humidity = "${forecast.precipitationProbability} %"
                )
            }
        }
    }
}

@Composable
private fun DayItem(day: String, iconRes: Int, temps: String, humidity: String) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.width(56.dp)
    ) {
        Text(day, color = TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.Medium)
        Spacer(modifier = Modifier.height(10.dp))
        Image(
            painter = painterResource(id = iconRes),
            contentDescription = null,
            modifier = Modifier.size(36.dp)
        )
        Spacer(modifier = Modifier.height(10.dp))
        Text(temps, color = TextPrimary, fontSize = 11.sp, fontWeight = FontWeight.Medium)
        Spacer(modifier = Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Image(
                painter = painterResource(id = R.drawable.ic_weather_precipitation),
                contentDescription = null,
                modifier = Modifier.size(10.dp),
                colorFilter = ColorFilter.tint(TextSecondary)
            )
            Spacer(modifier = Modifier.width(2.dp))
            Text(humidity, color = TextSecondary, fontSize = 10.sp)
        }
    }
}

// ===== 更多详情卡片 =====
@Composable
private fun MoreDetailsCard(data: WeatherData) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(CardBackground)
            .padding(20.dp)
    ) {
        Text(
            text = androidx.compose.ui.res.stringResource(R.string.weather_more_details),
            color = TextPrimary,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // 使用 Column + Row 实现网格布局，竖线和横线连接
        Column {
            // 第一行: Humidity | UV Index
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(IntrinsicSize.Min)
            ) {
                // 左侧 item
                DetailItemLeft(
                    modifier = Modifier.weight(1f),
                    iconRes = R.drawable.ic_weather_humidity,
                    value = "${data.humidity} %",
                    label = androidx.compose.ui.res.stringResource(R.string.weather_humidity)
                )
                // 竖向分隔线
                Box(
                    modifier = Modifier
                        .width(1.dp)
                        .fillMaxHeight()
                        .background(DividerColor)
                )
                // 右侧 item
                DetailItemLeft(
                    modifier = Modifier.weight(1f),
                    iconRes = R.drawable.ic_weather_uv,
                    value = "${data.uvIndex}",
                    badge = data.uvIndexText,
                    label = androidx.compose.ui.res.stringResource(R.string.weather_uv_index)
                )
            }
            
            // 横向分隔线 (与竖线连接)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(DividerColor)
            )
            
            // 第二行: Pressure | Visibility
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(IntrinsicSize.Min)
            ) {
                DetailItemLeft(
                    modifier = Modifier.weight(1f),
                    iconRes = R.drawable.ic_weather_pressure,
                    value = "${data.pressure.toInt()} hPa",
                    label = androidx.compose.ui.res.stringResource(R.string.weather_pressure)
                )
                Box(
                    modifier = Modifier
                        .width(1.dp)
                        .fillMaxHeight()
                        .background(DividerColor)
                )
                DetailItemLeft(
                    modifier = Modifier.weight(1f),
                    iconRes = R.drawable.ic_weather_visibility,
                    value = "${data.visibility.toInt()} km",
                    label = androidx.compose.ui.res.stringResource(R.string.weather_visibility)
                )
            }
            
            // 横向分隔线
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(DividerColor)
            )
            
            // 第三行: Sunrise | Sunset
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(IntrinsicSize.Min)
            ) {
                DetailItemLeft(
                    modifier = Modifier.weight(1f),
                    iconRes = R.drawable.ic_weather_sunrise,
                    value = data.sunrise,
                    label = androidx.compose.ui.res.stringResource(R.string.weather_sunrise)
                )
                Box(
                    modifier = Modifier
                        .width(1.dp)
                        .fillMaxHeight()
                        .background(DividerColor)
                )
                DetailItemLeft(
                    modifier = Modifier.weight(1f),
                    iconRes = R.drawable.ic_weather_sunset,
                    value = data.sunset,
                    label = androidx.compose.ui.res.stringResource(R.string.weather_sunset)
                )
            }
        }
    }
}

/**
 * 单个 Detail Item - 左对齐布局
 */
@Composable
private fun DetailItemLeft(
    modifier: Modifier = Modifier,
    iconRes: Int,
    value: String,
    badge: String? = null,
    label: String
) {
    Row(
        modifier = modifier.padding(vertical = 16.dp, horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 图标
        Image(
            painter = painterResource(id = iconRes),
            contentDescription = null,
            modifier = Modifier.size(32.dp),
            colorFilter = ColorFilter.tint(TextPrimary)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(value, color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                if (badge != null) {
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = badge,
                        color = TextPrimary,
                        fontSize = 9.sp,
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(Color(0x33FFFFFF))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }
            Text(label, color = TextTertiary, fontSize = 11.sp)
        }
    }
}

// ===== 工具函数 =====
private fun getCurrentDateString(): String {
    val calendar = java.util.Calendar.getInstance()
    val dayOfWeek = arrayOf("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat")[calendar.get(java.util.Calendar.DAY_OF_WEEK) - 1]
    val month = arrayOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")[calendar.get(java.util.Calendar.MONTH)]
    val day = calendar.get(java.util.Calendar.DAY_OF_MONTH)
    return "$dayOfWeek, $month $day"
}

private fun getWeatherIconRes(weatherCode: Int, isDayTime: Boolean): Int {
    return when (weatherCode) {
        0 -> if (isDayTime) R.drawable.ic_weather_sunny else R.drawable.ic_weather_night_clear
        1 -> if (isDayTime) R.drawable.ic_weather_sunny else R.drawable.ic_weather_night_clear
        2 -> if (isDayTime) R.drawable.ic_weather_partly_cloudy else R.drawable.ic_weather_night_cloudy
        3 -> R.drawable.ic_weather_cloudy
        45, 48 -> R.drawable.ic_weather_fog
        51, 53, 55, 56, 57 -> R.drawable.ic_weather_rain
        61, 63, 65, 66, 67 -> R.drawable.ic_weather_rain
        71, 73, 75, 77 -> R.drawable.ic_weather_snow
        80, 81, 82 -> R.drawable.ic_weather_rain
        85, 86 -> R.drawable.ic_weather_snow
        95, 96, 99 -> R.drawable.ic_weather_thunderstorm
        else -> R.drawable.ic_weather_cloudy
    }
}
