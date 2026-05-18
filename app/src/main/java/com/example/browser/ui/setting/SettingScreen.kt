package com.example.browser.ui.setting

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.browser.R

@Composable
fun SettingScreen(
    searchEngineName: String,
    searchEngineIcon: Bitmap?,
    languageName: String,
    isDefaultBrowser: Boolean,
    showDebugAdSource: Boolean,
    adSourceName: String,
    onSearchEngineClick: () -> Unit,
    onDefaultBrowserCheckedChange: (Boolean) -> Unit,
    onLanguageClick: () -> Unit,
    onClearHistoryClick: () -> Unit,
    onAboutClick: () -> Unit,
    onFeedbackClick: () -> Unit,
    onAdSourceClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF9F9FA)),
    ) {
        // Toolbar 独立于 LazyColumn，不受 contentPadding 影响
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .height(50.dp)
                .background(Color.White),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = stringResource(R.string.tab_settings),
                color = Color(0xFF333333),
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
            )
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item("search_engine") {
                SettingsActionRow(
                    icon = {
                        if (searchEngineIcon != null) {
                            Image(
                                painter = BitmapPainter(searchEngineIcon.asImageBitmap()),
                                contentDescription = null,
                                modifier = Modifier.size(24.dp),
                            )
                        } else {
                            Image(
                                painter = painterResource(R.mipmap.ic_search_icon_google),
                                contentDescription = null,
                                modifier = Modifier.size(24.dp),
                            )
                        }
                    },
                    title = stringResource(R.string.search_engine),
                    value = searchEngineName,
                    onClick = onSearchEngineClick,
                )
            }

            item("default_browser") {
                SettingsSwitchRow(
                    iconRes = R.mipmap.ic_setting_default_browser,
                    title = stringResource(R.string.default_browser),
                    checked = isDefaultBrowser,
                    onCheckedChange = onDefaultBrowserCheckedChange,
                )
            }

            item("language") {
                SettingsActionRow(
                    icon = {
                        Image(
                            painter = painterResource(R.mipmap.ic_setting_language),
                            contentDescription = null,
                            modifier = Modifier.size(24.dp),
                        )
                    },
                    title = stringResource(R.string.language),
                    value = languageName,
                    onClick = onLanguageClick,
                )
            }

            item("clear_history") {
                SettingsSimpleRow(
                    iconRes = R.mipmap.ic_setting_clear_history,
                    title = stringResource(R.string.clear_history),
                    onClick = onClearHistoryClick,
                )
            }

            item("about") {
                SettingsSimpleRow(
                    iconRes = R.mipmap.ic_setting_about,
                    title = stringResource(R.string.about),
                    onClick = onAboutClick,
                )
            }

            if (showDebugAdSource) {
                item("ad_source") {
                    SettingsActionRow(
                        icon = {
                            Image(
                                painter = painterResource(R.drawable.ic_ads),
                                contentDescription = null,
                                modifier = Modifier.size(24.dp),
                            )
                        },
                        title = "[Debug] 广告源",
                        value = adSourceName,
                        containerColor = Color(0xFFFFF3E0),
                        titleColor = Color(0xFFFF9800),
                        valueColor = Color(0xFFFF9800),
                        arrowTint = Color(0xFFFF9800),
                        onClick = onAdSourceClick,
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingsActionRow(
    icon: @Composable () -> Unit,
    title: String,
    value: String,
    onClick: () -> Unit,
    containerColor: Color = Color.White,
    titleColor: Color = Color(0xFF333333),
    valueColor: Color = Color(0xFF666666),
    arrowTint: Color = Color.Unspecified,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        color = containerColor,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(60.dp)
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            icon()

            Text(
                text = title,
                modifier = Modifier.padding(start = 10.dp),
                color = titleColor,
                fontSize = 16.sp,
            )

            Spacer(modifier = Modifier.weight(1f))

            Text(
                text = value,
                color = valueColor,
                fontSize = 14.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )

            Image(
                painter = painterResource(R.drawable.ic_arrow_right),
                contentDescription = null,
                modifier = Modifier
                    .padding(start = 10.dp)
                    .size(16.dp),
                colorFilter = if (arrowTint != Color.Unspecified) androidx.compose.ui.graphics.ColorFilter.tint(arrowTint) else null,
            )
        }
    }
}

@Composable
private fun SettingsSimpleRow(
    iconRes: Int,
    title: String,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        color = Color.White,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(60.dp)
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Image(
                painter = painterResource(iconRes),
                contentDescription = null,
                modifier = Modifier.size(24.dp),
            )

            Text(
                text = title,
                modifier = Modifier.padding(start = 10.dp),
                color = Color(0xFF333333),
                fontSize = 16.sp,
            )
        }
    }
}

@Composable
private fun SettingsSwitchRow(
    iconRes: Int,
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = Color.White,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(60.dp)
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Image(
                painter = painterResource(iconRes),
                contentDescription = null,
                modifier = Modifier.size(24.dp),
            )

            Text(
                text = title,
                modifier = Modifier.padding(start = 10.dp),
                color = Color(0xFF333333),
                fontSize = 16.sp,
            )

            Spacer(modifier = Modifier.weight(1f))

            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = Color(0xFF0881FE),
                    uncheckedThumbColor = Color.White,
                    uncheckedTrackColor = Color(0xFFD9D9DB),
                    uncheckedBorderColor = Color.Transparent,
                    checkedBorderColor = Color.Transparent,
                ),
            )
        }
    }
}
