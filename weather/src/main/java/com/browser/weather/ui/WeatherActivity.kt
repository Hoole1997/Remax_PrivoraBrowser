package com.browser.weather.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.browser.common.loadInterstitial
import kotlinx.coroutines.launch

/**
 * 天气页面入口 Activity
 */
class WeatherActivity : AppCompatActivity() {

    companion object {
        /**
         * 启动天气页面
         */
        fun start(context: Context) {
            val intent = Intent(context, WeatherActivity::class.java)
            context.startActivity(intent)
        }
    }

    private lateinit var viewModel: WeatherViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        viewModel =
            ViewModelProvider(this, WeatherViewModel.Factory(this))[WeatherViewModel::class.java]

        setContent {
            Surface(modifier = Modifier.fillMaxSize()) {
                WeatherScreen(
                    viewModel = viewModel,
                    onBackClick = {
                        loadInterstitial {
                            finish()
                        }
                    }
                )
            }
        }
    }

    override fun onBackPressed() {
        loadInterstitial {
            finish()
        }
    }

}
