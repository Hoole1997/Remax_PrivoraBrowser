package com.browser.weather.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.browser.weather.R
/**
 * 城市搜索数据类
 */
data class CitySearchItem(
    val key: String,
    val name: String,
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val country: String? = null,
    val administrativeArea: String? = null
)

/**
 * 城市搜索底部弹窗
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CitySearchBottomSheet(
    isVisible: Boolean,
    onDismiss: () -> Unit,
    onSearch: (String) -> Unit,
    onCitySelected: (CitySearchItem) -> Unit,
    searchResults: List<CitySearchItem>,
    isSearching: Boolean,
    isLoadingWeather: Boolean,
    selectedCityKey: String? = null,
    currentCity: String? = null
) {
    if (!isVisible) return
    
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color.White,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        dragHandle = null
    ) {
        CitySearchContent(
            onSearch = onSearch,
            onCitySelected = onCitySelected,
            searchResults = searchResults,
            isSearching = isSearching,
            isLoadingWeather = isLoadingWeather,
            selectedCityKey = selectedCityKey,
            currentCity = currentCity
        )
    }
}

@Composable
private fun CitySearchContent(
    onSearch: (String) -> Unit,
    onCitySelected: (CitySearchItem) -> Unit,
    searchResults: List<CitySearchItem>,
    isSearching: Boolean,
    isLoadingWeather: Boolean,
    selectedCityKey: String?,
    currentCity: String?
) {
    var searchText by remember { mutableStateOf("") }
    
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 350.dp)  // 增加最小高度
            .padding(horizontal = 16.dp)
            .padding(top = 16.dp, bottom = 32.dp)
    ) {
        // 搜索框
        SearchInputField(
            value = searchText,
            onValueChange = { searchText = it },
            onSearch = { onSearch(searchText) },
            onClear = { searchText = "" }
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // 搜索中加载指示器
        if (isSearching && searchResults.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(32.dp),
                        color = Color(0xFF4A91FF),
                        strokeWidth = 3.dp
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = androidx.compose.ui.res.stringResource(R.string.weather_searching),
                        color = Color(0xFF666666),
                        fontSize = 14.sp
                    )
                }
            }
        } else {
            // Current Location 标签
            Text(
                text = androidx.compose.ui.res.stringResource(R.string.weather_current_location),
                color = Color(0xFF666666),
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // 城市列表
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false)
            ) {
                // 当前城市（如果有）
                if (currentCity != null) {
                    item {
                        CityListItem(
                            city = CitySearchItem(key = "", name = currentCity),
                            isLoading = false,
                            onClick = { /* 当前城市不需要重新加载 */ }
                        )
                    }
                }
                
                // 搜索结果
                items(searchResults) { city ->
                    CityListItem(
                        city = city,
                        isLoading = isLoadingWeather && city.key == selectedCityKey,
                        onClick = { onCitySelected(city) }
                    )
                }
            }
        }
    }
}

/**
 * 搜索输入框
 */
@Composable
private fun SearchInputField(
    value: String,
    onValueChange: (String) -> Unit,
    onSearch: () -> Unit,
    onClear: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(40.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFFF5F5F5))
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 搜索图标
        Image(
            painter = painterResource(id = R.drawable.ic_weather_search),
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            colorFilter = ColorFilter.tint(Color(0xFF999999))
        )
        
        Spacer(modifier = Modifier.width(8.dp))
        
        // 输入框
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier
                .weight(1f)
                .padding(vertical = 8.dp),
            textStyle = TextStyle(
                color = Color(0xFF333333),
                fontSize = 14.sp
            ),
            singleLine = true,
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                imeAction = androidx.compose.ui.text.input.ImeAction.Search
            ),
            keyboardActions = androidx.compose.foundation.text.KeyboardActions(
                onSearch = { onSearch() }
            ),
            decorationBox = { innerTextField ->
                Box {
                    if (value.isEmpty()) {
                        Text(
                            text = androidx.compose.ui.res.stringResource(R.string.weather_search_city_hint),
                            color = Color(0xFF999999),
                            fontSize = 14.sp
                        )
                    }
                    innerTextField()
                }
            }
        )
        
        // 清除按钮
        if (value.isNotEmpty()) {
            Spacer(modifier = Modifier.width(8.dp))
            Image(
                painter = painterResource(id = R.drawable.ic_weather_close),
                contentDescription = "Clear",
                modifier = Modifier
                    .size(20.dp)
                    .clickable { onClear() },
                colorFilter = ColorFilter.tint(Color(0xFF999999))
            )
        }
    }
}

/**
 * 城市列表项
 */
@Composable
private fun CityListItem(
    city: CitySearchItem,
    isLoading: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = !isLoading) { onClick() }
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 位置图标
        Image(
            painter = painterResource(id = R.drawable.ic_weather_location),
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            colorFilter = ColorFilter.tint(Color(0xFF4A91FF))
        )
        
        Spacer(modifier = Modifier.width(8.dp))
        
        // 城市名称
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = city.name,
                color = Color(0xFF333333),
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium
            )
            // 显示国家和地区信息
            if (!city.country.isNullOrEmpty() || !city.administrativeArea.isNullOrEmpty()) {
                val subText = listOfNotNull(city.administrativeArea, city.country)
                    .filter { it.isNotEmpty() }
                    .joinToString(", ")
                if (subText.isNotEmpty()) {
                    Text(
                        text = subText,
                        color = Color(0xFF999999),
                        fontSize = 12.sp
                    )
                }
            }
        }
        
        // 加载指示器
        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                color = Color(0xFF4A91FF),
                strokeWidth = 2.dp
            )
        }
    }
}
