package com.digitumdei.shotquill

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.digitumdei.shotquill.shared.settings.AndroidLocalSettingsRepository

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val settingsRepository = AndroidLocalSettingsRepository(this)
        setContent {
            App(settingsRepository = settingsRepository)
        }
    }
}
